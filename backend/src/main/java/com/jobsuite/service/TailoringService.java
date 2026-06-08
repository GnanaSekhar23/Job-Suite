package com.jobsuite.service;

import com.jobsuite.entity.*;
import com.jobsuite.entity.JobApplication.ApplicationStatus;
import com.jobsuite.entity.TailoredResume.TailoringStatus;
import com.jobsuite.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class TailoringService {

    private final JobApplicationRepository jobApplicationRepository;
    private final TailoredResumeRepository tailoredResumeRepository;
    private final BaseResumeRepository baseResumeRepository;
    private final AiService aiService;
    private final LatexService latexService;
    private final StorageService storageService;

    public void processDiscoveredJobs(User user) {
        List<JobApplication> discoveredJobs = jobApplicationRepository
                .findByUserAndStatus(user, ApplicationStatus.DISCOVERED);
        log.info("Processing {} discovered jobs for: {}", discoveredJobs.size(), user.getEmail());
        for (JobApplication job : discoveredJobs) {
            try {
                tailorJob(job);
            } catch (Exception e) {
                log.error("Failed to tailor job {}: {}", job.getId(), e.getMessage());
                markJobFailed(job, e.getMessage());
            }
        }
    }

    @Async
    @Transactional
    public void tailorJob(JobApplication job) {
        log.info("Starting tailoring for: {} at {}", job.getJobTitle(), job.getCompanyName());

        // Step 1: Filter staffing firms
        try {
            job.setStatus(ApplicationStatus.FILTERING);
            jobApplicationRepository.save(job);
            boolean isReal = aiService.isRealCompany(job.getCompanyName(), job.getJobDescription());
            if (!isReal) {
                log.info("Filtering out staffing firm: {}", job.getCompanyName());
                job.setStatus(ApplicationStatus.WITHDRAWN);
                job.setFailureReason("Filtered: staffing firm");
                jobApplicationRepository.save(job);
                return;
            }
        } catch (Exception e) {
            log.warn("Could not filter company, proceeding: {}", e.getMessage());
        }

        // Step 2: Get base resume
        User user = job.getUser();
        BaseResume baseResume = baseResumeRepository.findByUserAndIsActiveTrue(user).orElse(null);
        if (baseResume == null || baseResume.getParsedContent() == null) {
            markJobFailed(job, "No base resume uploaded");
            return;
        }

        // Step 3: Set status to TAILORING
        job.setStatus(ApplicationStatus.TAILORING);
        jobApplicationRepository.save(job);

        TailoredResume tailoredResume = TailoredResume.builder()
                .jobApplication(job)
                .baseResume(baseResume)
                .status(TailoringStatus.PROCESSING)
                .build();
        tailoredResume = tailoredResumeRepository.save(tailoredResume);

        try {
            // Step 4: Generate resume LaTeX
            log.info("Generating resume LaTeX...");
            String resumeLatex = aiService.tailorResume(
                    baseResume.getParsedContent(),
                    baseResume.getLatexTemplate(),
                    job.getJobDescription(),
                    job.getJobTitle(),
                    job.getCompanyName()
            );

            // Validate resume LaTeX
            if (resumeLatex == null || resumeLatex.isBlank() || !resumeLatex.contains("\\documentclass")) {
                log.warn("Resume LaTeX invalid, using fallback");
                resumeLatex = buildFallbackResume(baseResume.getParsedContent(), job.getJobTitle(), job.getCompanyName());
            }

            // Step 5: Generate cover letter LaTeX
            log.info("Generating cover letter LaTeX...");
            String coverLetterLatex = aiService.generateCoverLetter(
                    baseResume.getParsedContent(),
                    job.getJobDescription(),
                    job.getJobTitle(),
                    job.getCompanyName()
            );

            // Validate cover letter LaTeX
            if (coverLetterLatex == null || coverLetterLatex.isBlank() || !coverLetterLatex.contains("\\documentclass")) {
                log.warn("Cover letter LaTeX invalid, using fallback");
                coverLetterLatex = buildFallbackCoverLetter(job.getJobTitle(), job.getCompanyName());
            }

            // Step 6: Compile resume PDF
            log.info("Compiling resume PDF...");
            byte[] resumePdf;
            try {
                resumePdf = latexService.compileToPdf(resumeLatex, "resume");
            } catch (Exception e) {
                log.warn("Resume LaTeX compile failed, using fallback: {}", e.getMessage());
                resumePdf = latexService.compileToPdf(
                        buildFallbackResume(baseResume.getParsedContent(), job.getJobTitle(), job.getCompanyName()),
                        "resume"
                );
            }

            // Step 7: Compile cover letter PDF
            log.info("Compiling cover letter PDF...");
            byte[] coverLetterPdf;
            try {
                coverLetterPdf = latexService.compileToPdf(coverLetterLatex, "cover-letter");
            } catch (Exception e) {
                log.warn("Cover letter compile failed, using fallback: {}", e.getMessage());
                coverLetterPdf = latexService.compileToPdf(
                        buildFallbackCoverLetter(job.getJobTitle(), job.getCompanyName()),
                        "cover-letter"
                );
            }

            // Step 8: Upload to Cloudinary
            log.info("Uploading to Cloudinary...");
            String folderName = buildFolderName(job.getCompanyName(), job.getJobTitle());
            String folder = storageService.buildFolderPath(user.getId(), "tailored/" + folderName);

            String resumePdfKey = storageService.uploadBytes(resumePdf, folder, "resume");
            String coverLetterPdfKey = storageService.uploadBytes(coverLetterPdf, folder, "cover-letter");

            // Step 9: Save
            tailoredResume.setResumeLatex(resumeLatex);
            tailoredResume.setCoverLetterLatex(coverLetterLatex);
            tailoredResume.setResumePdfKey(resumePdfKey);
            tailoredResume.setCoverLetterPdfKey(coverLetterPdfKey);
            tailoredResume.setStatus(TailoringStatus.COMPLETED);
            tailoredResumeRepository.save(tailoredResume);

            // Step 10: Update job status
            job.setStatus(job.isEasyApply() ? ApplicationStatus.READY : ApplicationStatus.MANUAL_APPLY);
            jobApplicationRepository.save(job);

            log.info("Tailoring complete for {} at {}", job.getJobTitle(), job.getCompanyName());

        } catch (Exception e) {
            log.error("Tailoring failed for job {}: {}", job.getId(), e.getMessage());
            tailoredResume.setStatus(TailoringStatus.FAILED);
            tailoredResume.setErrorMessage(e.getMessage());
            tailoredResumeRepository.save(tailoredResume);
            markJobFailed(job, e.getMessage());
        }
    }

    private String buildFallbackResume(String content, String jobTitle, String company) {
        String safe = content
                .replace("\\", "\\textbackslash{}")
                .replace("&", "\\&")
                .replace("%", "\\%")
                .replace("$", "\\$")
                .replace("#", "\\#")
                .replace("_", "\\_")
                .replace("{", "\\{")
                .replace("}", "\\}")
                .replace("~", "\\textasciitilde{}")
                .replace("^", "\\textasciicircum{}");
        if (safe.length() > 3000) safe = safe.substring(0, 3000);

        return "\\documentclass[10pt]{article}\n" +
               "\\usepackage[margin=0.75in]{geometry}\n" +
               "\\usepackage{parskip}\n" +
               "\\pagestyle{empty}\n" +
               "\\begin{document}\n" +
               "\\begin{center}{\\Large \\textbf{Resume}}\\\\[0.3em]\n" +
               "Tailored for: " + jobTitle.replace("&","and").replace("%","") + " at " + company.replace("&","and") + "\n" +
               "\\end{center}\\hrule\\vspace{0.5em}\n" +
               safe + "\n" +
               "\\end{document}";
    }

    private String buildFallbackCoverLetter(String jobTitle, String company) {
        String safeTitle = jobTitle.replace("&", "and").replace("%", "").replace("$", "");
        String safeCompany = company.replace("&", "and").replace("%", "").replace("$", "");
        return "\\documentclass[11pt]{article}\n" +
               "\\usepackage[margin=1in]{geometry}\n" +
               "\\usepackage{parskip}\n" +
               "\\pagestyle{empty}\n" +
               "\\setlength{\\parindent}{0pt}\n" +
               "\\begin{document}\n" +
               "Dear Hiring Manager at " + safeCompany + ",\n\n" +
               "I am writing to express my strong interest in the " + safeTitle + " position at " + safeCompany + ". " +
               "My background in software engineering and full-stack development makes me an excellent candidate for this role.\n\n" +
               "I have extensive experience building scalable web applications using Java, Spring Boot, Python, and React.js. " +
               "I am passionate about delivering high-quality software solutions and collaborating with cross-functional teams.\n\n" +
               "I would welcome the opportunity to discuss how my skills align with your needs. " +
               "Thank you for your consideration.\n\n" +
               "Sincerely,\\\\\n" +
               "Gnana Sekhar Chandra\n" +
               "\\end{document}";
    }

    private void markJobFailed(JobApplication job, String reason) {
        job.setStatus(ApplicationStatus.FAILED);
        job.setFailureReason(reason);
        jobApplicationRepository.save(job);
    }

    private String buildFolderName(String company, String title) {
        String combined = (company + "-" + title).toLowerCase()
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-+|-+$", "");
        return combined.length() > 50 ? combined.substring(0, 50) : combined;
    }
}
