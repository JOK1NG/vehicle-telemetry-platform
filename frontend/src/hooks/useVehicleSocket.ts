import { useEffect, useRef, useState } from 'react';
import { Client, type IFrame, type IMessage } from '@stomp/stompjs';
import SockJS from 'sockjs-client';
import type { VehicleUpdateEnvelope } from '../types';

/**
 * 创建 STOMP over SockJS 客户端并订阅 /topic/vehicles
 * 安全：Ws 握手时携带 JWT token，后端 StompAuthInterceptor 校验
 */
export function useVehicleSocket(onMessage: (envelope: VehicleUpdateEnvelope) => void) {
  const [wsConnected, setWsConnected] = useState(false);
  const [wsError, setWsError] = useState<string | null>(null);
  const clientRef = useRef<Client | null>(null);
  const onMessageRef = useRef(onMessage);
  onMessageRef.current = onMessage;

  useEffect(() => {
    const client = new Client({
      webSocketFactory: () => {
        const token = localStorage.getItem('token');
        const url = token ? `/ws?token=${encodeURIComponent(token)}` : '/ws';
        return new SockJS(url);
      },
      reconnectDelay: 5000,
      heartbeatIncoming: 10000,
      heartbeatOutgoing: 10000,
      debug: (msg: string) => {
        if (import.meta.env.DEV) {
          console.debug('[STOMP]', msg);
        }
      },
      onConnect: (_frame: IFrame) => {
        setWsConnected(true);
        setWsError(null);
        client.subscribe('/topic/vehicles', (msg: IMessage) => {
          try {
            const envelope = JSON.parse(msg.body) as VehicleUpdateEnvelope;
            if (envelope.type === 'VEHICLE_UPDATE') {
              onMessageRef.current(envelope);
            }
          } catch {
            console.warn('[STOMP] 无法解析车辆消息:', msg.body);
          }
        });
      },
      onDisconnect: () => {
        setWsConnected(false);
      },
      onStompError: (frame: IFrame) => {
        setWsConnected(false);
        setWsError(`STOMP 协议错误: ${frame.headers['message'] || '未知'}`);
        console.error('[STOMP] 错误:', frame);
      },
      onWebSocketClose: () => {
        setWsConnected(false);
      },
      onWebSocketError: () => {
        setWsConnected(false);
        setWsError('WebSocket 连接失败，请检查后端是否运行');
      },
    });

    clientRef.current = client;
    client.activate();

    return () => {
      client.deactivate();
      clientRef.current = null;
    };
  }, []);

  const connect = () => {
    if (clientRef.current && !clientRef.current.active) {
      setWsError(null);
      clientRef.current.activate();
    }
  };
  const disconnect = () => {
    clientRef.current?.deactivate();
  };

  return { client: clientRef.current, connect, disconnect, wsConnected, wsError };
}
