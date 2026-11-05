package com.ssafy.user_service.domain.user.repository;

import com.ssafy.user_service.domain.user.entity.Employee;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface EmployeeRepository extends JpaRepository<Employee, Long> {
    Optional<Employee> findByLoginId(String loginId);
    boolean existsByLoginId(String loginId);
    /** 부서원 목록. 정렬·페이지는 pageable 로 (unpaged 면 전체, 건수 쿼리 없음) */
    Page<Employee> findByJobDeptId(Long deptId, Pageable pageable);
}
