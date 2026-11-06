import { ClockCircleOutlined, UserOutlined } from '@ant-design/icons';
import type { ColumnsType } from 'antd/es/table';

/** 招聘面板、岗位看板候选人抽屉共用的一行。两处列表必须用同一套列，避免入口越改越不一样。 */
export type HrCandidateListRow = {
  applicationId: number;
  displayName?: string;
  pending?: boolean;
  basicInfo?: string;
  candidateNo?: string;
  currentTitle?: string;
  currentCompany?: string;
  recommendedAt?: string;
  remainingHours?: number | null;
  referrerName?: string;
  resumeName?: string;
  aiScore?: number | null;
  aiCount?: number | null;
};

export const CANDIDATE_LIST_DRAWER_WIDTH = 1200;

function remainingLabel(hours?: number | null) {
  if (hours == null || Number.isNaN(hours)) return '—';
  if (hours < 0) return <span className='text-red-500'>已超时</span>;
  return (
    <span className='inline-flex items-center gap-1 text-orange-500'>
      <ClockCircleOutlined />
      剩余{hours.toFixed(2)}小时
    </span>
  );
}

export function buildCandidateListColumns<T extends HrCandidateListRow>(onAi: (row: T) => void): ColumnsType<T> {
  return [
    {
      title: '候选人',
      dataIndex: 'displayName',
      width: 120,
      render: (_, record) => (
        <span className='inline-flex items-center gap-1.5'>
          <span className={`inline-block h-1.5 w-1.5 rounded-full ${record.pending ? 'bg-red-500' : 'bg-blue-500'}`} />
          <span className='text-blue-600'>{record.displayName || '—'}</span>
        </span>
      ),
    },
    {
      title: '基本信息',
      dataIndex: 'basicInfo',
      width: 160,
      ellipsis: true,
      render: (v) => v || '—',
    },
    {
      title: '人选编号',
      dataIndex: 'candidateNo',
      width: 110,
      render: (v) => v || '—',
    },
    {
      title: '目前职位',
      dataIndex: 'currentTitle',
      width: 160,
      ellipsis: true,
      render: (v) => v || '—',
    },
    {
      title: '目前公司',
      dataIndex: 'currentCompany',
      width: 200,
      ellipsis: true,
      render: (v) => v || '—',
    },
    {
      title: '提交时间',
      dataIndex: 'recommendedAt',
      width: 120,
      render: (v) => (v ? String(v).slice(0, 10) : '—'),
    },
    {
      title: '剩余处理时间',
      dataIndex: 'remainingHours',
      width: 150,
      render: (v) => remainingLabel(typeof v === 'number' ? v : null),
    },
    {
      title: '提交人',
      dataIndex: 'referrerName',
      width: 120,
      ellipsis: true,
      render: (v) =>
        v ? (
          <span className='inline-flex items-center gap-1'>
            <UserOutlined />
            {v}
          </span>
        ) : (
          '—'
        ),
    },
    {
      title: 'AI分析',
      dataIndex: 'aiScore',
      width: 120,
      render: (_, record) => (
        <button
          type='button'
          className='cursor-pointer border-0 bg-transparent p-0 text-left text-[#1677ff]'
          onClick={(event) => {
            event.stopPropagation();
            onAi(record);
          }}
        >
          {record.aiScore == null
            ? '未分析'
            : `${record.aiScore}分${record.aiCount && record.aiCount > 1 ? ` · ${record.aiCount}份` : ''}`}
        </button>
      ),
    },
  ];
}
