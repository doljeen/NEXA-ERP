package com.ssafy.attendanceservice.domain.attendance.repository;

import com.ssafy.attendanceservice.domain.attendance.entity.AttendanceRequest;
import com.ssafy.attendanceservice.domain.attendance.entity.enums.AttendanceRequestStatus;
import com.ssafy.attendanceservice.domain.attendance.entity.enums.AttendanceRequestType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;

public interface AttendanceRequestRepository extends JpaRepository<AttendanceRequest, Long> {

    // 같은 대상일, 같은 유형, 특정 상태의 신청이 있는지 (결재 대기 중복 신청 방지)
    boolean existsByEmpIdAndTargetDateAndReqTypeAndStatus(
            Long empId, LocalDate targetDate, AttendanceRequestType reqType, AttendanceRequestStatus status);

    // 같은 대상일(근무한 휴일)로 진행 중/승인된 신청이 있는지 (휴일대체 중복 방지)
    boolean existsByEmpIdAndReqTypeAndTargetDateAndStatusIn(
            Long empId, AttendanceRequestType reqType, LocalDate targetDate, Collection<AttendanceRequestStatus> statuses);

    // 같은 대체일로 진행 중/승인된 신청이 있는지 (휴일대체 중복 방지)
    boolean existsByEmpIdAndReqTypeAndSubstDateAndStatusIn(
            Long empId, AttendanceRequestType reqType, LocalDate substDate, Collection<AttendanceRequestStatus> statuses);

    // 대상일의 특정 종류·상태 신청 (재계산 시 승인된 초과근무 조회)
    List<AttendanceRequest> findByEmpIdAndTargetDateAndReqTypeAndStatus(
            Long empId, LocalDate targetDate, AttendanceRequestType reqType, AttendanceRequestStatus status);

    // 상신 일시 기간 내 내 신청 (최신순, 전체 유형)
    List<AttendanceRequest> findByEmpIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByCreatedAtDesc(
            Long empId, OffsetDateTime from, OffsetDateTime to);

    // 상신 일시 기간 내 내 신청 (최신순, 특정 유형)
    List<AttendanceRequest> findByEmpIdAndReqTypeAndCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByCreatedAtDesc(
            Long empId, AttendanceRequestType reqType, OffsetDateTime from, OffsetDateTime to);
}
