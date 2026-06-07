import { useEffect, useRef, useState } from 'react';
import SockJS from 'sockjs-client';
import { Client, type IMessage } from '@stomp/stompjs';
import type { AlertEnvelope, AlertItem } from '../types';

const WS_URL = '/ws';

/**
 * 订阅 /topic/alerts 的 STOMP 客户端 hook
 * 复用与 useVehicleSocket 相同的 SockJS + token 注入模式
 */
export function useAlertsSocket(onAlert: (alert: AlertItem) => void) {
  const onAlertRef = useRef(onAlert);
  onAlertRef.current = onAlert;

  const [wsConnected, setWsConnected] = useState(false);
  const [wsError, setWsError] = useState<string | null>(null);

  useEffect(() => {
    const token = localStorage.getItem('token');
    const url = token ? `${WS_URL}?token=${encodeURIComponent(token)}` : WS_URL;

    const client = new Client({
      webSocketFactory: () => new SockJS(url) as unknown as WebSocket,
      reconnectDelay: 5000,
      heartbeatIncoming: 10000,
      heartbeatOutgoing: 10000,
      debug: () => {},
    });

    client.onConnect = () => {
      setWsConnected(true);
      setWsError(null);
      client.subscribe('/topic/alerts', (msg: IMessage) => {
        try {
          const env = JSON.parse(msg.body) as AlertEnvelope;
          if (env.type === 'ALERT' && env.alert) {
            onAlertRef.current(env.alert);
          }
        } catch {
          /* ignore */
        }
      });
    };
    client.onWebSocketClose = () => setWsConnected(false);
    client.onStompError = (frame) => {
      setWsError(frame.headers['message'] ?? 'STOMP error');
      setWsConnected(false);
    };

    client.activate();
    return () => {
      client.deactivate();
    };
  }, []);

  return { wsConnected, wsError };
}
