import { create } from 'zustand';
import type { AlertItem } from '../types';

const RING_BUFFER_SIZE = 200;

interface AlertsState {
  items: AlertItem[];                       // 最新在前，最多 200 条
  lastSeenAt: number | null;                  // 用户上次"查看"的时间戳（毫秒）
  push: (a: AlertItem) => void;
  pushMany: (as: AlertItem[]) => void;
  clear: () => void;
  markAllSeen: () => void;
  /** 角标：未读 = occurredAt > lastSeenAt */
  unreadCount: () => number;
  setLastSeenAt: (ts: number) => void;
}

export const useAlertsStore = create<AlertsState>((set, get) => ({
  items: [],
  lastSeenAt: null,

  push: (a) => set((s) => {
    // 去重：同 id 不重复
    if (s.items.some((x) => x.id === a.id)) return s;
    const next = [a, ...s.items];
    if (next.length > RING_BUFFER_SIZE) next.length = RING_BUFFER_SIZE;
    return { items: next };
  }),

  pushMany: (as) => set((s) => {
    const seen = new Set(s.items.map((x) => x.id));
    const fresh = as.filter((a) => !seen.has(a.id));
    if (fresh.length === 0) return s;
    const next = [...fresh.reverse(), ...s.items];  // 保持最新在前
    if (next.length > RING_BUFFER_SIZE) next.length = RING_BUFFER_SIZE;
    return { items: next };
  }),

  clear: () => set({ items: [] }),

  markAllSeen: () => {
    const now = Date.now();
    try { localStorage.setItem('alerts:lastSeenAt', String(now)); } catch {}
    set({ lastSeenAt: now });
  },

  setLastSeenAt: (ts) => {
    try { localStorage.setItem('alerts:lastSeenAt', String(ts)); } catch {}
    set({ lastSeenAt: ts });
  },

  unreadCount: () => {
    const s = get();
    if (!s.lastSeenAt) return s.items.filter((a) => !a.handled).length;
    const since = new Date(s.lastSeenAt).getTime();
    return s.items.filter((a) => !a.handled && new Date(a.occurredAt).getTime() > since).length;
  },
}));

// 启动时从 localStorage 恢复
try {
  const saved = localStorage.getItem('alerts:lastSeenAt');
  if (saved) useAlertsStore.setState({ lastSeenAt: Number(saved) });
} catch {}
