import { defineStore } from 'pinia'

export const useVehicleStore = defineStore('vehicle', {
  state: () => ({
    list: [] as Array<{ id: number; plateNo: string; status: number }>,
    loading: false,
  }),
  actions: {
    async fetchList() {
      this.loading = true
      try {
        this.list = []
      } finally {
        this.loading = false
      }
    },
  },
})
