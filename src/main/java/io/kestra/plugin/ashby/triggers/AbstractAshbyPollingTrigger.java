package io.kestra.plugin.ashby.triggers;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.fasterxml.jackson.core.type.TypeReference;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.conditions.ConditionContext;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.triggers.AbstractTrigger;
import io.kestra.core.models.triggers.PollingTriggerInterface;
import io.kestra.core.models.triggers.TriggerContext;
import io.kestra.core.models.triggers.TriggerOutput;
import io.kestra.core.models.triggers.TriggerService;
import io.kestra.core.runners.RunContext;
import io.kestra.core.serializers.JacksonMapper;
import io.kestra.core.storages.kv.KVMetadata;
import io.kestra.core.storages.kv.KVValueAndMetadata;
import io.kestra.plugin.ashby.AshbyClient;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

@SuperBuilder
@ToString
@EqualsAndHashCode(callSuper = true)
@Getter
@NoArgsConstructor
public abstract class AbstractAshbyPollingTrigger extends AbstractTrigger implements PollingTriggerInterface, TriggerOutput<AbstractAshbyPollingTrigger.Output> {
    @NotNull
    @PluginProperty(secret = true, group = "connection")
    @ToString.Exclude
    @Schema(title = "Ashby API key", description = "Required secret API key used for Basic authentication.")
    protected Property<String> apiKey;

    @PluginProperty(group = "connection")
    @Schema(title = "Ashby API base URL", description = "Defaults to https://api.ashbyhq.com.")
    protected Property<String> baseUrl;

    @NotNull
    @Builder.Default
    @PluginProperty(group = "main")
    @Schema(title = "Polling interval", description = "Defaults to PT15M. The first successful poll seeds the state without firing.")
    protected Duration interval = Duration.ofMinutes(15);

    protected Map<String, Object> page(RunContext runContext, String endpoint, Map<String, Object> body) throws Exception {
        return AshbyClient.readResponse(AshbyClient.request(runContext, baseUrl, apiKey, "POST", "/" + endpoint, body, String.class));
    }

    static String stateKey(TriggerContext context) {
        return "ashby_watermark_" + context.getFlowId().length() + "_" + context.getFlowId()
            + "_" + context.getTriggerId().length() + "_" + context.getTriggerId();
    }

    protected Optional<Map<String, Object>> readState(RunContext runContext, TriggerContext context) throws Exception {
        return runContext.namespaceKv(context.getNamespace()).getValue(stateKey(context))
            .map(value -> JacksonMapper.ofJson().convertValue(value.value(), new TypeReference<Map<String, Object>>() {}));
    }

    protected Optional<Execution> saveAndGenerate(ConditionContext conditionContext, TriggerContext context,
        Map<String, Object> state, List<Map<String, Object>> events, String syncToken) throws Exception {
        var execution = events.isEmpty() ? Optional.<Execution>empty()
            : Optional.of(TriggerService.generateExecution(this, conditionContext, context, new Output(events, syncToken)));
        conditionContext.getRunContext().namespaceKv(context.getNamespace()).put(stateKey(context),
            new KVValueAndMetadata(new KVMetadata("Ashby polling state", (Duration) null), state), true);
        return execution;
    }

    public record Output(
        @Schema(title = "Matching events", description = "Applications newly transitioned to Hired, or job postings matching the configured publish/unpublish event. One execution contains all matches from the poll.")
        List<Map<String, Object>> events,
        @Schema(title = "Incremental sync token", description = "Token from the final application page. Absent for job postings, which use a snapshot instead.")
        String syncToken
    ) implements io.kestra.core.models.tasks.Output {
    }
}
