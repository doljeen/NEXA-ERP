package com.ssafy.nexa.security;

import org.springframework.security.oauth2.jwt.Jwt;

/** 요청한 사원. access 토큰의 sub(사원 ID)와 role 클레임으로 만든다 */
public record AuthUser(Long employeeId, String role) {

    public static final String ADMIN_ROLE = "ADMIN";

    public static AuthUser from(Jwt jwt) {
        return new AuthUser(Long.valueOf(jwt.getSubject()), jwt.getClaimAsString("role"));
    }

    public boolean isAdmin() {
        return ADMIN_ROLE.equals(role);
    }
}
