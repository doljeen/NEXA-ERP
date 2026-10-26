package com.ssafy.user_service.domain.user.repository;

import com.ssafy.user_service.domain.user.entity.Employee;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface EmployeeRepository extends JpaRepository<Employee, Long> {
    Optional<Employee> findByLoginId(String loginId);
    boolean existsByLoginId(String loginId);
    List<Employee> findByJobDeptId(Long deptId);
}
