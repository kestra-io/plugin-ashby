package io.kestra.plugin.ashby;

import java.io.BufferedOutputStream;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.models.tasks.common.FetchOutput;
import io.kestra.core.models.tasks.common.FetchType;
import io.kestra.core.runners.RunContext;
import io.kestra.core.serializers.FileSerde;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
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
public abstract class AbstractAshbyList extends AbstractAshbyConnection implements RunnableTask<FetchOutput> {
    @Schema(title = "How to return the records", description = """
        FETCH_ONE returns only the first record. FETCH returns all records in memory.
        STORE streams all records into an Ion file in internal storage (the default).
        NONE returns only the record count.
        """)
    @PluginProperty(group = "processing")
    @Builder.Default
    protected Property<FetchType> fetchType = Property.ofValue(FetchType.STORE);

    @Schema(title = "Additional Ashby request filters", description = "Optional endpoint-specific fields. Typed properties take precedence. Pagination cursors are managed automatically.")
    @PluginProperty(group = "advanced")
    protected Property<Map<String, Object>> filters;

    protected abstract String endpoint();

    protected Map<String, Object> requestFilters(RunContext runContext) throws Exception {
        return filters == null ? new LinkedHashMap<>() : new LinkedHashMap<>(runContext.render(filters).asMap(String.class, Object.class));
    }

    protected <T> void putFilter(RunContext runContext, Map<String, Object> body, String field, Property<T> property, Class<T> cls) throws Exception {
        runContext.render(property).as(cls).ifPresent(value -> body.put(field, value));
    }

    protected void putListFilter(RunContext runContext, Map<String, Object> body, String field, Property<List<String>> property) throws Exception {
        if (property != null) {
            body.put(field, runContext.render(property).asList(String.class));
        }
    }

    @Override
    public FetchOutput run(RunContext runContext) throws Exception {
        var rFetchType = runContext.render(fetchType).as(FetchType.class).orElse(FetchType.STORE);
        var rFilters = requestFilters(runContext);
        rFilters.remove("cursor");
        var rows = new ArrayList<Object>();
        Map<String, Object> first = null;
        var count = 0L;
        var seenCursors = new HashSet<String>();
        String cursor = null;
        var file = rFetchType == FetchType.STORE ? runContext.workingDir().createTempFile(".ion").toFile() : null;
        try (OutputStream stream = file == null ? null : new BufferedOutputStream(new FileOutputStream(file), FileSerde.BUFFER_SIZE)) {
            do {
                var requestBody = new LinkedHashMap<>(rFilters);
                if (cursor != null) {
                    requestBody.put("cursor", cursor);
                }
                var body = AshbyClient.readResponse(request(runContext, "POST", "/" + endpoint(), requestBody, String.class));
                var page = AshbyClient.rows(body);
                for (var row : page) {
                    count++;
                    switch (rFetchType) {
                        case STORE -> FileSerde.write(stream, row);
                        case FETCH -> rows.add(row);
                        case FETCH_ONE -> {
                            if (first == null) {
                                first = row;
                            }
                        }
                        case NONE -> { }
                    }
                }
                if (rFetchType == FetchType.FETCH_ONE && first != null) {
                    break;
                }
                cursor = AshbyClient.nextCursor(body, seenCursors);
            } while (cursor != null);
        }
        var output = FetchOutput.builder();
        switch (rFetchType) {
            case FETCH_ONE -> output.row(first).size(first == null ? 0L : 1L);
            case FETCH -> output.rows(rows).size(count);
            case NONE -> output.size(count);
            case STORE -> output.uri(runContext.storage().putFile(file)).size(count);
        }
        return output.build();
    }
}
