package com.ssafy.attendanceservice.domain.attendance.dto;

import com.ssafy.attendanceservice.domain.attendance.entity.Holiday;
import com.ssafy.attendanceservice.domain.attendance.entity.enums.HolidayType;

import java.time.LocalDate;

/** 공휴일 */
public record HolidayResponse(
        LocalDate holidayDate,  // 휴일 일자
        String name,            // 휴일명
        HolidayType type        // 휴일 구분 (LEGAL, COMPANY)
) {
    public static HolidayResponse from(Holiday holiday) {
        return new HolidayResponse(holiday.getHolidayDate(), holiday.getName(), holiday.getType());
    }
}
