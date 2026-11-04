package com.ssafy.user_service.domain.user.repository;

import com.ssafy.user_service.domain.user.entity.Department;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DepartmentRepository extends JpaRepository<Department, Long> {

    /** 바로 아래 하위 부서들 */
    List<Department> findByParent_DeptId(Long parentDeptId);
}
