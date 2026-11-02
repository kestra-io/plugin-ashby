package io.kestra.plugin.ashby.triggers;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.property.Property;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.conditions.ConditionContext;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.triggers.TriggerContext;
import io.kestra.plugin.ashby.AshbyClient;
import io.swagger.v3.oas.annotations.media.Schema;
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
@Schema(title = "Poll for job posting publication changes", description = "Compares listed, published job postings with a KV snapshot. The first successful poll seeds the snapshot without firing. Does not use incremental sync tokens.")
@Plugin(examples = {@Example(title = "React when a job posting is published", full = true, code = """
    id: ashby_job_posting
    namespace: company.team
    tasks:
      - id: log
        type: io.kestra.plugin.core.log.Log
        message: "{{ trigger.events }}"
    triggers:
      - id: ashby
        type: io.kestra.plugin.ashby.triggers.JobPostingTrigger
        apiKey: "{{ secret('ASHBY_API_KEY') }}"
        event: PUBLISHED
        interval: PT15M
    """)})
public class JobPostingTrigger extends AbstractAshbyPollingTrigger {
    @NotNull
    @Builder.Default
    @PluginProperty(group = "main")
    @Schema(title = "Publication event", description = "PUBLISHED (default) detects newly listed postings or a changed publishedDate. UNPUBLISHED detects postings no longer listed or returned by the API.")
    private Property<Event> event = Property.ofValue(Event.PUBLISHED);

    public enum Event {
        PUBLISHED,
        UNPUBLISHED
    }

    @Override
    @SuppressWarnings("unchecked")
    public Optional<Execution> evaluate(ConditionContext conditionContext, TriggerContext context) throws Exception {
        var runContext = conditionContext.getRunContext();
        var rEvent = runContext.render(event).as(Event.class).orElse(Event.PUBLISHED);
        var previous = readState(runContext, context);
        var snapshot = new LinkedHashMap<String, Map<String, Object>>();
        var seenCursors = new HashSet<String>();
        String cursor = null;
        do {
            var request = new LinkedHashMap<String, Object>();
            request.put("listedOnly", true);
            if (cursor != null) {
                request.put("cursor", cursor);
            }
            var body = page(runContext, "jobPosting.list", request);
            for (var posting : AshbyClient.rows(body)) {
                if (!(posting.get("id") instanceof String id) || id.isBlank()) {
                    throw new IllegalStateException("Ashby job posting has no ID");
                }
                if (!Boolean.FALSE.equals(posting.get("isListed"))) {
                    snapshot.put(id, posting);
                }
            }
            cursor = AshbyClient.nextCursor(body, seenCursors);
        } while (cursor != null);
        var events = new ArrayList<Map<String, Object>>();
        if (previous.isPresent()) {
            var oldSnapshot = (Map<String, Map<String, Object>>) previous.get().get("postings");
            if (rEvent == Event.PUBLISHED) {
                snapshot.forEach((id, posting) -> {
                    if (!oldSnapshot.containsKey(id) || !Objects.equals(oldSnapshot.get(id).get("publishedDate"), posting.get("publishedDate"))) {
                        events.add(posting);
                    }
                });
            } else {
                oldSnapshot.forEach((id, posting) -> {
                    if (!snapshot.containsKey(id)) {
                        events.add(posting);
                    }
                });
            }
        }
        return saveAndGenerate(conditionContext, context, Map.of("postings", snapshot), events, null);
    }
}
