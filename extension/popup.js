const API = 'http://localhost:8080/api'
const GOOGLE_CLIENT_ID = 'YOUR_GOOGLE_CLIENT_ID_HERE'

let currentJob = null

// ─── Storage helpers ───────────────────────────────────────────
function get(key) {
  return new Promise(resolve =>
    chrome.storage.local.get([key], r => resolve(r[key] || null))
  )
}
function set(obj) {
  return new Promise(resolve => chrome.storage.local.set(obj, resolve))
}
function remove(keys) {
  return new Promise(resolve => chrome.storage.local.remove(keys, resolve))
}

// ─── UI helpers ────────────────────────────────────────────────
function showLogin() {
  document.getElementById('loginSection').style.display = 'block'
  document.getElementById('mainSection').style.display = 'none'
  document.getElementById('dot').className = 'dot'
}
function showMain() {
  document.getElementById('loginSection').style.display = 'none'
  document.getElementById('mainSection').style.display = 'block'
  document.getElementById('dot').className = 'dot on'
}
function showMsg(id, text, type) {
  const el = document.getElementById(id)
  el.innerHTML = `<div class="msg ${type}">${text}</div>`
  setTimeout(() => { el.innerHTML = '' }, 5000)
}

// ─── Init ──────────────────────────────────────────────────────
document.addEventListener('DOMContentLoaded', async () => {
  const token = await get('accessToken')
  if (token) {
    showMain()
    loadJob()
  } else {
    showLogin()
  }

  // Attach all event listeners here — no inline handlers
  document.getElementById('loginBtn').addEventListener('click', loginWithEmail)
  document.getElementById('googleBtn').addEventListener('click', loginWithGoogle)
  document.getElementById('tailorBtn').addEventListener('click', tailorResume)
  document.getElementById('dashboardBtn').addEventListener('click', openDashboard)
  document.getElementById('signOutBtn').addEventListener('click', signOut)

  // Allow pressing Enter in password field
  document.getElementById('password').addEventListener('keydown', (e) => {
    if (e.key === 'Enter') loginWithEmail()
  })
  document.getElementById('email').addEventListener('keydown', (e) => {
    if (e.key === 'Enter') document.getElementById('password').focus()
  })
})

// ─── Email Login ───────────────────────────────────────────────
async function loginWithEmail() {
  const email = document.getElementById('email').value.trim()
  const password = document.getElementById('password').value
  const btn = document.getElementById('loginBtn')

  if (!email || !password) {
    showMsg('loginMsg', 'Enter email and password', 'error')
    return
  }

  btn.textContent = 'Signing in...'
  btn.disabled = true

  try {
    const res = await fetch(API + '/auth/login', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ email, password })
    })

    if (!res.ok) {
      const err = await res.json().catch(() => ({}))
      throw new Error(err.message || 'Invalid credentials')
    }

    const data = await res.json()
    await set({
      accessToken: data.accessToken,
      refreshToken: data.refreshToken,
      userEmail: data.user?.email || email
    })
    showMain()
    document.getElementById('userEmail').textContent = data.user?.email || email
    loadJob()
  } catch (err) {
    showMsg('loginMsg', err.message || 'Login failed', 'error')
  } finally {
    btn.textContent = 'Sign In'
    btn.disabled = false
  }
}

// ─── Google Login ──────────────────────────────────────────────
function loginWithGoogle() {
  const btn = document.getElementById('googleBtn')
  btn.textContent = 'Opening Google...'
  btn.disabled = true

  const script = document.createElement('script')
  script.src = 'https://accounts.google.com/gsi/client'
  script.onload = () => {
    window.google.accounts.id.initialize({
      client_id: GOOGLE_CLIENT_ID,
      callback: async (response) => {
        try {
          const res = await fetch(API + '/auth/google', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ idToken: response.credential })
          })
          if (!res.ok) throw new Error('Google auth failed')
          const data = await res.json()
          await set({
            accessToken: data.accessToken,
            refreshToken: data.refreshToken,
            userEmail: data.user?.email || ''
          })
          showMain()
          document.getElementById('userEmail').textContent = data.user?.email || ''
          loadJob()
        } catch (err) {
          showMsg('loginMsg', 'Google login failed: ' + err.message, 'error')
          btn.textContent = 'Continue with Google'
          btn.disabled = false
        }
      }
    })
    window.google.accounts.id.prompt((notification) => {
      if (notification.isNotDisplayed() || notification.isSkippedMoment()) {
        showMsg('loginMsg', 'Google prompt blocked. Try email login.', 'error')
        btn.textContent = 'Continue with Google'
        btn.disabled = false
      }
    })
  }
  script.onerror = () => {
    showMsg('loginMsg', 'Could not load Google. Check internet.', 'error')
    btn.textContent = 'Continue with Google'
    btn.disabled = false
  }
  document.head.appendChild(script)
}

// ─── Load job from current tab ─────────────────────────────────
async function loadJob() {
  const email = await get('userEmail')
  if (email) document.getElementById('userEmail').textContent = email

  try {
    const [tab] = await chrome.tabs.query({ active: true, currentWindow: true })
    if (!tab?.id) throw new Error('No tab')

    const result = await chrome.tabs.sendMessage(tab.id, { action: 'getJobData' })
      .catch(() => null)

    if (result?.title) {
      currentJob = { ...result, url: tab.url }
      document.getElementById('jobTitle').textContent = result.title
      document.getElementById('jobCompany').textContent = result.company || ''
      document.getElementById('jobCard').style.display = 'block'
      document.getElementById('noJob').style.display = 'none'
      document.getElementById('tailorBtn').style.display = 'block'
    } else {
      throw new Error('No job')
    }
  } catch {
    currentJob = null
    document.getElementById('jobCard').style.display = 'none'
    document.getElementById('noJob').style.display = 'block'
    document.getElementById('tailorBtn').style.display = 'none'
  }
}

// ─── Tailor resume ─────────────────────────────────────────────
async function tailorResume() {
  if (!currentJob) return
  const btn = document.getElementById('tailorBtn')
  btn.textContent = '⟳ Tailoring...'
  btn.disabled = true

  try {
    const token = await get('accessToken')
    const [tab] = await chrome.tabs.query({ active: true, currentWindow: true })

    const res = await fetch(API + '/dashboard/tailor-from-extension', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Authorization': 'Bearer ' + token
      },
      body: JSON.stringify({
        jobTitle: currentJob.title,
        companyName: currentJob.company || 'Unknown',
        jobDescription: currentJob.description || '',
        jobUrl: tab.url,
        jobLocation: currentJob.location || '',
        isRemote: (currentJob.location || '').toLowerCase().includes('remote'),
        isEasyApply: tab.url.includes('indeed.com') || tab.url.includes('linkedin.com')
      })
    })

    if (res.status === 401) {
      await remove(['accessToken', 'refreshToken'])
      showLogin()
      return
    }
    if (!res.ok) throw new Error('Server error ' + res.status)

    const msg = await res.text()
    showMsg('mainMsg', '✓ ' + msg, 'success')
  } catch (err) {
    showMsg('mainMsg', err.message || 'Failed', 'error')
  } finally {
    btn.textContent = '✦ Tailor Resume for This Job'
    btn.disabled = false
  }
}

// ─── Other actions ─────────────────────────────────────────────
function openDashboard() {
  chrome.tabs.create({ url: 'http://localhost:5173/dashboard' })
}

async function signOut() {
  await remove(['accessToken', 'refreshToken', 'userEmail'])
  showLogin()
}
