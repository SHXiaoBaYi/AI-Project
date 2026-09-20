import { memo, useMemo, useState } from 'react';
import type { ProColumnType } from '@ant-design/pro-components';
import { Segmented } from 'antd';
import BaseProTable from '@/components/BaseProTable';
import TableModal from '@/components/TableModal';
import ActionButtons from '@/components/Buttons/ActionButtons';
import { getHrSchoolsApi } from '@/api/hr';

interface SchoolRow {
  id: number;
  name: string;
  nameEn?: string;
  code?: string;
  schoolType?: string;
  tags?: string;
  eduLevel?: string;
  region?: string;
  authority?: string;
  qsRank?: string;
  rankNo?: number;
  abbr?: string;
  score?: number;
  intro?: string;
}

const SchoolPage = memo(function SchoolPage() {
  const [kind, setKind] = useState<'DOMESTIC' | 'QS'>('DOMESTIC');
  const [open, setOpen] = useState(false);
  const [current, setCurrent] = useState<SchoolRow | null>(null);
  const domestic = kind === 'DOMESTIC';

  const columns: ProColumnType<SchoolRow>[] = useMemo(
    () => [
      {
        title: '操作',
        valueType: 'option',
        width: 80,
        render: (_, record) => (
          <ActionButtons
            items={[
              {
                key: 'view',
                label: '详情',
                onClick: () => {
                  setCurrent(record);
                  setOpen(true);
                },
              },
            ]}
          />
        ),
      },
      {
        title: '学校名称',
        dataIndex: 'name',
        ellipsis: true,
      },
      {
        title: '英文名',
        dataIndex: 'nameEn',
        search: false,
        hideInTable: domestic,
        ellipsis: true,
      },
      {
        title: '缩写',
        dataIndex: 'abbr',
        search: false,
        hideInTable: domestic,
        width: 100,
      },
      {
        title: '排名',
        dataIndex: 'rankNo',
        search: false,
        hideInTable: domestic,
        width: 80,
      },
      {
        title: '得分',
        dataIndex: 'score',
        search: false,
        hideInTable: domestic,
        width: 90,
      },
      {
        title: '标识码',
        dataIndex: 'code',
        search: false,
        hideInTable: !domestic,
        width: 120,
      },
      {
        title: '类型',
        dataIndex: 'schoolType',
        search: false,
        hideInTable: !domestic,
        width: 140,
      },
      {
        title: '标签',
        dataIndex: 'tags',
        hideInTable: !domestic,
        hideInSearch: !domestic,
        width: 120,
      },
      {
        title: '办学层次',
        dataIndex: 'eduLevel',
        search: false,
        hideInTable: !domestic,
        width: 100,
      },
      {
        title: domestic ? '所在地' : '国家地区',
        dataIndex: 'region',
        width: 120,
      },
      {
        title: '主管部门',
        dataIndex: 'authority',
        search: false,
        hideInTable: !domestic,
        ellipsis: true,
      },
      {
        title: 'QS',
        dataIndex: 'qsRank',
        search: false,
        hideInTable: !domestic,
        width: 100,
      },
      {
        title: '简介',
        dataIndex: 'intro',
        valueType: 'textarea',
        search: false,
        hideInTable: true,
      },
    ],
    [domestic],
  );

  return (
    <>
      <BaseProTable<SchoolRow>
        key={kind}
        rowKey='id'
        columns={columns}
        headerTitle='院校信息'
        params={{ kind }}
        toolBarRender={() => [
          <Segmented
            key='kind'
            value={kind}
            options={[
              { label: '国内院校', value: 'DOMESTIC' },
              { label: 'QS院校', value: 'QS' },
            ]}
            onChange={(value) => setKind(value as 'DOMESTIC' | 'QS')}
          />,
        ]}
        request={async (params) => {
          const res = await getHrSchoolsApi({
            pageNum: params.current,
            pageSize: params.pageSize,
            kind: params.kind,
            name: params.name,
            region: params.region,
            tags: params.tags,
          });
          return { data: res.rows, success: true, total: res.total };
        }}
      />
      <TableModal
        title={current?.name || '院校信息'}
        columns={columns as never}
        open={open}
        onOpenChange={setOpen}
        initialValues={current || {}}
      />
    </>
  );
});

export default SchoolPage;
