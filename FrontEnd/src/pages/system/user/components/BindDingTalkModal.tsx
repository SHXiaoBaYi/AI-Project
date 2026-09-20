import { App } from 'antd';
import TableModal from '@/components/TableModal';
import { bindHrDingTalkApi } from '@/api/hr';
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
  return (
    <TableModal
      readonly={false}
      title={user ? `绑定钉钉：${user.nickname || user.username}` : '绑定钉钉'}
      open={open}
      onOpenChange={onOpenChange}
      initialValues={{
        phone: user?.phone,
        dingtalkUserId: user?.dingtalkUserId,
        unionId: user?.dingtalkUnionId,
      }}
      columns={[
        {
          title: '手机号',
          dataIndex: 'phone',
          formItemProps: { extra: '和钉钉通讯录里的手机号一致时，可以自动匹配企业身份' },
        },
        {
          title: '企业用户ID',
          dataIndex: 'dingtalkUserId',
          formItemProps: { extra: '手机号匹配失败时，和企业 unionId 一起手工填写。不是对外的钉钉号' },
        },
        { title: 'unionId', dataIndex: 'unionId' },
      ]}
      onFinish={async (values) => {
        if (!user) return false;
        const form = values as { phone?: string; dingtalkUserId?: string; unionId?: string };
        await bindHrDingTalkApi({
          userId: user.userId,
          phone: form.phone,
          dingtalkUserId: form.dingtalkUserId,
          unionId: form.unionId,
        });
        message.success('已绑定');
        onSuccess();
        return true;
      }}
    />
  );
}
