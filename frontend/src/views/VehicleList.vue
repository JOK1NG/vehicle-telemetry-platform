<script setup lang="ts">
import { ref, reactive, onMounted, computed } from 'vue'
import { ElMessage } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { useAuthStore } from '../stores/auth'
import { vehicleApi, type VehicleCreateRequest, type VehicleUpdateRequest } from '../api/vehicle'
import type { Vehicle } from '../types'

const authStore = useAuthStore()

// 列表状态
const tableData = ref<Vehicle[]>([])
const total = ref(0)
const currentPage = ref(1)
const pageSize = ref(10)
const loading = ref(false)
const submitting = ref(false)

// 对话框
const dialogVisible = ref(false)
const dialogMode = ref<'create' | 'edit' | 'view'>('create')
const dialogTitle = computed(() => {
  if (dialogMode.value === 'create') return '新增车辆'
  if (dialogMode.value === 'edit') return '编辑车辆'
  return '车辆详情'
})
const isReadOnly = computed(() => dialogMode.value === 'view')

const formRef = ref<FormInstance>()
const form = reactive({
  id: 0,
  plateNo: '',
  vin: '',
  model: '',
})
const formRules = reactive<FormRules>({
  plateNo: [{ required: true, message: '车牌号不能为空', trigger: 'blur' }],
})

const isAdmin = computed(() => authStore.isAdmin)

// 加载列表
const fetchList = async () => {
  loading.value = true
  try {
    const page = await vehicleApi.list(currentPage.value, pageSize.value)
    tableData.value = page.records || []
    total.value = page.total || 0
  } catch (e) {
    // 拦截器已提示
  } finally {
    loading.value = false
  }
}

// 分页变化
const handlePageChange = (page: number) => {
  currentPage.value = page
  fetchList()
}
const handleSizeChange = (size: number) => {
  pageSize.value = size
  currentPage.value = 1
  fetchList()
}

// 打开对话框
const openDialog = (mode: 'create' | 'edit' | 'view', row?: Vehicle) => {
  dialogMode.value = mode
  if (row) {
    form.id = row.id
    form.plateNo = row.plateNo || ''
    form.vin = row.vin || ''
    form.model = row.model || ''
  } else {
    form.id = 0
    form.plateNo = ''
    form.vin = ''
    form.model = ''
  }
  dialogVisible.value = true
  // 下一帧重置表单验证
  setTimeout(() => {
    formRef.value?.clearValidate()
  }, 0)
}

const closeDialog = () => {
  dialogVisible.value = false
  formRef.value?.resetFields()
}

// 提交表单（新增/编辑）
const handleSubmit = async () => {
  if (!formRef.value) return
  await formRef.value.validate(async (valid) => {
    if (!valid) return
    submitting.value = true
    try {
      if (dialogMode.value === 'create') {
        const payload: VehicleCreateRequest = {
          plateNo: form.plateNo.trim(),
          vin: form.vin || undefined,
          model: form.model || undefined,
        }
        await vehicleApi.create(payload)
        ElMessage.success('新增成功')
      } else if (dialogMode.value === 'edit') {
        const payload: VehicleUpdateRequest = {
          plateNo: form.plateNo.trim(),
          vin: form.vin || undefined,
          model: form.model || undefined,
        }
        await vehicleApi.update(form.id, payload)
        ElMessage.success('编辑成功')
      }
      closeDialog()
      await fetchList()
    } catch (e: any) {
      // 拦截器或业务错误已提示
    } finally {
      submitting.value = false
    }
  })
}

// 查看详情
const handleView = (row: Vehicle) => {
  openDialog('view', row)
}

// 编辑
const handleEdit = (row: Vehicle) => {
  openDialog('edit', row)
}

// 删除（由 el-popconfirm 触发，已确认）
const handleDelete = async (row: Vehicle) => {
  try {
    await vehicleApi.remove(row.id)
    ElMessage.success('删除成功')
    await fetchList()
  } catch (e) {
    // 拦截器已处理错误提示
  }
}

// 刷新
const handleRefresh = () => {
  fetchList()
}

// 新增
const handleCreate = () => {
  openDialog('create')
}

onMounted(() => {
  fetchList()
})
</script>

<template>
  <div class="page">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>车辆列表</span>
          <div class="header-actions">
            <el-button size="small" @click="handleRefresh" :loading="loading">刷新</el-button>
            <el-button
              v-if="isAdmin"
              type="primary"
              size="small"
              @click="handleCreate"
            >
              新增车辆
            </el-button>
          </div>
        </div>
      </template>

      <el-table
        v-loading="loading"
        :data="tableData"
        border
        style="width: 100%"
        :empty-text="isAdmin ? '暂无车辆，点击「新增车辆」添加' : '暂无车辆数据'"
      >
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="plateNo" label="车牌号" min-width="140" />
        <el-table-column prop="vin" label="VIN" min-width="160" />
        <el-table-column prop="model" label="车型" min-width="140" />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'info'">
              {{ row.status === 1 ? '在线' : '离线' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="创建时间" width="170">
          <template #default="{ row }">
            {{ row.createdAt ? row.createdAt.replace('T', ' ').slice(0, 19) : '-' }}
          </template>
        </el-table-column>
        <el-table-column label="操作" width="220" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="handleView(row)">查看</el-button>
            <template v-if="isAdmin">
              <el-button link type="warning" @click="handleEdit(row)">编辑</el-button>
              <el-popconfirm
                title="确认删除该车辆？"
                confirm-button-text="删除"
                cancel-button-text="取消"
                @confirm="handleDelete(row)"
              >
                <template #reference>
                  <el-button link type="danger">删除</el-button>
                </template>
              </el-popconfirm>
            </template>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination">
        <el-pagination
          v-model:current-page="currentPage"
          v-model:page-size="pageSize"
          :total="total"
          :page-sizes="[5, 10, 20]"
          layout="total, sizes, prev, pager, next, jumper"
          @current-change="handlePageChange"
          @size-change="handleSizeChange"
        />
      </div>
    </el-card>

    <!-- 新增/编辑/详情 对话框 -->
    <el-dialog
      v-model="dialogVisible"
      :title="dialogTitle"
      width="520px"
      :close-on-click-modal="false"
      @close="closeDialog"
    >
      <el-form
        ref="formRef"
        :model="form"
        :rules="formRules"
        label-width="80px"
        :disabled="isReadOnly"
      >
        <el-form-item label="车牌号" prop="plateNo">
          <el-input v-model="form.plateNo" placeholder="如：沪A12345" maxlength="32" />
        </el-form-item>
        <el-form-item label="VIN" prop="vin">
          <el-input v-model="form.vin" placeholder="车架号（可选）" maxlength="32" />
        </el-form-item>
        <el-form-item label="车型" prop="model">
          <el-input v-model="form.model" placeholder="如：Model 3（可选）" maxlength="64" />
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="closeDialog">取消</el-button>
        <el-button
          v-if="!isReadOnly"
          type="primary"
          :loading="submitting"
          @click="handleSubmit"
        >
          {{ dialogMode === 'create' ? '新增' : '保存' }}
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.page {
  max-width: 1100px;
  margin: 0 auto;
}
.card-header {
  font-size: 16px;
  font-weight: 500;
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.header-actions {
  display: flex;
  gap: 8px;
}
.pagination {
  margin-top: 16px;
  display: flex;
  justify-content: flex-end;
}
</style>
