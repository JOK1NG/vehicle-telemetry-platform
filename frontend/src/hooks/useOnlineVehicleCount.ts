import { useEffect, useState } from 'react';
import SockJS from 'sockjs-client';
import { Client, type IMessage } from '@stomp/stompjs';
import { realtimeApi } from '../api/realtime';
import type { VehicleUpdateData } from '../types';

const WS_URL = '/ws';

/**
 * 订阅 /topic/vehicles 维护一个 online vehicleId Set
 * - 启动时 GET /api/vehicles/snapshot 拿基线
 * - 每次推送把集合里所有 vehicleId 都标为在线
 * - 5 分钟没收到该车推送 → 移出集合（兜底；正常情况后端 redis TTL 10s 会让车掉出 online 集合）
 */
export function useOnlineVehicleCount(): number {
  const [online, setOnline] = useState<Set<number>>(new Set());

  useEffect(() => {
    let mounted = true;
    let client: Client | null = null;

    realtimeApi.snapshot().then((list) => {
      if (!mounted) return;
      setOnline(new Set(list.map((v) => v.vehicleId)));
    }).catch(() => {});

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
      client?.deactivate();
    };
  }, []);

  return online.size;
}
