package com.jobsuite.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
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
public class AiService {

    private final AppConfig appConfig;
    private final ObjectMapper objectMapper =
            new ObjectMapper();

    private final OkHttpClient httpClient =
            new OkHttpClient.Builder()
                    .connectTimeout(30, TimeUnit.SECONDS)
                    .readTimeout(120, TimeUnit.SECONDS)
                    .writeTimeout(30, TimeUnit.SECONDS)
                    .build();


    private String callAi(String prompt) {
        try {
            String endpoint = appConfig
                    .getAzureOpenAi().getEndpoint();
            String apiKey = appConfig
                    .getAzureOpenAi().getApiKey();
            String deployment = appConfig
                    .getAzureOpenAi().getDeploymentName();

            if (endpoint == null || endpoint.isBlank()) {
                throw new RuntimeException(
                        "AZURE_OPENAI_ENDPOINT is not configured!"
                );
            }
            if (apiKey == null || apiKey.isBlank()) {
                throw new RuntimeException(
                        "AZURE_OPENAI_KEY is not configured!"
                );
            }

            // Azure OpenAI v1 URL format
            String url = endpoint + "/chat/completions";

            // Build request body
            ObjectNode requestBody =
                    objectMapper.createObjectNode();
            requestBody.put("model", deployment);
            requestBody.put("max_tokens", 4000);
            requestBody.put("temperature", 0.7);

            // Messages
            ArrayNode messages =
                    objectMapper.createArrayNode();
            ObjectNode userMessage =
                    objectMapper.createObjectNode();
            userMessage.put("role", "user");
            userMessage.put("content", prompt);
            messages.add(userMessage);
            requestBody.set("messages", messages);

            RequestBody body = RequestBody.create(
                    objectMapper.writeValueAsString(
                            requestBody),
                    MediaType.parse("application/json")
            );

            Request request = new Request.Builder()
                    .url(url)
                    .post(body)
                    .addHeader("api-key", apiKey)
                    .addHeader("Content-Type",
                            "application/json")
                    .build();

            log.debug("Calling Azure OpenAI ({})...",
                    deployment);

            try (Response response = httpClient
                    .newCall(request).execute()) {

                if (!response.isSuccessful()) {
                    String errorBody =
                            response.body() != null
                                    ? response.body().string()
                                    : "No error body";
                    log.error("AI error {}: {}",
                            response.code(), errorBody);
                    throw new RuntimeException(
                            "AI API failed: "
                                    + response.code()
                                    + " - " + errorBody
                    );
                }

                String responseBody =
                        response.body().string();
                JsonNode jsonResponse = objectMapper
                        .readTree(responseBody);

                String content = jsonResponse
                        .path("choices")
                        .path(0)
                        .path("message")
                        .path("content")
                        .asText();

                log.debug(
                        "AI responded with {} characters",
                        content.length());
                return content;
            }

        } catch (IOException e) {
            log.error("Failed to call AI: {}",
                    e.getMessage());
            throw new RuntimeException(
                    "Failed to call AI: "
                            + e.getMessage()
            );
        }
    }

    public boolean isRealCompany(
            String companyName,
            String jobDescription) {

        String prompt = """
            Analyze this job posting carefully.
            Determine if it is posted by a REAL employer
            or a staffing/consulting agency.

            Company Name: %s

            Job Description (first 500 chars):
            %s

            ONLY classify as STAFFING if you see
            CLEAR evidence like:
            - Explicitly says "our client is looking for"
            - Mentions W2/C2C/1099 contract terms
            - Says "staffing agency" or "recruiting firm"
            - Company name contains words like:
              Staffing, Recruiting, Talent, Solutions
              (when combined with agency-like JD)

            When in doubt → classify as REAL.
            Defense contractors, tech companies,
            consulting firms that hire directly = REAL.

            Respond with ONLY one word:
            REAL - direct employer posting
            STAFFING - confirmed staffing agency

            Response:""".formatted(
                companyName,
                jobDescription.substring(0,
                        Math.min(500,
                                jobDescription.length()))
        );

        String response = callAi(prompt)
                .trim().toUpperCase();
        boolean isReal = response.startsWith("REAL");
        log.info("Company '{}' classified as: {}",
                companyName,
                isReal ? "REAL" : "STAFFING");
        return isReal;
    }

