package com.ssafy.attendanceservice.domain.attendance.service;

import com.ssafy.attendanceservice.domain.attendance.dto.AttendanceRequestDetailResponse;
import com.ssafy.attendanceservice.domain.attendance.dto.AttendanceRequestPreviewRequest;
import com.ssafy.attendanceservice.domain.attendance.dto.AttendanceRequestPreviewResponse;
import com.ssafy.attendanceservice.domain.attendance.dto.AttendanceRequestSubmitRequest;
import com.ssafy.attendanceservice.domain.attendance.dto.AttendanceRequestSummaryResponse;
import com.ssafy.attendanceservice.domain.attendance.entity.enums.AttendanceRequestType;

import java.util.List;

public interface AttendanceRequestService {

    /** 신청 검증 및 결과 미리보기 (저장하지 않음) */
    AttendanceRequestPreviewResponse preview(Long empId, AttendanceRequestPreviewRequest request);

    /**
     * 신청서 상신. 미리보기와 같은 검증 후 저장하고 결재 문서를 만든다.
     * 결재 문서 생성에 실패하면 신청서도 저장하지 않는다 (BusinessException(APPROVAL_*)).
     */
    AttendanceRequestDetailResponse submit(Long empId, AttendanceRequestSubmitRequest request);

    /** 내 신청 이력 (해당 연도에 상신한 신청, 최신순). requestType이 null이면 전체 유형 */
    List<AttendanceRequestSummaryResponse> getMyRequests(Long empId, int year, AttendanceRequestType requestType);

    /** 내 신청서 상세. 없거나 본인 신청서가 아니면 BusinessException(ATTENDANCE_REQUEST_NOT_FOUND) */
    AttendanceRequestDetailResponse getMyRequest(Long empId, Long requestId);
}
