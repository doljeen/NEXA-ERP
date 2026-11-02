package com.ssafy.leaveservice.client.hr;

import java.util.List;

/** 내부 조회 계약. URI·외부 DTO·Bearer 전달은 향후 구현체에서 매핑한다. */
public interface HrClient {
    boolean isAvailable();

    EmployeeInfo findEmployee(Long employeeId);

    MemberBatch findDepartmentMembers(Long employeeId);

    DutyInfo findDuty(Long dutyId);

    record EmployeeInfo(Long employeeId, Long departmentId, Long dutyId, Long positionId) {}
    record Member(Long employeeId, Long departmentId) {}
    record MemberBatch(List<Member> members, boolean complete) {}
    record DutyInfo(Long dutyId, String name) {}
}
