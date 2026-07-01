package com.ketangpai.service;

import com.ketangpai.model.entity.Assignment;
import com.ketangpai.model.entity.KnowledgeChunk;
import com.ketangpai.model.entity.Material;
import com.ketangpai.model.entity.Topic;
import com.ketangpai.model.enums.MaterialType;
import com.ketangpai.model.enums.SourceType;
import com.ketangpai.repository.KnowledgeChunkRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 知识库管理服务 — 负责课程资料的索引与检索（RAG 架构核心）
 *
 * <p>功能：
 * <ol>
 *   <li>文档解析与分块（~500 字/块，50 字重叠）</li>
 *   <li>Embedding 向量化（EmbeddingModel）</li>
 *   <li>Qdrant VectorStore 写入 / 删除</li>
 *   <li>按 courseId 过滤的语义检索</li>
 * </ol>
 */
@Slf4j
@Service
public class KnowledgeBaseService {

    /** 分块最大字符数 */
    private static final int MAX_CHUNK_SIZE = 500;

    /** 块间重叠字符数 */
    private static final int CHUNK_OVERLAP = 50;

    /** 默认检索数量 */
    private static final int DEFAULT_TOP_K = 5;

    /** 相似度阈值 */
    private static final double SIMILARITY_THRESHOLD = 0.6;

    /** 当前 Embedding 服务单次最多接受 10 条输入 */
    private static final int VECTORSTORE_ADD_BATCH_SIZE = 10;

    /** 可提取文本的文件扩展名 */
    private static final Set<String> TEXT_EXTRACTABLE = Set.of("txt", "docx", "pdf");

    private final EmbeddingModel embeddingModel;
    private final VectorStore vectorStore;
    private final KnowledgeChunkRepository knowledgeChunkRepository;
    private final FileService fileService;
    private final TextExtractionService textExtractionService;

    @Value("${spring.ai.vectorstore.qdrant.host:localhost}")
    private String qdrantHost;

    @Value("${spring.ai.vectorstore.qdrant.port:6334}")
    private int qdrantPort;

    @Value("${spring.ai.vectorstore.qdrant.use-tls:false}")
    private boolean qdrantUseTls;

    @Value("${spring.ai.vectorstore.qdrant.api-key:}")
    private String qdrantApiKey;

    @Value("${spring.ai.vectorstore.qdrant.collection-name:ketangpai-kb}")
    private String qdrantCollection;

    public KnowledgeBaseService(EmbeddingModel embeddingModel,
                                VectorStore vectorStore,
                                KnowledgeChunkRepository knowledgeChunkRepository,
                                FileService fileService,
                                TextExtractionService textExtractionService) {
        this.embeddingModel = embeddingModel;
        this.vectorStore = vectorStore;
        this.knowledgeChunkRepository = knowledgeChunkRepository;
        this.fileService = fileService;
        this.textExtractionService = textExtractionService;
    }

    /** 初始化 Qdrant payload 索引，确保 courseId 过滤可用 */
    @PostConstruct
    public void initPayloadIndexes() {
        try {
            String scheme = qdrantUseTls ? "https" : "http";
            String url = scheme + "://" + qdrantHost + ":" + qdrantPort
                    + "/collections/" + qdrantCollection + "/index";
            String body = "{\"field_name\":\"courseId\",\"field_type\":\"integer\"}";

            RestClient client = RestClient.create();
            client.put()
                    .uri(url)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("api-key", qdrantApiKey)
                    .body(body)
                    .retrieve()
                    .toBodilessEntity();

            log.info("Qdrant payload 索引已就绪: courseId (integer) on {}", qdrantCollection);
        } catch (Exception e) {
            // 索引可能已存在（Qdrant 幂等），400/409 忽略
            log.debug("Qdrant 索引初始化（可能已存在）: {}", e.getMessage());
        }
    }

    // ==================== 索引事件（@Async 触发） ====================

    /** 索引一份资料 */
    @Async
    @Transactional
    public void indexMaterial(Material material) {
        try {
            String text = buildMaterialText(material);
            if (text.isBlank()) {
                log.warn("资料 [id={}] 无可提取的文本内容，跳过索引", material.getId());
                return;
            }
            // 先删除旧索引
            deleteBySource(SourceType.MATERIAL, material.getId());
            // 再创建新索引
            indexText(material.getCourseId(), SourceType.MATERIAL, material.getId(),
                    material.getTitle(), text);
            log.info("资料 [id={}, title={}] 索引完成", material.getId(), material.getTitle());
        } catch (Exception e) {
            log.error("资料 [id={}] 索引失败", material.getId(), e);
        }
    }

