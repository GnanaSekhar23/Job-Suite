import { useState, useEffect } from 'react'
import { useNavigate } from 'react-router-dom'
import { preferencesApi, resumeApi } from '../services/api'

const inputClass = 'w-full bg-gray-800 border border-gray-700 rounded-lg px-4 py-2.5 text-white focus:outline-none focus:border-blue-500 transition'

export default function PreferencesPage() {
  const navigate = useNavigate()
  const [activeTab, setActiveTab] = useState('preferences')
  const [saving, setSaving] = useState(false)
  const [message, setMessage] = useState('')
  const [error, setError] = useState('')

  const [prefs, setPrefs] = useState({
    desiredJobTitle: '', country: 'us', desiredSalaryMin: '',
    desiredSalaryMax: '', remoteOnly: false, experienceLevel: 'MID',
    skills: '', phoneNumber: '', yearsOfExperience: '',
    requiresSponsorship: false, willingToRelocate: false,
    linkedinUrl: '', githubUrl: '', portfolioUrl: ''
  })
  const [expandedTitles, setExpandedTitles] = useState('')

  const [resumeFile, setResumeFile] = useState(null)
  const [resumeTitle, setResumeTitle] = useState('')
  const [activeResume, setActiveResume] = useState(null)
  const [allResumes, setAllResumes] = useState([])
  const [uploadingResume, setUploadingResume] = useState(false)

  const [latexFile, setLatexFile] = useState(null)
  const [uploadingLatex, setUploadingLatex] = useState(false)

  useEffect(() => {
    preferencesApi.get()
      .then(res => {
        setPrefs(prev => ({ ...prev, ...res.data }))
        if (res.data.expandedJobTitles) setExpandedTitles(res.data.expandedJobTitles)
      })
      .catch(() => {})

    resumeApi.getActive()
      .then(res => setActiveResume(res.data))
      .catch(() => {})

    resumeApi.getAll()
      .then(res => setAllResumes(Array.isArray(res.data) ? res.data : []))
      .catch(() => {})
  }, [])

  const handlePrefsChange = (e) => {
    const { name, value, type, checked } = e.target
    setPrefs(prev => ({ ...prev, [name]: type === 'checkbox' ? checked : value }))
  }

  const handleSavePrefs = async (e) => {
    e.preventDefault()
    setSaving(true)
    setMessage('')
    setError('')
    try {
      const res = await preferencesApi.save({
        ...prefs,
        desiredSalaryMin: prefs.desiredSalaryMin ? parseInt(prefs.desiredSalaryMin) : null,
        desiredSalaryMax: prefs.desiredSalaryMax ? parseInt(prefs.desiredSalaryMax) : null,
        yearsOfExperience: prefs.yearsOfExperience ? parseInt(prefs.yearsOfExperience) : null,
      })
      setMessage('Preferences saved! Job titles are being expanded in the background.')
      if (res.data.expandedJobTitles) setExpandedTitles(res.data.expandedJobTitles)
    } catch (err) {
      setError(err.response?.data?.message || 'Failed to save preferences')
    } finally {
      setSaving(false)
    }
  }

  const handleResumeUpload = async (e) => {
    e.preventDefault()
    if (!resumeFile) { setError('Please select a PDF file'); return }
    setUploadingResume(true)
    setMessage('')
    setError('')
    try {
      const formData = new FormData()
      formData.append('file', resumeFile)
      formData.append('title', resumeTitle || 'My Resume')
      const res = await resumeApi.upload(formData)
      setActiveResume(res.data)
      setMessage('Resume uploaded successfully!')
      setResumeFile(null)
      setResumeTitle('')
      resumeApi.getAll().then(r => setAllResumes(Array.isArray(r.data) ? r.data : []))
    } catch (err) {
      setError(err.response?.data?.message || 'Failed to upload resume')
    } finally {
      setUploadingResume(false)
    }
  }

  const handleLatexUpload = async (e) => {
    e.preventDefault()
    if (!latexFile) { setError('Please select a .tex file'); return }
    setUploadingLatex(true)
    setMessage('')
    setError('')
    try {
      const formData = new FormData()
      formData.append('file', latexFile)
      await resumeApi.uploadLatex(formData)
      setMessage('LaTeX template saved! Future tailored resumes will use your exact formatting.')
      setLatexFile(null)
      resumeApi.getActive().then(res => setActiveResume(res.data)).catch(() => {})
    } catch (err) {
      setError(err.response?.data?.message || 'Failed to upload LaTeX template')
    } finally {
      setUploadingLatex(false)
    }
  }

  const tabClass = (tab) => 'flex-1 py-2 rounded-lg text-sm font-medium transition ' +
    (activeTab === tab ? 'bg-blue-600 text-white' : 'text-gray-400 hover:text-white')

  return (
    <div className="min-h-screen bg-gray-950 text-white">
      <nav className="bg-gray-900 border-b border-gray-800 px-6 py-4">
        <div className="max-w-3xl mx-auto flex items-center justify-between">
          <h1 className="text-xl font-bold">Job Suite</h1>
          <button onClick={() => navigate('/dashboard')} className="text-gray-400 hover:text-white text-sm transition">
            Back to Dashboard
          </button>
        </div>
      </nav>

      <div className="max-w-3xl mx-auto px-6 py-8">
        <h2 className="text-2xl font-bold mb-1">Setup</h2>
        <p className="text-gray-400 mb-6">Configure your job preferences and upload your resume before fetching jobs.</p>

        <div className="flex gap-1 mb-8 bg-gray-900 rounded-xl p-1 border border-gray-800">
          <button onClick={() => setActiveTab('preferences')} className={tabClass('preferences')}>Job Preferences</button>
          <button onClick={() => setActiveTab('resume')} className={tabClass('resume')}>Resume</button>
        </div>

        {message && (
          <div className="bg-green-500/10 border border-green-500/50 text-green-400 rounded-lg p-3 mb-6 text-sm">{message}</div>
        )}
        {error && (
          <div className="bg-red-500/10 border border-red-500/50 text-red-400 rounded-lg p-3 mb-6 text-sm">{error}</div>
        )}

        {activeTab === 'preferences' && (
          <form onSubmit={handleSavePrefs} className="space-y-6">
            <div className="bg-gray-900 rounded-xl p-6 border border-gray-800 space-y-4">
              <h3 className="font-semibold">Job Search</h3>
              <div>
                <label className="block text-sm text-gray-400 mb-1">Desired Job Title *</label>
                <input name="desiredJobTitle" value={prefs.desiredJobTitle} onChange={handlePrefsChange}
                  required placeholder="e.g. Software Engineer" className={inputClass} />
              </div>
              {expandedTitles && (
                <div className="bg-blue-500/10 border border-blue-500/30 rounded-lg p-3">
                  <p className="text-xs text-blue-400 mb-1">Also searching for:</p>
                  <p className="text-sm text-gray-300">{expandedTitles}</p>
                </div>
              )}
              <div className="grid grid-cols-2 gap-4">
                <div>
                  <label className="block text-sm text-gray-400 mb-1">Country</label>
                  <select name="country" value={prefs.country} onChange={handlePrefsChange} className={inputClass}>
                    <option value="us">United States</option>
                    <option value="uk">United Kingdom</option>
                    <option value="ca">Canada</option>
                    <option value="au">Australia</option>
                    <option value="in">India</option>
                    <option value="de">Germany</option>
                  </select>
                </div>
                <div>
                  <label className="block text-sm text-gray-400 mb-1">Experience Level</label>
                  <select name="experienceLevel" value={prefs.experienceLevel} onChange={handlePrefsChange} className={inputClass}>
                    <option value="ENTRY">Entry Level</option>
                    <option value="MID">Mid Level</option>
                    <option value="SENIOR">Senior</option>
                  </select>
                </div>
              </div>
              <div>
                <label className="block text-sm text-gray-400 mb-1">Skills</label>
                <input name="skills" value={prefs.skills} onChange={handlePrefsChange}
                  placeholder="e.g. Java, Spring Boot, React, PostgreSQL" className={inputClass} />
              </div>
              <div className="grid grid-cols-2 gap-4">
                <div>
                  <label className="block text-sm text-gray-400 mb-1">Min Salary (USD/year)</label>
                  <input name="desiredSalaryMin" type="number" value={prefs.desiredSalaryMin}
                    onChange={handlePrefsChange} placeholder="80000" className={inputClass} />
                </div>
                <div>
                  <label className="block text-sm text-gray-400 mb-1">Max Salary (USD/year)</label>
                  <input name="desiredSalaryMax" type="number" value={prefs.desiredSalaryMax}
                    onChange={handlePrefsChange} placeholder="150000" className={inputClass} />
                </div>
              </div>
              <div className="flex items-center gap-3">
                <input type="checkbox" name="remoteOnly" id="remoteOnly" checked={prefs.remoteOnly}
                  onChange={handlePrefsChange} className="w-4 h-4 accent-blue-600" />
                <label htmlFor="remoteOnly" className="text-sm text-gray-300">Remote jobs only</label>
              </div>
            </div>

            <div className="bg-gray-900 rounded-xl p-6 border border-gray-800 space-y-4">
              <h3 className="font-semibold">Personal Info</h3>
              <div className="grid grid-cols-2 gap-4">
                <div>
                  <label className="block text-sm text-gray-400 mb-1">Phone Number</label>
                  <input name="phoneNumber" value={prefs.phoneNumber} onChange={handlePrefsChange}
                    placeholder="555-1234" className={inputClass} />
                </div>
                <div>
                  <label className="block text-sm text-gray-400 mb-1">Years of Experience</label>
                  <input name="yearsOfExperience" type="number" value={prefs.yearsOfExperience}
                    onChange={handlePrefsChange} placeholder="3" className={inputClass} />
                </div>
              </div>
              <div>
                <label className="block text-sm text-gray-400 mb-1">LinkedIn URL</label>
                <input name="linkedinUrl" value={prefs.linkedinUrl} onChange={handlePrefsChange}
                  placeholder="https://linkedin.com/in/yourprofile" className={inputClass} />
              </div>
              <div>
                <label className="block text-sm text-gray-400 mb-1">GitHub URL</label>
                <input name="githubUrl" value={prefs.githubUrl} onChange={handlePrefsChange}
                  placeholder="https://github.com/yourusername" className={inputClass} />
              </div>
              <div>
                <label className="block text-sm text-gray-400 mb-1">Portfolio URL</label>
                <input name="portfolioUrl" value={prefs.portfolioUrl} onChange={handlePrefsChange}
                  placeholder="https://yourportfolio.com" className={inputClass} />
              </div>
              <div className="space-y-3">
                <div className="flex items-center gap-3">
                  <input type="checkbox" name="requiresSponsorship" id="requiresSponsorship"
                    checked={prefs.requiresSponsorship} onChange={handlePrefsChange} className="w-4 h-4 accent-blue-600" />
                  <label htmlFor="requiresSponsorship" className="text-sm text-gray-300">Requires visa sponsorship</label>
                </div>
                <div className="flex items-center gap-3">
                  <input type="checkbox" name="willingToRelocate" id="willingToRelocate"
                    checked={prefs.willingToRelocate} onChange={handlePrefsChange} className="w-4 h-4 accent-blue-600" />
                  <label htmlFor="willingToRelocate" className="text-sm text-gray-300">Willing to relocate</label>
                </div>
              </div>
            </div>

            <button type="submit" disabled={saving}
              className="w-full bg-blue-600 hover:bg-blue-500 disabled:bg-blue-800 text-white rounded-lg py-3 font-medium transition">
              {saving ? 'Saving...' : 'Save Preferences'}
            </button>
          </form>
        )}

        {activeTab === 'resume' && (
          <div className="space-y-6">

            {activeResume && (
              <div className="bg-gray-900 rounded-xl p-6 border border-gray-800">
                <h3 className="font-semibold mb-3">Active Resume</h3>
                <div className="flex items-center justify-between">
                  <div>
                    <p className="text-white font-medium">{activeResume.title}</p>
                    <p className="text-gray-400 text-sm mt-0.5">
                      Uploaded {new Date(activeResume.createdAt).toLocaleDateString()}
                    </p>
                    <div className="flex gap-2 mt-1">
                      {activeResume.hasLatexTemplate && (
                        <span className="text-xs bg-green-500/20 text-green-400 px-2 py-0.5 rounded-full">
                          LaTeX template active
                        </span>
                      )}
                    </div>
                  </div>
                  {activeResume.originalPdfUrl && (
                    <a href={activeResume.originalPdfUrl} target="_blank" rel="noreferrer"
                      className="bg-gray-700 hover:bg-gray-600 px-4 py-2 rounded-lg text-sm transition">
                      View PDF
                    </a>
                  )}
                </div>
              </div>
            )}

            {/* PDF Upload */}
            <div className="bg-gray-900 rounded-xl p-6 border border-gray-800">
              <h3 className="font-semibold mb-1">{activeResume ? 'Upload New Resume' : 'Upload Your Resume'}</h3>
              <p className="text-gray-400 text-sm mb-4">PDF only. Used to extract your experience for tailoring.</p>
              <form onSubmit={handleResumeUpload} className="space-y-4">
                <div>
                  <label className="block text-sm text-gray-400 mb-1">Resume Title</label>
                  <input value={resumeTitle} onChange={e => setResumeTitle(e.target.value)}
                    placeholder="My Software Engineer Resume" className={inputClass} />
                </div>
                <div>
                  <label className="block text-sm text-gray-400 mb-1">PDF File</label>
                  <input type="file" accept=".pdf" onChange={e => setResumeFile(e.target.files[0])}
                    className="w-full bg-gray-800 border border-gray-700 rounded-lg px-4 py-2.5 text-white focus:outline-none focus:border-blue-500 transition file:mr-4 file:py-1 file:px-3 file:rounded-lg file:border-0 file:bg-blue-600 file:text-white file:text-sm cursor-pointer" />
                </div>
                {resumeFile && <p className="text-gray-400 text-sm">Selected: {resumeFile.name}</p>}
                <button type="submit" disabled={uploadingResume || !resumeFile}
                  className="w-full bg-blue-600 hover:bg-blue-500 disabled:bg-blue-800 text-white rounded-lg py-3 font-medium transition">
                  {uploadingResume ? 'Uploading...' : 'Upload Resume'}
                </button>
              </form>
            </div>

            {/* LaTeX Template Upload */}
            <div className="bg-gray-900 rounded-xl p-6 border border-gray-800">
              <div className="flex items-center gap-2 mb-1">
                <h3 className="font-semibold">LaTeX Template</h3>
                <span className="text-xs bg-blue-500/20 text-blue-400 px-2 py-0.5 rounded-full">Optional</span>
              </div>
              <p className="text-gray-400 text-sm mb-4">
                Upload your <span className="text-white font-mono">.tex</span> resume file.
                The AI will keep your exact LaTeX structure and only reword bullet points
                to match each job — producing perfectly formatted, one-page resumes every time.
              </p>

              {activeResume?.hasLatexTemplate ? (
                <div className="bg-green-500/10 border border-green-500/30 rounded-lg p-3 mb-4 flex items-center gap-2">
                  <span className="text-green-400 text-lg">✓</span>
                  <p className="text-green-400 text-sm">LaTeX template is active — tailored resumes will match your exact format</p>
                </div>
              ) : (
                <div className="bg-yellow-500/10 border border-yellow-500/30 rounded-lg p-3 mb-4">
                  <p className="text-yellow-400 text-sm">No LaTeX template yet — AI will generate its own formatting. Upload your .tex for best results.</p>
                </div>
              )}

              {!activeResume && (
                <p className="text-gray-500 text-sm italic mb-4">Upload a PDF resume first, then you can add your LaTeX template.</p>
              )}

              <form onSubmit={handleLatexUpload} className="space-y-4">
                <div>
                  <label className="block text-sm text-gray-400 mb-1">.tex File</label>
                  <input type="file" accept=".tex,text/x-tex,text/plain"
                    onChange={e => setLatexFile(e.target.files[0])}
                    disabled={!activeResume}
                    className="w-full bg-gray-800 border border-gray-700 rounded-lg px-4 py-2.5 text-white focus:outline-none focus:border-blue-500 transition file:mr-4 file:py-1 file:px-3 file:rounded-lg file:border-0 file:bg-blue-600 file:text-white file:text-sm cursor-pointer disabled:opacity-50" />
                </div>
                {latexFile && <p className="text-gray-400 text-sm">Selected: {latexFile.name}</p>}
                <button type="submit" disabled={uploadingLatex || !latexFile || !activeResume}
                  className="w-full bg-blue-600 hover:bg-blue-500 disabled:bg-blue-800 text-white rounded-lg py-3 font-medium transition">
                  {uploadingLatex ? 'Saving...' : activeResume?.hasLatexTemplate ? 'Update LaTeX Template' : 'Upload LaTeX Template'}
                </button>
              </form>
            </div>

          </div>
        )}
      </div>
    </div>
  )
}
