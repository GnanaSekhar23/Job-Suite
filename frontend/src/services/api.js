import axios from 'axios'

const api = axios.create({
  baseURL: '/api',
  headers: { 'Content-Type': 'application/json' }
})

api.interceptors.request.use(config => {
  const token = localStorage.getItem('accessToken')
  if (token) config.headers.Authorization = 'Bearer ' + token
  return config
})

api.interceptors.response.use(
  response => response,
  error => {
    if (error.response?.status === 401) {
      localStorage.removeItem('accessToken')
      localStorage.removeItem('refreshToken')
      window.location.href = '/login'
    }
    return Promise.reject(error)
  }
)

export const authApi = {
  register: (data) => api.post('/auth/register', data),
  login: (data) => api.post('/auth/login', data),
  googleLogin: (idToken) => api.post('/auth/google', { idToken }),
  logout: () => api.post('/auth/logout'),
  me: () => api.get('/auth/me')
}

export const preferencesApi = {
  save: (data) => api.post('/preferences', data),
  get: () => api.get('/preferences'),
  fetchJobs: () => api.post('/preferences/fetch-jobs'),
  tailorJobs: () => api.post('/preferences/tailor-jobs')
}

export const resumeApi = {
  upload: (formData) => api.post('/resumes', formData, {
    headers: { 'Content-Type': 'multipart/form-data' }
  }),
  uploadLatex: (formData) => api.post('/resumes/latex', formData, {
    headers: { 'Content-Type': 'multipart/form-data' }
  }),
  getActive: () => api.get('/resumes/active'),
  getAll: () => api.get('/resumes')
}

export const dashboardApi = {
  getAllJobs: () => api.get('/dashboard/jobs'),
  getJobsByStatus: (status) => api.get('/dashboard/jobs/status/' + status),
  getJobDetail: (id) => api.get('/dashboard/jobs/' + id),
  updateStatus: (id, data) => api.patch('/dashboard/jobs/' + id + '/status', data),
  getStats: () => api.get('/dashboard/stats')
}

export default api