    /** 索引一个作业 */
    @Async
    @Transactional
    public void indexAssignment(Assignment assignment) {
        try {
            String text = buildAssignmentText(assignment);
            if (text.isBlank()) {
                log.warn("作业 [id={}] 无文本内容，跳过索引", assignment.getId());
                return;
            }
            deleteBySource(SourceType.ASSIGNMENT, assignment.getId());
            indexText(assignment.getCourseId(), SourceType.ASSIGNMENT, assignment.getId(),
                    assignment.getTitle(), text);
            log.info("作业 [id={}, title={}] 索引完成", assignment.getId(), assignment.getTitle());
        } catch (Exception e) {
            log.error("作业 [id={}] 索引失败", assignment.getId(), e);
        }
    }

    /** 索引一个话题 */
    @Async
    @Transactional
    public void indexTopic(Topic topic) {
        try {
            String text = buildTopicText(topic);
            if (text.isBlank()) {
                log.warn("话题 [id={}] 无文本内容，跳过索引", topic.getId());
                return;
            }
            deleteBySource(SourceType.TOPIC, topic.getId());
            indexText(topic.getCourseId(), SourceType.TOPIC, topic.getId(),
                    topic.getTitle(), text);
            log.info("话题 [id={}, title={}] 索引完成", topic.getId(), topic.getTitle());
        } catch (Exception e) {
            log.error("话题 [id={}] 索引失败", topic.getId(), e);
        }
    }

    // ==================== 核心索引逻辑 ====================

    /**
     * 将文本分块、向量化并写入 Qdrant 和 knowledge_chunk 表。
     */
    private void indexText(Long courseId, SourceType sourceType, Long sourceId,
                           String sourceName, String text) {
        List<String> chunkTexts = chunkText(text);
        if (chunkTexts.isEmpty()) {
            return;
        }

        // 构建 Document 列表（供 VectorStore）
        List<Document> documents = new ArrayList<>();
        List<KnowledgeChunk> chunks = new ArrayList<>();

        for (int i = 0; i < chunkTexts.size(); i++) {
            String chunkContent = chunkTexts.get(i);
            String pointId = UUID.randomUUID().toString();

            // Qdrant Document
            Document doc = new Document(
                    pointId,
                    buildVectorDocumentContent(sourceType, sourceName, chunkContent),
                    Map.of(
                            "courseId", courseId,
                            "sourceType", sourceType.name(),
                            "sourceId", sourceId,
                            "sourceName", sourceName != null ? sourceName : "",
                            "chunkIndex", i
                    )
            );
            documents.add(doc);

            // DB 记录
            KnowledgeChunk chunk = KnowledgeChunk.builder()
                    .courseId(courseId)
                    .sourceType(sourceType)
                    .sourceId(sourceId)
                    .sourceName(sourceName)
                    .chunkIndex(i)
                    .content(chunkContent)
                    .qdrantPointId(pointId)
                    .build();
            chunks.add(chunk);
        }

        // 批量写入 VectorStore（Qdrant）
        addToVectorStoreInBatches(documents);
        // 批量写入数据库
        knowledgeChunkRepository.saveAll(chunks);

        log.info("知识库索引: courseId={}, sourceType={}, sourceId={}, chunks={}",
                courseId, sourceType, sourceId, chunks.size());
    }

    private void addToVectorStoreInBatches(List<Document> documents) {
        for (int start = 0; start < documents.size(); start += VECTORSTORE_ADD_BATCH_SIZE) {
            int end = Math.min(start + VECTORSTORE_ADD_BATCH_SIZE, documents.size());
            vectorStore.add(new ArrayList<>(documents.subList(start, end)));
        }
    }

    // ==================== 删除索引 ====================

    /** 删除指定来源的全部知识库块（Qdrant + DB） */
    @Transactional
    public void deleteBySource(SourceType sourceType, Long sourceId) {
        List<KnowledgeChunk> existing = knowledgeChunkRepository
                .findBySourceTypeAndSourceId(sourceType, sourceId);

        if (existing.isEmpty()) {
            return;
        }

        // 从 Qdrant 删除（使用 FilterExpressionBuilder 构建过滤条件）
        try {
            Filter.Expression filter = new FilterExpressionBuilder()
                    .and(
                            new FilterExpressionBuilder().eq("sourceType", sourceType.name()),
                            new FilterExpressionBuilder().eq("sourceId", sourceId)
                    )
                    .build();
            vectorStore.delete(filter);
        } catch (Exception e) {
            log.warn("从 Qdrant 删除索引失败: sourceType={}, sourceId={}", sourceType, sourceId, e);
        }

        // 从 DB 删除
        knowledgeChunkRepository.deleteBySourceTypeAndSourceId(sourceType, sourceId);
        log.info("知识库索引已删除: sourceType={}, sourceId={}, count={}", sourceType, sourceId, existing.size());
    }

