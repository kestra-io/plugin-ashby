package io.kestra.plugin.ashby.interviews;

import java.util.Map;
import io.kestra.plugin.ashby.AbstractAshbyList;
import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.property.Property;
import io.kestra.core.runners.RunContext;
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
@Schema(title = "List Ashby interview schedules", description = "Retrieves interview schedules for recruiting operations, following all available cursor pages.")
@Plugin(examples = {@Example(title = "List Ashby interview schedules", full = true, code = """
    id: list_ashby_interview_schedules
    namespace: company.team
    tasks:
      - id: list
        type: io.kestra.plugin.ashby.interviews.Schedules
        apiKey: "{{ secret('ASHBY_API_KEY') }}"
        fetchType: STORE
    """)})
public class Schedules extends AbstractAshbyList {
    @PluginProperty(group = "source")
    @Schema(title = "applicationId", description = "Return schedules for this application. Omitted by default.")
    private Property<String> applicationId;

    @PluginProperty(group = "source")
    @Schema(title = "interviewStageId", description = "Return schedules for this interview stage. Omitted by default.")
    private Property<String> interviewStageId;

    @PluginProperty(group = "source")
    @Schema(title = "createdAfter", description = "Minimum creation time as Unix epoch milliseconds. Omitted by default.")
    private Property<Long> createdAfter;

    @PluginProperty(group = "source")
    @Schema(title = "syncToken", description = "Token from a previous completed sync. Omitted by default.")
    private Property<String> syncToken;

    @Override
    protected String endpoint() {
        return "interviewSchedule.list";
    }

    @Override
    protected Map<String, Object> requestFilters(RunContext runContext) throws Exception {
        var body = super.requestFilters(runContext);
        putFilter(runContext, body, "applicationId", applicationId, String.class);
        putFilter(runContext, body, "interviewStageId", interviewStageId, String.class);
        putFilter(runContext, body, "createdAfter", createdAfter, Long.class);
        putFilter(runContext, body, "syncToken", syncToken, String.class);
        return body;
    }
}
