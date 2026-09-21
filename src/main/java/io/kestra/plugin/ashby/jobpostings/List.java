package io.kestra.plugin.ashby.jobpostings;

import io.kestra.core.http.HttpResponse;
import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.runners.RunContext;
import io.kestra.core.serializers.JacksonMapper;
import io.kestra.plugin.ashby.AbstractAshbyConnection;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

import java.util.Map;
import com.fasterxml.jackson.core.type.TypeReference;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Retrieve a list of Job Postings from Ashby",
    description = "Retrieves all job postings."
)
@Plugin(
    examples = {
        @Example(
            title = "Fetch all job postings",
            full = true,
            code = """
                id: fetch_job_postings
                namespace: company.team
                
                tasks:
                  - id: list_job_postings
                    type: io.kestra.plugin.ashby.jobpostings.List
                    apiKey: "{{ secret('ASHBY_API_KEY') }}"
                """
        )
    }
)
public class List extends AbstractAshbyConnection implements RunnableTask<List.Output> {

    @Override
    public List.Output run(RunContext runContext) throws Exception {
        HttpResponse<String> response = this.request(runContext, "POST", "/jobPosting.list", java.util.Map.of(), String.class);

        if (response.getBody() == null || response.getBody().trim().isEmpty()) {
            throw new IllegalStateException("Empty response from Ashby API");
        }

        TypeReference<Map<String, Object>> typeRef = new TypeReference<>() {};
        Map<String, Object> body = JacksonMapper.ofJson().readValue(response.getBody(), typeRef);

        return Output.builder()
            .body(body)
            .build();
    }

    @Builder
    @Getter
    public static class Output implements io.kestra.core.models.tasks.Output {
        @Schema(
            title = "The JSON body returned by Ashby API"
        )
        private Map<String, Object> body;
    }
}
