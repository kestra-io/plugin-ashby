package io.kestra.plugin.ashby;

import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.http.HttpRequest;
import io.kestra.core.http.HttpResponse;
import io.kestra.core.http.client.HttpClient;
import io.kestra.core.http.client.HttpClientException;
import io.kestra.core.http.client.configurations.HttpConfiguration;
import io.kestra.core.http.client.configurations.BasicAuthConfiguration;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.Task;
import io.kestra.core.runners.RunContext;
import io.kestra.core.models.annotations.PluginProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

import java.net.URI;
import java.io.IOException;
import java.io.UncheckedIOException;
import jakarta.validation.constraints.NotNull;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
public abstract class AbstractAshbyConnection extends Task {

    private static final String DEFAULT_BASE_URL = "https://api.ashbyhq.com";

    @Schema(
        title = "The Ashby API Base URL",
        description = "Defaults to https://api.ashbyhq.com"
    )
    @PluginProperty(group = "connection")
    protected Property<String> baseUrl;

    @Schema(
        title = "The Ashby API Key",
        description = "Used for HTTP Basic Authentication. Provided by Ashby."
    )
    @PluginProperty(secret = true, group = "connection")
    @NotNull
    @ToString.Exclude
    protected Property<String> apiKey;

    protected <RES> HttpResponse<RES> request(RunContext runContext, String method, String relativePath, java.util.Map<String, Object> body, Class<RES> responseType)
        throws HttpClientException, IllegalVariableEvaluationException {
        
        String rBaseUrl = runContext.render(this.baseUrl).as(String.class)
            .map(String::strip)
            .filter(url -> !url.isEmpty())
            .map(url -> url.replaceAll("/+$", ""))
            .orElse(DEFAULT_BASE_URL);
        
        HttpConfiguration httpConfiguration = HttpConfiguration.builder()
            .auth(BasicAuthConfiguration.builder().username(this.apiKey).password(Property.ofValue("")).build())
            .build();
            
        HttpRequest.HttpRequestBuilder requestBuilder = HttpRequest.builder()
            .method(method)
            .uri(URI.create(rBaseUrl + relativePath))
            .addHeader("Accept", "application/json");
            
        if (body != null) {
            requestBuilder.body(HttpRequest.JsonRequestBody.builder().content(body).build());
        }
        
        HttpRequest request = requestBuilder.build();
                    
        try (HttpClient client = new HttpClient(runContext, httpConfiguration)) {
            return client.request(request, responseType);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to close the Ashby HTTP client", e);
        }
    }
}
