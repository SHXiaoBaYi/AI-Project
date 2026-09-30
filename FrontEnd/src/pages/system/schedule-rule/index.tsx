import { memo, useCallback, useEffect, useMemo, useRef, useState, type ReactNode } from 'react';
import { AutoComplete, Button, Card, Form, Input, InputNumber, Select, Space, Tag, TimePicker, Typography } from 'antd';
import { HolderOutlined, MinusCircleOutlined, PlusOutlined } from '@ant-design/icons';
import dayjs, { type Dayjs } from 'dayjs';
import { usePermission } from '@/hooks/usePermission';
import {
  getMyScheduleRuleApi,
  getScheduleRuleOptionsApi,
  saveMyScheduleRuleApi,
  type ScheduleMeetingPriority,
  type SchedulePreferRule,
  type SchedulePreferScene,
  type SchedulePriorityCode,
  type ScheduleRuleDTO,
  type ScheduleRuleOptions,
  type ScheduleRuleWindow,
} from '@/api/scheduleRule';

const DEFAULT_PRIORITY_ORDER: SchedulePriorityCode[] = ['SUPERVISOR', 'CORE_PROJECT', 'OTHER_BIZ', 'CROSS_DEPT'];

const PRIORITY_META: Record<SchedulePriorityCode, { label: string; hint: string }> = {
  SUPERVISOR: { label: '直接上级', hint: '输入联想本地用户，回车可添加新人' },
  CORE_PROJECT: { label: '核心项目部', hint: '选择核心项目部门' },
  OTHER_BIZ: { label: '其他业务部门', hint: '选择其他业务部门' },
  CROSS_DEPT: { label: '跨部门协作', hint: '跨部门协作会议按此档排序' },
};

const PREFER_SCENE_OPTIONS: { value: SchedulePreferScene; label: string }[] = [
  { value: 'EXTERNAL_REPORT', label: '外部汇报' },
  { value: 'INTERNAL_MEETING', label: '内部会议' },
  { value: 'EXTERNAL_MEETING', label: '外部会议' },
  { value: 'INTERVIEW', label: '面试' },
  { value: 'ONE_ON_ONE', label: '一对一' },
  { value: 'CUSTOM', label: '自定义' },
];

const PREFER_SLOT_OPTIONS = [
  { value: 'PERIOD:MORNING', label: '上午' },
  { value: 'PERIOD:AFTERNOON', label: '下午' },
  { value: 'RANGE', label: '自定义时段' },
] as const;

const ACTION_META: { action: string; preferDurationMin: number }[] = [
  { action: 'interview', preferDurationMin: 60 },
  { action: 'meeting', preferDurationMin: 30 },
  { action: 'report', preferDurationMin: 30 },
];

const DEFAULT_BUFFER_MIN = 15;
const DEFAULT_SLOT_MIN = 30;
const DEFAULT_WORK_END = '18:30';
const DEFAULT_MORNING_START = '09:30';
const DEFAULT_MORNING_END = '11:00';
const DEFAULT_FORENOON_START = '11:00';
const DEFAULT_FORENOON_END = '13:00';
const DEFAULT_LUNCH_START = '13:00';
const DEFAULT_LUNCH_END = '14:00';

type PreferRuleForm = {
  scene: SchedulePreferScene;
  sceneLabel?: string;
  /** PERIOD:MORNING | PERIOD:AFTERNOON | RANGE */
  slotChoice: string;
  startTime?: Dayjs | null;
  endTime?: Dayjs | null;
};

type SaveStatus = 'idle' | 'saving' | 'saved' | 'error';

function parseHm(text?: string | null, fallback = '00:00'): Dayjs {
  const raw = text && /^\d{1,2}:\d{2}$/.test(text) ? text : fallback;
  return dayjs(raw, 'HH:mm');
}

function defaultDeptMeetingWindows(start = DEFAULT_FORENOON_START, end = DEFAULT_FORENOON_END): ScheduleRuleWindow[] {
  return [{ weekdays: [2, 3, 4], startTime: start, endTime: end }];
}

function defaultPreferRules(): PreferRuleForm[] {
  return [
    {
      scene: 'EXTERNAL_REPORT',
      slotChoice: 'RANGE',
      startTime: parseHm('14:00'),
      endTime: parseHm('17:00'),
    },
    {
      scene: 'INTERNAL_MEETING',
      slotChoice: 'PERIOD:MORNING',
    },
  ];
}

