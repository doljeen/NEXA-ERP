package com.ssafy.attendanceservice.domain.attendance.service;

import com.ssafy.attendanceservice.domain.attendance.calculator.WorkLogEntry;
import com.ssafy.attendanceservice.domain.attendance.calculator.WorkTimeCalculator;
import com.ssafy.attendanceservice.domain.attendance.calculator.WorkTimeResult;
import com.ssafy.attendanceservice.domain.attendance.dto.AttendanceRequestPreviewRequest;
import com.ssafy.attendanceservice.domain.attendance.dto.AttendanceRequestPreviewResponse;
import com.ssafy.attendanceservice.domain.attendance.dto.WorkTimeResponse;
import com.ssafy.attendanceservice.domain.attendance.entity.AttendanceDaily;
import com.ssafy.attendanceservice.domain.attendance.entity.WorkGroup;
import com.ssafy.attendanceservice.domain.attendance.entity.WorkStatusCode;
import com.ssafy.attendanceservice.domain.attendance.entity.WorkStatusLog;
import com.ssafy.attendanceservice.domain.attendance.entity.enums.AttendanceRequestStatus;
import com.ssafy.attendanceservice.domain.attendance.entity.enums.AttendanceRequestType;
import com.ssafy.attendanceservice.domain.attendance.repository.AttendanceDailyRepository;
import com.ssafy.attendanceservice.domain.attendance.repository.AttendanceRequestRepository;
import com.ssafy.attendanceservice.domain.attendance.repository.WorkStatusCodeRepository;
import com.ssafy.attendanceservice.domain.attendance.repository.WorkStatusLogRepository;
import com.ssafy.attendanceservice.global.exception.BusinessException;
import com.ssafy.attendanceservice.global.exception.ErrorCode;
import com.ssafy.attendanceservice.global.util.DateTimeUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class AttendanceRequestServiceImpl implements AttendanceRequestService {

    private final AttendanceRequestRepository attendanceRequestRepository;
    private final AttendanceDailyRepository attendanceDailyRepository;
    private final WorkStatusLogRepository workStatusLogRepository;
    private final WorkStatusCodeRepository workStatusCodeRepository;
    private final AttendanceDailyProcessor processor;

    /** 시뮬레이션용 로그 한 줄 (DB에 저장하지 않음) */
    private record SimulatedLog(String statusCode, OffsetDateTime startAt, boolean countAsWork) {
    }

    @Override
    @Transactional(readOnly = true)
    public AttendanceRequestPreviewResponse preview(Long empId, AttendanceRequestPreviewRequest request) {
        // 같은 날 결재 대기 중인 같은 유형 신청이 있으면 불가
        if (attendanceRequestRepository.existsByEmpIdAndTargetDateAndReqTypeAndStatus(
                empId, request.targetDate(), request.reqType(), AttendanceRequestStatus.PENDING)) {
            throw new BusinessException(ErrorCode.DUPLICATE_PENDING_REQUEST);
        }

        return switch (request.reqType()) {
            case TIME_FIX -> previewTimeFix(empId, request);
            // TODO: 다음 커밋에서 구현
            case OVERTIME, HOLIDAY_SUBST ->
                    throw new BusinessException(ErrorCode.INVALID_INPUT, "준비 중인 신청 유형입니다.");
        };
    }

    // ===== 근무시간 수정 =====

    private AttendanceRequestPreviewResponse previewTimeFix(Long empId, AttendanceRequestPreviewRequest request) {
        LocalDate targetDate = request.targetDate();

        // 1. 대상일은 오늘 이전이나 오늘
        if (targetDate.isAfter(DateTimeUtils.now().toLocalDate())) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "미래 날짜의 근무시간은 수정할 수 없습니다.");
        }
        if (request.itemsOrEmpty().isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "수정할 내용(items)이 없습니다.");
        }

        // 2. 현재 로그를 시뮬레이션 목록으로 (logId → 로그)
        Map<Long, SimulatedLog> current = new LinkedHashMap<>();
        workStatusLogRepository.findByEmpIdAndWorkDateAndIsDeletedFalseOrderByStartAtAscLogIdAsc(empId, targetDate)
                .forEach(log -> current.put(log.getLogId(), toSimulated(log)));
        List<SimulatedLog> added = new ArrayList<>();

        // 3. 신청 내용을 시뮬레이션에 적용 (DB는 그대로)
        for (AttendanceRequestPreviewRequest.Item item : request.itemsOrEmpty()) {
            if (item.action() == null) {
                throw new BusinessException(ErrorCode.INVALID_INPUT, "action은 필수입니다.");
            }
            switch (item.action()) {
                case ADD -> added.add(toSimulated(item, targetDate));
                case MODIFY -> {
                    validateTargetLog(current, item.targetLogId());
                    current.put(item.targetLogId(), toSimulated(item, targetDate));
                }
                case DELETE -> {
                    validateTargetLog(current, item.targetLogId());
                    current.remove(item.targetLogId());
                }
            }
        }

        List<SimulatedLog> after = new ArrayList<>(current.values());
        after.addAll(added);

        // 4. 적용 결과에서 출근/퇴근은 각각 1건 이하
        if (countByCode(after, WorkStatusCode.CLOCK_IN) > 1 || countByCode(after, WorkStatusCode.CLOCK_OUT) > 1) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "출근과 퇴근은 하루에 각각 1건만 등록할 수 있습니다.");
        }

        // 5. 수정 후 근로시간 계산 (그날 근무그룹, 휴일 여부 기준)
        Optional<AttendanceDaily> daily = attendanceDailyRepository.findByEmpIdAndWorkDate(empId, targetDate);
        WorkGroup workGroup = daily.map(AttendanceDaily::getWorkGroup)
                .orElseGet(() -> processor.resolveWorkGroup(empId, targetDate));
        boolean isHoliday = daily.map(AttendanceDaily::getIsHoliday)
                .orElseGet(() -> processor.isHoliday(targetDate, workGroup));

        OffsetDateTime clockOut = after.stream()
                .filter(log -> WorkStatusCode.CLOCK_OUT.equals(log.statusCode()))
                .map(SimulatedLog::startAt)
                .reduce((first, second) -> second)
                .orElse(null);
        WorkTimeResult afterWorkTime = WorkTimeCalculator.calculate(
                after.stream().map(log -> new WorkLogEntry(log.startAt(), log.countAsWork())).toList(),
                clockOut,
                workGroup.getDailyStdMinutes(),
                workGroup.getBreakMinutes(),
                isHoliday);

        // 6. 주간 총 근로시간 (그날은 수정 후 값으로 대체)
        int weeklyTotalMin = weeklyTotalMinExcept(empId, targetDate) + afterWorkTime.totalMin();

        return AttendanceRequestPreviewResponse.ofTimeFix(targetDate, new AttendanceRequestPreviewResponse.TimeFix(
                daily.map(WorkTimeResponse::from).orElseGet(WorkTimeResponse::empty),
                WorkTimeResponse.from(afterWorkTime),
                weeklyTotalMin,
                workGroup.getWeeklyMaxHours() * 60));
    }

    /** 수정/삭제 대상은 본인의 그날 로그(삭제 제외)이고 퇴근취소가 아니어야 함 */
    private void validateTargetLog(Map<Long, SimulatedLog> current, Long targetLogId) {
        if (targetLogId == null || !current.containsKey(targetLogId)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "수정/삭제 대상 로그가 올바르지 않습니다.");
        }
        if (WorkStatusCode.CLOCK_OUT_CANCEL.equals(current.get(targetLogId).statusCode())) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "퇴근취소 기록은 수정/삭제할 수 없습니다.");
        }
    }

    private SimulatedLog toSimulated(WorkStatusLog log) {
        return new SimulatedLog(
                log.getWorkStatusCode().getStatusCode(), log.getStartAt(), log.getWorkStatusCode().getCountAsWork());
    }

    /** 신청 항목 검증 후 시뮬레이션 로그로 변환 (추가/수정용) */
    private SimulatedLog toSimulated(AttendanceRequestPreviewRequest.Item item, LocalDate workDate) {
        if (item.statusCode() == null || WorkStatusCode.CLOCK_OUT_CANCEL.equals(item.statusCode())) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "등록할 수 없는 근무 상태입니다.");
        }
        WorkStatusCode statusCode = workStatusCodeRepository.findById(item.statusCode())
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_INPUT, "존재하지 않는 근무 상태입니다."));
        validateStartAt(workDate, item.startAt());
        return new SimulatedLog(statusCode.getStatusCode(), item.startAt(), statusCode.getCountAsWork());
    }

    private void validateStartAt(LocalDate workDate, OffsetDateTime startAt) {
        if (startAt == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "startAt은 필수입니다.");
        }
        LocalDate startDate = startAt.atZoneSameInstant(DateTimeUtils.ZONE).toLocalDate();
        if (!startDate.equals(workDate) && !startDate.equals(workDate.plusDays(1))) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "startAt은 근무일 당일 또는 다음 날이어야 합니다.");
        }
    }

    private long countByCode(List<SimulatedLog> logs, String statusCode) {
        return logs.stream().filter(log -> statusCode.equals(log.statusCode())).count();
    }

    /** 대상일이 포함된 주(월~일)에서 대상일을 제외한 근로시간 합 */
    private int weeklyTotalMinExcept(Long empId, LocalDate targetDate) {
        LocalDate monday = targetDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        return attendanceDailyRepository
                .findByEmpIdAndWorkDateBetweenOrderByWorkDateAsc(empId, monday, monday.plusDays(6)).stream()
                .filter(d -> !d.getWorkDate().equals(targetDate))
                .mapToInt(AttendanceDaily::getTotalMin)
                .sum();
    }
}