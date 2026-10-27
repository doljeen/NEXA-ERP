package com.ssafy.approval_service.global.security;

import org.springframework.security.core.annotation.AuthenticationPrincipal;

import java.lang.annotation.*;

@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@AuthenticationPrincipal(expression = "T(com.ssafy.approval_service.global.security.Requester).from(#this)")
public @interface CurrentRequester {
}