function toPreferRuleForms(rules?: SchedulePreferRule[]): PreferRuleForm[] {
  if (!rules?.length) return defaultPreferRules();
  return rules.map((r) => {
    if (r.slotKind === 'RANGE') {
      return {
        scene: r.scene,
        sceneLabel: r.sceneLabel,
        slotChoice: 'RANGE',
        startTime: parseHm(r.startTime, '14:00'),
        endTime: parseHm(r.endTime, '17:00'),
      };
    }
    return {
      scene: r.scene,
      sceneLabel: r.sceneLabel,
      slotChoice: `PERIOD:${r.period === 'AFTERNOON' ? 'AFTERNOON' : 'MORNING'}`,
    };
  });
}

function fromPreferRuleForms(rules: PreferRuleForm[] | undefined): SchedulePreferRule[] {
  return (rules || [])
    .filter((r) => r?.scene && r?.slotChoice)
    .map((r) => {
      if (r.slotChoice === 'RANGE') {
        return {
          scene: r.scene,
          sceneLabel: r.scene === 'CUSTOM' ? (r.sceneLabel || '').trim() : undefined,
          slotKind: 'RANGE' as const,
          startTime: r.startTime?.format('HH:mm') || '14:00',
          endTime: r.endTime?.format('HH:mm') || '17:00',
        };
      }
      const period = r.slotChoice === 'PERIOD:AFTERNOON' ? 'AFTERNOON' : 'MORNING';
      return {
        scene: r.scene,
        sceneLabel: r.scene === 'CUSTOM' ? (r.sceneLabel || '').trim() : undefined,
        slotKind: 'PERIOD' as const,
        period: period as 'MORNING' | 'AFTERNOON',
      };
    });
}

function normalizePriorityOrder(raw?: string[]): SchedulePriorityCode[] {
  const seen = new Set<SchedulePriorityCode>();
  const out: SchedulePriorityCode[] = [];
  for (const code of raw || []) {
    const c = String(code || '').toUpperCase() as SchedulePriorityCode;
    if (PRIORITY_META[c] && !seen.has(c)) {
      seen.add(c);
      out.push(c);
    }
  }
  for (const def of DEFAULT_PRIORITY_ORDER) {
    if (!seen.has(def)) out.push(def);
  }
  return out;
}

function defaultMeetingPriority(raw?: ScheduleMeetingPriority) {
  const freq = raw?.interviewFreq;
  return {
    priorityOrder: normalizePriorityOrder(raw?.priorityOrder),
    supervisorNames: raw?.supervisorNames ?? [],
    coreProjectDeptIds: raw?.coreProjectDeptIds ?? [],
    otherBizDeptIds: raw?.otherBizDeptIds ?? [],
    preferRules: toPreferRuleForms(raw?.preferRules),
    interviewFreq: {
      smallJobs: freq?.smallJobs ?? [],
      smallJobsDailyMin: freq?.smallJobsDailyMin ?? 2,
      smallJobsDailyMax: freq?.smallJobsDailyMax ?? 3,
      importantJobs: freq?.importantJobs ?? [],
      importantJobsDailyMax: freq?.importantJobsDailyMax ?? 2,
    },
  };
}

/** 动作喜好时段：按工作时间自动生成（周一至周五） */
function workHourActionPrefs(workStart: string, workEnd: string) {
  const windows: ScheduleRuleWindow[] = [{ weekdays: [1, 2, 3, 4, 5], startTime: workStart, endTime: workEnd }];
  return ACTION_META.map((meta) => ({
    action: meta.action,
    enabled: true,
    preferDurationMin: meta.preferDurationMin,
    maxDurationMin: null as number | null,
    windows,
  }));
}

