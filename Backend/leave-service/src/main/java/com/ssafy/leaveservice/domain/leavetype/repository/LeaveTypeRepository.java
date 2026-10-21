package com.ssafy.leaveservice.domain.leavetype.repository;

import com.ssafy.leaveservice.domain.leavetype.entity.LeaveType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface LeaveTypeRepository extends JpaRepository<LeaveType, Long> {
    @Query("select t from LeaveType t left join fetch t.balanceType order by t.leaveTypeId asc")
    List<LeaveType> findAllWithBalanceType();
}
