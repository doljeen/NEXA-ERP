package com.ssafy.attendanceservice.domain.attendance.repository;

import com.ssafy.attendanceservice.domain.attendance.entity.AttendanceAnomaly;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AttendanceAnomalyRepository extends JpaRepository<AttendanceAnomaly, Long> {
}