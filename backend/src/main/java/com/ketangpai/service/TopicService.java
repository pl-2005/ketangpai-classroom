package com.ketangpai.service;

import com.ketangpai.model.entity.Topic;
import com.ketangpai.model.entity.TopicReply;
import com.ketangpai.model.entity.User;
import com.ketangpai.exception.BusinessException;
import com.ketangpai.model.enums.NotificationType;
import com.ketangpai.model.enums.TopicStatus;
import com.ketangpai.repository.CourseMemberRepository;
import com.ketangpai.repository.TopicReplyRepository;
import com.ketangpai.repository.TopicRepository;
import com.ketangpai.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 话题讨论服务
 */
@Service
public class TopicService extends BaseService {

    private final TopicRepository topicRepository;
    private final TopicReplyRepository replyRepository;
    private final UserRepository userRepository;
    private final KnowledgeBaseService knowledgeBaseService;
    private final NotificationService notificationService;

    public TopicService(CourseMemberRepository courseMemberRepository,
                        TopicRepository topicRepository,
                        TopicReplyRepository replyRepository,
                        UserRepository userRepository,
                        KnowledgeBaseService knowledgeBaseService,
                        NotificationService notificationService) {
        super(courseMemberRepository);
        this.topicRepository = topicRepository;
        this.replyRepository = replyRepository;
        this.userRepository = userRepository;
        this.knowledgeBaseService = knowledgeBaseService;
        this.notificationService = notificationService;
    }

    public List<Topic> listByCourse(Long courseId, Long userId) {
        getMemberOrThrow(courseId, userId);
        List<Topic> topics = topicRepository.findByCourseIdOrderByStatusDescCreateTimeDesc(courseId);
        topics.forEach(topic -> {
            populateAuthorName(topic);
            anonymizeTopic(topic);
        });
        return topics;
    }

    public Topic getDetail(Long topicId, Long userId) {
        Topic topic = topicRepository.findById(topicId)
                .orElseThrow(() -> new BusinessException(404, "话题不存在"));
        getMemberOrThrow(topic.getCourseId(), userId);
        populateAuthorName(topic);
        anonymizeTopic(topic);
        return topic;
    }

    public List<TopicReply> getReplies(Long topicId) {
        List<TopicReply> replies = replyRepository.findByTopicIdOrderByPathAscCreateTimeAsc(topicId);
        replies.forEach(reply -> {
            populateAuthorName(reply);
            anonymizeReply(reply);
        });
        return replies;
    }

    @Transactional
    public Topic create(Long courseId, Long authorId, String title, String content, Boolean isAnonymous) {
        getMemberOrThrow(courseId, authorId);

        Topic topic = Topic.builder()
                .courseId(courseId)
                .authorId(authorId)
                .title(title)
                .content(content)
                .isAnonymous(isAnonymous != null && isAnonymous)
                .build();
        topic = topicRepository.save(topic);

        // 异步索引到知识库
        knowledgeBaseService.indexTopic(topic);

        return topic;
    }

    @Transactional
    public TopicReply reply(Long topicId, Long authorId, String content, Boolean isAnonymous, Long parentId) {
        Topic topic = topicRepository.findById(topicId)
                .orElseThrow(() -> new BusinessException(404, "话题不存在"));

        if (topic.getStatus() == TopicStatus.LOCKED) {
            throw new BusinessException(400, "该话题已锁定，无法回复");
        }
        if (!topic.getDiscussionEnabled()) {
            throw new BusinessException(400, "该话题已关闭讨论");
        }
        getMemberOrThrow(topic.getCourseId(), authorId);

        // 校验 parentId 属于当前话题，防止跨话题伪造楼中楼
        Long parentAuthorId = null;
        if (parentId != null) {
            TopicReply parent = replyRepository.findById(parentId)
                    .orElseThrow(() -> new BusinessException(404, "父回复不存在"));
            if (!parent.getTopicId().equals(topicId)) {
                throw new BusinessException(400, "父回复不属于当前话题");
            }
            // 限制最多 2 级评论：只能回复一级评论（depth=0），不能再嵌套
            int parentDepth = getDepth(parent.getPath());
            if (parentDepth >= 1) {
                throw new BusinessException(400, "最多支持 2 级评论，无法继续嵌套回复");
            }
            parentAuthorId = parent.getAuthorId();
        }

        TopicReply reply = TopicReply.builder()
                .topicId(topicId)
                .authorId(authorId)
                .content(content)
                .isAnonymous(isAnonymous != null && isAnonymous)
                .parentId(parentId)
                .build();

        reply = replyRepository.save(reply);

        // 自动维护物化路径
        String path;
        if (parentId != null) {
            // parent 已在上面校验时获取
            TopicReply parent = replyRepository.findById(parentId).orElse(null);
            String parentPath = (parent != null && parent.getPath() != null) ? parent.getPath() : "/" + parentId;
            path = parentPath + "/" + reply.getId();
        } else {
            path = "/" + reply.getId();
        }
        reply.setPath(path);
        reply = replyRepository.save(reply);

        // 准备回复者名称
        User replier = userRepository.findById(authorId).orElse(null);
        String replierName = replier != null ? replier.getRealName() : "用户";
        if (isAnonymous != null && isAnonymous) {
            replierName = "匿名用户";
        }

        // 向话题作者发送回复通知（自己不通知自己）
        if (!topic.getAuthorId().equals(authorId)) {
            notificationService.create(
                    topic.getAuthorId(),
                    topic.getCourseId(),
                    NotificationType.TOPIC_REPLY,
                    "话题收到新回复",
                    replierName + " 回复了你的话题「" + topic.getTitle() + "」",
                    topicId
            );
        }

        // 向被回复者发送通知（楼中楼，且被回复者不是话题作者、不是回复者自己）
        if (parentAuthorId != null
                && !parentAuthorId.equals(authorId)
                && !parentAuthorId.equals(topic.getAuthorId())) {
            notificationService.create(
                    parentAuthorId,
                    topic.getCourseId(),
                    NotificationType.TOPIC_REPLY,
                    "你的回复收到新回复",
                    replierName + " 回复了你的评论",
                    topicId
            );
        }

        return reply;
    }

