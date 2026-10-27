package com.ssafy.approval_service.global.security;

import org.springframework.security.oauth2.jwt.Jwt;

/** 요청한 사원. access 토큰의 sub(사원 ID)와 role 클레임으로 만든다 (문서 조회 권한 검사용) */
public record Requester(Long employeeId, boolean admin) {

    private static final String ADMIN_ROLE = "ADMIN";

    public static Requester from(Jwt jwt) {
        return new Requester(Long.valueOf(jwt.getSubject()), ADMIN_ROLE.equals(jwt.getClaimAsString("role")));
    }
}
