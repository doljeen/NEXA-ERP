package com.ssafy.leaveservice.client.attendance;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.cloud.client.DefaultServiceInstance;
import tools.jackson.databind.json.JsonMapper;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class RestHolidayClientTest {
    private HttpServer server;
    private ExecutorService executor;
    private String body;
    private int status;
    private long delay;
    private final AtomicReference<String> requestUri = new AtomicReference<>();
    private final DiscoveryClient discovery = mock(DiscoveryClient.class);
    private final LocalDate start = LocalDate.of(2030,1,7);
    private final LocalDate end = LocalDate.of(2030,1,10);

    @BeforeEach
    void setUp() throws Exception {
        body = "{\"success\":true,\"error\":null,\"data\":[]}";
        status=200;
        server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        executor=Executors.newCachedThreadPool();
        server.setExecutor(executor);
        server.createContext("/", exchange -> {
            requestUri.set(exchange.getRequestURI().toString());
            try {
                if (delay>0) { Thread.sleep(delay); }
                byte[] bytes=body.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type","application/json");
                exchange.sendResponseHeaders(status,bytes.length);
                exchange.getResponseBody().write(bytes);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            } finally {
                exchange.close();
            }
        });
        server.start();
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
        executor.shutdownNow();
    }

    private RestHolidayClient client(long readMillis) {
        return new RestHolidayClient(new AttendanceHolidayProperties("http://127.0.0.1:"+server.getAddress().getPort(),
                "attendance-service",Duration.ofMillis(500),Duration.ofMillis(readMillis)),discovery,JsonMapper.builder().build());
    }

    @Test
    void emptySuccessAndExactInclusiveRangeQuery() {
        assertThat(client(2000).findHolidays(start,end)).isEqualTo(new HolidayClient.HolidayResult(true,List.of()));
        assertThat(requestUri.get()).isEqualTo("/api/v1/attendance/holidays?startDate=2030-01-07&endDate=2030-01-10");
        verifyNoInteractions(discovery);
    }

    @Test
    void discoveryUsesConfiguredRegistrationAndParsesBothHolidayTypes() {
        body="{\"success\":true,\"error\":null,\"data\":[{\"holidayDate\":\"2030-01-07\",\"name\":\"법정\",\"type\":\"LEGAL\"},{\"holidayDate\":\"2030-01-10\",\"name\":\"회사\",\"type\":\"COMPANY\"}]}";
        when(discovery.getInstances("attendance-service")).thenReturn(List.of(new DefaultServiceInstance(
                "test","attendance-service","127.0.0.1",server.getAddress().getPort(),false)));
        RestHolidayClient client = new RestHolidayClient(new AttendanceHolidayProperties("","attendance-service",
                Duration.ofSeconds(1),Duration.ofSeconds(2)),discovery,JsonMapper.builder().build());
        var result=client.findHolidays(start,end);
        assertThat(result.verified()).isTrue();
        assertThat(result.holidays()).extracting(HolidayClient.Holiday::type)
                .containsExactly(HolidayClient.HolidayType.LEGAL,HolidayClient.HolidayType.COMPANY);
    }

    @ParameterizedTest
    @ValueSource(strings={
            "null","[]","bad","{}","{\"success\":false,\"error\":null,\"data\":[]}",
            "{\"success\":true,\"error\":null,\"data\":null}",
            "{\"success\":\"true\",\"error\":null,\"data\":[]}",
            "{\"success\":true,\"error\":null,\"data\":{}}",
            "{\"success\":true,\"error\":null,\"data\":[null]}",
            "{\"success\":true,\"error\":null,\"data\":[{\"holidayDate\":\"2030-01-07\",\"name\":\"x\",\"type\":\"UNKNOWN\"}]}",
            "{\"success\":true,\"error\":null,\"data\":[{\"holidayDate\":\"2030-01-07\",\"name\":\" \",\"type\":\"LEGAL\"}]}",
            "{\"success\":true,\"error\":null,\"data\":[{\"holidayDate\":\"2020-01-07\",\"name\":\"x\",\"type\":\"LEGAL\"}]}",
            "{\"success\":true,\"error\":null,\"data\":[{\"holidayDate\":\"bad\",\"name\":\"x\",\"type\":\"LEGAL\"}]}"
    })
    void malformedContractNeverBecomesEmptyHolidaySuccess(String response) {
        body=response;
        assertThat(client(2000).findHolidays(start,end).verified()).isFalse();
    }

    @ParameterizedTest
    @ValueSource(ints={400,401,403,500,503,302})
    void non200IsUnverifiable(int httpStatus) {
        status=httpStatus;
        assertThat(client(2000).findHolidays(start,end).verified()).isFalse();
    }

    @Test
    void timeoutDoesNotBecomeEmptySuccess() {
        delay=600;
        assertThat(client(100).findHolidays(start,end).verified()).isFalse();
    }

    @Test
    void connectionFailureDoesNotBecomeEmptySuccess() {
        int port=server.getAddress().getPort();
        server.stop(0);
        assertThat(client(500).findHolidays(start,end).verified()).isFalse();
    }

    @Test
    void noDiscoveredServiceIsUnverifiable() {
        RestHolidayClient client = new RestHolidayClient(new AttendanceHolidayProperties("","missing",
                Duration.ofSeconds(1),Duration.ofSeconds(1)),discovery,JsonMapper.builder().build());
        assertThat(client.findHolidays(start,end).verified()).isFalse();
    }

    @Test
    void discoveryFailureIsUnverifiable() {
        when(discovery.getInstances("attendance-service")).thenThrow(new IllegalStateException("registry unavailable"));
        RestHolidayClient client = new RestHolidayClient(new AttendanceHolidayProperties("","attendance-service",
                Duration.ofSeconds(1),Duration.ofSeconds(1)),discovery,JsonMapper.builder().build());
        assertThat(client.findHolidays(start,end).verified()).isFalse();
    }
}
