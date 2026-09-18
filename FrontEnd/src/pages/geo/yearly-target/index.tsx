import { memo, useEffect, useMemo, useState } from 'react';
import { ProFormDatePicker, ProFormDigit, ProFormSelect, ProFormText, QueryFilter } from '@ant-design/pro-components';
import { App, Button, Card, Col, Popconfirm, Row, Statistic, Table, Tag } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import dayjs from 'dayjs';
import BaseModalForm from '@/components/BaseModalForm';
import PermissionButton from '@/components/Buttons/PermissionButton';
import {
  deleteGeoYearTargetApi,
  getGeoPlatformsApi,
  getGeoTopicOptionsApi,
  getGeoYearlyBoardApi,
  getGeoYearTargetsApi,
  saveGeoYearTargetApi,
  seedGeoYearlySampleApi,
} from '@/api/geo';
import type { GeoTopic, GeoYearTarget, GeoYearlyBoard, GeoYearlyRow } from '@/types/geo';
import { GEO_LABEL } from '@/constants/geoLabels';
import { BUTTERFLY_SEARCH } from '@/constants/searchLayout';

const currentYear = dayjs().year();
const defaultYear = dayjs(`${currentYear}-01-01`);

type MatrixCell = { actualRate?: number; achieveRate?: number };
type MatrixRow = {
  key: string;
  periodLabel: string;
  topicName: string;
  targetRate?: number;
  byPlatform: Record<string, MatrixCell>;
};

function pct(v?: number | null) {
  if (v == null || Number.isNaN(v)) return '-';
  return `${Number(v).toFixed(2)}%`;
}

function buildMatrix(
  rows: GeoYearlyRow[],
  targets: GeoYearTarget[],
  topics: GeoTopic[],
  platforms: string[],
): MatrixRow[] {
  const topicName = (id?: number) => topics.find((t) => t.id === id)?.topicName || String(id ?? '');
  const map = new Map<string, MatrixRow>();

  const ensure = (periodLabel: string, name: string, targetRate?: number) => {
    const key = `${periodLabel}::${name}`;
    let row = map.get(key);
    if (!row) {
      row = { key, periodLabel, topicName: name, targetRate, byPlatform: {} };
      map.set(key, row);
    } else if (targetRate != null && row.targetRate == null) {
      row.targetRate = targetRate;
    }
    return row;
  };

  for (const t of targets) {
    ensure(t.periodLabel, topicName(t.topicId), Number(t.targetRate));
  }
  for (const r of rows) {
    if (!r.platform || r.platform === '-') continue;
    const row = ensure(r.periodLabel, r.topicName || '-', r.targetRate != null ? Number(r.targetRate) : undefined);
    row.byPlatform[r.platform] = {
      actualRate: r.actualRate,
      achieveRate: r.achieveRate,
    };
    if (!platforms.includes(r.platform)) {
      platforms.push(r.platform);
    }
  }

  const periodOrder = new Map<string, number>();
  targets.forEach((t, i) => {
    if (!periodOrder.has(t.periodLabel)) periodOrder.set(t.periodLabel, i);
  });

  return [...map.values()].sort((a, b) => {
    const pa = periodOrder.get(a.periodLabel) ?? 999;
    const pb = periodOrder.get(b.periodLabel) ?? 999;
    if (pa !== pb) return pa - pb;
    return a.topicName.localeCompare(b.topicName, 'zh');
  });
}

