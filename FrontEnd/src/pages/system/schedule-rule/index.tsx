import { memo, useCallback, useEffect, useMemo, useRef, useState, type ReactNode } from 'react';
import { App, Card, Form, Input, InputNumber, Switch, Typography } from 'antd';
import dayjs, { type Dayjs } from 'dayjs';
import { usePermission } from '@/hooks/usePermission';
import {
  getMyScheduleRuleApi,
  saveMyScheduleRuleApi,
  type ScheduleActionPref,
  type ScheduleRuleDTO,
  type ScheduleRuleWindow,
} from '@/api/scheduleRule';

const WEEKDAYS = [
  { label: '一', value: 1 },
  { label: '二', value: 2 },
  { label: '三', value: 3 },
  { label: '四', value: 4 },
  { label: '五', value: 5 },
  { label: '六', value: 6 },
  { label: '日', value: 7 },
] as const;

const ACTION_META: { action: string; label: string }[] = [
  { action: 'interview', label: '面试邀约' },
  { action: 'meeting', label: '会议' },
  { action: 'report', label: '工作汇报' },
];

/** 网格：30 分钟一格，08:00～21:00 */
const SLOT_MINUTES = 30;
const GRID_START_MIN = 8 * 60;
const GRID_END_MIN = 21 * 60;

type WindowForm = {
  weekdays: number[];
  startTime: Dayjs | null;
  endTime: Dayjs | null;
};

type ActionPrefForm = {
  action: string;
  enabled: boolean;
  preferDurationMin: number;
  maxDurationMin?: number | null;
  windows: WindowForm[];
};

type SaveStatus = 'idle' | 'saving' | 'saved' | 'error';

const ACTION_SLOT_START = '09:30';

function minsToDayjs(mins: number): Dayjs {
  const h = Math.floor(mins / 60);
  const m = mins % 60;
  return dayjs(`${String(h).padStart(2, '0')}:${String(m).padStart(2, '0')}`, 'HH:mm');
}

function dayjsToMins(v: Dayjs | null | undefined): number | null {
  if (!v) return null;
  return v.hour() * 60 + v.minute();
}

function formatHm(mins: number): string {
  const h = Math.floor(mins / 60);
  const m = mins % 60;
  return `${String(h).padStart(2, '0')}:${String(m).padStart(2, '0')}`;
}

function cellKey(weekday: number, startMin: number): string {
  return `${weekday}:${startMin}`;
}

function parseCellKey(key: string): { weekday: number; startMin: number } {
  const [d, t] = key.split(':');
  return { weekday: Number(d), startMin: Number(t) };
}

function buildSlotStarts(): number[] {
  const list: number[] = [];
  for (let t = GRID_START_MIN; t < GRID_END_MIN; t += SLOT_MINUTES) list.push(t);
  return list;
}

const SLOT_STARTS = buildSlotStarts();

function makeDefaultWindow(durationMin = SLOT_MINUTES): WindowForm {
  const startTime = dayjs(ACTION_SLOT_START, 'HH:mm');
  return {
    weekdays: [1, 2, 3, 4, 5],
    startTime,
    endTime: startTime.add(durationMin, 'minute'),
  };
}

function toWindowForms(windows?: ScheduleRuleWindow[]): WindowForm[] {
  if (!windows?.length) return [];
  return windows.map((w) => ({
    weekdays: w.weekdays?.length ? w.weekdays : [1, 2, 3, 4, 5],
    startTime: w.startTime ? dayjs(w.startTime, 'HH:mm') : null,
    endTime: w.endTime ? dayjs(w.endTime, 'HH:mm') : null,
  }));
}

function fromWindowForms(windows: WindowForm[] | undefined): ScheduleRuleWindow[] {
  return (windows || [])
    .filter((w) => w?.weekdays?.length && w.startTime && w.endTime)
    .map((w) => ({
      weekdays: w.weekdays,
      startTime: w.startTime!.format('HH:mm'),
      endTime: w.endTime!.format('HH:mm'),
    }));
}

