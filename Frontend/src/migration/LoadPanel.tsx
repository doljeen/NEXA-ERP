import { useState } from 'react';
import { Alert, Button, Input, Modal, Tooltip } from 'antd';
import {
  useLoad, useMigrationFile, usePreviewLoad,
  type BatchView, type LoadPreview, type MigrationDetail, type MigrationEntity, type MigrationFileSummary,
} from '@/api/migration';
import { StatusBadge } from '@/ui/StatusBadge';
import { useNotify } from '@/ui/useNotify';
import { formatDateTime, formatNumber, serverUtc } from '@/format';
import { ENTITY_LABEL, ENTITY_ORDER, ENTITY_SCHEMAS } from './schemas';
import s from './migration.module.css';

/** 미리보기 예시 표: 대상 필드 라벨로 열을 만든다(필드 순서는 EntitySchemas 순서) */
function SampleTable({ entity, sample }: { entity: MigrationEntity; sample: Record<string, unknown>[] }) {
  if (sample.length === 0) return <span className={s.muted}>예시 행이 없습니다.</span>;
  const keys = new Set(sample.flatMap((r) => Object.keys(r)));
  const cols = ENTITY_SCHEMAS[entity].filter((f) => keys.has(f.field));
  const extra = [...keys].filter((k) => !cols.some((c) => c.field === k));
  return (
    <div style={{ overflowX: 'auto' }}>
      <table className={s.table}>
        <thead>
          <tr>{cols.map((c) => <th key={c.field}>{c.label}</th>)}{extra.map((k) => <th key={k} className="nx-mono">{k}</th>)}</tr>
        </thead>
        <tbody>
          {sample.map((r, i) => (
            <tr key={i}>
              {[...cols.map((c) => c.field), ...extra].map((k) => (
                <td key={k} className={s.value}>{r[k] == null || r[k] === '' ? '—' : String(r[k])}</td>
              ))}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

function BatchRow({ b }: { b: BatchView }) {
  return (
    <tr>
      <td><StatusBadge domain="BatchStatus" value={b.status} /></td>
      <td className="nx-num">{formatNumber(b.sourceRows)}행</td>
      <td className="nx-num">생성 {formatNumber(b.created)} · 수정 {formatNumber(b.updated)}</td>
      <td className="nx-num">{formatDateTime(serverUtc(b.finishedAt ?? b.startedAt), true)}</td>
      <td className={s.value}>{b.error ?? ''}</td>
    </tr>
  );
}

/**
 * ④ 미리보기 · ⑤ 적재(설계 6.7, 요구사항 4.5). entity 탭의 최신 파일 하나만 보낸다.
 * - 미리보기는 적재 불가·자동 정리가 0건일 때만 연다(비활성 사유 툴팁). 서버 loadable이 최종 판단이다.
 * - 적재 모달은 건수를 적고, 작업명을 그대로 입력해야 [적재]가 켜진다.
 * - 사원은 조직이 먼저 적재돼 있어야 부서를 찾는다 → 조직 적재 전이면 막는다.
 */
export function LoadPanel({ d, entity, file }: { d: MigrationDetail; entity: MigrationEntity; file: MigrationFileSummary }) {
  const notify = useNotify();
  const detail = useMigrationFile(d.migrationId, file.fileId);
  const preview = usePreviewLoad(d.migrationId);
  const load = useLoad(d.migrationId);
  const [result, setResult] = useState<LoadPreview | null>(null);
  const [confirmOpen, setConfirmOpen] = useState(false);
  const [typed, setTyped] = useState('');

  const totals = detail.data?.issueTotals;
  const mappedOk = !!detail.data && detail.data.missingRequired.length === 0 && detail.data.columnMappings.every((m) => m.confirmed);
  const batches = d.batches.filter((b) => b.fileId === file.fileId).sort((a, b) => b.startedAt.localeCompare(a.startedAt));
  const loaded = batches.some((b) => b.status === 'LOADED');
  // 조직 → 사원 순서: 앞 entity가 하나라도 적재돼 있어야 한다
  const prev = ENTITY_ORDER.slice(0, ENTITY_ORDER.indexOf(entity));
  const prevMissing = prev.filter((e) => !d.batches.some((b) => b.entity === e && b.status === 'LOADED'));

  const blockedReason = !detail.data ? '파일 정보를 불러오는 중입니다'
    : !mappedOk ? '매핑을 먼저 확정해 주세요'
    : totals && totals.BLOCK > 0 ? `적재 불가 ${totals.BLOCK}건을 먼저 해결해 주세요`
    : totals && totals.FIX > 0 ? `자동 정리 ${totals.FIX}건을 먼저 적용해 주세요`
    : null;
  const loadReason = prevMissing.length ? `${prevMissing.map((e) => ENTITY_LABEL[e]).join('·')}을(를) 먼저 적재해 주세요`
    : !result ? '미리보기를 먼저 확인해 주세요'
    : !result.loadable ? '미리보기에서 적재할 수 없다고 나왔습니다'
    : null;

  const runPreview = () => preview.mutate([file.fileId], {
    onSuccess: setResult,
    onError: (e) => notify.error(e, '미리보기를 만들지 못했습니다'),
  });
  const runLoad = () => load.mutate([file.fileId], {
    onSuccess: (r) => {
      setConfirmOpen(false);
      setTyped('');
      setResult(null);
      const b = r.batches.find((x) => x.fileId === file.fileId) ?? r.batches[0];
      // 대상 서비스 실패도 200으로 온다 → batch 상태로 판단한다
      if (b?.status === 'FAILED') notify.error(new Error(b.error ?? ''), `${ENTITY_LABEL[entity]} 적재가 실패했습니다`);
      else notify.success(`${ENTITY_LABEL[entity]} ${formatNumber(b?.sourceRows ?? 0)}행을 적재했습니다`,
        b ? `생성 ${formatNumber(b.created)} · 수정 ${formatNumber(b.updated)}` : undefined);
    },
    onError: (e) => notify.error(e, '적재하지 못했습니다'),
  });

  const item = result?.files.find((f) => f.fileId === file.fileId);
  return (
    <div className={s.section}>
      <div className={s.foot}>
        <Tooltip title={blockedReason}>
          <Button onClick={runPreview} loading={preview.isPending} disabled={!!blockedReason}>④ 미리보기(dry run)</Button>
        </Tooltip>
        <Tooltip title={loadReason}>
          <Button type="primary" onClick={() => setConfirmOpen(true)} disabled={!!loadReason}>⑤ 적재</Button>
        </Tooltip>
        {loaded && <span className={s.muted}>이 파일은 적재됐습니다. 다시 적재하면 기준 키(부서코드·사번)로 덮어씁니다.</span>}
      </div>

      {prevMissing.length > 0 && (
        <Alert type="info" showIcon message={`${prevMissing.map((e) => ENTITY_LABEL[e]).join('·')}을(를) 먼저 적재합니다`}
               description="사원의 부서는 조직 파일의 부서코드·부서명으로 찾습니다." />
      )}

      {result && (
        <div className={s.section}>
          {result.loadable
            ? <Alert type="success" showIcon message={`${ENTITY_LABEL[entity]} ${formatNumber(item?.rows ?? file.rows)}행을 적재할 수 있습니다`}
                     description="아래는 자동 정리가 반영된 값 예시입니다. 실제로는 아직 보내지 않았습니다." />
            : <Alert type="warning" showIcon message="적재할 수 없습니다"
                     description={`적재 불가 ${formatNumber(result.blockIssues)}건 · 자동 정리 ${formatNumber(result.fixIssues)}건이 남아 있습니다.`} />}
          {item && <SampleTable entity={entity} sample={item.sample} />}
        </div>
      )}

      {batches.length > 0 && (
        <div className={s.section}>
          <h4 className={s.h4}>적재 기록</h4>
          <table className={s.table}>
            <thead><tr><th>상태</th><th>원본</th><th>결과</th><th>시각</th><th>오류</th></tr></thead>
            <tbody>{batches.map((b) => <BatchRow key={b.batchId} b={b} />)}</tbody>
          </table>
        </div>
      )}

      <Modal open={confirmOpen} title={`${ENTITY_LABEL[entity]} 적재`} onCancel={() => { setConfirmOpen(false); setTyped(''); }}
             okText="적재" okButtonProps={{ disabled: typed !== d.name, loading: load.isPending }} onOk={runLoad} destroyOnHidden>
        <p><b>{entity} {formatNumber(item?.rows ?? file.rows)}건</b>이 실제 DB에 적재됩니다.</p>
        <p className={s.muted}>확인을 위해 작업명 <b>{d.name}</b>을(를) 그대로 입력해 주세요.</p>
        <Input value={typed} onChange={(e) => setTyped(e.target.value)} placeholder={d.name} aria-label="작업명 확인"
               onPressEnter={() => typed === d.name && runLoad()} />
      </Modal>
    </div>
  );
}
