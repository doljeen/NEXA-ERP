package com.ssafy.attendanceservice.domain.attendance.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDate;
import java.time.OffsetDateTime;

@Entity
@Table(name = "attendance_daily")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AttendanceDaily {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "att_id")
    private Long attId;                     // 집계 ID

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "work_group_id", nullable = false)
    private WorkGroup workGroup;            // 근무그룹

    @Column(name = "emp_id", nullable = false)
    private Long empId;                     // 사원 ID

    @Column(name = "work_date", nullable = false)
    private LocalDate workDate;             // 근무일 (출근일 기준)

    @Column(name = "clock_in")
    private OffsetDateTime clockIn;         // 출근 시각

    @Column(name = "clock_out")
    private OffsetDateTime clockOut;        // 퇴근 시각

    @Column(name = "total_min", nullable = false)
    private Integer totalMin;               // 총 근로시간(분)

    @Column(name = "regular_min", nullable = false)
    private Integer regularMin;             // 소정 근로시간(분)

    @Column(name = "break_min", nullable = false)
    private Integer breakMin;               // 휴게시간(분)

    @Column(name = "excluded_min", nullable = false)
    private Integer excludedMin;            // 근로 미포함 시간(분)

    @Column(name = "night_min", nullable = false)
    private Integer nightMin;               // 야간 근로(분)

    @Column(name = "ot_extended_min", nullable = false)
    private Integer otExtendedMin;          // 초과 - 연장(분)

    @Column(name = "ot_night_min", nullable = false)
    private Integer otNightMin;             // 초과 - 야간(분)

    @Column(name = "ot_holiday_min", nullable = false)
    private Integer otHolidayMin;           // 초과 - 휴일(분)

    @Column(name = "approved_ot_min", nullable = false)
    private Integer approvedOtMin;          // 결재 승인된 초과근로(분)

    @Column(name = "unapproved_ot_min", nullable = false)
    private Integer unapprovedOtMin;        // 미승인 초과근로(분)

    @Column(name = "is_confirmed", nullable = false)
    private Boolean isConfirmed;            // 퇴근 체크 확정 여부

    @Column(name = "is_holiday", nullable = false)
    private Boolean isHoliday;              // 휴일 여부

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;       // 마지막 재계산 시각

    @Builder
    private AttendanceDaily(WorkGroup workGroup, Long empId, LocalDate workDate,
                            OffsetDateTime clockIn, Boolean isHoliday) {
        this.workGroup = workGroup;
        this.empId = empId;
        this.workDate = workDate;
        this.clockIn = clockIn;
        this.isHoliday = isHoliday;
        this.totalMin = 0;
        this.regularMin = 0;
        this.breakMin = 0;
        this.excludedMin = 0;
        this.nightMin = 0;
        this.otExtendedMin = 0;
        this.otNightMin = 0;
        this.otHolidayMin = 0;
        this.approvedOtMin = 0;
        this.unapprovedOtMin = 0;
        this.isConfirmed = false;
    }
}