    public String tailorResume(
            String baseResumeContent,
            String latexTemplate,
            String jobDescription,
            String jobTitle,
            String companyName) {

        if (jobDescription == null) jobDescription = "";
        if (baseResumeContent == null) baseResumeContent = "";
        if (latexTemplate != null && latexTemplate.isBlank()) latexTemplate = null;
        if (jobTitle == null) jobTitle = "Software Engineer";
        if (companyName == null) companyName = "the company";

        String prompt;

        if (latexTemplate != null && !latexTemplate.isBlank()) {
            prompt = """
You are a professional resume optimizer specializing in ATS-friendly LaTeX resumes.

JOB: %s at %s

JOB DESCRIPTION:
%s

CANDIDATE'S LATEX RESUME TO MODIFY:
%s

Follow this exact process:

STEP 1 — ANALYZE JOB DESCRIPTION
- Extract ALL required and preferred technical skills, languages, frameworks, tools, platforms
- Extract key responsibilities and action verbs
- Note the full tech stack (e.g. if JD says C#/.NET/Azure/SQL Server, that is the target stack)
- Identify domain focus (distributed systems, AI/ML, DevOps, fintech, etc.)

STEP 2 — MODIFY SUMMARY
- Keep the candidate's current role title and experience years EXACTLY as they are — do NOT change them
- Keep the university name and degree EXACTLY as they are
- Only update the technology stack mentioned in the summary to match the JD's tech stack
- Do NOT add company name anywhere in the summary
- Do NOT add phrases like "eagerly waiting to contribute", "excited to join", "looking forward to", or any forward-looking statements
- Remove buzzwords: "passionate", "strong foundation", "hands-on", "expertise", "ensuring", "achieving"
- The summary should read as a factual professional statement — no fluff, no enthusiasm phrases

STEP 3 — MODIFY EXPERIENCE SECTION (most important step)
- Rewrite EVERY bullet point to use exact technologies and action verbs from the JD
- REPLACE technologies in bullets: if original bullet says "React.js" but JD needs "Angular", rewrite the bullet to say "Angular"; if original says "MySQL" but JD needs "SQL Server", change it; if original says "Python/FastAPI" but JD needs "C#/.NET", rewrite to reference C#/.NET patterns
- Show the candidate has worked with the JD's exact tech stack or very close equivalents
- Inject JD-specific tools naturally into bullets (e.g. if JD needs Kubernetes, add "containerized using Kubernetes" to a relevant bullet)
- No repeated action verbs (max 2x each across all bullets)
- Keep all quantified metrics exactly as they are (percentages, user counts, numbers)
- Keep all company names, job titles, dates exactly as they are
- Match the ORIGINAL number of bullets per role exactly — do not reduce

STEP 4 — MODIFY PROJECTS SECTION
- Rewrite every bullet to use technologies from the JD's tech stack
- REPLACE project technologies where needed: if project used Python but JD needs Java, rewrite bullets to reference Java-equivalent patterns; if project used MongoDB but JD needs PostgreSQL, change it
- Expand bullet points to mention specific JD tools and frameworks
- Emphasize the aspects of each project most relevant to this job
- Match the ORIGINAL number of bullets per project exactly — do not reduce

STEP 5 — REVISE SKILLS SECTION (aggressive replacement)
- Programming Languages: Keep only languages relevant to JD. Add ALL languages the JD mentions. Remove languages not relevant to this role.
- Frameworks and Technologies: Replace with the JD's exact framework stack. Add every framework from JD. Remove frameworks not mentioned in JD if space is needed.
- Databases: Replace with databases the JD uses. If JD needs SQL Server, add it; remove irrelevant ones.
- Cloud and DevOps: Replace with cloud/DevOps tools from JD. If JD needs Azure, add Azure services; if JD needs GCP, add GCP tools.
- Web Development: Update to match JD's frontend/backend preferences.
- Core Competencies: Keep general competencies, add any domain-specific ones from JD.
- The Skills section must reflect the JD's tech stack as closely as possible.

STEP 6 — ATS COMPLIANCE
- Use EXACT skill names from JD (e.g. if JD says "ASP.NET Core", use that exact phrase, not just ".NET")
- No buzzwords: passionate, strong foundation, hands-on, expertise, ensuring, achieving, leveraging
- No repeated action verbs (max 2x each)
- Quantify every achievement

STEP 7 — FORMAT RULES (CRITICAL — DO NOT CHANGE ANY OF THESE)
- Keep EVERY \\usepackage command exactly as-is
- Keep EVERY \\titleformat, \\titlespacing, \\geometry command exactly as-is
- Keep EVERY \\vspace, \\hfill, \\textbar, \\begin{center}, \\end{center} exactly as-is
- Keep contact info line exactly as-is (name, phone, email, LinkedIn)
- 10pt font, article class — do NOT change to extarticle
- The resume MUST fill the ENTIRE page — if there is whitespace at the bottom, expand bullet points until the page is full
- ONE PAGE — never exceed one page
- Escape: \\%% for percent sign, \\& for ampersand, $<$ for less-than
- Do NOT wrap output in markdown code blocks

Return ONLY the complete modified LaTeX document. Start directly with \\documentclass
""".formatted(
                    jobTitle,
                    companyName,
                    jobDescription.substring(0, Math.min(3000, jobDescription.length())),
                    latexTemplate
            );
        } else {
            prompt = """
You are a professional resume optimizer specializing in ATS-friendly LaTeX resumes.

JOB: %s at %s

JOB DESCRIPTION:
%s

CANDIDATE RESUME CONTENT:
%s

Follow this exact process:

STEP 1 — ANALYZE JOB DESCRIPTION
- Extract ALL required and preferred technical skills, languages, frameworks, tools
- Note the full tech stack the JD expects
- Extract key action verbs and responsibilities

STEP 2 — MODIFY SUMMARY
- Keep the candidate's current role title and experience years EXACTLY as they are
- Keep the university name and degree EXACTLY as they are
- Only update the technology stack in the summary to match the JD's tech stack
- Do NOT add company name in the summary
- Do NOT add forward-looking phrases like "eager to contribute", "excited to join", "looking forward to"
- No buzzwords: passionate, strong foundation, hands-on, expertise
- Factual professional statement only — no fluff

STEP 3 — EXPERIENCE BULLETS
- REPLACE technologies in bullets to match JD stack (Python → C# if JD needs it; MySQL → SQL Server if JD needs it)
- Inject JD-specific tools naturally into bullets
- No repeated action verbs (max 2x)
- Keep all metrics, company names, dates exactly
- Match the ORIGINAL number of bullets per role exactly

STEP 4 — PROJECTS
- REPLACE project technologies to match JD stack where possible
- Rewrite bullets to emphasize JD-relevant skills
- Match the ORIGINAL number of bullets per project exactly

STEP 5 — SKILLS (aggressive replacement)
- Replace skills to match JD's exact tech stack
- Remove skills irrelevant to this role if space needed
- Add every technology mentioned in JD
- Use EXACT names from JD (e.g. "ASP.NET Core" not just ".NET")

STEP 6 — ATS
- Exact keyword matching, no buzzwords, quantify everything

STEP 7 — USE THIS EXACT LATEX (do not change any formatting):
\\documentclass[10pt,a4paper]{article}
\\usepackage[left=0.5in,right=0.5in,top=0.38in,bottom=0.18in]{geometry}
\\usepackage{titlesec}
\\usepackage{enumitem}
\\usepackage{hyperref}
\\titleformat{\\section}{\\large\\bfseries}{}{0em}{}
\\titlespacing{\\section}{0pt}{5pt}{2pt}
\\renewcommand{\\labelitemi}{$\\bullet$}
\\setlist[itemize]{leftmargin=*, itemsep=0pt, parsep=0pt, topsep=2pt}
\\hypersetup{colorlinks=true, linkcolor=black, urlcolor=black}
\\pagenumbering{gobble}
\\setlength{\\parindent}{0pt}

The resume MUST fill the ENTIRE page — never leave whitespace at the bottom.
ONE PAGE ONLY — never exceed one page.
No markdown. Return ONLY LaTeX starting with \\documentclass
""".formatted(
                    jobTitle,
                    companyName,
                    jobDescription.substring(0, Math.min(3000, jobDescription.length())),
                    baseResumeContent.substring(0, Math.min(4000, baseResumeContent.length()))
            );
        }

        String response = callAi(prompt);
        response = response.trim();
        if (response.startsWith("```")) {
            response = response.replaceAll("(?s)^```[a-z]*\\n?", "").replaceAll("```\\s*$", "").trim();
        }
        return response;
    }


