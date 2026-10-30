package com.ssafy.approval_service.domain.document.model.entity.enums;

/** 문서양식. ERD에 값이 명시되지 않아 임시 값으로 구성 (확정 시 수정) */
public enum FormType {
    LEAVE,          // 휴가 신청
    OVERTIME,       // 연장 근무 신청
    BUSINESS_TRIP,  // 출장 신청
    GENERAL         // 일반 기안
}
