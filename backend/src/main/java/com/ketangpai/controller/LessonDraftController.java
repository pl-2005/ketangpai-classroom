package com.ketangpai.controller;

import com.ketangpai.common.Result;
import com.ketangpai.model.entity.LessonDraft;
import com.ketangpai.model.enums.DraftType;
import com.ketangpai.service.LessonDraftService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 备课区 Controller（教师专属）
 */
@RestController
@RequestMapping("/api/drafts")
@RequiredArgsConstructor
public class LessonDraftController {

    private final LessonDraftService draftService;

    @GetMapping
    public Result<List<LessonDraft>> list(@RequestParam(required = false) String type) {
        Long userId = 1L;
        return Result.ok(draftService.list(userId, type));
    }

    @PostMapping
    public Result<LessonDraft> save(@RequestBody Map<String, Object> body) {
        Long userId = 1L;
        return Result.ok(draftService.save(userId,
                DraftType.valueOf((String) body.get("type")),
                (String) body.get("title"),
                (String) body.get("contentJson")));
    }

    @PutMapping("/{draftId}")
    public Result<LessonDraft> update(@PathVariable Long draftId, @RequestBody Map<String, String> body) {
        Long userId = 1L;
        return Result.ok(draftService.update(draftId, userId,
                body.get("title"), body.get("contentJson")));
    }

    @PostMapping("/{draftId}/publish")
    public Result<Void> publish(@PathVariable Long draftId, @RequestBody Map<String, Object> body) {
        Long userId = 1L;
        draftService.publish(draftId, userId, ((Number) body.get("courseId")).longValue());
        return Result.ok();
    }

    @DeleteMapping("/{draftId}")
    public Result<Void> delete(@PathVariable Long draftId) {
        Long userId = 1L;
        draftService.delete(draftId, userId);
        return Result.ok();
    }
}
