package io.kestra.plugin.ashby;

import io.kestra.core.models.tasks.Output;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Value;
import java.util.Map;

@Value
@Builder
public class AshbyInfoOutput implements Output {
    @Schema(title = "Ashby record", description = "The results object returned by the info endpoint, without the API response envelope.")
    Map<String, Object> result;
}
