import { memo, useCallback, useEffect, useMemo, useState } from 'react';
import { Button, Calendar, Drawer, Empty, Select, Space, Spin, Table, Tag, Tooltip, Typography } from 'antd';
import type { CalendarProps } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { LeftOutlined, RightOutlined, ReloadOutlined } from '@ant-design/icons';
import dayjs, { type Dayjs } from 'dayjs';
import 'dayjs/locale/zh-cn';
import { getHrUsersApi, listHrInvitesApi } from '@/api/hr';

dayjs.locale('zh-cn');

const ROUND_LABEL: Record<number, string> = {
  1: '一面',
  2: '二面',
  3: '三面',
  4: '四面',
  5: '五面',
};

const STATUS_META: Record<string, { color: string; label: string; chip: string }> = {
  SUCCESS: { color: 'blue', label: '已建日程', chip: 'bg-sky-500/90' },
  NO_CALENDAR: { color: 'orange', label: '未建日程', chip: 'bg-amber-500/90' },
  FAILED: { color: 'red', label: '日程失败', chip: 'bg-rose-500/90' },
  CANCELLED: { color: 'default', label: '已取消', chip: 'bg-neutral-400/80' },
  CANCEL_FAILED: { color: 'default', label: '取消失败', chip: 'bg-neutral-400/80' },
};

type CalendarEvent = {
  id: number;
  inviteIds: number[];
  applicationId: number;
  displayName: string;
  jobName?: string;
  roundNo: number;
  interviewAt: string;
  durationMin?: number;
  location?: string;
  interviewerNames: string[];
  ownerNames?: string;
  status: string;
  memberStatuses: string[];
};

type PersonSummaryRow = {
  key: string;
  name: string;
  sessionCount: number;
  candidateCount: number;
  pendingCalendar: number;
  failedCalendar: number;
};

type DateSummaryRow = {
  key: string;
  date: string;
  weekday: string;
  sessionCount: number;
  candidateCount: number;
  pendingCalendar: number;
};

function textOf(value: unknown) {
  if (value == null || value === '') return undefined;
  return String(value);
}

function mapRaw(raw: Record<string, unknown>): CalendarEvent {
  const status = textOf(raw.status) || 'NO_CALENDAR';
  return {
    id: Number(raw.id),
    inviteIds: [Number(raw.id)],
    applicationId: Number(raw.application_id),
    displayName: String(raw.display_name ?? ''),
    jobName: textOf(raw.job_name),
    roundNo: Number(raw.round_no) || 1,
    interviewAt: String(raw.interview_at ?? ''),
    durationMin: raw.duration_min == null ? undefined : Number(raw.duration_min),
    location: textOf(raw.location),
    interviewerNames: [String(raw.interviewer_name ?? '').trim()].filter(Boolean),
    ownerNames: textOf(raw.owner_names),
    status,
    memberStatuses: [status],
  };
}

function sessionKey(row: Pick<CalendarEvent, 'applicationId' | 'roundNo' | 'interviewAt'>) {
  return `${row.applicationId}|${row.roundNo}|${row.interviewAt}`;
}

function groupSessions(rows: CalendarEvent[]) {
  const grouped = new Map<string, CalendarEvent>();
  for (const row of rows) {
    const key = sessionKey(row);
    const existing = grouped.get(key);
    if (!existing) {
      grouped.set(key, {
        ...row,
        inviteIds: [...row.inviteIds],
        interviewerNames: [...row.interviewerNames],
        memberStatuses: [...row.memberStatuses],
      });
      continue;
    }
    existing.inviteIds.push(...row.inviteIds);
    for (const name of row.interviewerNames) {
      if (name && !existing.interviewerNames.includes(name)) existing.interviewerNames.push(name);
    }
    for (const st of row.memberStatuses) {
      if (!existing.memberStatuses.includes(st)) existing.memberStatuses.push(st);
    }
    const rank = (s: string) => (s === 'FAILED' ? 4 : s === 'NO_CALENDAR' ? 3 : s === 'SUCCESS' ? 2 : 1);
    if (rank(row.status) > rank(existing.status)) existing.status = row.status;
  }
  return [...grouped.values()].sort((a, b) => a.interviewAt.localeCompare(b.interviewAt));
}

