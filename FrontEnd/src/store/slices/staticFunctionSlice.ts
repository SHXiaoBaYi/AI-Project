import { useLayoutEffect } from 'react';
import { createSlice } from '@reduxjs/toolkit';
import { App } from 'antd';
import type { MessageInstance } from 'antd/es/message/interface';
import type { ModalStaticFunctions } from 'antd/es/modal/confirm';
import type { NotificationInstance } from 'antd/es/notification/interface';

const staticFunctionSlice = createSlice({
  name: 'staticFunction',
  initialState: {},
  reducers: {},
});

// 模块级变量，存储 App.useApp() 返回的静态方法引用
let message: MessageInstance;
let notification: NotificationInstance;
let modal: Omit<ModalStaticFunctions, 'warn'>;

export { message, notification, modal };

export default staticFunctionSlice.reducer;

// 入口组件，挂载在 <AntdApp> 内部以捕获静态方法
export function StaticFunctionCapture() {
  const staticFunction = App.useApp();

  useLayoutEffect(() => {
    message = staticFunction.message;
    modal = staticFunction.modal;
    notification = staticFunction.notification;
  });

  return null;
}
