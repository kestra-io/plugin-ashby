package io.kestra.plugin.ashby.candidates;

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
@Schema(title = "List Ashby candidates", description = "Retrieves candidate profiles, following all available cursor pages.")
@Plugin(examples = {@Example(title = "List Ashby candidates", full = true, code = """
    id: list_ashby_candidates
    namespace: company.team
    tasks:
      - id: list
        type: io.kestra.plugin.ashby.candidates.List
        apiKey: "{{ secret('ASHBY_API_KEY') }}"
        fetchType: STORE
    """)})
public class List extends AbstractAshbyList {
    @PluginProperty(group = "source")
    @Schema(title = "syncToken", description = "Token from a previous completed sync. Omitted by default.")
    private Property<String> syncToken;

    @Override
    protected String endpoint() {
        return "candidate.list";
    }

    @Override
    protected Map<String, Object> requestFilters(RunContext runContext) throws Exception {
        var body = super.requestFilters(runContext);
        putFilter(runContext, body, "syncToken", syncToken, String.class);
        return body;
    }
}
