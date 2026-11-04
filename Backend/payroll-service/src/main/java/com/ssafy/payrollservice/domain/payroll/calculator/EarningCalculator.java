package com.ssafy.payrollservice.domain.payroll.calculator;

/**
 * 근태 월 집계(분)와 월 기본급으로 지급 항목을 계산한다. DB/Spring에 의존하지 않는 순수 계산 로직.
 * 금액은 모두 원 미만 버림.
 */
public final class EarningCalculator {

    /** 월 소정근로시간: (주 40시간 + 주휴 8시간) × 365 ÷ 7 ÷ 12 ≒ 209 */
    static final int MONTHLY_STD_HOURS = 209;

    private EarningCalculator() {
    }

    /** 무급휴가 없이 계산 */
    public static EarningResult calculate(int baseAmount, int otExtendedMin, int nightMin, int otHolidayMin) {
        return calculate(baseAmount, otExtendedMin, nightMin, otHolidayMin, 0);
    }

    /**
     * @param baseAmount     월 기본급
     * @param otExtendedMin  연장 근로시간(분)
     * @param nightMin       야간 근로시간 전체(분) = 소정 중 야간 + 초과 야간
     * @param otHolidayMin   휴일 근로시간(분)
     * @param unpaidLeaveMin 무급휴가 시간(분). 시급 × 시간만큼 지급에서 뺀다
     */
    public static EarningResult calculate(int baseAmount, int otExtendedMin, int nightMin, int otHolidayMin,
                                          int unpaidLeaveMin) {
        int hourlyWage = baseAmount / MONTHLY_STD_HOURS;

        return new EarningResult(
                hourlyWage,
                baseAmount,
                pay(otExtendedMin, hourlyWage, 3, 2),   // 1.5배
                pay(nightMin, hourlyWage, 1, 2),        // 0.5배 (가산분만)
                pay(otHolidayMin, hourlyWage, 3, 2),    // 1.5배
                0,  // TODO: 휴가 서비스 연동 후 연차수당
                0,
                0,
                -pay(unpaidLeaveMin, hourlyWage, 1, 1)); // 1배를 뺀다
    }

    /** 분 × 시급 × (분자/분모) ÷ 60, 원 미만 버림 */
    private static int pay(int minutes, int hourlyWage, int numerator, int denominator) {
        return (int) ((long) minutes * hourlyWage * numerator / (denominator * 60L));
    }
}