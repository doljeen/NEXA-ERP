package com.ssafy.leaveservice.client.attendance;

import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import java.net.http.HttpClient;
import java.time.LocalDate;
import java.util.*;

@Component
public class RestHolidayClient implements HolidayClient {
    private final AttendanceHolidayProperties properties;
    private final DiscoveryClient discoveryClient;
    private final ObjectMapper objectMapper;
    private final RestClient restClient;

    public RestHolidayClient(
            AttendanceHolidayProperties properties,
            DiscoveryClient discoveryClient,
            ObjectMapper objectMapper
    ) {
        this.properties = properties;
        this.discoveryClient = discoveryClient;
        this.objectMapper = objectMapper;
        HttpClient httpClient = HttpClient.newBuilder().connectTimeout(properties.connectTimeout())
                .followRedirects(HttpClient.Redirect.NEVER).build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(properties.readTimeout());
        this.restClient = RestClient.builder().requestFactory(factory).build();
    }

    @Override
    public HolidayResult findHolidays(LocalDate startDate, LocalDate endDate) {
        try {
            String baseUrl = properties.baseUrl();
            if (baseUrl == null || baseUrl.isBlank()) {
                List<org.springframework.cloud.client.ServiceInstance> instances = discoveryClient.getInstances(properties.serviceId());
                if (instances.isEmpty()) {
                    return HolidayResult.unavailable();
                }
                baseUrl = instances.getFirst().getUri().toString();
            }
            // TODO: 근태 내부 인증 계약 확정 후 서비스 자격 증명을 연동한다. 401/403은 현재도 검증 불가다.
            ResponseEntity<String> response = restClient.get()
                    .uri(org.springframework.web.util.UriComponentsBuilder.fromUriString(baseUrl)
                            .path("/api/v1/attendance/holidays").queryParam("startDate", startDate)
                            .queryParam("endDate", endDate).build().toUri())
                    .retrieve().toEntity(String.class);
            if (response.getStatusCode().value() != 200 || response.getBody() == null) {
                return HolidayResult.unavailable();
            }
            return parseResponse(response.getBody(), startDate, endDate);
        } catch (RuntimeException exception) {
            // 외부 장애를 공휴일 없음으로 바꾸지 않는다. 인증/응답 본문은 기록하지 않는다.
            return HolidayResult.unavailable();
        }
    }

    private HolidayResult parseResponse(String body, LocalDate startDate, LocalDate endDate) {
        try {
            JsonNode response = objectMapper.readTree(body);
            if (response == null || !response.isObject() || !response.path("success").isBoolean()
                    || !response.path("success").asBoolean() || !response.path("error").isNull()
                    || !response.path("data").isArray()) {
                return HolidayResult.unavailable();
            }
            List<Holiday> holidays = new ArrayList<>();
            Set<LocalDate> seen = new HashSet<>();
            for (JsonNode item : response.get("data")) {
                if (!item.isObject() || !item.path("holidayDate").isString() || !item.path("name").isString()
                        || item.path("name").asString().isBlank() || !item.path("type").isString()) {
                    return HolidayResult.unavailable();
                }
                LocalDate date = LocalDate.parse(item.get("holidayDate").asString());
                HolidayType type = HolidayType.valueOf(item.get("type").asString());
                if (date.isBefore(startDate) || date.isAfter(endDate) || !seen.add(date)) {
                    return HolidayResult.unavailable();
                }
                holidays.add(new Holiday(date, item.get("name").asString(), type));
            }
            return new HolidayResult(true, List.copyOf(holidays));
        } catch (RuntimeException exception) {
            return HolidayResult.unavailable();
        }
    }
}
