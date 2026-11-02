package com.ssafy.approval_service.domain.document.model.entity.enums;

/**
 * 문서양식. 양식마다 만들 수 있는 출처가 정해져 있다.
 * 휴가/근태 문서는 해당 서비스의 신청을 거쳐야 승인 결과가 원본 신청에 반영되므로 직접 작성할 수 없다.
 */
public enum FormType {
    LEAVE(SourceType.LEAVE),                // 휴가 신청
    OVERTIME(SourceType.ATTENDANCE),        // 초과근무 신청
    TIME_FIX(SourceType.ATTENDANCE),        // 근무시간 수정 신청
    HOLIDAY_SUBST(SourceType.ATTENDANCE),   // 휴일대체 신청
    BUSINESS_TRIP(SourceType.DIRECT),       // 출장 신청
    GENERAL(SourceType.DIRECT);             // 일반 기안

    private final SourceType requiredSource;

    FormType(SourceType requiredSource) {
        this.requiredSource = requiredSource;
    }

    /** 이 양식의 문서를 만들 수 있는 출처 */
    public SourceType requiredSource() {
        return requiredSource;
    }
}
