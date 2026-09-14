import { memo, useEffect, useState } from 'react';
import { ProForm, ProFormDateRangePicker, ProFormSelect, ProFormText } from '@ant-design/pro-components';
import { Card } from 'antd';
import dayjs from 'dayjs';
import { getGeoPlatformsApi, getGeoTopicOptionsApi, getGeoWeeklyBoardApi } from '@/api/geo';
import type { GeoTopic, GeoWeeklyBoard } from '@/types/geo';
import { GeoTrendBoard } from '@/components/geo/GeoTrendBoard';

const emptyBoard: GeoWeeklyBoard = { mentionChart: [], firstMentionChart: [], recommendChart: [], rows: [] };

function startOfIsoWeek(value: dayjs.Dayjs) {
  return value.subtract((value.day() + 6) % 7, 'day').startOf('day');
}

function endOfIsoWeek(value: dayjs.Dayjs) {
  return startOfIsoWeek(value).add(6, 'day').endOf('day');
}

const defaultWeeks = [startOfIsoWeek(dayjs().subtract(4, 'week')), endOfIsoWeek(dayjs())];

const WeeklyPage = memo(function WeeklyPage() {
  const [topics, setTopics] = useState<GeoTopic[]>([]);
  const [platforms, setPlatforms] = useState<string[]>([]);
  const [board, setBoard] = useState<GeoWeeklyBoard>(emptyBoard);

  const load = async (values?: Record<string, any>) => {
    const range = (values?.weekRange as dayjs.Dayjs[] | undefined) ?? defaultWeeks;
    const data = await getGeoWeeklyBoardApi({
      startDate: startOfIsoWeek(range[0] ?? dayjs()).format('YYYY-MM-DD'),
      endDate: endOfIsoWeek(range[1] ?? dayjs()).format('YYYY-MM-DD'),
      topicId: values?.topicId,
      keyword: values?.keyword,
      platforms: values?.platforms,
    });
    setBoard({
      mentionChart: data?.mentionChart ?? [],
      firstMentionChart: data?.firstMentionChart ?? [],
      recommendChart: data?.recommendChart ?? [],
      rows: data?.rows ?? [],
    });
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
            name='weekRange'
            label='周范围'
            initialValue={defaultWeeks}
            fieldProps={{
              picker: 'week',
              format: 'YYYY-[第]ww[周]',
              placeholder: ['开始周', '结束周'],
            }}
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
        rows={board.rows.map((r) => ({ ...r, axisLabel: r.weekLabel }))}
        axisTitle='周次'
        tableTitle='周报明细（实时聚合，默认近 5 周）'
      />
    </div>
  );
});

export default WeeklyPage;