/** 窗口列表 → 格子集合 */
function windowsToCells(windows: WindowForm[] | undefined): Set<string> {
  const set = new Set<string>();
  for (const w of windows || []) {
    const start = dayjsToMins(w.startTime);
    const end = dayjsToMins(w.endTime);
    if (start == null || end == null || end <= start || !w.weekdays?.length) continue;
    for (const d of w.weekdays) {
      for (let t = start; t < end; t += SLOT_MINUTES) {
        if (t < GRID_START_MIN || t >= GRID_END_MIN) continue;
        set.add(cellKey(d, t));
      }
    }
  }
  return set;
}

/** 格子集合 → 合并后的窗口（同起止的星期合并） */
function cellsToWindows(cells: Set<string>): WindowForm[] {
  if (cells.size === 0) return [];
  // weekday -> sorted start mins
  const byDay = new Map<number, number[]>();
  for (const key of cells) {
    const { weekday, startMin } = parseCellKey(key);
    if (!byDay.has(weekday)) byDay.set(weekday, []);
    byDay.get(weekday)!.push(startMin);
  }
  // weekday -> list of [start, end)
  const rangesByDay = new Map<number, { start: number; end: number }[]>();
  for (const [day, mins] of byDay) {
    const sorted = [...new Set(mins)].sort((a, b) => a - b);
    const ranges: { start: number; end: number }[] = [];
    let i = 0;
    while (i < sorted.length) {
      const start = sorted[i];
      let end = start + SLOT_MINUTES;
      i += 1;
      while (i < sorted.length && sorted[i] === end) {
        end += SLOT_MINUTES;
        i += 1;
      }
      ranges.push({ start, end });
    }
    rangesByDay.set(day, ranges);
  }
  // merge identical ranges across days
  const bucket = new Map<string, number[]>();
  for (const [day, ranges] of rangesByDay) {
    for (const r of ranges) {
      const k = `${r.start}-${r.end}`;
      if (!bucket.has(k)) bucket.set(k, []);
      bucket.get(k)!.push(day);
    }
  }
  return [...bucket.entries()]
    .map(([k, days]) => {
      const [startStr, endStr] = k.split('-');
      const start = Number(startStr);
      const end = Number(endStr);
      return {
        weekdays: days.sort((a, b) => a - b),
        startTime: minsToDayjs(start),
        endTime: minsToDayjs(end),
      };
    })
    .sort((a, b) => {
      const as = dayjsToMins(a.startTime) ?? 0;
      const bs = dayjsToMins(b.startTime) ?? 0;
      if (as !== bs) return as - bs;
      return (a.weekdays[0] ?? 0) - (b.weekdays[0] ?? 0);
    });
}

function defaultActionPrefs(prefs?: ScheduleActionPref[]): ActionPrefForm[] {
  return ACTION_META.map((meta) => {
    const found = prefs?.find((p) => p.action === meta.action);
    const windows = found?.windows?.length ? toWindowForms(found.windows) : [makeDefaultWindow()];
    return {
      action: meta.action,
      enabled: found ? !!found.enabled : true,
      preferDurationMin: found?.preferDurationMin ?? (meta.action === 'interview' ? 60 : 30),
      maxDurationMin: found?.maxDurationMin ?? undefined,
      windows,
    };
  });
}

function buildPayload(values: Record<string, unknown>): ScheduleRuleDTO {
  const actionPrefs = ((values.actionPrefs || []) as ActionPrefForm[]).map((p) => ({
    action: p.action,
    enabled: !!p.enabled,
    preferDurationMin: p.preferDurationMin,
    maxDurationMin: p.maxDurationMin ?? null,
    windows: fromWindowForms(p.windows),
  }));
  return {
    enabled: !!values.enabled,
    secretaryEnabled: values.secretaryEnabled !== false,
    denyHolidays: values.denyHolidays !== false,
    bufferMin: (values.bufferMin as number) ?? 0,
    lookAheadDays: (values.lookAheadDays as number) ?? 14,
    recommendLimit: (values.recommendLimit as number) ?? 8,
    robotHint: (values.robotHint as string) || '',
    blockedWindows: fromWindowForms(values.blockedWindows as WindowForm[]),
    actionPrefs,
  };
}

