import { useEffect, useMemo, useState } from 'react';
import { App, Button, Checkbox, Input, InputNumber, Modal, Select, Switch, Tag } from 'antd';
import {
  CloseOutlined,
  CopyOutlined,
  DeleteOutlined,
  EditOutlined,
  InboxOutlined,
  PlusOutlined,
  UploadOutlined,
} from '@ant-design/icons';
import { getGeoTopicOptionsApi } from '@/api/geo';
import {
  createGeoAutoQuestionApi,
  createGeoAutoTaskApi,
  getGeoAutoPlatformOptionsApi,
  getGeoAutoQuestionListApi,
  getGeoAutoQuestionOptionsApi,
  updateGeoAutoTaskApi,
} from '@/api/geoAuto';
import type {
  GeoAutoPlatformOption,
  GeoAutoQuestion,
  GeoAutoQuestionOption,
  GeoAutoTask,
  GeoAutoTaskDTO,
} from '@/types/geoAuto';
import { GEO_AUTO_PROBE_REGIONS, GEO_AUTO_SCREENSHOT_MODES } from '@/constants/geoAuto';
import type { GeoTopic } from '@/types/geo';
import { platformAvatar, splitPlatforms } from './modelMeta';
import './batchAddMonitorModal.css';

interface BatchAddMonitorModalProps {
  open: boolean;
  editing: GeoAutoTask | null;
  onClose: () => void;
  onSaved: (taskName?: string) => void;
}

type QuestionTab = 'query' | 'import' | 'recommend';

interface QuestionItem {
  text: string;
  coreWord: string;
  enabled: boolean;
}

interface CoreWordItem {
  text: string;
  enabled: boolean;
}

function toScreenshotMode(raw?: string) {
  if (raw === 'DISABLED' || raw === 'ALL' || raw === 'MENTION') {
    return raw;
  }
  return 'MENTION';
}

function parseRemarkParts(remark?: string) {
  const map = new Map<string, string>();
  for (const part of (remark || '').split(/[；;]/)) {
    const text = part.trim();
    if (!text) {
      continue;
    }
    const colon = Math.min(
      ...[':', '：']
        .map((ch) => {
          const i = text.indexOf(ch);
          return i < 0 ? Number.POSITIVE_INFINITY : i;
        })
        .filter((i) => Number.isFinite(i)),
    );
    if (!Number.isFinite(colon)) {
      continue;
    }
    map.set(text.slice(0, colon).trim(), text.slice(colon + 1).trim());
  }
  return map;
}

function splitCsv(raw?: string) {
  return (raw || '')
    .split(',')
    .map((s) => s.trim())
    .filter(Boolean);
}

function taskScopeIds(task: GeoAutoTask) {
  if (task.scopeIdList?.length) {
    return task.scopeIdList.map(Number).filter((n) => Number.isFinite(n));
  }
  if (!task.scopeIds) {
    return [];
  }
  return String(task.scopeIds)
    .split(',')
    .map((s) => Number(s.trim()))
    .filter((n) => Number.isFinite(n) && n > 0);
}

function coreFromQuestionRemark(remark?: string) {
  const hit = (remark || '').match(/核心词\s*[:：]\s*(.+)/);
  return hit?.[1]?.trim() || '';
}

