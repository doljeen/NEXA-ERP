package com.ssafy.migrationservice.domain.migration.validation.schema;

import com.ssafy.migrationservice.domain.migration.entity.enums.MigrationEntity;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.ssafy.migrationservice.domain.migration.validation.schema.FieldType.DATE;
import static com.ssafy.migrationservice.domain.migration.validation.schema.FieldType.EMAIL;
import static com.ssafy.migrationservice.domain.migration.validation.schema.FieldType.ENUM;
import static com.ssafy.migrationservice.domain.migration.validation.schema.FieldType.NUMBER;
import static com.ssafy.migrationservice.domain.migration.validation.schema.FieldType.PHONE;
import static com.ssafy.migrationservice.domain.migration.validation.schema.FieldType.STRING;

/**
 * entity별 필드 정의.
 * 지금은 user-service의 EmployeeCreateRequest·employee 테이블 NOT NULL 기준으로 여기 직접 둔다.
 * 각 서비스에 스키마 API가 생기면 그쪽에서 받아오도록 바꾼다.
 */
public final class EntitySchemas {

    private static final Map<MigrationEntity, EntitySchema> SCHEMAS = new EnumMap<>(MigrationEntity.class);

    static {
        SCHEMAS.put(MigrationEntity.HR_EMPLOYEE, new EntitySchema(MigrationEntity.HR_EMPLOYEE, List.of(
                FieldSpec.of("empNo", "사번", STRING).key().maxLength(50).synonyms("사원번호", "직원번호", "사번코드", "사원코드", "empno").build(),
                FieldSpec.of("name", "이름", STRING).required().synonyms("성명", "사원명", "직원명", "name", "empname").build(),
                FieldSpec.of("engName", "영문이름", STRING).synonyms("영문명", "영문성명", "englishname").build(),
                FieldSpec.of("email", "이메일", EMAIL).required().synonyms("메일", "이메일주소", "로그인id", "email").build(),
                FieldSpec.of("dept", "부서", STRING).required().synonyms("부서명", "소속", "소속부서", "부서코드").build(),
                FieldSpec.of("position", "직위", STRING).required().codeLike().synonyms("직위명").build(),
                FieldSpec.of("grade", "직급", STRING).required().codeLike().synonyms("직급명").build(),
                FieldSpec.of("duty", "직책", STRING).required().codeLike().synonyms("직책명").build(),
                FieldSpec.of("jobFamily", "직군", STRING).required().codeLike().build(),
                FieldSpec.of("jobKind", "직종", STRING).required().codeLike().build(),
                FieldSpec.of("jobRole", "직무", STRING).required().build(),
                FieldSpec.of("empType", "직원구분", ENUM).synonyms("고용형태", "근로형태")
                        .enumValues(Map.of("정규직", "REGULAR", "파트타임", "PARTTIME", "계약직", "CONTRACT")).build(),
                FieldSpec.of("hireType", "채용구분", ENUM).synonyms("채용유형")
                        .enumValues(Map.of("공개채용", "OPEN", "수시채용", "ROLL", "추천채용", "RECOMMENDATION")).build(),
                FieldSpec.of("hireDate", "입사일", DATE).required().synonyms("입사일자", "입사년월일", "최초입사일").build(),
                FieldSpec.of("recogHireDate", "인정입사일", DATE).synonyms("그룹입사일", "인정입사일자").build(),
                FieldSpec.of("birthDate", "생년월일", DATE).synonyms("생일").build(),
                FieldSpec.of("gender", "성별", ENUM).enumValues(Map.of("남", "MALE", "남자", "MALE", "여", "FEMALE", "여자", "FEMALE")).build(),
                FieldSpec.of("mobile", "휴대전화", PHONE).synonyms("휴대폰", "핸드폰", "연락처", "휴대폰번호").build(),
                FieldSpec.of("officePhone", "직통번호", PHONE).synonyms("사내전화", "내선번호", "회사전화").build(),
                FieldSpec.of("nationality", "국적", STRING).build(),
                FieldSpec.of("address", "주소", STRING).synonyms("거주지").build(),
                FieldSpec.of("status", "재직상태", ENUM).synonyms("재직구분", "상태")
                        .enumValues(Map.of("재직", "EMPLOYED", "휴직", "ON_LEAVE", "퇴직", "TERMINATED", "퇴사", "TERMINATED")).build(),
                FieldSpec.of("resignDate", "퇴사일", DATE).synonyms("퇴직일", "퇴사일자").build()
        )));

        SCHEMAS.put(MigrationEntity.HR_ORG, new EntitySchema(MigrationEntity.HR_ORG, List.of(
                FieldSpec.of("deptCode", "부서코드", STRING).key().maxLength(50).synonyms("조직코드", "부서id").build(),
                FieldSpec.of("name", "부서명", STRING).required().synonyms("조직명", "부서", "deptname", "orgname").build(),
                FieldSpec.of("parentDeptCode", "상위부서", STRING)
                        .synonyms("상위부서코드", "상위조직", "상위조직코드", "parentcode", "parentdept", "upperdeptcode", "parentorgcode").build(),
                FieldSpec.of("sortOrder", "정렬순서", NUMBER).synonyms("순서", "표시순서").build(),
                // user-service 부서 유형(DeptType)·부서장(head_emp_id). 결재선 자동 결정에 쓰인다
                FieldSpec.of("deptType", "부서유형", ENUM).synonyms("조직유형", "부서구분", "조직구분", "depttype", "orgtype")
                        .enumValues(Map.of("회사", "COMPANY", "본부", "DIVISION", "팀", "TEAM")).build(),
                FieldSpec.of("headEmpNo", "부서장 사번", STRING).maxLength(50)
                        .synonyms("부서장", "부서장사번", "조직장", "조직장사번", "headempno", "deptheadempno", "managerempno").build()
        )));
    }

    private EntitySchemas() {
    }

    /** 아직 정의가 없는 entity는 비어 있음 (검증 건너뜀) */
    public static Optional<EntitySchema> of(MigrationEntity entity) {
        return Optional.ofNullable(SCHEMAS.get(entity));
    }
}
