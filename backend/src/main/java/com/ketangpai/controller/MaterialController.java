package com.ketangpai.controller;

import com.ketangpai.common.Result;
import com.ketangpai.model.entity.Material;
import com.ketangpai.model.entity.MaterialFolder;
import com.ketangpai.model.enums.MaterialType;
import com.ketangpai.security.CurrentUserId;
import com.ketangpai.service.MaterialService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 资料管理 Controller
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class MaterialController {

    private final MaterialService materialService;

    @GetMapping("/courses/{courseId}/materials/tree")
    public Result<List<Map<String, Object>>> getTree(@CurrentUserId Long userId, @PathVariable Long courseId) {
        return Result.ok(materialService.getTree(courseId, userId));
    }

    @PostMapping("/materials/folders")
    public Result<MaterialFolder> createFolder(@CurrentUserId Long userId, @RequestBody Map<String, Object> body) {
        return Result.ok(materialService.createFolder(
                ((Number) body.get("courseId")).longValue(),
                userId,
                body.get("parentId") != null ? ((Number) body.get("parentId")).longValue() : null,
                (String) body.get("name")));
    }

    @PostMapping("/materials")
    public Result<Material> create(@CurrentUserId Long userId, @RequestBody Map<String, Object> body) {
        return Result.ok(materialService.create(
                ((Number) body.get("courseId")).longValue(),
                userId,
                body.get("folderId") != null ? ((Number) body.get("folderId")).longValue() : null,
                (String) body.get("title"),
                MaterialType.valueOf((String) body.get("type")),
                (String) body.get("fileUrl"),
                body.get("fileSize") != null ? ((Number) body.get("fileSize")).longValue() : null,
                (String) body.get("linkUrl")));
    }

    @PutMapping("/materials/{materialId}/move")
    public Result<Void> move(@CurrentUserId Long userId,
                              @PathVariable Long materialId,
                              @RequestBody Map<String, Object> body) {
        materialService.move(materialId, userId,
                body.get("targetFolderId") != null ? ((Number) body.get("targetFolderId")).longValue() : null);
        return Result.ok();
    }

    @PutMapping("/materials/{materialId}")
    public Result<Material> update(@CurrentUserId Long userId,
                                    @PathVariable Long materialId,
                                    @RequestBody Map<String, Object> body) {
        return Result.ok(materialService.update(materialId, userId,
                (String) body.get("title"),
                body.get("sortOrder") != null ? ((Number) body.get("sortOrder")).intValue() : null));
    }

    @DeleteMapping("/materials/{materialId}")
    public Result<Void> deleteMaterial(@CurrentUserId Long userId, @PathVariable Long materialId) {
        materialService.deleteMaterial(materialId, userId);
        return Result.ok();
    }

    @DeleteMapping("/materials/folders/{folderId}")
    public Result<Void> deleteFolder(@CurrentUserId Long userId, @PathVariable Long folderId) {
        materialService.deleteFolder(folderId, userId);
        return Result.ok();
    }
}
