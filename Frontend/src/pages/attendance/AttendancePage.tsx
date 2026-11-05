import type { ReactNode } from 'react';
import { Button, DatePicker, Drawer, Tabs } from 'antd';
import { useLocation, useNavigate, useSearchParams } from 'react-router-dom';
import dayjs from 'dayjs';
import { Card, PageHeader } from '@/ui/primitives';
import { Icon } from '@/ui/Icon';
import { ChevronLeft, ChevronRight } from '@/ui/icons';
import { ClockPanel } from '@/attendance/Clock';
import { DailyDetail } from '@/attendance/DailyDetail';
import { WeeklyView, mondayOf } from '@/attendance/Weekly';
import { RequestForm } from '@/attendance/requests/RequestForm';
import { RequestList } from '@/attendance/requests/RequestList';
import { RequestDetail } from '@/attendance/requests/RequestDetail';
import { REQUESTS_PATH } from '@/attendance/requests/links';
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

/** 근태 신청: /attendance/requests 목록(+ /:id 상세 Drawer), /attendance/requests/new 신청 */
function RequestsTab() {
  const location = useLocation();
  const navigate = useNavigate();
  const rest = location.pathname.replace(/^\/attendance\/requests\/?/, '');
  // 딥링크(근태이상 칩 등)로 다시 들어오면 입력을 새로 채운다
  if (rest === 'new') return <RequestForm key={location.search} />;
  const id = /^\d+$/.test(rest) ? Number(rest) : null;
  return (
    <>
      <Card><RequestList /></Card>
      <Drawer open={id != null} onClose={() => navigate(`${REQUESTS_PATH}${location.search}`)} width={560}
              title="근태 신청" destroyOnHidden>
        {id != null && <RequestDetail key={id} id={id} />}
      </Drawer>
    </>
  );
}

/** 근태(요구사항 3.2·3.3, 설계 6.4): 일간·주간·근태 신청 */
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
      {active === 'requests' && <RequestsTab />}
    </>
  );
}
