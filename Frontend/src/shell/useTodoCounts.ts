import { useQuery } from '@tanstack/react-query';
import { api } from '@/api/client';

interface RunSummary { id: number; status: string }

/**
 * 탑바·사이드바 카운터(설계 7.3 전역 카운트): 60초 + 포커스 재조회, 숨겨진 탭과 관리 모드에서는 중단.
 * approval(내 차례)은 3주차 useApprovalBox가 생기면 연결한다. 그 전에는 undefined(카운터 숨김).
 */
export function useTodoCounts(adminMode: boolean) {
  const waiting = useQuery({
    queryKey: ['runs', 'me', 'pending'],
    queryFn: () => api.get<RunSummary[]>('/api/v1/ai/runs/me/pending'),
    enabled: !adminMode,
    refetchInterval: () => (document.hidden ? false : 60_000),
    refetchIntervalInBackground: false,
    retry: false,
  });
  return {
    approval: undefined as number | undefined,
    waiting: waiting.isSuccess ? waiting.data.length : undefined,
  };
}
