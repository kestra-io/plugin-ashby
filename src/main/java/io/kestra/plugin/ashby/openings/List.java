package io.kestra.plugin.ashby.openings;

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
@Schema(title = "List Ashby openings", description = "Retrieves headcount openings, following all available cursor pages.")
@Plugin(examples = {@Example(title = "List Ashby openings", full = true, code = """
    id: list_ashby_openings
    namespace: company.team
    tasks:
      - id: list
        type: io.kestra.plugin.ashby.openings.List
        apiKey: "{{ secret('ASHBY_API_KEY') }}"
        fetchType: STORE
    """)})
public class List extends AbstractAshbyList {
    @PluginProperty(group = "source")
    @Schema(title = "syncToken", description = "Token from a previous completed sync. Omitted by default.")
    private Property<String> syncToken;

    @PluginProperty(group = "source")
    @Schema(title = "createdAfter", description = "Minimum creation time as Unix epoch milliseconds. Omitted by default.")
    private Property<Long> createdAfter;

    @Override
    protected String endpoint() {
        return "opening.list";
    }

    @Override
    protected Map<String, Object> requestFilters(RunContext runContext) throws Exception {
        var body = super.requestFilters(runContext);
        putFilter(runContext, body, "syncToken", syncToken, String.class);
        putFilter(runContext, body, "createdAfter", createdAfter, Long.class);
        return body;
    }
}
