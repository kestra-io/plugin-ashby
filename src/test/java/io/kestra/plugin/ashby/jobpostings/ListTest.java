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

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

@KestraTest
@WireMockTest
class ListTest {

    @Inject
    private RunContextFactory runContextFactory;

    @Test
    void run(WireMockRuntimeInfo wmRuntimeInfo) throws Exception {
        stubFor(
            post(urlEqualTo("/jobPosting.list"))
                .withHeader("Authorization", matching("Basic .*"))
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
            .build();

        RunContext runContext = runContextFactory.of(ImmutableMap.of());
        List.Output output = task.run(runContext);

        assertThat(output.getBody(), notNullValue());
        
        java.util.List<Map<String, Object>> results = (java.util.List<Map<String, Object>>) output.getBody().get("results");
        
        assertThat(results.size(), is(1));
        assertThat(results.get(0).get("title"), is("Software Engineer"));
    }
}
