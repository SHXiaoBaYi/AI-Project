import { createSlice, createAsyncThunk } from '@reduxjs/toolkit';
import { dingTalkLoginApi, loginApi, getUserInfoApi, logoutApi } from '@/api/auth';
import { getToken, setToken, removeToken } from '@/utils/auth';
import type { UserInfo } from '@/types/user';
import type { MenuTree } from '@/types/menu';

interface UserState {
  token: string;
  userInfo: UserInfo | null;
  permissions: string[];
  menus: MenuTree[];
}

export interface LoginRejectPayload {
  code?: number;
  message?: string;
}

const initialState: UserState = {
  token: getToken(),
  userInfo: null,
  permissions: [],
  menus: [],
};

export const login = createAsyncThunk(
  'user/login',
  async (
    { username, password, force = false }: { username: string; password: string; force?: boolean },
    { rejectWithValue },
  ) => {
    try {
      const res = await loginApi(username, password, force);
      setToken(res.token);
      return res.token;
    } catch (err) {
      const e = err as Error & { code?: number };
      return rejectWithValue({
        code: e.code,
        message: e.message || '登录失败',
      } satisfies LoginRejectPayload);
    }
  },
);

export const loginByDingTalk = createAsyncThunk(
  'user/loginByDingTalk',
  async ({ authCode, force = false }: { authCode: string; force?: boolean }, { rejectWithValue }) => {
    try {
      const res = await dingTalkLoginApi(authCode, force);
      setToken(res.token);
      return res.token;
    } catch (err) {
      const e = err as Error & { code?: number };
      return rejectWithValue({
        code: e.code,
        message: e.message || '登录失败',
      } satisfies LoginRejectPayload);
    }
  },
);

export const getInfo = createAsyncThunk('user/getInfo', async () => {
  const res = await getUserInfoApi();
  return res;
});

export const logoutRemote = createAsyncThunk('user/logoutRemote', async () => {
  try {
    await logoutApi();
  } catch {
    // ignore network errors on logout
  } finally {
    removeToken();
  }
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
      .addCase(loginByDingTalk.fulfilled, (state, action) => {
        state.token = action.payload;
      })
      .addCase(getInfo.fulfilled, (state, action) => {
        const { menus, ...userInfo } = action.payload as any;
        state.userInfo = userInfo;
        state.permissions = userInfo.permissions ?? [];
        state.menus = menus ?? [];
      })
      .addCase(logoutRemote.fulfilled, (state) => {
        state.token = '';
        state.userInfo = null;
        state.permissions = [];
        state.menus = [];
      });
  },
});

export const { logout } = userSlice.actions;
export default userSlice.reducer;
