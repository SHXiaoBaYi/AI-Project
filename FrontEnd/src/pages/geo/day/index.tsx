import { memo, useEffect, useState } from 'react';
import { ProForm, ProFormDateRangePicker, ProFormSelect, ProFormText } from '@ant-design/pro-components';
import { Card } from 'antd';
import dayjs from 'dayjs';
import { getGeoDailyBoardApi, getGeoPlatformsApi, getGeoTopicOptionsApi } from '@/api/geo';
import type { GeoDailyBoard, GeoTopic } from '@/types/geo';
import { GeoTrendBoard } from '@/components/geo/GeoTrendBoard';

const emptyBoard: GeoDailyBoard = { mentionChart: [], firstMentionChart: [], recommendChart: [], rows: [] };
const defaultDays = [dayjs().subtract(13, 'day'), dayjs()];

const DayBoardPage = memo(function DayBoardPage() {
  const [topics, setTopics] = useState<GeoTopic[]>([]);
  const [platforms, setPlatforms] = useState<string[]>([]);
  const [board, setBoard] = useState<GeoDailyBoard>(emptyBoard);

  const load = async (values?: Record<string, any>) => {
    const range = (values?.dateRange as dayjs.Dayjs[] | undefined) ?? defaultDays;
    const data = await getGeoDailyBoardApi({
      startDate: range[0]?.format('YYYY-MM-DD'),
      endDate: range[1]?.format('YYYY-MM-DD'),
      topicId: values?.topicId,
      keyword: values?.keyword,
      platforms: values?.platforms,
    });
    setBoard(data);
  };

  useEffect(() => {
    getGeoTopicOptionsApi().then(setTopics);
    getGeoPlatformsApi().then(setPlatforms);
    load();
  }, []);

  return (
    <div className='flex flex-col gap-4'>
      <Card>
        <ProForm
          layout='inline'
          submitter={{ searchConfig: { submitText: '查询' } }}
          onFinish={async (v) => {
            await load(v);
            return true;
          }}
        >
          <ProFormDateRangePicker
            name='dateRange'
            label='日期范围'
            initialValue={defaultDays}
            fieldProps={{ placeholder: ['开始日期', '结束日期'] }}
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
        </ProForm>
      </Card>
      <GeoTrendBoard
        mentionChart={board.mentionChart}
        firstMentionChart={board.firstMentionChart}
        recommendChart={board.recommendChart}
        rows={(board.rows ?? []).map((r) => ({ ...r, axisLabel: r.dateLabel }))}
        axisTitle='日期'
        tableTitle='日报明细（实时聚合，默认近 14 天）'
      />
    </div>
  );
});

export default DayBoardPage;
