import { useCallback, useEffect, useRef, useState, type MouseEvent } from 'react';
import { App, Button, Input, Modal } from 'antd';
import {
  cancelGeoBrowserBoothApi,
  finishGeoBrowserBoothApi,
  inputGeoBrowserBoothApi,
  snapshotGeoBrowserBoothApi,
  startGeoBrowserBoothApi,
} from '@/api/geoAuto';
import type { GeoBrowserLoginBoothVO } from '@/types/geoAuto';

type Props = {
  accountId: number | null;
  accountLabel?: string;
  platform?: string;
  onClose: (saved: boolean) => void;
};

/**
 * 在服务器浏览器里完成扫码或短信。画面通过已登录的后台接口返回，不暴露 Cookie。
 */
export default function BrowserLoginBooth({ accountId, accountLabel, platform, onClose }: Props) {
  const { message } = App.useApp();
  const [shot, setShot] = useState<GeoBrowserLoginBoothVO | null>(null);
  const [text, setText] = useState('');
  const [busy, setBusy] = useState(false);
  const messageRef = useRef(message);
  messageRef.current = message;

  const apply = useCallback((next: GeoBrowserLoginBoothVO) => {
    setShot((prev) => ({
      ...prev,
      ...next,
      imageBase64: next.imageBase64 || prev?.imageBase64,
    }));
  }, []);

  useEffect(() => {
    if (accountId == null) {
      return;
    }
    const sessionAlive = { current: true };
    setShot(null);
    setText('');
    void startGeoBrowserBoothApi(accountId)
      .then((res) => {
        if (sessionAlive.current) {
          apply(res);
        }
      })
      .catch(() => {
        if (sessionAlive.current) {
          messageRef.current.error('打不开服务器登录窗口');
        }
      });
    const timer = window.setInterval(() => {
      if (!sessionAlive.current) {
        return;
      }
      void snapshotGeoBrowserBoothApi(accountId)
        .then((res) => {
          if (sessionAlive.current && res.phase !== 'CLOSED') {
            apply(res);
          }
        })
        .catch(() => {
          // 窗口还在打开或已被关掉时，下一轮再试
        });
    }, 1500);
    return () => {
      sessionAlive.current = false;
      window.clearInterval(timer);
      void cancelGeoBrowserBoothApi(accountId).catch(() => {
        // 窗口可能已经保存并关闭
      });
    };
  }, [accountId, apply]);

  const send = async (action: 'type' | 'key', extra: { text?: string; key?: string }) => {
    if (accountId == null) {
      return;
    }
    setBusy(true);
    try {
      const res = await inputGeoBrowserBoothApi({ accountId, action, ...extra });
      apply(res);
      if (action === 'type') {
        setText('');
      }
    } catch {
      message.error('输入没有送到服务器浏览器');
    } finally {
      setBusy(false);
    }
  };

  const onImageClick = async (event: MouseEvent<HTMLImageElement>) => {
    if (accountId == null || !shot?.viewportWidth || !shot.viewportHeight) {
      return;
    }
    const rect = event.currentTarget.getBoundingClientRect();
    const x = ((event.clientX - rect.left) / rect.width) * shot.viewportWidth;
    const y = ((event.clientY - rect.top) / rect.height) * shot.viewportHeight;
    setBusy(true);
    try {
      const res = await inputGeoBrowserBoothApi({ accountId, action: 'click', x, y });
      apply(res);
    } catch {
      message.error('点击没有送到服务器浏览器');
    } finally {
      setBusy(false);
    }
  };

  const finish = async () => {
    if (accountId == null) {
      return;
    }
    setBusy(true);
    try {
      const res = await finishGeoBrowserBoothApi(accountId);
      apply(res);
      if (res.phase === 'SAVED') {
        message.success(res.message || '登录态已保存');
        onClose(true);
        return;
      }
      message.warning(res.message || '页面仍要求登录，未保存');
    } catch {
      message.error('保存登录态失败');
    } finally {
      setBusy(false);
    }
  };

  const cancel = async () => {
    if (accountId != null) {
      try {
        await cancelGeoBrowserBoothApi(accountId);
      } catch {
        // 窗口可能已经关掉
      }
    }
    onClose(false);
  };

  return (
    <Modal
      title={`在服务器上登录${platform ? ` · ${platform}` : ''}${accountLabel ? ` · ${accountLabel}` : ''}`}
      open={accountId != null}
      width={920}
      destroyOnHidden
      maskClosable={false}
      onCancel={() => void cancel()}
      footer={[
        <Button
          key='backspace'
          disabled={busy || accountId == null}
          onClick={() => void send('key', { key: 'Backspace' })}
        >
          退格
        </Button>,
        <Button
          key='enter'
          disabled={busy || accountId == null}
          onClick={() => void send('key', { key: 'Enter' })}
        >
          回车
        </Button>,
        <Button
          key='cancel'
          onClick={() => void cancel()}
        >
          取消
        </Button>,
        <Button
          key='save'
          type='primary'
          loading={busy}
          onClick={() => void finish()}
        >
          完成并保存
        </Button>,
      ]}
    >
      <div className='mb-3 text-sm text-neutral-500'>
        {shot?.message || '正在打开服务器浏览器…'}
        {shot?.loginDetected ? ' 当前画面未发现登录按钮。' : ''}
      </div>
      <div className='overflow-hidden rounded-lg border border-neutral-200 bg-neutral-100'>
        {shot?.imageBase64 ? (
          <img
            alt='服务器浏览器'
            src={`data:image/jpeg;base64,${shot.imageBase64}`}
            className='block w-full cursor-crosshair'
            onClick={(event) => void onImageClick(event)}
          />
        ) : (
          <div className='flex h-80 items-center justify-center text-neutral-400'>等待画面</div>
        )}
      </div>
      <div className='mt-3 flex gap-2'>
        <Input
          value={text}
          maxLength={32}
          placeholder='手机号或验证码，点输入后送到服务器浏览器'
          onChange={(event) => setText(event.target.value)}
          onPressEnter={() => {
            if (text.trim()) {
              void send('type', { text });
            }
          }}
        />
        <Button
          disabled={busy || !text.trim()}
          onClick={() => void send('type', { text })}
        >
          输入
        </Button>
      </div>
    </Modal>
  );
}
