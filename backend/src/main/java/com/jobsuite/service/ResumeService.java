package com.jobsuite.service;

import com.jobsuite.dto.ResumeDtos.*;
import com.jobsuite.entity.BaseResume;
import com.jobsuite.entity.User;
import com.jobsuite.exception.AppException;
import com.jobsuite.repository.BaseResumeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class ResumeService {

    private final BaseResumeRepository baseResumeRepository;
    private final StorageService storageService;

    @Transactional
    public ResumeResponse uploadResume(User user, MultipartFile file, String title) {
        if (file.isEmpty()) throw new AppException("Please select a file", HttpStatus.BAD_REQUEST);

        String contentType = file.getContentType();
        if (contentType == null || !contentType.equals("application/pdf"))
            throw new AppException("Only PDF files are allowed", HttpStatus.BAD_REQUEST);

        if (file.getSize() > 5 * 1024 * 1024)
            throw new AppException("File size must be less than 5MB", HttpStatus.BAD_REQUEST);

        String parsedContent = extractTextFromPdf(file);
        if (parsedContent == null || parsedContent.trim().isEmpty())
            throw new AppException("Could not extract text from PDF.", HttpStatus.BAD_REQUEST);

        // Deactivate old resume but preserve its latex template
        String existingLatex = null;
        BaseResume oldResume = baseResumeRepository.findByUserAndIsActiveTrue(user).orElse(null);
        if (oldResume != null) {
            existingLatex = oldResume.getLatexTemplate();
            oldResume.setActive(false);
            baseResumeRepository.save(oldResume);
        }

        String folder = storageService.buildFolderPath(user.getId(), "base-resumes");
        String publicId = storageService.uploadFile(file, folder, "original");

        BaseResume resume = BaseResume.builder()
                .user(user)
                .title(title != null && !title.isBlank() ? title : "My Resume")
                .originalPdfKey(publicId)
                .parsedContent(parsedContent)
                .latexTemplate(existingLatex) // carry over latex template from old resume
                .isActive(true)
                .build();

        resume = baseResumeRepository.save(resume);
        String downloadUrl = storageService.getFileUrl(publicId);

        return ResumeResponse.builder()
                .id(resume.getId())
                .title(resume.getTitle())
                .originalPdfUrl(downloadUrl)
                .isActive(resume.isActive())
                .hasLatexTemplate(resume.getLatexTemplate() != null)
                .createdAt(resume.getCreatedAt())
                .build();
    }

    @Transactional
    public ResumeResponse uploadLatexTemplate(User user, MultipartFile file) {
        if (file.isEmpty()) throw new AppException("Please select a file", HttpStatus.BAD_REQUEST);
        try {
            String latex = new String(file.getBytes(), StandardCharsets.UTF_8);
            return saveLatexTemplate(user, latex);
        } catch (IOException e) {
            throw new AppException("Failed to read LaTeX file", HttpStatus.BAD_REQUEST);
        }
    }

    @Transactional
    public ResumeResponse saveLatexTemplate(User user, String latex) {
        if (latex == null || latex.isBlank())
            throw new AppException("LaTeX content is empty", HttpStatus.BAD_REQUEST);

        if (!latex.contains("\\documentclass"))
            throw new AppException("Invalid LaTeX — must contain \\documentclass", HttpStatus.BAD_REQUEST);

        BaseResume resume = baseResumeRepository.findByUserAndIsActiveTrue(user)
                .orElseThrow(() -> new AppException(
                        "Please upload a PDF resume first before adding LaTeX template",
                        HttpStatus.NOT_FOUND));

        resume.setLatexTemplate(latex);
        resume = baseResumeRepository.save(resume);

        log.info("LaTeX template saved for user: {} ({} chars)", user.getEmail(), latex.length());

        String downloadUrl = resume.getOriginalPdfKey() != null
                ? storageService.getFileUrl(resume.getOriginalPdfKey()) : null;

        return ResumeResponse.builder()
                .id(resume.getId())
                .title(resume.getTitle())
                .originalPdfUrl(downloadUrl)
                .isActive(resume.isActive())
                .hasLatexTemplate(true)
                .createdAt(resume.getCreatedAt())
                .build();
    }

    public ResumeResponse getActiveResume(User user) {
        BaseResume resume = baseResumeRepository.findByUserAndIsActiveTrue(user)
                .orElseThrow(() -> new AppException("No resume found. Please upload first.", HttpStatus.NOT_FOUND));

        String downloadUrl = storageService.getFileUrl(resume.getOriginalPdfKey());

        return ResumeResponse.builder()
                .id(resume.getId())
                .title(resume.getTitle())
                .originalPdfUrl(downloadUrl)
                .isActive(resume.isActive())
                .hasLatexTemplate(resume.getLatexTemplate() != null)
                .createdAt(resume.getCreatedAt())
                .build();
    }

    public List<ResumeListResponse> getAllResumes(User user) {
        return baseResumeRepository.findByUser(user).stream()
                .map(r -> ResumeListResponse.builder()
                        .id(r.getId())
                        .title(r.getTitle())
                        .isActive(r.isActive())
                        .hasLatexTemplate(r.getLatexTemplate() != null)
                        .createdAt(r.getCreatedAt())
                        .build())
                .collect(Collectors.toList());
    }

    private String extractTextFromPdf(MultipartFile file) {
        try {
            try (PDDocument document = Loader.loadPDF(file.getBytes())) {
                PDFTextStripper stripper = new PDFTextStripper();
                return stripper.getText(document).trim();
            }
        } catch (IOException e) {
            log.error("Failed to extract text from PDF: {}", e.getMessage());
            return null;
        }
    }
}
