package com.ssafy.leaveservice.domain.leavetype;

import com.ssafy.leaveservice.global.auth.EmployeeAccess;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import static org.assertj.core.api.Assertions.*;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "spring.cloud.config.enabled=false",
        "eureka.client.enabled=false",
        "eureka.client.service-url.defaultZone=http://localhost:8761/eureka/",
        "spring.jpa.open-in-view=false"
})
@ActiveProfiles("dev")
@Testcontainers
class LeaveTypePostgresIntegrationTest {

    private static final String URL = "/api/v1/leaves/admin/types";
    private static final String VALID_REQUEST = """
            {
              "name": "병가",
              "isPaid": false,
              "balanceMode": "NONE",
              "requestUnit": "DAY",
              "maxMinutes": 480
            }
            """;

    @Container
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

    @DynamicPropertySource
    static void configureDatabase(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private WebApplicationContext applicationContext;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private EmployeeAccess employeeAccess;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(applicationContext).build();
        jdbcTemplate.update("delete from leave_type");
        jdbcTemplate.update("delete from leave_balance_type");
        when(employeeAccess.currentEmployeeId()).thenReturn(42L);
        when(employeeAccess.isAdmin(42L)).thenReturn(true);
    }

    @Test
    void createsTypeAndReturnsPersistedDataFromGet() throws Exception {
        MvcResult createResult = mvc.perform(post(URL)
                        .contentType(APPLICATION_JSON)
                        .content(VALID_REQUEST))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.error").value(nullValue()))
                .andExpect(jsonPath("$.data.name").value("병가"))
                .andExpect(jsonPath("$.data.paid").value(false))
                .andExpect(jsonPath("$.data.balanceMode").value("NONE"))
                .andExpect(jsonPath("$.data.requestUnit").value("DAY"))
                .andExpect(jsonPath("$.data.maxMinutes").value(480))
                .andExpect(jsonPath("$.data.balanceType").value(nullValue()))
                .andExpect(jsonPath("$.data.isActive").value(true))
                .andReturn();

        JsonNode createdType = objectMapper.readTree(createResult.getResponse().getContentAsString()).get("data");
        long leaveTypeId = createdType.get("leaveTypeId").asLong();
        assertThat(leaveTypeId).isPositive();
        assertThat(jdbcTemplate.queryForObject(
                "select is_active from leave_type where leave_type_id = ?", Boolean.class, leaveTypeId
        )).isTrue();

        MvcResult getResult = mvc.perform(get(URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andReturn();
        JsonNode returnedType = objectMapper.readTree(getResult.getResponse().getContentAsString()).get("data").get(0);
        assertThat(returnedType).isEqualTo(createdType);
    }

    @Test
    void createsAccountTypeWithBalanceAndExplicitInactiveState() throws Exception {
        Long balanceTypeId = jdbcTemplate.queryForObject(
                "insert into leave_balance_type (name, is_active) values ('연차', false) returning balance_type_id",
                Long.class
        );
        ObjectNode request = (ObjectNode) objectMapper.readTree(VALID_REQUEST);
        request.put("balanceTypeId", balanceTypeId);
        request.put("balanceMode", "ACCOUNT");
        request.put("isPaid", true);
        request.put("isActive", false);
        request.putNull("maxMinutes");

        mvc.perform(post(URL).contentType(APPLICATION_JSON).content(request.toString()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.balanceType.balanceTypeId").value(balanceTypeId))
                .andExpect(jsonPath("$.data.balanceType.active").value(false))
                .andExpect(jsonPath("$.data.paid").value(true))
                .andExpect(jsonPath("$.data.maxMinutes").value(nullValue()))
                .andExpect(jsonPath("$.data.isActive").value(false));

        mvc.perform(get(URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].isActive").value(false));
    }

    @ParameterizedTest
    @ValueSource(strings = {"name", "isPaid", "balanceMode", "requestUnit"})
    void rejectsMissingRequiredField(String fieldName) throws Exception {
        ObjectNode request = (ObjectNode) objectMapper.readTree(VALID_REQUEST);
        request.remove(fieldName);

        mvc.perform(post(URL).contentType(APPLICATION_JSON).content(request.toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("COMMON_001"));
        assertThat(jdbcTemplate.queryForObject("select count(*) from leave_type", Long.class)).isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = {"balanceMode", "requestUnit"})
    void rejectsInvalidEnum(String fieldName) throws Exception {
        ObjectNode request = (ObjectNode) objectMapper.readTree(VALID_REQUEST);
        request.put(fieldName, "INVALID");

        mvc.perform(post(URL).contentType(APPLICATION_JSON).content(request.toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("COMMON_001"));
    }

    @Test
    void rejectsUnknownBalanceType() throws Exception {
        ObjectNode request = (ObjectNode) objectMapper.readTree(VALID_REQUEST);
        request.put("balanceTypeId", 999999L);

        mvc.perform(post(URL).contentType(APPLICATION_JSON).content(request.toString()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("LEAVE_001"));
        assertThat(jdbcTemplate.queryForObject("select count(*) from leave_type", Long.class)).isZero();
    }

    @Test
    void rejectsAccountWithoutBalanceType() throws Exception {
        ObjectNode request = (ObjectNode) objectMapper.readTree(VALID_REQUEST);
        request.put("balanceMode", "ACCOUNT");

        mvc.perform(post(URL).contentType(APPLICATION_JSON).content(request.toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("COMMON_001"))
                .andExpect(jsonPath("$.error.message").value("ACCOUNT 유형은 잔액 유형 ID가 필수입니다."));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1})
    void rejectsNonPositiveMaxMinutes(int maxMinutes) throws Exception {
        ObjectNode request = (ObjectNode) objectMapper.readTree(VALID_REQUEST);
        request.put("maxMinutes", maxMinutes);

        mvc.perform(post(URL).contentType(APPLICATION_JSON).content(request.toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("COMMON_001"));
    }

    @Test
    void rejectsNonAdminWithoutSaving() throws Exception {
        when(employeeAccess.isAdmin(42L)).thenReturn(false);

        mvc.perform(post(URL).contentType(APPLICATION_JSON).content(VALID_REQUEST))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("AUTH_009"));
        assertThat(jdbcTemplate.queryForObject("select count(*) from leave_type", Long.class)).isZero();
    }

    @Test
    void returnsEmptyGetResponse() throws Exception {
        mvc.perform(get(URL))
                .andExpect(status().isOk())
                .andExpect(content().json("{\"success\":true,\"error\":null,\"data\":[]}"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    void rejectsBlankName(String name) throws Exception {
        ObjectNode request = (ObjectNode) objectMapper.readTree(VALID_REQUEST);
        request.put("name", name);

        mvc.perform(post(URL).contentType(APPLICATION_JSON).content(request.toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("COMMON_001"));
    }

    @Test
    void rejectsNameLongerThanDatabaseColumn() throws Exception {
        ObjectNode request = (ObjectNode) objectMapper.readTree(VALID_REQUEST);
        request.put("name", "가".repeat(101));

        mvc.perform(post(URL).contentType(APPLICATION_JSON).content(request.toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("COMMON_001"));
    }

    @Test
    void v2PreservesExistingRowsAndAddsDefaultAndNotNullConstraint() {
        String migrationSchema = "migration_upgrade";
        Flyway versionOne = Flyway.configure()
                .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .schemas(migrationSchema)
                .defaultSchema(migrationSchema)
                .target("1")
                .load();
        versionOne.migrate();

        String jdbcUrl = postgres.getJdbcUrl();
        String schemaJdbcUrl = jdbcUrl + (jdbcUrl.contains("?") ? "&" : "?") + "currentSchema=" + migrationSchema;
        DriverManagerDataSource migrationDataSource = new DriverManagerDataSource(
                schemaJdbcUrl,
                postgres.getUsername(),
                postgres.getPassword()
        );
        JdbcTemplate migrationJdbcTemplate = new JdbcTemplate(migrationDataSource);
        migrationJdbcTemplate.update("insert into leave_type (name, balance_mode) values ('기존 휴가', 'NONE')");

        Flyway versionTwo = Flyway.configure()
                .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .schemas(migrationSchema)
                .defaultSchema(migrationSchema)
                .load();
        versionTwo.migrate();

        assertThat(migrationJdbcTemplate.queryForObject(
                "select is_active from leave_type where name = '기존 휴가'", Boolean.class
        )).isTrue();
        migrationJdbcTemplate.update("insert into leave_type (name, balance_mode) values ('기본값 확인', 'NONE')");
        assertThat(migrationJdbcTemplate.queryForObject(
                "select is_active from leave_type where name = '기본값 확인'", Boolean.class
        )).isTrue();
        assertThatThrownBy(() -> migrationJdbcTemplate.update(
                "insert into leave_type (name, balance_mode, is_active) values ('NULL 불가', 'NONE', null)"
        )).isInstanceOf(DataIntegrityViolationException.class);
    }
}
