import { memo } from 'react';
import HrDictMaintainPage from '@/components/hr/HrDictMaintainPage';

const BgFailPage = memo(function BgFailPage() {
  return (
    <HrDictMaintainPage
      dictType='BG_FAIL'
      title='背调不通过原因'
      nameLabel='原因'
      namePlaceholder='例如：学历造假'
      permEdit='hr:bg-fail:edit'
      deleteHint='已有流程使用该原因时不能删除。'
    />
  );
});

export default BgFailPage;
