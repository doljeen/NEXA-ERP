package com.ssafy.attendanceservice.domain.attendance.dto;

import com.ssafy.attendanceservice.domain.attendance.entity.enums.AttendanceRequestType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;

/** 근태 신청서 상신 요청. 신청 내용은 미리보기와 같고, 사유와 결재선이 추가된다. */
public record AttendanceRequestSubmitRequest(
        @NotNull AttendanceRequestType reqType,                 // 신청 유형
        @NotNull LocalDate targetDate,                          // 대상일
        LocalDate substDate,                                    // 대체 휴일 (HOLIDAY_SUBST)
        @NotBlank @Size(max = 500) String reason,               // 신청 사유
        List<AttendanceRequestPreviewRequest.Item> items,       // 신청 상세 (TIME_FIX, OVERTIME)
        @NotEmpty @Valid List<ApprovalLine> approvalLines       // 결재선 (화면에서 직원이 지정)
) {

    public record ApprovalLine(
            @NotNull Long empId,            // 결재선 사원 ID
            @NotNull @Min(1) Integer step,  // 결재 순서 (1부터)
            @NotBlank String role           // APPROVER / AGREEMENT / REFERENCE / VIEWER
    ) {
    }

    /** 미리보기와 같은 검증을 하기 위한 변환 */
    public AttendanceRequestPreviewRequest toPreviewRequest() {
        return new AttendanceRequestPreviewRequest(reqType, targetDate, substDate, items);
    }

    /** items가 없으면 빈 목록 */
    public List<AttendanceRequestPreviewRequest.Item> itemsOrEmpty() {
        return items == null ? List.of() : items;
    }
}
