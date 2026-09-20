import { memo, useEffect, useMemo, useRef, useState } from 'react';
import type { ActionType, ProColumnType } from '@ant-design/pro-components';
import { App, DatePicker, Form, Input, Modal, Select, Tag } from 'antd';
import dayjs from 'dayjs';
import BaseProTable from '@/components/BaseProTable';
import BaseModalForm from '@/components/BaseModalForm/index';
import PermissionButton from '@/components/Buttons/PermissionButton';
import ActionButtons from '@/components/Buttons/ActionButtons';
import { ExternalLinkText } from '@/components/ExternalLinkDrawer';
import { STATUS_COLOR } from '@/components/geo/content-placement/constants';
import {
  createGeoContentPlacementItemApi,
  deleteGeoContentPlacementItemApi,
  deleteGeoContentPlacementItemBatchApi,
  getGeoContentPlacementArticleListApi,
  getGeoOwnerOptionsApi,
  getGeoPlatformOptionsApi,
  getGeoTargetQuestionsApi,
  getGeoTopicOptionsApi,
  updateGeoContentPlacementItemApi,
} from '@/api/geo';
import type {
  GeoContentPlacementArticle,
  GeoOwnerOption,
  GeoPlatform,
  GeoTargetQuestionOption,
  GeoTopic,
} from '@/types/geo';
import { GEO_CONTENT_FORMS, GEO_CONTENT_PUBLISH_STATUS, GEO_PLATFORM_TYPE_CONTENT } from '@/constants/geo';
import { createTimeDisplayColumn, createTimeRangeColumn } from '@/components/table/createTimeColumns';

