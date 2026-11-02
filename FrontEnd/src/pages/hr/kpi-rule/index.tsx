import { memo } from 'react';
import { Card, Result } from 'antd';
import { Link } from 'react-router-dom';

/** 统一考核计分规则已废弃：改在招聘需求按岗位配置每月任务要求 */
const KpiRuleDeprecatedPage = memo(function KpiRuleDeprecatedPage() {
  return (
    <Card className='m-4'>
      <Result
        status='info'
        title='考核计分规则已下线'
        subTitle='请在「招聘需求」中按岗位配置每个月的任务要求（P0/P1、到岗或合格简历二选一）。考核得分按该配置计算。'
        extra={
          <Link
            className='text-sky-600'
            to='/hr/requisition'
          >
            前往招聘需求
          </Link>
        }
      />
    </Card>
  );
});

export default KpiRuleDeprecatedPage;
