package com.jobsuite.controller;

import com.jobsuite.dto.ResumeDtos.*;
import com.jobsuite.entity.User;
import com.jobsuite.service.ResumeService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.util.List;

@RestController
@RequestMapping("/api/resumes")
@RequiredArgsConstructor
public class ResumeController {

    private final ResumeService resumeService;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ResumeResponse> uploadResume(
            @AuthenticationPrincipal User user,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "title", required = false) String title) {
        return ResponseEntity.ok(resumeService.uploadResume(user, file, title));
    }

    // Upload LaTeX template (optional — attached to active resume)
    @PostMapping(value = "/latex", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ResumeResponse> uploadLatexTemplate(
            @AuthenticationPrincipal User user,
            @RequestParam("file") MultipartFile file) {
        return ResponseEntity.ok(resumeService.uploadLatexTemplate(user, file));
    }

    // Also accept raw LaTeX text directly
    @PostMapping("/latex-text")
    public ResponseEntity<ResumeResponse> saveLatexText(
            @AuthenticationPrincipal User user,
            @RequestBody LatexTextRequest request) {
        return ResponseEntity.ok(resumeService.saveLatexTemplate(user, request.getLatex()));
    }

    @GetMapping("/active")
    public ResponseEntity<ResumeResponse> getActiveResume(
            @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(resumeService.getActiveResume(user));
    }

    @GetMapping
    public ResponseEntity<List<ResumeListResponse>> getAllResumes(
            @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(resumeService.getAllResumes(user));
    }
}
