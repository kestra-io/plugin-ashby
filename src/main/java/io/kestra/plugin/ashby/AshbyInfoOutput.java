package io.kestra.plugin.ashby;

import io.kestra.core.models.tasks.Output;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Value;
import java.util.Map;

@Value
@Builder
public class AshbyInfoOutput implements Output {
    @Schema(title = "Ashby API response", description = "The successful response returned by the Ashby info endpoint.")
    Map<String, Object> result;
}
