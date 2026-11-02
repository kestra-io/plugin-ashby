package io.kestra.plugin.ashby.applications;

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
@Schema(title = "Retrieve an Ashby application", description = "Retrieves one application by its Ashby identifier.")
@Plugin(examples = {@Example(title = "Retrieve an application by ID", full = true, code = """
    id: ashby_application_info
    namespace: company.team
    tasks:
      - id: info
        type: io.kestra.plugin.ashby.applications.Info
        apiKey: "{{ secret('ASHBY_API_KEY') }}"
        applicationId: "00000000-0000-0000-0000-000000000001"
    """)})
public class Info extends AbstractAshbyInfo {
    @NotNull
    @PluginProperty(group = "main")
    @Schema(title = "Ashby application ID", description = "Required identifier of the application to retrieve.")
    private Property<String> applicationId;

    @Override
    protected String endpoint() {
        return "application.info";
    }

    @Override
    protected Map<String, Object> requestBody(RunContext runContext) throws Exception {
        return Map.of("applicationId", runContext.render(applicationId).as(String.class).orElseThrow());
    }
}
