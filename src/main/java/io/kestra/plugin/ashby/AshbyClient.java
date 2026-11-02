package io.kestra.plugin.ashby;

import java.math.BigInteger;
import java.net.URI;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.core.type.TypeReference;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;

import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.http.HttpRequest;
import io.kestra.core.http.HttpResponse;
import io.kestra.core.http.client.HttpClient;
import io.kestra.core.http.client.HttpClientResponseException;
import io.kestra.core.http.client.configurations.BasicAuthConfiguration;
import io.kestra.core.http.client.configurations.HttpConfiguration;
import io.kestra.core.models.property.Property;
import io.kestra.core.runners.RunContext;
import io.kestra.core.serializers.JacksonMapper;

public final class AshbyClient {
    private static final int MAX_ATTEMPTS = 4;
    private static final long MAX_RETRY_DELAY_MS = 60_000;

    private AshbyClient() {
    }

    public static <T> HttpResponse<T> request(RunContext runContext, Property<String> baseUrl, Property<String> apiKey,
        String method, String path, Map<String, Object> body, Class<T> responseType) throws Exception {
        var rBaseUrl = runContext.render(baseUrl).as(String.class)
            .map(String::strip)
            .filter(url -> !url.isEmpty())
            .map(url -> url.replaceAll("/+$", ""))
            .orElse("https://api.ashbyhq.com");
        var configuration = HttpConfiguration.builder()
            .auth(new RetryControlledBasicAuth(apiKey))
            .allowFailed(Property.ofValue(true))
            .build();
        var builder = HttpRequest.builder().method(method).uri(URI.create(rBaseUrl + path))
            .addHeader("Accept", "application/json");
        if (body != null) {
            builder.body(HttpRequest.JsonRequestBody.builder().content(body).build());
        }
        var request = builder.build();
        try (var client = new HttpClient(runContext, configuration)) {
            for (var attempt = 1; ; attempt++) {
                var response = client.request(request, responseType);
                var status = response.getStatus().getCode();
                if (status < 400) {
                    return response;
                }
                if (attempt == MAX_ATTEMPTS || (status != 429 && (status < 500 || status >= 600))) {
                    throw new HttpClientResponseException("Ashby API returned HTTP " + status, response);
                }
                var delay = retryDelay(response, attempt);
                runContext.logger().warn("Ashby returned HTTP {}; retrying attempt {}/{} in {} ms", status, attempt + 1, MAX_ATTEMPTS, delay);
                try {
                    Thread.sleep(delay);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw e;
                }
            }
        }
    }

    private static final class RetryControlledBasicAuth extends BasicAuthConfiguration {
        private RetryControlledBasicAuth(Property<String> apiKey) {
            super(BasicAuthConfiguration.builder().username(apiKey).password(Property.ofValue("")));
        }

        @Override
        public void configure(HttpClientBuilder builder, RunContext runContext) throws IllegalVariableEvaluationException {
            super.configure(builder, runContext);
            // Apache would retry 429/503 on its own and exceed MAX_ATTEMPTS.
            builder.disableAutomaticRetries();
        }
    }

    static long retryDelay(HttpResponse<?> response, int attempt) {
        var retryAfter = response.getHeaders().firstValue("Retry-After").orElse("").strip();
        try {
            return new BigInteger(retryAfter).multiply(BigInteger.valueOf(1000))
                .max(BigInteger.ZERO).min(BigInteger.valueOf(MAX_RETRY_DELAY_MS)).longValue();
        } catch (NumberFormatException ignored) {
            try {
                return Math.min(MAX_RETRY_DELAY_MS, Math.max(0L, Duration.between(ZonedDateTime.now(), ZonedDateTime.parse(retryAfter, DateTimeFormatter.RFC_1123_DATE_TIME)).toMillis()));
            } catch (DateTimeParseException | ArithmeticException e) {
                return 1000L << (attempt - 1);
            }
        }
    }

    public static Map<String, Object> readResponse(HttpResponse<String> response) throws Exception {
        if (response.getBody() == null || response.getBody().isBlank()) {
            throw new IllegalStateException("Empty response from Ashby API");
        }
        Map<String, Object> body = JacksonMapper.ofJson().readValue(response.getBody(), new TypeReference<>() {});
        if (body == null) {
            throw new IllegalStateException("Empty response from Ashby API");
        }
        if (Boolean.FALSE.equals(body.get("success"))) {
            var message = body.get("errorInfo") instanceof Map<?, ?> info && info.get("message") != null
                ? info.get("message").toString() : JacksonMapper.ofJson().writeValueAsString(body);
            var code = body.get("errorInfo") instanceof Map<?, ?> info ? info.get("code") : null;
            throw new ApiException(code instanceof String value ? value : null, "Ashby API request failed: " + message);
        }
        return body;
    }

    public static final class ApiException extends IllegalStateException {
        private final String code;

        public ApiException(String code, String message) {
            super(message);
            this.code = code;
        }

        public String code() {
            return code;
        }
    }

    @SuppressWarnings("unchecked")
    public static List<Map<String, Object>> rows(Map<String, Object> body) {
        if (!(body.get("results") instanceof List<?> rows) || rows.stream().anyMatch(row -> !(row instanceof Map<?, ?>))) {
            throw new IllegalStateException("Ashby API response must contain a results array of objects");
        }
        return (List<Map<String, Object>>) rows;
    }

    public static String nextCursor(Map<String, Object> body, Set<String> seenCursors) {
        if (!Boolean.TRUE.equals(body.get("moreDataAvailable"))) {
            return null;
        }
        if (!(body.get("nextCursor") instanceof String cursor) || cursor.isBlank()) {
            throw new IllegalStateException("Ashby returned moreDataAvailable without a nextCursor");
        }
        if (!seenCursors.add(cursor)) {
            throw new IllegalStateException("Ashby returned a repeating pagination cursor");
        }
        return cursor;
    }
}
