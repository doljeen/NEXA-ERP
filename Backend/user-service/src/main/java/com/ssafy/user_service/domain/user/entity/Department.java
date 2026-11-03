package com.ssafy.user_service.domain.user.entity;

import com.ssafy.user_service.domain.user.entity.enums.DeptType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "department")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Department {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "dept_id")
    private Long deptId;                // 부서ID

    @Column(name = "name", length = 255)
    private String name;                // 부서명

    @Column(name = "sort_order")
    private Integer sortOrder;          // 정렬번호

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;            // 회사

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_dept_id")
    private Department parent;          // 상위부서 (최상위 부서는 null)

    @Enumerated(EnumType.STRING)
    @Column(name = "dept_type", length = 20)
    private DeptType deptType;          // 부서 유형 (회사/본부/팀). 결재 전결 단계 기준

    @Column(name = "head_emp_id")
    private Long headEmpId;             // 부서장 사원ID (null 이면 공석)

    @Builder
    private Department(String name, Integer sortOrder, Company company, Department parent,
                       DeptType deptType, Long headEmpId) {
        this.name = name;
        this.sortOrder = sortOrder;
        this.company = company;
        this.parent = parent;
        this.deptType = deptType;
        this.headEmpId = headEmpId;
    }
}
