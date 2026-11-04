import { api, http } from '@/api/client';
import type { Employee, LoginResponse } from '@/api/types';

export const authApi = {
  // 필드명은 password가 아니라 pass다(요구사항 1.1)
  login: (email: string, pass: string) => api.post<LoginResponse>('/api/v1/auth/login', { email, pass }),
  logout: () => http.post('/api/v1/auth/logout').catch(() => undefined),
  employee: (id: number) => api.get<Employee>(`/api/v1/employees/${id}`),
};
