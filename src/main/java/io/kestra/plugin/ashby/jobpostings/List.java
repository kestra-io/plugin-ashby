package io.kestra.plugin.ashby.jobpostings;

import io.kestra.core.http.HttpResponse;
import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.models.tasks.common.FetchOutput;
import io.kestra.core.models.tasks.common.FetchType;
import io.kestra.core.runners.RunContext;
import io.kestra.core.serializers.JacksonMapper;
import io.kestra.core.serializers.FileSerde;
import io.kestra.plugin.ashby.AbstractAshbyConnection;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.io.BufferedOutputStream;
import java.util.Collections;
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
            title = "Fetch all job postings and store them as internal storage",
            full = true,
            code = """
                id: fetch_job_postings
                namespace: company.team
                
                tasks:
                  - id: list_job_postings
                    type: io.kestra.plugin.ashby.jobpostings.List
                    apiKey: "{{ secret('ASHBY_API_KEY') }}"
                    fetchType: STORE
                """
        )
    }
)
public class List extends AbstractAshbyConnection implements RunnableTask<FetchOutput> {

    @Builder.Default
    @Schema(
        title = "The way you want to store the data",
        description = "FETCH_ONE outputs the first row, "
            + "FETCH outputs all the rows, "
            + "STORE stores all rows in a file, "
            + "NONE does nothing."
    )
    private Property<FetchType> fetchType = Property.ofValue(FetchType.STORE);

    @Override
    public FetchOutput run(RunContext runContext) throws Exception {
        FetchType rFetchType = runContext.render(this.fetchType).as(FetchType.class).orElse(FetchType.STORE);

        HttpResponse<String> response = this.request(runContext, "POST", "/jobPosting.list", java.util.Map.of(), String.class);

        if (response.getBody() == null || response.getBody().isBlank()) {
            throw new IllegalStateException("Empty response from Ashby API");
        }

        TypeReference<Map<String, Object>> typeRef = new TypeReference<>() {};
        Map<String, Object> body = JacksonMapper.ofJson().readValue(response.getBody(), typeRef);

        if (Boolean.FALSE.equals(body.get("success"))) {
            String errorMsg = body.get("errorInfo") instanceof Map<?, ?> errorInfo && errorInfo.get("message") != null 
                ? String.valueOf(errorInfo.get("message")) 
                : JacksonMapper.ofJson().writeValueAsString(body);
            throw new IllegalStateException("Ashby API request failed: " + errorMsg);
        }

        java.util.List<Map<String, Object>> results = body.get("results") instanceof java.util.List<?> list 
            ? (java.util.List<Map<String, Object>>) list 
            : java.util.List.of();

        FetchOutput.FetchOutputBuilder outputBuilder = FetchOutput.builder();

        switch (rFetchType) {
            case FETCH_ONE -> {
                if (!results.isEmpty()) {
                    outputBuilder.row(results.get(0));
                }
                outputBuilder.size(results.isEmpty() ? 0L : 1L);
            }
            case FETCH -> {
                outputBuilder.rows(new java.util.ArrayList<>(results))
                             .size((long) results.size());
            }
            case STORE -> {
                File tempFile = runContext.workingDir().createTempFile(".ion").toFile();
                try (OutputStream outputStream = new BufferedOutputStream(new FileOutputStream(tempFile), FileSerde.BUFFER_SIZE)) {
                    for (Map<String, Object> row : results) {
                        FileSerde.write(outputStream, row);
                    }
                }
                outputBuilder.uri(runContext.storage().putFile(tempFile))
                             .size((long) results.size());
            }
            case NONE -> {
                outputBuilder.size((long) results.size());
            }
        }

        return outputBuilder.build();
    }
}
