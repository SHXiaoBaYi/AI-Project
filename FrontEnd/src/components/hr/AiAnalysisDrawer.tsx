import { useEffect, useState } from 'react';
import { App, Button, Drawer, Empty, Select, Spin, Tag } from 'antd';
import { generateHrApplicationAiApi, getHrAiProvidersApi, listHrApplicationAiApi } from '@/api/hr';

interface Analysis {
  id: number;
  providerName?: string;
  modelName?: string;
  score?: number;
  prosCons?: string;
  interviewAdvice?: string;
  createTime?: string;
}

export function AiAnalysisDrawer({
  applicationId,
  name,
  open,
  onClose,
}: {
  applicationId: number;
  name: string;
  open: boolean;
  onClose: () => void;
}) {
  const { message } = App.useApp();
  const [providers, setProviders] = useState<{ value: string; label: string }[]>([]);
  const [provider, setProvider] = useState<string>();
  const [rows, setRows] = useState<Analysis[]>([]);
  const [loading, setLoading] = useState(false);
  const [generating, setGenerating] = useState(false);

  useEffect(() => {
    if (!open) return;
    setLoading(true);
    Promise.all([getHrAiProvidersApi(), listHrApplicationAiApi(applicationId)])
      .then(([options, list]) => {
        const next = options.map((item) => ({
          value: item.provider,
          label: item.model ? `${item.label} · ${item.model}` : item.label,
        }));
        setProviders(next);
        setProvider((current) => current || next[0]?.value);
        setRows(list);
      })
      .catch(() => {
        setProviders([]);
        setRows([]);
      })
      .finally(() => setLoading(false));
  }, [applicationId, open]);

  const generate = async () => {
    if (!provider) {
      message.warning('请先选择 AI 模型');
      return;
    }
    setGenerating(true);
    try {
      const created = await generateHrApplicationAiApi({ applicationId, provider });
      setRows((current) => [created, ...current.filter((item) => item.id !== created.id)]);
      message.success('已生成一份新的分析');
    } finally {
      setGenerating(false);
    }
  };

  return (
    <Drawer
      title={`${name} · AI分析`}
      open={open}
      onClose={onClose}
      size='large'
      className='[&_.ant-drawer-content-wrapper]:!w-[50vw]'
      styles={{ wrapper: { width: '50vw' } }}
    >
      <div className='mb-4 flex items-center gap-2'>
        <Select
          className='min-w-0 flex-1'
          placeholder={providers.length ? '选择 AI 模型' : '还没有可用的 AI 模型'}
          options={providers}
          value={provider}
          onChange={setProvider}
          disabled={!providers.length || generating}
        />
        <Button
          type='primary'
          loading={generating}
          disabled={!providers.length}
          onClick={generate}
        >
          生成
        </Button>
      </div>
      {!providers.length && !loading ? (
        <p className='mb-4 text-sm text-black/45'>
          请先到「系统管理 → AI模型配置」填写并启用模型。每次生成都会新存一份，不会覆盖以前的。
        </p>
      ) : (
        <p className='mb-4 text-sm text-black/45'>
          按这个候选人对应的招聘需求打分。可以换模型再生成，历史记录都会保留。
        </p>
      )}
      <Spin spinning={loading || generating}>
        {rows.length === 0 && !loading ? (
          <Empty description='还没有分析' />
        ) : (
          <div className='flex flex-col gap-3'>
            {rows.map((item) => (
              <article
                key={item.id}
                className='rounded border border-black/10 p-3'
              >
                <div className='mb-2 flex items-center justify-between gap-2'>
                  <span className='text-sm text-black/65'>
                    {item.providerName || 'AI'}
                    {item.modelName ? ` · ${item.modelName}` : ''}
                  </span>
                  <span className='text-xs text-black/45'>{item.createTime?.replace('T', ' ').slice(0, 19)}</span>
                </div>
                <Tag color='blue'>{item.score ?? '—'} 分</Tag>
                <p className='mt-2 text-sm whitespace-pre-wrap'>{item.prosCons}</p>
                <p className='mt-2 text-sm whitespace-pre-wrap text-black/75'>{item.interviewAdvice}</p>
              </article>
            ))}
          </div>
        )}
      </Spin>
    </Drawer>
  );
}
