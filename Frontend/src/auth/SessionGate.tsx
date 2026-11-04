import { useEffect, type ReactNode } from 'react';
import { Navigate, useLocation } from 'react-router-dom';
import { useSession } from './session';
import { authApi } from './authApi';
import { FullPageNotice } from '@/ui/FullPageNotice';

/** 저장된 employeeId로 사원 정보를 불러와 세션을 복원한다. 쿠키가 무효면 client의 reissue 흐름이 처리한다. */
export function SessionGate({ children }: { children: ReactNode }) {
  const { status, employeeId, setMe, setRestoring, clear } = useSession();
  const location = useLocation();

  useEffect(() => {
    if (employeeId == null || status === 'authenticated' || status === 'anonymous') return;
    let cancelled = false;
    setRestoring();
    authApi
      .employee(employeeId)
      .then((me) => { if (!cancelled) setMe(me); })
      .catch(() => {
        // 401(재발급 실패 포함)이든 다른 오류든 신원을 확인할 수 없으므로 로그인으로 보낸다.
        if (!cancelled) clear();
      });
    return () => { cancelled = true; };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [employeeId]);

  if (status === 'anonymous') {
    const next = location.pathname + location.search;
    return <Navigate to={`/login${next && next !== '/' ? `?next=${encodeURIComponent(next)}` : ''}`} replace />;
  }
  if (status !== 'authenticated') return <FullPageNotice />;
  return <>{children}</>;
}
