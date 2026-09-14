import { createSlice } from '@reduxjs/toolkit';
import { getInfo } from './userSlice';
import type { MenuTree } from '@/types/menu';

interface MenuState {
  menus: MenuTree[];
  routesLoaded: boolean;
}

const initialState: MenuState = {
  menus: [],
  routesLoaded: false,
};

const menuSlice = createSlice({
  name: 'menu',
  initialState,
  reducers: {
    resetMenus(state) {
      state.menus = [];
      state.routesLoaded = false;
    },
    setRoutesLoaded(state) {
      state.routesLoaded = true;
    },
  },
  extraReducers: (builder) => {
    builder.addCase(getInfo.fulfilled, (state, action) => {
      state.menus = action.payload.menus ?? [];
      state.routesLoaded = true;
    });
  },
});

export const { resetMenus, setRoutesLoaded } = menuSlice.actions;
export default menuSlice.reducer;
