import { useRef, useState } from 'react';
import type { ActionType, ProColumnType } from '@ant-design/pro-components';
import { App, Form, Input, InputNumber, Modal, Select, Switch, Tag } from 'antd';
import BaseProTable from '@/components/BaseProTable';
import ActionButtons from '@/components/Buttons/ActionButtons';
import PermissionButton from '@/components/Buttons/PermissionButton';
import {
  addLinkMonitorApi,
  clearLinkMonitorProbeCookieApi,
  deleteLinkMonitorApi,
  getLinkMonitorProbeCookieApi,
  listLinkMonitorAlertsApi,
  listLinkMonitorChecksApi,
  listLinkMonitorsApi,
  probeLinkMonitorApi,
  saveLinkMonitorProbeCookieApi,
  updateLinkMonitorApi,
  type LinkMonitor,
} from '@/api/robot';

function mapRow(r: Record<string, unknown>): LinkMonitor {
  return {
    id: Number(r.id),
    url: String(r.url ?? ''),
    name: r.name == null ? undefined : String(r.name),
    remark: r.remark == null ? undefined : String(r.remark),
    scopeType: r.scope_type == null ? undefined : String(r.scope_type),
    source: r.source == null ? undefined : String(r.source),
    openConversationId: r.open_conversation_id == null ? undefined : String(r.open_conversation_id),
    conversationType: r.conversation_type == null ? undefined : String(r.conversation_type),
    groupTitle: r.group_title == null ? undefined : String(r.group_title),
    status: String(r.status ?? ''),
    intervalSec: r.interval_sec == null ? undefined : Number(r.interval_sec),
    lastCheckAt: r.last_check_at == null ? undefined : String(r.last_check_at),
    lastOk: r.last_ok == null ? undefined : Number(r.last_ok),
    lastStatusCode: r.last_status_code == null ? undefined : Number(r.last_status_code),
    lastError: r.last_error == null ? undefined : String(r.last_error),
    lastAlertAt: r.last_alert_at == null ? undefined : String(r.last_alert_at),
    createBy: r.create_by == null ? undefined : String(r.create_by),
    createTime: r.create_time == null ? undefined : String(r.create_time),
  };
}

function alertGroupLabel(r: LinkMonitor) {
  if (r.conversationType === '2' && r.openConversationId) {
    return r.groupTitle || r.openConversationId;
  }
  if (r.source === 'admin' || r.conversationType === '1') {
    return '单聊/后台（通知添加人）';
  }
  return '-';
}