const ArticlePage = memo(function ArticlePage() {
  const { message } = App.useApp();
  const actionRef = useRef<ActionType>(null);
  const currentPageKeysRef = useRef<Set<number>>(new Set());
  const [selectedRowKeys, setSelectedRowKeys] = useState<React.Key[]>([]);
  const [form] = Form.useForm();
  const [open, setOpen] = useState(false);
  const [editing, setEditing] = useState<GeoContentPlacementArticle | null>(null);
  const [owners, setOwners] = useState<GeoOwnerOption[]>([]);
  const [topics, setTopics] = useState<GeoTopic[]>([]);
  const [platforms, setPlatforms] = useState<GeoPlatform[]>([]);
  const [questionOptions, setQuestionOptions] = useState<GeoTargetQuestionOption[]>([]);
  const [formTopicId, setFormTopicId] = useState<number | undefined>();

  useEffect(() => {
    void Promise.all([
      getGeoOwnerOptionsApi(),
      getGeoTopicOptionsApi(),
      getGeoPlatformOptionsApi(GEO_PLATFORM_TYPE_CONTENT),
    ]).then(([ownerOpts, topicOpts, plats]) => {
      setOwners(ownerOpts ?? []);
      setTopics(topicOpts ?? []);
      setPlatforms(plats ?? []);
    });
  }, []);

  useEffect(() => {
    if (!formTopicId) {
      setQuestionOptions([]);
      return;
    }
    void getGeoTargetQuestionsApi(formTopicId).then((list) => setQuestionOptions(list ?? []));
  }, [formTopicId]);

  useEffect(() => {
    if (!open) return;
    if (editing) {
      setFormTopicId(editing.topicId);
      form.setFieldsValue({
        topicId: editing.topicId,
        placementId: editing.placementId,
        title: editing.title,
        platformName: editing.platformName,
        contentForm: editing.contentForm || '图文',
        publishStatus: editing.publishStatus || '未投放',
        publishUrl: editing.publishUrl,
        publishTime: editing.publishTime ? dayjs(editing.publishTime) : undefined,
        remark: editing.remark,
      });
    } else {
      setFormTopicId(undefined);
      form.setFieldsValue({
        topicId: undefined,
        placementId: undefined,
        title: undefined,
        platformName: undefined,
        contentForm: '图文',
        publishStatus: '未投放',
        publishUrl: undefined,
        publishTime: undefined,
        remark: undefined,
      });
    }
  }, [editing, form, open]);

  const userOptions = useMemo(() => owners.map((u) => ({ label: u.displayName, value: u.userId })), [owners]);
  const topicOptions = useMemo(() => topics.map((t) => ({ label: t.topicName, value: t.id })), [topics]);
  const platformOptions = useMemo(
    () => platforms.map((p) => ({ label: p.platformName, value: p.platformName })),
    [platforms],
  );
  const targetQuestionFieldOptions = useMemo(
    () =>
      questionOptions.map((q) => ({
        label: q.targetQuestion || `投放#${q.placementId}`,
        value: q.placementId,
      })),
    [questionOptions],
  );

  const columns: ProColumnType<GeoContentPlacementArticle>[] = [
    {
      title: '操作',
      valueType: 'option',
      width: 140,
      render: (_, record) => (
        <ActionButtons
          items={[
            {
              key: 'edit',
              label: '编辑',
              perm: 'geo:article:edit',
              onClick: () => {
                setEditing(record);
                setOpen(true);
              },
            },
            {
              key: 'delete',
              label: '删除',
              perm: 'geo:article:delete',
              confirmTitle: '确定删除该发布文章？',
              onClick: async () => {
                await deleteGeoContentPlacementItemApi(record.id);
                message.success('已删除');
                actionRef.current?.reload();
              },
            },
          ]}
        />
      ),
    },
    {
      title: '发布人',
      dataIndex: 'publisherUserId',
      width: 120,
      valueType: 'select',
      fieldProps: {
        showSearch: true,
        optionFilterProp: 'label',
        allowClear: true,
        options: userOptions,
      },
      render: (_, r) => r.publisherName || '-',
    },
    {
      title: '撰写人',
      dataIndex: 'ownerUserId',
      width: 120,
      valueType: 'select',
      fieldProps: {
        showSearch: true,
        optionFilterProp: 'label',
        allowClear: true,
        options: userOptions,
      },
      render: (_, r) => r.ownerName || '-',
    },
    {
      title: '话题',
      dataIndex: 'topicId',
      width: 140,
      valueType: 'select',
      fieldProps: {
        showSearch: true,
        optionFilterProp: 'label',
        allowClear: true,
        options: topicOptions,
      },
      render: (_, r) => r.topicName || '-',
    },
    {
      title: '目标问题',
      dataIndex: 'targetQuestion',
      width: 240,
      ellipsis: true,
    },
    {
      title: '标题',
      dataIndex: 'title',
      width: 200,
      ellipsis: true,
      search: false,
    },
    {
      title: '发布平台',
      dataIndex: 'platformName',
      width: 120,
      search: false,
    },
    {
      title: '内容形态',
      dataIndex: 'contentForm',
      width: 100,
      search: false,
      render: (_, r) => r.contentForm || '图文',
    },
    {
      title: '投放状态',
      dataIndex: 'publishStatus',
      width: 120,
      valueType: 'select',
      valueEnum: Object.fromEntries(GEO_CONTENT_PUBLISH_STATUS.map((s) => [s.value, { text: s.label }])),
      fieldProps: { options: [...GEO_CONTENT_PUBLISH_STATUS], allowClear: true },
      render: (_, r) => {
        const status = r.publishStatus || '未投放';
        return <Tag color={STATUS_COLOR[status] || 'default'}>{status}</Tag>;
      },
    },
    {
      title: '投放链接',
      dataIndex: 'publishUrl',
      width: 220,
      ellipsis: true,
      search: false,
      render: (_, r) => (
        <ExternalLinkText
          href={r.publishUrl}
          drawerTitle='投放页面'
        />
      ),
    },
    {
      title: '发布时间',
      dataIndex: 'publishTime',
      width: 120,
      valueType: 'date',
      search: false,
    },
    createTimeRangeColumn<GeoContentPlacementArticle>(),
    createTimeDisplayColumn<GeoContentPlacementArticle>(),
  ];

  return (
    <>
      <BaseProTable<GeoContentPlacementArticle>
        rowKey='id'
        actionRef={actionRef}
        columns={columns}
        headerTitle='文章列表（按平台发布明细）'
        scroll={{ x: 1600 }}
        request={async (params) => {
          const res = await getGeoContentPlacementArticleListApi({
            pageNum: params.current,
            pageSize: params.pageSize,
            publisherUserId: params.publisherUserId,
            ownerUserId: params.ownerUserId,
            topicId: params.topicId,
            targetQuestion: params.targetQuestion,
            publishStatus: params.publishStatus,
            createTimeStart: params.createTimeStart,
            createTimeEnd: params.createTimeEnd,
          });
          currentPageKeysRef.current = new Set(res.rows.map((r) => r.id));
          return { data: res.rows, success: true, total: res.total };
        }}
        rowSelection={{
          selectedRowKeys,
          onChange: (keys, _rows, { type }) => {
            if (type === 'none') {
              return setSelectedRowKeys([]);
            }
            setSelectedRowKeys((prev) => {
              const global = new Set(prev as number[]);
              currentPageKeysRef.current.forEach((k) => global.delete(k));
              (keys as number[]).forEach((k) => global.add(k));
              return [...global];
            });
          },
        }}
        toolBarRender={() => [
          <PermissionButton
            key='del'
            color='danger'
            variant='filled'
            perm='geo:article:delete'
            onClick={() => {
              if (selectedRowKeys.length === 0) {
                message.warning('请先选择要删除的文章');
                return;
              }
              Modal.confirm({
                title: '批量删除文章',
                content: `确定要删除选中的 ${selectedRowKeys.length} 篇文章吗？此操作不可撤销。`,
                okText: '确定删除',
                cancelText: '取消',
                okButtonProps: { danger: true },
                onOk: async () => {
                  await deleteGeoContentPlacementItemBatchApi(selectedRowKeys as number[]);
                  message.success(`已删除 ${selectedRowKeys.length} 条`);
                  setSelectedRowKeys([]);
                  currentPageKeysRef.current = new Set();
                  actionRef.current?.reload();
                },
              });
            }}
          >
            批量删除
          </PermissionButton>,
          <PermissionButton
            key='add'
            type='primary'
            perm='geo:article:add'
            onClick={() => {
              setEditing(null);
              setOpen(true);
            }}
          >
            新增文章
          </PermissionButton>,
        ]}
      />

      <BaseModalForm
        title={editing ? '编辑发布文章' : '新增发布文章'}
        open={open}
        form={form}
        width={720}
        onOpenChange={(next) => {
          setOpen(next);
          if (!next) setEditing(null);
        }}
        modalProps={{ destroyOnHidden: true, styles: { body: { padding: '24px' } } }}
        onFinish={async (values) => {
          const placementId = values.placementId as number;
          if (!placementId) {
            message.warning('请选择目标问题');
            return false;
          }
          const payload = {
            placementId,
            title: values.title as string,
            platformName: values.platformName as string,
            contentForm: (values.contentForm as string) || '图文',
            publishStatus: values.publishStatus as string,
            publishUrl: values.publishUrl as string | undefined,
            publishTime: values.publishTime
              ? dayjs(values.publishTime as string | dayjs.Dayjs).format('YYYY-MM-DD')
              : undefined,
            remark: values.remark as string | undefined,
          };
          if (editing) {
            await updateGeoContentPlacementItemApi({ ...payload, id: editing.id });
            message.success('已更新');
          } else {
            await createGeoContentPlacementItemApi(payload);
            message.success('已创建');
          }
          actionRef.current?.reload();
          return true;
        }}
      >
        <div className='grid grid-cols-2 gap-x-8 gap-y-1'>
          <Form.Item
            name='topicId'
            label='话题'
            rules={[{ required: true, message: '请选择话题' }]}
          >
            <Select
              options={topicOptions}
              showSearch
              optionFilterProp='label'
              placeholder='请选择话题'
              onChange={(v: number) => {
                setFormTopicId(v);
                form.setFieldsValue({ placementId: undefined });
              }}
            />
          </Form.Item>
          <Form.Item
            name='placementId'
            label='目标问题'
            rules={[{ required: true, message: '请选择目标问题' }]}
          >
            <Select
              options={targetQuestionFieldOptions}
              showSearch
              optionFilterProp='label'
              placeholder={formTopicId ? '请选择目标问题' : '请先选择话题'}
              disabled={!formTopicId && !editing}
            />
          </Form.Item>
          <Form.Item
            name='title'
            label='标题'
            rules={[{ required: true, message: '请输入标题' }]}
            className='col-span-2'
          >
            <Input
              allowClear
              placeholder='文章标题'
            />
          </Form.Item>
          <Form.Item
            name='platformName'
            label='发布平台'
            rules={[{ required: true, message: '请选择发布平台' }]}
          >
            <Select
              options={platformOptions}
              showSearch
              optionFilterProp='label'
              placeholder='请选择发布平台'
            />
          </Form.Item>
          <Form.Item
            name='contentForm'
            label='内容形态'
          >
            <Select options={[...GEO_CONTENT_FORMS]} />
          </Form.Item>
          <Form.Item
            name='publishStatus'
            label='投放状态'
            rules={[{ required: true, message: '请选择投放状态' }]}
          >
            <Select options={[...GEO_CONTENT_PUBLISH_STATUS]} />
          </Form.Item>
          <Form.Item
            name='publishTime'
            label='发布时间'
          >
            <DatePicker className='w-full' />
          </Form.Item>
          <Form.Item
            name='publishUrl'
            label='投放链接'
            className='col-span-2'
          >
            <Input
              allowClear
              placeholder='https://...'
            />
          </Form.Item>
          <Form.Item
            name='remark'
            label='备注'
            className='col-span-2'
          >
            <Input.TextArea
              rows={3}
              allowClear
            />
          </Form.Item>
        </div>
      </BaseModalForm>
    </>
  );
});

export default ArticlePage;
