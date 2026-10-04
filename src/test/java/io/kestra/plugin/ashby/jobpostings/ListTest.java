package io.kestra.plugin.ashby.jobpostings;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import com.google.common.collect.ImmutableMap;
import io.kestra.core.models.property.Property;
import io.kestra.core.runners.RunContext;
import io.kestra.core.runners.RunContextFactory;
import io.kestra.core.junit.annotations.KestraTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.Map;
import io.kestra.core.utils.IdUtils;
import io.kestra.core.models.tasks.common.FetchOutput;
import io.kestra.core.models.tasks.common.FetchType;
import io.kestra.core.serializers.FileSerde;
import org.junit.jupiter.api.Assertions;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.hasSize;

@KestraTest
@WireMockTest
class ListTest {
    private static final String AUTH_HEADER = "Basic " + Base64.getEncoder()
        .encodeToString("dummy-api-key:".getBytes(StandardCharsets.UTF_8));
    private static final String TWO_POSTINGS = """
        {"success": true, "results": [{"id": "1", "title": "Engineer"}, {"id": "2", "title": "Designer"}]}""";

    @Inject
    private RunContextFactory runContextFactory;

    @Test
    void run(WireMockRuntimeInfo wmRuntimeInfo) throws Exception {
        stubFor(
            post(urlEqualTo("/jobPosting.list"))
                .withHeader("Authorization", equalTo(AUTH_HEADER))
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"results\": [{\"id\": \"123\", \"title\": \"Software Engineer\"}]}")
                )
        );

        List task = List.builder()
            .id(IdUtils.create())
            .type(List.class.getName())
            .apiKey(Property.ofValue("dummy-api-key"))
            .baseUrl(Property.ofValue(wmRuntimeInfo.getHttpBaseUrl()))
            .fetchType(Property.ofValue(FetchType.FETCH))
            .build();

        RunContext runContext = runContextFactory.of(ImmutableMap.of());
        FetchOutput output = task.run(runContext);

        assertThat(output.getRows(), notNullValue());
        assertThat(output.getSize(), is(1L));
        
        java.util.List<Object> results = output.getRows();
        Map<String, Object> firstRow = (Map<String, Object>) results.get(0);
        assertThat(firstRow.get("title"), is("Software Engineer"));
    }

    @Test
    void run_empty(WireMockRuntimeInfo wmRuntimeInfo) throws Exception {
        stubFor(
            post(urlEqualTo("/jobPosting.list"))
                .withHeader("Authorization", equalTo(AUTH_HEADER))
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"results\": []}")
                )
        );

        List task = List.builder()
            .id(IdUtils.create())
            .type(List.class.getName())
            .apiKey(Property.ofValue("dummy-api-key"))
            .baseUrl(Property.ofValue(wmRuntimeInfo.getHttpBaseUrl()))
            .fetchType(Property.ofValue(FetchType.FETCH))
            .build();

        RunContext runContext = runContextFactory.of(ImmutableMap.of());
        FetchOutput output = task.run(runContext);

        assertThat(output.getRows(), notNullValue());
        assertThat(output.getSize(), is(0L));
        assertThat(output.getRows().size(), is(0));
    }

    @Test
    void run_error(WireMockRuntimeInfo wmRuntimeInfo) throws Exception {
        stubFor(
            post(urlEqualTo("/jobPosting.list"))
                .withHeader("Authorization", equalTo(AUTH_HEADER))
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"success\": false, \"errorInfo\": {\"message\": \"Invalid API Key\"}}")
                )
        );

        List task = List.builder()
            .id(IdUtils.create())
            .type(List.class.getName())
            .apiKey(Property.ofValue("dummy-api-key"))
            .baseUrl(Property.ofValue(wmRuntimeInfo.getHttpBaseUrl()))
            .fetchType(Property.ofValue(FetchType.FETCH))
            .build();

        RunContext runContext = runContextFactory.of(ImmutableMap.of());
        
        IllegalStateException exception = Assertions.assertThrows(IllegalStateException.class, () -> {
            task.run(runContext);
        });

        assertThat(exception.getMessage(), is("Ashby API request failed: Invalid API Key"));
    }

    @Test
    void run_error_fallback(WireMockRuntimeInfo wmRuntimeInfo) throws Exception {
        stubFor(
            post(urlEqualTo("/jobPosting.list"))
                .withHeader("Authorization", equalTo(AUTH_HEADER))
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"success\": false, \"errors\": [\"Unknown token\"]}")
                )
        );

        List task = List.builder()
            .id(IdUtils.create())
            .type(List.class.getName())
            .apiKey(Property.ofValue("dummy-api-key"))
            .baseUrl(Property.ofValue(wmRuntimeInfo.getHttpBaseUrl()))
            .fetchType(Property.ofValue(FetchType.FETCH))
            .build();

        RunContext runContext = runContextFactory.of(ImmutableMap.of());
        
        IllegalStateException exception = Assertions.assertThrows(IllegalStateException.class, () -> {
            task.run(runContext);
        });

        assertThat(exception.getMessage(), is("Ashby API request failed: {\"success\":false,\"errors\":[\"Unknown token\"]}"));
    }

    @Test
    void store(WireMockRuntimeInfo wmRuntimeInfo) throws Exception {
        stubFor(post(urlEqualTo("/jobPosting.list"))
            .withHeader("Authorization", equalTo(AUTH_HEADER))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(TWO_POSTINGS)
            )
        );

        List task = List.builder()
            .id(IdUtils.create())
            .type(List.class.getName())
            .apiKey(Property.ofValue("dummy-api-key"))
            .baseUrl(Property.ofValue(wmRuntimeInfo.getHttpBaseUrl()))
            .build(); // default fetchType: STORE

        RunContext runContext = runContextFactory.of(Map.of());
        FetchOutput output = task.run(runContext);

        assertThat(output.getSize(), is(2L));
        assertThat(output.getUri(), notNullValue());
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(runContext.storage().getFile(output.getUri())))) {
            java.util.List<Object> rows = FileSerde.readAll(reader).collectList().block();
            assertThat(rows, hasSize(2));
            assertThat(((Map<String, Object>) rows.get(1)).get("title"), is("Designer"));
        }
    }

    @Test
    void fetchOne(WireMockRuntimeInfo wmRuntimeInfo) throws Exception {
        stubFor(post(urlEqualTo("/jobPosting.list"))
            .withHeader("Authorization", equalTo(AUTH_HEADER))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(TWO_POSTINGS)
            )
        );

        List task = List.builder()
            .id(IdUtils.create())
            .type(List.class.getName())
            .apiKey(Property.ofValue("dummy-api-key"))
            .baseUrl(Property.ofValue(wmRuntimeInfo.getHttpBaseUrl()))
            .fetchType(Property.ofValue(FetchType.FETCH_ONE))
            .build();

        FetchOutput output = task.run(runContextFactory.of(Map.of()));

        assertThat(output.getSize(), is(1L));
        assertThat((String) output.getRow().get("id"), is("1"));
        assertThat(output.getRows(), nullValue());
    }
}