function buildPayload(values: Record<string, unknown>): ScheduleRuleDTO {
  const morningStart = (values.morningStart as Dayjs)?.format?.('HH:mm') || DEFAULT_MORNING_START;
  const morningEnd = (values.morningEnd as Dayjs)?.format?.('HH:mm') || DEFAULT_MORNING_END;
  const forenoonStart = (values.forenoonStart as Dayjs)?.format?.('HH:mm') || DEFAULT_FORENOON_START;
  const forenoonEnd = (values.forenoonEnd as Dayjs)?.format?.('HH:mm') || DEFAULT_FORENOON_END;
  const lunchStart = (values.lunchStart as Dayjs)?.format?.('HH:mm') || DEFAULT_LUNCH_START;
  const lunchEnd = (values.lunchEnd as Dayjs)?.format?.('HH:mm') || DEFAULT_LUNCH_END;
  const assertRange = (start: string, end: string, label: string) => {
    if (end <= start) throw new Error(`${label}结束须晚于开始`);
  };
  assertRange(morningStart, morningEnd, '早晨时段');
  assertRange(forenoonStart, forenoonEnd, '上午时段');
  assertRange(lunchStart, lunchEnd, '午休时段');
  const workStart = morningStart;
  const workEnd = DEFAULT_WORK_END;
  assertRange(workStart, workEnd, '工作时间');
  const mp = (values.meetingPriority || {}) as ReturnType<typeof defaultMeetingPriority>;
  const preferRules = fromPreferRuleForms(mp.preferRules);
  for (const r of preferRules) {
    if (r.slotKind === 'RANGE' && r.startTime && r.endTime) {
      assertRange(r.startTime, r.endTime, '偏好时间');
    }
  }
  return {
    enabled: true,
    secretaryEnabled: true,
    denyHolidays: true,
    bufferMin: typeof values.bufferMin === 'number' ? values.bufferMin : DEFAULT_BUFFER_MIN,
    slotMin: DEFAULT_SLOT_MIN,
    lookAheadDays: 14,
    recommendLimit: 8,
    robotHint: (values.robotHint as string) || '',
    workStart,
    workEnd,
    morningStart,
    morningEnd,
    forenoonStart,
    forenoonEnd,
    lunchStart,
    lunchEnd,
    blockedWindows: [],
    deptMeetingWindows: defaultDeptMeetingWindows(forenoonStart, forenoonEnd),
    meetingPriority: {
      priorityOrder: normalizePriorityOrder(mp.priorityOrder),
      supervisorNames: mp.supervisorNames || [],
      coreProjectDeptIds: mp.coreProjectDeptIds || [],
      otherBizDeptIds: mp.otherBizDeptIds || [],
      preferRules,
      interviewFreq: {
        smallJobs: mp.interviewFreq?.smallJobs || [],
        smallJobsDailyMin: mp.interviewFreq?.smallJobsDailyMin ?? 2,
        smallJobsDailyMax: mp.interviewFreq?.smallJobsDailyMax ?? 3,
        importantJobs: mp.interviewFreq?.importantJobs || [],
        importantJobsDailyMax: mp.interviewFreq?.importantJobsDailyMax ?? 2,
      },
    },
    actionPrefs: workHourActionPrefs(workStart, workEnd),
  };
}

const Tofu = memo(function Tofu({
  title,
  children,
  className = '',
}: {
  title: ReactNode;
  children: ReactNode;
  className?: string;
}) {
  return (
    <Card
      size='small'
      title={<span className='text-sm font-medium'>{title}</span>}
      className={`h-full shadow-sm ${className}`}
      styles={{ body: { padding: '10px 12px' }, header: { minHeight: 40, padding: '0 12px' } }}
    >
      {children}
    </Card>
  );
});

