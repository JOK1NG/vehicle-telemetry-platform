<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref, shallowRef } from 'vue'
import { ElMessage } from 'element-plus'
import { Connection, Location, Refresh, WarningFilled } from '@element-plus/icons-vue'
import { realtimeApi } from '../api/realtime'
import { useAMap } from '../composables/useAMap'
import { useVehicleSocket } from '../composables/useVehicleSocket'
import type { VehicleSnapshot, VehicleUpdateData, VehicleUpdateEnvelope } from '../types'

type RealtimeVehicle = VehicleUpdateData & { lastTs?: string }

const mapContainerId = 'dashboard-amap'

const vehicles = reactive(new Map<number, RealtimeVehicle>())
const markers = new Map<number, any>()
const markerElements = new Map<number, HTMLElement>()

const map = shallowRef<any>(null)
const mapReady = ref(false)
const snapshotLoading = ref(false)
const selectedVehicleId = ref<number | null>(null)
const lastUpdatedAt = ref('-')

const {
  loading: amapLoading,
  load: loadAMap,
  amapReady,
  amapLoadError,
  createMap,
  getAMap,
} = useAMap()

const socket = useVehicleSocket(handleVehicleUpdate)

const vehicleList = computed(() =>
  Array.from(vehicles.values()).sort((a, b) => a.vehicleId - b.vehicleId),
)

const onlineCount = computed(() => vehicleList.value.filter((item) => item.status === 1).length)

const averageSpeed = computed(() => {
  if (!vehicleList.value.length) return 0
  const total = vehicleList.value.reduce((sum, item) => sum + item.speed, 0)
  return total / vehicleList.value.length
})

const averageBattery = computed(() => {
  if (!vehicleList.value.length) return 0
  const total = vehicleList.value.reduce((sum, item) => sum + item.battery, 0)
  return total / vehicleList.value.length
})

const connectionTagType = computed(() => (socket.wsConnected.value ? 'success' : 'danger'))
const connectionText = computed(() => (socket.wsConnected.value ? '实时连接正常' : '实时连接断开'))

const mapOverlayText = computed(() => {
  if (amapLoadError.value) return amapLoadError.value
  if (amapLoading.value) return '地图加载中'
  if (!vehicleList.value.length) return '暂无在线车辆，启动模拟器或等待 MQTT 数据接入'
  return ''
})

onMounted(async () => {
  await initializeMap()
  await fetchSnapshot()
  socket.connect()
})

onBeforeUnmount(() => {
  socket.disconnect()
  clearMarkers()
  if (map.value?.destroy) {
    map.value.destroy()
  }
})

async function initializeMap() {
  await nextTick()
  await loadAMap()

  if (!amapReady.value) return

  const mapInstance = createMap(mapContainerId, {
    zoom: 12,
    center: [121.473701, 31.230416],
  })

  if (!mapInstance) return

  map.value = mapInstance
  mapReady.value = true
  renderMarkers()
}

async function fetchSnapshot(showSuccess = false) {
  snapshotLoading.value = true
  try {
    const snapshots = await realtimeApi.snapshot()
    vehicles.clear()
    clearMarkers()
    applyVehicleUpdates(snapshots)
    if (showSuccess) {
      ElMessage.success('实时快照已刷新')
    }
  } catch (error) {
    console.error('[Dashboard] 获取车辆快照失败:', error)
    ElMessage.error('获取车辆实时快照失败')
  } finally {
    snapshotLoading.value = false
  }
}

function handleVehicleUpdate(envelope: VehicleUpdateEnvelope) {
  applyVehicleUpdates(envelope.vehicles, envelope.timestamp)
}

function applyVehicleUpdates(items: Array<VehicleUpdateData | VehicleSnapshot>, timestamp?: string) {
  if (!items.length) return

  for (const item of items) {
    const existing = vehicles.get(item.vehicleId)
    const lastTs = 'lastTs' in item ? item.lastTs : timestamp
    const merged: RealtimeVehicle = {
      ...existing,
      ...item,
      lastTs: lastTs ?? existing?.lastTs,
    }

    vehicles.set(item.vehicleId, merged)
    upsertMarker(merged)
  }

  lastUpdatedAt.value = formatTime(timestamp ?? new Date().toISOString())
}

function upsertMarker(vehicle: RealtimeVehicle) {
  if (!map.value || !mapReady.value) return

  const AMap = getAMap()
  if (!AMap) return

  let marker = markers.get(vehicle.vehicleId)
  let content = markerElements.get(vehicle.vehicleId)

  if (!content) {
    content = document.createElement('div')
    markerElements.set(vehicle.vehicleId, content)
  }

  updateMarkerContent(content, vehicle)

  const position = [vehicle.lng, vehicle.lat]

  if (!marker) {
    marker = new AMap.Marker({
      position,
      content,
      title: vehicle.plateNo || `车辆 ${vehicle.vehicleId}`,
      offset: new AMap.Pixel(-18, -18),
    })
    marker.setMap(map.value)
    markers.set(vehicle.vehicleId, marker)
    return
  }

  marker.setPosition(position)
  marker.setContent(content)
}

