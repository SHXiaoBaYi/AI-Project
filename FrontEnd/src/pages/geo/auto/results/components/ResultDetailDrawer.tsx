import { useEffect, useState } from 'react';
import { Drawer, Tag, Typography } from 'antd';
import { ArrowDownOutlined, ArrowUpOutlined, RightOutlined } from '@ant-design/icons';
import { getGeoAutoResultDetailApi } from '@/api/geoAuto';
import type { GeoAutoResultDetail } from '@/types/geoAuto';
import { GEO_AUTO_RESULT_STATUS_MAP, geoAutoSentimentColor } from '@/constants/geoAuto';

interface ResultDetailDrawerProps {
  resultId: number | null;
  onClose: () => void;
}

/** 监测结果详情：判定指标 + 与上次对比 + 回答原文 + 引用来源 */
export default function ResultDetailDrawer({ resultId, onClose }: ResultDetailDrawerProps) {
  const [detail, setDetail] = useState<GeoAutoResultDetail | null>(null);
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    if (!resultId) {
      setDetail(null);
      return;
    }
    setLoading(true);
    getGeoAutoResultDetailApi(resultId)
      .then(setDetail)
      .finally(() => setLoading(false));
  }, [resultId]);

  const statusMeta = detail ? GEO_AUTO_RESULT_STATUS_MAP[detail.status] : null;

  return (
    <Drawer
      title={detail ? `监测详情 #${detail.id}` : '监测详情'}
      open={!!resultId}
      onClose={onClose}
      width={720}
      loading={loading}
    >
      {detail && (
        <div className='flex flex-col gap-4'>
          <div>
            <Typography.Text
              strong
              className='text-base'
            >
              {detail.questionText}
            </Typography.Text>
            <div className='mt-2 flex flex-wrap items-center gap-2'>
              <Tag color='blue'>{detail.topicName || `话题#${detail.topicId}`}</Tag>
              <Tag color='geekblue'>{detail.platform}</Tag>
              <Tag>{detail.channel === 'API' ? 'API通道' : detail.channel}</Tag>
              {detail.recommendStatus && <Tag color='gold'>{detail.recommendStatus}</Tag>}
              {detail.sentiment && <Tag color={geoAutoSentimentColor(detail.sentiment)}>{detail.sentiment}</Tag>}
              {statusMeta && <Tag color={statusMeta.color}>{statusMeta.label}</Tag>}
              <span className='text-xs text-neutral-400'>
                {detail.runDate || ''} · 模型 {detail.answerModel || '-'} · 耗时 {detail.latencyMs ?? '-'}ms
              </span>
            </div>
          </div>

          <div className='grid grid-cols-4 gap-3'>
            <div className='rounded-lg bg-neutral-50 px-3 py-2 text-center'>
              <div className='text-xs text-neutral-500'>是否提及</div>
              <div className={detail.mentioned === 1 ? 'text-green-600' : 'text-neutral-500'}>
                {detail.mentioned === 1 ? '提及' : '未提及'}
              </div>
            </div>
            <div className='rounded-lg bg-neutral-50 px-3 py-2 text-center'>
              <div className='text-xs text-neutral-500'>排名</div>
              <div>{detail.rankNo != null ? `第 ${detail.rankNo} 位` : '未上榜'}</div>
            </div>
            <div className='rounded-lg bg-neutral-50 px-3 py-2 text-center'>
              <div className='text-xs text-neutral-500'>首次出现</div>
              <div>{detail.mentionPosition != null ? `第 ${detail.mentionPosition} 段` : '-'}</div>
            </div>
            <div className='rounded-lg bg-neutral-50 px-3 py-2 text-center'>
              <div className='text-xs text-neutral-500'>竞品</div>
              <div
                className='truncate'
                title={detail.competitors || ''}
              >
                {detail.competitors || '-'}
              </div>
            </div>
          </div>

          {detail.diffs && detail.diffs.length > 0 && (
            <div>
              <Typography.Text strong>与上次监测对比</Typography.Text>
              <div className='mt-2 flex flex-col gap-1'>
                {detail.diffs.map((d, i) => (
                  <div
                    key={i}
                    className='flex items-center gap-2 rounded-md bg-neutral-50 px-3 py-1.5 text-sm'
                  >
                    <span className='w-20 shrink-0 text-neutral-500'>{d.label}</span>
                    <span>{d.before}</span>
                    <RightOutlined className='text-xs text-neutral-400' />
                    <span
                      className={d.direction === 'up' ? 'text-green-600' : d.direction === 'down' ? 'text-red-500' : ''}
                    >
                      {d.after}
                    </span>
                    {d.direction === 'up' && <ArrowUpOutlined className='text-xs text-green-600' />}
                    {d.direction === 'down' && <ArrowDownOutlined className='text-xs text-red-500' />}
                  </div>
                ))}
              </div>
            </div>
          )}

          {detail.negativeContent && (
            <div className='rounded-md border border-red-200 bg-red-50 px-3 py-2 text-sm text-red-600'>
              负面/错误内容：{detail.negativeContent}
            </div>
          )}

          <div>
            <Typography.Text strong>AI 回答原文</Typography.Text>
            <div className='mt-2 max-h-80 overflow-auto rounded-md bg-neutral-50 p-3 text-sm leading-6 whitespace-pre-wrap'>
              {detail.answerText || '（无回答内容）'}
            </div>
          </div>

          {detail.citations && detail.citations.length > 0 && (
            <div>
              <Typography.Text strong>引用来源（{detail.citations.length}）</Typography.Text>
              <div className='mt-2 flex flex-col gap-1'>
                {detail.citations.map((c, i) => (
                  <div
                    key={i}
                    className='rounded-md bg-neutral-50 px-3 py-1.5 text-sm'
                  >
                    <span className='font-medium'>{c.title || '（无标题）'}</span>
                    {c.site && <Tag className='ml-2'>{c.site}</Tag>}
                    {c.context && <div className='mt-1 text-xs text-neutral-500'>{c.context}</div>}
                  </div>
                ))}
              </div>
            </div>
          )}

          <div>
            <Typography.Text strong>已回写日监测</Typography.Text>
            <div className='mt-1 text-sm text-neutral-500'>
              {detail.dailyId
                ? `已回写（日监测记录 #${detail.dailyId}），日报/周报/月报/全年目标看板自动生效`
                : '未回写'}
            </div>
          </div>

          {detail.judgeRaw && (
            <details className='rounded-md bg-neutral-50 px-3 py-2 text-sm'>
              <summary className='cursor-pointer text-neutral-500'>裁判模型原始输出（复核用）</summary>
              <pre className='mt-2 max-h-60 overflow-auto text-xs whitespace-pre-wrap'>{detail.judgeRaw}</pre>
            </details>
          )}
        </div>
      )}
    </Drawer>
  );
}
