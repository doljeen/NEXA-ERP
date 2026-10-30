package com.ssafy.leaveservice.domain.account.repository;

public final class LeaveBalanceSql {

    private LeaveBalanceSql() {
    }

    // 현재 잔액과 Preview가 같은 원장 delta 집계를 공유한다.
    public static final String GRANT_TOTALS = """
            with calculation_time as (
                select cast(:asOf as timestamptz) as as_of
            ), owned_accounts as (
                select account_id, balance_type_id from leave_account where emp_id = :employeeId
            ), grant_totals as (
                select grant_record.account_id, grant_record.grant_id,
                       grant_record.valid_from, grant_record.expires_on,
                       coalesce(sum(cast(ledger.quantity_delta as numeric)), 0) as quantity_minutes,
                       coalesce(sum(cast(ledger.reserved_delta as numeric)), 0) as reserved_minutes
                from owned_accounts account
                join leave_grant grant_record on grant_record.account_id = account.account_id
                cross join calculation_time time
                left join leave_ledger ledger on ledger.grant_id = grant_record.grant_id
                    and ledger.account_id = grant_record.account_id and ledger.created_at <= time.as_of
                group by grant_record.account_id, grant_record.grant_id,
                         grant_record.valid_from, grant_record.expires_on
            )
            """;
}
