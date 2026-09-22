/** 根据候选人当前阶段，推导下一场可创建的面试轮次；不适合邀约时返回 null */
export function nextInviteRound(stage?: string | null): number | null {
  switch (stage) {
    case 'SCREEN_PASS':
      return 1;
    case 'FIRST_ROUND':
      return 2;
    case 'SECOND_ROUND':
      return 3;
    case 'R3_PASS':
      return 4;
    case 'R4_PASS':
      return 5;
    default:
      return null;
  }
}

export function canCreateInvite(stage?: string | null, maxRound?: number | null): boolean {
  const round = nextInviteRound(stage);
  if (round == null) return false;
  if (maxRound != null && maxRound > 0 && round > maxRound) return false;
  return true;
}
