import { useQuery } from '@tanstack/react-query';
import { api } from './client';

// FastAPI runs/schemas.py RunSummary (camelCase alias)
export interface RunSummary {
  id: number;
  workflowId: number;
  version: number | null;
  isTest: boolean;
  status: string;
  currentNodeId: string | null;
  requestedBy: string | null;
  createdAt: string;
  startedAt: string | null;
  finishedAt: string | null;
}

/** 내 확인 대기(WAITING) 실행. 탑바 카운터와 홈 카드가 같은 키를 쓴다(설계 7.3 전역 카운트: 60초 + 포커스). */
export function useMyPendingRuns(enabled = true) {
  return useQuery({
    queryKey: ['runs', 'me', 'pending'],
    queryFn: () => api.get<RunSummary[]>('/api/v1/ai/runs/me/pending'),
    enabled,
    refetchInterval: () => (document.hidden ? false : 60_000),
    refetchIntervalInBackground: false,
    retry: false,
  });
}
