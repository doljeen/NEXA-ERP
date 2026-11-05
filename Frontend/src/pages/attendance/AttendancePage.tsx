import type { ReactNode } from 'react';
import { Button, DatePicker, Tabs } from 'antd';
import { useLocation, useNavigate, useSearchParams } from 'react-router-dom';
import dayjs from 'dayjs';
import { Card, PageHeader, EmptyState } from '@/ui/primitives';
import { Icon } from '@/ui/Icon';
import { ChevronLeft, ChevronRight } from '@/ui/icons';
import { ClockPanel } from '@/attendance/Clock';
import { DailyDetail } from '@/attendance/DailyDetail';
import { WeeklyView, mondayOf } from '@/attendance/Weekly';
import { formatDate, kst, nowKst } from '@/format';
import s from '@/attendance/attendance.module.css';

const TABS = [
  { key: '', label: '일간' },
  { key: 'weekly', label: '주간' },
  { key: 'requests', label: '근태 신청' },
];

/** 날짜 이동 툴바: ◀ 라벨 ▶ [오늘/이번 주] */
function Stepper({ label, onPrev, onNext, onReset, resetLabel, nextDisabled, picker }:
  { label: string; onPrev: () => void; onNext: () => void; onReset: () => void; resetLabel: string; nextDisabled?: boolean; picker?: ReactNode }) {
  return (
    <div className={s.toolbar}>
      <Button icon={<Icon as={ChevronLeft} />} onClick={onPrev} aria-label="이전" />
      <span className={`${s.range} nx-num`}>{label}</span>
      <Button icon={<Icon as={ChevronRight} />} onClick={onNext} disabled={nextDisabled} aria-label="다음" />
      <Button onClick={onReset}>{resetLabel}</Button>
      {picker}
    </div>
  );
}

function DailyTab() {
  const [params, setParams] = useSearchParams();
  const today = nowKst().format('YYYY-MM-DD');
  const date = params.get('date') ?? today;
  const go = (d: string) => setParams(d === today ? {} : { date: d }, { replace: true });
  const isToday = date === today;
  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
      <Stepper
        label={formatDate(date)}
        onPrev={() => go(kst(date).subtract(1, 'day').format('YYYY-MM-DD'))}
        onNext={() => go(kst(date).add(1, 'day').format('YYYY-MM-DD'))}
        onReset={() => go(today)}
        resetLabel="오늘"
        picker={
          <DatePicker value={dayjs(date)} allowClear={false} aria-label="날짜 선택"
                      onChange={(v) => v && go(v.format('YYYY-MM-DD'))} />
        }
      />
      <div style={{ display: 'grid', gridTemplateColumns: isToday ? 'minmax(260px, 1fr) 3fr' : '1fr', gap: 16, alignItems: 'start' }}>
        {isToday && <Card title="오늘 근무"><ClockPanel /></Card>}
        <Card title="하루 근태"><DailyDetail date={date} /></Card>
      </div>
    </div>
  );
}

function WeeklyTab() {
  const [params, setParams] = useSearchParams();
  const thisWeek = mondayOf(nowKst());
  const monday = mondayOf(params.get('week') ?? thisWeek);
  const go = (d: string) => setParams(d === thisWeek ? {} : { week: d }, { replace: true });
  const sunday = kst(monday).add(6, 'day');
  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
      <Stepper
        label={`${kst(monday).format('YYYY.MM.DD')} ~ ${sunday.format('MM.DD')}`}
        onPrev={() => go(kst(monday).subtract(7, 'day').format('YYYY-MM-DD'))}
        onNext={() => go(kst(monday).add(7, 'day').format('YYYY-MM-DD'))}
        onReset={() => go(thisWeek)}
        resetLabel="이번 주"
      />
      <Card><WeeklyView monday={monday} /></Card>
    </div>
  );
}

/** 근태(요구사항 3.2, 설계 6.4). 2주차: 일간·주간. 근태 신청은 3주차. */
export function AttendancePage() {
  const location = useLocation();
  const navigate = useNavigate();
  const sub = location.pathname.replace(/^\/attendance\/?/, '').split('/')[0] ?? '';
  const active = TABS.some((t) => t.key === sub) ? sub : '';
  return (
    <>
      <PageHeader title="근태" />
      <Tabs
        activeKey={active}
        onChange={(k) => navigate(k ? `/attendance/${k}` : '/attendance')}
        items={TABS.map((t) => ({ key: t.key, label: t.label }))}
      />
      {active === '' && <DailyTab />}
      {active === 'weekly' && <WeeklyTab />}
      {active === 'requests' && (
        <Card><EmptyState title="화면 준비 중" description="근태 신청(근무시간 수정·초과근무·휴일대체)은 3주차에 구현합니다" /></Card>
      )}
    </>
  );
}
