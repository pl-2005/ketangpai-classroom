package com.ketangpai.service;

import com.ketangpai.model.entity.Topic;
import com.ketangpai.model.entity.TopicReply;
import com.ketangpai.exception.BusinessException;
import com.ketangpai.model.enums.TopicStatus;
import com.ketangpai.repository.CourseMemberRepository;
import com.ketangpai.repository.TopicReplyRepository;
import com.ketangpai.repository.TopicRepository;
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

    public TopicService(CourseMemberRepository courseMemberRepository,
                        TopicRepository topicRepository,
                        TopicReplyRepository replyRepository) {
        super(courseMemberRepository);
        this.topicRepository = topicRepository;
        this.replyRepository = replyRepository;
    }

    public List<Topic> listByCourse(Long courseId, Long userId) {
        getMemberOrThrow(courseId, userId);
        return topicRepository.findByCourseIdOrderByStatusDescCreateTimeDesc(courseId);
    }

    public Topic getDetail(Long topicId, Long userId) {
        Topic topic = topicRepository.findById(topicId)
                .orElseThrow(() -> new BusinessException(404, "话题不存在"));
        getMemberOrThrow(topic.getCourseId(), userId);
        return topic;
    }

    public List<TopicReply> getReplies(Long topicId) {
        return replyRepository.findByTopicIdOrderByPathAscCreateTimeAsc(topicId);
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
        return topicRepository.save(topic);
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
        if (parentId != null) {
            TopicReply parent = replyRepository.findById(parentId)
                    .orElseThrow(() -> new BusinessException(404, "父回复不存在"));
            if (!parent.getTopicId().equals(topicId)) {
                throw new BusinessException(400, "父回复不属于当前话题");
            }
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
        return replyRepository.save(reply);
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
        return topicRepository.save(topic);
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

    private boolean isTeacher(Long courseId, Long userId) {
        try {
            checkTeacher(courseId, userId);
            return true;
        } catch (BusinessException e) {
            return false;
        }
    }
}