const TimePair = memo(function TimePair({
  startName,
  endName,
  label,
  hint,
}: {
  startName: string;
  endName: string;
  label: string;
  hint?: string;
}) {
  const form = Form.useFormInstance();
  return (
    <div className='col-span-2 sm:col-span-4'>
      <div className='mb-0.5 text-xs font-medium text-neutral-600'>{label}</div>
      {hint ? <div className='mb-1.5 text-[11px] leading-snug text-neutral-400'>{hint}</div> : null}
      <Space.Compact className='w-full max-w-xs'>
        <Form.Item
          name={startName}
          className='!mb-0 w-1/2'
          dependencies={[endName]}
          rules={[
            { required: true, message: '必填' },
            {
              validator: async (_, value: Dayjs | null) => {
                const end = form.getFieldValue(endName) as Dayjs | null;
                if (value && end && !end.isAfter(value)) {
                  throw new Error(`${label}结束须晚于开始`);
                }
              },
            },
          ]}
        >
          <TimePicker
            format='HH:mm'
            minuteStep={5}
            className='w-full'
            needConfirm={false}
            onChange={(v) => {
              const end = form.getFieldValue(endName) as Dayjs | null;
              if (v && end && !end.isAfter(v)) {
                form.setFieldValue(endName, v.add(30, 'minute'));
              }
            }}
          />
        </Form.Item>
        <Form.Item
          name={endName}
          className='!mb-0 w-1/2'
          dependencies={[startName]}
          rules={[
            { required: true, message: '必填' },
            {
              validator: async (_, value: Dayjs | null) => {
                const start = form.getFieldValue(startName) as Dayjs | null;
                if (value && start && !value.isAfter(start)) {
                  throw new Error(`${label}结束须晚于开始`);
                }
              },
            },
          ]}
        >
          <TimePicker
            format='HH:mm'
            minuteStep={5}
            className='w-full'
            needConfirm={false}
            onChange={(v) => {
              const start = form.getFieldValue(startName) as Dayjs | null;
              if (v && start && !v.isAfter(start)) {
                form.setFieldValue(startName, v.subtract(30, 'minute'));
              }
            }}
          />
        </Form.Item>
      </Space.Compact>
    </div>
  );
});

/** 直接上级：AutoComplete 模糊匹配，回车/选中可添加不存在的人名 */
const SupervisorAutoComplete = memo(function SupervisorAutoComplete({
  value,
  onChange,
  disabled,
  options,
}: {
  value?: string[];
  onChange?: (next: string[]) => void;
  disabled?: boolean;
  options: { value: string; label: string; username?: string }[];
}) {
  const selected = value || [];
  const [text, setText] = useState('');

  const suggestions = useMemo(() => {
    const q = text.trim().toLowerCase();
    const picked = new Set(selected);
    return options
      .filter((o) => {
        if (picked.has(o.value)) return false;
        if (!q) return true;
        return (
          o.label.toLowerCase().includes(q) ||
          o.value.toLowerCase().includes(q) ||
          (o.username || '').toLowerCase().includes(q)
        );
      })
      .slice(0, 20)
      .map((o) => ({ value: o.value, label: o.label }));
  }, [options, selected, text]);

  const addName = useCallback(
    (raw: string) => {
      const name = raw.trim();
      if (!name || selected.includes(name)) {
        setText('');
        return;
      }
      onChange?.([...selected, name]);
      setText('');
    },
    [onChange, selected],
  );

  return (
    <div className='w-full'>
      {selected.length > 0 && (
        <div className='mb-1.5 flex flex-wrap gap-1'>
          {selected.map((name) => (
            <Tag
              key={name}
              closable={!disabled}
              onClose={(e) => {
                e.preventDefault();
                onChange?.(selected.filter((n) => n !== name));
              }}
            >
              {name}
            </Tag>
          ))}
        </div>
      )}
      <AutoComplete
        className='w-full'
        disabled={disabled}
        value={text}
        options={suggestions}
        onSearch={setText}
        onChange={setText}
        onSelect={(v) => addName(String(v))}
        placeholder='输入姓名搜索，回车添加'
        allowClear
      >
        <Input
          size='small'
          onPressEnter={(e) => {
            e.preventDefault();
            addName(text);
          }}
        />
      </AutoComplete>
    </div>
  );
});

