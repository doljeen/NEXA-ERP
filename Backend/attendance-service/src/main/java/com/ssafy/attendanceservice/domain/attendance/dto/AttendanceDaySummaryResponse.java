package com.ssafy.attendanceservice.domain.attendance.dto;

import com.ssafy.attendanceservice.domain.attendance.entity.AttendanceAnomaly;
import com.ssafy.attendanceservice.domain.attendance.entity.AttendanceDaily;
import com.ssafy.attendanceservice.domain.attendance.entity.enums.AnomalyType;
import com.ssafy.attendanceservice.global.util.DateTimeUtils;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

/** 주간/월간 조회의 하루 요약 */
public record AttendanceDaySummaryResponse(
        LocalDate workDate,             // 근무일
        Boolean isHoliday,              // 휴일 여부
        OffsetDateTime clockIn,         // 출근 시각
        OffsetDateTime clockOut,        // 퇴근 시각
        WorkTimeResponse workTime,      // 근로시간 상세
        List<AnomalyType> anomalies     // 근태이상
) {
    public static AttendanceDaySummaryResponse of(AttendanceDaily daily, List<AttendanceAnomaly> anomalies) {
        return new AttendanceDaySummaryResponse(
                daily.getWorkDate(),
                daily.getIsHoliday(),
                DateTimeUtils.toSeoul(daily.getClockIn()),
                DateTimeUtils.toSeoul(daily.getClockOut()),
                WorkTimeResponse.from(daily),
                anomalies.stream().map(AttendanceAnomaly::getAnomalyType).toList()
        );
    }

    /** 근태 기록이 없는 날 */
    public static AttendanceDaySummaryResponse empty(LocalDate workDate, boolean isHoliday) {
        return new AttendanceDaySummaryResponse(
                workDate, isHoliday, null, null, WorkTimeResponse.empty(), List.of());
    }
}