export default function BatchAddMonitorModal({ open, editing, onClose, onSaved }: BatchAddMonitorModalProps) {
  const { message } = App.useApp();
  const [taskName, setTaskName] = useState('');
  const [topicIds, setTopicIds] = useState<number[]>([]);
  const [competitors, setCompetitors] = useState<string[]>([]);
  const [compDraft, setCompDraft] = useState('');
  const [freqOn, setFreqOn] = useState(true);
  const [coreWords, setCoreWords] = useState<CoreWordItem[]>([]);
  const [coreDraft, setCoreDraft] = useState('');
  const [editingCore, setEditingCore] = useState('');
  const [coreEditDraft, setCoreEditDraft] = useState('');
  const [activeCore, setActiveCore] = useState('');
  const [questions, setQuestions] = useState<QuestionItem[]>([]);
  const [questionDraft, setQuestionDraft] = useState('');
  const [composing, setComposing] = useState(false);
  const [editingKey, setEditingKey] = useState('');
  const [importDraft, setImportDraft] = useState('');
  const [questionTab, setQuestionTab] = useState<QuestionTab>('query');
  const [platforms, setPlatforms] = useState<GeoAutoPlatformOption[]>([]);
  const [selectedPlatforms, setSelectedPlatforms] = useState<string[]>([]);
  const [deepThinkModels, setDeepThinkModels] = useState<string[]>([]);
  const [screenshotMode, setScreenshotMode] = useState('MENTION');
  const [freq, setFreq] = useState('每日');
  const [askCount, setAskCount] = useState(2);
  const [probeRegion, setProbeRegion] = useState('全国随机');
  const [sentimentOn, setSentimentOn] = useState(true);
  const [topics, setTopics] = useState<GeoTopic[]>([]);
  const [existingQuestions, setExistingQuestions] = useState<GeoAutoQuestionOption[]>([]);
  const [submitting, setSubmitting] = useState(false);

  useEffect(() => {
    if (!open) {
      return;
    }
    getGeoAutoPlatformOptionsApi()
      .then(setPlatforms)
      .catch(() => setPlatforms([]));
    getGeoTopicOptionsApi()
      .then(setTopics)
      .catch(() => setTopics([]));
    getGeoAutoQuestionOptionsApi()
      .then(setExistingQuestions)
      .catch(() => setExistingQuestions([]));
  }, [open]);

  useEffect(() => {
    if (!open) {
      return;
    }
    if (!editing) {
      setTaskName('');
      setTopicIds([]);
      setCompetitors([]);
      setCompDraft('');
      setFreqOn(true);
      setCoreWords([]);
      setCoreDraft('');
      setEditingCore('');
      setCoreEditDraft('');
      setActiveCore('');
      setQuestions([]);
      setQuestionDraft('');
      setComposing(false);
      setEditingKey('');
      setImportDraft('');
      setQuestionTab('query');
      setSelectedPlatforms([]);
      setDeepThinkModels([]);
      setScreenshotMode('MENTION');
      setFreq('每日');
      setAskCount(2);
      setProbeRegion('全国随机');
      setSentimentOn(true);
      return;
    }

    const parts = parseRemarkParts(editing.remark);
    const coreTexts = splitCsv(parts.get('核心词'));
    const brandIds = splitCsv(parts.get('品牌ID'))
      .map(Number)
      .filter((n) => Number.isFinite(n) && n > 0);
    const comps = splitCsv(parts.get('竞品'));
    const ask = Number(parts.get('提问次数'));
    const listedThink = splitCsv(parts.get('深度思考模型'));
    const scopeIds = taskScopeIds(editing);

    setTaskName(editing.taskName || '');
    setCompetitors(comps);
    setCompDraft('');
    setCoreWords(coreTexts.map((text) => ({ text, enabled: true })));
    setCoreDraft('');
    setEditingCore('');
    setCoreEditDraft('');
    setActiveCore(coreTexts[0] || '');
    setQuestionDraft('');
    setComposing(false);
    setEditingKey('');
    setImportDraft('');
    setQuestionTab('query');
    setSelectedPlatforms(editing.platformList ?? []);
    setFreq(editing.freq || '每日');
    setDeepThinkModels(listedThink.length ? listedThink : editing.deepThink === 1 ? (editing.platformList ?? []) : []);
    setScreenshotMode(toScreenshotMode(editing.screenshotMode));
    setProbeRegion(editing.probeRegion || '全国随机');
    setFreqOn(editing.enabled !== 0);
    setAskCount(Number.isFinite(ask) && ask > 0 ? ask : 2);
    if (editing.scopeType === 'TOPIC' && !brandIds.length) {
      setTopicIds(scopeIds);
    } else {
      setTopicIds(brandIds);
    }

    let cancelled = false;
    const hydrateQuestions = async () => {
      const idSet = new Set(scopeIds);
      let rows: Array<Pick<GeoAutoQuestion, 'id' | 'questionText' | 'topicId' | 'remark' | 'enabled'>> = [];
      const fromOptions = (list: GeoAutoQuestionOption[]) =>
        list
          .filter((q) => idSet.has(Number(q.id)))
          .map((q) => ({
            id: q.id,
            questionText: q.questionText,
            topicId: q.topicId,
            remark: '',
            enabled: 1,
          }));
      try {
        const res = await getGeoAutoQuestionListApi({ pageNum: 1, pageSize: 500 });
        rows = (res.rows || []).filter((q) => idSet.has(Number(q.id)));
      } catch {
        rows = fromOptions(existingQuestions);
      }
      if (!rows.length && existingQuestions.length) {
        rows = fromOptions(existingQuestions);
      }
      if (cancelled) {
        return;
      }
      const packed: QuestionItem[] = rows.map((q) => ({
        text: q.questionText,
        coreWord: coreFromQuestionRemark(q.remark) || coreTexts[0] || '',
        enabled: q.enabled !== 0,
      }));
      const cores =
        coreTexts.length > 0 ? coreTexts : Array.from(new Set(packed.map((q) => q.coreWord).filter(Boolean)));
      setCoreWords(cores.map((text) => ({ text, enabled: true })));
      setQuestions(
        packed.map((q) => ({
          ...q,
          coreWord: q.coreWord || cores[0] || '',
        })),
      );
      setActiveCore(cores[0] || packed[0]?.coreWord || '');
      const topicFromQs = Array.from(new Set(rows.map((q) => Number(q.topicId)).filter((n) => n > 0)));
      if (!brandIds.length && editing.scopeType !== 'TOPIC') {
        setTopicIds(topicFromQs);
      } else if (!brandIds.length && editing.scopeType === 'TOPIC' && !scopeIds.length) {
        setTopicIds(topicFromQs);
      }
    };
    void hydrateQuestions();
    return () => {
      cancelled = true;
    };
    // 回填以任务 id / 打开状态为准，避免 options 后到时冲掉正在编辑的内容
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [open, editing]);

  const domestic = useMemo(() => splitPlatforms(platforms).domestic, [platforms]);

  const togglePlatform = (name: string, ready: boolean) => {
    if (!ready) {
      return;
    }
    setSelectedPlatforms((prev) => (prev.includes(name) ? prev.filter((x) => x !== name) : [...prev, name]));
  };

  const selectGroup = (list: GeoAutoPlatformOption[]) => {
    const names = list.filter((p) => p.providerReady).map((p) => p.platformName);
    setSelectedPlatforms((prev) => Array.from(new Set([...prev, ...names])));
  };

  const upsertCoreWord = (text: string, enabled: boolean) => {
    setCoreWords((prev) => {
      const idx = prev.findIndex((w) => w.text === text);
      if (idx >= 0) {
        return prev.map((w, i) => (i === idx ? { ...w, enabled } : w));
      }
      return [...prev, { text, enabled }];
    });
  };

  const addQuestionLines = (raw: string, coreWord = activeCore) => {
    if (!coreWord) {
      message.warning('请先添加并选中一个核心词');
      return 0;
    }
    const lines = raw
      .split(/\r?\n/)
      .map((s) => s.trim())
      .filter(Boolean);
    if (!lines.length) {
      return 0;
    }
    let added = 0;
    setQuestions((prev) => {
      const next = [...prev];
      for (const text of lines) {
        if (text.length > 500) {
          message.warning(`问题超过 500 字，已跳过：${text.slice(0, 12)}…`);
          continue;
        }
        if (next.some((q) => q.coreWord === coreWord && q.text === text)) {
          continue;
        }
        next.push({ text, coreWord, enabled: true });
        added += 1;
      }
      return next;
    });
    return added;
  };

  const commitDraft = () => {
    const n = addQuestionLines(questionDraft);
    if (n > 0 || !questionDraft.trim()) {
      setQuestionDraft('');
      setComposing(false);
    }
  };

  const importQuestions = () => {
    const n = addQuestionLines(importDraft);
    setImportDraft('');
    setQuestionTab('query');
    if (n > 0) {
      message.success(`已导入 ${n} 条`);
    }
  };

  const addCore = () => {
    const text = coreDraft.trim();
    if (!text) {
      return;
    }
    if (coreWords.some((w) => w.text === text)) {
      setActiveCore(text);
      setCoreDraft('');
      return;
    }
    upsertCoreWord(text, true);
    setActiveCore(text);
    setCoreDraft('');
    setEditingCore('');
    setCoreEditDraft('');
    setComposing(false);
    setQuestionDraft('');
    setEditingKey('');
  };

  const renameCore = (from: string, toRaw: string) => {
    const to = toRaw.trim();
    if (!to || to === from) {
      setEditingCore('');
      setCoreEditDraft('');
      return;
    }
    if (coreWords.some((w) => w.text === to)) {
      message.warning('该核心词已存在');
      return;
    }
    setCoreWords((prev) => prev.map((w) => (w.text === from ? { ...w, text: to } : w)));
    setQuestions((prev) => prev.map((q) => (q.coreWord === from ? { ...q, coreWord: to } : q)));
    if (activeCore === from) {
      setActiveCore(to);
    }
    setEditingCore('');
    setCoreEditDraft('');
  };

  const addCompetitor = () => {
    const text = compDraft.trim();
    if (!text) {
      return;
    }
    setCompetitors((prev) => (prev.includes(text) ? prev : [...prev, text]));
    setCompDraft('');
  };

  const copyQuestions = async () => {
    const list = questions.filter((q) => q.coreWord === activeCore);
    if (!list.length) {
      message.info('当前核心词还没有监控问题');
      return;
    }
    await navigator.clipboard.writeText(list.map((q) => q.text).join('\n'));
    message.success('已复制问题');
  };

  const visibleQuestions = questions.filter((q) => q.coreWord === activeCore);

  const readyDomestic = domestic.filter((p) => p.providerReady);
  const allModelsChecked =
    readyDomestic.length > 0 && readyDomestic.every((p) => selectedPlatforms.includes(p.platformName));
  const selectedReadyNames = selectedPlatforms.filter((name) => readyDomestic.some((p) => p.platformName === name));
  const thinkControlNames = readyDomestic.map((p) => p.platformName);
  const deepThinkTargets = selectedReadyNames.length ? selectedReadyNames : thinkControlNames;
  const allSelectedDeepThink =
    deepThinkTargets.length > 0 && deepThinkTargets.every((name) => deepThinkModels.includes(name));

  const setModelDeepThink = (name: string, on: boolean) => {
    setDeepThinkModels((prev) => (on ? Array.from(new Set([...prev, name])) : prev.filter((x) => x !== name)));
  };

  const setAllDeepThink = (on: boolean) => {
    setDeepThinkModels((prev) => {
      if (on) {
        return Array.from(new Set([...prev, ...thinkControlNames]));
      }
      return prev.filter((name) => !thinkControlNames.includes(name));
    });
  };

  const handleSave = async () => {
    if (!taskName.trim()) {
      message.warning('请填写监控名称');
      return;
    }
    if (!topicIds.length && !topics.length) {
      message.warning('请先在话题管理中创建品牌/话题，再创建监控');
      return;
    }
    if (!questions.length) {
      message.warning('请至少添加一个监控问题');
      return;
    }
    const enabledCoreWords = coreWords.filter((w) => w.enabled).map((w) => w.text);
    if (!selectedPlatforms.length) {
      message.warning('请至少选择一个模型');
      return;
    }
    const topicId = topicIds[0] ?? topics[0]?.id;
    if (!topicId) {
      message.warning('请选择监控品牌');
      return;
    }
    setSubmitting(true);
    try {
      let optionList = existingQuestions;
      try {
        optionList = await getGeoAutoQuestionOptionsApi();
      } catch {
        /* keep */
      }
      const byText = new Map(optionList.map((q) => [q.questionText.trim(), q.id]));
      const scopeIds: number[] = [];
      for (const item of questions) {
        const existed = byText.get(item.text);
        if (existed) {
          scopeIds.push(existed);
          continue;
        }
        await createGeoAutoQuestionApi({
          questionText: item.text,
          topicId,
          brandWords: competitors.join(',') || undefined,
          freq,
          enabled: 1,
          remark: item.coreWord ? `核心词：${item.coreWord}` : undefined,
        });
      }
      const refreshed = await getGeoAutoQuestionOptionsApi();
      const latest = new Map(refreshed.map((q) => [q.questionText.trim(), q.id]));
      questions.forEach((item) => {
        const id = latest.get(item.text);
        if (id && !scopeIds.includes(id)) {
          scopeIds.push(id);
        }
      });
      if (!scopeIds.length) {
        message.error('问题未能写入问题库，请检查是否已存在或权限不足');
        return;
      }
      const payload: GeoAutoTaskDTO = {
        id: editing?.id,
        taskName: taskName.trim(),
        scopeType: 'QUESTION',
        scopeIds,
        platforms: selectedPlatforms.filter((name) => domestic.some((p) => p.platformName === name)),
        freq,
        runTime: editing?.runTime || '08:30:00',
        weekDay: freq === '每周' ? (editing?.weekDay ?? 1) : undefined,
        overwriteManual: editing?.overwriteManual ?? 0,
        deepThink: allSelectedDeepThink ? 1 : 0,
        screenshotMode,
        probeRegion,
        notifyEnabled: editing?.notifyEnabled ?? 1,
        enabled: freqOn ? 1 : 0,
        remark: [
          topicIds.length ? `品牌ID:${topicIds.join(',')}` : '',
          competitors.length ? `竞品:${competitors.join(',')}` : '',
          enabledCoreWords.length ? `核心词:${enabledCoreWords.join(',')}` : '',
          `提问次数:${askCount}`,
          deepThinkModels.length ? `深度思考模型:${deepThinkModels.join(',')}` : '',
        ]
          .filter(Boolean)
          .join('；'),
      };
      if (editing) {
        await updateGeoAutoTaskApi(payload);
        message.success('监控已更新');
      } else {
        await createGeoAutoTaskApi(payload);
        message.success('监控已保存');
      }
      onSaved(taskName.trim());
      onClose();
    } finally {
      setSubmitting(false);
    }
  };

  const renderModelCard = (p: GeoAutoPlatformOption) => {
    const selected = selectedPlatforms.includes(p.platformName);
    const avatar = platformAvatar(p.platformName);
    const disabled = !p.providerReady;
    return (
      <div
        key={p.platformName}
        className={`relative rounded-xl border px-3 py-2.5 ${
          selected ? 'border-violet-400 bg-violet-50/60' : 'border-neutral-200 bg-white'
        } ${disabled ? 'opacity-50' : ''}`}
      >
        <button
          type='button'
          disabled={disabled}
          onClick={() => togglePlatform(p.platformName, p.providerReady)}
          className='flex w-full items-center gap-2 text-left'
        >
          <span
            className='flex h-7 w-7 shrink-0 items-center justify-center overflow-hidden rounded-full'
            style={{ background: avatar.bg, color: avatar.fg }}
          >
            {avatar.src ? (
              <img
                src={avatar.src}
                alt=''
                className='h-5 w-5 object-contain'
              />
            ) : (
              <span className='text-xs font-semibold'>{avatar.letter}</span>
            )}
          </span>
          <span className='min-w-0 flex-1 truncate text-sm text-neutral-800'>{p.platformName}</span>
          <Tag
            color='purple'
            className='m-0 px-1 py-0 text-[10px] leading-4'
          >
            网页版
          </Tag>
        </button>
        <div className='mt-1.5 pl-9 text-[11px] text-neutral-400'>
          {disabled ? (
            p.hint || '不支持深度思考'
          ) : (
            <span className='flex items-center gap-2'>
              思考
              <label className='flex cursor-pointer items-center gap-1'>
                <input
                  type='radio'
                  name={`think-${p.platformName}`}
                  checked={deepThinkModels.includes(p.platformName)}
                  onChange={() => setModelDeepThink(p.platformName, true)}
                />
                开启
              </label>
              <label className='flex cursor-pointer items-center gap-1'>
                <input
                  type='radio'
                  name={`think-${p.platformName}`}
                  checked={!deepThinkModels.includes(p.platformName)}
                  onChange={() => setModelDeepThink(p.platformName, false)}
                />
                关闭
              </label>
            </span>
          )}
        </div>
      </div>
    );
  };

  return (
    <Modal
      title={editing ? '编辑监控' : '批量添加问题'}
      open={open}
      onCancel={onClose}
      maskClosable={false}
      width='92vw'
      destroyOnHidden
      centered={false}
      rootClassName='geo-batch-add-modal'
      styles={{
        container: {
          height: '85vh',
          maxHeight: '85vh',
          display: 'flex',
          flexDirection: 'column',
        },
        body: {
          flex: 1,
          minHeight: 0,
          overflow: 'auto',
        },
      }}
      footer={
        <div className='flex justify-end gap-2'>
          <Button onClick={onClose}>取消</Button>
          <Button
            type='primary'
            className='!bg-violet-600'
            loading={submitting}
            onClick={() => {
              void handleSave();
            }}
          >
            保存监控
          </Button>
        </div>
      }
    >
      <div className='flex h-full min-h-0 gap-5 overflow-hidden'>
        <div className='flex min-w-0 flex-1 flex-col gap-3'>
          <div className='grid grid-cols-2 gap-3'>
            <div>
              <div className='mb-1 text-sm'>
                监控名称 <span className='text-red-500'>*</span>
              </div>
              <Input
                placeholder='请输入监控名称'
                value={taskName}
                maxLength={128}
                onChange={(e) => setTaskName(e.target.value)}
              />
            </div>
            <div>
              <div className='mb-1 text-sm'>
                监控品牌 <span className='text-red-500'>*</span>
              </div>
              <Select
                mode='multiple'
                className='w-full'
                placeholder='点击添加监控品牌'
                value={topicIds}
                options={topics.map((t) => ({ label: t.topicName, value: t.id }))}
                onChange={setTopicIds}
                showSearch
                optionFilterProp='label'
              />
            </div>
          </div>

          <div>
            <div className='mb-1 flex items-center justify-between text-sm'>
              竞品品牌
              <button
                type='button'
                className='text-xs text-violet-500'
              >
                推荐竞品
              </button>
            </div>
            <div className='flex min-h-10 items-center gap-2 rounded-lg border border-neutral-200 bg-white px-2'>
              <button
                type='button'
                className='flex h-6 w-6 shrink-0 items-center justify-center rounded-full text-neutral-400 hover:bg-neutral-100'
                onClick={addCompetitor}
              >
                <PlusOutlined />
              </button>
              {competitors.length === 0 && !compDraft ? (
                <span className='text-sm text-neutral-400'>暂无竞品，点击“+”添加</span>
              ) : (
                <div className='flex min-w-0 flex-1 flex-wrap items-center gap-1 py-1'>
                  {competitors.map((c) => (
                    <Tag
                      key={c}
                      closable
                      onClose={() => setCompetitors((prev) => prev.filter((x) => x !== c))}
                    >
                      {c}
                    </Tag>
                  ))}
                  <Input
                    variant='borderless'
                    size='small'
                    className='!w-28'
                    placeholder='回车添加'
                    value={compDraft}
                    onChange={(e) => setCompDraft(e.target.value)}
                    onPressEnter={addCompetitor}
                  />
                </div>
              )}
            </div>
          </div>

          <div className='flex min-h-0 flex-1 gap-3'>
            <div className='flex w-[180px] shrink-0 flex-col rounded-xl border border-neutral-100 bg-[#fafafa] p-2'>
              <div className='mb-2 flex items-center justify-between text-sm font-medium'>
                <span>
                  核心词 <span className='text-red-500'>*</span>
                </span>
                <button
                  type='button'
                  className='text-neutral-400 hover:text-violet-500'
                  onClick={addCore}
                >
                  <PlusOutlined />
                </button>
              </div>
              <Input
                size='small'
                placeholder='回车添加'
                value={coreDraft}
                onChange={(e) => setCoreDraft(e.target.value)}
                onPressEnter={addCore}
              />
              <div className='mt-2 min-h-0 flex-1 space-y-1 overflow-y-auto'>
                {coreWords.map((w) => (
                  <div
                    key={w.text}
                    className={`flex cursor-pointer items-center gap-1 rounded-lg px-1.5 py-1 ${
                      activeCore === w.text ? 'bg-violet-50 ring-1 ring-violet-200' : 'bg-white'
                    }`}
                    onClick={() => {
                      setActiveCore(w.text);
                      setComposing(false);
                      setQuestionDraft('');
                      setEditingKey('');
                      setQuestionTab('query');
                      if (editingCore && editingCore !== w.text) {
                        renameCore(editingCore, coreEditDraft);
                      }
                    }}
                  >
                    <Switch
                      size='small'
                      checked={w.enabled}
                      onClick={(e) => e.stopPropagation()}
                      onChange={(on) => upsertCoreWord(w.text, on)}
                    />
                    {editingCore === w.text ? (
                      <Input
                        size='small'
                        autoFocus
                        variant='borderless'
                        className='min-w-0 flex-1 !px-0 text-xs'
                        value={coreEditDraft}
                        onClick={(e) => e.stopPropagation()}
                        onChange={(e) => setCoreEditDraft(e.target.value)}
                        onPressEnter={() => renameCore(w.text, coreEditDraft)}
                        onBlur={() => renameCore(w.text, coreEditDraft)}
                      />
                    ) : (
                      <span
                        className='min-w-0 flex-1 truncate text-xs'
                        onDoubleClick={(e) => {
                          e.stopPropagation();
                          setEditingCore(w.text);
                          setCoreEditDraft(w.text);
                        }}
                      >
                        {w.text}
                      </span>
                    )}
                    <EditOutlined
                      className='shrink-0 text-[11px] text-neutral-400 hover:text-violet-500'
                      onClick={(e) => {
                        e.stopPropagation();
                        setActiveCore(w.text);
                        setEditingCore(w.text);
                        setCoreEditDraft(w.text);
                      }}
                    />
                    <DeleteOutlined
                      className='shrink-0 text-[11px] text-neutral-400 hover:text-red-500'
                      onClick={(e) => {
                        e.stopPropagation();
                        setCoreWords((prev) => prev.filter((x) => x.text !== w.text));
                        setQuestions((prev) => prev.filter((q) => q.coreWord !== w.text));
                        if (editingCore === w.text) {
                          setEditingCore('');
                          setCoreEditDraft('');
                        }
                        if (activeCore === w.text) {
                          const rest = coreWords.filter((x) => x.text !== w.text);
                          setActiveCore(rest[0]?.text || '');
                        }
                      }}
                    />
                  </div>
                ))}
              </div>
            </div>

            <div className='flex min-w-0 flex-1 flex-col'>
              <div className='mb-2 flex items-center justify-between'>
                <div className='text-sm font-medium'>
                  监控问题 <span className='text-red-500'>*</span>
                  <span className='ml-2 text-xs font-normal text-neutral-400'>每个问题最多 500 个字符</span>
                </div>
                <div className='flex gap-3 text-xs text-violet-500'>
                  <button
                    type='button'
                    onClick={() => void copyQuestions()}
                  >
                    <CopyOutlined /> 复制问题
                  </button>
                  <button
                    type='button'
                    onClick={() => setQuestionTab('import')}
                  >
                    <UploadOutlined /> 导入问题
                  </button>
                  <button
                    type='button'
                    onClick={() => setQuestionTab('recommend')}
                  >
                    推荐问题
                  </button>
                </div>
              </div>
              <div className='flex min-h-0 flex-1 flex-col rounded-xl border border-neutral-200 bg-white'>
                {questionTab === 'import' ? (
                  <div className='p-3'>
                    <Input.TextArea
                      rows={10}
                      placeholder='每行一个问题，最多 500 个字符'
                      value={importDraft}
                      onChange={(e) => setImportDraft(e.target.value)}
                    />
                    <Button
                      type='primary'
                      className='mt-2 !bg-violet-600'
                      onClick={importQuestions}
                    >
                      导入
                    </Button>
                  </div>
                ) : questionTab === 'recommend' ? (
                  <div className='flex flex-1 flex-col items-center justify-center text-neutral-400'>
                    <InboxOutlined className='mb-2 text-3xl' />
                    推荐问题后续接入
                  </div>
                ) : !activeCore ? (
                  <div className='flex flex-1 flex-col items-center justify-center text-neutral-400'>
                    <div className='text-sm'>请先在左侧添加并选中核心词</div>
                  </div>
                ) : visibleQuestions.length === 0 && !composing ? (
                  <div className='flex flex-1 flex-col items-center justify-center text-neutral-400'>
                    <div className='mb-2 flex h-10 w-10 items-center justify-center rounded-full bg-violet-50 text-violet-400'>
                      ?
                    </div>
                    <div className='mb-1 text-sm font-medium text-neutral-700'>添加第一个问题</div>
                    <div className='mb-3 text-xs'>支持逐条添加，可粘贴多行文本批量导入</div>
                    <Button
                      type='primary'
                      className='!bg-violet-600'
                      icon={<PlusOutlined />}
                      onClick={() => setComposing(true)}
                    >
                      添加问题
                    </Button>
                  </div>
                ) : (
                  <div className='flex min-h-0 flex-1 flex-col px-3 py-2'>
                    <div className='min-h-0 flex-1 space-y-1 overflow-y-auto'>
                      {visibleQuestions.map((q) =>
                        editingKey === `${q.coreWord}::${q.text}` ? (
                          <div
                            key={`${q.coreWord}::${q.text}`}
                            className='flex items-center gap-2 rounded-lg border border-neutral-200 px-2 py-1.5'
                          >
                            <Switch
                              size='small'
                              checked={q.enabled}
                              onChange={(on) =>
                                setQuestions((prev) =>
                                  prev.map((x) =>
                                    x.coreWord === q.coreWord && x.text === q.text ? { ...x, enabled: on } : x,
                                  ),
                                )
                              }
                            />
                            <Input
                              autoFocus
                              maxLength={500}
                              value={questionDraft}
                              onChange={(e) => setQuestionDraft(e.target.value.slice(0, 500))}
                              onPressEnter={() => {
                                const next = questionDraft.trim();
                                if (!next) {
                                  return;
                                }
                                setQuestions((prev) =>
                                  prev.map((x) =>
                                    x.coreWord === q.coreWord && x.text === q.text ? { ...x, text: next } : x,
                                  ),
                                );
                                setEditingKey('');
                                setQuestionDraft('');
                              }}
                            />
                            <span className='shrink-0 text-[11px] text-neutral-400'>{questionDraft.length}/500</span>
                            <CloseOutlined
                              className='cursor-pointer text-neutral-400'
                              onClick={() => {
                                setEditingKey('');
                                setQuestionDraft('');
                              }}
                            />
                          </div>
                        ) : (
                          <div
                            key={`${q.coreWord}::${q.text}`}
                            className='flex items-center gap-2 rounded-lg px-1 py-1.5 hover:bg-[#fafafa]'
                          >
                            <Switch
                              size='small'
                              checked={q.enabled}
                              onChange={(on) =>
                                setQuestions((prev) =>
                                  prev.map((x) =>
                                    x.coreWord === q.coreWord && x.text === q.text ? { ...x, enabled: on } : x,
                                  ),
                                )
                              }
                            />
                            <span className='min-w-0 flex-1 truncate text-sm'>{q.text}</span>
                            <EditOutlined
                              className='cursor-pointer text-neutral-400 hover:text-violet-500'
                              onClick={() => {
                                setEditingKey(`${q.coreWord}::${q.text}`);
                                setQuestionDraft(q.text);
                                setComposing(false);
                              }}
                            />
                            <DeleteOutlined
                              className='cursor-pointer text-neutral-400 hover:text-red-500'
                              onClick={() =>
                                setQuestions((prev) =>
                                  prev.filter((x) => !(x.coreWord === q.coreWord && x.text === q.text)),
                                )
                              }
                            />
                          </div>
                        ),
                      )}
                      {composing && (
                        <div className='flex items-center gap-2 rounded-lg border border-neutral-200 px-2 py-1.5'>
                          <Switch
                            size='small'
                            disabled
                          />
                          <Input
                            autoFocus
                            variant='borderless'
                            placeholder='请输入问题，支持粘贴多行文本'
                            maxLength={500}
                            value={questionDraft}
                            onChange={(e) => setQuestionDraft(e.target.value.slice(0, 500))}
                            onPaste={(e) => {
                              const text = e.clipboardData.getData('text');
                              if (text.includes('\n')) {
                                e.preventDefault();
                                addQuestionLines(text);
                                setQuestionDraft('');
                                setComposing(false);
                              }
                            }}
                            onPressEnter={commitDraft}
                          />
                          <span className='shrink-0 text-[11px] text-neutral-400'>{questionDraft.length}/500</span>
                          <CloseOutlined
                            className='cursor-pointer text-neutral-400'
                            onClick={() => {
                              setComposing(false);
                              setQuestionDraft('');
                            }}
                          />
                        </div>
                      )}
                      {!composing && (
                        <button
                          type='button'
                          className='mt-1 flex items-center gap-1 px-1 py-1 text-sm text-neutral-400 hover:text-violet-500'
                          onClick={() => {
                            setComposing(true);
                            setQuestionDraft('');
                            setEditingKey('');
                          }}
                        >
                          <PlusOutlined /> 添加问题
                        </button>
                      )}
                    </div>
                  </div>
                )}
              </div>
            </div>
          </div>
        </div>

        <div className='flex w-[52%] shrink-0 flex-col overflow-y-auto'>
          <div className='mb-2 flex items-center justify-between gap-3'>
            <div className='text-sm font-medium'>
              模型选择 <span className='text-red-500'>*</span>
            </div>
            <div className='flex rounded-full bg-neutral-100 p-0.5 text-xs'>
              {GEO_AUTO_SCREENSHOT_MODES.map((item) => (
                <button
                  key={item.value}
                  type='button'
                  className={`rounded-full px-2.5 py-1 ${
                    screenshotMode === item.value ? 'bg-violet-600 text-white' : 'text-neutral-500'
                  }`}
                  onClick={() => setScreenshotMode(item.value)}
                >
                  {item.label}
                </button>
              ))}
            </div>
          </div>

          <div className='mb-2 flex items-center justify-between'>
            <div className='text-sm text-neutral-600'>
              国内模型 <span className='text-neutral-400'>{domestic.length}项</span>
            </div>
            <div className='flex items-center gap-4 text-sm text-neutral-600'>
              <Checkbox
                checked={allModelsChecked}
                onChange={(e) => {
                  if (e.target.checked) {
                    selectGroup(domestic);
                  } else {
                    setSelectedPlatforms([]);
                  }
                }}
              >
                全部模型
              </Checkbox>
              <Checkbox
                checked={allSelectedDeepThink}
                indeterminate={deepThinkTargets.some((name) => deepThinkModels.includes(name)) && !allSelectedDeepThink}
                disabled={!thinkControlNames.length}
                onChange={(e) => setAllDeepThink(e.target.checked)}
              >
                深度思考
              </Checkbox>
            </div>
          </div>

          <div className='grid grid-cols-2 gap-2 xl:grid-cols-4'>{domestic.map(renderModelCard)}</div>

          <div className='mt-4 rounded-xl border border-neutral-200 p-3'>
            <div className='mb-3 flex items-center justify-between text-sm font-medium'>
              监控频次
              <span className='flex items-center gap-2 text-xs font-normal text-neutral-500'>
                <Switch
                  size='small'
                  checked={freqOn}
                  onChange={setFreqOn}
                />
                {freqOn ? '开启' : '暂停'}
              </span>
            </div>
            <div className='grid grid-cols-2 gap-3'>
              <div>
                <div className='mb-1 text-xs text-neutral-500'>监控周期</div>
                <Select
                  className='w-full'
                  value={freq}
                  options={[
                    { label: '每日', value: '每日' },
                    { label: '每周', value: '每周' },
                  ]}
                  onChange={setFreq}
                />
              </div>
              <div>
                <div className='mb-1 text-xs text-neutral-500'>提问次数</div>
                <InputNumber
                  className='w-full'
                  min={1}
                  max={10}
                  value={askCount}
                  onChange={(v) => setAskCount(v || 1)}
                />
              </div>
            </div>
          </div>

          <div className='mt-3 grid grid-cols-2 gap-3'>
            <div>
              <div className='mb-1 text-sm'>提问位置</div>
              <Select
                className='w-full'
                value={probeRegion}
                options={GEO_AUTO_PROBE_REGIONS.map((r) => ({ label: r, value: r }))}
                onChange={setProbeRegion}
              />
            </div>
            <div>
              <div className='mb-1 text-sm'>舆情分析</div>
              <Select
                className='w-full'
                value={sentimentOn ? 'on' : 'off'}
                options={[
                  { label: '开启分析', value: 'on' },
                  { label: '关闭分析', value: 'off' },
                ]}
                onChange={(v) => setSentimentOn(v === 'on')}
              />
            </div>
          </div>
        </div>
      </div>
    </Modal>
  );
}