/** 会议来源优先级：拖拽排序，越靠上越高 */
const PriorityOrderList = memo(function PriorityOrderList({
  value,
  onChange,
  disabled,
  userOptions,
  deptOptions,
}: {
  value?: SchedulePriorityCode[];
  onChange?: (next: SchedulePriorityCode[]) => void;
  disabled?: boolean;
  userOptions: { value: string; label: string; username?: string }[];
  deptOptions: { value: number; label: string }[];
}) {
  const order = normalizePriorityOrder(value);
  const dragIndex = useRef<number | null>(null);
  const [overIndex, setOverIndex] = useState<number | null>(null);

  const move = useCallback(
    (from: number, to: number) => {
      if (from === to || from < 0 || to < 0 || from >= order.length || to >= order.length) return;
      const next = [...order];
      const [item] = next.splice(from, 1);
      next.splice(to, 0, item);
      onChange?.(next);
    },
    [onChange, order],
  );

  return (
    <div className='space-y-2'>
      <p className='mb-0 text-xs text-neutral-500'>
        拖拽左侧手柄调整顺序，越靠上优先级越高。默认：直接上级 &gt; 核心项目部 &gt; 其他业务部门 &gt; 跨部门协作。
      </p>
      {order.map((code, index) => {
        const meta = PRIORITY_META[code];
        const isSupervisor = code === 'SUPERVISOR';
        const fieldName =
          code === 'SUPERVISOR'
            ? (['meetingPriority', 'supervisorNames'] as const)
            : code === 'CORE_PROJECT'
              ? (['meetingPriority', 'coreProjectDeptIds'] as const)
              : code === 'OTHER_BIZ'
                ? (['meetingPriority', 'otherBizDeptIds'] as const)
                : null;
        return (
          <div
            key={code}
            onDragOver={(e) => {
              if (disabled || dragIndex.current == null) return;
              e.preventDefault();
              if (overIndex !== index) setOverIndex(index);
            }}
            onDrop={(e) => {
              e.preventDefault();
              const from = dragIndex.current;
              dragIndex.current = null;
              setOverIndex(null);
              if (from == null) return;
              move(from, index);
            }}
            onDragLeave={() => {
              if (overIndex === index) setOverIndex(null);
            }}
            className={`flex flex-wrap items-center gap-2 rounded border bg-white px-2 py-2 transition-colors ${
              overIndex === index ? 'border-sky-400 bg-sky-50/60' : 'border-neutral-200'
            } ${disabled ? 'opacity-70' : ''}`}
          >
            <span className='flex w-6 shrink-0 items-center justify-center text-xs font-semibold text-neutral-400 tabular-nums'>
              {index + 1}
            </span>
            <button
              type='button'
              disabled={disabled}
              draggable={!disabled}
              className='flex h-7 w-7 shrink-0 cursor-grab items-center justify-center rounded text-neutral-400 hover:bg-neutral-100 active:cursor-grabbing disabled:cursor-not-allowed'
              title='拖拽排序'
              onDragStart={(e) => {
                dragIndex.current = index;
                e.dataTransfer.effectAllowed = 'move';
                e.dataTransfer.setData('text/plain', String(index));
              }}
              onDragEnd={() => {
                dragIndex.current = null;
                setOverIndex(null);
              }}
            >
              <HolderOutlined />
            </button>
            <div className='min-w-[96px] shrink-0'>
              <div className='text-sm font-medium text-neutral-800'>{meta.label}</div>
              <div className='text-[11px] text-neutral-400'>{meta.hint}</div>
            </div>
            <div className='min-w-[180px] flex-1'>
              {isSupervisor ? (
                <Form.Item
                  name={['meetingPriority', 'supervisorNames']}
                  className='!mb-0'
                >
                  <SupervisorAutoComplete
                    disabled={disabled}
                    options={userOptions}
                  />
                </Form.Item>
              ) : fieldName ? (
                <Form.Item
                  name={[...fieldName]}
                  className='!mb-0'
                >
                  <Select
                    mode='multiple'
                    allowClear
                    showSearch
                    optionFilterProp='label'
                    placeholder='可多选'
                    options={deptOptions}
                    disabled={disabled}
                    maxTagCount='responsive'
                  />
                </Form.Item>
              ) : (
                <span className='text-xs text-neutral-400'>无需指定对象，按会议是否跨部门判定</span>
              )}
            </div>
          </div>
        );
      })}
    </div>
  );
});

