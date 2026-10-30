package com.ssafy.leaveservice.domain.request.repository;

import com.ssafy.leaveservice.domain.request.entity.LeaveRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

public interface LeaveRequestRepository extends JpaRepository<LeaveRequest, Long> {

    @Query("""
            select distinct requestDate.leaveDate from LeaveRequestDate requestDate
            join requestDate.leaveRequest request
            where request.employeeId = :employeeId
                and request.status in (com.ssafy.leaveservice.domain.request.entity.LeaveRequestStatus.SUBMITTING,
                                       com.ssafy.leaveservice.domain.request.entity.LeaveRequestStatus.SUBMITTED,
                                       com.ssafy.leaveservice.domain.request.entity.LeaveRequestStatus.APPROVED)
                and requestDate.leaveDate >= :start and requestDate.leaveDate < :end
            """)
    List<OffsetDateTime> findBlockingDates(
            @Param("employeeId") Long employeeId,
            @Param("start") OffsetDateTime start,
            @Param("end") OffsetDateTime end
    );

    @Query("""
            select leaveRequest from LeaveRequest leaveRequest
            join fetch leaveRequest.leaveType
            order by leaveRequest.createdAt desc, leaveRequest.requestId desc
            """)
    List<LeaveRequest> findAllWithLeaveType();

    @Query("""
            select distinct leaveRequest from LeaveRequest leaveRequest
            join fetch leaveRequest.leaveType
            left join fetch leaveRequest.dates
            where leaveRequest.requestId = :requestId
            """)
    Optional<LeaveRequest> findDetailByRequestId(@Param("requestId") Long requestId);

    @Query("""
            select leaveRequest from LeaveRequest leaveRequest
            join fetch leaveRequest.leaveType
            where leaveRequest.employeeId = :employeeId
            order by leaveRequest.createdAt desc, leaveRequest.requestId desc
            """)
    List<LeaveRequest> findAllWithLeaveTypeByEmployeeId(@Param("employeeId") Long employeeId);

    @Query("""
            select distinct leaveRequest from LeaveRequest leaveRequest
            join fetch leaveRequest.leaveType
            left join fetch leaveRequest.dates
            where leaveRequest.requestId = :requestId and leaveRequest.employeeId = :employeeId
            """)
    Optional<LeaveRequest> findDetailByRequestIdAndEmployeeId(
            @Param("requestId") Long requestId,
            @Param("employeeId") Long employeeId
    );

    Optional<LeaveRequest> findByEmployeeIdAndSubmissionKey(Long employeeId, String submissionKey);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select request from LeaveRequest request where request.requestId=:requestId and request.employeeId=:employeeId")
    Optional<LeaveRequest> findOwnedForUpdate(@Param("requestId") Long requestId, @Param("employeeId") Long employeeId);

    boolean existsByApprovalDocIdAndRequestIdNot(Long documentId, Long requestId);
}
