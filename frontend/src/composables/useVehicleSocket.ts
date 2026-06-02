import { ref } from 'vue'
import { Client, type IFrame, type IMessage } from '@stomp/stompjs'
import SockJS from 'sockjs-client'
import type { VehicleUpdateEnvelope } from '../types'

/**
 * 创建 STOMP over SockJS 客户端并订阅 /topic/vehicles
 * 安全修复（MUL-39）：Ws 握手时携带 JWT token，后端 StompAuthInterceptor 校验
 */
export function useVehicleSocket(onMessage: (envelope: VehicleUpdateEnvelope) => void) {
  /** WebSocket 连接状态 */
  const wsConnected = ref(false)
  /** WebSocket 错误信息 */
  const wsError = ref<string | null>(null)

  const client = new Client({
    // 安全修复：在 SockJS URL 中带上 token 参数，后端 StompAuthInterceptor 会读取
    webSocketFactory: () => {
      const token = localStorage.getItem('token')
      const url = token ? `/ws?token=${encodeURIComponent(token)}` : '/ws'
      return new SockJS(url)
    },
    reconnectDelay: 5000, // 断线后 5 秒重连
    heartbeatIncoming: 10000,
    heartbeatOutgoing: 10000,
    debug: (msg: string) => {
      if (import.meta.env.DEV) {
        console.debug('[STOMP]', msg)
      }
    },

    onConnect: (_frame: IFrame) => {
      wsConnected.value = true
      wsError.value = null

      // 订阅车辆实时位置推送
      client.subscribe('/topic/vehicles', (msg: IMessage) => {
        try {
          const envelope = JSON.parse(msg.body) as VehicleUpdateEnvelope
          if (envelope.type === 'VEHICLE_UPDATE') {
            onMessage(envelope)
          }
        } catch {
          console.warn('[STOMP] 无法解析车辆消息:', msg.body)
        }
      })
    },

    onDisconnect: () => {
      wsConnected.value = false
    },

    onStompError: (frame: IFrame) => {
      wsConnected.value = false
      wsError.value = `STOMP 协议错误: ${frame.headers['message'] || '未知'}`
      console.error('[STOMP] 错误:', frame)
    },

    onWebSocketClose: () => {
      wsConnected.value = false
    },

    onWebSocketError: () => {
      wsConnected.value = false
      wsError.value = 'WebSocket 连接失败，请检查后端是否运行'
    },
  })

  function connect(): void {
    if (client.active) return
    wsError.value = null
    client.activate()
  }

  function disconnect(): void {
    client.deactivate()
  }

  return { client, connect, disconnect, wsConnected, wsError }
}