    public String generateCoverLetter(
            String baseResumeContent,
            String jobDescription,
            String jobTitle,
            String companyName) {

        if (jobDescription == null) jobDescription = "";
        if (baseResumeContent == null) baseResumeContent = "";
        if (jobTitle == null) jobTitle = "Software Engineer";
        if (companyName == null) companyName = "the company";

        String safeCompany = companyName.replace("&", "and").replace("%", "");
        String safeTitle = jobTitle.replace("&", "and").replace("%", "");

        int year = java.time.LocalDate.now().getYear();
        String month = java.time.LocalDate.now().getMonth()
                .getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.ENGLISH);
        int day = java.time.LocalDate.now().getDayOfMonth();
        String todayDate = month + " " + day + ", " + year;

        String prompt = """
You are an expert cover letter writer and LaTeX typesetter.

Write a compelling, specific, professional cover letter in LaTeX.

DATE: %s
JOB: %s at %s

JOB DESCRIPTION:
%s

CANDIDATE RESUME:
%s

COVER LETTER CONTENT RULES:
1. Opening paragraph: Start with something compelling — NOT "I am writing to express my interest". Reference the specific role and company. Show you know what they do.
2. Second paragraph: Pick the 2-3 most important technical requirements from the JD. Directly connect them to specific achievements from the resume with real numbers and metrics.
3. Third paragraph: Highlight one specific project or experience that directly matches what this company needs. Be specific, not generic.
4. Closing paragraph: Confident, specific closing. Express genuine enthusiasm for contributing to this company specifically.
- Total: 4 paragraphs, 250-350 words. Professional but personable. NOT generic.
- Do NOT use: "I am writing", "passionate", "strong foundation", "team player", "hard worker"

STRICT LATEX RULES — follow exactly or it will not compile:
- Line 1 must be: \\documentclass[11pt]{article}
- Line 2 must be: \\usepackage[margin=1in]{geometry}
- Line 3 must be: \\usepackage[parfill]{parskip}
- Line 4 must be: \\pagestyle{empty}
- Line 5 must be: \\setlength{\\parindent}{0pt}
- Line 6 must be: \\setlength{\\parskip}{8pt}
- Then: \\begin{document}
- First line after \\begin{document}: %s
- Skip one line then: Dear Hiring Manager,
- Skip one line then write the 4 paragraphs separated by blank lines
- After last paragraph skip one line then: Sincerely,
- Skip two lines then: Gnana Sekhar Chandra
- Last line: \\end{document}
- Do NOT use: \\opening, \\closing, \\signature, \\address, \\newcommand, \\fontsize
- Do NOT use percent sign in text — write "percent" instead
- Do NOT use ampersand in text — write "and" instead
- Do NOT use dollar sign in text — write "dollars" instead
- Do NOT use \\setlength anywhere else
- Do NOT wrap in markdown code blocks

Return ONLY the complete valid LaTeX document starting with \\documentclass
""".formatted(
                todayDate,
                safeTitle,
                safeCompany,
                jobDescription.substring(0, Math.min(1500, jobDescription.length())),
                baseResumeContent.substring(0, Math.min(2000, baseResumeContent.length())),
                todayDate
        );

        log.info("Generating cover letter for {} at {}", jobTitle, companyName);
        String response = callAi(prompt);
        response = response.trim();
        if (response.startsWith("```")) {
            response = response.replaceAll("(?s)^```[a-z]*\\n?", "").replaceAll("```\\s*$", "").trim();
        }
        return response;
    }

    public String expandJobTitles(
            String jobTitle,
            String skills,
            String experienceLevel) {

        String prompt = """
            Generate exactly 4 alternative job titles similar to "%s".
            Experience level: %s
            Skills: %s
            
            Rules:
            - Return ONLY a comma-separated list, nothing else
            - No explanations, no numbering, no extra text
            - Example output: Java Developer, Backend Engineer, Software Developer
            """.formatted(jobTitle, experienceLevel, skills != null ? skills : "");

        String response = callAi(prompt).trim();
        log.info("Expanded '{}' to: {}", jobTitle, response);
        return response;
    }

    public String testAi() {
        return callAi("Say exactly this: AI is working correctly!");
    }
}