package com.ssafy.attendanceservice.domain.attendance.calculator;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 상태 로그로 하루 근로시간을 계산한다. DB/Spring에 의존하지 않는 순수 계산 로직.
 * 1분 단위로 근무 구간을 펼쳐서 휴게 차감 → 소정/초과 → 야간/휴일 순서로 분류한다.
 */
public final class WorkTimeCalculator {

    private static final ZoneId ZONE = ZoneId.of("Asia/Seoul");

    private static final int HALF_BREAK_THRESHOLD = 240;   // 4시간 이상 근무 시 휴게 30분
    private static final int HALF_BREAK_MINUTES = 30;
    private static final int FULL_BREAK_THRESHOLD = 480;   // 8시간 이상 근무 시 근무그룹 휴게시간

    private static final int NIGHT_START_HOUR = 22;        // 야간 22:00 ~ 06:00
    private static final int NIGHT_END_HOUR = 6;

    private WorkTimeCalculator() {
    }

    /**
     * @param logs            그 근무일의 상태 로그 (출근 ~ 퇴근)
     * @param clockOut        퇴근 시각 (null이면 계산하지 않음)
     * @param dailyStdMinutes 일 소정근로시간(분)
     * @param breakMinutes    근무그룹 휴게시간(분)
     * @param isHoliday       휴일 여부
     */
    public static WorkTimeResult calculate(List<WorkLogEntry> logs, OffsetDateTime clockOut,
                                           int dailyStdMinutes, int breakMinutes, boolean isHoliday) {
        if (logs.isEmpty() || clockOut == null) {
            return WorkTimeResult.empty();
        }

        // 1. 근무 구간을 1분 단위로 펼치고, 미포함 시간은 따로 센다
        List<OffsetDateTime> workMinutes = new ArrayList<>();
        int excludedMin = 0;

        List<WorkLogEntry> sorted = logs.stream()
                .sorted(Comparator.comparing(WorkLogEntry::startAt))
                .toList();
        OffsetDateTime end = toMinute(clockOut);

        for (int i = 0; i < sorted.size(); i++) {
            OffsetDateTime from = toMinute(sorted.get(i).startAt());
            OffsetDateTime to = (i + 1 < sorted.size()) ? toMinute(sorted.get(i + 1).startAt()) : end;
            if (to.isAfter(end)) {
                to = end;
            }
            for (OffsetDateTime t = from; t.isBefore(to); t = t.plusMinutes(1)) {
                if (sorted.get(i).countAsWork()) {
                    workMinutes.add(t);
                } else {
                    excludedMin++;
                }
            }
        }

        // 2. 휴게 차감: 필요한 휴게 - 이미 쉰 시간(미포함)
        int requiredBreak = requiredBreak(workMinutes.size(), breakMinutes);
        int breakMin = Math.max(0, requiredBreak - excludedMin);
        List<OffsetDateTime> counted = removeBreak(workMinutes, breakMin);
        int totalMin = counted.size();

        // 3. 소정/초과/야간/휴일 분류
        int regularMin = 0, nightMin = 0, otExtendedMin = 0, otNightMin = 0, otHolidayMin = 0;

        if (isHoliday) {
            // 휴일: 전부 휴일근로
            otHolidayMin = totalMin;
            otNightMin = (int) counted.stream().filter(WorkTimeCalculator::isNight).count();
        } else {
            // 근무일: 시간 순서로 앞의 소정근로시간까지 소정, 나머지 연장
            for (int i = 0; i < counted.size(); i++) {
                boolean night = isNight(counted.get(i));
                if (i < dailyStdMinutes) {
                    regularMin++;
                    if (night) nightMin++;
                } else {
                    otExtendedMin++;
                    if (night) otNightMin++;
                }
            }
        }

        // 4. 초과근로는 결재 기능 전까지 전부 미승인
        int unapprovedOtMin = isHoliday ? otHolidayMin : otExtendedMin;

        return new WorkTimeResult(totalMin, regularMin, breakMin, excludedMin,
                nightMin, otExtendedMin, otNightMin, otHolidayMin, 0, unapprovedOtMin);
    }

    /** 근무 시간에 따라 필요한 휴게시간 (근로기준법: 4시간 30분, 8시간 1시간) */
    private static int requiredBreak(int workMinutes, int breakMinutes) {
        if (workMinutes >= FULL_BREAK_THRESHOLD) {
            return breakMinutes;
        }
        if (workMinutes >= HALF_BREAK_THRESHOLD) {
            return HALF_BREAK_MINUTES;
        }
        return 0;
    }

    /** 휴게는 점심으로 보고 주간 시간에서 앞쪽부터 뺀다. 주간이 부족하면 남은 만큼 앞에서 뺀다. */
    private static List<OffsetDateTime> removeBreak(List<OffsetDateTime> minutes, int breakMin) {
        List<OffsetDateTime> result = new ArrayList<>();
        int remaining = breakMin;
        for (OffsetDateTime t : minutes) {
            if (remaining > 0 && !isNight(t)) {
                remaining--;
                continue;
            }
            result.add(t);
        }
        if (remaining > 0) {
            result = new ArrayList<>(result.subList(Math.min(remaining, result.size()), result.size()));
        }
        return result;
    }

    private static boolean isNight(OffsetDateTime t) {
        int hour = t.atZoneSameInstant(ZONE).getHour();
        return hour >= NIGHT_START_HOUR || hour < NIGHT_END_HOUR;
    }

    private static OffsetDateTime toMinute(OffsetDateTime t) {
        return t.truncatedTo(ChronoUnit.MINUTES);
    }
}