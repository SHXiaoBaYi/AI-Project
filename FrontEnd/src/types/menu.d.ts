export interface MenuVO {
  menuId: number;
  menuName: string;
  parentId: number;
  sortOrder: number;
  path: string;
  component: string;
  menuType: 'M' | 'C' | 'F';
  perms: string;
  icon: string;
  visible: number;
  status: number;
  remark: string;
  createTime: string;
}

export interface MenuTree {
  menuId: number;
  menuName: string;
  path: string;
  component: string;
  icon: string;
  sortOrder: number;
  status: number;
  children: MenuTree[];
}

export interface MenuDTO {
  menuId?: number;
  menuName: string;
  parentId?: number;
  sortOrder?: number;
  path?: string;
  component?: string;
  menuType: string;
  perms?: string;
  icon?: string;
  visible?: number;
  status?: number;
  remark?: string;
}
