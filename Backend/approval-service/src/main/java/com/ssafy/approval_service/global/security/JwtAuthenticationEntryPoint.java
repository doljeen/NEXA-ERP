package com.ssafy.approval_service.global.security;

import com.ssafy.approval_service.global.dto.ApiResult;
import com.ssafy.approval_service.global.exception.ErrorCode;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    private ErrorCode resolve(AuthenticationException e) {
        if (!(e instanceof OAuth2AuthenticationException)) {
            return ErrorCode.UNAUTHORIZED;
        }
        String message = e.getMessage();
        return message != null && message.contains("expired")
                ? ErrorCode.TOKEN_EXPIRED
                : ErrorCode.TOKEN_INVALID;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException authException) throws IOException, ServletException {
        ErrorCode errorCode = resolve(authException);

        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.setStatus(errorCode.getStatus().value());
        response.getWriter().write(objectMapper.writeValueAsString(ApiResult.fail(errorCode)));
    }
}
