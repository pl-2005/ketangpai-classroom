package com.ketangpai.repository;

import com.ketangpai.entity.Material;
import com.ketangpai.model.enums.MaterialType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 课程资料 Repository
 */
@Repository
public interface MaterialRepository extends JpaRepository<Material, Long> {

    List<Material> findByCourseIdOrderBySortOrder(Long courseId);

    List<Material> findByFolderIdOrderBySortOrder(Long folderId);

    /** 查询某课程下的根目录资料（不在任何文件夹中） */
    List<Material> findByCourseIdAndFolderIdIsNullOrderBySortOrder(Long courseId);

    List<Material> findByCourseIdAndType(Long courseId, MaterialType type);
}
