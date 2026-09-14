import { memo, useEffect, useRef, useState, type ReactNode, type RefObject } from 'react';
import type { ActionType, ProColumnType, ProFormInstance } from '@ant-design/pro-components';
import {
  ProFormDatePicker,
  ProFormDigit,
  ProFormSelect,
  ProFormText,
  ProFormTextArea,
} from '@ant-design/pro-components';
import { App, Button, Drawer, Image, Upload } from 'antd';
import { InboxOutlined } from '@ant-design/icons';
import dayjs from 'dayjs';
import BaseProTable from '@/components/BaseProTable';
import BaseModalForm from '@/components/BaseModalForm';
import PermissionButton from '@/components/Buttons/PermissionButton';
import ActionButtons from '@/components/Buttons/ActionButtons';
import {
  deleteGeoDailyApi,
  getGeoDailyGroupApi,
  getGeoDailyListApi,
  getGeoLatestInspectDateApi,
  getGeoPlatformsApi,
  getGeoTopicOptionsApi,
  importGeoDailyApi,
  saveGeoDailyBatchApi,
  uploadGeoScreenshotApi,
} from '@/api/geo';
import type { GeoDailyGroup, GeoDailyVO, GeoTopic } from '@/types/geo';

const RECOMMEND_OPTIONS = ['未出现', '出现且推荐', '出现未推荐'].map((v) => ({ label: v, value: v }));

function emptyPlatformItems(platformNames: string[]) {
  return Object.fromEntries(platformNames.map((p) => [p, { mentioned: 0, recommendStatus: '未出现' }])) as Record<
    string,
    Record<string, any>
  >;
}

function groupToItems(platformNames: string[], group?: GeoDailyGroup) {
  const items = emptyPlatformItems(platformNames);
  group?.items?.forEach((row) => {
    items[row.platform] = {
      id: row.id,
      mentioned: row.mentioned ?? 0,
      rankNo: row.rankNo,
      recommendStatus: row.recommendStatus,
      screenshotUrl: row.screenshotUrl,
      thirdPartyUrl: row.thirdPartyUrl,
      negativeContent: row.negativeContent,
      competitors: row.competitors,
    };
  });
  return items;
}

