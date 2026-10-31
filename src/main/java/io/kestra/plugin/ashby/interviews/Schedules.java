package io.kestra.plugin.ashby.interviews;

import io.kestra.core.models.annotations.Plugin;
import io.kestra.plugin.ashby.AbstractAshbyList;
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
@Schema(title = "List Ashby interviews", description = "Retrieves records from the Ashby API with cursor pagination.")
@Plugin(examples = {@io.kestra.core.models.annotations.Example(title = "List Ashby records", full = true, code = """
id: list_ashby_records
namespace: company.team
tasks:
  - id: list
    type: interviews
    apiKey: "{{ secret('ASHBY_API_KEY') }}"
""")})
public class Schedules extends AbstractAshbyList {
    @Override
    protected String endpoint() { return "interviewSchedule.list"; }
}