    @Transactional
    public Topic updateStatus(Long topicId, Long userId, TopicStatus newStatus) {
        Topic topic = topicRepository.findById(topicId)
                .orElseThrow(() -> new BusinessException(404, "话题不存在"));
        checkTeacher(topic.getCourseId(), userId);
        topic.setStatus(newStatus);
        return topicRepository.save(topic);
    }

    @Transactional
    public Topic update(Long topicId, Long userId, String title, String content) {
        Topic topic = topicRepository.findById(topicId)
                .orElseThrow(() -> new BusinessException(404, "话题不存在"));

        // 作者或教师可编辑
        boolean isAuthor = topic.getAuthorId().equals(userId);
        boolean isTeacher = isTeacher(topic.getCourseId(), userId);
        if (!isAuthor && !isTeacher) {
            throw new BusinessException(403, "无权编辑该话题");
        }

        if (title != null) topic.setTitle(title);
        if (content != null) topic.setContent(content);
        topic = topicRepository.save(topic);

        // 内容变更后重新索引
        if (content != null || title != null) {
            knowledgeBaseService.indexTopic(topic);
        }

        return topic;
    }

    @Transactional
    public void deleteTopic(Long topicId, Long userId) {
        Topic topic = topicRepository.findById(topicId)
                .orElseThrow(() -> new BusinessException(404, "话题不存在"));

        boolean isAuthor = topic.getAuthorId().equals(userId);
        boolean isTeacher = isTeacher(topic.getCourseId(), userId);
        if (!isAuthor && !isTeacher) {
            throw new BusinessException(403, "无权删除该话题");
        }

        topic.setDeleted(true);
        topicRepository.save(topic);

        // 异步清理知识库索引
        knowledgeBaseService.deleteBySource(
                com.ketangpai.model.enums.SourceType.TOPIC, topicId);
    }

    @Transactional
    public void deleteReply(Long replyId, Long userId) {
        TopicReply reply = replyRepository.findById(replyId)
                .orElseThrow(() -> new BusinessException(404, "回复不存在"));
        Topic topic = topicRepository.findById(reply.getTopicId())
                .orElseThrow(() -> new BusinessException(404, "话题不存在"));

        boolean isAuthor = reply.getAuthorId().equals(userId);
        boolean isTeacher = isTeacher(topic.getCourseId(), userId);
        if (!isAuthor && !isTeacher) {
            throw new BusinessException(403, "无权删除该回复");
        }

        reply.setDeleted(true);
        replyRepository.save(reply);
    }

    @Transactional
    public Topic toggleDiscussion(Long topicId, Long userId) {
        Topic topic = topicRepository.findById(topicId)
                .orElseThrow(() -> new BusinessException(404, "话题不存在"));
        checkTeacher(topic.getCourseId(), userId);
        topic.setDiscussionEnabled(!topic.getDiscussionEnabled());
        return topicRepository.save(topic);
    }

    // ==================== 辅助方法 ====================

    private void populateAuthorName(Topic topic) {
        userRepository.findById(topic.getAuthorId())
                .ifPresent(user -> topic.setAuthorName(user.getRealName()));
    }

    private void populateAuthorName(TopicReply reply) {
        userRepository.findById(reply.getAuthorId())
                .ifPresent(user -> reply.setAuthorName(user.getRealName()));
    }

    private void anonymizeTopic(Topic topic) {
        if (Boolean.TRUE.equals(topic.getIsAnonymous())) {
            topic.setAuthorId(null);
            topic.setAuthorName("匿名用户");
        }
    }

    private void anonymizeReply(TopicReply reply) {
        if (Boolean.TRUE.equals(reply.getIsAnonymous())) {
            reply.setAuthorId(null);
            reply.setAuthorName("匿名用户");
        }
    }

    private int getDepth(String path) {
        if (path == null) return 0;
        // 路径格式: /123 或 /456/789，depth = 段数 - 1
        int depth = 0;
        for (int i = 0; i < path.length(); i++) {
            if (path.charAt(i) == '/') depth++;
        }
        return depth - 1;
    }

    private boolean isTeacher(Long courseId, Long userId) {
        try {
            checkTeacher(courseId, userId);
            return true;
        } catch (BusinessException e) {
            return false;
        }
    }
}