function updateMarkerContent(content: HTMLElement, vehicle: RealtimeVehicle) {
  content.className =
    selectedVehicleId.value === vehicle.vehicleId ? 'vehicle-marker is-selected' : 'vehicle-marker'

  const arrow = document.createElement('span')
  arrow.className = 'vehicle-marker__arrow'
  arrow.style.transform = `rotate(${vehicle.heading}deg)`

  const label = document.createElement('span')
  label.className = 'vehicle-marker__label'
  label.textContent = vehicle.plateNo || `#${vehicle.vehicleId}`

  content.replaceChildren(arrow, label)
}

function renderMarkers() {
  vehicleList.value.forEach((vehicle) => upsertMarker(vehicle))
  fitMapToVehicles()
}

function clearMarkers() {
  markers.forEach((marker) => marker.setMap(null))
  markers.clear()
  markerElements.clear()
}

function focusVehicle(vehicle: RealtimeVehicle) {
  selectedVehicleId.value = vehicle.vehicleId
  upsertMarker(vehicle)
  vehicleList.value.forEach((item) => {
    if (item.vehicleId !== vehicle.vehicleId) {
      upsertMarker(item)
    }
  })

  if (!map.value) return

  map.value.setZoomAndCenter(16, [vehicle.lng, vehicle.lat])
}

function fitMapToVehicles() {
  if (!map.value || markers.size === 0) return
  map.value.setFitView(Array.from(markers.values()), false, [72, 72, 72, 72], 15)
}

function formatNumber(value: number, digits = 1) {
  return Number.isFinite(value) ? value.toFixed(digits) : '-'
}

function formatTime(value?: string) {
  if (!value) return '-'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value
  return date.toLocaleTimeString('zh-CN', { hour12: false })
}
</script>

<template>
  <div class="dashboard-page">
    <section class="dashboard-toolbar">
      <div>
        <h1>监控大屏</h1>
        <p>车辆快照 + WebSocket 实时更新，坐标全链路使用 GCJ-02。</p>
      </div>
      <div class="toolbar-actions">
        <el-tag :type="connectionTagType" effect="light">
          <el-icon><Connection /></el-icon>
          {{ connectionText }}
        </el-tag>
        <el-button :icon="Refresh" :loading="snapshotLoading" @click="fetchSnapshot(true)">
          刷新
        </el-button>
      </div>
    </section>

    <section class="metric-grid">
      <div class="metric-item">
        <el-icon><Location /></el-icon>
        <span>在线车辆</span>
        <strong>{{ onlineCount }}</strong>
      </div>
      <div class="metric-item">
        <el-icon><Connection /></el-icon>
        <span>平均速度</span>
        <strong>{{ formatNumber(averageSpeed) }} km/h</strong>
      </div>
      <div class="metric-item">
        <el-icon><WarningFilled /></el-icon>
        <span>平均电量</span>
        <strong>{{ formatNumber(averageBattery) }}%</strong>
      </div>
      <div class="metric-item">
        <el-icon><Refresh /></el-icon>
        <span>最近更新</span>
        <strong>{{ lastUpdatedAt }}</strong>
      </div>
    </section>

    <section class="dashboard-content">
      <div class="map-panel">
        <div class="panel-header">
          <div>
            <h2>实时车辆位置</h2>
            <p>订阅 /topic/vehicles，按车辆 ID 更新地图标记。</p>
          </div>
          <el-button size="small" text :disabled="!vehicleList.length" @click="fitMapToVehicles">
            适配视野
          </el-button>
        </div>
        <div class="map-shell" v-loading="amapLoading || snapshotLoading">
          <div :id="mapContainerId" class="map-canvas" />
          <div v-if="mapOverlayText" class="map-overlay">
            {{ mapOverlayText }}
          </div>
        </div>
        <p v-if="socket.wsError" class="inline-error">{{ socket.wsError }}</p>
      </div>

      <aside class="vehicle-panel">
        <div class="panel-header compact">
          <div>
            <h2>在线车辆</h2>
            <p>{{ vehicleList.length }} 台</p>
          </div>
        </div>
        <el-table
          :data="vehicleList"
          height="488"
          size="small"
          empty-text="暂无在线车辆"
          highlight-current-row
          @row-click="focusVehicle"
        >
          <el-table-column prop="plateNo" label="车牌" min-width="104">
            <template #default="{ row }">
              <span class="plate-cell">{{ row.plateNo || `#${row.vehicleId}` }}</span>
            </template>
          </el-table-column>
          <el-table-column label="速度" width="92" align="right">
            <template #default="{ row }">{{ formatNumber(row.speed) }}</template>
          </el-table-column>
          <el-table-column label="电量" width="78" align="right">
            <template #default="{ row }">{{ formatNumber(row.battery, 0) }}%</template>
          </el-table-column>
          <el-table-column label="更新" width="96" align="right">
            <template #default="{ row }">{{ formatTime(row.lastTs) }}</template>
          </el-table-column>
        </el-table>
      </aside>
    </section>
  </div>
