import { Link } from 'react-router-dom';
import type { AnomalyType, WorkTime } from '@/api/attendance';
import { StatusBadge } from '@/ui/StatusBadge';
import { formatDateShort, formatDurationShort } from '@/format';
import { anomalyHref, overtimeHref } from './requests/links';
import s from './attendance.module.css';

interface Day { workDate: string; clockOut: string | null; workTime: WorkTime; anomalies: AnomalyType[] }

const CTA: Partial<Record<AnomalyType, string>> = {
  LATE: '수정 신청', EARLY_LEAVE: '수정 신청', MISSING_CLOCK: '수정 신청', UNAPPROVED_OT: '초과근무 신청',
};

/**
 * 근태이상 칩(설계 6.4). 신청으로 해결할 수 있는 이상은 바로 신청 화면으로 간다.
 * 소정근로 미달 등은 배지만 보여 준다(행 클릭이 일간 상세를 연다).
 */
export function AnomalyChips({ day }: { day: Day }) {
  return (
    <>
      {day.anomalies.map((a) => {
        const href = anomalyHref(a, day);
        if (!href) return <StatusBadge key={a} domain="AnomalyType" value={a} />;
        return (
          <Link key={a} to={href} className={s.anomalyLink} onClick={(e) => e.stopPropagation()}
                aria-label={`${formatDateShort(day.workDate)} ${CTA[a]}`}>
            <StatusBadge domain="AnomalyType" value={a} />
            <span>→ {CTA[a]}</span>
          </Link>
        );
      })}
    </>
  );
}

/** 미승인 초과근로가 있는 날마다 초과근무 신청 바로가기(주간 요약·홈) */
export function UnapprovedOtCta({ days }: { days: Omit<Day, 'anomalies'>[] }) {
  const list = days.filter((d) => d.workTime.unapprovedOtMin > 0);
  if (list.length === 0) return null;
  return (
    <div className={s.otCta}>
      <span className={s.danger}>미승인 초과근로</span>
      {list.map((d) => (
        <Link key={d.workDate} to={overtimeHref(d)}>
          <span className="nx-num">{formatDateShort(d.workDate)} {formatDurationShort(d.workTime.unapprovedOtMin)}</span> → 초과근무 신청
        </Link>
      ))}
    </div>
  );
}