const Tofu = memo(function Tofu({
  title,
  extra,
  children,
  className = '',
}: {
  title: ReactNode;
  extra?: ReactNode;
  children: ReactNode;
  className?: string;
}) {
  return (
    <Card
      size='small'
      title={<span className='text-sm font-medium'>{title}</span>}
      extra={extra}
      className={`h-full shadow-sm ${className}`}
      styles={{ body: { padding: '10px 12px' }, header: { minHeight: 40, padding: '0 12px' } }}
    >
      {children}
    </Card>
  );
});

type GridTone = 'prefer' | 'block';

/**
 * 周一～周日平铺 × 半小时格子；按下拖拽框选，松手写入。
 * 起点已选 → 本次为擦除；起点未选 → 本次为涂选。
 */
const WeekTimeGrid = memo(function WeekTimeGrid({
  value,
  onChange,
  disabled,
  tone = 'prefer',
}: {
  value?: WindowForm[];
  onChange?: (next: WindowForm[]) => void;
  disabled?: boolean;
  tone?: GridTone;
}) {
  const committed = useMemo(() => windowsToCells(value), [value]);
  const [draft, setDraft] = useState<Set<string> | null>(null);
  const draftRef = useRef<Set<string> | null>(null);
  const dragRef = useRef<{
    day0: number;
    slot0: number;
    paintOn: boolean;
    base: Set<string>;
  } | null>(null);

  const display = draft ?? committed;

  const applyRect = useCallback((day1: number, slot1: number) => {
    const drag = dragRef.current;
    if (!drag) return;
    const dayMin = Math.min(drag.day0, day1);
    const dayMax = Math.max(drag.day0, day1);
    const slotMin = Math.min(drag.slot0, slot1);
    const slotMax = Math.max(drag.slot0, slot1);
    const next = new Set(drag.base);
    for (let di = dayMin; di <= dayMax; di += 1) {
      const weekday = WEEKDAYS[di].value;
      for (let si = slotMin; si <= slotMax; si += 1) {
        const key = cellKey(weekday, SLOT_STARTS[si]);
        if (drag.paintOn) next.add(key);
        else next.delete(key);
      }
    }
    draftRef.current = next;
    setDraft(next);
  }, []);

  const endDrag = useCallback(() => {
    if (!dragRef.current) return;
    dragRef.current = null;
    const cur = draftRef.current;
    draftRef.current = null;
    setDraft(null);
    if (cur) onChange?.(cellsToWindows(cur));
  }, [onChange]);

  useEffect(() => {
    const up = () => endDrag();
    window.addEventListener('mouseup', up);
    window.addEventListener('blur', up);
    return () => {
      window.removeEventListener('mouseup', up);
      window.removeEventListener('blur', up);
    };
  }, [endDrag]);

  const selectedCls = tone === 'block' ? 'bg-rose-500/85 hover:bg-rose-500' : 'bg-sky-500/85 hover:bg-sky-500';
  const previewCls = tone === 'block' ? 'bg-rose-400/50 ring-1 ring-rose-300' : 'bg-sky-400/50 ring-1 ring-sky-300';

  return (
    <div className='select-none'>
      <div className='mb-1 flex items-center justify-between gap-2 text-[11px] text-neutral-400'>
        <span>{disabled ? '仅查看' : '拖拽框选格子，松手即保存；再框已选区域可取消'}</span>
        <span className='shrink-0 tabular-nums'>{display.size ? `${display.size} 格` : '未选'}</span>
      </div>
      <div
        className='overflow-auto rounded border border-neutral-200'
        style={{ maxHeight: 280 }}
      >
        <div
          className='grid min-w-[260px]'
          style={{ gridTemplateColumns: `36px repeat(7, minmax(0, 1fr))` }}
          onMouseLeave={() => {
            /* 保持拖拽，由 window mouseup 结束 */
          }}
        >
          <div className='sticky top-0 z-[1] bg-neutral-50' />
          {WEEKDAYS.map((d) => (
            <div
              key={d.value}
              className='sticky top-0 z-[1] border-b border-l border-neutral-100 bg-neutral-50 py-1 text-center text-[11px] font-medium text-neutral-600'
            >
              {d.label}
            </div>
          ))}
          {SLOT_STARTS.map((startMin, slotIdx) => (
            <div
              key={startMin}
              className='contents'
            >
              <div
                className={`border-t border-neutral-100 pr-1 text-right text-[10px] leading-4 text-neutral-400 tabular-nums ${
                  startMin % 60 === 0 ? 'font-medium text-neutral-500' : ''
                }`}
              >
                {startMin % 60 === 0 ? formatHm(startMin) : ''}
              </div>
              {WEEKDAYS.map((d, dayIdx) => {
                const key = cellKey(d.value, startMin);
                const on = display.has(key);
                const inDraft = draft?.has(key);
                const committedOn = committed.has(key);
                const isPreview = draft != null && inDraft !== committedOn;
                return (
                  <button
                    key={key}
                    type='button'
                    disabled={disabled}
                    title={`${WEEKDAYS[dayIdx].label} ${formatHm(startMin)}–${formatHm(startMin + SLOT_MINUTES)}`}
                    className={`h-4 border-t border-l border-neutral-100 transition-colors disabled:cursor-not-allowed ${
                      on ? (isPreview ? previewCls : selectedCls) : 'bg-white hover:bg-neutral-100'
                    }`}
                    onMouseDown={(e) => {
                      if (disabled || e.button !== 0) return;
                      e.preventDefault();
                      const paintOn = !committed.has(key);
                      dragRef.current = {
                        day0: dayIdx,
                        slot0: slotIdx,
                        paintOn,
                        base: new Set(committed),
                      };
                      applyRect(dayIdx, slotIdx);
                    }}
                    onMouseEnter={() => {
                      if (!dragRef.current || disabled) return;
                      applyRect(dayIdx, slotIdx);
                    }}
                  />
                );
              })}
            </div>
          ))}
        </div>
      </div>
    </div>
  );
});

