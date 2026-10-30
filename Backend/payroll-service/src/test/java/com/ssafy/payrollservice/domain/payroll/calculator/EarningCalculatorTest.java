package com.ssafy.payrollservice.domain.payroll.calculator;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class EarningCalculatorTest {

    private static final int BASE = 3_500_000;  // 시급 16,746원

    @Test
    @DisplayName("초과근로 없음: 기본급만 지급")
    void baseOnly() {
        EarningResult r = EarningCalculator.calculate(BASE, 0, 0, 0);

        assertEquals(16_746, r.hourlyWage());
        assertEquals(BASE, r.basePay());
        assertEquals(0, r.otExtendedPay());
        assertEquals(0, r.otNightPay());
        assertEquals(0, r.otHolidayPay());
        assertEquals(BASE, r.total());
    }

    @Test
    @DisplayName("연장 10시간, 야간 2시간, 휴일 8시간")
    void overtime() {
        EarningResult r = EarningCalculator.calculate(BASE, 600, 120, 480);

        assertEquals(251_190, r.otExtendedPay());   // 10h × 16,746 × 1.5
        assertEquals(16_746, r.otNightPay());       // 2h × 16,746 × 0.5
        assertEquals(200_952, r.otHolidayPay());    // 8h × 16,746 × 1.5
        assertEquals(3_968_888, r.total());
    }

    @Test
    @DisplayName("원 미만 버림: 연장 1분 = 418.65원 → 418원")
    void truncate() {
        EarningResult r = EarningCalculator.calculate(BASE, 1, 0, 0);

        assertEquals(418, r.otExtendedPay());
    }
}