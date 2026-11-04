import { useSession } from '@/auth/session';
import { PageHeader, Card, EmptyState } from '@/ui/primitives';
import { FeatureGate } from '@/ui/FeatureGate';
import { formatDate, nowKst } from '@/format';

/** 1주차: 빈 홈(설계 9장 완료 기준 "로그인 → 빈 홈"). 위젯은 2주차부터 채운다. */
export function HomePage() {
  const { me } = useSession();
  return (
    <>
      <PageHeader title={`${me?.name ?? ''}님, 안녕하세요`} description={formatDate(nowKst())} />
      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(320px, 1fr))', gap: 16 }}>
        <Card title="처리할 일">
          <EmptyState title="처리할 일이 없어요" description="결재와 확인 대기가 생기면 여기에 표시됩니다" />
        </Card>
        <Card title="오늘 근무">
          <EmptyState title="근무 위젯 준비 중" description="출퇴근과 이번 주 근로시간은 2주차에 연결됩니다" />
        </Card>
        <Card title="휴가">
          <FeatureGate flag="leaveBalance">{null}</FeatureGate>
        </Card>
      </div>
    </>
  );
}