/** 将「外部汇报」安排在 14:00~17:00；将「内部会议」安排在上午 */
const PreferRuleList = memo(function PreferRuleList({ disabled }: { disabled?: boolean }) {
  return (
    <Form.List name={['meetingPriority', 'preferRules']}>
      {(fields, { add, remove }) => (
        <div className='space-y-2'>
          {fields.map((field) => (
            <Form.Item
              key={field.key}
              noStyle
              shouldUpdate={(prev, cur) => {
                const p = prev?.meetingPriority?.preferRules?.[field.name];
                const c = cur?.meetingPriority?.preferRules?.[field.name];
                return p?.scene !== c?.scene || p?.slotChoice !== c?.slotChoice;
              }}
            >
              {({ getFieldValue }) => {
                const scene = getFieldValue(['meetingPriority', 'preferRules', field.name, 'scene']) as
                  SchedulePreferScene | undefined;
                const slotChoice = getFieldValue(['meetingPriority', 'preferRules', field.name, 'slotChoice']) as
                  string | undefined;
                const isCustom = scene === 'CUSTOM';
                const isRange = slotChoice === 'RANGE';
                return (
                  <div className='flex flex-wrap items-center gap-1.5 rounded border border-neutral-200 bg-neutral-50/50 px-2 py-2'>
                    <span className='shrink-0 text-xs text-neutral-500'>将</span>
                    <Form.Item
                      {...field}
                      name={[field.name, 'scene']}
                      className='!mb-0 w-[120px]'
                      rules={[{ required: true, message: '选场景' }]}
                    >
                      <Select
                        options={PREFER_SCENE_OPTIONS}
                        disabled={disabled}
                        placeholder='场景'
                      />
                    </Form.Item>
                    {isCustom && (
                      <Form.Item
                        {...field}
                        name={[field.name, 'sceneLabel']}
                        className='!mb-0 w-[120px]'
                        rules={[{ required: true, message: '填名称' }]}
                      >
                        <Input
                          placeholder='自定义名称'
                          maxLength={40}
                          disabled={disabled}
                        />
                      </Form.Item>
                    )}
                    <span className='shrink-0 text-xs text-neutral-500'>安排在</span>
                    <Form.Item
                      {...field}
                      name={[field.name, 'slotChoice']}
                      className='!mb-0 w-[120px]'
                      rules={[{ required: true, message: '选时段' }]}
                    >
                      <Select
                        options={[...PREFER_SLOT_OPTIONS]}
                        disabled={disabled}
                        placeholder='时段'
                      />
                    </Form.Item>
                    {isRange && (
                      <Form.Item
                        noStyle
                        shouldUpdate={(prev, cur) => {
                          const p = prev?.meetingPriority?.preferRules?.[field.name];
                          const c = cur?.meetingPriority?.preferRules?.[field.name];
                          return p?.startTime !== c?.startTime || p?.endTime !== c?.endTime;
                        }}
                      >
                        {({ getFieldValue, setFieldValue }) => {
                          const startPath = ['meetingPriority', 'preferRules', field.name, 'startTime'] as const;
                          const endPath = ['meetingPriority', 'preferRules', field.name, 'endTime'] as const;
                          return (
                            <>
                              <Form.Item
                                {...field}
                                name={[field.name, 'startTime']}
                                className='!mb-0 w-[100px]'
                                dependencies={[[field.name, 'endTime']]}
                                rules={[
                                  { required: true, message: '开始' },
                                  {
                                    validator: async (_, value: Dayjs | null) => {
                                      const end = getFieldValue(endPath) as Dayjs | null;
                                      if (value && end && !end.isAfter(value)) {
                                        throw new Error('结束须晚于开始');
                                      }
                                    },
                                  },
                                ]}
                              >
                                <TimePicker
                                  format='HH:mm'
                                  minuteStep={5}
                                  className='w-full'
                                  needConfirm={false}
                                  disabled={disabled}
                                  onChange={(v) => {
                                    const end = getFieldValue(endPath) as Dayjs | null;
                                    if (v && end && !end.isAfter(v)) {
                                      setFieldValue(endPath, v.add(30, 'minute'));
                                    }
                                  }}
                                />
                              </Form.Item>
                              <span className='shrink-0 text-xs text-neutral-400'>~</span>
                              <Form.Item
                                {...field}
                                name={[field.name, 'endTime']}
                                className='!mb-0 w-[100px]'
                                dependencies={[[field.name, 'startTime']]}
                                rules={[
                                  { required: true, message: '结束' },
                                  {
                                    validator: async (_, value: Dayjs | null) => {
                                      const start = getFieldValue(startPath) as Dayjs | null;
                                      if (value && start && !value.isAfter(start)) {
                                        throw new Error('结束须晚于开始');
                                      }
                                    },
                                  },
                                ]}
                              >
                                <TimePicker
                                  format='HH:mm'
                                  minuteStep={5}
                                  className='w-full'
                                  needConfirm={false}
                                  disabled={disabled}
                                  onChange={(v) => {
                                    const start = getFieldValue(startPath) as Dayjs | null;
                                    if (v && start && !v.isAfter(start)) {
                                      setFieldValue(startPath, v.subtract(30, 'minute'));
                                    }
                                  }}
                                />
                              </Form.Item>
                            </>
                          );
                        }}
                      </Form.Item>
                    )}
                    {!disabled && (
                      <Button
                        type='text'
                        danger
                        size='small'
                        icon={<MinusCircleOutlined />}
                        onClick={() => remove(field.name)}
                      />
                    )}
                  </div>
                );
              }}
            </Form.Item>
          ))}
          {!disabled && (
            <Button
              type='dashed'
              size='small'
              icon={<PlusOutlined />}
              onClick={() =>
                add({
                  scene: 'EXTERNAL_MEETING',
                  slotChoice: 'PERIOD:AFTERNOON',
                })
              }
            >
              添加偏好
            </Button>
          )}
        </div>
      )}
    </Form.List>
  );
});

