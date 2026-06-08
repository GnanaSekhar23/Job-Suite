package com.jobsuite.service;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import com.jobsuite.config.AppConfig;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.util.Map;

@Service
@Slf4j
@RequiredArgsConstructor
public class StorageService {

    private final AppConfig appConfig;
    private Cloudinary cloudinary;

    @PostConstruct
    public void init() {
        cloudinary = new Cloudinary(ObjectUtils.asMap(
                "cloud_name", appConfig.getCloudinary().getCloudName(),
                "api_key", appConfig.getCloudinary().getApiKey(),
                "api_secret", appConfig.getCloudinary().getApiSecret(),
                "secure", true
        ));
        log.info("Cloudinary initialized for cloud: {}", appConfig.getCloudinary().getCloudName());
    }

    public String uploadFile(MultipartFile file, String folder, String fileName) {
        try {
            String publicId = folder + "/" + fileName;
            @SuppressWarnings("unchecked")
            Map<String, Object> result = cloudinary.uploader()
                    .upload(file.getBytes(), ObjectUtils.asMap(
                            "public_id", publicId,
                            "resource_type", "raw",
                            "overwrite", true
                    ));
            String uploadedId = (String) result.get("public_id");
            log.info("Uploaded file: {}", uploadedId);
            return uploadedId;
        } catch (IOException e) {
            log.error("Failed to upload file: {}", e.getMessage());
            throw new RuntimeException("Failed to upload file: " + e.getMessage());
        }
    }

    public String uploadBytes(byte[] bytes, String folder, String fileName) {
        try {
            // Always store with .pdf extension so browser opens correctly
            String publicId = folder + "/" + fileName + ".pdf";
            @SuppressWarnings("unchecked")
            Map<String, Object> result = cloudinary.uploader()
                    .upload(bytes, ObjectUtils.asMap(
                            "public_id", publicId,
                            "resource_type", "raw",
                            "type", "upload",
                            "overwrite", true
                    ));
            String uploadedId = (String) result.get("public_id");
            log.info("Uploaded bytes: {}", uploadedId);
            return uploadedId;
        } catch (IOException e) {
            log.error("Failed to upload bytes: {}", e.getMessage());
            throw new RuntimeException("Failed to upload file: " + e.getMessage());
        }
    }

    public String getFileUrl(String publicId) {
        // Build URL manually to avoid /v1/ prefix that breaks direct access
        String cloudName = appConfig.getCloudinary().getCloudName();
        return "https://res.cloudinary.com/" + cloudName + "/raw/upload/" + publicId;
    }

    public void deleteFile(String publicId) {
        try {
            cloudinary.uploader().destroy(publicId, ObjectUtils.asMap("resource_type", "raw"));
            log.info("Deleted file: {}", publicId);
        } catch (IOException e) {
            log.warn("Failed to delete file: {}", e.getMessage());
        }
    }

    public String buildFolderPath(Long userId, String subFolder) {
        return "users/" + userId + "/" + subFolder;
    }
}
