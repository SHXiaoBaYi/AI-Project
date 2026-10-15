import { memo } from 'react';
import HrDictMaintainPage from '@/components/hr/HrDictMaintainPage';

const JobPriorityPage = memo(function JobPriorityPage() {
  return (
    <HrDictMaintainPage
      dictType='JOB_PRIORITY'
      title='岗位优先级'
      nameLabel='说明'
      namePlaceholder='例如：本月必须到岗'
      permEdit='hr:job-priority:edit'
      showCode
      codeLabel='编码'
      codePlaceholder='例如：P0'
      extraHint='独立枚举，不与招聘需求里的紧急 / 优先 / 常规混用。默认 P0 本月必须到岗、P1 x份合格简历/到岗。'
    />
  );
});

export default JobPriorityPage;
