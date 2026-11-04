// 서비스 공통 봉투(요구사항 1.3)
export interface ApiEnvelope<T> {
  success: boolean;
  error: { code: string; message: string } | null;
  data: T;
}

export type Role = 'USER' | 'ADMIN';
export type EmploymentStatus = 'EMPLOYED' | 'ON_LEAVE' | 'TERMINATED';

// user-service EmployeeInfoResponse
export interface Employee {
  empId: number;
  empNo: string | null;
  name: string;
  loginId: string;
  role: Role;
  deptId: number;
  positionId: number;
  gradeId: number;
  dutyId: number;
  empType: string | null;
  hireType: string | null;
  hireDate: string | null;
  status: EmploymentStatus;
}

export interface LoginResponse {
  employeeId: number;
  deptName: string;
  gradeName: string;
}
