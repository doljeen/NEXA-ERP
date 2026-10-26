package com.ssafy.user_service.domain.auth.service;

import com.ssafy.user_service.global.config.AppProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * 로그인 실패 횟수 제한 (Redis).
 * LF:{loginId} 실패 횟수(failureWindow 동안 유지), LK:{loginId} 잠금 표시(lockDuration 동안 유지).
 * 계정 존재 여부와 무관하게 loginId 기준으로 세어, 응답으로 계정 존재가 드러나지 않게 한다.
 */
@Service
@RequiredArgsConstructor
public class LoginAttemptService {

    private static final String FAIL_PREFIX = "LF:";
    private static final String LOCK_PREFIX = "LK:";

    private final StringRedisTemplate redisTemplate;
    private final AppProperties appProperties;

    public boolean isLocked(String loginId) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(LOCK_PREFIX + loginId));
    }

    /** 실패를 기록하고, 한도에 도달하면 잠금. */
    public void recordFailure(String loginId) {
        AppProperties.Login login = appProperties.login();
        String failKey = FAIL_PREFIX + loginId;
        Long failures = redisTemplate.opsForValue().increment(failKey);
        if (failures != null && failures == 1L) {
            redisTemplate.expire(failKey, login.failureWindow());
        }
        if (failures != null && failures >= login.maxFailures()) {
            redisTemplate.opsForValue().set(LOCK_PREFIX + loginId, "locked", login.lockDuration());
            redisTemplate.delete(failKey);
        }
    }

    public void reset(String loginId) {
        redisTemplate.delete(FAIL_PREFIX + loginId);
    }
}
