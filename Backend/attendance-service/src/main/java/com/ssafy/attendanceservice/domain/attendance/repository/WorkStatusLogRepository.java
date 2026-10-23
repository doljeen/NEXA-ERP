package com.ssafy.attendanceservice.domain.attendance.repository;

import com.ssafy.attendanceservice.domain.attendance.entity.WorkStatusLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface WorkStatusLogRepository extends JpaRepository<WorkStatusLog, Long> {

    // 해당 근무일의 마지막 상태 로그 (= 현재 상태)
    Optional<WorkStatusLog> findTopByEmpIdAndWorkDateAndIsDeletedFalseOrderByStartAtDescLogIdDesc(
            Long empId, LocalDate workDate);

    // 해당 근무일의 상태 로그 전체 (시간순, 근로시간 계산용)
    List<WorkStatusLog> findByEmpIdAndWorkDateAndIsDeletedFalseOrderByStartAtAscLogIdAsc(
            Long empId, LocalDate workDate);
}