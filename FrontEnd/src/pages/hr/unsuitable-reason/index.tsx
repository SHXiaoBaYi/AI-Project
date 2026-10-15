import { memo } from 'react';
import HrDictMaintainPage from '@/components/hr/HrDictMaintainPage';

const UnsuitableReasonPage = memo(function UnsuitableReasonPage() {
  return (
    <HrDictMaintainPage
      dictType='UNSUITABLE'
      title='电话&面试不合适理由'
      nameLabel='理由'
      namePlaceholder='例如：沟通表达不足'
      permEdit='hr:unsuitable:edit'
      deleteHint='已有电话沟通使用该理由时不能删除。'
    />
  );
});

export default UnsuitableReasonPage;
