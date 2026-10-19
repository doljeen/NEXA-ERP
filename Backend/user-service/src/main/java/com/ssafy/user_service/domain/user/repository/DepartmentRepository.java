package com.ssafy.user_service.domain.user.repository;

import com.ssafy.user_service.domain.user.entity.Department;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DepartmentRepository extends JpaRepository<Department, Long> {
}
