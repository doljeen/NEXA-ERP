package com.ssafy.attendanceservice.domain.attendance.event;

import com.ssafy.attendanceservice.domain.attendance.entity.enums.AttendanceRequestStatus;
import com.ssafy.attendanceservice.domain.attendance.service.AttendanceRequestService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * 결재 결과 이벤트 수신. 근태 문서의 결과만 신청서에 반영한다.
 * 처리 중 예외는 잡지 않는다: 재시도 후 DLT (KafkaConfig). 신청서가 아직 커밋 전이면 재시도에서 처리된다
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ApprovalResultListener {

    static final String TOPIC = "approval.document.result";
    private static final String EVENT_COMPLETED = "DOCUMENT_COMPLETED";
    private static final String SOURCE_ATTENDANCE = "ATTENDANCE";

    private final JsonMapper jsonMapper;
    private final AttendanceRequestService attendanceRequestService;

    @KafkaListener(topics = TOPIC)
    public void onApprovalResult(String payload) {
        ApprovalResultEvent event = jsonMapper.readValue(payload, ApprovalResultEvent.class);

        // 다른 종류의 이벤트, 다른 서비스(휴가 등)의 문서는 무시
        if (!EVENT_COMPLETED.equals(event.eventType()) || !SOURCE_ATTENDANCE.equals(event.sourceType())) {
            return;
        }

        AttendanceRequestStatus result = toRequestStatus(event.status());
        if (result == null) {
            log.warn("모르는 결재 결과라 무시: eventId={}, status={}", event.eventId(), event.status());
            return;
        }

        log.info("결재 결과 반영: eventId={}, documentId={}, attReqId={}, status={}",
                event.eventId(), event.documentId(), event.sourceId(), event.status());
        attendanceRequestService.applyApprovalResult(event.sourceId(), result);
    }

    /** 결재 문서 상태 → 근태 신청 상태 */
    private static AttendanceRequestStatus toRequestStatus(String status) {
        return switch (status == null ? "" : status) {
            case "APPROVED" -> AttendanceRequestStatus.APPROVED;
            case "REJECTED" -> AttendanceRequestStatus.REJECTED;
            case "CANCEL" -> AttendanceRequestStatus.CANCELED;
            default -> null;
        };
    }
}
