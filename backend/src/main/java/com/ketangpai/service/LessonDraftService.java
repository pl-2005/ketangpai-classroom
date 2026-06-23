package com.ketangpai.service;

import com.ketangpai.model.entity.LessonDraft;
import com.ketangpai.exception.BusinessException;
import com.ketangpai.model.enums.DraftType;
import com.ketangpai.repository.LessonDraftRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 备课区服务（教师专属）
 */
@Service
@RequiredArgsConstructor
public class LessonDraftService {

    private final LessonDraftRepository draftRepository;

    public List<LessonDraft> list(Long userId, String typeFilter) {
        if (typeFilter != null) {
            return draftRepository.findByUserIdAndTypeOrderByUpdateTimeDesc(userId, DraftType.valueOf(typeFilter));
        }
        return draftRepository.findByUserIdOrderByUpdateTimeDesc(userId);
    }

    @Transactional
    public LessonDraft save(Long userId, DraftType type, String title, String contentJson) {
        LessonDraft draft = LessonDraft.builder()
                .userId(userId)
                .type(type)
                .title(title)
                .contentJson(contentJson)
                .build();
        return draftRepository.save(draft);
    }

    @Transactional
    public LessonDraft update(Long draftId, Long userId, String title, String contentJson) {
        LessonDraft draft = draftRepository.findById(draftId)
                .orElseThrow(() -> new BusinessException(404, "草稿不存在"));
        if (!draft.getUserId().equals(userId)) {
            throw new BusinessException(403, "无权编辑该草稿");
        }
        if (title != null) draft.setTitle(title);
        if (contentJson != null) draft.setContentJson(contentJson);
        return draftRepository.save(draft);
    }

    @Transactional
    public void publish(Long draftId, Long userId, Long courseId) {
        LessonDraft draft = draftRepository.findById(draftId)
                .orElseThrow(() -> new BusinessException(404, "草稿不存在"));
        if (!draft.getUserId().equals(userId)) {
            throw new BusinessException(403, "无权发布该草稿");
        }

        // TODO: 根据 draft.type 反序列化 contentJson 并调用对应的创建逻辑
        //   ASSIGNMENT → AssignmentService.create(...)
        //   MATERIAL   → MaterialService.create(...)
        //   TOPIC      → TopicService.create(...)

        // 发布后删除草稿
        draft.setDeleted(true);
        draftRepository.save(draft);
    }

    @Transactional
    public void delete(Long draftId, Long userId) {
        LessonDraft draft = draftRepository.findById(draftId)
                .orElseThrow(() -> new BusinessException(404, "草稿不存在"));
        if (!draft.getUserId().equals(userId)) {
            throw new BusinessException(403, "无权删除该草稿");
        }
        draft.setDeleted(true);
        draftRepository.save(draft);
    }
}