    // ==================== 检索 ====================

    /**
     * 语义检索课程相关知识库块。
     *
     * @param courseId 课程 ID（强制过滤）
     * @param query    查询文本
     * @param topK     返回数量
     * @return 按相似度降序排列的知识库块列表
     */
    public List<KnowledgeChunk> searchRelevant(Long courseId, String query, int topK) {
        if (query == null || query.isBlank()) {
            return List.of();
        }

        int k = topK > 0 ? topK : DEFAULT_TOP_K;

        try {
            List<KnowledgeChunk> allCourseChunks = knowledgeChunkRepository.findByCourseId(courseId);
            List<KnowledgeChunk> keywordMatches = findKeywordMatches(allCourseChunks, query, k);
            List<KnowledgeChunk> vectorMatches = List.of();

            // 使用 courseId 过滤 + 相似度阈值
            Filter.Expression courseFilter = new FilterExpressionBuilder()
                    .eq("courseId", courseId)
                    .build();

            SearchRequest request = SearchRequest.builder()
                    .query(query)
                    .topK(k)
                    .similarityThreshold(SIMILARITY_THRESHOLD)
                    .filterExpression(courseFilter)
                    .build();

            try {
                List<Document> results = vectorStore.similaritySearch(request);

                // 通过 qdrantPointId 反查 KnowledgeChunk
                List<String> pointIds = results.stream()
                        .map(Document::getId)
                        .collect(Collectors.toList());

                if (!pointIds.isEmpty()) {
                    Map<String, KnowledgeChunk> chunkMap = allCourseChunks.stream()
                            .filter(c -> pointIds.contains(c.getQdrantPointId()))
                            .collect(Collectors.toMap(KnowledgeChunk::getQdrantPointId, c -> c, (a, b) -> a));

                    // 保持与向量搜索相同的顺序
                    List<KnowledgeChunk> ordered = new ArrayList<>();
                    for (Document doc : results) {
                        KnowledgeChunk chunk = chunkMap.get(doc.getId());
                        if (chunk != null) {
                            ordered.add(chunk);
                        }
                    }
                    vectorMatches = ordered;
                }
            } catch (Exception e) {
                log.warn("向量检索失败，降级使用关键词检索: courseId={}, query={}", courseId, query, e);
            }

            return mergeAndLimit(vectorMatches, keywordMatches, k);
        } catch (Exception e) {
            log.error("知识库检索失败: courseId={}, query={}", courseId, query, e);
            return List.of();
        }
    }

    private String buildVectorDocumentContent(SourceType sourceType, String sourceName, String chunkContent) {
        StringBuilder sb = new StringBuilder();
        if (sourceName != null && !sourceName.isBlank()) {
            sb.append("来源：").append(sourceName).append('\n');
        }
        sb.append("类型：").append(sourceType.name()).append('\n');
        sb.append(chunkContent);
        return sb.toString();
    }

    private List<KnowledgeChunk> findKeywordMatches(List<KnowledgeChunk> chunks, String query, int limit) {
        if (chunks == null || chunks.isEmpty()) {
            return List.of();
        }

        String normalizedQuery = normalizeMatchText(query);
        if (normalizedQuery.isBlank()) {
            return List.of();
        }

        List<KnowledgeChunk> sourceNameMatches = chunks.stream()
                .filter(chunk -> sourceNameMatches(chunk.getSourceName(), normalizedQuery))
                .sorted(Comparator
                        .comparing(KnowledgeChunk::getSourceId, Comparator.nullsLast(Long::compareTo))
                        .thenComparing(KnowledgeChunk::getChunkIndex, Comparator.nullsLast(Integer::compareTo)))
                .limit(limit)
                .toList();

        if (!sourceNameMatches.isEmpty()) {
            return sourceNameMatches;
        }

        return chunks.stream()
                .filter(chunk -> normalizeMatchText(chunk.getContent()).contains(normalizedQuery))
                .sorted(Comparator
                        .comparing(KnowledgeChunk::getSourceId, Comparator.nullsLast(Long::compareTo))
                        .thenComparing(KnowledgeChunk::getChunkIndex, Comparator.nullsLast(Integer::compareTo)))
                .limit(limit)
                .toList();
    }

