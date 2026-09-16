import { Navigate } from 'react-router-dom';

/** 旧「内容投放管理」入口重定向到管理视角 */
export default function ContentPlacementRedirect() {
  return (
    <Navigate
      to='/geo/content-placement-manage'
      replace
    />
  );
}
