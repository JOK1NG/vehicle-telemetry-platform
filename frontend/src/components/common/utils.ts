export function cx(...classes: (string | false | null | undefined)[]): string {
  return classes.filter(Boolean).join(' ');
}

export const fmtTime = (ts: number | string): string => {
  const d = new Date(ts);
  if (Number.isNaN(d.getTime())) return '-';
  return d.toLocaleTimeString('zh-CN', { hour12: false });
};

export const fmtDate = (iso?: string): string => {
  if (!iso) return '-';
  return iso.replace('T', ' ').slice(0, 19);
};

export const fmtNum = (n: number, d = 1): string =>
  Number.isFinite(n) ? n.toFixed(d) : '-';