    private boolean sourceNameMatches(String sourceName, String normalizedQuery) {
        String normalizedSourceName = normalizeMatchText(stripExtension(sourceName));
        return !normalizedSourceName.isBlank()
                && (normalizedQuery.contains(normalizedSourceName)
                || normalizedSourceName.contains(normalizedQuery));
    }

    private List<KnowledgeChunk> mergeAndLimit(List<KnowledgeChunk> primary,
                                               List<KnowledgeChunk> secondary,
                                               int limit) {
        Map<String, KnowledgeChunk> merged = new LinkedHashMap<>();
        addChunks(merged, primary);
        addChunks(merged, secondary);
        return merged.values().stream().limit(limit).toList();
    }

    private void addChunks(Map<String, KnowledgeChunk> merged, List<KnowledgeChunk> chunks) {
        if (chunks == null) {
            return;
        }
        for (KnowledgeChunk chunk : chunks) {
            merged.putIfAbsent(chunkKey(chunk), chunk);
        }
    }

    private String chunkKey(KnowledgeChunk chunk) {
        if (chunk.getQdrantPointId() != null && !chunk.getQdrantPointId().isBlank()) {
            return chunk.getQdrantPointId();
        }
        if (chunk.getId() != null) {
            return "id:" + chunk.getId();
        }
        return chunk.getSourceType() + ":" + chunk.getSourceId() + ":" + chunk.getChunkIndex();
    }

    private String stripExtension(String fileName) {
        if (fileName == null) {
            return "";
        }
        int lastDot = fileName.lastIndexOf('.');
        if (lastDot <= 0) {
            return fileName;
        }
        return fileName.substring(0, lastDot);
    }

    private String normalizeMatchText(String text) {
        if (text == null) {
            return "";
        }
        return text.toLowerCase()
                .replaceAll("[\\s\\p{Punct}，。！？、；：“”‘’（）【】《》]+", "");
    }

    // ==================== 课程知识库重建 ====================

    /** 重建课程的全部知识库（先清空再全量索引）。教师操作。 */
    @Transactional
    public void rebuildCourseKnowledge(Long courseId,
                                       List<Material> materials,
                                       List<Assignment> assignments,
                                       List<Topic> topics) {
        // 清空现有索引
        List<KnowledgeChunk> existing = knowledgeChunkRepository.findByCourseId(courseId);
        if (!existing.isEmpty()) {
            List<String> pointIds = existing.stream()
                    .map(KnowledgeChunk::getQdrantPointId)
                    .filter(id -> id != null && !id.isBlank())
                    .toList();
            if (!pointIds.isEmpty()) {
                try {
                    vectorStore.delete(pointIds);
                } catch (Exception e) {
                    log.warn("删除 Qdrant 向量失败: courseId={}, count={}", courseId, pointIds.size(), e);
                }
            }
            knowledgeChunkRepository.deleteAll(existing);
            log.info("已清空课程 [id={}] 知识库，共 {} 块", courseId, existing.size());
        }

        // 全量重建（同步，不通过 @Async 避免并发问题）
        if (materials != null) {
            for (Material m : materials) {
                try {
                    String text = buildMaterialText(m);
                    if (!text.isBlank()) {
                        indexText(m.getCourseId(), SourceType.MATERIAL, m.getId(), m.getTitle(), text);
                    }
                } catch (Exception e) {
                    log.error("重建索引失败: material id={}", m.getId(), e);
                }
            }
        }
        if (assignments != null) {
            for (Assignment a : assignments) {
                try {
                    String text = buildAssignmentText(a);
                    if (!text.isBlank()) {
                        indexText(a.getCourseId(), SourceType.ASSIGNMENT, a.getId(), a.getTitle(), text);
                    }
                } catch (Exception e) {
                    log.error("重建索引失败: assignment id={}", a.getId(), e);
                }
            }
        }
        if (topics != null) {
            for (Topic t : topics) {
                try {
                    String text = buildTopicText(t);
                    if (!text.isBlank()) {
                        indexText(t.getCourseId(), SourceType.TOPIC, t.getId(), t.getTitle(), text);
                    }
                } catch (Exception e) {
                    log.error("重建索引失败: topic id={}", t.getId(), e);
                }
            }
        }

        log.info("课程 [id={}] 知识库重建完成", courseId);
    }

    // ==================== 文本构造 ====================