const DailyPage = memo(function DailyPage() {
  const { message } = App.useApp();
  const actionRef = useRef<ActionType>(null);
  const formRef = useRef<ProFormInstance>(null);
  const [topics, setTopics] = useState<GeoTopic[]>([]);
  const [platforms, setPlatforms] = useState<string[]>([]);
  const [formOpen, setFormOpen] = useState(false);
  const [editing, setEditing] = useState<GeoDailyVO | null>(null);
  const [formKey, setFormKey] = useState(0);
  const [initialValues, setInitialValues] = useState<Record<string, any>>({});
  const [importOpen, setImportOpen] = useState(false);
  const [file, setFile] = useState<File | null>(null);
  const [iframeUrl, setIframeUrl] = useState<string>();

  useEffect(() => {
    getGeoTopicOptionsApi().then(setTopics);
    getGeoPlatformsApi().then(setPlatforms);
  }, []);

  const openForm = async (record?: GeoDailyVO) => {
    const names = platforms.length
      ? platforms
      : await getGeoPlatformsApi().then((list) => {
          setPlatforms(list);
          return list;
        });
    if (record) {
      const group = await getGeoDailyGroupApi(record.inspectDate, record.keyword);
      setEditing(record);
      setInitialValues({
        inspectDate: dayjs(group.inspectDate || record.inspectDate),
        topicId: group.topicId || record.topicId,
        keyword: group.keyword || record.keyword,
        items: groupToItems(names, group),
      });
    } else {
      const latest = await getGeoLatestInspectDateApi();
      setEditing(null);
      setInitialValues({
        inspectDate: latest.inspectDate ? dayjs(latest.inspectDate) : dayjs(),
        items: emptyPlatformItems(names),
      });
    }
    setFormKey((k) => k + 1);
    setFormOpen(true);
  };

  const fillGroupFromKey = async () => {
    const dateVal = formRef.current?.getFieldValue('inspectDate');
    const keyword = String(formRef.current?.getFieldValue('keyword') || '').trim();
    if (!dateVal || !keyword) return;
    const group = await getGeoDailyGroupApi(dayjs(dateVal).format('YYYY-MM-DD'), keyword);
    if (!group.items?.length) return;
    formRef.current?.setFieldsValue({
      topicId: group.topicId,
      items: groupToItems(platforms, group),
    });
  };

  const columns: ProColumnType<GeoDailyVO>[] = [
    {
      title: '巡查日期',
      dataIndex: 'inspectDate',
      valueType: 'dateRange',
      width: 120,
      render: (_, r) => r.inspectDate,
    },
    {
      title: '平台',
      dataIndex: 'platform',
      width: 90,
      search: false,
    },
    {
      title: '平台',
      dataIndex: 'platforms',
      hideInTable: true,
      valueType: 'select',
      fieldProps: {
        mode: 'multiple',
        maxTagCount: 'responsive',
        options: platforms.map((p) => ({ label: p, value: p })),
      },
    },
    {
      title: '话题',
      dataIndex: 'topicId',
      width: 120,
      valueType: 'select',
      fieldProps: { options: topics.map((t) => ({ label: t.topicName, value: t.id })) },
      render: (_, r) => r.topicName,
    },
    { title: '关键字', dataIndex: 'keyword', ellipsis: true, width: 220 },
    {
      title: '提及',
      dataIndex: 'mentioned',
      width: 80,
      search: false,
      valueEnum: { 1: { text: '是', status: 'Success' }, 0: { text: '否', status: 'Default' } },
    },
    { title: '排名', dataIndex: 'rankNo', width: 80, search: false },
    { title: '推荐状态', dataIndex: 'recommendStatus', width: 120, search: false },
    {
      title: '截图',
      dataIndex: 'screenshotUrl',
      width: 80,
      search: false,
      render: (_, r) =>
        r.screenshotUrl ? (
          <Image
            width={40}
            src={`/api${r.screenshotUrl}`}
          />
        ) : (
          '-'
        ),
    },
    {
      title: '第三方链接',
      dataIndex: 'thirdPartyUrl',
      width: 120,
      search: false,
      ellipsis: true,
      render: (_, r) =>
        r.thirdPartyUrl ? (
          <Button
            type='link'
            size='small'
            onClick={() => setIframeUrl(r.thirdPartyUrl)}
          >
            站内打开
          </Button>
        ) : (
          '-'
        ),
    },
    { title: '竞品', dataIndex: 'competitors', search: false, ellipsis: true, width: 140 },
    { title: '更新时间', dataIndex: 'updateTime', search: false, width: 170 },
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
              perm: 'geo:daily:edit',
              onClick: () => openForm(record),
            },
            {
              key: 'delete',
              label: '删除',
              perm: 'geo:daily:delete',
              confirmTitle: '确定删除？删除后不再计入看板统计。',
              onClick: async () => {
                await deleteGeoDailyApi(record.id);
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
      <BaseProTable<GeoDailyVO>
        rowKey='id'
        actionRef={actionRef}
        columns={columns}
        headerTitle='日监测数据'
        request={async (params) => {
          const range = params.inspectDate as string[] | undefined;
          const res = await getGeoDailyListApi({
            pageNum: params.current,
            pageSize: params.pageSize,
            startDate: range?.[0],
            endDate: range?.[1],
            topicId: params.topicId,
            keyword: params.keyword,
            platforms: params.platforms,
          });
          return { data: res.rows, success: true, total: res.total };
        }}
        toolBarRender={() => [
          <PermissionButton
            key='import'
            perm='geo:daily:import'
            onClick={() => setImportOpen(true)}
          >
            导入 Excel
          </PermissionButton>,
          <PermissionButton
            key='add'
            type='primary'
            perm='geo:daily:add'
            onClick={() => openForm()}
          >
            新增
          </PermissionButton>,
        ]}
      />

      <BaseModalForm
        key={formKey}
        formRef={formRef}
        width={1000}
        title={editing ? '编辑日监测（多平台）' : '新增日监测（多平台）'}
        open={formOpen}
        onOpenChange={setFormOpen}
        initialValues={initialValues}
        onFinish={async (values) => {
          const items = platforms
            .map((platform) => ({ platform, ...(values.items?.[platform] || {}) }))
            .filter((item) => item.platform);
          await saveGeoDailyBatchApi(
            {
              inspectDate: values.inspectDate ? dayjs(values.inspectDate).format('YYYY-MM-DD') : values.inspectDate,
              topicId: values.topicId,
              keyword: values.keyword,
              items,
            },
            !!editing,
          );
          message.success(editing ? '已更新' : '已保存');
          getGeoPlatformsApi().then(setPlatforms);
          getGeoTopicOptionsApi().then(setTopics);
          actionRef.current?.reload();
          return true;
        }}
      >
        <div className='mb-3 text-sm font-medium'>基础信息</div>
        <div className='grid grid-cols-3 gap-x-4'>
          <ProFormDatePicker
            name='inspectDate'
            label='巡查日期'
            rules={[{ required: true, message: '请选择巡查日期' }]}
            fieldProps={{
              onChange: () => {
                void fillGroupFromKey();
              },
            }}
          />
          <ProFormSelect
            name='topicId'
            label='话题'
            rules={[{ required: true, message: '请选择话题' }]}
            options={topics.map((t) => ({ label: t.topicName, value: t.id }))}
          />
          <ProFormText
            name='keyword'
            label='关键字'
            rules={[{ required: true, message: '请输入关键字' }]}
            fieldProps={{
              onBlur: () => {
                void fillGroupFromKey();
              },
            }}
          />
        </div>

        <MetricGroup
          title='提及情况'
          platforms={platforms}
        >
          {(p) => (
            <>
              <ProFormDigit
                name={['items', p, 'id']}
                hidden
              />
              <ProFormSelect
                name={['items', p, 'mentioned']}
                label={false}
                options={[
                  { label: '是', value: 1 },
                  { label: '否', value: 0 },
                ]}
              />
            </>
          )}
        </MetricGroup>
        <MetricGroup
          title='排名'
          platforms={platforms}
        >
          {(p) => (
            <ProFormDigit
              name={['items', p, 'rankNo']}
              label={false}
              min={1}
              fieldProps={{ precision: 0 }}
            />
          )}
        </MetricGroup>
        <MetricGroup
          title='推荐状态'
          platforms={platforms}
        >
          {(p) => (
            <ProFormSelect
              name={['items', p, 'recommendStatus']}
              label={false}
              options={RECOMMEND_OPTIONS}
            />
          )}
        </MetricGroup>
        <MetricGroup
          title='第三方链接'
          platforms={platforms}
        >
          {(p) => (
            <ProFormText
              name={['items', p, 'thirdPartyUrl']}
              label={false}
            />
          )}
        </MetricGroup>
        <MetricGroup
          title='出现的竞品'
          platforms={platforms}
        >
          {(p) => (
            <ProFormText
              name={['items', p, 'competitors']}
              label={false}
            />
          )}
        </MetricGroup>
        <MetricGroup
          title='负面/错误内容'
          platforms={platforms}
        >
          {(p) => (
            <ProFormTextArea
              name={['items', p, 'negativeContent']}
              label={false}
              fieldProps={{ rows: 2 }}
            />
          )}
        </MetricGroup>
        <MetricGroup
          title='监测截图'
          platforms={platforms}
        >
          {(p) => (
            <PlatformShot
              platform={p}
              formRef={formRef}
            />
          )}
        </MetricGroup>
      </BaseModalForm>

      <BaseModalForm
        title='导入日监测 Excel'
        open={importOpen}
        onOpenChange={(v) => {
          if (!v) setFile(null);
          setImportOpen(v);
        }}
        submitter={false}
      >
        <Upload.Dragger
          accept='.xlsx,.xls'
          maxCount={1}
          beforeUpload={(f) => {
            setFile(f);
            return false;
          }}
          onRemove={() => setFile(null)}
          fileList={file ? [{ uid: '1', name: file.name }] : []}
        >
          <p className='ant-upload-drag-icon'>
            <InboxOutlined />
          </p>
          <p>请上传与「GEO优化监测样例」相同的宽表格式</p>
        </Upload.Dragger>
        <div className='mt-4 flex justify-end'>
          <Button
            type='primary'
            disabled={!file}
            onClick={async () => {
              if (!file) return;
              const res = await importGeoDailyApi(file);
              message.success(`导入完成：新增 ${res.insertCount}，更新 ${res.updateCount}，失败 ${res.failureCount}`);
              setImportOpen(false);
              setFile(null);
              getGeoTopicOptionsApi().then(setTopics);
              getGeoPlatformsApi().then(setPlatforms);
              actionRef.current?.reload();
            }}
          >
            开始导入
          </Button>
        </div>
      </BaseModalForm>

      <Drawer
        title='第三方页面'
        width='70%'
        open={!!iframeUrl}
        onClose={() => setIframeUrl(undefined)}
        extra={
          iframeUrl ? (
            <Button
              type='link'
              href={iframeUrl}
              target='_blank'
            >
              新窗口打开
            </Button>
          ) : null
        }
      >
        {iframeUrl ? (
          <iframe
            title='geo-link'
            src={iframeUrl}
            className='h-[70vh] w-full border-0'
            sandbox='allow-scripts allow-same-origin allow-popups allow-forms'
          />
        ) : null}
      </Drawer>
    </>
  );
});

function MetricGroup({
  title,
  platforms,
  children,
}: {
  title: string;
  platforms: string[];
  children: (platform: string) => ReactNode;
}) {
  return (
    <div className='mb-4'>
      <div className='mb-2 border-b border-neutral-200 pb-1 text-sm font-medium'>{title}</div>
      {platforms.length ? (
        <div
          className='grid gap-3'
          style={{ gridTemplateColumns: `repeat(${platforms.length}, minmax(0, 1fr))` }}
        >
          {platforms.map((p) => (
            <div
              key={p}
              className='min-w-0'
            >
              <div className='mb-1 text-xs text-neutral-500'>{p}</div>
              {children(p)}
            </div>
          ))}
        </div>
      ) : (
        <div className='text-xs text-neutral-400'>请先在平台管理中维护平台</div>
      )}
    </div>
  );
}

function PlatformShot({ platform, formRef }: { platform: string; formRef: RefObject<ProFormInstance | null> }) {
  const [url, setUrl] = useState<string | undefined>();

  useEffect(() => {
    setUrl(formRef.current?.getFieldValue(['items', platform, 'screenshotUrl']));
  }, [platform, formRef]);

  return (
    <div>
      <ProFormText
        name={['items', platform, 'screenshotUrl']}
        hidden
      />
      <Upload
        maxCount={1}
        accept='image/*'
        showUploadList={false}
        customRequest={async (opt) => {
          try {
            const res = await uploadGeoScreenshotApi(opt.file as File);
            formRef.current?.setFieldValue(['items', platform, 'screenshotUrl'], res.url);
            setUrl(res.url);
            opt.onSuccess?.(res);
          } catch (e) {
            opt.onError?.(e as Error);
          }
        }}
      >
        <Button size='small'>上传截图</Button>
      </Upload>
      {url ? (
        <div className='mt-2'>
          <Image
            width={96}
            src={`/api${url}`}
          />
        </div>
      ) : null}
    </div>
  );
}

export default DailyPage;
