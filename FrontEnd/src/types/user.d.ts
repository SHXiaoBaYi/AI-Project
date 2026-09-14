export interface UserInfo {
  userId: number;
  username: string;
  nickname: string;
  avatar: string;
  roles: string[];
  permissions: string[];
}

export interface UserVO {
  userId: number;
  username: string;
  nickname: string;
  email: string;
  phone: string;
  gender: number;
  status: number;
  remark: string;
  createTime: string;
  roles: { roleId: number; roleName: string }[];
}

export interface UserDTO {
  userId?: number;
  username: string;
  password?: string;
  nickname?: string;
  email?: string;
  phone?: string;
  gender?: number;
  status?: number;
  remark?: string;
  roleIds?: number[];
}

export interface UserImportError {
  rowIndex: number;
  field: string;
  message: string;
}

export interface UserImportResult {
  totalCount: number;
  successCount: number;
  failureCount: number;
  errors: UserImportError[];
}
