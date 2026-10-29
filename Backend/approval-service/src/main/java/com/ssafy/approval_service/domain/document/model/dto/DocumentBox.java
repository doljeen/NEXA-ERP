package com.ssafy.approval_service.domain.document.model.dto;

/** 목록 조회의 문서함 구분 (box 파라미터). 지정하지 않으면 조회할 수 있는 문서 전체 */
public enum DocumentBox {
    DRAFTED     // 기안함: 요청자가 기안한 문서 (관리자도 본인 기안만)
}
