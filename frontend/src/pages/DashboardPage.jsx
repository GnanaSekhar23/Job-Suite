import { useState, useEffect } from 'react'
import { useNavigate } from 'react-router-dom'
import { useAuth } from '../context/AuthContext'
import { dashboardApi, preferencesApi } from '../services/api'
import JSZip from 'jszip'
import { saveAs } from 'file-saver'

const STATUS_COLORS = {
  DISCOVERED: 'bg-gray-500', FILTERING: 'bg-yellow-600',
  TAILORING: 'bg-blue-500', READY: 'bg-green-500',
  MANUAL_APPLY: 'bg-purple-500', FAILED: 'bg-red-900',
}

// ─── Download helpers ─────────────────────────────────────────
function sanitizeName(name) {
  return (name || 'unknown').replace(/[^a-zA-Z0-9\s]/g, '').replace(/\s+/g, '-').toLowerCase().substring(0, 40)
}

async function fetchBlob(url) {
  const res = await fetch(url)
  if (!res.ok) throw new Error('Fetch failed: ' + url)
  return res.blob()
}

function downloadText(content, filename) {
  saveAs(new Blob([content], { type: 'text/plain;charset=utf-8' }), filename)
}

async function buildCompanyZip(job) {
  const zip = new JSZip()
  const folder = zip.folder(sanitizeName(job.companyName) + '--' + sanitizeName(job.jobTitle))

  const tasks = []
  if (job.resumePdfUrl) tasks.push(fetchBlob(job.resumePdfUrl).then(b => folder.file('resume.pdf', b)).catch(() => {}))
  if (job.coverLetterPdfUrl) tasks.push(fetchBlob(job.coverLetterPdfUrl).then(b => folder.file('cover-letter.pdf', b)).catch(() => {}))
  if (job.resumeLatex) folder.file('resume.tex', job.resumeLatex)
  if (job.coverLetterLatex) folder.file('cover-letter.tex', job.coverLetterLatex)
  folder.file('job-info.txt', [
    'Title: ' + job.jobTitle,
    'Company: ' + job.companyName,
    'Location: ' + (job.jobLocation || 'N/A'),
    'Status: ' + job.status,
    'Apply URL: ' + (job.jobUrl || 'N/A'),
    'Added: ' + (job.createdAt ? new Date(job.createdAt).toLocaleDateString() : 'N/A'),
  ].join('\n'))

  await Promise.all(tasks)
  return zip
}

