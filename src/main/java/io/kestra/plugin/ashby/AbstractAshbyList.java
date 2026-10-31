package io.kestra.plugin.ashby;

import com.fasterxml.jackson.core.type.TypeReference;
import io.kestra.core.http.HttpResponse;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.models.tasks.common.FetchOutput;
import io.kestra.core.models.tasks.common.FetchType;
import io.kestra.core.runners.RunContext;
import io.kestra.core.serializers.FileSerde;
import io.kestra.core.serializers.JacksonMapper;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.EqualsAndHashCode;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Shared list endpoint behavior for Ashby RPC list methods. */
@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
public abstract class AbstractAshbyList extends AbstractAshbyConnection implements RunnableTask<FetchOutput> {
    @Schema(title = "The way you want to store the data")
    @Builder.Default
    protected Property<FetchType> fetchType = Property.ofValue(FetchType.STORE);

    @io.swagger.v3.oas.annotations.media.Schema(title = "Ashby API request filters", description = "Additional Ashby list request fields such as status, jobId, listedOnly, or syncToken.")
    @io.kestra.core.models.annotations.PluginProperty(group = "advanced")
    protected Property<Map<String, Object>> filters;

    protected abstract String endpoint();

    @Override
    public FetchOutput run(RunContext runContext) throws Exception {
        List<Map<String, Object>> rows = new ArrayList<>();
        String cursor = null;
        do {
            Map<String, Object> requestBody = new java.util.LinkedHashMap<>();
            if (filters != null) requestBody.putAll(runContext.render(filters).asMap(String.class, Object.class));
            if (cursor != null) requestBody.put("cursor", cursor);
            HttpResponse<String> response = request(runContext, "POST", "/" + endpoint(), requestBody, String.class);
            if (response.getBody() == null || response.getBody().isBlank()) throw new IllegalStateException("Empty response from Ashby API");
            Map<String, Object> body = JacksonMapper.ofJson().readValue(response.getBody(), new TypeReference<>() {});
            if (Boolean.FALSE.equals(body.get("success"))) throw new IllegalStateException("Ashby API request failed: " + body.getOrDefault("errorInfo", body));
            if (body.get("results") instanceof List<?> page) for (Object row : page) if (row instanceof Map<?, ?> map) rows.add((Map<String, Object>) map);
            cursor = Boolean.TRUE.equals(body.get("moreDataAvailable")) && body.get("nextCursor") != null ? body.get("nextCursor").toString() : null;
        } while (cursor != null);

        FetchType type = runContext.render(fetchType).as(FetchType.class).orElse(FetchType.STORE);
        FetchOutput.FetchOutputBuilder out = FetchOutput.builder();
        switch (type) {
            case FETCH_ONE -> { if (!rows.isEmpty()) out.row(rows.get(0)); out.size(rows.isEmpty() ? 0L : 1L); }
            case FETCH -> out.rows(new ArrayList<Object>(rows)).size((long) rows.size());
            case NONE -> out.size((long) rows.size());
            case STORE -> {
                File file = runContext.workingDir().createTempFile(".ion").toFile();
                try (OutputStream stream = new BufferedOutputStream(new FileOutputStream(file), FileSerde.BUFFER_SIZE)) { for (Map<String, Object> row : rows) FileSerde.write(stream, row); }
                out.uri(runContext.storage().putFile(file)).size((long) rows.size());
            }
        }
        return out.build();
    }
}
