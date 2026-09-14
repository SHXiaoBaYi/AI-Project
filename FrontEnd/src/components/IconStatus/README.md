## 图标状态组件

    设置一个主色，自动生成背景色的图标状态组件，支持iconfont自定义图标和@ant-design/icons组件，如何定义字典请看dictionary中userStatus.ts定义规则

```javascript
import dictionary from '@/dictionary';
import IconStatus from '@/components/IconStatus';

<IconStatus
  type='userStatus'
  value={value}
/>;
```
