package com.ssafy.attendanceservice.domain.attendance.repository;

import com.ssafy.attendanceservice.domain.attendance.entity.WorkStatusCode;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkStatusCodeRepository extends JpaRepository<WorkStatusCode, String> {
}