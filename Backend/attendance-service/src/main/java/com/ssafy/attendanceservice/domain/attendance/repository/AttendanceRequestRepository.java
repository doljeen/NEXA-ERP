package com.ssafy.attendanceservice.domain.attendance.repository;

import com.ssafy.attendanceservice.domain.attendance.entity.AttendanceRequest;
import com.ssafy.attendanceservice.domain.attendance.entity.enums.AttendanceRequestStatus;
import com.ssafy.attendanceservice.domain.attendance.entity.enums.AttendanceRequestType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;

public interface AttendanceRequestRepository extends JpaRepository<AttendanceRequest, Long> {

    // 같은 대상일, 같은 유형, 특정 상태의 신청이 있는지 (결재 대기 중복 신청 방지)
    boolean existsByEmpIdAndTargetDateAndReqTypeAndStatus(
            Long empId, LocalDate targetDate, AttendanceRequestType reqType, AttendanceRequestStatus status);
}