import { memo, useEffect, useMemo, useRef, useState } from 'react';
import type { ActionType, ProColumnType, ProFormInstance } from '@ant-design/pro-components';
import { App, Tag } from 'antd';
import BaseProTable from '@/components/BaseProTable';
import TableModal from '@/components/TableModal';
import PermissionButton from '@/components/Buttons/PermissionButton';
import ActionButtons from '@/components/Buttons/ActionButtons';
import { ExternalLinkText } from '@/components/ExternalLinkDrawer';
import { STATUS_COLOR } from '@/components/geo/content-placement/constants';
import {
  createGeoContentPlacementItemApi,
  deleteGeoContentPlacementItemApi,
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
  const formRef = useRef<ProFormInstance>(undefined);
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
      hideInForm: true,
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
      hideInForm: true,
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
        options: topicOptions,
        onChange: (v: number) => {
          setFormTopicId(v);
          formRef.current?.setFieldsValue({ placementId: undefined });
        },
      },
      formItemProps: { rules: [{ required: true, message: '请选择话题' }] },
      render: (_, r) => r.topicName || '-',
    },
    {
      title: '目标问题',
      dataIndex: 'placementId',
      width: 240,
      ellipsis: true,
      valueType: 'select',
      hideInSearch: true,
      fieldProps: {
        showSearch: true,
        optionFilterProp: 'label',
        options: targetQuestionFieldOptions,
        placeholder: formTopicId ? '请选择目标问题' : '请先选择话题',
        disabled: !formTopicId && !editing,
      },
      formItemProps: { rules: [{ required: true, message: '请选择目标问题' }] },
      render: (_, r) => r.targetQuestion || '-',
    },
    {
      title: '目标问题',
      dataIndex: 'targetQuestion',
      width: 240,
      ellipsis: true,
      hideInTable: true,
      hideInForm: true,
    },
    {
      title: '标题',
      dataIndex: 'title',
      width: 200,
      ellipsis: true,
      search: false,
      formItemProps: { rules: [{ required: true, message: '请输入标题' }] },
    },
    {
      title: '发布平台',
      dataIndex: 'platformName',
      width: 120,
      valueType: 'select',
      search: false,
      fieldProps: {
        showSearch: true,
        optionFilterProp: 'label',
        options: platformOptions,
      },
      formItemProps: { rules: [{ required: true, message: '请选择发布平台' }] },
    },
    {
      title: '内容形态',
      dataIndex: 'contentForm',
      width: 100,
      valueType: 'select',
      search: false,
      fieldProps: { options: [...GEO_CONTENT_FORMS] },
      render: (_, r) => r.contentForm || '图文',
    },
    {
      title: '投放状态',
      dataIndex: 'publishStatus',
      width: 120,
      valueType: 'select',
      valueEnum: Object.fromEntries(GEO_CONTENT_PUBLISH_STATUS.map((s) => [s.value, { text: s.label }])),
      fieldProps: { options: [...GEO_CONTENT_PUBLISH_STATUS], allowClear: true },
      formItemProps: { rules: [{ required: true, message: '请选择投放状态' }] },
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
    {
      title: '备注',
      dataIndex: 'remark',
      search: false,
      hideInTable: true,
      valueType: 'textarea',
    },
    createTimeRangeColumn<GeoContentPlacementArticle>(),
    createTimeDisplayColumn<GeoContentPlacementArticle>(),
    {
      title: '操作',
      valueType: 'option',
      width: 140,
      hideInForm: true,
      render: (_, record) => (
        <ActionButtons
          items={[
            {
              key: 'edit',
              label: '编辑',
              perm: 'geo:article:edit',
              onClick: () => {
                setEditing(record);
                setFormTopicId(record.topicId);
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
  ];

  return (
    <>
      <BaseProTable<GeoContentPlacementArticle>
        rowKey='id'
        actionRef={actionRef}
        columns={columns}
        headerTitle='发布文章'
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
          return { data: res.rows, success: true, total: res.total };
        }}
        toolBarRender={() => [
          <PermissionButton
            key='add'
            type='primary'
            perm='geo:article:add'
            onClick={() => {
              setEditing(null);
              setFormTopicId(undefined);
              setQuestionOptions([]);
              setOpen(true);
            }}
          >
            新增文章
          </PermissionButton>,
        ]}
      />

      <TableModal
        readonly={false}
        title={editing ? '编辑发布文章' : '新增发布文章'}
        columns={columns as any}
        open={open}
        formRef={formRef}
        onOpenChange={(v) => {
          setOpen(v);
          if (!v) {
            setEditing(null);
            setFormTopicId(undefined);
          }
        }}
        modalProps={{ width: 720 }}
        initialValues={
          editing
            ? {
                topicId: editing.topicId,
                placementId: editing.placementId,
                title: editing.title,
                platformName: editing.platformName,
                contentForm: editing.contentForm || '图文',
                publishStatus: editing.publishStatus || '未投放',
                publishUrl: editing.publishUrl,
                publishTime: editing.publishTime,
                remark: editing.remark,
              }
            : {
                contentForm: '图文',
                publishStatus: '未投放',
              }
        }
        onFinish={async (values) => {
          const placementId = values.placementId as number;
          if (!placementId) {
            message.warning('请选择目标问题');
            return false;
          }
          const payload = {
            placementId,
            title: values.title,
            platformName: values.platformName,
            contentForm: values.contentForm || '图文',
            publishStatus: values.publishStatus,
            publishUrl: values.publishUrl,
            publishTime: values.publishTime,
            remark: values.remark,
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
      />
    </>
  );
});

export default ArticlePage;
