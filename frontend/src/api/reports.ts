import api from './client'
import type { Report } from '@/types'
import { useAuthStore } from '@/store/authStore'

export const reportsApi = {
  generate: (propertyId: number) =>
    api.post<Report>('/reports/generate', { propertyId }).then(r => r.data),

  list: (propertyId: number) =>
    api.get<Report[]>('/reports', { params: { propertyId } }).then(r => r.data),

  get: (id: number) =>
    api.get<Report>(`/reports/${id}`).then(r => r.data),

  download: async (id: number) => {
    const token = useAuthStore.getState().accessToken
    const response = await fetch(`/api/reports/${id}/download`, {
      headers: { Authorization: `Bearer ${token}` }
    })
    if (!response.ok) throw new Error('Download failed')
    const blob = await response.blob()
    const url = window.URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = `site-survey-report-${id}.pdf`
    document.body.appendChild(a)
    a.click()
    document.body.removeChild(a)
    window.URL.revokeObjectURL(url)
  }
}