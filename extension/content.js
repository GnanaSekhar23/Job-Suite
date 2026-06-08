function getJobData() {
  const url = window.location.href

  // ─── LinkedIn ───────────────────────────────────────────────
  if (url.includes('linkedin.com')) {
    // Try job detail page first
    let title = document.querySelector(
      '.job-details-jobs-unified-top-card__job-title h1, ' +
      'h1.topcard__title, ' +
      '.t-24.t-bold'
    )?.textContent?.trim()

    // Try the side panel on search pages (job shown on right side)
    if (!title) {
      title = document.querySelector(
        '.jobs-unified-top-card__job-title h1, ' +
        '.jobs-unified-top-card__job-title, ' +
        '.job-details-jobs-unified-top-card__job-title, ' +
        '[class*="job-title"] h1, ' +
        '.jobs-search__job-details h1'
      )?.textContent?.trim()
    }

    const company = document.querySelector(
      '.job-details-jobs-unified-top-card__company-name a, ' +
      '.job-details-jobs-unified-top-card__company-name, ' +
      '.jobs-unified-top-card__company-name a, ' +
      '.jobs-unified-top-card__company-name, ' +
      '.topcard__org-name-link, ' +
      '[class*="company-name"]'
    )?.textContent?.trim()

    const description = document.querySelector(
      '.job-details__description-section, ' +
      '.description__text, ' +
      '#job-details, ' +
      '.jobs-description__content, ' +
      '.jobs-box__html-content'
    )?.innerText?.trim()

    const location = document.querySelector(
      '.job-details-jobs-unified-top-card__bullet, ' +
      '.jobs-unified-top-card__bullet, ' +
      '.topcard__flavor--bullet, ' +
      '[class*="workplace-type"]'
    )?.textContent?.trim()

    if (title) return { title, company, description, location }
  }

  // ─── Indeed ─────────────────────────────────────────────────
  if (url.includes('indeed.com')) {
    const title = document.querySelector(
      'h1.jobsearch-JobInfoHeader-title, ' +
      '[data-testid="jobsearch-JobInfoHeader-title"], ' +
      'h1.jobTitle, ' +
      'h1[class*="title"]'
    )?.textContent?.trim()

    const company = document.querySelector(
      '[data-testid="inlineHeader-companyName"] a, ' +
      '[data-testid="inlineHeader-companyName"], ' +
      '.jobsearch-CompanyInfoWithoutHeaderImage a, ' +
      '[class*="companyName"]'
    )?.textContent?.trim()

    const description = document.querySelector(
      '#jobDescriptionText, ' +
      '.jobsearch-jobDescriptionText, ' +
      '[data-testid="jobsearch-JobComponent-description"]'
    )?.innerText?.trim()

    const location = document.querySelector(
      '[data-testid="job-location"], ' +
      '.jobsearch-JobInfoHeader-subtitle div:last-child, ' +
      '[class*="location"]'
    )?.textContent?.trim()

    if (title) return { title, company, description, location }
  }

  // ─── Glassdoor ──────────────────────────────────────────────
  if (url.includes('glassdoor.com')) {
    const title = document.querySelector(
      '[data-test="job-title"], ' +
      '.JobDetails_jobTitle__Rw_gn, ' +
      'h1[class*="title"]'
    )?.textContent?.trim()

    const company = document.querySelector(
      '[data-test="employer-name"], ' +
      '.JobDetails_companyName__t9nhH, ' +
      '[class*="employer"]'
    )?.textContent?.trim()

    const description = document.querySelector(
      '[data-test="description"], ' +
      '.JobDetails_jobDescriptionWrapper__NPBZX, ' +
      '[class*="jobDescription"]'
    )?.innerText?.trim()

    const location = document.querySelector(
      '[data-test="location"], ' +
      '.JobDetails_location__mSg5h'
    )?.textContent?.trim()

    if (title) return { title, company, description, location }
  }

  // ─── Greenhouse ─────────────────────────────────────────────
  if (url.includes('greenhouse.io')) {
    const title = document.querySelector('h1.app-title, h1')?.textContent?.trim()
    const company = document.querySelector('.company-name')?.textContent?.trim()
    const description = document.querySelector('#content, .job-post')?.innerText?.trim()
    const location = document.querySelector('.location')?.textContent?.trim()
    if (title) return { title, company, description, location }
  }

  // ─── Lever ──────────────────────────────────────────────────
  if (url.includes('lever.co')) {
    const title = document.querySelector('.posting-headline h2, h2')?.textContent?.trim()
    const company = document.querySelector('.main-header-logo img')?.alt?.trim()
    const description = document.querySelector('.posting-page-content, .content')?.innerText?.trim()
    const location = document.querySelector('.posting-categories .sort-by-time')?.textContent?.trim()
    if (title) return { title, company, description, location }
  }

  return null
}

// Listen for messages from popup
chrome.runtime.onMessage.addListener((message, sender, sendResponse) => {
  if (message.action === 'getJobData') {
    // Try immediately first
    const data = getJobData()
    if (data) {
      sendResponse(data)
      return true
    }

    // If not found, wait 1s for dynamic content to load and retry
    setTimeout(() => {
      sendResponse(getJobData() || {})
    }, 1000)

    return true // Keep channel open for async response
  }
  return true
})
