import { useEffect, useState } from 'react';
import { Drawer, Input, Table } from 'antd';
import { listHrStageAdjustLogsApi, type HrStageAdjustLog } from '@/api/hr';

type Props = {
  open: boolean;
  onClose: () => void;
  applicationId?: number;
  title?: string;
};

export default function StageAdjustLogDrawer({ open, onClose, applicationId, title }: Props) {
  const [keyword, setKeyword] = useState('');
  const [loading, setLoading] = useState(false);
  const [page, setPage] = useState(1);
  const [total, setTotal] = useState(0);
  const [rows, setRows] = useState<HrStageAdjustLog[]>([]);

  useEffect(() => {
    if (!open) return;
    setKeyword('');
    setPage(1);
  }, [open, applicationId]);

  useEffect(() => {
    if (!open) return;
    let cancelled = false;
    setLoading(true);
    void listHrStageAdjustLogsApi({
      pageNum: page,
      pageSize: 10,
      applicationId,
      keyword: keyword.trim() || undefined,
    })
      .then((res) => {
        if (cancelled) return;
        setRows(res?.rows ?? []);
        setTotal(res?.total ?? 0);
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, [applicationId, keyword, open, page]);

  return (
    <Drawer
      title={title || '阶段调整记录'}
      width={960}
      open={open}
      destroyOnHidden
      onClose={onClose}
    >
      <Input.Search
        allowClear
        className='mb-3 max-w-sm'
        placeholder='候选人、岗位、操作人或原因'
        onSearch={(value) => {
          setPage(1);
          setKeyword(value);
        }}
      />
      <Table<HrStageAdjustLog>
        rowKey='id'
        size='small'
        loading={loading}
        dataSource={rows}
        scroll={{ x: 980 }}
        pagination={{
          current: page,
          pageSize: 10,
          total,
          showSizeChanger: false,
          onChange: setPage,
        }}
        columns={[
          { title: '候选人', dataIndex: 'candidateName', width: 100 },
          { title: '岗位', dataIndex: 'jobName', width: 140, ellipsis: true },
          {
            title: '阶段',
            width: 180,
            render: (_, record) => `${record.fromStageName || '—'} → ${record.toStageName || '—'}`,
          },
          { title: '业务时间', dataIndex: 'businessAt', width: 160 },
          { title: '原因', dataIndex: 'reason', ellipsis: true },
          { title: '操作人', dataIndex: 'operatorNickname', width: 100 },
          { title: '操作时间', dataIndex: 'operatedAt', width: 160 },
        ]}
      />
    </Drawer>
  );
}
