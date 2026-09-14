import { createSlice, type PayloadAction } from '@reduxjs/toolkit';
import type { ThemeConfig } from 'antd';

export interface ThemeState {
  config: ThemeConfig;
}

const initialState: ThemeState = {
  config: {
    token: {
      colorPrimary: '#165dff',
      colorLink: '#165dff',
    },
    components: {
      Button: { borderRadius: 2 },
      Menu: { fontFamily: 'AlibabaPuHuiTi-3-55-Regular' },
      Grid: { fontFamily: 'AlibabaPuHuiTi-3-45-Light' },
      Pagination: { itemSize: 28 },
    },
  },
};

const themeSlice = createSlice({
  name: 'theme',
  initialState,
  reducers: {
    setThemeConfig(state: ThemeState, action: PayloadAction<ThemeConfig>) {
      state.config = action.payload;
    },
    setColorPrimary(state, action: PayloadAction<string>) {
      if (!state.config.token) {
        state.config.token = {};
      }
      state.config.token.colorPrimary = action.payload;
      state.config.token.colorLink = action.payload;
    },
  },
});

export const { setThemeConfig, setColorPrimary } = themeSlice.actions;
export default themeSlice.reducer;
