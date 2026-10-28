package com.ssafy.user_service.global.security;

import com.ssafy.user_service.global.config.AppProperties;
import com.ssafy.user_service.global.exception.ErrorCode;
import com.ssafy.user_service.global.security.jwt.JwtKeyProvider;
import com.ssafy.user_service.global.security.jwt.JwtProperties;
import com.ssafy.user_service.global.security.jwt.JwtUtil;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class JwtFilterTest {

    private final TokenStore tokenStore = mock(TokenStore.class);
    private JwtUtil jwtUtil;
    private JwtKeyProvider keys;
    private JwtFilter filter;

    @BeforeEach
    void setUp() throws Exception {
        JwtProperties jwtProps = new JwtProperties("", "", 900_000L, 604_800_000L);
        keys = new JwtKeyProvider(jwtProps);
        keys.init();
        jwtUtil = new JwtUtil(keys, jwtProps);
        AppProperties appProps = new AppProperties(new AppProperties.Cookie(false, "Lax"),
                new AppProperties.Cors(List.of()), new AppProperties.Login(5, Duration.ofMinutes(10), Duration.ofMinutes(15)));
        filter = new JwtFilter(jwtUtil, tokenStore, new TokenCookieManager(appProps, jwtProps));
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private MockHttpServletRequest run(String accessCookie) throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/v1/x");
        if (accessCookie != null) req.setCookies(new Cookie(SecurityConstants.ACCESS_COOKIE, accessCookie));
        MockFilterChain chain = new MockFilterChain();
        filter.doFilter(req, new MockHttpServletResponse(), chain);
        assertThat(chain.getRequest()).as("다음 필터로 항상 넘어간다").isNotNull();
        return req;
    }

    private MockHttpServletRequest runWithHeader(String authorization) throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/v1/x");
        req.addHeader("Authorization", authorization);
        filter.doFilter(req, new MockHttpServletResponse(), new MockFilterChain());
        return req;
    }

    private static Object errorOf(MockHttpServletRequest req) {
        return req.getAttribute(JwtFilter.JWT_ERROR_ATTRIBUTE);
    }

    @Test
    void 쿠키가_없으면_인증없이_통과() throws Exception {
        MockHttpServletRequest req = run(null);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        assertThat(errorOf(req)).isNull();
    }

    @Test
    void 유효한_access면_AuthUser와_ROLE_권한을_등록() throws Exception {
        run(jwtUtil.generateAccessToken(42L, Role.ADMIN).value());
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertThat(auth).isInstanceOf(JwtAuthenticationToken.class);
        assertThat(((AuthUser) auth.getPrincipal()).employeeId()).isEqualTo(42L);
        assertThat(auth.getAuthorities()).extracting("authority").containsExactly("ROLE_ADMIN");
    }

    @Test
    void refresh를_access_자리에_넣으면_TOKEN_INVALID() throws Exception {
        MockHttpServletRequest req = run(jwtUtil.generateRefreshToken(42L).value());
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        assertThat(errorOf(req)).isEqualTo(ErrorCode.TOKEN_INVALID);
    }

    @Test
    void 블랙리스트_토큰은_TOKEN_BLACKLISTED() throws Exception {
        var token = jwtUtil.generateAccessToken(42L, Role.USER);
        when(tokenStore.isBlacklisted(token.id())).thenReturn(true);
        MockHttpServletRequest req = run(token.value());
        assertThat(errorOf(req)).isEqualTo(ErrorCode.TOKEN_BLACKLISTED);
    }

    // ===== Authorization: Bearer 헤더 (서비스 간 호출) =====

    @Test
    void 쿠키가_없으면_Bearer_헤더의_access로_인증() throws Exception {
        runWithHeader("Bearer " + jwtUtil.generateAccessToken(7L, Role.USER).value());
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertThat(((AuthUser) auth.getPrincipal()).employeeId()).isEqualTo(7L);
        assertThat(auth.getAuthorities()).extracting("authority").containsExactly("ROLE_USER");
    }

    @Test
    void Bearer_헤더도_블랙리스트와_type_검사를_거침() throws Exception {
        var token = jwtUtil.generateAccessToken(7L, Role.USER);
        when(tokenStore.isBlacklisted(token.id())).thenReturn(true);
        assertThat(errorOf(runWithHeader("Bearer " + token.value()))).isEqualTo(ErrorCode.TOKEN_BLACKLISTED);
        assertThat(errorOf(runWithHeader("Bearer " + jwtUtil.generateRefreshToken(7L).value())))
                .isEqualTo(ErrorCode.TOKEN_INVALID);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void Bearer_형식이_아닌_헤더는_토큰_없음으로_통과() throws Exception {
        MockHttpServletRequest req = runWithHeader("Basic dXNlcjpwYXNz");
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        assertThat(errorOf(req)).isNull();
    }

    @Test
    void 쿠키와_헤더가_모두_있으면_쿠키가_우선() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/v1/x");
        req.setCookies(new Cookie(SecurityConstants.ACCESS_COOKIE, jwtUtil.generateAccessToken(1L, Role.USER).value()));
        req.addHeader("Authorization", "Bearer " + jwtUtil.generateAccessToken(2L, Role.ADMIN).value());
        filter.doFilter(req, new MockHttpServletResponse(), new MockFilterChain());
        assertThat(((AuthUser) SecurityContextHolder.getContext().getAuthentication().getPrincipal()).employeeId())
                .isEqualTo(1L);
    }

    @Test
    void 만료_토큰은_TOKEN_EXPIRED_위변조는_TOKEN_INVALID() throws Exception {
        JwtUtil expiredIssuer = new JwtUtil(keys, new JwtProperties("", "", -1_000L, 1L));
        assertThat(errorOf(run(expiredIssuer.generateAccessToken(1L, Role.USER).value()))).isEqualTo(ErrorCode.TOKEN_EXPIRED);
        assertThat(errorOf(run("garbage.token.value"))).isEqualTo(ErrorCode.TOKEN_INVALID);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }
}
