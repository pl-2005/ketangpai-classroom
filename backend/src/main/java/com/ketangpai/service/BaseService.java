package com.ketangpai.service;

import com.ketangpai.model.entity.CourseMember;
import com.ketangpai.exception.BusinessException;
import com.ketangpai.model.enums.CourseMemberRole;
import com.ketangpai.repository.CourseMemberRepository;

/**
 * Service 基类 — 提供课程成员身份校验
 */
public abstract class BaseService {

    protected final CourseMemberRepository courseMemberRepository;

    protected BaseService(CourseMemberRepository courseMemberRepository) {
        this.courseMemberRepository = courseMemberRepository;
    }

    /** 校验用户是课程成员，返回成员信息；否则抛出 403 */
    protected CourseMember getMemberOrThrow(Long courseId, Long userId) {
        return courseMemberRepository.findByCourseIdAndUserId(courseId, userId)
                .filter(cm -> !cm.getDeleted())
                .orElseThrow(() -> new BusinessException(403, "你不是该课程的成员"));
    }

    /** 校验用户是教师（含创建者），返回成员信息；否则抛出 403 */
    protected CourseMember getTeacherOrThrow(Long courseId, Long userId) {
        CourseMember cm = getMemberOrThrow(courseId, userId);
        if (cm.getRole() == CourseMemberRole.STUDENT) {
            throw new BusinessException(403, "仅教师可执行此操作");
        }
        return cm;
    }

    /** 校验用户是课程创建者，返回成员信息；否则抛出 403 */
    protected CourseMember getCreatorOrThrow(Long courseId, Long userId) {
        CourseMember cm = getMemberOrThrow(courseId, userId);
        if (cm.getRole() != CourseMemberRole.CREATOR) {
            throw new BusinessException(403, "仅课程创建者可执行此操作");
        }
        return cm;
    }

    /** 校验用户是教师（含创建者），学生则抛出 403 */
    protected void checkTeacher(Long courseId, Long userId) {
        getTeacherOrThrow(courseId, userId);
    }
}
