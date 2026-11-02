package io.kestra.plugin.ashby;

import java.util.Map;

import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.models.property.Property;
import io.kestra.core.runners.RunContext;
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
public abstract class AbstractAshbyInfo extends AbstractAshbyConnection implements RunnableTask<AshbyInfoOutput> {
    protected abstract String endpoint();

    protected abstract Map<String, Object> requestBody(RunContext runContext) throws Exception;

    protected String requiredId(RunContext runContext, Property<String> id, String name) throws Exception {
        return runContext.render(id).as(String.class).filter(value -> !value.isBlank())
            .orElseThrow(() -> new IllegalArgumentException(name + " is required: set it to the Ashby " + name + " ID"));
    }

    @Override
    @SuppressWarnings("unchecked")
    public AshbyInfoOutput run(RunContext runContext) throws Exception {
        var body = AshbyClient.readResponse(request(runContext, "POST", "/" + endpoint(), requestBody(runContext), String.class));
        if (!(body.get("results") instanceof Map<?, ?> result)) {
            throw new IllegalStateException("Ashby info response must contain a results object");
        }
        return AshbyInfoOutput.builder().result((Map<String, Object>) result).build();
    }
}