    /** 从 Material 构造待索引文本 */
    private String buildMaterialText(Material material) {
        StringBuilder sb = new StringBuilder();
        sb.append("资料：").append(material.getTitle()).append("\n");

        if (material.getType() == MaterialType.FILE && material.getFileUrl() != null) {
            sb.append("[文件]\n");
            try {
                String extension = FileService.extractExtension(material.getTitle()).toLowerCase();
                if (TEXT_EXTRACTABLE.contains(extension)) {
                    String objectPath = FileService.normalizeObjectPath(material.getFileUrl());
                    byte[] bytes = fileService.downloadBytes(objectPath);
                    String extracted = textExtractionService.extractFromBytes(bytes, extension);
                    sb.append(extracted);
                } else {
                    sb.append("文件类型: .").append(extension).append("（不支持文本提取）");
                }
            } catch (Exception e) {
                log.warn("资料文件下载/提取失败: materialId={}, fileUrl={}", material.getId(), material.getFileUrl(), e);
                sb.append("[文件内容无法提取]");
            }
        } else if (material.getType() == MaterialType.LINK && material.getLinkUrl() != null) {
            sb.append("[链接] ").append(material.getLinkUrl());
        }

        return sb.toString();
    }

    /** 从 Assignment 构造待索引文本 */
    private String buildAssignmentText(Assignment assignment) {
        StringBuilder sb = new StringBuilder();
        sb.append("作业：").append(assignment.getTitle()).append("\n");
        sb.append("满分：").append(assignment.getMaxScore()).append("分\n");
        if (assignment.getContent() != null && !assignment.getContent().isBlank()) {
            sb.append("要求：\n").append(assignment.getContent());
        }
        return sb.toString();
    }

    /** 从 Topic 构造待索引文本 */
    private String buildTopicText(Topic topic) {
        StringBuilder sb = new StringBuilder();
        sb.append("讨论话题：").append(topic.getTitle()).append("\n");
        if (topic.getContent() != null && !topic.getContent().isBlank()) {
            sb.append(topic.getContent());
        }
        return sb.toString();
    }

    // ==================== 文本分块 ====================

    /** 按段落分块，每块最多 {@link #MAX_CHUNK_SIZE} 字符，块间重叠 {@link #CHUNK_OVERLAP} 字符 */
    List<String> chunkText(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }

        List<String> paragraphs = splitByParagraph(text);
        List<String> chunks = new ArrayList<>();
        StringBuilder current = new StringBuilder();

        for (String para : paragraphs) {
            String trimmed = para.trim();
            if (trimmed.isEmpty()) {
                continue;
            }

            // 单段超出块大小：强制按句子拆分
            if (trimmed.length() > MAX_CHUNK_SIZE) {
                if (current.length() > 0) {
                    chunks.add(current.toString().trim());
                    String overlapText = current.substring(Math.max(0, current.length() - CHUNK_OVERLAP));
                    current = new StringBuilder(overlapText);
                }
                chunks.addAll(splitLongParagraph(trimmed));
                continue;
            }

            if (current.length() + trimmed.length() > MAX_CHUNK_SIZE && current.length() > 0) {
                chunks.add(current.toString().trim());
                String overlapText = current.substring(Math.max(0, current.length() - CHUNK_OVERLAP));
                current = new StringBuilder(overlapText);
            }

            if (current.length() > 0) {
                current.append("\n\n");
            }
            current.append(trimmed);
        }

        if (current.length() > 0) {
            chunks.add(current.toString().trim());
        }

        return chunks;
    }

    /** 将长段落按句子拆分为多个块 */
    private List<String> splitLongParagraph(String text) {
        List<String> chunks = new ArrayList<>();
        StringBuilder current = new StringBuilder();

        String[] sentences = text.split("(?<=[。！？\\n])");
        for (String sentence : sentences) {
            if (current.length() + sentence.length() > MAX_CHUNK_SIZE && current.length() > 0) {
                chunks.add(current.toString().trim());
                String overlapText = current.substring(Math.max(0, current.length() - CHUNK_OVERLAP));
                current = new StringBuilder(overlapText);
            }
            current.append(sentence);
        }

        if (current.length() > 0) {
            chunks.add(current.toString().trim());
        }

        return chunks;
    }

    /** 按双换行拆分段落 */
    private List<String> splitByParagraph(String text) {
        String[] parts = text.split("\\n\\s*\\n");
        List<String> result = new ArrayList<>();
        for (String part : parts) {
            if (!part.trim().isEmpty()) {
                result.add(part.trim());
            }
        }
        return result;
    }
}
