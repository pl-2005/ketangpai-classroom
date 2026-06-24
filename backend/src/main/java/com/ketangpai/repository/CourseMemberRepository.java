package com.ketangpai.repository;

import com.ketangpai.dto.course.CourseCardResponse;
import com.ketangpai.dto.course.CourseMemberResponse;
import com.ketangpai.model.entity.CourseMember;
import com.ketangpai.model.enums.CourseMemberRole;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.Collection;

/**
 * 课程成员 Repository
 * <p>
 * 注意：实体未加 @SQLRestriction，查询需手动过滤 deleted=0 或退课记录。
 */
@Repository
public interface CourseMemberRepository extends JpaRepository<CourseMember, Long> {

    /** 查询用户所有有效成员关系（未退课） */
    @Query("SELECT cm FROM CourseMember cm WHERE cm.userId = :userId AND cm.deleted = false")
    List<CourseMember> findByUserId(@Param("userId") Long userId);

    /** 查询用户所有成员关系（含已退课，用于判断是否可重新加入） */
    @Query("SELECT cm FROM CourseMember cm WHERE cm.userId = :userId")
    List<CourseMember> findByUserIdIncludeDeleted(@Param("userId") Long userId);

    /** 查询课程所有有效成员 */
    @Query("SELECT cm FROM CourseMember cm WHERE cm.courseId = :courseId AND cm.deleted = false")
    List<CourseMember> findByCourseId(@Param("courseId") Long courseId);

    /** 查询课程中某角色的所有有效成员 */
    @Query("SELECT cm FROM CourseMember cm WHERE cm.courseId = :courseId AND cm.role = :role AND cm.deleted = false")
    List<CourseMember> findByCourseIdAndRole(@Param("courseId") Long courseId, @Param("role") CourseMemberRole role);

    /** 查询单个成员关系（含已退课） */
    @Query("SELECT cm FROM CourseMember cm WHERE cm.courseId = :courseId AND cm.userId = :userId")
    Optional<CourseMember> findByCourseIdAndUserId(@Param("courseId") Long courseId, @Param("userId") Long userId);

    /** 统计课程有效学生人数 */
    @Query("SELECT COUNT(cm) FROM CourseMember cm WHERE cm.courseId = :courseId AND cm.role = 'STUDENT' AND cm.deleted = false")
    long countStudentsByCourseId(@Param("courseId") Long courseId);

    /** 统计课程全部有效成员。 */
    @Query("SELECT COUNT(cm) FROM CourseMember cm WHERE cm.courseId = :courseId AND cm.deleted = false")
    long countActiveMembersByCourseId(@Param("courseId") Long courseId);

    /**
     * 查询课程卡片。全局归档课程始终进入归档视图，个人归档只影响当前成员。
     */
    @Query(value = """
            SELECT new com.ketangpai.dto.course.CourseCardResponse(
                c.id, c.name, c.courseCode, c.coverUrl, c.status,
                (SELECT COUNT(memberCount) FROM CourseMember memberCount
                    WHERE memberCount.courseId = c.id AND memberCount.deleted = false),
                cm.role, cm.isArchived, cm.sortOrder, c.createTime)
            FROM CourseMember cm
            JOIN Course c ON c.id = cm.courseId
            WHERE cm.userId = :userId
              AND cm.deleted = false
              AND c.deleted = false
              AND ((:archived = true AND (cm.isArchived = true OR c.status = com.ketangpai.model.enums.CourseStatus.ARCHIVED))
                OR (:archived = false AND cm.isArchived = false AND c.status = com.ketangpai.model.enums.CourseStatus.ACTIVE))
            ORDER BY cm.sortOrder ASC, c.createTime DESC
            """,
            countQuery = """
            SELECT COUNT(cm)
            FROM CourseMember cm
            JOIN Course c ON c.id = cm.courseId
            WHERE cm.userId = :userId
              AND cm.deleted = false
              AND c.deleted = false
              AND ((:archived = true AND (cm.isArchived = true OR c.status = com.ketangpai.model.enums.CourseStatus.ARCHIVED))
                OR (:archived = false AND cm.isArchived = false AND c.status = com.ketangpai.model.enums.CourseStatus.ACTIVE))
            """)
    Page<CourseCardResponse> findCourseCards(@Param("userId") Long userId,
                                              @Param("archived") boolean archived,
                                              Pageable pageable);

    /** 查询课程成员展示信息。 */
    @Query("""
            SELECT new com.ketangpai.dto.course.CourseMemberResponse(
                cm.id, u.id, u.username, u.realName, u.avatarUrl, u.role, cm.role, cm.joinedAt)
            FROM CourseMember cm
            JOIN User u ON u.id = cm.userId
            WHERE cm.courseId = :courseId
              AND cm.deleted = false
              AND (:role IS NULL OR cm.role = :role)
            ORDER BY CASE cm.role
                WHEN com.ketangpai.model.enums.CourseMemberRole.CREATOR THEN 0
                WHEN com.ketangpai.model.enums.CourseMemberRole.TEACHER THEN 1
                ELSE 2 END,
                cm.joinedAt ASC
            """)
    Page<CourseMemberResponse> findMemberResponses(@Param("courseId") Long courseId,
                                                    @Param("role") CourseMemberRole role,
                                                    Pageable pageable);

    @Query("""
            SELECT cm
            FROM CourseMember cm
            JOIN Course c ON c.id = cm.courseId
            WHERE cm.userId = :userId
              AND cm.deleted = false
              AND c.deleted = false
              AND cm.courseId IN :courseIds
            """)
    List<CourseMember> findActiveForSorting(@Param("userId") Long userId,
                                             @Param("courseIds") Collection<Long> courseIds);
}
