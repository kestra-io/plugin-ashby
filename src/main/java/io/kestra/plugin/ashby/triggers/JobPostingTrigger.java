package io.kestra.plugin.ashby.triggers;
import io.kestra.core.models.annotations.Plugin;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.EqualsAndHashCode; import lombok.Getter; import lombok.NoArgsConstructor; import lombok.ToString; import lombok.experimental.SuperBuilder;
@SuperBuilder @ToString @EqualsAndHashCode(callSuper = true) @Getter @NoArgsConstructor
@Schema(title = "Ashby JobPosting trigger", description = "Polls Ashby for jobposting events using the incremental sync token.")
@Plugin(examples = {@io.kestra.core.models.annotations.Example(title = "Poll for Ashby jobposting events", full = true, code = """
id: ashby_jobposting
namespace: company.team
triggers:
  - id: ashby
    type: io.kestra.plugin.ashby.triggers.JobPostingTrigger
    apiKey: "{{ secret('ASHBY_API_KEY') }}"
    interval: PT15M
""")})
public class JobPostingTrigger extends AbstractAshbyPollingTrigger {
 @Override protected String endpoint() { return "jobPosting.list"; }
 @Override protected String eventField() { return "event"; }
 @Override protected String expectedEvent() { return "published"; }
}
