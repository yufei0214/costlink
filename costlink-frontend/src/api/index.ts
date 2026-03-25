import axios from 'axios'

const api = axios.create({
  baseURL: '/api',
  timeout: 30000
})

api.interceptors.request.use(config => {
  const token = localStorage.getItem('token')
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

api.interceptors.response.use(
  response => response.data,
  error => {
    if (error.response?.status === 401) {
      localStorage.removeItem('token')
      window.location.href = '/login'
    }
    return Promise.reject(error.response?.data || error)
  }
)

// Auth
export const login = (username: string, password: string) =>
  api.post('/auth/login', { username, password })

export const getCurrentUser = () =>
  api.get('/auth/me')

// User
export const updateAlipayAccount = (alipayAccount: string) =>
  api.put('/user/alipay', { alipayAccount })

export const getProfile = () =>
  api.get('/user/profile')

// Reimbursement
export const uploadImage = (file: File) => {
  const formData = new FormData()
  formData.append('file', file)
  return api.post('/reimbursement/upload', formData, {
    headers: { 'Content-Type': 'multipart/form-data' }
  })
}

export const createReimbursement = (data: any) =>
  api.post('/reimbursement', data)

export const getMyReimbursements = (page: number, size: number) =>
  api.get('/reimbursement', { params: { page, size } })

export const getReimbursementDetail = (id: number) =>
  api.get(`/reimbursement/${id}`)

// Admin
export const getStatistics = () =>
  api.get('/admin/statistics')

export const getAllReimbursements = (page: number, size: number, status?: string, username?: string, month?: string) =>
  api.get('/admin/reimbursements', { params: { page, size, status, username, month } })

export const confirmReimbursement = (id: number) =>
  api.put(`/admin/reimbursement/${id}/confirm`)

export const rejectReimbursement = (id: number, reason: string) =>
  api.put(`/admin/reimbursement/${id}/reject`, { reason })

export const markAsPaid = (id: number) =>
  api.post(`/admin/reimbursement/${id}/pay`)

export const updateAdminPayAccount = (alipayPayAccount: string) =>
  api.put('/admin/config/alipay', { alipayPayAccount })

export const getAdminConfig = () =>
  api.get('/admin/config')

export const deleteReimbursements = (ids: number[]) =>
  api.delete('/admin/reimbursements', { data: { ids } })

export const exportImages = (ids: number[]) =>
  api.post('/admin/export', { ids }, { responseType: 'blob' })

export default api