const ScheduleRulePage = memo(function ScheduleRulePage() {
  const { message } = App.useApp();
  const { has } = usePermission();
  const canEdit = has('system:schedule-rule:edit');
  const [form] = Form.useForm();
  const [loading, setLoading] = useState(false);
  const [saveStatus, setSaveStatus] = useState<SaveStatus>('idle');
  const readyRef = useRef(false);
  const timerRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const seqRef = useRef(0);

  const load = useCallback(async () => {
    readyRef.current = false;
    setLoading(true);
    try {
      const data = await getMyScheduleRuleApi();
      form.setFieldsValue({
        enabled: !!data.enabled,
        secretaryEnabled: data.secretaryEnabled !== false,
        denyHolidays: data.denyHolidays !== false,
        bufferMin: data.bufferMin ?? 0,
        lookAheadDays: data.lookAheadDays ?? 14,
        recommendLimit: data.recommendLimit ?? 8,
        robotHint: data.robotHint || '',
        blockedWindows: toWindowForms(data.blockedWindows),
        actionPrefs: defaultActionPrefs(data.actionPrefs),
      });
    } finally {
      setLoading(false);
      requestAnimationFrame(() => {
        readyRef.current = true;
      });
    }
  }, [form]);

  useEffect(() => {
    void load();
    return () => {
      if (timerRef.current) clearTimeout(timerRef.current);
    };
  }, [load]);

  const persist = useCallback(async () => {
    if (!canEdit || !readyRef.current) return;
    let values: Record<string, unknown>;
    try {
      values = await form.validateFields();
    } catch {
      setSaveStatus('idle');
      return;
    }
    const payload = buildPayload(values);
    if (payload.enabled) {
      const bad = payload.actionPrefs.find((p) => p.enabled && (!p.windows || p.windows.length === 0));
      if (bad) {
        setSaveStatus('idle');
        return;
      }
    }
    const seq = ++seqRef.current;
    setSaveStatus('saving');
    try {
      await saveMyScheduleRuleApi(payload);
      if (seq === seqRef.current) setSaveStatus('saved');
    } catch {
      if (seq === seqRef.current) {
        setSaveStatus('error');
        message.error('自动保存失败');
      }
    }
  }, [canEdit, form, message]);

  const scheduleSave = useCallback(() => {
    if (!canEdit || !readyRef.current) return;
    setSaveStatus('saving');
    if (timerRef.current) clearTimeout(timerRef.current);
    timerRef.current = setTimeout(() => {
      void persist();
    }, 450);
  }, [canEdit, persist]);

  const statusText =
    saveStatus === 'saving'
      ? '保存中…'
      : saveStatus === 'saved'
        ? '已自动保存'
        : saveStatus === 'error'
          ? '保存失败'
          : canEdit
            ? '改动后自动保存'
            : '仅查看';

  return (
    <div className='space-y-3'>
      <div className='flex flex-wrap items-end justify-between gap-2'>
        <div>
          <Typography.Title
            level={5}
            className='!mb-0'
          >
            我的日程规则
          </Typography.Title>
          <Typography.Text
            type='secondary'
            className='text-xs'
          >
            仅本人可配。拖拽框选周一～周日时段，松手后自动保存。
          </Typography.Text>
        </div>
        <Typography.Text
          type={saveStatus === 'error' ? 'danger' : 'secondary'}
          className='text-xs'
        >
          {loading ? '加载中…' : statusText}
        </Typography.Text>
      </div>

      <Form
        form={form}
        layout='horizontal'
        size='small'
        disabled={!canEdit || loading}
        labelCol={{ flex: '0 0 72px' }}
        wrapperCol={{ flex: '1 1 auto' }}
        labelAlign='left'
        colon={false}
        onValuesChange={() => scheduleSave()}
        initialValues={{
          enabled: false,
          secretaryEnabled: true,
          denyHolidays: true,
          bufferMin: 0,
          lookAheadDays: 14,
          recommendLimit: 8,
          blockedWindows: [],
          actionPrefs: defaultActionPrefs(),
        }}
      >
        <div className='grid grid-cols-1 gap-3 md:grid-cols-2 xl:grid-cols-3'>
          <Tofu title='基本配置'>
            <div className='grid grid-cols-2 gap-x-3 gap-y-1 sm:grid-cols-4'>
              <Form.Item
                name='secretaryEnabled'
                label='秘书可读'
                valuePropName='checked'
                layout='vertical'
                labelCol={{ span: 24 }}
                wrapperCol={{ span: 24 }}
                className='!mb-0'
              >
                <Switch
                  size='small'
                  checkedChildren='开'
                  unCheckedChildren='关'
                />
              </Form.Item>
              <Form.Item
                name='enabled'
                label='时段规则'
                valuePropName='checked'
                layout='vertical'
                labelCol={{ span: 24 }}
                wrapperCol={{ span: 24 }}
                className='!mb-0'
                tooltip='开启后动作须落在喜好时段，并避开不安排时段'
              >
                <Switch
                  size='small'
                  checkedChildren='开'
                  unCheckedChildren='关'
                />
              </Form.Item>
              <Form.Item
                name='denyHolidays'
                label='禁节假日'
                valuePropName='checked'
                layout='vertical'
                labelCol={{ span: 24 }}
                wrapperCol={{ span: 24 }}
                className='!mb-0'
              >
                <Switch
                  size='small'
                  checkedChildren='禁'
                  unCheckedChildren='允'
                />
              </Form.Item>
              <Form.Item
                name='bufferMin'
                label='间隔(分)'
                layout='vertical'
                labelCol={{ span: 24 }}
                wrapperCol={{ span: 24 }}
                className='!mb-0'
              >
                <InputNumber
                  min={0}
                  max={240}
                  step={5}
                  className='w-full'
                />
              </Form.Item>
              <Form.Item
                name='lookAheadDays'
                label='前瞻(天)'
                layout='vertical'
                labelCol={{ span: 24 }}
                wrapperCol={{ span: 24 }}
                className='!mb-0'
              >
                <InputNumber
                  min={1}
                  max={14}
                  className='w-full'
                />
              </Form.Item>
              <Form.Item
                name='recommendLimit'
                label='推荐条数'
                layout='vertical'
                labelCol={{ span: 24 }}
                wrapperCol={{ span: 24 }}
                className='!mb-0'
              >
                <InputNumber
                  min={1}
                  max={30}
                  className='w-full'
                />
              </Form.Item>
              <Form.Item
                name='robotHint'
                label='拒约提示'
                layout='vertical'
                labelCol={{ span: 24 }}
                wrapperCol={{ span: 24 }}
                className='col-span-2 !mb-0 sm:col-span-4'
              >
                <Input.TextArea
                  rows={2}
                  maxLength={500}
                  placeholder='无空档或拒约时话术'
                />
              </Form.Item>
            </div>
          </Tofu>

          <Tofu title='不安排时段'>
            <p className='mb-2 text-xs text-neutral-500'>框选后这些格子内任意动作都不推荐、不创建。</p>
            <Form.Item
              name='blockedWindows'
              className='!mb-0'
            >
              <WeekTimeGrid tone='block' />
            </Form.Item>
          </Tofu>

          {ACTION_META.map((meta, index) => (
            <Tofu
              key={meta.action}
              title={meta.label}
              extra={
                <Form.Item
                  name={['actionPrefs', index, 'enabled']}
                  valuePropName='checked'
                  className='!mb-0'
                >
                  <Switch
                    size='small'
                    checkedChildren='开'
                    unCheckedChildren='关'
                  />
                </Form.Item>
              }
            >
              <Form.Item
                name={['actionPrefs', index, 'action']}
                hidden
                initialValue={meta.action}
              >
                <Input />
              </Form.Item>
              <div className='mb-2 grid grid-cols-2 gap-1'>
                <Form.Item
                  name={['actionPrefs', index, 'preferDurationMin']}
                  label='默认分'
                  rules={[{ required: true, message: '必填' }]}
                  className='!mb-0'
                  labelCol={{ flex: '0 0 48px' }}
                >
                  <InputNumber
                    min={15}
                    max={480}
                    step={15}
                    className='w-full'
                  />
                </Form.Item>
                <Form.Item
                  name={['actionPrefs', index, 'maxDurationMin']}
                  label='最长'
                  className='!mb-0'
                  labelCol={{ flex: '0 0 36px' }}
                >
                  <InputNumber
                    min={15}
                    max={480}
                    step={15}
                    className='w-full'
                    placeholder='不限'
                  />
                </Form.Item>
              </div>
              <div className='mb-1 text-xs text-neutral-500'>喜好可约时段（30 分钟一格，拖拽框选）</div>
              <Form.Item
                name={['actionPrefs', index, 'windows']}
                className='!mb-0'
              >
                <WeekTimeGrid tone='prefer' />
              </Form.Item>
            </Tofu>
          ))}
        </div>
      </Form>
    </div>
  );
});

export default ScheduleRulePage;
