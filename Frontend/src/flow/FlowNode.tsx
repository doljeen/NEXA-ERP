import { Handle, Position, type Node, type NodeProps } from '@xyflow/react';
import { Icon } from '@/ui/Icon';
import { Check, Clock, Sparkles } from '@/ui/icons';
import { StatusBadge } from '@/ui/StatusBadge';
import s from './flow.module.css';

/** 서버 노드 카테고리 code(FastAPI registry.py) 5종 */
export type NodeCategory = 'input' | 'ai' | 'rule' | 'human' | 'output';
export type NodeRunState = 'idle' | 'QUEUED' | 'RUNNING' | 'WAITING' | 'SUCCEEDED' | 'FAILED' | 'SKIPPED';

export type FlowNodeData = {
  title: string;        // 카탈로그 name
  subtitle?: string;
  category: NodeCategory;
  state?: NodeRunState;
  elapsed?: string;     // "2.1s"
  selected?: boolean;
};
export type FlowNodeType = Node<FlowNodeData, 'nexa'>;

/**
 * 빌더와 실행 모니터가 같이 쓰는 커스텀 노드 1종(style-guide 8.13). 1주차 시제품.
 * 상태는 테두리로만 표시한다(오버레이·스피너 금지, opacity 금지).
 */
export function FlowNode({ data }: NodeProps<FlowNodeType>) {
  const state = data.state ?? 'idle';
  const cls = [s.node, s[`cat-${data.category}`], s[`st-${state}`], state === 'RUNNING' ? 'nx-running' : ''].join(' ');
  return (
    <div className={cls}>
      <Handle type="target" position={Position.Left} className={s.handle} />
      <div className={s.title}>
        {data.category === 'ai' && <Icon as={Sparkles} size={12} className={s.aiIcon} />}
        <span>{data.title}</span>
      </div>
      <div className={s.sub}>
        {data.category === 'ai' ? `AI · ${data.subtitle ?? ''}` : data.subtitle}
        {state === 'SKIPPED' && <span className={s.skipped}> · 실행 안 됨</span>}
      </div>
      <div className={s.corner}>
        {state === 'QUEUED' && <Icon as={Clock} size={12} className={s.queuedIcon} />}
        {state === 'SUCCEEDED' && <Icon as={Check} size={12} className={s.okIcon} />}
        {state === 'WAITING' && <StatusBadge domain="RunStatus" value="WAITING" />}
        {data.elapsed && (state === 'RUNNING' || state === 'SUCCEEDED') && (
          <span className={s.chip}>{state === 'RUNNING' ? `경과 ${data.elapsed}` : data.elapsed}</span>
        )}
      </div>
      <Handle type="source" position={Position.Right} className={s.handle} />
    </div>
  );
}

export const nodeTypes = { nexa: FlowNode };
