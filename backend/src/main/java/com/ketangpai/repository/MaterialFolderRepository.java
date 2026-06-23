package com.ketangpai.repository;

import com.ketangpai.model.entity.MaterialFolder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 资料文件夹 Repository
 */
@Repository
public interface MaterialFolderRepository extends JpaRepository<MaterialFolder, Long> {

    List<MaterialFolder> findByCourseIdOrderBySortOrder(Long courseId);

    List<MaterialFolder> findByParentIdOrderBySortOrder(Long parentId);

    /** 查询某课程下的根目录文件夹 */
    List<MaterialFolder> findByCourseIdAndParentIdIsNullOrderBySortOrder(Long courseId);
}
