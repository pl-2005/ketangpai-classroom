package com.ketangpai.repository;

import com.ketangpai.entity.CourseMember;
import com.ketangpai.model.enums.CourseMemberRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

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
}
