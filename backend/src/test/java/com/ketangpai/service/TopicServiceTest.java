package com.ketangpai.service;

import com.ketangpai.model.entity.Topic;
import com.ketangpai.model.entity.TopicReply;
import com.ketangpai.repository.CourseMemberRepository;
import com.ketangpai.repository.TopicReplyRepository;
import com.ketangpai.repository.TopicRepository;
import com.ketangpai.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TopicServiceTest {

    @Test
    void deleteReplySoftDeletesDescendantReplies() {
        CourseMemberRepository courseMemberRepository = mock(CourseMemberRepository.class);
        TopicRepository topicRepository = mock(TopicRepository.class);
        TopicReplyRepository replyRepository = mock(TopicReplyRepository.class);
        UserRepository userRepository = mock(UserRepository.class);
        KnowledgeBaseService knowledgeBaseService = mock(KnowledgeBaseService.class);
        NotificationService notificationService = mock(NotificationService.class);

        TopicService service = new TopicService(
                courseMemberRepository,
                topicRepository,
                replyRepository,
                userRepository,
                knowledgeBaseService,
                notificationService
        );

        Topic topic = Topic.builder()
                .courseId(3L)
                .authorId(9L)
                .title("讨论")
                .content("内容")
                .build();
        TopicReply parent = TopicReply.builder()
                .id(11L)
                .topicId(7L)
                .authorId(5L)
                .content("父回复")
                .build();
        TopicReply child = TopicReply.builder()
                .id(12L)
                .topicId(7L)
                .authorId(6L)
                .content("子回复")
                .parentId(11L)
                .build();
        TopicReply grandchild = TopicReply.builder()
                .id(13L)
                .topicId(7L)
                .authorId(6L)
                .content("孙回复")
                .parentId(12L)
                .build();

        when(replyRepository.findById(11L)).thenReturn(Optional.of(parent));
        when(topicRepository.findById(7L)).thenReturn(Optional.of(topic));
        when(replyRepository.findByParentId(11L)).thenReturn(List.of(child));
        when(replyRepository.findByParentId(12L)).thenReturn(List.of(grandchild));
        when(replyRepository.findByParentId(13L)).thenReturn(List.of());

        service.deleteReply(11L, 5L);

        assertTrue(parent.getDeleted());
        assertTrue(child.getDeleted());
        assertTrue(grandchild.getDeleted());
        verify(replyRepository).save(parent);
        verify(replyRepository).save(child);
        verify(replyRepository).save(grandchild);
    }
}
