package com.ssafy.user_service.domain.user.service;

import com.ssafy.user_service.domain.user.dto.DutyResponse;

public interface DutyService {

    /** 직책 ID로 직책을 조회한다. 없는 ID면 BusinessException(NOT_FOUND) */
    DutyResponse getDuty(Long dutyId);
}
