package io.kestra.plugin.ashby.directory;

import io.kestra.plugin.ashby.AbstractAshbyList;
import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
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
@Schema(title = "List Ashby locations", description = "Retrieves office and recruiting locations, following all available cursor pages.")
@Plugin(examples = {@Example(title = "List Ashby locations", full = true, code = """
    id: list_ashby_locations
    namespace: company.team
    tasks:
      - id: list
        type: io.kestra.plugin.ashby.directory.Locations
        apiKey: "{{ secret('ASHBY_API_KEY') }}"
        fetchType: STORE
    """)})
public class Locations extends AbstractAshbyList {
    @Override
    protected String endpoint() {
        return "location.list";
    }
}
