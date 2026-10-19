package com.ssafy.user_service.global.security;

import com.ssafy.user_service.global.exception.ErrorCode;
import com.ssafy.user_service.global.security.jwt.JwtUtil;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * accessToken 쿠키를 검증해 SecurityContext에 인증을 등록한다.
 * 토큰이 없거나 유효하지 않으면 인증을 등록하지 않고 다음 필터로 넘긴다.
 * 보호된 경로라면 이후 AuthorizationFilter가 401을 발생시키고 JwtAuthenticationEntryPoint가
 * 응답을 작성하며, 그때 이 필터가 request attribute에 남긴 ErrorCode를 사용한다.
 * (이 필터는 ExceptionTranslationFilter보다 앞에 있으므로 여기서 예외를 던지면 500이 된다)
 */
@Component
@RequiredArgsConstructor
public class JwtFilter extends OncePerRequestFilter {

    public static final String JWT_ERROR_ATTRIBUTE = "jwtError";

    private final JwtUtil jwtUtil;
    private final TokenStore tokenStore;
    private final TokenCookieManager cookieManager;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String token = cookieManager.readAccessToken(request);

        // 토큰이 없는 경우는 오류가 아니라 미인증 상태 → 그대로 통과
        if (token == null) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            // 1) 서명/만료 검증 (검증 전에는 Redis 등 외부 조회를 하지 않는다)
            Claims claims = jwtUtil.parseClaims(token);

            // 2) refreshToken을 accessToken 자리에 넣는 것을 차단
            if (!JwtUtil.TYPE_ACCESS.equals(jwtUtil.getType(claims))) {
                reject(request, ErrorCode.TOKEN_INVALID);
            }
            // 3) 로그아웃된 토큰(jti 블랙리스트) 차단
            else if (tokenStore.isBlacklisted(jwtUtil.getTokenId(claims))) {
                reject(request, ErrorCode.TOKEN_BLACKLISTED);
            }
            else {
                Role role = jwtUtil.getRole(claims);
                List<GrantedAuthority> authorities = role == null
                        ? List.of()
                        : List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
                AuthUser principal = new AuthUser(jwtUtil.getUserId(claims), role);
                SecurityContextHolder.getContext()
                        .setAuthentication(new JwtAuthenticationToken(principal, authorities));
            }
        } catch (ExpiredJwtException e) {
            reject(request, ErrorCode.TOKEN_EXPIRED);
        } catch (JwtException | IllegalArgumentException e) {
            // 위변조, 형식 오류, subject 파싱 실패 등
            reject(request, ErrorCode.TOKEN_INVALID);
        }

        filterChain.doFilter(request, response);
    }

    private void reject(HttpServletRequest request, ErrorCode errorCode) {
        SecurityContextHolder.clearContext();
        request.setAttribute(JWT_ERROR_ATTRIBUTE, errorCode);
    }
}