export default function LinkMonitorPage() {
  const { message, modal } = App.useApp();
  const actionRef = useRef<ActionType>(null);
  const [addOpen, setAddOpen] = useState(false);
  const [editOpen, setEditOpen] = useState(false);
  const [cookieOpen, setCookieOpen] = useState(false);
  const [cookieHint, setCookieHint] = useState('');
  const [editing, setEditing] = useState<LinkMonitor | null>(null);
  const [form] = Form.useForm();
  const [editForm] = Form.useForm();
  const [cookieForm] = Form.useForm();

  const openCookieModal = async () => {
    const cfg = await getLinkMonitorProbeCookieApi();
    cookieForm.setFieldsValue({
      cookieHeader: cfg.cookieHeader || '',
      hostSuffixes: cfg.hostSuffixes || 'xiaohongshu.com,xhslink.com',
      enabled: cfg.enabled !== false,
      remark: cfg.remark || '',
    });
    const parts: string[] = [];
    if (cfg.configured) {
      parts.push(`已配置${cfg.cookiePreview ? `：${cfg.cookiePreview}` : ''}`);
      if (cfg.updateTime) parts.push(`更新于 ${cfg.updateTime}`);
      if (cfg.updateBy) parts.push(`by ${cfg.updateBy}`);
    } else {
      parts.push('尚未配置；无 Cookie 时跳登录页不判失效');
    }
    setCookieHint(parts.filter(Boolean).join(' · '));
    setCookieOpen(true);
  };

  const columns: ProColumnType<LinkMonitor>[] = [
    {
      title: '操作',
      valueType: 'option',
      width: 260,
      render: (_, row) => (
        <ActionButtons
          items={[
            {
              key: 'probe',
              label: '探测',
              perm: 'robot:link-monitor:probe',
              onClick: async () => {
                const res = await probeLinkMonitorApi(row.id);
                if (res?.ok) {
                  message.success('探测正常');
                } else {
                  const tip = res?.error ? `：${res.error}` : '';
                  message.warning(res?.alerted ? `探测异常已告警${tip}` : `探测异常${tip || '，请查看探测日志'}`);
                }
                actionRef.current?.reload();
              },
            },
            {
              key: 'edit',
              label: '编辑',
              perm: 'robot:link-monitor:edit',
              onClick: () => {
                setEditing(row);
                editForm.setFieldsValue({
                  name: row.name,
                  remark: row.remark,
                  intervalSec: row.intervalSec ?? 86400,
                  status: row.status,
                });
                setEditOpen(true);
              },
            },
            {
              key: 'log',
              label: '日志',
              onClick: async () => {
                const [checks, alerts] = await Promise.all([
                  listLinkMonitorChecksApi(row.id),
                  listLinkMonitorAlertsApi(row.id),
                ]);
                modal.info({
                  title: `监控 #${row.id} 日志`,
                  width: 720,
                  content: (
                    <div className='max-h-96 space-y-3 overflow-auto text-xs'>
                      <div className='font-medium'>探测</div>
                      <pre className='whitespace-pre-wrap'>{JSON.stringify(checks, null, 2)}</pre>
                      <div className='font-medium'>告警</div>
                      <pre className='whitespace-pre-wrap'>{JSON.stringify(alerts, null, 2)}</pre>
                    </div>
                  ),
                });
              },
            },
            {
              key: 'del',
              label: '删除',
              perm: 'robot:link-monitor:delete',
              onClick: () => {
                modal.confirm({
                  title: '删除该监控？',
                  onOk: async () => {
                    await deleteLinkMonitorApi(row.id);
                    message.success('已删除');
                    actionRef.current?.reload();
                  },
                });
              },
            },
          ]}
        />
      ),
    },
    { title: 'URL', dataIndex: 'url', ellipsis: true, copyable: true },
    { title: '名称', dataIndex: 'name', width: 120, search: false },
    {
      title: '状态',
      dataIndex: 'status',
      width: 100,
      valueType: 'select',
      valueEnum: { ACTIVE: '监控中', PAUSED: '暂停', DELETED: '已删' },
      render: (_, r) => <Tag color={r.status === 'ACTIVE' ? 'green' : 'default'}>{r.status}</Tag>,
    },
    {
      title: '间隔(秒)',
      dataIndex: 'intervalSec',
      width: 100,
      search: false,
      render: (_, r) => r.intervalSec ?? 86400,
    },
    {
      title: '最近探测',
      dataIndex: 'lastCheckAt',
      width: 170,
      search: false,
      render: (_, r) => (r.lastCheckAt ? `${r.lastOk === 1 ? 'OK' : 'FAIL'} ${r.lastCheckAt}` : '-'),
    },
    {
      title: '告警推送群',
      dataIndex: 'groupTitle',
      width: 160,
      search: false,
      ellipsis: true,
      render: (_, r) => alertGroupLabel(r),
    },
    { title: '来源', dataIndex: 'source', width: 80, search: false },
    { title: '创建', dataIndex: 'createBy', width: 100, search: false },
  ];

  return (
    <>
      <BaseProTable<LinkMonitor>
        actionRef={actionRef}
        rowKey='id'
        columns={columns}
        headerTitle='链接监控（可选 Cookie 登录态探测；群内添加的告警推回该群；每天 09:30 探测）'
        toolBarRender={() => [
          <PermissionButton
            key='cookie'
            perm='robot:link-monitor:edit'
            onClick={() => {
              void openCookieModal();
            }}
          >
            探测 Cookie
          </PermissionButton>,
          <PermissionButton
            key='add'
            perm='robot:link-monitor:add'
            type='primary'
            onClick={() => {
              form.resetFields();
              setAddOpen(true);
            }}
          >
            新增
          </PermissionButton>,
        ]}
        request={async (params) => {
          const res = await listLinkMonitorsApi({
            pageNum: params.current,
            pageSize: params.pageSize,
            status: params.status ? String(params.status) : undefined,
            keyword: params.url ? String(params.url) : undefined,
          });
          const rows = (res.rows || []).map((r) => mapRow(r as unknown as Record<string, unknown>));
          return { data: rows, success: true, total: res.total || 0 };
        }}
      />
      <Modal
        title='新增监控'
        open={addOpen}
        onCancel={() => setAddOpen(false)}
        onOk={async () => {
          const v = await form.validateFields();
          await addLinkMonitorApi(v);
          message.success('已添加');
          setAddOpen(false);
          actionRef.current?.reload();
        }}
      >
        <Form
          form={form}
          layout='vertical'
        >
          <Form.Item
            name='url'
            label='URL'
            rules={[{ required: true }]}
          >
            <Input placeholder='https://...' />
          </Form.Item>
          <Form.Item
            name='name'
            label='名称'
          >
            <Input />
          </Form.Item>
          <Form.Item
            name='remark'
            label='备注'
          >
            <Input />
          </Form.Item>
        </Form>
      </Modal>
      <Modal
        title='编辑监控'
        open={editOpen}
        onCancel={() => setEditOpen(false)}
        onOk={async () => {
          if (!editing) return;
          const v = await editForm.validateFields();
          await updateLinkMonitorApi(editing.id, v);
          message.success('已保存');
          setEditOpen(false);
          actionRef.current?.reload();
        }}
      >
        <Form
          form={editForm}
          layout='vertical'
        >
          <Form.Item
            name='name'
            label='名称'
          >
            <Input />
          </Form.Item>
          <Form.Item
            name='remark'
            label='备注'
          >
            <Input />
          </Form.Item>
          <Form.Item
            name='intervalSec'
            label='间隔秒（调度已改为每天 09:30，此字段仅兼容保留）'
          >
            <InputNumber
              min={60}
              className='w-full'
            />
          </Form.Item>
          <Form.Item
            name='status'
            label='状态'
          >
            <Select
              options={[
                { value: 'ACTIVE', label: '监控中' },
                { value: 'PAUSED', label: '暂停' },
              ]}
            />
          </Form.Item>
        </Form>
      </Modal>
      <Modal
        title='探测 Cookie（浏览器会话）'
        open={cookieOpen}
        width={640}
        onCancel={() => setCookieOpen(false)}
        footer={(_, { OkBtn, CancelBtn }) => (
          <div className='flex flex-wrap justify-end gap-2'>
            <PermissionButton
              perm='robot:link-monitor:edit'
              danger
              onClick={() => {
                modal.confirm({
                  title: '清空探测 Cookie？',
                  onOk: async () => {
                    await clearLinkMonitorProbeCookieApi();
                    message.success('已清空');
                    setCookieOpen(false);
                  },
                });
              }}
            >
              清空
            </PermissionButton>
            <CancelBtn />
            <OkBtn />
          </div>
        )}
        onOk={async () => {
          const v = await cookieForm.validateFields();
          await saveLinkMonitorProbeCookieApi({
            cookieHeader: v.cookieHeader,
            hostSuffixes: v.hostSuffixes,
            enabled: v.enabled,
            remark: v.remark,
          });
          message.success('已保存');
          setCookieOpen(false);
        }}
      >
        <p className='mb-3 text-xs text-neutral-500'>
          从已登录浏览器复制 Cookie（DevTools → Network 请求头，或 Application → Cookies）。支持 Cookie 原文或
          EditThisCookie JSON。不存账号密码；过期后需重新粘贴。匹配域名的探测会带上该 Cookie，此时仍跳登录页会判异常。
        </p>
        {cookieHint ? <p className='mb-3 text-xs text-neutral-600'>{cookieHint}</p> : null}
        <Form
          form={cookieForm}
          layout='vertical'
        >
          <Form.Item
            name='enabled'
            label='启用'
            valuePropName='checked'
          >
            <Switch />
          </Form.Item>
          <Form.Item
            name='hostSuffixes'
            label='适用域名后缀'
            rules={[{ required: true, message: '请填写域名后缀' }]}
            extra='逗号分隔，如 xiaohongshu.com,xhslink.com'
          >
            <Input placeholder='xiaohongshu.com,xhslink.com' />
          </Form.Item>
          <Form.Item
            name='cookieHeader'
            label='Cookie'
            extra='留空并保存等同清空'
          >
            <Input.TextArea
              rows={6}
              placeholder='a1=...; web_session=... 或 [{"name":"a1","value":"..."}]'
            />
          </Form.Item>
          <Form.Item
            name='remark'
            label='备注'
          >
            <Input placeholder='如：小红书个人号会话，预计 7 天过期' />
          </Form.Item>
        </Form>
      </Modal>
    </>
  );
}
