package com.ketangpai.controller;

import com.ketangpai.dto.course.CourseDetailResponse;
import com.ketangpai.dto.course.CourseMemberResponse;
import com.ketangpai.exception.GlobalExceptionHandler;
import com.ketangpai.model.enums.CourseMemberRole;
import com.ketangpai.model.enums.CourseStatus;
import com.ketangpai.model.enums.UserRole;
import com.ketangpai.security.CurrentUserId;
import com.ketangpai.service.CourseService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.MethodParameter;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class CourseControllerTest {

    @Mock
    private CourseService courseService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mockMvc = MockMvcBuilders.standaloneSetup(new CourseController(courseService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .setCustomArgumentResolvers(
                        new FixedCurrentUserIdResolver(),
                        new PageableHandlerMethodArgumentResolver())
                .build();
    }

    @Test
    void createRejectsBlankCourseNameBeforeCallingService() throws Exception {
        mockMvc.perform(post("/api/v1/courses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "   ",
                                  "description": "测试课程"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));

        verifyNoInteractions(courseService);
    }

    @Test
    void createReturnsHttp201AndCourseDto() throws Exception {
        CourseDetailResponse response = new CourseDetailResponse(
                10L, "软件工程", null, "SE2026", null,
                CourseStatus.ACTIVE, 1L, CourseMemberRole.CREATOR,
                1L, null, null);
        when(courseService.createCourse(eq(1L), any())).thenReturn(response);

        mockMvc.perform(post("/api/v1/courses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "软件工程"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("课程创建成功"))
                .andExpect(jsonPath("$.data.id").value(10))
                .andExpect(jsonPath("$.data.currentUserRole").value("CREATOR"));
    }

    @Test
    void detailReturnsCourseRoleAndMemberCount() throws Exception {
        CourseDetailResponse response = new CourseDetailResponse(
                10L, "软件工程", "课程简介", "SE2026", null,
                CourseStatus.ACTIVE, 1L, CourseMemberRole.TEACHER,
                35L, null, null);
        when(courseService.getDetail(10L, 1L)).thenReturn(response);

        mockMvc.perform(get("/api/v1/courses/10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(10))
                .andExpect(jsonPath("$.data.currentUserRole").value("TEACHER"))
                .andExpect(jsonPath("$.data.memberCount").value(35));
    }

    @Test
    void memberListAcceptsRoleKeywordAndPagination() throws Exception {
        CourseMemberResponse member = new CourseMemberResponse(
                20L, 2L, "lisi", "李四", null,
                UserRole.STUDENT, CourseMemberRole.STUDENT, null);
        when(courseService.getMemberList(
                eq(10L), eq(1L), eq(CourseMemberRole.STUDENT), eq("李"), any()))
                .thenReturn(new PageImpl<>(
                        java.util.List.of(member), PageRequest.of(0, 30), 1));

        mockMvc.perform(get("/api/v1/courses/10/members")
                        .param("role", "student")
                        .param("keyword", "李")
                        .param("page", "0")
                        .param("size", "30"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].username").value("lisi"))
                .andExpect(jsonPath("$.data.content[0].role").value("STUDENT"));
    }

    @Test
    void memberListRejectsUnknownRole() throws Exception {
        mockMvc.perform(get("/api/v1/courses/10/members")
                        .param("role", "owner"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));

        verifyNoInteractions(courseService);
    }

    @Test
    void actionRejectsUnknownEnumAsBadRequest() throws Exception {
        mockMvc.perform(post("/api/v1/courses/10/action")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "action": "UNKNOWN"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));

        verifyNoInteractions(courseService);
    }

    @Test
    void sortingRejectsEmptyItemList() throws Exception {
        mockMvc.perform(put("/api/v1/courses/order")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "items": []
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));

        verifyNoInteractions(courseService);
    }

    @Test
    void trashActionRejectsUnknownEnumAsBadRequest() throws Exception {
        mockMvc.perform(post("/api/v1/courses/10/trash/action")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "action": "UNKNOWN"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));

        verifyNoInteractions(courseService);
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
