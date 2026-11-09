import { PageHeader } from '@/ui/primitives';
import { DepartmentAdmin } from '@/admin/DepartmentAdmin';

/** 관리자 인사(요구사항 4.1). 지금은 부서(트리·부서장·유형)만, 사원 목록·퇴직은 P1 다음 항목 */
export function HrPage() {
  return (
    <>
      <PageHeader title="인사" description="부서 트리에서 부서장과 부서 유형을 관리합니다. 결재선이 이 정보로 만들어집니다." />
      <DepartmentAdmin />
    </>
  );
}