const YearlyPage = memo(function YearlyPage() {
  const { message } = App.useApp();
  const [topics, setTopics] = useState<GeoTopic[]>([]);
  const [platforms, setPlatforms] = useState<string[]>([]);
  const [board, setBoard] = useState<GeoYearlyBoard>({ actualChart: [], achieveChart: [], rows: [] });
  const [targets, setTargets] = useState<GeoYearTarget[]>([]);
  const [targetOpen, setTargetOpen] = useState(false);
  const [editing, setEditing] = useState<GeoYearTarget | null>(null);
  const [year, setYear] = useState(currentYear);
  const [seeding, setSeeding] = useState(false);

  const loadBoard = async (y = year) => {
    const data = await getGeoYearlyBoardApi({
      startDate: `${y}-01-01`,
      endDate: `${y}-12-31`,
    });
    setBoard({
      platforms: data?.platforms ?? [],
      overallAchieveRates: data?.overallAchieveRates ?? [],
      actualChart: data?.actualChart ?? [],
      achieveChart: data?.achieveChart ?? [],
      rows: data?.rows ?? [],
      persistedPeriodCount: data?.persistedPeriodCount ?? 0,
    });
  };

  const loadTargets = () => getGeoYearTargetsApi().then(setTargets);

  useEffect(() => {
    getGeoTopicOptionsApi().then(setTopics);
    getGeoPlatformsApi().then(setPlatforms);
    void loadBoard(currentYear);
    loadTargets();
  }, []);

  const columnPlatforms = useMemo(() => {
    const list = [...(board.platforms?.length ? board.platforms : platforms)];
    for (const r of board.rows ?? []) {
      if (r.platform && r.platform !== '-' && !list.includes(r.platform)) list.push(r.platform);
    }
    return list;
  }, [board.platforms, board.rows, platforms]);

  const matrixRows = useMemo(
    () => buildMatrix(board.rows ?? [], targets, topics, [...columnPlatforms]),
    [board.rows, targets, topics, columnPlatforms],
  );

  const overallCards = board.overallAchieveRates ?? [];

  const matrixColumns: ColumnsType<MatrixRow> = useMemo(() => {
    const cols: ColumnsType<MatrixRow> = [
      {
        title: '时间',
        dataIndex: 'periodLabel',
        width: 120,
        fixed: 'left',
        onCell: (row, index) => {
          const i = index ?? 0;
          if (i > 0 && matrixRows[i - 1]?.periodLabel === row.periodLabel) {
            return { rowSpan: 0 };
          }
          let span = 1;
          for (let j = i + 1; j < matrixRows.length; j++) {
            if (matrixRows[j].periodLabel !== row.periodLabel) break;
            span++;
          }
          return { rowSpan: span };
        },
      },
      {
        title: '目标场景/人群/话题',
        dataIndex: 'topicName',
        width: 220,
        fixed: 'left',
      },
      {
        title: '目标',
        dataIndex: 'targetRate',
        width: 88,
        render: (v) => pct(v),
      },
    ];

    cols.push({
      title: '实际达成',
      children: columnPlatforms.map((p) => ({
        title: p,
        key: `actual-${p}`,
        width: 96,
        align: 'right' as const,
        render: (_: unknown, row: MatrixRow) => pct(row.byPlatform[p]?.actualRate),
      })),
    });

    cols.push({
      title: '达成率',
      children: columnPlatforms.map((p) => ({
        title: p,
        key: `achieve-${p}`,
        width: 96,
        align: 'right' as const,
        render: (_: unknown, row: MatrixRow) => {
          const v = row.byPlatform[p]?.achieveRate;
          if (v == null) return '-';
          const ok = v >= 100;
          return <span className={ok ? 'text-emerald-600' : 'text-amber-700'}>{pct(v)}</span>;
        },
      })),
    });

    return cols;
  }, [columnPlatforms, matrixRows]);

  const openCreate = () => {
    setEditing(null);
    setTargetOpen(true);
  };

  const openEdit = (row: GeoYearTarget) => {
    setEditing(row);
    setTargetOpen(true);
  };

  return (
    <div className='flex flex-col gap-4'>
      <Card size='small'>
        <QueryFilter
          {...BUTTERFLY_SEARCH}
          initialValues={{ year: defaultYear }}
          onFinish={async (v) => {
            const y = v?.year ? dayjs(v.year).year() : currentYear;
            setYear(y);
            await loadBoard(y);
            return true;
          }}
          onReset={() => {
            setYear(currentYear);
            void loadBoard(currentYear);
          }}
          submitter={{
            render: (_, dom) => (
              <div className='flex flex-wrap items-center gap-2'>
                {dom}
                <PermissionButton
                  perm='geo:yearly:target'
                  loading={seeding}
                  onClick={async () => {
                    setSeeding(true);
                    try {
                      const msg = await seedGeoYearlySampleApi();
                      message.success(typeof msg === 'string' ? msg : '样例已洗入');
                      await Promise.all([loadTargets(), loadBoard(year)]);
                      const opts = await getGeoTopicOptionsApi();
                      setTopics(opts);
                    } finally {
                      setSeeding(false);
                    }
                  }}
                >
                  洗入样例数据
                </PermissionButton>
              </div>
            ),
          }}
        >
          <ProFormDatePicker
            name='year'
            label='年份'
            fieldProps={{ picker: 'year', format: 'YYYY', allowClear: false }}
          />
        </QueryFilter>
      </Card>

      <Row gutter={[16, 16]}>
        {overallCards.length === 0 ? (
          <Col span={24}>
            <Card>
              <div className='text-sm text-neutral-500'>
                暂无全年整体达成率。请点击上方「洗入样例数据」，将 Excel 中的目标与各平台实际达成写入系统。
              </div>
            </Card>
          </Col>
        ) : (
          overallCards.map((item) => (
            <Col
              key={item.platform}
              xs={24}
              sm={12}
              lg={overallCards.length <= 2 ? 12 : 8}
            >
              <Card className='h-full bg-gradient-to-br from-slate-50 to-white'>
                <Statistic
                  title={`全年目标达成率 · ${item.platform}`}
                  value={Number(item.achieveRate ?? 0)}
                  precision={2}
                  suffix='%'
                  valueStyle={{ fontSize: 36, fontWeight: 600, color: '#0f766e' }}
                />
                <div className='mt-2 text-xs text-neutral-500'>
                  口径：各话题达成率合计 / 话题行总数
                  {item.filledCount != null && item.totalCount != null
                    ? `（已填 ${item.filledCount} / 共 ${item.totalCount}）`
                    : ''}
                </div>
              </Card>
            </Col>
          ))
        )}
      </Row>

      <Card
        title={`${GEO_LABEL.yearlyTarget}达成情况`}
        extra={
          <PermissionButton
            perm='geo:yearly:target'
            type='primary'
            onClick={openCreate}
          >
            新增目标
          </PermissionButton>
        }
      >
        <div className='mb-3 text-sm text-neutral-600'>
          平台列取自系统 AI 平台主数据（不限于样例中的豆包/DS）；目标为默认可改配置，实际达成已洗入样例值。
        </div>
        <Table
          size='small'
          rowKey='key'
          dataSource={matrixRows}
          columns={matrixColumns}
          pagination={false}
          scroll={{ x: 'max-content', y: 480 }}
          bordered
        />
      </Card>

      <Card title='目标配置（可改）'>
        <Table
          rowKey='id'
          size='small'
          dataSource={targets}
          pagination={false}
          scroll={{ x: 'max-content' }}
          columns={[
            {
              title: '操作',
              width: 140,
              fixed: 'left',
              render: (_, r) => (
                <div className='flex gap-1'>
                  <PermissionButton
                    perm='geo:yearly:target'
                    type='link'
                    size='small'
                    onClick={() => openEdit(r)}
                  >
                    编辑
                  </PermissionButton>
                  <Popconfirm
                    title='确定删除该目标？'
                    onConfirm={async () => {
                      await deleteGeoYearTargetApi(r.id);
                      message.success('已删除');
                      loadTargets();
                      void loadBoard(year);
                    }}
                  >
                    <Button
                      type='link'
                      size='small'
                      danger
                    >
                      删除
                    </Button>
                  </Popconfirm>
                </div>
              ),
            },
            { title: '时间段', dataIndex: 'periodLabel' },
            {
              title: '话题',
              dataIndex: 'topicId',
              render: (id) => topics.find((t) => t.id === id)?.topicName || id,
            },
            { title: '开始', dataIndex: 'periodStart' },
            { title: '结束', dataIndex: 'periodEnd' },
            {
              title: '目标%',
              dataIndex: 'targetRate',
              render: (v) => pct(Number(v)),
            },
            {
              title: '状态',
              width: 90,
              render: () => <Tag color='processing'>默认可改</Tag>,
            },
          ]}
        />
      </Card>

      <BaseModalForm
        title={editing ? '编辑目标' : '新增目标'}
        open={targetOpen}
        onOpenChange={(open) => {
          setTargetOpen(open);
          if (!open) setEditing(null);
        }}
        initialValues={
          editing
            ? {
                ...editing,
                periodStart: editing.periodStart ? dayjs(editing.periodStart) : undefined,
                periodEnd: editing.periodEnd ? dayjs(editing.periodEnd) : undefined,
              }
            : { targetRate: 80, periodLabel: '全年' }
        }
        onFinish={async (values) => {
          await saveGeoYearTargetApi({
            id: editing?.id,
            ...values,
            periodStart: values.periodStart ? dayjs(values.periodStart).format('YYYY-MM-DD') : undefined,
            periodEnd: values.periodEnd ? dayjs(values.periodEnd).format('YYYY-MM-DD') : undefined,
          });
          message.success('已保存');
          loadTargets();
          void loadBoard(year);
          return true;
        }}
      >
        <ProFormText
          name='periodLabel'
          label='时间段名称'
          rules={[{ required: true }]}
          placeholder='如 全年 / 7-8月'
        />
        <ProFormDatePicker
          name='periodStart'
          label='区间开始'
        />
        <ProFormDatePicker
          name='periodEnd'
          label='区间结束'
        />
        <ProFormSelect
          name='topicId'
          label='话题'
          rules={[{ required: true }]}
          options={topics.map((t) => ({ label: t.topicName, value: t.id }))}
          showSearch
          optionFilterProp='label'
        />
        <ProFormDigit
          name='targetRate'
          label='目标%'
          min={0}
          max={100}
          fieldProps={{ precision: 2 }}
          rules={[{ required: true }]}
        />
      </BaseModalForm>
    </div>
  );
});

export default YearlyPage;
