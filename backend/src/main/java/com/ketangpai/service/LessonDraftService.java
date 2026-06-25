package com.ketangpai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ketangpai.model.entity.LessonDraft;
import com.ketangpai.exception.BusinessException;
import com.ketangpai.model.entity.User;
import com.ketangpai.model.enums.DraftType;
import com.ketangpai.model.enums.MaterialType;
import com.ketangpai.model.enums.UserRole;
import com.ketangpai.repository.LessonDraftRepository;
import com.ketangpai.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 备课区服务（教师专属）
 * <p>
 * 仅全局角色为 TEACHER 的用户可使用备课区。
 */
@Slf4j
@Service
public class LessonDraftService {

    private final LessonDraftRepository draftRepository;
    private final UserRepository userRepository;
    private final AssignmentService assignmentService;
    private final MaterialService materialService;
    private final TopicService topicService;
    private final ObjectMapper objectMapper;

    public LessonDraftService(LessonDraftRepository draftRepository,
                              UserRepository userRepository,
                              AssignmentService assignmentService,
                              MaterialService materialService,
                              TopicService topicService,
                              ObjectMapper objectMapper) {
        this.draftRepository = draftRepository;
        this.userRepository = userRepository;
        this.assignmentService = assignmentService;
        this.materialService = materialService;
        this.topicService = topicService;
        this.objectMapper = objectMapper;
    }

    /** 确保用户是全局教师 */
    private void checkTeacherRole(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(404, "用户不存在"));
        if (user.getRole() != UserRole.TEACHER) {
            throw new BusinessException(403, "仅教师可使用备课区");
        }
    }

    public List<LessonDraft> list(Long userId, String typeFilter) {
        checkTeacherRole(userId);
        if (typeFilter != null) {
            return draftRepository.findByUserIdAndTypeOrderByUpdateTimeDesc(userId, DraftType.valueOf(typeFilter));
        }
        return draftRepository.findByUserIdOrderByUpdateTimeDesc(userId);
    }

    @Transactional
    public LessonDraft save(Long userId, DraftType type, String title, String contentJson) {
        checkTeacherRole(userId);
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
        checkTeacherRole(userId);
        LessonDraft draft = draftRepository.findById(draftId)
                .orElseThrow(() -> new BusinessException(404, "草稿不存在"));
        if (!draft.getUserId().equals(userId)) {
            throw new BusinessException(403, "无权编辑该草稿");
        }
        if (title != null) draft.setTitle(title);
        if (contentJson != null) draft.setContentJson(contentJson);
        return draftRepository.save(draft);
    }

    /**
     * 发布草稿：根据类型反序列化 contentJson，创建目标实体，成功后删除草稿。
     */
    @Transactional
    public void publish(Long draftId, Long userId, Long courseId) {
        checkTeacherRole(userId);
        LessonDraft draft = draftRepository.findById(draftId)
                .orElseThrow(() -> new BusinessException(404, "草稿不存在"));
        if (!draft.getUserId().equals(userId)) {
            throw new BusinessException(403, "无权发布该草稿");
        }

        // 先创建目标实体，成功后删除草稿（避免数据丢失）
        try {
            switch (draft.getType()) {
                case ASSIGNMENT -> publishAssignment(userId, courseId, draft.getContentJson());
                case MATERIAL   -> publishMaterial(userId, courseId, draft.getContentJson());
                case TOPIC      -> publishTopic(userId, courseId, draft.getContentJson());
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("草稿发布失败: draftId={}, type={}", draftId, draft.getType(), e);
            throw new BusinessException(500, "草稿发布失败: " + e.getMessage());
        }

        // 创建成功后才标记删除
        draft.setDeleted(true);
        draftRepository.save(draft);
        log.info("草稿发布成功: draftId={}, type={}, courseId={}", draftId, draft.getType(), courseId);
    }

    @Transactional
    public void delete(Long draftId, Long userId) {
        checkTeacherRole(userId);
        LessonDraft draft = draftRepository.findById(draftId)
                .orElseThrow(() -> new BusinessException(404, "草稿不存在"));
        if (!draft.getUserId().equals(userId)) {
            throw new BusinessException(403, "无权删除该草稿");
        }
        draft.setDeleted(true);
        draftRepository.save(draft);
    }

    // ==================== 发布辅助方法 ====================

    @SuppressWarnings("unchecked")
    private void publishAssignment(Long userId, Long courseId, String contentJson) throws Exception {
        Map<String, Object> data = objectMapper.readValue(contentJson, Map.class);
        assignmentService.create(
                userId,
                (String) data.get("title"),
                (String) data.get("content"),
                courseId,
                parseDateTime(data.get("deadline")),
                data.get("maxScore") != null ? ((Number) data.get("maxScore")).intValue() : null,
                data.get("allowResubmit") instanceof Boolean ? (Boolean) data.get("allowResubmit") : null,
                parseLongList(data.get("attachmentIds"))
        );
    }

    @SuppressWarnings("unchecked")
    private void publishMaterial(Long userId, Long courseId, String contentJson) throws Exception {
        Map<String, Object> data = objectMapper.readValue(contentJson, Map.class);
        materialService.create(
                courseId,
                userId,
                data.get("folderId") != null ? ((Number) data.get("folderId")).longValue() : null,
                (String) data.get("title"),
                MaterialType.valueOf((String) data.get("type")),
                (String) data.get("fileUrl"),
                data.get("fileSize") != null ? ((Number) data.get("fileSize")).longValue() : null,
                (String) data.get("linkUrl"),
                data.get("fileId") != null ? ((Number) data.get("fileId")).longValue() : null
        );
    }

    @SuppressWarnings("unchecked")
    private void publishTopic(Long userId, Long courseId, String contentJson) throws Exception {
        Map<String, Object> data = objectMapper.readValue(contentJson, Map.class);
        topicService.create(
                courseId,
                userId,
                (String) data.get("title"),
                (String) data.get("content"),
                (Boolean) data.get("isAnonymous")
        );
    }

    private static LocalDateTime parseDateTime(Object value) {
        if (value == null) return null;
        try {
            return LocalDateTime.parse(value.toString());
        } catch (Exception e) {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private static List<Long> parseLongList(Object value) {
        if (value == null) return null;
        if (value instanceof List<?> list) {
            return list.stream()
                    .map(item -> item instanceof Number ? ((Number) item).longValue() : null)
                    .filter(java.util.Objects::nonNull)
                    .toList();
        }
        return null;
    }
}
