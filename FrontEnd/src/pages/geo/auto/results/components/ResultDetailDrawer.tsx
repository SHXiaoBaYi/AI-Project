import { useEffect, useMemo, useState } from 'react';
import { App, Button, Drawer, Empty, Image, Input, Space, Tag, Tooltip, Typography, Upload } from 'antd';
import {
  ArrowDownOutlined,
  ArrowUpOutlined,
  BulbOutlined,
  CameraOutlined,
  CopyOutlined,
  LinkOutlined,
  RightOutlined,
  UploadOutlined,
} from '@ant-design/icons';
import { getGeoAutoResultDetailApi, uploadGeoAutoScreenshotApi } from '@/api/geoAuto';
import { resolveUploadUrl } from '@/utils/uploadUrl';
import type { GeoAutoResultDetail } from '@/types/geoAuto';
import { GEO_AUTO_RESULT_STATUS_MAP, GEO_AUTO_SCREENSHOT_TYPE_MAP, geoAutoSentimentColor } from '@/constants/geoAuto';

interface ResultDetailDrawerProps {
  resultId: number | null;
  onClose: () => void;
}

/** 是否是可直接打开的外链（防止 javascript: 等伪协议） */
function isSafeUrl(url?: string) {
  return !!url && /^https?:\/\//i.test(url.trim());
}

/**
 * 监测详情（对标模力指数「问答明细」）：
 * 深度思考标记 / 思考过程 / 真实回答链接 / 整页截图存证 / 引用信源 / 判定指标 / 与上次对比 / 采集诊断。
 */