function splitOwnerNames(raw?: string) {
  if (!raw?.trim()) return ['未指定负责人'];
  const parts = raw
    .split(/[、,，;/|]+/)
    .map((s) => s.trim())
    .filter(Boolean);
  return parts.length ? parts : ['未指定负责人'];
}

function yearOptions(center: Dayjs) {
  const y = center.year();
  return Array.from({ length: 7 }, (_, i) => {
    const year = y - 3 + i;
    return { value: year, label: `${year}年` };
  });
}

const MONTH_OPTIONS = Array.from({ length: 12 }, (_, i) => ({
  value: i,
  label: `${i + 1}月`,
}));

const HrInterviewCalendarPage = memo(function HrInterviewCalendarPage() {
  const [owners, setOwners] = useState<{ value: number; label: string }[]>([]);
  const [ownerUserIds, setOwnerUserIds] = useState<number[]>([]);
  const [panelValue, setPanelValue] = useState<Dayjs>(() => dayjs());
  const [selectedDate, setSelectedDate] = useState<Dayjs>(() => dayjs());
  const [drawerOpen, setDrawerOpen] = useState(false);
  const [loading, setLoading] = useState(false);
  const [events, setEvents] = useState<CalendarEvent[]>([]);

  useEffect(() => {
    void getHrUsersApi('owner').then((list) =>
      setOwners(
        (list ?? []).map((item) => ({
          value: Number(
            (item as { userId?: number; user_id?: number }).userId ?? (item as { user_id?: number }).user_id,
          ),
          label: String((item as { nickname?: string }).nickname || (item as { username?: string }).username || ''),
        })),
      ),
    );
  }, []);

  const loadMonth = useCallback(async (month: Dayjs, ownerIds: number[]) => {
    setLoading(true);
    try {
      const start = month.startOf('month').subtract(7, 'day');
      const end = month.endOf('month').add(7, 'day');
      const rows = await listHrInvitesApi({
        startDate: start.format('YYYY-MM-DD'),
        endDate: end.format('YYYY-MM-DD'),
        ownerUserIds: ownerIds.length ? ownerIds : undefined,
      });
      const mapped = (rows ?? [])
        .map(mapRaw)
        .filter((row) => row.interviewAt && !['CANCELLED', 'CANCEL_FAILED'].includes(row.status));
      setEvents(groupSessions(mapped));
    } catch {
      setEvents([]);
    } finally {
      setLoading(false);
    }
  }, []);

  const monthKey = panelValue.format('YYYY-MM');

  useEffect(() => {
    void loadMonth(panelValue, ownerUserIds);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [monthKey, ownerUserIds, loadMonth]);

  /** 汇总与日历色块：只统计当前面板月份内的场次 */
  const monthEvents = useMemo(() => {
    return events.filter((ev) => dayjs(ev.interviewAt).isSame(panelValue, 'month'));
  }, [events, panelValue]);

  const byDay = useMemo(() => {
    const map = new Map<string, CalendarEvent[]>();
    for (const ev of events) {
      const key = dayjs(ev.interviewAt).format('YYYY-MM-DD');
      const list = map.get(key) ?? [];
      list.push(ev);
      map.set(key, list);
    }
    return map;
  }, [events]);

  const personSummary = useMemo<PersonSummaryRow[]>(() => {
    const map = new Map<string, { sessions: Set<string>; candidates: Set<number>; pending: number; failed: number }>();
    for (const ev of monthEvents) {
      const sk = sessionKey(ev);
      for (const name of splitOwnerNames(ev.ownerNames)) {
        const row = map.get(name) ?? {
          sessions: new Set<string>(),
          candidates: new Set<number>(),
          pending: 0,
          failed: 0,
        };
        if (!row.sessions.has(sk)) {
          row.sessions.add(sk);
          if (ev.status === 'NO_CALENDAR') row.pending += 1;
          if (ev.status === 'FAILED') row.failed += 1;
        }
        row.candidates.add(ev.applicationId);
        map.set(name, row);
      }
    }
    return [...map.entries()]
      .map(([name, row]) => ({
        key: name,
        name,
        sessionCount: row.sessions.size,
        candidateCount: row.candidates.size,
        pendingCalendar: row.pending,
        failedCalendar: row.failed,
      }))
      .sort((a, b) => b.sessionCount - a.sessionCount || a.name.localeCompare(b.name, 'zh'));
  }, [monthEvents]);

  const dateSummary = useMemo<DateSummaryRow[]>(() => {
    const map = new Map<string, CalendarEvent[]>();
    for (const ev of monthEvents) {
      const key = dayjs(ev.interviewAt).format('YYYY-MM-DD');
      const list = map.get(key) ?? [];
      list.push(ev);
      map.set(key, list);
    }
    return [...map.entries()]
      .map(([date, list]) => {
        const d = dayjs(date);
        return {
          key: date,
          date,
          weekday: d.format('ddd'),
          sessionCount: list.length,
          candidateCount: new Set(list.map((e) => e.applicationId)).size,
          pendingCalendar: list.filter((e) => e.status === 'NO_CALENDAR').length,
        };
      })
      .sort((a, b) => a.date.localeCompare(b.date));
  }, [monthEvents]);

  const dayEvents = useMemo(() => {
    return byDay.get(selectedDate.format('YYYY-MM-DD')) ?? [];
  }, [byDay, selectedDate]);

  const openDay = (date: Dayjs) => {
    setSelectedDate(date);
    setDrawerOpen(true);
  };

  const shiftMonth = (delta: number) => {
    setPanelValue((prev) => prev.add(delta, 'month'));
  };

  const filterOwnerByName = (name: string) => {
    if (name === '未指定负责人') {
      setOwnerUserIds([]);
      return;
    }
    const matched = owners.filter((o) => o.label === name).map((o) => o.value);
    if (matched.length) setOwnerUserIds(matched);
  };

  const personColumns = useMemo<ColumnsType<PersonSummaryRow>>(
    () => [
      {
        title: '招聘负责人',
        dataIndex: 'name',
        ellipsis: true,
        render: (name: string) => (
          <button
            type='button'
            className='cursor-pointer border-0 bg-transparent p-0 text-sky-600 hover:underline'
            onClick={() => filterOwnerByName(name)}
          >
            {name}
          </button>
        ),
      },
      { title: '场次', dataIndex: 'sessionCount', width: 64, align: 'right' },
      { title: '候选人', dataIndex: 'candidateCount', width: 72, align: 'right' },
      {
        title: '待建日程',
        dataIndex: 'pendingCalendar',
        width: 80,
        align: 'right',
        render: (n: number) => (n > 0 ? <span className='text-amber-600'>{n}</span> : n),
      },
      {
        title: '失败',
        dataIndex: 'failedCalendar',
        width: 56,
        align: 'right',
        render: (n: number) => (n > 0 ? <span className='text-rose-600'>{n}</span> : n),
      },
    ],
    [owners],
  );

  const dateColumns = useMemo<ColumnsType<DateSummaryRow>>(
    () => [
      {
        title: '日期',
        dataIndex: 'date',
        render: (_: string, row) => (
          <button
            type='button'
            className='cursor-pointer border-0 bg-transparent p-0 text-sky-600 hover:underline'
            onClick={() => openDay(dayjs(row.date))}
          >
            {dayjs(row.date).format('M月D日')}
            <span className='ml-1 text-neutral-400'>{row.weekday}</span>
          </button>
        ),
      },
      { title: '场次', dataIndex: 'sessionCount', width: 64, align: 'right' },
      { title: '候选人', dataIndex: 'candidateCount', width: 72, align: 'right' },
      {
        title: '待建日程',
        dataIndex: 'pendingCalendar',
        width: 80,
        align: 'right',
        render: (n: number) => (n > 0 ? <span className='text-amber-600'>{n}</span> : n),
      },
    ],
    [],
  );

  const cellRender: CalendarProps<Dayjs>['cellRender'] = (current, info) => {
    if (info.type !== 'date') return info.originNode;
    const list = byDay.get(current.format('YYYY-MM-DD')) ?? [];
    const inMonth = current.month() === panelValue.month();
    const maxShow = 3;
    return (
      <div
        className={`flex h-full min-h-0 flex-col gap-0.5 overflow-hidden px-0.5 pb-0.5 ${inMonth ? '' : 'opacity-40'}`}
        onClick={(e) => {
          e.stopPropagation();
          openDay(current);
        }}
      >
        {list.slice(0, maxShow).map((ev) => {
          const meta = STATUS_META[ev.status] || STATUS_META.NO_CALENDAR;
          const time = dayjs(ev.interviewAt).format('HH:mm');
          const tip = [
            `${time} ${ev.displayName}`,
            ROUND_LABEL[ev.roundNo] || `${ev.roundNo}面`,
            ev.jobName,
            ev.interviewerNames.join('、'),
          ]
            .filter(Boolean)
            .join(' · ');
          return (
            <Tooltip
              key={ev.id}
              title={tip}
            >
              <div
                className={`truncate rounded px-1 text-[11px] leading-[18px] text-white ${meta.chip}`}
                onClick={(e) => {
                  e.stopPropagation();
                  openDay(current);
                }}
              >
                {time} {ev.displayName}
              </div>
            </Tooltip>
          );
        })}
        {list.length > maxShow ? (
          <div className='px-0.5 text-[11px] leading-4 text-neutral-500'>+{list.length - maxShow} 场</div>
        ) : null}
      </div>
    );
  };

  return (
    <div className='flex h-[calc(100vh-100px)] min-h-0 flex-col gap-2 overflow-hidden'>
      <div className='flex shrink-0 flex-wrap items-center justify-between gap-2 rounded-lg bg-white px-3 py-2 shadow-sm'>
        <Space
          wrap
          size='small'
        >
          <Typography.Text className='mr-1 font-medium text-neutral-800'>HR面试日历</Typography.Text>
          <Button
            size='small'
            onClick={() => setPanelValue(dayjs())}
          >
            今天
          </Button>
          <Space.Compact>
            <Button
              size='small'
              icon={<LeftOutlined />}
              onClick={() => shiftMonth(-1)}
            />
            <Button
              size='small'
              icon={<RightOutlined />}
              onClick={() => shiftMonth(1)}
            />
          </Space.Compact>
          <Select
            size='small'
            className='w-24'
            options={yearOptions(panelValue)}
            value={panelValue.year()}
            onChange={(year) => setPanelValue(panelValue.year(year))}
          />
          <Select
            size='small'
            className='w-20'
            options={MONTH_OPTIONS}
            value={panelValue.month()}
            onChange={(month) => setPanelValue(panelValue.month(month))}
          />
          <Typography.Text className='text-sm font-medium text-neutral-700'>
            {panelValue.format('YYYY年M月')}
          </Typography.Text>
          <Typography.Text
            type='secondary'
            className='text-xs'
          >
            本月 {monthEvents.length} 场 · {new Set(monthEvents.map((e) => e.applicationId)).size} 人
          </Typography.Text>
        </Space>
        <Space
          wrap
          size='small'
        >
          <Select
            allowClear
            showSearch
            mode='multiple'
            size='small'
            maxTagCount='responsive'
            optionFilterProp='label'
            placeholder='招聘负责人（多选）'
            className='min-w-[220px]'
            options={owners}
            value={ownerUserIds}
            onChange={(value) => setOwnerUserIds(value ?? [])}
          />
          <Button
            size='small'
            icon={<ReloadOutlined />}
            onClick={() => void loadMonth(panelValue, ownerUserIds)}
          >
            刷新
          </Button>
        </Space>
      </div>

      <div className='grid h-[30%] min-h-[200px] shrink-0 gap-2 lg:grid-cols-2'>
        <div className='flex min-h-0 flex-col overflow-hidden rounded-lg bg-white px-3 py-2 shadow-sm'>
          <div className='mb-1 flex shrink-0 items-baseline justify-between gap-2'>
            <Typography.Text className='text-sm font-medium text-neutral-800'>人维度汇总</Typography.Text>
            <Typography.Text
              type='secondary'
              className='text-[11px]'
            >
              按招聘负责人 · 本月 · 点击姓名可筛选
            </Typography.Text>
          </div>
          <div className='min-h-0 flex-1 overflow-y-auto'>
            <Table<PersonSummaryRow>
              size='small'
              rowKey='key'
              pagination={false}
              loading={loading}
              columns={personColumns}
              dataSource={personSummary}
              locale={{ emptyText: '本月暂无面试' }}
            />
          </div>
        </div>
        <div className='flex min-h-0 flex-col overflow-hidden rounded-lg bg-white px-3 py-2 shadow-sm'>
          <div className='mb-1 flex shrink-0 items-baseline justify-between gap-2'>
            <Typography.Text className='text-sm font-medium text-neutral-800'>日期维度汇总</Typography.Text>
            <Typography.Text
              type='secondary'
              className='text-[11px]'
            >
              有安排的日期 · 点击可看当天明细
            </Typography.Text>
          </div>
          <div className='min-h-0 flex-1 overflow-y-auto'>
            <Table<DateSummaryRow>
              size='small'
              rowKey='key'
              pagination={false}
              loading={loading}
              columns={dateColumns}
              dataSource={dateSummary}
              locale={{ emptyText: '本月暂无面试' }}
            />
          </div>
        </div>
      </div>

      <div className='flex min-h-0 flex-1 flex-col overflow-hidden rounded-lg bg-white px-3 py-2 shadow-sm'>
        <Spin
          spinning={loading}
          className='flex min-h-0 flex-1 flex-col [&_.ant-spin-container]:flex [&_.ant-spin-container]:min-h-0 [&_.ant-spin-container]:flex-1 [&_.ant-spin-container]:flex-col'
        >
          <Calendar
            fullscreen
            value={selectedDate}
            mode='month'
            onPanelChange={(date) => setPanelValue(date)}
            onSelect={(date, { source }) => {
              setSelectedDate(date);
              if (source === 'date') openDay(date);
            }}
            headerRender={() => null}
            cellRender={cellRender}
            className='hr-interview-calendar min-h-0 flex-1 overflow-hidden [&_.ant-picker-body]:min-h-0 [&_.ant-picker-body]:flex-1 [&_.ant-picker-calendar]:flex [&_.ant-picker-calendar]:h-full [&_.ant-picker-calendar]:min-h-0 [&_.ant-picker-calendar]:flex-col [&_.ant-picker-calendar-date]:!h-full [&_.ant-picker-calendar-date]:!p-0.5 [&_.ant-picker-calendar-date-content]:!h-full [&_.ant-picker-calendar-date-content]:!min-h-[56px] [&_.ant-picker-calendar-date-content]:!overflow-hidden [&_.ant-picker-cell]:!p-0 [&_.ant-picker-content]:h-full [&_.ant-picker-content]:table-fixed [&_.ant-picker-content_tbody_tr]:h-[16%] [&_.ant-picker-content_th]:!py-1 [&_.ant-picker-content_th]:text-xs [&_.ant-picker-panel]:flex [&_.ant-picker-panel]:min-h-0 [&_.ant-picker-panel]:flex-1 [&_.ant-picker-panel]:flex-col'
          />
        </Spin>
      </div>

      <Drawer
        title={`${selectedDate.format('YYYY年M月D日 dddd')} · 面试明细`}
        open={drawerOpen}
        onClose={() => setDrawerOpen(false)}
        size={440}
        destroyOnHidden
      >
        {dayEvents.length === 0 ? (
          <Empty
            description='当天暂无面试安排'
            image={Empty.PRESENTED_IMAGE_SIMPLE}
          />
        ) : (
          <div className='flex flex-col gap-3'>
            {dayEvents.map((ev) => {
              const meta = STATUS_META[ev.status] || STATUS_META.NO_CALENDAR;
              const start = dayjs(ev.interviewAt);
              const end =
                ev.durationMin && ev.durationMin > 0 ? start.add(ev.durationMin, 'minute') : start.add(1, 'hour');
              return (
                <div
                  key={ev.id}
                  className='rounded-lg border border-l-4 border-neutral-200 border-l-sky-500 bg-neutral-50/80 p-3'
                >
                  <div className='mb-1 flex items-center justify-between gap-2'>
                    <span className='text-sm font-semibold text-neutral-800'>
                      {start.format('HH:mm')} – {end.format('HH:mm')}
                    </span>
                    <Tag color={meta.color}>{meta.label}</Tag>
                  </div>
                  <div className='text-base font-medium text-neutral-900'>{ev.displayName}</div>
                  <div className='mt-1 space-y-0.5 text-sm text-neutral-600'>
                    <div>
                      {ROUND_LABEL[ev.roundNo] || `${ev.roundNo}面`}
                      {ev.jobName ? ` · ${ev.jobName}` : ''}
                    </div>
                    {ev.interviewerNames.length ? <div>面试官：{ev.interviewerNames.join('、')}</div> : null}
                    {ev.ownerNames ? <div>负责人：{ev.ownerNames}</div> : null}
                    {ev.location ? <div>地点：{ev.location}</div> : null}
                  </div>
                </div>
              );
            })}
          </div>
        )}
      </Drawer>
    </div>
  );
});

export default HrInterviewCalendarPage;
