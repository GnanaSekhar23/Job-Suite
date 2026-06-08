package com.jobsuite.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "app")
@Getter
@Setter
public class AppConfig {

    private AzureOpenAi azureOpenAi = new AzureOpenAi();
    private JSearch jsearch = new JSearch();
    private Apify apify = new Apify();
    private Latex latex = new Latex();
    private Cloudinary cloudinary = new Cloudinary();
    private String frontendUrl;

    @Getter @Setter
    public static class AzureOpenAi {
        private String endpoint;
        private String apiKey;
        private String deploymentName;
    }

    @Getter @Setter
    public static class JSearch {
        private String apiKey;
    }

    @Getter @Setter
    public static class Apify {
        private String apiToken;
    }

    @Getter @Setter
    public static class Latex {
        private String serviceUrl;
    }

    @Getter @Setter
    public static class Cloudinary {
        private String cloudName;
        private String apiKey;
        private String apiSecret;
    }
}
