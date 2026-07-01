package com.ketangpai.service;

import com.ketangpai.model.entity.Material;
import com.ketangpai.model.enums.MaterialType;
import com.ketangpai.model.entity.KnowledgeChunk;
import com.ketangpai.model.enums.SourceType;
import com.ketangpai.repository.KnowledgeChunkRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KnowledgeBaseServiceTest {

    @Test
    @SuppressWarnings("unchecked")
    void indexMaterialDownloadsUploadedFileByMinioObjectPath() {
        EmbeddingModel embeddingModel = mock(EmbeddingModel.class);
        VectorStore vectorStore = mock(VectorStore.class);
        KnowledgeChunkRepository knowledgeChunkRepository = mock(KnowledgeChunkRepository.class);
        FileService fileService = mock(FileService.class);
        TextExtractionService textExtractionService = mock(TextExtractionService.class);
        KnowledgeBaseService service = new KnowledgeBaseService(
                embeddingModel,
                vectorStore,
                knowledgeChunkRepository,
                fileService,
                textExtractionService
        );

        byte[] fileBytes = "course notes".getBytes(StandardCharsets.UTF_8);
        when(fileService.downloadBytes(any())).thenReturn(fileBytes);
        when(textExtractionService.extractFromBytes(eq(fileBytes), eq("txt")))
                .thenReturn("向量数据库索引内容");

        Material material = Material.builder()
                .courseId(42L)
                .title("demo.txt")
                .type(MaterialType.FILE)
                .fileUrl("/api/files/files/2026/07/demo.txt")
                .build();
        material.setId(7L);

        service.indexMaterial(material);

        verify(fileService).downloadBytes("files/2026/07/demo.txt");

        ArgumentCaptor<List<Document>> documentsCaptor = ArgumentCaptor.forClass(List.class);
        verify(vectorStore).add(documentsCaptor.capture());
        List<Document> documents = documentsCaptor.getValue();
        assertEquals(1, documents.size());
        assertTrue(documents.getFirst().getText().contains("向量数据库索引内容"));
        assertTrue(documents.getFirst().getText().contains("demo.txt"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void indexMaterialWritesVectorStoreInEmbeddingBatchesOfTen() {
        EmbeddingModel embeddingModel = mock(EmbeddingModel.class);
        VectorStore vectorStore = mock(VectorStore.class);
        KnowledgeChunkRepository knowledgeChunkRepository = mock(KnowledgeChunkRepository.class);
        FileService fileService = mock(FileService.class);
        TextExtractionService textExtractionService = mock(TextExtractionService.class);
        KnowledgeBaseService service = new KnowledgeBaseService(
                embeddingModel,
                vectorStore,
                knowledgeChunkRepository,
                fileService,
                textExtractionService
        );

        byte[] fileBytes = "large course notes".getBytes(StandardCharsets.UTF_8);
        String extractedText = IntStream.range(0, 21)
                .mapToObj(i -> "第" + i + "段 " + "课程资料内容".repeat(35))
                .collect(Collectors.joining("\n\n"));
        when(fileService.downloadBytes(any())).thenReturn(fileBytes);
        when(textExtractionService.extractFromBytes(eq(fileBytes), eq("txt")))
                .thenReturn(extractedText);

        Material material = Material.builder()
                .courseId(42L)
                .title("large.txt")
                .type(MaterialType.FILE)
                .fileUrl("/api/files/files/2026/07/large.txt")
                .build();
        material.setId(8L);

        service.indexMaterial(material);

        ArgumentCaptor<List<Document>> documentsCaptor = ArgumentCaptor.forClass(List.class);
        verify(vectorStore, atLeastOnce()).add(documentsCaptor.capture());

        List<List<Document>> batches = documentsCaptor.getAllValues();
        assertTrue(batches.size() > 1);
        assertTrue(batches.stream().allMatch(batch -> batch.size() <= 10));
        assertTrue(batches.stream().mapToInt(List::size).sum() > 10);
    }

    @Test
    @SuppressWarnings("unchecked")
    void rebuildCourseKnowledgeDeletesExistingVectorsByIdList() {
        EmbeddingModel embeddingModel = mock(EmbeddingModel.class);
        VectorStore vectorStore = mock(VectorStore.class);
        KnowledgeChunkRepository knowledgeChunkRepository = mock(KnowledgeChunkRepository.class);
        FileService fileService = mock(FileService.class);
        TextExtractionService textExtractionService = mock(TextExtractionService.class);
        KnowledgeBaseService service = new KnowledgeBaseService(
                embeddingModel,
                vectorStore,
                knowledgeChunkRepository,
                fileService,
                textExtractionService
        );

        String pointId = "4b4a55fe-2616-4cf0-b708-0b3d15d4fd96";
        KnowledgeChunk existing = KnowledgeChunk.builder()
                .courseId(42L)
                .sourceType(SourceType.MATERIAL)
                .sourceId(5L)
                .sourceName("demo.txt")
                .chunkIndex(0)
                .content("old content")
                .qdrantPointId(pointId)
                .build();
        when(knowledgeChunkRepository.findByCourseId(42L)).thenReturn(List.of(existing));

        service.rebuildCourseKnowledge(42L, null, null, null);

        ArgumentCaptor<List<String>> idsCaptor = ArgumentCaptor.forClass(List.class);
        verify(vectorStore).delete(idsCaptor.capture());
        assertEquals(List.of(pointId), idsCaptor.getValue());
        verify(vectorStore, never()).delete(pointId);
    }

    @Test
    void searchRelevantFallsBackToSourceNameWhenVectorSearchMisses() {
        EmbeddingModel embeddingModel = mock(EmbeddingModel.class);
        VectorStore vectorStore = mock(VectorStore.class);
        KnowledgeChunkRepository knowledgeChunkRepository = mock(KnowledgeChunkRepository.class);
        FileService fileService = mock(FileService.class);
        TextExtractionService textExtractionService = mock(TextExtractionService.class);
        KnowledgeBaseService service = new KnowledgeBaseService(
                embeddingModel,
                vectorStore,
                knowledgeChunkRepository,
                fileService,
                textExtractionService
        );

        KnowledgeChunk targetChunk = KnowledgeChunk.builder()
                .courseId(3L)
                .sourceType(SourceType.MATERIAL)
                .sourceId(6L)
                .sourceName("卓越软件工程师技术基础期末工程考核报告.docx")
                .chunkIndex(1)
                .content("目录\n1 绪论与行业分析\n2 业务与需求分析")
                .qdrantPointId("target-point")
                .build();
        KnowledgeChunk otherChunk = KnowledgeChunk.builder()
                .courseId(3L)
                .sourceType(SourceType.MATERIAL)
                .sourceId(7L)
                .sourceName("其他资料.docx")
                .chunkIndex(0)
                .content("无关内容")
                .qdrantPointId("other-point")
                .build();

        when(knowledgeChunkRepository.findByCourseId(3L))
                .thenReturn(List.of(otherChunk, targetChunk));
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of());

        List<KnowledgeChunk> results = service.searchRelevant(
                3L,
                "卓越软件工程师技术基础期末工程考核报告相关问题",
                5
        );

        assertEquals(List.of(targetChunk), results);
    }
}
