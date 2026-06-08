package com.jobsuite.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.jobsuite.config.AppConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.stereotype.Service;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
@RequiredArgsConstructor
public class LatexService {

    private final AppConfig appConfig;
    private final ObjectMapper objectMapper =
            new ObjectMapper();

    // Longer timeout — LaTeX compilation can take
    // 30-60 seconds especially first time
    // (Tectonic downloads packages on demand)
    private final OkHttpClient httpClient =
            new OkHttpClient.Builder()
                    .connectTimeout(10, TimeUnit.SECONDS)
                    .readTimeout(120, TimeUnit.SECONDS)
                    .writeTimeout(30, TimeUnit.SECONDS)
                    .build();

    /**
     * Compile LaTeX source code to PDF bytes
     *
     * @param latexContent the full LaTeX source code
     * @param filename     name for the output file
     *                     (without .pdf extension)
     * @return PDF as byte array
     */
    public byte[] compileToPdf(
            String latexContent,
            String filename) {

        String serviceUrl = appConfig
                .getLatex().getServiceUrl();

        log.info("Compiling LaTeX document: {}", filename);

        try {
            // Build request body
            ObjectNode requestBody =
                    objectMapper.createObjectNode();
            requestBody.put("latex_content", latexContent);
            requestBody.put("filename", filename);

            RequestBody body = RequestBody.create(
                    objectMapper.writeValueAsString(requestBody),
                    MediaType.parse("application/json")
            );

            Request request = new Request.Builder()
                    .url(serviceUrl + "/compile")
                    .post(body)
                    .addHeader("Content-Type",
                            "application/json")
                    .build();

            try (Response response = httpClient
                    .newCall(request).execute()) {

                if (!response.isSuccessful()) {
                    String errorBody =
                            response.body() != null
                                    ? response.body().string()
                                    : "No error body";
                    log.error(
                            "LaTeX compilation failed {}: {}",
                            response.code(), errorBody);
                    throw new RuntimeException(
                            "LaTeX compilation failed: "
                                    + errorBody
                    );
                }

                // Return raw PDF bytes
                byte[] pdfBytes =
                        response.body().bytes();
                log.info(
                        "PDF compiled successfully: {} bytes",
                        pdfBytes.length);
                return pdfBytes;
            }

        } catch (IOException e) {
            log.error(
                    "Failed to call LaTeX service: {}",
                    e.getMessage());
            throw new RuntimeException(
                    "LaTeX service unavailable: "
                            + e.getMessage()
            );
        }
    }

    /**
     * Check if LaTeX service is running
     * Called during startup to verify Docker container
     * is available before processing begins
     */
    public boolean isServiceHealthy() {
        try {
            String serviceUrl = appConfig
                    .getLatex().getServiceUrl();

            Request request = new Request.Builder()
                    .url(serviceUrl + "/health")
                    .get()
                    .build();

            try (Response response = httpClient
                    .newCall(request).execute()) {
                return response.isSuccessful();
            }
        } catch (Exception e) {
            log.warn("LaTeX service health check failed: {}",
                    e.getMessage());
            return false;
        }
    }
}