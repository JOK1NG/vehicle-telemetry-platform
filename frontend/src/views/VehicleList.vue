<template>
  <div class="page">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>车辆列表</span>
        </div>
      </template>
      <p>占位页面 - M1 阶段实现车辆台账 CRUD 表格与分页</p>
      <el-table :data="[]" border style="width: 100%">
        <el-table-column prop="plateNo" label="车牌号" />
        <el-table-column prop="status" label="状态" />
        <el-table-column label="操作" width="200" />
      </el-table>
    </el-card>
  </div>
</template>

<style scoped>
.page {
  max-width: 960px;
  margin: 0 auto;
}
.card-header {
  font-size: 16px;
  font-weight: 500;
}
</style>
