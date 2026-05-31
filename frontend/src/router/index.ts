import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'

const routes: RouteRecordRaw[] = [
  {
    path: '/',
    redirect: '/vehicles',
  },
  {
    path: '/login',
    component: () => import('../views/Login.vue'),
    meta: { requiresAuth: false },
  },
  {
    path: '/vehicles',
    component: () => import('../views/VehicleList.vue'),
    meta: { requiresAuth: true },
  },
  {
    path: '/dashboard',
    component: () => import('../views/Dashboard.vue'),
    meta: { requiresAuth: true },
  },
]

const router = createRouter({
  history: createWebHistory(),
  routes,
})

// 路由守卫：未登录跳转登录，已登录访问登录页跳转列表
router.beforeEach((to, _from, next) => {
  const token = localStorage.getItem('token')
  const isLogged = !!token
  const requiresAuth = to.matched.some((r) => r.meta?.requiresAuth)

  if (requiresAuth && !isLogged) {
    next({ path: '/login', query: { redirect: to.fullPath } })
  } else if (to.path === '/login' && isLogged) {
    next('/vehicles')
  } else {
    next()
  }
})

export default router
