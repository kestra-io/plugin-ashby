package io.kestra.plugin.ashby.openings;

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
@Schema(title = "List Ashby openings", description = "Retrieves records from the Ashby API with cursor pagination.")
@Plugin(examples = {@io.kestra.core.models.annotations.Example(title = "List Ashby records", full = true, code = """
id: list_ashby_records
namespace: company.team
tasks:
  - id: list
    type: openings
    apiKey: "{{ secret('ASHBY_API_KEY') }}"
""")})
public class List extends AbstractAshbyList {
    @Override
    protected String endpoint() { return "opening.list"; }
}
