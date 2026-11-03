package com.ssafy.attendanceservice.domain.attendance.event;

import com.ssafy.attendanceservice.domain.attendance.entity.enums.AttendanceRequestStatus;
import com.ssafy.attendanceservice.domain.attendance.service.AttendanceRequestService;
import com.ssafy.attendanceservice.global.exception.BusinessException;
import com.ssafy.attendanceservice.global.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/** 결재 결과 이벤트 → 근태 신청 반영 호출 (Kafka·스프링 없이) */
class ApprovalResultListenerTest {

    private final AttendanceRequestService service = mock(AttendanceRequestService.class);
    private final ApprovalResultListener listener = new ApprovalResultListener(JsonMapper.builder().build(), service);

    private static String event(String sourceType, long sourceId, String status) {
        return """
                {"eventId":"6f1c2a9e-0b7d-4c3f-9a51-2e8d7c4b1f03","eventVersion":1,"eventType":"DOCUMENT_COMPLETED",
                 "occurredAt":"2026-11-03T13:20:01.123+09:00","documentId":128,"docNo":"OVERTIME-2026-0001",
                 "formType":"OVERTIME","sourceType":"%s","sourceId":%d,
                 "requestKey":"3f6b2c1e-8d4a-4b7f-9c2e-1a5d7e9f0b12","status":"%s"}""".formatted(sourceType, sourceId, status);
    }

    @Test
    void 근태_문서_승인은_신청서_승인으로_반영() {
        listener.onApprovalResult(event("ATTENDANCE", 42, "APPROVED"));

        verify(service).applyApprovalResult(42L, AttendanceRequestStatus.APPROVED);
    }

    @Test
    void 반려는_반려로() {
        listener.onApprovalResult(event("ATTENDANCE", 42, "REJECTED"));

        verify(service).applyApprovalResult(42L, AttendanceRequestStatus.REJECTED);
    }

    @Test
    void 결재_서비스의_CANCEL은_근태의_CANCELED로() {
        listener.onApprovalResult(event("ATTENDANCE", 42, "CANCEL"));

        verify(service).applyApprovalResult(42L, AttendanceRequestStatus.CANCELED);
    }

    @Test
    void 휴가_문서는_무시() {
        listener.onApprovalResult(event("LEAVE", 42, "APPROVED"));

        verify(service, never()).applyApprovalResult(anyLong(), any());
    }

    @Test
    void 모르는_결과는_무시() {
        listener.onApprovalResult(event("ATTENDANCE", 42, "HOLD"));

        verify(service, never()).applyApprovalResult(anyLong(), any());
    }

    @Test
    void 신청서가_없으면_예외를_그대로_던져_재시도되게() {
        doThrow(new BusinessException(ErrorCode.ATTENDANCE_REQUEST_NOT_FOUND))
                .when(service).applyApprovalResult(42L, AttendanceRequestStatus.APPROVED);

        assertThatThrownBy(() -> listener.onApprovalResult(event("ATTENDANCE", 42, "APPROVED")))
                .isInstanceOf(BusinessException.class);
    }
}
