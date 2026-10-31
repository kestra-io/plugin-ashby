package io.kestra.plugin.ashby.triggers;
import io.kestra.core.models.annotations.Plugin;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.EqualsAndHashCode; import lombok.Getter; import lombok.NoArgsConstructor; import lombok.ToString; import lombok.experimental.SuperBuilder;
@SuperBuilder @ToString @EqualsAndHashCode(callSuper = true) @Getter @NoArgsConstructor
@Schema(title = "Ashby CandidateHired trigger", description = "Polls Ashby for candidatehired events using the incremental sync token.")
@Plugin(examples = {@io.kestra.core.models.annotations.Example(title = "Poll for Ashby candidatehired events", full = true, code = """
id: ashby_candidatehired
namespace: company.team
triggers:
  - id: ashby
    type: io.kestra.plugin.ashby.triggers.CandidateHiredTrigger
    apiKey: "{{ secret('ASHBY_API_KEY') }}"
    interval: PT15M
""")})
public class CandidateHiredTrigger extends AbstractAshbyPollingTrigger {
 @Override protected String endpoint() { return "application.list"; }
 @Override protected String eventField() { return "status"; }
 @Override protected String expectedEvent() { return "Hired"; }
}
