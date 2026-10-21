package com.ssafy.attendanceservice.domain.attendance.repository;

import com.ssafy.attendanceservice.domain.attendance.entity.AttendanceDaily;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;

public interface AttendanceDailyRepository extends JpaRepository<AttendanceDaily, Long> {

    boolean existsByEmpIdAndWorkDate(Long empId, LocalDate workDate);
}