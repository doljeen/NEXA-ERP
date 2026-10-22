package com.ssafy.approval_service.domain.approval.controller;

import com.ssafy.approval_service.domain.approval.service.ApprovalService;
import com.ssafy.approval_service.global.security.SecurityConstants;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping(SecurityConstants.APPROVAL_BASE_PATH)
public class ApprovalController {

    private final ApprovalService approvalService;

    

}
