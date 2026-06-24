package com.ketangpai.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 物理删除课程及其关联业务数据。
 *
 * 数据库当前未声明外键级联，因此必须在同一事务内按依赖顺序清理。
 */
@Repository
@RequiredArgsConstructor
public class CoursePurgeRepository {

    private final JdbcTemplate jdbcTemplate;

    public void purge(Long courseId) {
        delete("DELETE FROM similarity_pair WHERE report_id IN "
                + "(SELECT id FROM similarity_report WHERE assignment_id IN "
                + "(SELECT id FROM assignment WHERE course_id = ?))", courseId);
        delete("DELETE FROM ai_grading_result WHERE submission_id IN "
                + "(SELECT id FROM submission WHERE assignment_id IN "
                + "(SELECT id FROM assignment WHERE course_id = ?))", courseId);
        delete("DELETE FROM submission_file WHERE submission_id IN "
                + "(SELECT id FROM submission WHERE assignment_id IN "
                + "(SELECT id FROM assignment WHERE course_id = ?))", courseId);
        delete("DELETE FROM submission WHERE assignment_id IN "
                + "(SELECT id FROM assignment WHERE course_id = ?)", courseId);
        delete("DELETE FROM ai_grading_config WHERE assignment_id IN "
                + "(SELECT id FROM assignment WHERE course_id = ?)", courseId);
        delete("DELETE FROM assignment_attachment WHERE assignment_id IN "
                + "(SELECT id FROM assignment WHERE course_id = ?)", courseId);
        delete("DELETE FROM similarity_report WHERE assignment_id IN "
                + "(SELECT id FROM assignment WHERE course_id = ?)", courseId);
        delete("DELETE FROM assignment WHERE course_id = ?", courseId);

        delete("DELETE FROM topic_reply WHERE topic_id IN "
                + "(SELECT id FROM topic WHERE course_id = ?)", courseId);
        delete("DELETE FROM topic WHERE course_id = ?", courseId);
        delete("DELETE FROM material WHERE course_id = ?", courseId);
        delete("DELETE FROM material_folder WHERE course_id = ?", courseId);
        delete("DELETE FROM knowledge_chunk WHERE course_id = ?", courseId);
        delete("DELETE FROM chat_message WHERE course_id = ?", courseId);
        delete("DELETE FROM notification WHERE course_id = ?", courseId);
        delete("DELETE FROM course_member WHERE course_id = ?", courseId);
        delete("DELETE FROM course WHERE id = ?", courseId);
    }

    private void delete(String sql, Long courseId) {
        jdbcTemplate.update(sql, courseId);
    }
}
