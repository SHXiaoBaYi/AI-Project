import { useEffect, useState } from 'react';
import { Drawer, Table } from 'antd';
import { listHrInterviewReviewsApi } from '@/api/hr';

const CONCLUSION_LABEL: Record<string, string> = {
  PASS: '通过',
  FAIL: '未通过',
  PENDING: '待定',
};

export default function InterviewReviewDrawer({
  applicationId,
  candidateName,
  open,
  onClose,
}: {
  applicationId?: number;
  candidateName?: string;
  open: boolean;
  onClose: () => void;
}) {
  const [rows, setRows] = useState<Record<string, unknown>[]>([]);
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    if (!open || !applicationId) return;
    setLoading(true);
    listHrInterviewReviewsApi(applicationId)
      .then((list) => setRows(list as unknown as Record<string, unknown>[]))
      .finally(() => setLoading(false));
  }, [open, applicationId]);

  return (
    <Drawer
      title={candidateName ? `${candidateName}的面试评价` : '面试评价'}
      open={open}
      size='large'
      onClose={onClose}
    >
      <Table
        rowKey={(row) => `${row.kind}-${row.roundNo}-${row.interviewerName}-${row.interviewedAt}`}
        loading={loading}
        dataSource={rows}
        pagination={false}
        locale={{ emptyText: '还没有面试评价' }}
        columns={[
          { title: '轮次', dataIndex: 'roundName', width: 80 },
          {
            title: '类型',
            dataIndex: 'kind',
            width: 110,
            render: (value: string) => (value === 'JOINT' ? '联合评价' : '面试官评价'),
          },
          { title: '评价人', dataIndex: 'interviewerName', width: 120, render: (value: string) => value || '—' },
          {
            title: '结论',
            dataIndex: 'conclusion',
            width: 90,
            render: (value: string) => CONCLUSION_LABEL[value] || value || '—',
          },
          { title: '评语', dataIndex: 'comment', render: (value: string) => value || '—' },
          {
            title: '时间',
            dataIndex: 'interviewedAt',
            width: 170,
            render: (value: string) => (value ? String(value).replace('T', ' ').slice(0, 19) : '—'),
          },
        ]}
      />
    </Drawer>
  );
}
