import { createRouter, createWebHistory } from 'vue-router'

const routes = [
  {
    path: '/',
    redirect: '/dashboard',
  },
  {
    path: '/login',
    component: () => import('../views/Login.vue'),
  },
  {
    path: '/vehicles',
    component: () => import('../views/VehicleList.vue'),
  },
  {
    path: '/dashboard',
    component: () => import('../views/Dashboard.vue'),
  },
]

const router = createRouter({
  history: createWebHistory(),
  routes,
})

export default router
