package com.ssafy.attendanceservice.domain.attendance.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.ssafy.attendanceservice.global.exception.BusinessException;
import com.ssafy.attendanceservice.global.exception.ErrorCode;
import com.ssafy.nexa.security.BearerTokenRelayInterceptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Duration;

/**
 * 결재 서비스 호출 (DOCS/approval-document-source.md).
 * POST /api/v1/documents → ApiResult{data: {documentId, docNo}}
 * 신청한 사원의 토큰으로 호출해야 그 사원이 기안자가 된다.
 */
@Component
public class ApprovalClient {

    private final RestClient restClient;

    @Autowired
    public ApprovalClient(@Value("${attendance.clients.approval.base-url}") String baseUrl,
                          BearerTokenRelayInterceptor bearerTokenRelay) {
        this(RestClient.builder().requestFactory(timeoutRequestFactory()), baseUrl, bearerTokenRelay);
    }

    // 신청한 사원의 access 토큰을 결재 서비스로 넘긴다 (결재 서비스가 다시 검증).
    // 테스트에서 요청 팩토리를 바꿔 끼울 수 있게 빌더를 받는다
    ApprovalClient(RestClient.Builder builder, String baseUrl, BearerTokenRelayInterceptor bearerTokenRelay) {
        this.restClient = builder.baseUrl(baseUrl).requestInterceptor(bearerTokenRelay).build();
    }

    // 상신 트랜잭션 안에서 호출하므로 오래 기다리지 않는다 (결재 서비스가 결재선 사원을 user-service로 확인함)
    private static SimpleClientHttpRequestFactory timeoutRequestFactory() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(3));
        factory.setReadTimeout(Duration.ofSeconds(10));
        return factory;
    }

    public ApprovalDocument createDocument(ApprovalDocumentCreateRequest request) {
        InternalResult<ApprovalDocument> res;
        try {
            res = restClient.post()
                    .uri("/api/v1/documents")
                    .body(request)
                    .retrieve()
                    .body(new ParameterizedTypeReference<InternalResult<ApprovalDocument>>() {
                    });
        } catch (HttpClientErrorException.BadRequest e) {
            // 결재선 오류 등 요청 값 문제: 결재 서비스 메시지를 그대로 전달
            throw new BusinessException(ErrorCode.APPROVAL_REQUEST_REJECTED, rejectMessage(e));
        } catch (RestClientException e) {
            throw new BusinessException(ErrorCode.APPROVAL_CALL_FAILED, "결재 서비스 호출 실패: " + e.getMessage());
        }

        if (res == null || !res.success() || res.data() == null || res.data().documentId() == null) {
            throw new BusinessException(ErrorCode.APPROVAL_CALL_FAILED, "결재 서비스 응답이 비어 있거나 실패입니다");
        }
        return res.data();
    }

    /** 400 응답 본문의 error.message. 읽을 수 없으면 기본 메시지 */
    private static String rejectMessage(HttpClientErrorException e) {
        try {
            InternalResult<?> body = e.getResponseBodyAs(InternalResult.class);
            if (body != null && body.error() != null && body.error().message() != null) {
                return body.error().message();
            }
        } catch (RuntimeException ignored) {
            // 본문 형식이 다르면 기본 메시지
        }
        return ErrorCode.APPROVAL_REQUEST_REJECTED.getMessage();
    }

    // 팀 공통 응답 형식 ApiResult{success, error, data}
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record InternalResult<T>(boolean success, ErrorBody error, T data) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ErrorBody(String code, String message) {
    }
}
