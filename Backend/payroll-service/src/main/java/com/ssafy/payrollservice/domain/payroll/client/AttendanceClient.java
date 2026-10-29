package com.ssafy.payrollservice.domain.payroll.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.ssafy.payrollservice.global.exception.BusinessException;
import com.ssafy.payrollservice.global.exception.ErrorCode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Duration;
import java.time.YearMonth;

/**
 * 근태 서비스 호출.
 * GET /api/v1/attn/summary?empId=&month=YYYY-MM → ApiResult{data: AttendanceSummary}
 */
@Component
public class AttendanceClient {

    private final RestClient restClient;

    public AttendanceClient(@Value("${payroll.clients.attendance.base-url}") String baseUrl) {
        // 근태 서비스가 응답하지 않으면 급여 생성 요청이 끝없이 걸리지 않게
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(3));
        factory.setReadTimeout(Duration.ofSeconds(30));
        this.restClient = RestClient.builder().baseUrl(baseUrl).requestFactory(factory).build();
    }

    // TODO: 서비스 간 인증 방식이 정해지면 인증 헤더 추가
    public AttendanceSummary getSummary(Long empId, YearMonth month) {
        InternalResult<AttendanceSummary> res;
        try {
            res = restClient.get()
                    .uri("/api/v1/attn/summary?empId={empId}&month={month}", empId, month.toString())
                    .retrieve()
                    .body(new ParameterizedTypeReference<InternalResult<AttendanceSummary>>() {
                    });
        } catch (RestClientException e) {
            throw new BusinessException(ErrorCode.ATTENDANCE_CALL_FAILED, "근태 서비스 호출 실패: " + e.getMessage());
        }

        if (res == null || !res.success() || res.data() == null) {
            throw new BusinessException(ErrorCode.ATTENDANCE_CALL_FAILED, "근태 서비스 응답이 비어 있거나 실패입니다");
        }
        return res.data();
    }

    // 팀 공통 응답 형식 ApiResult{success, error, data}
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record InternalResult<T>(boolean success, T data) {
    }
}