package com.ketangpai.controller;

import com.ketangpai.dto.assignment.AssignmentListResponse;
import com.ketangpai.model.enums.AssignmentStatus;
import com.ketangpai.security.CurrentUserId;
import com.ketangpai.service.AssignmentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.MethodParameter;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import java.util.List;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AssignmentControllerTest {

    @Mock
    private AssignmentService assignmentService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new AssignmentController(assignmentService))
                .setCustomArgumentResolvers(
                        new FixedCurrentUserIdResolver(),
                        new PageableHandlerMethodArgumentResolver())
                .build();
    }

    @Test
    void assignmentListForwardsStatusAndPaginationAndReturnsDtoPage() throws Exception {
        PageRequest pageable = PageRequest.of(0, 20);
        AssignmentListResponse assignment = new AssignmentListResponse(
                30L, 10L, "需求分析", AssignmentStatus.PUBLISHED,
                null, 100, true, null, null);
        when(assignmentService.listByCourse(
                eq(10L), eq(1L), eq("PUBLISHED"), eq(pageable)))
                .thenReturn(new PageImpl<>(List.of(assignment), pageable, 1));

        mockMvc.perform(get("/api/v1/courses/10/assignments")
                        .param("status", "PUBLISHED")
                        .param("page", "0")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].id").value(30))
                .andExpect(jsonPath("$.data.content[0].status").value("PUBLISHED"))
                .andExpect(jsonPath("$.data.totalElements").value(1));
    }

    private static class FixedCurrentUserIdResolver implements HandlerMethodArgumentResolver {

        @Override
        public boolean supportsParameter(MethodParameter parameter) {
            return parameter.hasParameterAnnotation(CurrentUserId.class);
        }

        @Override
        public Object resolveArgument(MethodParameter parameter,
                                      ModelAndViewContainer mavContainer,
                                      NativeWebRequest webRequest,
                                      org.springframework.web.bind.support.WebDataBinderFactory binderFactory) {
            return 1L;
        }
    }
}
