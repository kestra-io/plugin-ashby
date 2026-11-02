package io.kestra.plugin.ashby.jobs;

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
@Schema(title = "List Ashby jobs", description = "Retrieves jobs and their hiring details, following all available cursor pages.")
@Plugin(examples = {@Example(title = "List Ashby jobs", full = true, code = """
    id: list_ashby_jobs
    namespace: company.team
    tasks:
      - id: list
        type: io.kestra.plugin.ashby.jobs.List
        apiKey: "{{ secret('ASHBY_API_KEY') }}"
        fetchType: STORE
    """)})
public class List extends AbstractAshbyList {
    @PluginProperty(group = "source")
    @Schema(title = "status", description = "Filter by Draft, Open, Closed, or Archived statuses. Omitted by default.")
    private Property<java.util.List<String>> status;

    @PluginProperty(group = "source")
    @Schema(title = "syncToken", description = "Token from a previous completed sync. Omitted by default.")
    private Property<String> syncToken;

    @Override
    protected String endpoint() {
        return "job.list";
    }

    @Override
    protected Map<String, Object> requestFilters(RunContext runContext) throws Exception {
        var body = super.requestFilters(runContext);
        putListFilter(runContext, body, "status", status);
        putFilter(runContext, body, "syncToken", syncToken, String.class);
        return body;
    }
}
