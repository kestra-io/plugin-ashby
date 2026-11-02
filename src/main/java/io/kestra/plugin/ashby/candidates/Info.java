package io.kestra.plugin.ashby.candidates;

import java.util.Map;
import jakarta.validation.constraints.NotNull;
import io.kestra.plugin.ashby.AbstractAshbyInfo;
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
@Schema(title = "Retrieve an Ashby candidate", description = "Retrieves one candidate by its Ashby identifier.")
@Plugin(examples = {@Example(title = "Retrieve a candidate by ID", full = true, code = """
    id: ashby_candidate_info
    namespace: company.team
    tasks:
      - id: info
        type: io.kestra.plugin.ashby.candidates.Info
        apiKey: "{{ secret('ASHBY_API_KEY') }}"
        candidateId: "00000000-0000-0000-0000-000000000001"
    """)})
public class Info extends AbstractAshbyInfo {
    @NotNull
    @PluginProperty(group = "main")
    @Schema(title = "Ashby candidate ID", description = "Required identifier of the candidate to retrieve.")
    private Property<String> candidateId;

    @Override
    protected String endpoint() {
        return "candidate.info";
    }

    @Override
    protected Map<String, Object> requestBody(RunContext runContext) throws Exception {
        return Map.of("candidateId", runContext.render(candidateId).as(String.class).orElseThrow());
    }
}
