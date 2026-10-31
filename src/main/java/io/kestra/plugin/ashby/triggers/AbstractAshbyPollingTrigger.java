package io.kestra.plugin.ashby.triggers;

import com.fasterxml.jackson.core.type.TypeReference;
import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.http.HttpRequest;
import io.kestra.core.http.HttpResponse;
import io.kestra.core.http.client.HttpClient;
import io.kestra.core.http.client.configurations.BasicAuthConfiguration;
import io.kestra.core.http.client.configurations.HttpConfiguration;
import io.kestra.core.models.conditions.ConditionContext;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.ExecutionTrigger;
import io.kestra.core.models.flows.State;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.triggers.AbstractTrigger;
import io.kestra.core.models.triggers.PollingTriggerInterface;
import io.kestra.core.models.triggers.TriggerContext;
import io.kestra.core.serializers.JacksonMapper;
import io.kestra.core.storages.kv.KVMetadata;
import io.kestra.core.storages.kv.KVValueAndMetadata;
import io.swagger.v3.oas.annotations.media.Schema;
import io.kestra.core.models.annotations.PluginProperty;
import jakarta.validation.constraints.NotNull;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;
import java.net.URI;
import java.time.Duration;
import java.util.*;

@SuperBuilder @ToString @EqualsAndHashCode(callSuper = true) @Getter @NoArgsConstructor
public abstract class AbstractAshbyPollingTrigger extends AbstractTrigger implements PollingTriggerInterface {
    @NotNull @PluginProperty(secret = true, group = "connection") @ToString.Exclude @Schema(title = "Ashby API key", description = "Secret API key used for Basic authentication.")
    protected Property<String> apiKey;
    @PluginProperty(group = "connection") @Schema(title = "Ashby API base URL", description = "Defaults to https://api.ashbyhq.com.") protected Property<String> baseUrl;
    @NotNull @PluginProperty(group = "main") @Schema(title = "Polling interval") protected Duration interval = Duration.ofMinutes(15);
    protected abstract String endpoint();
    protected abstract String eventField();
    protected abstract String expectedEvent();

    @Override public Optional<Execution> evaluate(ConditionContext cc, TriggerContext tc) throws Exception {
        var runContext = cc.getRunContext();
        String url = runContext.render(baseUrl).as(String.class).orElse("https://api.ashbyhq.com").replaceAll("/+$", "");
        String api = runContext.render(apiKey).as(String.class).orElseThrow();
        String key = "ashby-" + tc.getFlowId() + "-" + tc.getTriggerId() + "-sync-token";
        var kv = runContext.namespaceKv(tc.getNamespace());
        String syncToken = kv.getValue(key).map(v -> String.valueOf(v.value())).orElse(null);
        Map<String,Object> requestBody = syncToken == null ? new LinkedHashMap<>() : new LinkedHashMap<>(Map.of("syncToken", syncToken));
        HttpConfiguration config = HttpConfiguration.builder().auth(BasicAuthConfiguration.builder().username(Property.ofValue(api)).password(Property.ofValue("")).build()).build();
        String body;
        try (HttpClient client = new HttpClient(runContext, config)) {
            HttpRequest request = HttpRequest.builder().method("POST").uri(URI.create(url + "/" + endpoint())).addHeader("Accept", "application/json").body(HttpRequest.JsonRequestBody.builder().content(requestBody).build()).build();
            HttpResponse<String> response = client.request(request, String.class);
            body = response.getBody();
        }
        Map<String,Object> response = JacksonMapper.ofJson().readValue(body, new TypeReference<>() {});
        if (Boolean.FALSE.equals(response.get("success"))) throw new IllegalStateException("Ashby API request failed: " + response.get("errorInfo"));
        String nextToken = Objects.toString(response.get("syncToken"), syncToken);
        List<Map<String,Object>> events = new ArrayList<>();
        if (response.get("results") instanceof List<?> results) for (Object item : results) if (item instanceof Map<?,?> row && expectedEvent().equalsIgnoreCase(String.valueOf(row.get(eventField())))) events.add((Map<String,Object>) row);
        if (nextToken != null && !Objects.equals(syncToken, nextToken)) kv.put(key, new KVValueAndMetadata(new KVMetadata("Ashby incremental sync watermark", (Duration) null), nextToken), true);
        if (events.isEmpty()) return Optional.empty();
        Map<String,Object> output = Map.of("events", events, "syncToken", Objects.toString(nextToken, ""));
        Execution execution = Execution.builder().id(runContext.getTriggerExecutionId()).namespace(tc.getNamespace()).flowId(tc.getFlowId()).flowRevision(cc.getFlow().getRevision()).state(new State()).trigger(ExecutionTrigger.of(this, output)).build();
        return Optional.of(execution);
    }
}