const ScheduleRulePage = memo(function ScheduleRulePage() {
  const { has } = usePermission();
  const canEdit = has('system:schedule-rule:edit');
  const [form] = Form.useForm();
  const [loading, setLoading] = useState(false);
  const [saveStatus, setSaveStatus] = useState<SaveStatus>('idle');
  const [options, setOptions] = useState<ScheduleRuleOptions>({ users: [], departments: [], jobs: [] });
  const readyRef = useRef(false);
  const timerRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const seqRef = useRef(0);

  /** value/label 均用昵称，便于 tags 手填与已有用户统一存字符串 */
  const userOptions = useMemo(
    () =>
      (options.users || []).map((u) => ({
        value: u.label,
        label: u.username && u.username !== u.label ? `${u.label}（${u.username}）` : u.label,
        username: u.username || '',
      })),
    [options.users],
  );
  const deptOptions = useMemo(
    () => options.departments.map((d) => ({ value: d.value, label: d.label })),
    [options.departments],
  );
  const jobOptions = useMemo(() => options.jobs.map((j) => ({ value: j, label: j })), [options.jobs]);

  const load = useCallback(async () => {
    readyRef.current = false;
    setLoading(true);
    try {
      const [data, opts] = await Promise.all([getMyScheduleRuleApi(), getScheduleRuleOptionsApi()]);
      setOptions(opts || { users: [], departments: [], jobs: [] });
      form.setFieldsValue({
        bufferMin: data.bufferMin ?? DEFAULT_BUFFER_MIN,
        robotHint: data.robotHint || '',
        morningStart: parseHm(data.morningStart, DEFAULT_MORNING_START),
        morningEnd: parseHm(data.morningEnd, DEFAULT_MORNING_END),
        forenoonStart: parseHm(data.forenoonStart, DEFAULT_FORENOON_START),
        forenoonEnd: parseHm(data.forenoonEnd, DEFAULT_FORENOON_END),
        lunchStart: parseHm(data.lunchStart, DEFAULT_LUNCH_START),
        lunchEnd: parseHm(data.lunchEnd, DEFAULT_LUNCH_END),
        meetingPriority: defaultMeetingPriority(data.meetingPriority),
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
    let payload: ScheduleRuleDTO;
    try {
      payload = buildPayload(values);
    } catch {
      // 时段起止未调顺时先不提交，等用户改完再自动保存
      setSaveStatus('idle');
      return;
    }
    const seq = ++seqRef.current;
    setSaveStatus('saving');
    try {
      await saveMyScheduleRuleApi(payload);
      if (seq === seqRef.current) setSaveStatus('saved');
    } catch {
      // 业务错误已由 request 拦截器 toast，这里只更新状态，避免重复「自动保存失败」
      if (seq === seqRef.current) setSaveStatus('error');
    }
  }, [canEdit, form]);

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
            仅本人可配。工作时间、午休、早晨与会议优先级会参与秘书推荐。
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
          bufferMin: DEFAULT_BUFFER_MIN,
          morningStart: parseHm(DEFAULT_MORNING_START),
          morningEnd: parseHm(DEFAULT_MORNING_END),
          forenoonStart: parseHm(DEFAULT_FORENOON_START),
          forenoonEnd: parseHm(DEFAULT_FORENOON_END),
          lunchStart: parseHm(DEFAULT_LUNCH_START),
          lunchEnd: parseHm(DEFAULT_LUNCH_END),
          meetingPriority: defaultMeetingPriority(),
        }}
      >
        <div className='grid grid-cols-1 gap-3 md:grid-cols-2'>
          <Tofu title='基本配置'>
            <div className='grid grid-cols-2 gap-x-3 gap-y-3 sm:grid-cols-4'>
              <Form.Item
                name='bufferMin'
                label='缓冲时间'
                layout='vertical'
                labelCol={{ span: 24 }}
                wrapperCol={{ span: 24 }}
                className='!mb-0'
                tooltip='相邻日程之间的缓冲间隔（分钟）'
              >
                <InputNumber
                  min={0}
                  max={240}
                  step={5}
                  className='w-full max-w-[140px]'
                />
              </Form.Item>
              <TimePair
                startName='morningStart'
                endName='morningEnd'
                label='早晨时段'
                hint='一般不安排日程，除非很紧急重要事项'
              />
              <TimePair
                startName='forenoonStart'
                endName='forenoonEnd'
                label='上午时段'
                hint='集中安排部门会议'
              />
              <TimePair
                startName='lunchStart'
                endName='lunchEnd'
                label='午休时段'
                hint='为吃饭时间，尽量不要安排工作'
              />
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

          <Tofu title='会议优先级'>
            <Form.Item
              name={['meetingPriority', 'priorityOrder']}
              className='!mb-0'
            >
              <PriorityOrderList
                disabled={!canEdit || loading}
                userOptions={userOptions}
                deptOptions={deptOptions}
              />
            </Form.Item>
          </Tofu>

          <Tofu title='偏好时间设置'>
            <p className='mb-2 text-xs text-neutral-500'>
              例：将「外部汇报」安排在 14:00~17:00；将「内部会议」安排在上午。
            </p>
            <PreferRuleList disabled={!canEdit || loading} />
          </Tofu>

          <Tofu title='面试频次设置'>
            <div className='grid grid-cols-1 gap-x-4 gap-y-2 sm:grid-cols-2'>
              <Form.Item
                name={['meetingPriority', 'interviewFreq', 'smallJobs']}
                label='小岗位'
                layout='vertical'
                labelCol={{ span: 24 }}
                wrapperCol={{ span: 24 }}
                className='!mb-0'
              >
                <Select
                  mode='tags'
                  allowClear
                  placeholder='可多选或输入岗位名'
                  options={jobOptions}
                />
              </Form.Item>
              <div className='grid grid-cols-2 gap-2'>
                <Form.Item
                  name={['meetingPriority', 'interviewFreq', 'smallJobsDailyMin']}
                  label='每天合计下限'
                  layout='vertical'
                  labelCol={{ span: 24 }}
                  wrapperCol={{ span: 24 }}
                  className='!mb-0'
                >
                  <InputNumber
                    min={0}
                    max={20}
                    className='w-full'
                  />
                </Form.Item>
                <Form.Item
                  name={['meetingPriority', 'interviewFreq', 'smallJobsDailyMax']}
                  label='每天合计上限'
                  layout='vertical'
                  labelCol={{ span: 24 }}
                  wrapperCol={{ span: 24 }}
                  className='!mb-0'
                  tooltip='默认 2～3 个'
                >
                  <InputNumber
                    min={0}
                    max={20}
                    className='w-full'
                  />
                </Form.Item>
              </div>
              <Form.Item
                name={['meetingPriority', 'interviewFreq', 'importantJobs']}
                label='重要岗位'
                layout='vertical'
                labelCol={{ span: 24 }}
                wrapperCol={{ span: 24 }}
                className='!mb-0'
              >
                <Select
                  mode='tags'
                  allowClear
                  placeholder='可多选或输入岗位名'
                  options={jobOptions}
                />
              </Form.Item>
              <Form.Item
                name={['meetingPriority', 'interviewFreq', 'importantJobsDailyMax']}
                label='每天最多'
                layout='vertical'
                labelCol={{ span: 24 }}
                wrapperCol={{ span: 24 }}
                className='!mb-0'
                tooltip='重要岗位默认最多安排 2 个'
              >
                <InputNumber
                  min={0}
                  max={20}
                  className='w-full'
                />
              </Form.Item>
            </div>
          </Tofu>
        </div>
      </Form>
    </div>
  );
});

export default ScheduleRulePage;
