package io.kestra.plugin.ashby.jobs;

import io.kestra.core.models.annotations.Plugin;
import io.kestra.plugin.ashby.AbstractAshbyInfo;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(title = "Retrieve an Ashby jobs record", description = "Retrieves one record using the corresponding Ashby info endpoint.")
@Plugin(examples = {@io.kestra.core.models.annotations.Example(title = "Retrieve a record", full = true, code = """
id: ashby_info
namespace: company.team
tasks:
  - id: info
    type: io.kestra.plugin.ashby.jobs.Info
    apiKey: "{{ secret('ASHBY_API_KEY') }}"
    parameters:
      id: "record-id"
""")})
public class Info extends AbstractAshbyInfo {
    @Override
    protected String endpoint() { return "job.info"; }
}
