import { Navigate } from 'react-router-dom';

/** 旧入口兼容：跳转到 GEO 看板 */
export default function BoardIndexRedirect() {
  return (
    <Navigate
      to='/board/geo'
      replace
    />
  );
}
