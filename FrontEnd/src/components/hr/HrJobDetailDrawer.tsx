import { useEffect, useState } from 'react';
import { App, Descriptions, Drawer, Spin } from 'antd';
import { getHrRequisitionsApi } from '@/api/hr';

const REQ_STATUS: Record<string, string> = {
  OPEN: '招聘中',
  DONE: '已完成',
  STOPPED: '停止招聘',
  PAUSED: '暂缓',
  ARCHIVED: '已归档',
};

const PRIORITY: Record<number, string> = { 1: '紧急', 2: '优先', 3: '常规' };
const LEVEL: Record<number, string> = { 1: '高', 2: '中', 3: '低' };

export type HrJobDetailFallback = {
  jobName?: string;
  deptName?: string;
  cardStatusLabel?: string;
  ownerNames?: string;
  headcount?: number;
  workLocation?: string;
  salaryRange?: string;
  priority?: number;
  priorityLabel?: string;
  jobPriorityLabel?: string;
  jobPriorityCode?: string;
  targetText?: string;
  receivedDate?: string;
  onboardDate?: string;
  educationReq?: string;
  experienceReq?: string;
  skillReq?: string;
  jobSummary?: string;
  status?: string;
  statusLabel?: string;
  location?: string;
  ownerName?: string;
};

function fmtDate(value?: string) {
  if (!value) return '—';
  return String(value).slice(0, 10).replaceAll('-', '/');
}

type Props = {
  open: boolean;
  requisitionId?: number | null;
  fallback?: HrJobDetailFallback | null;
  onClose: () => void;
};

export function HrJobDetailDrawer({ open, requisitionId, fallback, onClose }: Props) {
  const { message } = App.useApp();
  const [loading, setLoading] = useState(false);
  const [raw, setRaw] = useState<Record<string, unknown> | null>(null);

  useEffect(() => {
    if (!open || requisitionId == null) {
      setRaw(null);
      return;
    }
    let cancelled = false;
    setLoading(true);
    void getHrRequisitionsApi({ requisitionId })
      .then((rows) => {
        if (!cancelled) setRaw((rows as unknown as Record<string, unknown>[])?.[0] || null);
      })
      .catch((e) => {
        if (!cancelled) message.error(e instanceof Error ? e.message : '加载岗位详情失败');
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, [message, open, requisitionId]);

  const text = (key: string) => {
    const v = raw?.[key];
    if (v == null || v === '') return undefined;
    return String(v);
  };
  const num = (key: string) => {
    const v = raw?.[key];
    if (v == null || v === '') return undefined;
    return Number(v);
  };

  const titleName = text('job_name') || fallback?.jobName || '岗位详情';
  const statusCode = text('status') || fallback?.status || '';

  return (
    <Drawer
      title={`岗位详情 · ${titleName}`}
      open={open}
      onClose={onClose}
      width={720}
      destroyOnHidden
    >
      <Spin spinning={loading}>
        <Descriptions
          bordered
          size='small'
          column={2}
          className='bg-white'
        >
          <Descriptions.Item label='岗位'>{text('job_name') || fallback?.jobName || '—'}</Descriptions.Item>
          <Descriptions.Item label='部门'>{text('dept_name') || fallback?.deptName || '—'}</Descriptions.Item>
          <Descriptions.Item label='招聘状态'>
            {fallback?.cardStatusLabel || fallback?.statusLabel || REQ_STATUS[statusCode] || statusCode || '—'}
          </Descriptions.Item>
          <Descriptions.Item label='需求状态'>{REQ_STATUS[statusCode] || statusCode || '—'}</Descriptions.Item>
          <Descriptions.Item label='负责人'>
            {text('owner_names') || fallback?.ownerNames || fallback?.ownerName || '—'}
          </Descriptions.Item>
          <Descriptions.Item label='需求人数'>{num('headcount') ?? fallback?.headcount ?? '—'}</Descriptions.Item>
          <Descriptions.Item label='工作地址'>
            {fallback?.workLocation || fallback?.location || text('location_code') || '—'}
          </Descriptions.Item>
          <Descriptions.Item label='薪资范围'>{text('salary_range') || fallback?.salaryRange || '—'}</Descriptions.Item>
          <Descriptions.Item label='紧急程度'>
            {PRIORITY[num('priority') ?? fallback?.priority ?? 0] || fallback?.priorityLabel || '—'}
          </Descriptions.Item>
          <Descriptions.Item label='岗位优先级'>
            {fallback?.jobPriorityLabel || text('job_priority_code') || fallback?.jobPriorityCode || '—'}
          </Descriptions.Item>
          <Descriptions.Item label='重要性'>{LEVEL[num('importance_level') ?? 0] || '—'}</Descriptions.Item>
          <Descriptions.Item label='难度'>{LEVEL[num('difficulty_level') ?? 0] || '—'}</Descriptions.Item>
          <Descriptions.Item label='目标到岗'>{text('target_text') || fallback?.targetText || '—'}</Descriptions.Item>
          <Descriptions.Item label='招聘起始'>
            {fmtDate(text('received_date') || fallback?.receivedDate)}
          </Descriptions.Item>
          <Descriptions.Item label='入职日'>{fmtDate(text('onboard_date') || fallback?.onboardDate)}</Descriptions.Item>
          <Descriptions.Item label='学历要求'>
            {text('education_req') || fallback?.educationReq || '—'}
          </Descriptions.Item>
          <Descriptions.Item label='经验要求'>
            {text('experience_req') || fallback?.experienceReq || '—'}
          </Descriptions.Item>
          <Descriptions.Item
            label='技能要求'
            span={2}
          >
            {text('skill_req') || fallback?.skillReq || '—'}
          </Descriptions.Item>
          <Descriptions.Item
            label='面试流程'
            span={2}
          >
            {text('interview_flow') || '未配置'}
          </Descriptions.Item>
          <Descriptions.Item
            label='岗位职责'
            span={2}
          >
            <div className='whitespace-pre-wrap'>{text('job_desc') || fallback?.jobSummary || '—'}</div>
          </Descriptions.Item>
        </Descriptions>
      </Spin>
    </Drawer>
  );
}
