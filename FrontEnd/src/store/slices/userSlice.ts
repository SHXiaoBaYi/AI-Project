import { createSlice, createAsyncThunk } from '@reduxjs/toolkit';
import { loginApi, getUserInfoApi } from '@/api/auth';
import { getToken, setToken, removeToken } from '@/utils/auth';
import type { UserInfo } from '@/types/user';
import type { MenuTree } from '@/types/menu';

interface UserState {
  token: string;
  userInfo: UserInfo | null;
  permissions: string[];
  menus: MenuTree[];
}

const initialState: UserState = {
  token: getToken(),
  userInfo: null,
  permissions: [],
  menus: [],
};

export const login = createAsyncThunk(
  'user/login',
  async ({ username, password }: { username: string; password: string }) => {
    const res = await loginApi(username, password);
    setToken(res.token);
    return res.token;
  },
);

export const getInfo = createAsyncThunk('user/getInfo', async () => {
  const res = await getUserInfoApi();
  return res;
});

const userSlice = createSlice({
  name: 'user',
  initialState,
  reducers: {
    logout(state) {
      state.token = '';
      state.userInfo = null;
      state.permissions = [];
      state.menus = [];
      removeToken();
    },
  },
  extraReducers: (builder) => {
    builder
      .addCase(login.fulfilled, (state, action) => {
        state.token = action.payload;
      })
      .addCase(getInfo.fulfilled, (state, action) => {
        const { menus, ...userInfo } = action.payload as any;
        state.userInfo = userInfo;
        state.permissions = userInfo.permissions ?? [];
        state.menus = menus ?? [];
      });
  },
});

export const { logout } = userSlice.actions;
export default userSlice.reducer;
