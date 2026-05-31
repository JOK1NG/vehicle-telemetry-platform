<template>
  <div class="page">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>监控大屏（实时地图 + 指标）</span>
        </div>
      </template>
      <p>占位页面 - M2 阶段集成高德地图组件与 ECharts 实时数据大屏</p>
      <div class="map-placeholder">
        <el-empty description="高德地图容器预留位置 (components/map)" />
      </div>
    </el-card>
  </div>
</template>

<style scoped>
.page {
  max-width: 1200px;
  margin: 0 auto;
}
.card-header {
  font-size: 16px;
  font-weight: 500;
}
.map-placeholder {
  height: 420px;
  background: #fafafa;
  border: 1px dashed #dcdfe6;
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
}
</style>
