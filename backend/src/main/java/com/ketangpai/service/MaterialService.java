package com.ketangpai.service;

import com.ketangpai.model.entity.Material;
import com.ketangpai.model.entity.MaterialFolder;
import com.ketangpai.exception.BusinessException;
import com.ketangpai.model.enums.MaterialType;
import com.ketangpai.repository.CourseMemberRepository;
import com.ketangpai.repository.MaterialFolderRepository;
import com.ketangpai.repository.MaterialRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 资料管理服务
 */
@Service
public class MaterialService extends BaseService {

    private final MaterialFolderRepository folderRepository;
    private final MaterialRepository materialRepository;
    private final FileService fileService;
    private final KnowledgeBaseService knowledgeBaseService;

    public MaterialService(CourseMemberRepository courseMemberRepository,
                           MaterialFolderRepository folderRepository,
                           MaterialRepository materialRepository,
                           FileService fileService,
                           KnowledgeBaseService knowledgeBaseService) {
        super(courseMemberRepository);
        this.folderRepository = folderRepository;
        this.materialRepository = materialRepository;
        this.fileService = fileService;
        this.knowledgeBaseService = knowledgeBaseService;
    }

    /** 构建课程资料目录树 */
    public List<Map<String, Object>> getTree(Long courseId, Long userId) {
        getMemberOrThrow(courseId, userId);

        List<MaterialFolder> allFolders = folderRepository.findByCourseIdOrderBySortOrder(courseId);
        List<Material> allMaterials = materialRepository.findByCourseIdOrderBySortOrder(courseId);

        // 构建 folderId → children folders 映射
        Map<Long, List<MaterialFolder>> folderChildren = new LinkedHashMap<>();
        Map<Long, List<Material>> folderMaterials = new LinkedHashMap<>();
        List<MaterialFolder> roots = new ArrayList<>();
        List<Material> rootMaterials = new ArrayList<>();

        for (MaterialFolder f : allFolders) {
            if (f.getParentId() == null) {
                roots.add(f);
            } else {
                folderChildren.computeIfAbsent(f.getParentId(), k -> new ArrayList<>()).add(f);
            }
        }

        for (Material m : allMaterials) {
            if (m.getFolderId() == null) {
                rootMaterials.add(m);
            } else {
                folderMaterials.computeIfAbsent(m.getFolderId(), k -> new ArrayList<>()).add(m);
            }
        }

        // 递归构建
        List<Map<String, Object>> result = new ArrayList<>();
        for (MaterialFolder root : roots) {
            result.add(buildFolderNode(root, folderChildren, folderMaterials));
        }
        if (!rootMaterials.isEmpty()) {
            Map<String, Object> rootNode = new LinkedHashMap<>();
            rootNode.put("folder", null);
            rootNode.put("materials", rootMaterials);
            rootNode.put("children", new ArrayList<>());
            result.add(rootNode);
        }

        return result;
    }

    private Map<String, Object> buildFolderNode(MaterialFolder folder,
                                                 Map<Long, List<MaterialFolder>> folderChildren,
                                                 Map<Long, List<Material>> folderMaterials) {
        Map<String, Object> node = new LinkedHashMap<>();
        node.put("folder", folder);
        node.put("materials", folderMaterials.getOrDefault(folder.getId(), List.of()));

        List<Map<String, Object>> children = new ArrayList<>();
        for (MaterialFolder child : folderChildren.getOrDefault(folder.getId(), List.of())) {
            children.add(buildFolderNode(child, folderChildren, folderMaterials));
        }
        node.put("children", children);
        return node;
    }

    @Transactional
    public MaterialFolder createFolder(Long courseId, Long userId, Long parentId, String name) {
        checkTeacher(courseId, userId);
        MaterialFolder folder = MaterialFolder.builder()
                .courseId(courseId)
                .parentId(parentId)
                .name(name)
                .build();
        return folderRepository.save(folder);
    }

    @Transactional
    public Material create(Long courseId, Long userId, Long folderId, String title,
                           MaterialType type, String fileUrl, Long fileSize, String linkUrl, Long fileId) {
        checkTeacher(courseId, userId);

        Material material = Material.builder()
                .courseId(courseId)
                .folderId(folderId)
                .title(title)
                .type(type)
                .fileUrl(fileUrl)
                .fileSize(fileSize)
                .linkUrl(linkUrl)
                .build();

        Material saved = materialRepository.save(material);

        // 关联临时上传文件，防止被定时清理
        if (fileId != null) {
            fileService.associateFile(fileId);
        }

        // 异步索引到知识库
        knowledgeBaseService.indexMaterial(saved);

        return saved;
    }

    /**
     * 生成资料文件的预签名下载 URL（需课程成员身份）
     */
    public String getDownloadUrl(Long materialId, Long userId) {
        Material material = materialRepository.findById(materialId)
                .orElseThrow(() -> new BusinessException(404, "资料不存在"));
        getMemberOrThrow(material.getCourseId(), userId);
        return fileService.getPresignedUrlByPath(material.getFileUrl());
    }

    @Transactional
    public void move(Long materialId, Long userId, Long targetFolderId) {
        Material material = materialRepository.findById(materialId)
                .orElseThrow(() -> new BusinessException(404, "资料不存在"));
        checkTeacher(material.getCourseId(), userId);

        // 校验目标文件夹属于同一课程
        if (targetFolderId != null) {
            MaterialFolder targetFolder = folderRepository.findById(targetFolderId)
                    .orElseThrow(() -> new BusinessException(404, "目标文件夹不存在"));
            if (!targetFolder.getCourseId().equals(material.getCourseId())) {
                throw new BusinessException(400, "目标文件夹不属于同一课程");
            }
        }

        material.setFolderId(targetFolderId);
        materialRepository.save(material);
    }

    @Transactional
    public Material update(Long materialId, Long userId, String title, Integer sortOrder) {
        Material material = materialRepository.findById(materialId)
                .orElseThrow(() -> new BusinessException(404, "资料不存在"));
        checkTeacher(material.getCourseId(), userId);
        if (title != null) material.setTitle(title);
        if (sortOrder != null) material.setSortOrder(sortOrder);
        return materialRepository.save(material);
    }

    @Transactional
    public void deleteMaterial(Long materialId, Long userId) {
        Material material = materialRepository.findById(materialId)
                .orElseThrow(() -> new BusinessException(404, "资料不存在"));
        checkTeacher(material.getCourseId(), userId);
        material.setDeleted(true);
        materialRepository.save(material);

        // 异步清理知识库索引
        knowledgeBaseService.deleteBySource(
                com.ketangpai.model.enums.SourceType.MATERIAL, materialId);
    }

    @Transactional
    public void deleteFolder(Long folderId, Long userId) {
        MaterialFolder folder = folderRepository.findById(folderId)
                .orElseThrow(() -> new BusinessException(404, "文件夹不存在"));
        checkTeacher(folder.getCourseId(), userId);

        // 软删除文件夹本身
        folder.setDeleted(true);
        folderRepository.save(folder);

        // 递归软删除子文件夹和资料
        deleteFolderRecursively(folderId);
    }

    private void deleteFolderRecursively(Long folderId) {
        List<MaterialFolder> children = folderRepository.findByParentIdOrderBySortOrder(folderId);
        for (MaterialFolder child : children) {
            child.setDeleted(true);
            folderRepository.save(child);
            deleteFolderRecursively(child.getId());
        }
        List<Material> materials = materialRepository.findByFolderIdOrderBySortOrder(folderId);
        for (Material m : materials) {
            m.setDeleted(true);
            materialRepository.save(m);
        }
    }
}
