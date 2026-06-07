import { useEffect, useState } from 'react';
import SockJS from 'sockjs-client';
import { Client, type IMessage } from '@stomp/stompjs';
import { realtimeApi } from '../api/realtime';
import type { VehicleUpdateData } from '../types';

const WS_URL = '/ws';
const SNAPSHOT_REFRESH_MS = 30_000;

/**
 * 订阅 /topic/vehicles 维护一个 online vehicleId Set
 * - 启动时 GET /api/vehicles/snapshot 拿基线
 * - 推送 status=1 时加入，非在线状态时移除
 * - 每 30 秒用 snapshot 覆盖校准，避免后端清理 Redis 在线集合后前端计数滞留
 */
export function useOnlineVehicleCount(): number {
  const [online, setOnline] = useState<Set<number>>(new Set());

  useEffect(() => {
    let mounted = true;
    let client: Client | null = null;
    let refreshTimer: number | null = null;

    const refreshSnapshot = () => {
      realtimeApi.snapshot().then((list) => {
        if (!mounted) return;
        setOnline(new Set(list.map((v) => v.vehicleId)));
      }).catch(() => {});
    };

    refreshSnapshot();
    refreshTimer = window.setInterval(refreshSnapshot, SNAPSHOT_REFRESH_MS);

    const token = localStorage.getItem('token');
    const url = token ? `${WS_URL}?token=${encodeURIComponent(token)}` : WS_URL;
    client = new Client({
      webSocketFactory: () => new SockJS(url) as unknown as WebSocket,
      reconnectDelay: 5000,
      heartbeatIncoming: 10000,
      heartbeatOutgoing: 10000,
      debug: () => {},
    });
    client.onConnect = () => {
      client!.subscribe('/topic/vehicles', (msg: IMessage) => {
        try {
          const env = JSON.parse(msg.body) as { type: string; vehicles: VehicleUpdateData[] };
          if (env.type === 'VEHICLE_UPDATE' && env.vehicles?.length) {
            setOnline((prev) => {
              const next = new Set(prev);
              for (const v of env.vehicles) {
                if (v.status === 1) next.add(v.vehicleId);
                else next.delete(v.vehicleId);
              }
              return next;
            });
          }
        } catch {}
      });
    };
    client.activate();

    return () => {
      mounted = false;
      if (refreshTimer !== null) window.clearInterval(refreshTimer);
      client?.deactivate();
    };
  }, []);

  return online.size;
}
