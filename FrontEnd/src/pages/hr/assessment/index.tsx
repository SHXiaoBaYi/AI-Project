import { memo, useCallback, useEffect, useRef, useState } from 'react';
import {
  App,
  Button,
  Card,
  Col,
  DatePicker,
  Modal,
  Progress,
  Row,
  Select,
  Space,
  Statistic,
  Table,
  Tag,
  Typography,
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import dayjs, { type Dayjs } from 'dayjs';
import PermissionButton from '@/components/Buttons/PermissionButton';
import {
  backfillHrKpiAssessmentApi,
  getHrKpiAssessmentApi,
  getHrUsersApi,
  updateHrKpiTaskDueApi,
  type HrKpiAssessment,
} from '@/api/hr';

const STATUS_OPTIONS = [
  { label: '全部', value: '' },
  { label: '未完成', value: 'OPEN' },
  { label: '已完成', value: 'DONE' },
];

function fmtDate(value?: string) {
  if (!value) return '—';
  return String(value).replace('T', ' ').slice(0, 10);
}

function fmtScore(n?: number) {
  if (n == null || Number.isNaN(n)) return '0';
  const v = Math.round(Number(n) * 100) / 100;
  return Number.isInteger(v) ? String(v) : v.toFixed(2);
}

/** 展示为「已得分 / 满分」，得分可含小数，如 2.66 / 30 */
function scoreOverBase(score?: number, base?: number) {
  const s = score == null || Number.isNaN(score) ? 0 : Number(score);
  const b = base == null || Number.isNaN(base) || base <= 0 ? 30 : Number(base);
  const sText = Number.isInteger(s) ? String(Math.round(s)) : s.toFixed(2);
  const bText = Number.isInteger(b) ? String(Math.round(b)) : b.toFixed(2);
  return `${sText} / ${bText}`;
}

/** 相对考核满分的达成比例 0~100 */
function scoreAttainPct(score?: number, max?: number) {
  const s = score == null || Number.isNaN(score) ? 0 : Number(score);
  const m = max == null || Number.isNaN(max) || max <= 0 ? 0 : Number(max);
  if (m <= 0) return 0;
  return Math.min(100, Math.round((s / m) * 1000) / 10);
}

function doneReasonLabel(reason?: string, priority?: string) {
  if (reason === 'QUALIFIED_RESUME') return '合格简历达标';
  if (reason === 'ONBOARD') return priority === 'P1' ? '入职达标' : '入职';
  return reason || '—';
}

function doneReasonColor(reason?: string) {
  if (reason === 'QUALIFIED_RESUME') return 'purple';
  if (reason === 'ONBOARD') return 'cyan';
  return 'default';
}

/** 例：P0 入职 0/0 · P1 入职 0/3  简历合格5份(+1.66) */
function ownerScoreBreakdown(
  o: {
    p0Done?: number;
    p0Due?: number;
    p1Due?: number;
    p1DoneOnboard?: number;
    p1DoneResume?: number;
    p1ResumePartialCount?: number;
    p1ResumePartialScore?: number;
  },
  p1ResumeNeed: number,
  weightP1: number,
) {
  const p0 = `P0 入职 ${o.p0Done ?? 0}/${o.p0Due ?? 0}`;
  const p1Onboard = `P1 入职 ${o.p1DoneOnboard ?? 0}/${o.p1Due ?? 0}`;
  const need = Math.max(1, p1ResumeNeed || 3);
  const resumeCnt = (o.p1DoneResume ?? 0) * need + (o.p1ResumePartialCount ?? 0);
  const resumeScore = (o.p1DoneResume ?? 0) * Number(weightP1 || 0) + (o.p1ResumePartialScore ?? 0);
  if (resumeCnt <= 0 && resumeScore <= 0) {
    return `${p0} · ${p1Onboard}`;
  }
  return `${p0} · ${p1Onboard}  简历合格${resumeCnt}份(+${fmtScore(resumeScore)})`;
}

const AssessmentPage = memo(function AssessmentPage() {
  const { message } = App.useApp();
  const [month, setMonth] = useState<Dayjs>(dayjs().startOf('month'));
  const [owners, setOwners] = useState<number[]>([]);
  const [status, setStatus] = useState('');
  const [userOptions, setUserOptions] = useState<{ value: number; label: string }[]>([]);
  const [data, setData] = useState<HrKpiAssessment | null>(null);
  const [loading, setLoading] = useState(false);
  const [dueEdit, setDueEdit] = useState<{ id: number; dueAt?: string; jobName?: string } | null>(null);
  const [dueValue, setDueValue] = useState<Dayjs | null>(null);
  const [savingDue, setSavingDue] = useState(false);
  const [backfilling, setBackfilling] = useState(false);
  const tableHostRef = useRef<HTMLDivElement>(null);
  const [tableBodyY, setTableBodyY] = useState(320);

  useEffect(() => {
    getHrUsersApi('owner').then((list) =>
      setUserOptions(
        list.map((item) => {
          const row = item as { userId?: number; user_id?: number; nickname: string };
          return { value: Number(row.userId ?? row.user_id), label: row.nickname };
        }),
      ),
    );
  }, []);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const res = await getHrKpiAssessmentApi({
        settleMonth: month.format('YYYY-MM-01'),
        ownerUserIds: owners.length ? owners : undefined,
        status: status || undefined,
      });
      setData(res);
    } finally {
      setLoading(false);
    }
  }, [month, owners, status]);

  useEffect(() => {
    load();
  }, [load]);

  useEffect(() => {
    const host = tableHostRef.current;
    if (!host || typeof ResizeObserver === 'undefined') return;
    const measure = () => {
      const head =
        (host.querySelector('.ant-table-header') as HTMLElement | null) ||
        (host.querySelector('.ant-table-thead') as HTMLElement | null);
      const pagination = host.querySelector('.ant-table-pagination') as HTMLElement | null;
      const headH = head?.offsetHeight || 39;
      const pageH = pagination ? pagination.offsetHeight + 16 : 56;
      setTableBodyY(Math.max(120, host.clientHeight - headH - pageH));
    };
    measure();
    const ro = new ResizeObserver(measure);
    ro.observe(host);
    return () => ro.disconnect();
  }, [data?.tasks?.length, loading]);

  const baseScore = data?.baseScore ?? 30;
  const weightP0 = data?.weightP0 ?? 2;
  const weightP1 = data?.weightP1 ?? 1;
  const p0OnboardNeed = data?.p0OnboardCount ?? 1;
  const p1ResumeNeed = data?.p1QualifiedResumeCount ?? 3;
  const p1OnboardNeed = data?.p1OnboardCount ?? 1;
  const overallMax = data?.overallScoreMax ?? (data?.ownerCount ?? data?.owners?.length ?? 0) * baseScore;
  const overallAttain = scoreAttainPct(data?.overallScore, overallMax);
  const overallP0Done = (data?.owners ?? []).reduce((n, o) => n + (o.p0Done ?? 0), 0);
  const overallP1Onboard = (data?.owners ?? []).reduce((n, o) => n + (o.p1DoneOnboard ?? 0), 0);
  const overallP1ResumeDone = (data?.owners ?? []).reduce((n, o) => n + (o.p1DoneResume ?? 0), 0);
  const overallP1ResumePartial = (data?.owners ?? []).reduce((n, o) => n + (o.p1ResumePartialCount ?? 0), 0);
  const overallP1ResumePartialScore = (data?.owners ?? []).reduce((n, o) => n + (o.p1ResumePartialScore ?? 0), 0);
  const overallResumeCnt = overallP1ResumeDone * Math.max(1, p1ResumeNeed) + overallP1ResumePartial;
  const overallResumeScore = overallP1ResumeDone * Number(weightP1) + overallP1ResumePartialScore;

  const taskColumns: ColumnsType<HrKpiAssessment['tasks'][number]> = [
    {
      title: '操作',
      fixed: 'left',
      width: 72,
      render: (_, row) =>
        row.status === 'OPEN' ? (
          <a
            onClick={() => {
              setDueEdit({ id: row.id, dueAt: row.dueAt, jobName: row.jobName });
              setDueValue(row.dueAt ? dayjs(row.dueAt) : dayjs());
            }}
          >
            延期
          </a>
        ) : (
          '—'
        ),
    },
    { title: '负责人', dataIndex: 'ownerName', width: 88, ellipsis: true },
    { title: '岗位', dataIndex: 'jobName', ellipsis: true },
    {
      title: '优先级',
      dataIndex: 'priorityCode',
      width: 72,
      render: (v: string) => <Tag color={v === 'P0' ? 'red' : 'blue'}>{v || '—'}</Tag>,
    },
    { title: 'HC', dataIndex: 'slotNo', width: 56 },
    {
      title: '记分',
      dataIndex: 'weight',
      width: 64,
      render: (v, row) => {
        const w = (v as number | undefined) ?? (row.priorityCode === 'P0' ? weightP0 : weightP1);
        return fmtScore(Number(w));
      },
    },
    {
      title: '状态',
      dataIndex: 'status',
      width: 80,
      render: (v: string, row) =>
        v === 'DONE' ? (
          <Tag color='success'>已完成</Tag>
        ) : Number(row.earnedWeight ?? 0) > 0 ? (
          <Tag color='gold'>进行中</Tag>
        ) : (
          <Tag color='processing'>未完成</Tag>
        ),
    },
    {
      title: '计入得分',
      dataIndex: 'earnedWeight',
      width: 88,
      render: (v, row) => {
        if (v != null && v !== '') return fmtScore(Number(v));
        return row.status === 'DONE'
          ? fmtScore(Number(row.weight ?? (row.priorityCode === 'P0' ? weightP0 : weightP1)))
          : '0';
      },
    },
    { title: '发布', dataIndex: 'publishedAt', width: 110, render: fmtDate },
    { title: '到期', dataIndex: 'dueAt', width: 110, render: fmtDate },
    {
      title: '完成方式',
      dataIndex: 'doneReason',
      width: 120,
      render: (v: string | undefined, row) => {
        if (row.status === 'DONE' && v) {
          return <Tag color={doneReasonColor(v)}>{doneReasonLabel(v, row.priorityCode)}</Tag>;
        }
        if (row.status === 'OPEN' && Number(row.earnedWeight ?? 0) > 0 && row.priorityCode === 'P1') {
          return <Tag color='purple'>合格简历进度</Tag>;
        }
        return '—';
      },
    },
  ];

  return (
    <div className='flex h-[calc(100vh-100px)] min-h-0 flex-col gap-3 overflow-hidden p-1'>
      <Card
        size='small'
        className='shrink-0 overflow-x-hidden'
      >
        <Space wrap>
          <span className='text-sm text-neutral-500'>核算月</span>
          <DatePicker
            picker='month'
            value={month}
            allowClear={false}
            onChange={(v) => v && setMonth(v.startOf('month'))}
          />
          <span className='text-sm text-neutral-500'>负责人</span>
          <Select
            mode='multiple'
            allowClear
            placeholder='全部'
            className='max-w-[280px] min-w-[180px]'
            options={userOptions}
            value={owners}
            onChange={setOwners}
            maxTagCount='responsive'
            optionFilterProp='label'
            showSearch
          />
          <span className='text-sm text-neutral-500'>状态</span>
          <Select
            className='w-[110px]'
            options={STATUS_OPTIONS}
            value={status}
            onChange={setStatus}
          />
          <PermissionButton
            perm='hr:kpi:view'
            loading={backfilling}
            onClick={async () => {
              setBackfilling(true);
              try {
                const n = await backfillHrKpiAssessmentApi();
                message.success(`历史回填完成，处理 ${n} 个 P0/P1 岗位`);
                load();
              } finally {
                setBackfilling(false);
              }
            }}
          >
            回填历史数据
          </PermissionButton>
          <Button
            onClick={() => load()}
            loading={loading}
          >
            刷新
          </Button>
        </Space>
        <Typography.Paragraph
          type='secondary'
          className='!mt-2 !mb-0 text-xs leading-relaxed'
        >
          {data?.scoreRuleHint ||
            `基准分${baseScore}；P0每入职${p0OnboardNeed}人记${weightP0}分；P1满${p1ResumeNeed}份合格简历记${weightP1}分（未满按份折算、最后一份补齐；已入职人选简历不计），或入职${p1OnboardNeed}人记${weightP1}分`}
        </Typography.Paragraph>
      </Card>

      <div className='max-h-[38%] shrink-0 overflow-x-hidden overflow-y-auto'>
        <Row gutter={[12, 12]}>
          <Col
            xs={24}
            sm={12}
            lg={8}
            xl={6}
          >
            <Card
              size='small'
              className='overflow-hidden border-sky-200 bg-sky-50/60'
              loading={loading && !data}
            >
              <div className='mb-2 truncate font-medium text-sky-800'>整体得分</div>
              <Statistic
                title='考核得分'
                value={scoreOverBase(data?.overallScore, overallMax)}
              />
              <Progress
                percent={overallAttain}
                size='small'
                showInfo={false}
                className='!mt-2 !mb-1'
                strokeColor='#0ea5e9'
              />
              <div className='text-xs text-neutral-500'>达成 {overallAttain}%</div>
              <div className='mt-2 text-xs leading-relaxed text-neutral-500'>
                各人已得分之和 / ({data?.ownerCount ?? data?.owners?.length ?? 0}人 × 基准分{baseScore})
                <br />
                P0 入职 {overallP0Done} · P1 入职 {overallP1Onboard}
                {overallResumeCnt > 0 ? `  简历合格${overallResumeCnt}份(+${fmtScore(overallResumeScore)})` : ''}
              </div>
            </Card>
          </Col>
          {(data?.owners ?? []).map((o) => {
            const attain = scoreAttainPct(o.score, baseScore);
            return (
              <Col
                key={o.ownerUserId}
                xs={24}
                sm={12}
                lg={8}
                xl={6}
              >
                <Card
                  size='small'
                  className='overflow-hidden'
                >
                  <div className='mb-2 truncate font-medium'>{o.ownerName || o.ownerUserId}</div>
                  <Statistic
                    title='考核得分'
                    value={scoreOverBase(o.score, baseScore)}
                  />
                  <Progress
                    percent={attain}
                    size='small'
                    showInfo={false}
                    className='!mt-2 !mb-1'
                  />
                  <div className='text-xs text-neutral-500'>达成 {attain}%</div>
                  <div className='mt-2 text-xs leading-relaxed text-neutral-500'>
                    HC {o.doneCount}/{o.dueCount} · 未完成 {o.openCount}
                    <br />
                    {ownerScoreBreakdown(o, p1ResumeNeed, weightP1)}
                  </div>
                </Card>
              </Col>
            );
          })}
          {!loading && !(data?.owners ?? []).length ? (
            <Col span={24}>
              <Card size='small'>
                <div className='py-6 text-center text-neutral-400'>本月暂无到期考核任务</div>
              </Card>
            </Col>
          ) : null}
        </Row>
      </div>

      <Card
        size='small'
        title='任务明细'
        className='flex min-h-0 flex-1 flex-col overflow-hidden [&_.ant-card-body]:flex [&_.ant-card-body]:min-h-0 [&_.ant-card-body]:flex-1 [&_.ant-card-body]:flex-col [&_.ant-card-body]:overflow-hidden [&_.ant-card-body]:!pt-3'
      >
        <div
          ref={tableHostRef}
          className='min-h-0 flex-1 overflow-hidden overflow-x-hidden'
        >
          <Table
            rowKey='id'
            size='small'
            loading={loading}
            dataSource={data?.tasks ?? []}
            columns={taskColumns}
            scroll={{ y: tableBodyY }}
            tableLayout='fixed'
            pagination={{ pageSize: 20, showSizeChanger: true, size: 'small' }}
          />
        </div>
      </Card>

      <Modal
        title={dueEdit ? `延期：${dueEdit.jobName || ''}` : '延期'}
        open={!!dueEdit}
        confirmLoading={savingDue}
        onCancel={() => setDueEdit(null)}
        onOk={async () => {
          if (!dueEdit || !dueValue) {
            message.warning('请选择新的到期时间');
            return;
          }
          setSavingDue(true);
          try {
            await updateHrKpiTaskDueApi(dueEdit.id, dueValue.format('YYYY-MM-DD HH:mm:ss'));
            message.success('已延期');
            setDueEdit(null);
            load();
          } finally {
            setSavingDue(false);
          }
        }}
      >
        <div className='mb-2 text-sm text-neutral-500'>任何人可将未完成任务的到期日改到其他月份</div>
        <DatePicker
          showTime
          className='w-full'
          value={dueValue}
          onChange={setDueValue}
        />
      </Modal>
    </div>
  );
});

export default AssessmentPage;
