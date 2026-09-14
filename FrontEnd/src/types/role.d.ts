export interface RoleVO {
  roleId: number;
  roleName: string;
  roleKey: string;
  sortOrder: number;
  status: number;
  remark: string;
  createTime: string;
  menuIds: number[];
}

export interface RoleDTO {
  roleId?: number;
  roleName: string;
  roleKey: string;
  sortOrder?: number;
  status?: number;
  remark?: string;
}
