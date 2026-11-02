package io.kestra.plugin.ashby.triggers;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

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
@Schema(title = "Poll for newly hired applications", description = "Follows application.list incremental sync pages. The first poll seeds the watermark and hired IDs without firing; later updates fire only when an application transitions to Hired.")
@Plugin(examples = {@Example(title = "React to newly hired applications", full = true, code = """
    id: ashby_candidate_hired
    namespace: company.team
    tasks:
      - id: log
        type: io.kestra.plugin.core.log.Log
        message: "{{ trigger.events }}"
    triggers:
      - id: ashby
        type: io.kestra.plugin.ashby.triggers.CandidateHiredTrigger
        apiKey: "{{ secret('ASHBY_API_KEY') }}"
        interval: PT15M
    """)})
public class CandidateHiredTrigger extends AbstractAshbyPollingTrigger {
    @Override
    public Optional<Execution> evaluate(ConditionContext conditionContext, TriggerContext context) throws Exception {
        var runContext = conditionContext.getRunContext();
        var previous = readState(runContext, context);
        var syncToken = previous.map(state -> (String) state.get("syncToken")).orElse(null);
        var hiredIds = new HashSet<String>();
        if (previous.isPresent() && previous.get().get("hiredIds") instanceof List<?> ids) {
            ids.forEach(id -> hiredIds.add(id.toString()));
        }
        var events = new ArrayList<Map<String, Object>>();
        var seenCursors = new HashSet<String>();
        String cursor = null;
        Map<String, Object> body;
        var seeding = previous.isEmpty();
        while (true) {
            var request = new LinkedHashMap<String, Object>();
            if (syncToken != null) {
                request.put("syncToken", syncToken);
            }
            if (cursor != null) {
                request.put("cursor", cursor);
            }
            try {
                body = page(runContext, "application.list", request);
            } catch (AshbyClient.ApiException e) {
                if (syncToken == null || !"sync_token_expired".equals(e.code())) {
                    throw e;
                }
                runContext.logger().warn("Ashby sync token expired; reseeding state. Hires made since the last successful poll will not be reported.");
                syncToken = null;
                cursor = null;
                seenCursors.clear();
                hiredIds.clear();
                events.clear();
                seeding = true;
                continue;
            }
            for (var application : AshbyClient.rows(body)) {
                if (!(application.get("id") instanceof String id) || id.isBlank()) {
                    throw new IllegalStateException("Ashby application has no ID");
                }
                if ("Hired".equalsIgnoreCase(String.valueOf(application.get("status")))) {
                    if (hiredIds.add(id) && !seeding) {
                        events.add(application);
                    }
                } else {
                    hiredIds.remove(id);
                }
            }
            cursor = AshbyClient.nextCursor(body, seenCursors);
            if (cursor == null) {
                break;
            }
        }
        if (!(body.get("syncToken") instanceof String nextToken) || nextToken.isBlank()) {
            throw new IllegalStateException("Ashby final application page has no syncToken");
        }
        return saveAndGenerate(conditionContext, context,
            Map.of("syncToken", nextToken, "hiredIds", new ArrayList<>(hiredIds)), events, nextToken);
    }
}
