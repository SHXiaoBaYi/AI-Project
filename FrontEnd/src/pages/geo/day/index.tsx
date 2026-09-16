import { Navigate } from 'react-router-dom';

/** Legacy day board -> expose board (day) */
export default function DayBoardRedirect() {
  return (
    <Navigate
      to='/geo/expose-board?period=day'
      replace
    />
  );
}
