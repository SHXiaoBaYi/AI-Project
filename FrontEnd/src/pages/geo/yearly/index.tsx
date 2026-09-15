import { memo, useEffect, useState } from 'react';
import {
  ProFormDatePicker,
  ProFormDateRangePicker,
  ProFormDigit,
  ProFormSelect,
  ProFormText,
  QueryFilter,
} from '@ant-design/pro-components';
import { App, Button, Card, Popconfirm, Table, Tag } from 'antd';
import { Column, Line } from '@ant-design/charts';
import dayjs from 'dayjs';
import BaseModalForm from '@/components/BaseModalForm';
import PermissionButton from '@/components/Buttons/PermissionButton';
import {
  deleteGeoYearTargetApi,
  getGeoPlatformsApi,
  getGeoTopicOptionsApi,
  getGeoYearlyBoardApi,
  getGeoYearTargetsApi,
  saveGeoYearTargetApi,
} from '@/api/geo';
import type { GeoTopic, GeoYearTarget, GeoYearlyBoard } from '@/types/geo';
import { BUTTERFLY_SEARCH } from '@/constants/searchLayout';

const defaultYears = [dayjs().subtract(1, 'year'), dayjs()];

const YearlyPage = memo(function YearlyPage() {
  const { message } = App.useApp();
  const [topics, setTopics] = useState<GeoTopic[]>([]);
  const [platforms, setPlatforms] = useState<string[]>([]);
  const [board, setBoard] = useState<GeoYearlyBoard>({ actualChart: [], achieveChart: [], rows: [] });
  const [targets, setTargets] = useState<GeoYearTarget[]>([]);
  const [targetOpen, setTargetOpen] = useState(false);

  const loadBoard = async (values?: Record<string, any>) => {
    const range = (values?.yearRange as dayjs.Dayjs[] | undefined) ?? defaultYears;
    const data = await getGeoYearlyBoardApi({
      startDate: range[0]?.startOf('year').format('YYYY-MM-DD'),
      endDate: range[1]?.endOf('year').format('YYYY-MM-DD'),
      topicId: values?.topicId,
      keyword: values?.keyword,
      platforms: values?.platforms,
    });
    setBoard(data);
  };

  const loadTargets = () => getGeoYearTargetsApi().then(setTargets);

  useEffect(() => {
    getGeoTopicOptionsApi().then(setTopics);
    getGeoPlatformsApi().then(setPlatforms);
    loadBoard();
    loadTargets();
  }, []);

  return (
    <div className='flex flex-col gap-4'>
      <Card>
        <QueryFilter
          {...BUTTERFLY_SEARCH}
          initialValues={{ yearRange: defaultYears }}
          onFinish={async (v) => {
            await loadBoard(v);
            return true;
          }}
        >
          <ProFormDateRangePicker
            name='yearRange'
            label='年份范围'
            fieldProps={{ picker: 'year', format: 'YYYY', placeholder: ['开始年', '结束年'] }}
          />
          <ProFormSelect
            name='topicId'
            label='话题'
            allowClear
            options={topics.map((t) => ({ label: t.topicName, value: t.id }))}
          />
          <ProFormText
            name='keyword'
            label='关键字'
          />
          <ProFormSelect
            name='platforms'
            label='平台'
            allowClear
            options={platforms.map((p) => ({ label: p, value: p }))}
            fieldProps={{ mode: 'multiple', maxTagCount: 'responsive' }}
          />
        </QueryFilter>
      </Card>
      <Card title='实际达成%（折线，系列=平台）'>
        <Line
          data={board.actualChart}
          xField='axis'
          yField='value'
          colorField='series'
          height={280}
        />
      </Card>
      <Card title='目标达成率%（柱状，系列=平台）'>
        <Column
          data={board.achieveChart}
          xField='axis'
          yField='value'
          colorField='series'
          height={260}
        />
      </Card>
      <Card
        title={`达成明细（只读，已落库周期 ${board.persistedPeriodCount ?? 0}）`}
        extra={
          <PermissionButton
            perm='geo:yearly:target'
            onClick={() => setTargetOpen(true)}
          >
            配置目标
          </PermissionButton>
        }
      >
        <div className='mb-3 text-sm text-neutral-600'>
          年报数据由定时任务自动落库，页面仅支持查看；目标配置仍可维护。
        </div>
        <Table
          rowKey={(r) => `${r.periodLabel}-${r.topicName}-${r.platform}`}
          dataSource={board.rows}
          pagination={false}
          columns={[
            { title: '时间', dataIndex: 'periodLabel' },
            { title: '话题', dataIndex: 'topicName' },
            { title: '平台', dataIndex: 'platform' },
            { title: '目标%', dataIndex: 'targetRate' },
            { title: '实际达成%', dataIndex: 'actualRate' },
            { title: '达成率%', dataIndex: 'achieveRate' },
            { title: '样本', dataIndex: 'sampleCount' },
            {
              title: '来源',
              dataIndex: 'fromSnapshot',
              width: 90,
              render: (v: boolean | undefined) => (v ? <Tag color='success'>已落库</Tag> : <Tag>实时</Tag>),
            },
          ]}
        />
      </Card>
      <Card title='目标配置'>
        <Table
          rowKey='id'
          dataSource={targets}
          pagination={false}
          columns={[
            { title: '时间段', dataIndex: 'periodLabel' },
            { title: '话题', dataIndex: 'topicId', render: (id) => topics.find((t) => t.id === id)?.topicName || id },
            { title: '开始', dataIndex: 'periodStart' },
            { title: '结束', dataIndex: 'periodEnd' },
            { title: '目标%', dataIndex: 'targetRate' },
            {
              title: '操作',
              render: (_, r) => (
                <Popconfirm
                  title='确定删除该目标？'
                  onConfirm={async () => {
                    await deleteGeoYearTargetApi(r.id);
                    message.success('已删除');
                    loadTargets();
                    loadBoard();
                  }}
                >
                  <Button
                    type='link'
                    size='small'
                    danger
                  >
                    删除
                  </Button>
                </Popconfirm>
              ),
            },
          ]}
        />
      </Card>
      <BaseModalForm
        title='新增目标'
        open={targetOpen}
        onOpenChange={setTargetOpen}
        onFinish={async (values) => {
          await saveGeoYearTargetApi({
            ...values,
            periodStart: values.periodStart ? dayjs(values.periodStart).format('YYYY-MM-DD') : undefined,
            periodEnd: values.periodEnd ? dayjs(values.periodEnd).format('YYYY-MM-DD') : undefined,
          });
          message.success('已保存');
          loadTargets();
          loadBoard();
          return true;
        }}
      >
        <ProFormText
          name='periodLabel'
          label='时间段名称'
          rules={[{ required: true }]}
          placeholder='如 全年 / 7-8月'
        />
        <ProFormDatePicker
          name='periodStart'
          label='区间开始'
        />
        <ProFormDatePicker
          name='periodEnd'
          label='区间结束'
        />
        <ProFormSelect
          name='topicId'
          label='话题'
          rules={[{ required: true }]}
          options={topics.map((t) => ({ label: t.topicName, value: t.id }))}
        />
        <ProFormDigit
          name='targetRate'
          label='目标%'
          min={0}
          max={100}
          rules={[{ required: true }]}
          initialValue={80}
        />
      </BaseModalForm>
    </div>
  );
});

export default YearlyPage;
