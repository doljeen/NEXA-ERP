package com.ssafy.attendanceservice.domain.attendance.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "work_status_code")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WorkStatusCode {

    @Id
    @Column(name = "status_code", length = 20)
    private String statusCode;          // 상태 코드

    @Column(name = "name", length = 30, nullable = false)
    private String name;                // 화면 표시명

    @Column(name = "count_as_work", nullable = false)
    private Boolean countAsWork;        // 근로시간 포함 여부

    @Builder
    private WorkStatusCode(String statusCode, String name, Boolean countAsWork) {
        this.statusCode = statusCode;
        this.name = name;
        this.countAsWork = countAsWork;
    }
}