</template>

<style scoped>
.dashboard-page {
  max-width: 1440px;
  margin: 0 auto;
}

.dashboard-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 16px;
}

.dashboard-toolbar h1,
.panel-header h2 {
  margin: 0;
  color: #303133;
  font-weight: 650;
  letter-spacing: 0;
}

.dashboard-toolbar h1 {
  font-size: 24px;
  line-height: 32px;
}

.dashboard-toolbar p,
.panel-header p {
  margin: 4px 0 0;
  color: #6b7280;
  font-size: 13px;
}

.toolbar-actions {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
  justify-content: flex-end;
}

.toolbar-actions :deep(.el-tag) {
  display: inline-flex;
  align-items: center;
  gap: 4px;
}

.metric-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
  margin-bottom: 16px;
}

.metric-item {
  min-height: 86px;
  padding: 16px;
  border: 1px solid #ebeef5;
  border-radius: 8px;
  background: #fff;
  display: grid;
  grid-template-columns: 28px 1fr;
  grid-template-rows: auto auto;
  gap: 4px 10px;
  align-items: center;
}

.metric-item .el-icon {
  grid-row: 1 / span 2;
  width: 28px;
  height: 28px;
  border-radius: 7px;
  color: #0f766e;
  background: #e6fffb;
}

.metric-item span {
  color: #6b7280;
  font-size: 13px;
}

.metric-item strong {
  color: #303133;
  font-size: 22px;
  line-height: 28px;
  font-weight: 650;
}

.dashboard-content {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 380px;
  gap: 16px;
  align-items: stretch;
}

.map-panel,
.vehicle-panel {
  min-width: 0;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  background: #fff;
}

.map-panel {
  padding: 16px;
}

.vehicle-panel {
  overflow: hidden;
}

.panel-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 12px;
}

.panel-header h2 {
  font-size: 16px;
  line-height: 24px;
}

.panel-header.compact {
  padding: 16px 16px 0;
}

.map-shell {
  position: relative;
  height: 520px;
  overflow: hidden;
  border: 1px solid #dfe5ee;
  border-radius: 8px;
  background: #eef3f8;
}

.map-canvas {
  width: 100%;
  height: 100%;
}

.map-overlay {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24px;
  text-align: center;
  color: #606266;
  background: rgba(255, 255, 255, 0.78);
  font-size: 14px;
}

.inline-error {
  margin: 10px 0 0;
  color: #c45656;
  font-size: 13px;
}

.plate-cell {
  color: #303133;
  font-weight: 600;
}

:global(.vehicle-marker) {
  position: relative;
  width: 36px;
  height: 36px;
  pointer-events: none;
}

:global(.vehicle-marker__arrow) {
  position: absolute;
  left: 8px;
  top: 6px;
  width: 20px;
  height: 20px;
  border-radius: 6px 6px 10px 10px;
  background: #0f766e;
  box-shadow: 0 8px 16px rgba(15, 118, 110, 0.28);
}

:global(.vehicle-marker__arrow::before) {
  content: '';
  position: absolute;
  left: 5px;
  top: -7px;
  border-right: 5px solid transparent;
  border-bottom: 9px solid #0f766e;
  border-left: 5px solid transparent;
}

:global(.vehicle-marker__label) {
  position: absolute;
  left: 50%;
  top: 33px;
  transform: translateX(-50%);
  max-width: 96px;
  overflow: hidden;
  padding: 2px 6px;
  border-radius: 4px;
  color: #303133;
  background: rgba(255, 255, 255, 0.94);
  box-shadow: 0 4px 12px rgba(31, 41, 55, 0.12);
  font-size: 12px;
  line-height: 18px;
  white-space: nowrap;
  text-overflow: ellipsis;
}

:global(.vehicle-marker.is-selected .vehicle-marker__arrow) {
  background: #f59e0b;
  box-shadow: 0 8px 18px rgba(245, 158, 11, 0.34);
}

:global(.vehicle-marker.is-selected .vehicle-marker__arrow::before) {
  border-bottom-color: #f59e0b;
}

@media (max-width: 1100px) {
  .metric-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .dashboard-content {
    grid-template-columns: 1fr;
  }

  .vehicle-panel {
    min-height: 360px;
  }
}

@media (max-width: 720px) {
  .dashboard-toolbar {
    align-items: flex-start;
    flex-direction: column;
  }

  .toolbar-actions {
    width: 100%;
    justify-content: flex-start;
  }

  .metric-grid {
    grid-template-columns: 1fr;
  }

  .map-shell {
    height: 420px;
  }
}
</style>
