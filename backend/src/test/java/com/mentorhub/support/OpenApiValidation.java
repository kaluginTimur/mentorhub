package com.mentorhub.support;

import com.atlassian.oai.validator.OpenApiInteractionValidator;
import com.atlassian.oai.validator.model.Request;
import com.atlassian.oai.validator.model.SimpleRequest;
import com.atlassian.oai.validator.model.SimpleResponse;
import com.atlassian.oai.validator.report.SimpleValidationReportFormat;
import com.atlassian.oai.validator.report.ValidationReport;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.ResultMatcher;

/**
 * Validates MockMvc requests and responses against the OpenAPI contract in {@code api/openapi.yaml}.
 */
public final class OpenApiValidation {

    private static final String API_PREFIX = "/api/v1/";
    private static final String TEST_PROBE_PREFIX = "/api/v1/test";

    private static final OpenApiInteractionValidator VALIDATOR = createValidator();

    private OpenApiValidation() {}

    /**
     * Returns the shared {@link OpenApiInteractionValidator} instance.
     */
    public static OpenApiInteractionValidator validator() {
        return VALIDATOR;
    }

    /**
     * Returns whether the given request path should be validated against the OpenAPI specification.
     * Skips paths outside {@code /api/v1/} and test-only probes under {@code /api/v1/test/**}.
     */
    public static boolean shouldValidate(String path) {
        if (path == null) {
            return false;
        }
        if (!path.startsWith(API_PREFIX)) {
            return false;
        }
        return !path.equals(TEST_PROBE_PREFIX) && !path.startsWith(TEST_PROBE_PREFIX + "/");
    }

    /**
     * Returns a {@link ResultMatcher} that validates the HTTP response against {@code api/openapi.yaml}.
     */
    public static ResultMatcher responseMatcher() {
        return result -> {
            MockHttpServletRequest request = result.getRequest();
            String path = request.getRequestURI();
            if (!shouldValidate(path)) {
                return;
            }
            Request.Method method = Request.Method.valueOf(request.getMethod().toUpperCase(Locale.ROOT));
            SimpleResponse response = buildSimpleResponse(result.getResponse());
            ValidationReport report = VALIDATOR.validateResponse(path, method, response);
            if (report.hasErrors()) {
                if (isAcceptableUnmappedNotFound(result.getResponse().getStatus(), report)) {
                    return;
                }
                String formatted = SimpleValidationReportFormat.getInstance().apply(report);
                throw new AssertionError(
                        "OpenAPI response validation failed for " + method + " " + path + ":\n" + formatted);
            }
        };
    }

    /**
     * Returns a {@link ResultMatcher} that validates the HTTP request against {@code api/openapi.yaml}.
     */
    public static ResultMatcher requestMatcher() {
        return result -> {
            MockHttpServletRequest request = result.getRequest();
            String path = request.getRequestURI();
            if (!shouldValidate(path)) {
                return;
            }
            SimpleRequest simpleRequest = buildSimpleRequest(request);
            ValidationReport report = VALIDATOR.validateRequest(simpleRequest);
            if (report.hasErrors()) {
                String formatted = SimpleValidationReportFormat.getInstance().apply(report);
                throw new AssertionError("OpenAPI request validation failed for " + request.getMethod() + " " + path
                        + ":\n" + formatted);
            }
        };
    }

    /**
     * Returns a {@link ResultMatcher} that validates both the HTTP request and response against {@code api/openapi.yaml}.
     */
    public static ResultMatcher requestAndResponseMatcher() {
        return result -> {
            MockHttpServletRequest request = result.getRequest();
            String path = request.getRequestURI();
            if (!shouldValidate(path)) {
                return;
            }
            SimpleRequest simpleRequest = buildSimpleRequest(request);
            SimpleResponse simpleResponse = buildSimpleResponse(result.getResponse());
            ValidationReport report = VALIDATOR.validate(simpleRequest, simpleResponse);
            if (report.hasErrors()) {
                if (isAcceptableUnmappedNotFound(result.getResponse().getStatus(), report)) {
                    return;
                }
                String formatted = SimpleValidationReportFormat.getInstance().apply(report);
                throw new AssertionError("OpenAPI request/response validation failed for " + request.getMethod() + " "
                        + path + ":\n" + formatted);
            }
        };
    }

    private static boolean isAcceptableUnmappedNotFound(int status, ValidationReport report) {
        if (status != 404) {
            return false;
        }
        List<ValidationReport.Message> errorMessages = report.getMessages().stream()
                .filter(m -> m.getLevel() == ValidationReport.Level.ERROR)
                .toList();
        return !errorMessages.isEmpty()
                && errorMessages.stream().allMatch(m -> "validation.request.path.missing".equals(m.getKey()));
    }

    public static SimpleResponse buildSimpleResponse(MockHttpServletResponse response) {
        SimpleResponse.Builder builder = SimpleResponse.Builder.status(response.getStatus());

        for (String headerName : response.getHeaderNames()) {
            List<String> values = response.getHeaders(headerName);
            if (values != null && !values.isEmpty()) {
                builder.withHeader(headerName, values);
            }
        }

        String contentType = response.getContentType();
        if (contentType != null && !contentType.isBlank()) {
            builder.withContentType(contentType);
        }

        byte[] content = response.getContentAsByteArray();
        if (content != null && content.length > 0) {
            builder.withBody(content);
        }

        return builder.build();
    }

    public static SimpleRequest buildSimpleRequest(MockHttpServletRequest request) {
        String method = request.getMethod();
        String path = request.getRequestURI();
        SimpleRequest.Builder builder = new SimpleRequest.Builder(method, path);

        Enumeration<String> headerNames = request.getHeaderNames();
        if (headerNames != null) {
            while (headerNames.hasMoreElements()) {
                String name = headerNames.nextElement();
                List<String> values = Collections.list(request.getHeaders(name));
                if (!values.isEmpty()) {
                    builder.withHeader(name, values);
                }
            }
        }

        String contentType = request.getContentType();
        if (contentType != null && !contentType.isBlank()) {
            builder.withContentType(contentType);
        }

        Map<String, String[]> parameterMap = request.getParameterMap();
        if (parameterMap != null) {
            parameterMap.forEach((name, values) -> {
                if (values != null && values.length > 0) {
                    builder.withQueryParam(name, Arrays.asList(values));
                }
            });
        }

        byte[] content = request.getContentAsByteArray();
        if (content != null && content.length > 0) {
            builder.withBody(content);
        }

        return builder.build();
    }

    private static OpenApiInteractionValidator createValidator() {
        String spec = loadSpecificationContent();
        return OpenApiInteractionValidator.createForInlineApiSpecification(spec).build();
    }

    private static String loadSpecificationContent() {
        Path parentPath = Path.of("../api/openapi.yaml");
        if (Files.isRegularFile(parentPath)) {
            try {
                return Files.readString(parentPath, StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new UncheckedIOException("Failed to read " + parentPath, e);
            }
        }
        Path rootPath = Path.of("api/openapi.yaml");
        if (Files.isRegularFile(rootPath)) {
            try {
                return Files.readString(rootPath, StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new UncheckedIOException("Failed to read " + rootPath, e);
            }
        }
        try (InputStream in = OpenApiValidation.class.getResourceAsStream("/static/openapi.yaml")) {
            if (in != null) {
                return new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read classpath:/static/openapi.yaml", e);
        }
        throw new IllegalStateException(
                "OpenAPI specification not found in ../api/openapi.yaml, api/openapi.yaml, or classpath:/static/openapi.yaml");
    }
}
