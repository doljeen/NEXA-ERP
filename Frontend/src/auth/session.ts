import { create } from 'zustand';
import type { Employee } from '@/api/types';

const STORAGE_KEY = 'nx.employeeId';

function readStoredId(): number | null {
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    const id = raw ? Number(raw) : NaN;
    return Number.isInteger(id) && id > 0 ? id : null;
  } catch {
    return null;
  }
}

export type SessionStatus = 'unknown' | 'restoring' | 'authenticated' | 'anonymous';

interface SessionState {
  status: SessionStatus;
  employeeId: number | null;
  me: Employee | null;
  deptName: string | null;
  gradeName: string | null;
  setLoggedIn: (employeeId: number, deptName?: string, gradeName?: string) => void;
  setMe: (me: Employee) => void;
  setRestoring: () => void;
  clear: () => void;
}

// /me API가 없어 employeeId를 localStorage에 둔다(요구사항 1.1, 설계 9장 #6)
export const useSession = create<SessionState>((set) => ({
  status: readStoredId() ? 'unknown' : 'anonymous',
  employeeId: readStoredId(),
  me: null,
  deptName: null,
  gradeName: null,
  setLoggedIn: (employeeId, deptName, gradeName) => {
    try { localStorage.setItem(STORAGE_KEY, String(employeeId)); } catch { /* 저장 불가 환경 */ }
    set({ employeeId, deptName: deptName ?? null, gradeName: gradeName ?? null, status: 'restoring' });
  },
  setMe: (me) => set({ me, status: 'authenticated' }),
  setRestoring: () => set({ status: 'restoring' }),
  clear: () => {
    try { localStorage.removeItem(STORAGE_KEY); } catch { /* noop */ }
    set({ status: 'anonymous', employeeId: null, me: null, deptName: null, gradeName: null });
  },
}));

export const isAdmin = (me: Employee | null) => me?.role === 'ADMIN';
