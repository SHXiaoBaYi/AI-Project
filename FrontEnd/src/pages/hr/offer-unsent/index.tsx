import { memo } from 'react';
import HrDictMaintainPage from '@/components/hr/HrDictMaintainPage';

const OfferUnsentPage = memo(function OfferUnsentPage() {
  return (
    <HrDictMaintainPage
      dictType='OFFER_UNSENT'
      title='offer未发原因'
      nameLabel='原因'
      namePlaceholder='例如：HC冻结'
      permEdit='hr:offer-unsent:edit'
      deleteHint='已有流程使用该原因时不能删除。'
    />
  );
});

export default OfferUnsentPage;
