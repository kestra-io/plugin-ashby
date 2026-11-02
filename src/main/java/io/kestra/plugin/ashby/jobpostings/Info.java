package io.kestra.plugin.ashby.jobpostings;

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
@Schema(title = "Retrieve an Ashby job posting", description = "Retrieves one job posting by its Ashby identifier.")
@Plugin(examples = {@Example(title = "Retrieve a job posting by ID", full = true, code = """
    id: ashby_jobposting_info
    namespace: company.team
    tasks:
      - id: info
        type: io.kestra.plugin.ashby.jobpostings.Info
        apiKey: "{{ secret('ASHBY_API_KEY') }}"
        jobPostingId: "00000000-0000-0000-0000-000000000001"
    """)})
public class Info extends AbstractAshbyInfo {
    @NotNull
    @PluginProperty(group = "main")
    @Schema(title = "Ashby job posting ID", description = "Required identifier of the job posting to retrieve.")
    private Property<String> jobPostingId;

    @Override
    protected String endpoint() {
        return "jobPosting.info";
    }

    @Override
    protected Map<String, Object> requestBody(RunContext runContext) throws Exception {
        return Map.of("jobPostingId", runContext.render(jobPostingId).as(String.class).orElseThrow());
    }
}
