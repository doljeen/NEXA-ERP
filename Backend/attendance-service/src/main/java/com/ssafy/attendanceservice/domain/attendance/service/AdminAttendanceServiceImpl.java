package com.ssafy.attendanceservice.domain.attendance.service;

import com.ssafy.attendanceservice.domain.attendance.dto.AdminWorkStatusLogRequest;
import com.ssafy.attendanceservice.domain.attendance.dto.DailyAttendanceResponse;
import com.ssafy.attendanceservice.domain.attendance.entity.AttendanceDaily;
import com.ssafy.attendanceservice.domain.attendance.entity.WorkGroup;
import com.ssafy.attendanceservice.domain.attendance.entity.WorkStatusCode;
import com.ssafy.attendanceservice.domain.attendance.entity.WorkStatusLog;
import com.ssafy.attendanceservice.domain.attendance.entity.enums.WorkStatusSource;
import com.ssafy.attendanceservice.domain.attendance.repository.AttendanceDailyRepository;
import com.ssafy.attendanceservice.domain.attendance.repository.WorkStatusCodeRepository;
import com.ssafy.attendanceservice.domain.attendance.repository.WorkStatusLogRepository;
import com.ssafy.attendanceservice.global.exception.BusinessException;
import com.ssafy.attendanceservice.global.exception.ErrorCode;
import com.ssafy.attendanceservice.global.util.DateTimeUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;

@Service
@RequiredArgsConstructor
public class AdminAttendanceServiceImpl implements AdminAttendanceService {

    private final AttendanceDailyRepository attendanceDailyRepository;
    private final WorkStatusLogRepository workStatusLogRepository;
    private final WorkStatusCodeRepository workStatusCodeRepository;
    private final AttendanceDailyProcessor processor;
    private final AttendanceService attendanceService;

    @Override
    @Transactional
    public DailyAttendanceResponse registerLog(Long adminId, AdminWorkStatusLogRequest request) {
        Long empId = request.empId();
        LocalDate workDate = request.workDate();

        // 1. 상태코드 검증 (퇴근취소는 시스템 기록이라 직접 등록 불가)
        WorkStatusCode statusCode = getRegistrableStatusCode(request.statusCode());

        // 2. 시각은 근무일 당일 또는 다음 날 (야간 근무 퇴근)
        validateStartAt(workDate, request.startAt());

        // 3. 출근/퇴근은 하루에 하나 (고치려면 수정 API 사용)
        if (isClockCode(statusCode) && workStatusLogRepository
                .existsByEmpIdAndWorkDateAndWorkStatusCode_StatusCodeAndIsDeletedFalse(
                        empId, workDate, statusCode.getStatusCode())) {
            throw new BusinessException(ErrorCode.DUPLICATE_CLOCK_LOG);
        }

        // 4. 그날 근태가 없으면 생성
        AttendanceDaily daily = attendanceDailyRepository.findByEmpIdAndWorkDate(empId, workDate)
                .orElseGet(() -> createDaily(empId, workDate));

        // 5. 로그 저장
        workStatusLogRepository.save(WorkStatusLog.builder()
                .workStatusCode(statusCode)
                .empId(empId)
                .workDate(workDate)
                .startAt(request.startAt())
                .source(WorkStatusSource.ADMIN)
                .createdBy(adminId)
                .reason(request.reason())
                .build());

        // 6. 로그 기준으로 그날 근태 재동기화
        processor.syncDaily(daily);

        return attendanceService.getDaily(empId, workDate);
    }

    /** 등록 가능한 상태코드. 없는 코드이거나 퇴근취소면 INVALID_STATUS_CHANGE */
    private WorkStatusCode getRegistrableStatusCode(String statusCode) {
        if (WorkStatusCode.CLOCK_OUT_CANCEL.equals(statusCode)) {
            throw new BusinessException(ErrorCode.INVALID_STATUS_CHANGE);
        }
        return workStatusCodeRepository.findById(statusCode)
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_STATUS_CHANGE));
    }

    private void validateStartAt(LocalDate workDate, OffsetDateTime startAt) {
        LocalDate startDate = startAt.atZoneSameInstant(DateTimeUtils.ZONE).toLocalDate();
        if (!startDate.equals(workDate) && !startDate.equals(workDate.plusDays(1))) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "startAt은 근무일 당일 또는 다음 날이어야 합니다.");
        }
    }

    private boolean isClockCode(WorkStatusCode statusCode) {
        return WorkStatusCode.CLOCK_IN.equals(statusCode.getStatusCode())
                || WorkStatusCode.CLOCK_OUT.equals(statusCode.getStatusCode());
    }

    /** 출근 기록 없이 관리자가 먼저 등록하는 날. 출근 시각은 재동기화 때 로그에서 채운다. */
    private AttendanceDaily createDaily(Long empId, LocalDate workDate) {
        WorkGroup workGroup = processor.resolveWorkGroup(empId, workDate);
        return attendanceDailyRepository.save(AttendanceDaily.builder()
                .workGroup(workGroup)
                .empId(empId)
                .workDate(workDate)
                .clockIn(null)
                .isHoliday(processor.isHoliday(workDate, workGroup))
                .build());
    }
}