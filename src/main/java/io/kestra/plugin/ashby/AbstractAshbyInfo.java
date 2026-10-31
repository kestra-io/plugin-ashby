package io.kestra.plugin.ashby;

import com.fasterxml.jackson.core.type.TypeReference;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.runners.RunContext;
import io.kestra.core.serializers.JacksonMapper;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

import java.util.Map;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
public abstract class AbstractAshbyInfo extends AbstractAshbyConnection implements RunnableTask<AshbyInfoOutput> {
    @Schema(title = "Ashby info request parameters", description = "Fields required by the selected Ashby info endpoint.")
    @PluginProperty(group = "main")
    protected Property<Map<String, Object>> parameters;

    protected abstract String endpoint();

    @Override
    public AshbyInfoOutput run(RunContext runContext) throws Exception {
        Map<String, Object> body = parameters == null ? Map.of() : runContext.render(parameters).asMap(String.class, Object.class);
        var response = request(runContext, "POST", "/" + endpoint(), body, String.class);
        if (response.getBody() == null || response.getBody().isBlank()) throw new IllegalStateException("Empty response from Ashby API");
        Map<String, Object> result = JacksonMapper.ofJson().readValue(response.getBody(), new TypeReference<>() {});
        if (Boolean.FALSE.equals(result.get("success"))) throw new IllegalStateException("Ashby API request failed: " + result.getOrDefault("errorInfo", result));
        return AshbyInfoOutput.builder().result(result).build();
    }
}
