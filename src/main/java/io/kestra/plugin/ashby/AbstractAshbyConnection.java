package io.kestra.plugin.ashby;

import io.kestra.core.http.HttpResponse;
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

import jakarta.validation.constraints.NotNull;
import java.util.Map;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
public abstract class AbstractAshbyConnection extends Task {

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

    protected <T> HttpResponse<T> request(RunContext runContext, String method, String relativePath, Map<String, Object> body, Class<T> responseType) throws Exception {
        return AshbyClient.request(runContext, baseUrl, apiKey, method, relativePath, body, responseType);
    }
}
