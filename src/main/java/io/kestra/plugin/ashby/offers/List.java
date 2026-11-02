package io.kestra.plugin.ashby.offers;

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
@Schema(title = "List Ashby offers", description = "Retrieves offers with their latest version, following all available cursor pages.")
@Plugin(examples = {@Example(title = "List Ashby offers", full = true, code = """
    id: list_ashby_offers
    namespace: company.team
    tasks:
      - id: list
        type: io.kestra.plugin.ashby.offers.List
        apiKey: "{{ secret('ASHBY_API_KEY') }}"
        fetchType: STORE
    """)})
public class List extends AbstractAshbyList {
    @PluginProperty(group = "source")
    @Schema(title = "offerStatus", description = "Filter by offer statuses. Omitted by default.")
    private Property<java.util.List<String>> offerStatus;

    @PluginProperty(group = "source")
    @Schema(title = "acceptanceStatus", description = "Filter by acceptance statuses. Omitted by default.")
    private Property<java.util.List<String>> acceptanceStatus;

    @PluginProperty(group = "source")
    @Schema(title = "applicationId", description = "Return offers for this application. Omitted by default.")
    private Property<String> applicationId;

    @PluginProperty(group = "source")
    @Schema(title = "syncToken", description = "Token from a previous completed sync. Omitted by default.")
    private Property<String> syncToken;

    @Override
    protected String endpoint() {
        return "offer.list";
    }

    @Override
    protected Map<String, Object> requestFilters(RunContext runContext) throws Exception {
        var body = super.requestFilters(runContext);
        putListFilter(runContext, body, "offerStatus", offerStatus);
        putListFilter(runContext, body, "acceptanceStatus", acceptanceStatus);
        putFilter(runContext, body, "applicationId", applicationId, String.class);
        putFilter(runContext, body, "syncToken", syncToken, String.class);
        return body;
    }
}
