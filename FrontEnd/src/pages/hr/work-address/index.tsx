import { memo } from 'react';
import HrDictMaintainPage from '@/components/hr/HrDictMaintainPage';

const WorkAddressPage = memo(function WorkAddressPage() {
  return (
    <HrDictMaintainPage
      dictType='WORK_ADDRESS'
      title='工作地址'
      nameLabel='工作地址'
      namePlaceholder='例如：上海漕河泾'
      permEdit='hr:work-address:edit'
      showCategory
      categoryLabel='地址类别'
      categoryPlaceholder='例如：办公室'
    />
  );
});

export default WorkAddressPage;
