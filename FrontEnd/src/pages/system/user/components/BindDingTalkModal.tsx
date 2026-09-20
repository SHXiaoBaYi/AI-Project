import { useEffect, useState } from 'react';
import { App, Form, Input, Modal } from 'antd';
import { bindHrDingTalkApi, previewHrDingTalkApi } from '@/api/hr';
import type { UserVO } from '@/types/user';

export default function BindDingTalkModal({
  user,
  open,
  onOpenChange,
  onSuccess,
}: {
  user: UserVO | null;
  open: boolean;
  onOpenChange: (open: boolean) => void;
  onSuccess: () => void;
}) {
  const { message } = App.useApp();
  const [loading, setLoading] = useState(false);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState('');
  const [phone, setPhone] = useState('');
  const [dingtalkUserId, setDingtalkUserId] = useState('');
  const [unionId, setUnionId] = useState('');

  useEffect(() => {
    if (!open || !user) return;
    setPhone(user.phone || '');
    setDingtalkUserId('');
    setUnionId('');
    setError('');
    if (!user.phone) {
      setError('请先在用户资料里填写手机号');
      return;
    }
    let cancelled = false;
    setLoading(true);
    previewHrDingTalkApi(user.userId)
      .then((identity) => {
        if (cancelled) return;
        setPhone(identity.phone);
        setDingtalkUserId(identity.dingtalkUserId);
        setUnionId(identity.unionId);
      })
      .catch((err: Error) => {
        if (!cancelled) setError(err?.message || '获取钉钉身份失败');
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, [open, user]);

  return (
    <Modal
      title={user ? `绑定钉钉：${user.nickname || user.username}` : '绑定钉钉'}
      open={open}
      confirmLoading={saving}
      okButtonProps={{ disabled: loading || !dingtalkUserId || !unionId }}
      onCancel={() => onOpenChange(false)}
      onOk={async () => {
        if (!user) return;
        setSaving(true);
        try {
          await bindHrDingTalkApi(user.userId);
          message.success('已绑定');
          onOpenChange(false);
          onSuccess();
        } finally {
          setSaving(false);
        }
      }}
    >
      <p className='mb-3 text-sm text-neutral-500'>
        按系统用户手机号从钉钉获取，不能手改。手机号变更后会自动重新绑定。
      </p>
      <Form layout='vertical'>
        <Form.Item
          label='手机号'
          extra={error || '使用用户资料中的手机号'}
          validateStatus={error ? 'error' : undefined}
        >
          <Input
            value={phone}
            disabled
          />
        </Form.Item>
        <Form.Item label='企业用户ID'>
          <Input
            value={loading ? '正在从钉钉获取…' : dingtalkUserId}
            disabled
          />
        </Form.Item>
        <Form.Item label='unionId'>
          <Input
            value={loading ? '正在从钉钉获取…' : unionId}
            disabled
          />
        </Form.Item>
      </Form>
    </Modal>
  );
}
