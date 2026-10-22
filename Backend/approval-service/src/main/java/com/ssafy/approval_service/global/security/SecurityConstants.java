package com.ssafy.approval_service.global.security;

public class SecurityConstants {
    public static final String DOCUMENT_BASE_PATH = "/api/v1/documents";
    public static final String APPROVAL_BASE_PATH = "/api/v1/approvals";

    /** 게이트웨이가 JWT 검증 후 넣어주는 사원ID 헤더 */
    public static final String USER_ID_HEADER = "X-User-Id";

    private SecurityConstants() {
    }
}