// ─── Main component ───────────────────────────────────────────
export default function DashboardPage() {
  const navigate = useNavigate()
  const { user, logout } = useAuth()
  const [jobs, setJobs] = useState([])
  const [jobDetails, setJobDetails] = useState({}) // cached detail responses
  const [stats, setStats] = useState(null)
  const [loading, setLoading] = useState(true)
  const [message, setMessage] = useState({ text: '', type: 'info' })
  const [selectedJob, setSelectedJob] = useState(null)
  const [filterStatus, setFilterStatus] = useState('ALL')
  const [selectedDate, setSelectedDate] = useState('ALL')
  const [fetchingJobs, setFetchingJobs] = useState(false)
  const [tailoring, setTailoring] = useState(false)
  const [downloading, setDownloading] = useState(null) // job id or 'bulk'
  const [bulkProgress, setBulkProgress] = useState({ done: 0, total: 0 })

  useEffect(() => { loadDashboard() }, [])

  const loadDashboard = async () => {
    try {
      const [jobsRes, statsRes] = await Promise.all([
        dashboardApi.getAllJobs(),
        dashboardApi.getStats()
      ])
      setJobs(jobsRes.data)
      setStats(statsRes.data)
    } catch (err) {
      console.error('Failed to load:', err)
    } finally {
      setLoading(false)
    }
  }

  const showMsg = (text, type = 'info') => setMessage({ text, type })

  // Get or fetch full job detail (with URLs and LaTeX)
  const getDetail = async (jobId) => {
    if (jobDetails[jobId]) return jobDetails[jobId]
    const res = await dashboardApi.getJobDetail(jobId)
    setJobDetails(prev => ({ ...prev, [jobId]: res.data }))
    return res.data
  }

  // Date grouping
  const getDateLabel = (job) => job.createdAt
    ? new Date(job.createdAt).toLocaleDateString('en-US', { month: 'short', day: 'numeric', year: 'numeric' })
    : 'Unknown'

  const jobDates = [...new Set(jobs.map(getDateLabel))].sort((a, b) => new Date(b) - new Date(a))

  const getFilteredJobs = () => {
    let filtered = jobs
    if (selectedDate !== 'ALL') filtered = filtered.filter(j => getDateLabel(j) === selectedDate)
    if (filterStatus !== 'ALL') filtered = filtered.filter(j => j.status === filterStatus)
    return filtered
  }

  const filteredJobs = getFilteredJobs()

  // ─── Actions ──────────────────────────────────────────────

  const handleFetchJobs = async () => {
    setFetchingJobs(true)
    showMsg('Fetching jobs from Indeed...', 'info')
    try {
      const res = await preferencesApi.fetchJobs()
      showMsg(res.data, 'success')
      await loadDashboard()
    } catch (err) {
      showMsg(err.response?.data?.message || 'Failed to fetch jobs', 'error')
    } finally { setFetchingJobs(false) }
  }

  const handleTailorJobs = async () => {
    setTailoring(true)
    showMsg('Tailoring started. Refresh in 2-3 minutes...', 'info')
    try {
      await preferencesApi.tailorJobs()
      setTimeout(() => { loadDashboard(); setTailoring(false) }, 120000)
    } catch (err) {
      showMsg('Failed to start tailoring', 'error')
      setTailoring(false)
    }
  }

  // Open job modal
  const handleOpenJob = async (jobId) => {
    try {
      const detail = await getDetail(jobId)
      setSelectedJob(detail)
    } catch { showMsg('Failed to load job details', 'error') }
  }

  // Download single .tex file from modal
  const handleDownloadTex = (content, filename) => downloadText(content, filename)

  // Download single PDF
  const handleDownloadPdf = (url) => { if (url) window.open(url, '_blank') }

  // ─── Company folder download ──────────────────────────────
  const handleDownloadCompanyFolder = async (job, e) => {
    e.stopPropagation()
    setDownloading(job.id)
    showMsg('Building zip for ' + job.companyName + '...', 'info')
    try {
      const detail = await getDetail(job.id)
      const fullJob = { ...job, ...detail }
      const zip = await buildCompanyZip(fullJob)
      const blob = await zip.generateAsync({ type: 'blob' })
      const fname = sanitizeName(job.companyName) + '--' + sanitizeName(job.jobTitle) + '.zip'
      saveAs(blob, fname)
      showMsg('Downloaded ' + job.companyName + ' folder', 'success')
    } catch (err) {
      showMsg('Download failed: ' + err.message, 'error')
    } finally { setDownloading(null) }
  }

  // ─── Bulk download ALL visible jobs ──────────────────────
  const handleBulkDownload = async () => {
    const targets = filteredJobs.filter(j => j.hasResume || j.hasCoverLetter)
    if (targets.length === 0) { showMsg('No jobs with files in current view', 'error'); return }

    setDownloading('bulk')
    setBulkProgress({ done: 0, total: targets.length })
    showMsg('Building bulk zip for ' + targets.length + ' jobs...', 'info')

    try {
      const rootZip = new JSZip()
      const dateStr = new Date().toISOString().split('T')[0]
      const root = rootZip.folder('JobSuite-' + dateStr)

      for (let i = 0; i < targets.length; i++) {
        const job = targets[i]
        setBulkProgress({ done: i, total: targets.length })

        try {
          const detail = await getDetail(job.id)
          const fullJob = { ...job, ...detail }
          const folderName = sanitizeName(job.companyName) + '--' + sanitizeName(job.jobTitle)
          const folder = root.folder(folderName)

          const tasks = []
          if (fullJob.resumePdfUrl) tasks.push(fetchBlob(fullJob.resumePdfUrl).then(b => folder.file('resume.pdf', b)).catch(() => {}))
          if (fullJob.coverLetterPdfUrl) tasks.push(fetchBlob(fullJob.coverLetterPdfUrl).then(b => folder.file('cover-letter.pdf', b)).catch(() => {}))
          if (fullJob.resumeLatex) folder.file('resume.tex', fullJob.resumeLatex)
          if (fullJob.coverLetterLatex) folder.file('cover-letter.tex', fullJob.coverLetterLatex)
          folder.file('job-info.txt', [
            'Title: ' + job.jobTitle,
            'Company: ' + job.companyName,
            'Location: ' + (job.jobLocation || 'N/A'),
            'Apply URL: ' + (job.jobUrl || 'N/A'),
          ].join('\n'))

          await Promise.all(tasks)
        } catch (err) {
          console.warn('Skipped job', job.id, err.message)
        }
      }

      setBulkProgress({ done: targets.length, total: targets.length })
      showMsg('Compressing ' + targets.length + ' folders...', 'info')

      const blob = await rootZip.generateAsync({ type: 'blob' })
      saveAs(blob, 'JobSuite-' + dateStr + '.zip')
      showMsg('Downloaded all ' + targets.length + ' company folders!', 'success')
    } catch (err) {
      showMsg('Bulk download failed: ' + err.message, 'error')
    } finally {
      setDownloading(null)
      setBulkProgress({ done: 0, total: 0 })
    }
  }

  // ─── Delete date ──────────────────────────────────────────
  const handleDeleteDateJobs = async (dateLabel) => {
    const dateJobs = jobs.filter(j => getDateLabel(j) === dateLabel)
    if (!window.confirm('Delete all ' + dateJobs.length + ' jobs from ' + dateLabel + '? This cannot be undone.')) return
    showMsg('Deleting ' + dateJobs.length + ' jobs...', 'info')
    try {
      await dashboardApi.deleteJobsByDate(dateLabel)
      showMsg('Deleted all jobs from ' + dateLabel, 'success')
      if (selectedDate === dateLabel) setSelectedDate('ALL')
      await loadDashboard()
    } catch (err) {
      showMsg('Failed to delete: ' + (err.response?.data?.message || err.message), 'error')
    }
  }
 

  // ─── Formatting ───────────────────────────────────────────
  const formatSalary = (job) => {
    const min = job.salaryMin && job.salaryMin > 0 ? job.salaryMin : null
    const max = job.salaryMax && job.salaryMax > 0 ? job.salaryMax : null
    if (!min && !max) return null
    const fmt = (n) => '$' + Math.round(n / 1000) + 'k'
    if (min && max) return fmt(min) + ' - ' + fmt(max)
    return min ? 'From ' + fmt(min) : 'Up to ' + fmt(max)
  }

  const statusBadge = (status) => (
    <span className={'text-xs px-2 py-0.5 rounded-full text-white font-medium ' + (STATUS_COLORS[status] || 'bg-gray-500')}>
      {status.replace('_', ' ')}
    </span>
  )

  if (loading) {
    return (
      <div className="min-h-screen bg-gray-950 flex items-center justify-center">
        <div className="text-white">Loading dashboard...</div>
      </div>
    )
  }

  return (
    <div className="min-h-screen bg-gray-950 text-white">

      {/* Navbar */}
      <nav className="bg-gray-900 border-b border-gray-800 px-6 py-4 sticky top-0 z-10">
        <div className="max-w-7xl mx-auto flex items-center justify-between">
          <h1 className="text-xl font-bold">Job Suite</h1>
          <div className="flex items-center gap-4">
            <span className="text-gray-400 text-sm hidden sm:block">{user?.email}</span>
            <button onClick={() => navigate('/preferences')} className="bg-gray-700 hover:bg-gray-600 px-3 py-1.5 rounded-lg text-sm transition">Setup</button>
            <button onClick={logout} className="text-gray-400 hover:text-white text-sm transition">Logout</button>
          </div>
        </div>
      </nav>

      <div className="max-w-7xl mx-auto px-4 py-6">

        {/* Stats */}
        {stats && (
          <div className="grid grid-cols-2 gap-3 mb-6 max-w-xs">
            <div className="bg-gray-900 rounded-xl p-3 border border-gray-800 text-center">
              <div className="text-2xl font-bold text-green-400">{stats.totalReady}</div>
              <div className="text-gray-500 text-xs mt-0.5">Ready</div>
            </div>
            <div className="bg-gray-900 rounded-xl p-3 border border-gray-800 text-center">
              <div className="text-2xl font-bold text-red-400">{stats.totalFailed}</div>
              <div className="text-gray-500 text-xs mt-0.5">Failed</div>
            </div>
          </div>
        )}

        {/* Action buttons */}
        <div className="flex gap-2 mb-4 flex-wrap">
          <button onClick={handleFetchJobs} disabled={fetchingJobs}
            className="bg-blue-600 hover:bg-blue-500 disabled:bg-blue-800 px-4 py-2 rounded-lg text-sm font-medium transition">
            {fetchingJobs ? 'Fetching...' : 'Fetch New Jobs'}
          </button>
          <button onClick={handleTailorJobs} disabled={tailoring}
            className="bg-purple-600 hover:bg-purple-500 disabled:bg-purple-800 px-4 py-2 rounded-lg text-sm font-medium transition">
            {tailoring ? 'Tailoring...' : 'Tailor Resumes'}
          </button>
          <button onClick={handleBulkDownload} disabled={downloading === 'bulk'}
            className="bg-teal-700 hover:bg-teal-600 disabled:bg-teal-900 px-4 py-2 rounded-lg text-sm font-medium transition">
            {downloading === 'bulk'
              ? 'Zipping ' + bulkProgress.done + '/' + bulkProgress.total + '...'
              : 'Bulk Download All'}
          </button>
          <button onClick={loadDashboard} className="bg-gray-700 hover:bg-gray-600 px-4 py-2 rounded-lg text-sm font-medium transition">
            Refresh
          </button>
        </div>

        {/* Message */}
        {message.text && (
          <div className={'rounded-lg p-3 mb-4 text-sm border ' + (
            message.type === 'success' ? 'bg-green-500/10 border-green-500/50 text-green-400' :
            message.type === 'error' ? 'bg-red-500/10 border-red-500/50 text-red-400' :
            'bg-blue-500/10 border-blue-500/50 text-blue-400')}>
            {message.text}
          </div>
        )}

        {/* Date filter */}
        {jobDates.length > 0 && (
          <div className="mb-4">
            <p className="text-xs text-gray-500 mb-2">Filter by date:</p>
            <div className="flex gap-2 flex-wrap">
              <button onClick={() => setSelectedDate('ALL')}
                className={'px-3 py-1.5 rounded-lg text-xs font-medium transition ' +
                  (selectedDate === 'ALL' ? 'bg-blue-600 text-white' : 'bg-gray-800 text-gray-400 hover:text-white')}>
                All ({jobs.length})
              </button>
              {jobDates.map(date => {
                const count = jobs.filter(j => getDateLabel(j) === date).length
                return (
                  <div key={date} className="flex items-center gap-1">
                    <button onClick={() => setSelectedDate(date)}
                      className={'px-3 py-1.5 rounded-lg text-xs font-medium transition ' +
                        (selectedDate === date ? 'bg-blue-600 text-white' : 'bg-gray-800 text-gray-400 hover:text-white')}>
                      {date} ({count})
                    </button>
                    <button onClick={() => handleDeleteDateJobs(date)}
                      title={'Delete all jobs from ' + date}
                      className="px-2 py-1.5 rounded-lg text-xs bg-red-900/40 hover:bg-red-800/60 text-red-400 transition">
                      ✕
                    </button>
                  </div>
                )
              })}
            </div>
          </div>
        )}

        {/* Status filter */}
        {jobs.length > 0 && (
          <div className="flex gap-1 mb-4 overflow-x-auto pb-1">
            <button onClick={() => setFilterStatus('ALL')}
              className={'px-3 py-1.5 rounded-lg text-xs font-medium transition whitespace-nowrap ' +
                (filterStatus === 'ALL' ? 'bg-gray-700 text-white' : 'text-gray-400 hover:text-white')}>
              All statuses
            </button>
            {['READY', 'MANUAL_APPLY', 'FAILED', 'TAILORING', 'DISCOVERED'].map(s => {
              const count = filteredJobs.filter(j => j.status === s).length
              if (!count && filterStatus !== s) return null
              return (
                <button key={s} onClick={() => setFilterStatus(s)}
                  className={'px-3 py-1.5 rounded-lg text-xs font-medium transition whitespace-nowrap ' +
                    (filterStatus === s ? 'bg-gray-700 text-white' : 'text-gray-400 hover:text-white')}>
                  {s.replace('_', ' ')} ({jobs.filter(j => j.status === s).length})
                </button>
              )
            })}
          </div>
        )}

        {/* Jobs */}
        {filteredJobs.length === 0 ? (
          <div className="text-center py-20">
            <div className="text-gray-500 text-4xl mb-4">💼</div>
            <div className="text-gray-400 text-lg mb-2">{jobs.length === 0 ? 'No jobs yet' : 'No jobs match this filter'}</div>
            {jobs.length === 0 && <p className="text-gray-600 text-sm">Go to Setup → save preferences → upload resume → Fetch New Jobs</p>}
          </div>
        ) : (
          <div className="grid gap-3">
            {filteredJobs.map(job => (
              <div key={job.id}
                className="bg-gray-900 rounded-xl p-4 border border-gray-800 hover:border-gray-700 transition cursor-pointer"
                onClick={() => handleOpenJob(job.id)}>
                <div className="flex items-start justify-between gap-4">
                  <div className="flex-1 min-w-0">
                    <div className="flex items-center gap-2 mb-1 flex-wrap">
                      {statusBadge(job.status)}
                      {job.remote && <span className="text-xs px-2 py-0.5 rounded-full bg-green-500/20 text-green-400">Remote</span>}
                      <span className="text-xs text-gray-600">{getDateLabel(job)}</span>
                    </div>
                    <h3 className="font-semibold text-white truncate text-sm">{job.jobTitle}</h3>
                    <p className="text-gray-400 text-sm">{job.companyName}{job.jobLocation ? ' · ' + job.jobLocation : ''}</p>
                    {formatSalary(job) && <p className="text-green-400 text-xs mt-1">{formatSalary(job)}</p>}
                  </div>

                  {/* Per-job action buttons */}
                  <div className="flex flex-col gap-1.5 shrink-0" onClick={e => e.stopPropagation()}>
                    {/* Company folder download */}
                    {(job.hasResume || job.hasCoverLetter) && (
                      <button
                        onClick={(e) => handleDownloadCompanyFolder(job, e)}
                        disabled={downloading === job.id}
                        className="bg-teal-700 hover:bg-teal-600 disabled:bg-teal-900 px-3 py-1 rounded-lg text-xs transition whitespace-nowrap">
                        {downloading === job.id ? 'Zipping...' : '⬇ Folder'}
                      </button>
                    )}
                    {(job.status === 'READY' || job.status === 'MANUAL_APPLY') && job.jobUrl && (
                      <a href={job.jobUrl} target="_blank" rel="noreferrer"
                        className="bg-blue-600 hover:bg-blue-500 px-3 py-1 rounded-lg text-xs text-center transition">
                        Apply ↗
                      </a>
                    )}
                  </div>
                </div>
              </div>
            ))}
          </div>
        )}
      </div>

      {/* ─── Job Detail Modal ─── */}
      {selectedJob && (
        <div className="fixed inset-0 bg-black/70 z-50 flex items-center justify-center p-4"
          onClick={() => setSelectedJob(null)}>
          <div className="bg-gray-900 rounded-2xl border border-gray-700 w-full max-w-2xl max-h-[90vh] overflow-y-auto"
            onClick={e => e.stopPropagation()}>

            {/* Modal header */}
            <div className="sticky top-0 bg-gray-900 border-b border-gray-800 px-6 py-4 flex items-start justify-between">
              <div>
                <h2 className="font-bold text-lg">{selectedJob.jobTitle}</h2>
                <p className="text-gray-400 text-sm">{selectedJob.companyName}{selectedJob.jobLocation ? ' · ' + selectedJob.jobLocation : ''}</p>
              </div>
              <button onClick={() => setSelectedJob(null)} className="text-gray-400 hover:text-white text-2xl ml-4">×</button>
            </div>

            <div className="px-6 py-4 space-y-4">
              <div className="flex flex-wrap gap-2 items-center">
                {statusBadge(selectedJob.status)}
                {selectedJob.remote && <span className="text-xs px-2 py-0.5 rounded-full bg-green-500/20 text-green-400">Remote</span>}
                {formatSalary(selectedJob) && <span className="text-green-400 text-sm">{formatSalary(selectedJob)}</span>}
              </div>

              {selectedJob.status === 'FAILED' && selectedJob.tailoringError && (
                <div className="bg-red-500/10 border border-red-500/30 rounded-lg p-3">
                  <p className="text-xs text-red-400 font-medium mb-1">Tailoring failed:</p>
                  <p className="text-xs text-gray-400">{selectedJob.tailoringError}</p>
                </div>
              )}

              {/* All download options */}
              <div>
                <p className="text-xs text-gray-500 mb-2">Downloads:</p>
                <div className="flex gap-2 flex-wrap">
                  {/* PDF downloads */}
                  {selectedJob.resumePdfUrl && (
                    <button onClick={() => handleDownloadPdf(selectedJob.resumePdfUrl)}
                      className="bg-gray-700 hover:bg-gray-600 px-3 py-1.5 rounded-lg text-xs transition">
                      Resume PDF
                    </button>
                  )}
                  {selectedJob.coverLetterPdfUrl && (
                    <button onClick={() => handleDownloadPdf(selectedJob.coverLetterPdfUrl)}
                      className="bg-gray-700 hover:bg-gray-600 px-3 py-1.5 rounded-lg text-xs transition">
                      Cover Letter PDF
                    </button>
                  )}
                  {/* LaTeX downloads */}
                  {selectedJob.resumeLatex && (
                    <button onClick={() => handleDownloadTex(selectedJob.resumeLatex, sanitizeName(selectedJob.companyName) + '-resume.tex')}
                      className="bg-gray-600 hover:bg-gray-500 px-3 py-1.5 rounded-lg text-xs transition">
                      Resume .tex
                    </button>
                  )}
                  {selectedJob.coverLetterLatex && (
                    <button onClick={() => handleDownloadTex(selectedJob.coverLetterLatex, sanitizeName(selectedJob.companyName) + '-cover-letter.tex')}
                      className="bg-gray-600 hover:bg-gray-500 px-3 py-1.5 rounded-lg text-xs transition">
                      Cover Letter .tex
                    </button>
                  )}
                  {/* Company folder zip */}
                  {(selectedJob.resumePdfUrl || selectedJob.resumeLatex) && (
                    <button
                      onClick={async () => {
                        showMsg('Building company folder zip...', 'info')
                        try {
                          const zip = await buildCompanyZip(selectedJob)
                          const blob = await zip.generateAsync({ type: 'blob' })
                          saveAs(blob, sanitizeName(selectedJob.companyName) + '--' + sanitizeName(selectedJob.jobTitle) + '.zip')
                          showMsg('Downloaded!', 'success')
                        } catch (err) { showMsg('Failed: ' + err.message, 'error') }
                      }}
                      className="bg-teal-700 hover:bg-teal-600 px-3 py-1.5 rounded-lg text-xs transition">
                      All Files (.zip)
                    </button>
                  )}
                  {/* Apply link */}
                  {selectedJob.jobUrl && (
                    <a href={selectedJob.jobUrl} target="_blank" rel="noreferrer"
                      className="bg-blue-600 hover:bg-blue-500 px-3 py-1.5 rounded-lg text-xs transition">
                      Apply ↗
                    </a>
                  )}
                </div>
              </div>

              {/* Job description */}
              {selectedJob.jobDescription && (
                <div>
                  <p className="text-sm text-gray-400 mb-2">Job Description:</p>
                  <div className="bg-gray-800 rounded-lg p-4 text-sm text-gray-300 max-h-64 overflow-y-auto whitespace-pre-wrap leading-relaxed">
                    {selectedJob.jobDescription}
                  </div>
                </div>
              )}

              <div className="text-xs text-gray-600 pt-2">
                Added: {selectedJob.createdAt ? new Date(selectedJob.createdAt).toLocaleDateString() : 'Unknown'}
              </div>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}