export default function ResultDetailDrawer({ resultId, onClose }: ResultDetailDrawerProps) {
  const { message } = App.useApp();
  const [detail, setDetail] = useState<GeoAutoResultDetail | null>(null);
  const [loading, setLoading] = useState(false);
  const [uploading, setUploading] = useState(false);

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

  const diagnosticText = useMemo(() => {
    if (!detail?.probeRequest) return '';
    try {
      return JSON.stringify(JSON.parse(detail.probeRequest), null, 2);
    } catch {
      return detail.probeRequest;
    }
  }, [detail?.probeRequest]);

  const copy = (text?: string) => {
    if (!text) return;
    void navigator.clipboard
      .writeText(text)
      .then(() => message.success('已复制'))
      .catch(() => message.error('复制失败，请手动选择文本'));
  };

  const handleUpload = async (file: File) => {
    if (!detail) return;
    setUploading(true);
    try {
      const res = await uploadGeoAutoScreenshotApi(detail.id, file);
      setDetail({ ...detail, screenshotUrl: res.screenshotUrl, screenshotType: 'MANUAL' });
      message.success('真实截图已补录');
    } catch {
      message.error('截图上传失败');
    } finally {
      setUploading(false);
    }
  };

  return (
    <Drawer
      title={detail ? `问答明细 #${detail.id}` : '问答明细'}
      open={!!resultId}
      onClose={onClose}
      width={820}
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
              <Tag color='purple'>{detail.channel === 'BROWSER' ? '网页版采集' : detail.channel || 'API'}</Tag>
              <Tag color={detail.deepThink === 1 ? 'cyan' : 'default'}>
                深度思考 {detail.deepThink === 1 ? '开启' : '关闭'}
              </Tag>
              {detail.recommendStatus && <Tag color='gold'>{detail.recommendStatus}</Tag>}
              {detail.sentiment && <Tag color={geoAutoSentimentColor(detail.sentiment)}>{detail.sentiment}</Tag>}
              {statusMeta && <Tag color={statusMeta.color}>{statusMeta.label}</Tag>}
              <span className='text-xs text-neutral-400'>
                {detail.runDate || ''} · {detail.answerModel || '-'} · 耗时 {detail.latencyMs ?? '-'}ms
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

          {/* 取证一：真实回答链接（模力指数 V1.24 回答链接直达） */}
          <div className='rounded-lg border border-neutral-200 p-3'>
            <div className='mb-2 flex items-center gap-2'>
              <LinkOutlined className='text-blue-500' />
              <Typography.Text strong>回答链接</Typography.Text>
              {detail.probeUrl && (
                <Tag color={detail.probeUrl.startsWith('http') ? 'green' : 'default'}>
                  {detail.probeUrl.includes('/thread/') || detail.probeUrlSource === 'OFFICIAL_SHARE'
                    ? '官方分享链接'
                    : '会话页面地址'}
                </Tag>
              )}
            </div>
            {isSafeUrl(detail.probeUrl) ? (
              <>
                <Space.Compact className='w-full'>
                  <Input
                    readOnly
                    value={detail.probeUrl}
                    size='small'
                  />
                  <Tooltip title='复制链接'>
                    <Button
                      size='small'
                      icon={<CopyOutlined />}
                      onClick={() => copy(detail.probeUrl)}
                    />
                  </Tooltip>
                  <Button
                    size='small'
                    type='primary'
                    href={detail.probeUrl}
                    target='_blank'
                    rel='noreferrer'
                  >
                    打开原始回答
                  </Button>
                </Space.Compact>
                <div className='mt-1.5 text-xs text-neutral-400'>
                  模型官方分享链接由平台生成，可能随平台策略失效；建议同时保留下方截图存证。
                </div>
              </>
            ) : (
              <div className='text-sm text-neutral-400'>
                本次未取到官方分享链接（多为未登录或平台改版），可在「监测任务 → 采集诊断」里核对选择器与登录态。
              </div>
            )}
          </div>

          {/* 取证二：整页截图存证 */}
          <div className='rounded-lg border border-neutral-200 p-3'>
            <div className='mb-2 flex items-center justify-between'>
              <div className='flex items-center gap-2'>
                <CameraOutlined className='text-blue-500' />
                <Typography.Text strong>截图存证</Typography.Text>
                {detail.screenshotType && (
                  <Tag>{GEO_AUTO_SCREENSHOT_TYPE_MAP[detail.screenshotType] || detail.screenshotType}</Tag>
                )}
              </div>
              <Upload
                accept='image/*'
                showUploadList={false}
                beforeUpload={(file) => {
                  void handleUpload(file);
                  return false;
                }}
              >
                <Button
                  size='small'
                  icon={<UploadOutlined />}
                  loading={uploading}
                >
                  补录真实截图
                </Button>
              </Upload>
            </div>
            {detail.screenshotUrl ? (
              <Image
                width='100%'
                src={resolveUploadUrl(detail.screenshotUrl)}
                alt='本次问答整页截图'
                className='rounded-md border border-neutral-100'
              />
            ) : (
              <Empty
                image={Empty.PRESENTED_IMAGE_SIMPLE}
                description='本次未生成整页截图（截图模式禁用 / 品牌未提及 / 平台不支持）'
              />
            )}
          </div>

          {/* 取证三：思考过程 */}
          <details className='rounded-lg border border-neutral-200 px-3 py-2'>
            <summary className='cursor-pointer text-sm'>
              <BulbOutlined className='mr-2 text-amber-500' />
              <Typography.Text strong>思考过程</Typography.Text>
              <span className='ml-2 text-xs text-neutral-400'>
                {detail.reasoningText ? `${detail.reasoningText.length} 字` : '本次未返回'}
              </span>
            </summary>
            <div className='mt-2 max-h-72 overflow-auto rounded-md bg-neutral-50 p-3 text-sm leading-6 whitespace-pre-wrap'>
              {detail.reasoningText ||
                '该模型/平台本次未返回思考过程（可能未开启深度思考，或页面思考面板选择器需调整）。'}
            </div>
          </details>

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
            <div className='flex items-center justify-between'>
              <Typography.Text strong>AI 回答原文</Typography.Text>
              <Button
                type='text'
                size='small'
                icon={<CopyOutlined />}
                onClick={() => copy(detail.answerText)}
              >
                复制
              </Button>
            </div>
            <div className='mt-2 max-h-80 overflow-auto rounded-md bg-neutral-50 p-3 text-sm leading-6 whitespace-pre-wrap'>
              {detail.answerText || '（无回答内容）'}
            </div>
          </div>

          <div>
            <Typography.Text strong>引用信源（{detail.citations?.length || 0}）</Typography.Text>
            {detail.citations && detail.citations.length > 0 ? (
              <div className='mt-2 flex flex-col gap-1'>
                {detail.citations.map((c, i) => (
                  <div
                    key={i}
                    className='rounded-md bg-neutral-50 px-3 py-1.5 text-sm'
                  >
                    <span className='mr-2 text-xs text-neutral-400'>{i + 1}</span>
                    {isSafeUrl(c.url) ? (
                      <a
                        href={c.url}
                        target='_blank'
                        rel='noreferrer'
                        className='font-medium'
                      >
                        {c.title || c.url}
                      </a>
                    ) : (
                      <span className='font-medium'>{c.title || '（无标题）'}</span>
                    )}
                    {c.site && <Tag className='ml-2'>{c.site}</Tag>}
                    {isSafeUrl(c.url) && (
                      <Button
                        type='text'
                        size='small'
                        icon={<CopyOutlined />}
                        onClick={() => copy(c.url)}
                      />
                    )}
                    {c.context && <div className='mt-1 text-xs text-neutral-500'>{c.context}</div>}
                  </div>
                ))}
              </div>
            ) : (
              <div className='mt-1 text-sm text-neutral-400'>本次未提取到引用信源</div>
            )}
          </div>

          <div>
            <Typography.Text strong>已回写日监测</Typography.Text>
            <div className='mt-1 text-sm text-neutral-500'>
              {detail.dailyId
                ? `已回写（日监测记录 #${detail.dailyId}），日报/周报/月报/全年目标看板自动生效`
                : '未回写'}
            </div>
          </div>

          {diagnosticText && (
            <details className='rounded-md bg-neutral-50 px-3 py-2 text-sm'>
              <summary className='cursor-pointer text-neutral-500'>
                浏览器采集诊断（选择器/登录态/耗时，排查用）
              </summary>
              <pre className='mt-2 max-h-60 overflow-auto text-xs whitespace-pre-wrap'>{diagnosticText}</pre>
            </details>
          )}

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
