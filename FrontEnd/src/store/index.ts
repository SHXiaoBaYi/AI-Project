import { configureStore } from '@reduxjs/toolkit';
import userReducer from './slices/userSlice';
import menuReducer from './slices/menuSlice';
import themeReducer from './slices/themeSlice';
import staticFunctionReducer from './slices/staticFunctionSlice';

export const store = configureStore({
  reducer: {
    user: userReducer,
    menu: menuReducer,
    theme: themeReducer,
    staticFunction: staticFunctionReducer,
  },
});

export type RootState = ReturnType<typeof store.getState>;
export type AppDispatch = typeof store.dispatch;
