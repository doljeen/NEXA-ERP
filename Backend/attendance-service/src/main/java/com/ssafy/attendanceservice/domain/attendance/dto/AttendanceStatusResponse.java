package com.ssafy.attendanceservice.domain.attendance.dto;

import com.ssafy.attendanceservice.domain.attendance.entity.AttendanceDaily;
import com.ssafy.attendanceservice.domain.attendance.entity.WorkStatusCode;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

/** 근태 위젯 상태 응답. 출근/퇴근/상태변경/today API가 공통으로 사용한다. */
public record AttendanceStatusResponse(
        LocalDate workDate,                 // 근무일
        String statusCode,                  // 현재 상태 코드
        String statusName,                  // 현재 상태명
        Boolean isWorking,                  // 현재 상태가 근로시간에 포함되는지
        OffsetDateTime clockIn,             // 출근 시각
        OffsetDateTime clockOut,            // 퇴근 시각
        List<StatusItem> ableStatusList     // 지금 바꿀 수 있는 상태 목록
) {

    public record StatusItem(
            String statusCode,
            String statusName,
            Boolean countAsWork
    ) {
        public static StatusItem from(WorkStatusCode code) {
            return new StatusItem(code.getStatusCode(), code.getName(), code.getCountAsWork());
        }
    }

    public static AttendanceStatusResponse of(AttendanceDaily daily,
                                              WorkStatusCode current,
                                              List<WorkStatusCode> ableStatuses) {
        return new AttendanceStatusResponse(
                daily.getWorkDate(),
                current.getStatusCode(),
                current.getName(),
                current.getCountAsWork(),
                daily.getClockIn(),
                daily.getClockOut(),
                ableStatuses.stream().map(StatusItem::from).toList()
        );
    }
}