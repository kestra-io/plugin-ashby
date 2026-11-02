package io.kestra.plugin.ashby;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Stream;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.common.FetchType;
import io.kestra.core.runners.RunContextFactory;
import io.kestra.core.serializers.FileSerde;
import io.kestra.core.http.HttpResponse;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.junit.jupiter.api.Assertions.*;

@KestraTest
@WireMockTest
class AshbyTasksTest {
    @Inject
    RunContextFactory runContextFactory;

    record ListCase(String endpoint, String filters, Function<String, AbstractAshbyList> task) {
        @Override
        public String toString() {
            return endpoint;
        }
    }

    static Stream<ListCase> lists() {
        return Stream.of(
            new ListCase("jobPosting.list", "{\"listedOnly\": true}", url -> io.kestra.plugin.ashby.jobpostings.List.builder()
                .id("list").type("io.kestra.plugin.ashby.jobpostings.List").apiKey(Property.ofValue("test-key"))
                .baseUrl(Property.ofValue(url)).fetchType(Property.ofValue(FetchType.FETCH)).listedOnly(Property.ofValue(true)).build()),
            new ListCase("job.list", "{\"status\": [\"Open\"]}", url -> io.kestra.plugin.ashby.jobs.List.builder()
                .id("list").type("io.kestra.plugin.ashby.jobs.List").apiKey(Property.ofValue("test-key"))
                .baseUrl(Property.ofValue(url)).fetchType(Property.ofValue(FetchType.FETCH)).status(Property.ofValue(List.of("Open"))).build()),
            new ListCase("application.list", "{\"jobId\": \"job-1\"}", url -> io.kestra.plugin.ashby.applications.List.builder()
                .id("list").type("io.kestra.plugin.ashby.applications.List").apiKey(Property.ofValue("test-key"))
                .baseUrl(Property.ofValue(url)).fetchType(Property.ofValue(FetchType.FETCH)).jobId(Property.ofValue("job-1")).build()),
            new ListCase("candidate.list", "{\"syncToken\": \"sync-1\"}", url -> io.kestra.plugin.ashby.candidates.List.builder()
                .id("list").type("io.kestra.plugin.ashby.candidates.List").apiKey(Property.ofValue("test-key"))
                .baseUrl(Property.ofValue(url)).fetchType(Property.ofValue(FetchType.FETCH)).syncToken(Property.ofValue("sync-1")).build()),
            new ListCase("opening.list", "{\"createdAfter\": 123}", url -> io.kestra.plugin.ashby.openings.List.builder()
                .id("list").type("io.kestra.plugin.ashby.openings.List").apiKey(Property.ofValue("test-key"))
                .baseUrl(Property.ofValue(url)).fetchType(Property.ofValue(FetchType.FETCH)).createdAfter(Property.ofValue(123L)).build()),
            new ListCase("offer.list", "{\"offerStatus\": [\"Approved\"]}", url -> io.kestra.plugin.ashby.offers.List.builder()
                .id("list").type("io.kestra.plugin.ashby.offers.List").apiKey(Property.ofValue("test-key"))
                .baseUrl(Property.ofValue(url)).fetchType(Property.ofValue(FetchType.FETCH)).offerStatus(Property.ofValue(List.of("Approved"))).build()),
            new ListCase("interviewSchedule.list", "{\"applicationId\": \"app-1\"}", url -> io.kestra.plugin.ashby.interviews.Schedules.builder()
                .id("list").type("io.kestra.plugin.ashby.interviews.Schedules").apiKey(Property.ofValue("test-key"))
                .baseUrl(Property.ofValue(url)).fetchType(Property.ofValue(FetchType.FETCH)).applicationId(Property.ofValue("app-1")).build()),
            new ListCase("department.list", "{}", url -> io.kestra.plugin.ashby.directory.Departments.builder()
                .id("list").type("io.kestra.plugin.ashby.directory.Departments").apiKey(Property.ofValue("test-key"))
                .baseUrl(Property.ofValue(url)).fetchType(Property.ofValue(FetchType.FETCH)).build()),
            new ListCase("location.list", "{}", url -> io.kestra.plugin.ashby.directory.Locations.builder()
                .id("list").type("io.kestra.plugin.ashby.directory.Locations").apiKey(Property.ofValue("test-key"))
                .baseUrl(Property.ofValue(url)).fetchType(Property.ofValue(FetchType.FETCH)).build()),
            new ListCase("user.list", "{}", url -> io.kestra.plugin.ashby.directory.Users.builder()
                .id("list").type("io.kestra.plugin.ashby.directory.Users").apiKey(Property.ofValue("test-key"))
                .baseUrl(Property.ofValue(url)).fetchType(Property.ofValue(FetchType.FETCH)).build()));
    }

    @ParameterizedTest
    @MethodSource("lists")
    void everyListUsesItsEndpointAndTypedFilter(ListCase test, WireMockRuntimeInfo server) throws Exception {
        stubFor(post(urlEqualTo("/" + test.endpoint())).withRequestBody(equalToJson(test.filters()))
            .withBasicAuth("test-key", "")
            .willReturn(okJson("{\"success\":true,\"results\":[{\"id\":\"record-1\"}]}")));
        var output = test.task().apply(server.getHttpBaseUrl()).run(runContextFactory.of());
        assertEquals(1L, output.getSize());
        assertEquals("record-1", ((Map<?, ?>) output.getRows().getFirst()).get("id"));
    }

    record InfoCase(String endpoint, String field, Function<String, AbstractAshbyInfo> task) {
        @Override
        public String toString() {
            return endpoint;
        }
    }

    static Stream<InfoCase> infos() {
        return Stream.of(
            new InfoCase("application.info", "applicationId", url -> io.kestra.plugin.ashby.applications.Info.builder()
                .id("info").type("io.kestra.plugin.ashby.applications.Info").apiKey(Property.ofValue("test-key"))
                .baseUrl(Property.ofValue(url)).applicationId(Property.ofValue("record-1")).build()),
            new InfoCase("candidate.info", "candidateId", url -> io.kestra.plugin.ashby.candidates.Info.builder()
                .id("info").type("io.kestra.plugin.ashby.candidates.Info").apiKey(Property.ofValue("test-key"))
                .baseUrl(Property.ofValue(url)).candidateId(Property.ofValue("record-1")).build()),
            new InfoCase("job.info", "jobId", url -> io.kestra.plugin.ashby.jobs.Info.builder()
                .id("info").type("io.kestra.plugin.ashby.jobs.Info").apiKey(Property.ofValue("test-key"))
                .baseUrl(Property.ofValue(url)).jobId(Property.ofValue("record-1")).build()),
            new InfoCase("jobPosting.info", "jobPostingId", url -> io.kestra.plugin.ashby.jobpostings.Info.builder()
                .id("info").type("io.kestra.plugin.ashby.jobpostings.Info").apiKey(Property.ofValue("test-key"))
                .baseUrl(Property.ofValue(url)).jobPostingId(Property.ofValue("record-1")).build()),
            new InfoCase("offer.info", "offerId", url -> io.kestra.plugin.ashby.offers.Info.builder()
                .id("info").type("io.kestra.plugin.ashby.offers.Info").apiKey(Property.ofValue("test-key"))
                .baseUrl(Property.ofValue(url)).offerId(Property.ofValue("record-1")).build()),
            new InfoCase("opening.info", "openingId", url -> io.kestra.plugin.ashby.openings.Info.builder()
                .id("info").type("io.kestra.plugin.ashby.openings.Info").apiKey(Property.ofValue("test-key"))
                .baseUrl(Property.ofValue(url)).openingId(Property.ofValue("record-1")).build()));
    }

    @ParameterizedTest
    @MethodSource("infos")
    void everyInfoSendsTypedIdAndUnwrapsResults(InfoCase test, WireMockRuntimeInfo server) throws Exception {
        stubFor(post(urlEqualTo("/" + test.endpoint()))
            .withRequestBody(equalToJson("{\"" + test.field() + "\":\"record-1\"}"))
            .willReturn(okJson("{\"success\":true,\"results\":{\"id\":\"record-1\",\"name\":\"Record\"}}")));
        var output = test.task().apply(server.getHttpBaseUrl()).run(runContextFactory.of());
        assertEquals(Map.of("id", "record-1", "name", "Record"), output.getResult());
    }

    private io.kestra.plugin.ashby.jobs.List task(WireMockRuntimeInfo server, FetchType fetchType) {
        return io.kestra.plugin.ashby.jobs.List.builder().id("list").type(io.kestra.plugin.ashby.jobs.List.class.getName())
            .apiKey(Property.ofValue("test-key")).baseUrl(Property.ofValue(server.getHttpBaseUrl()))
            .fetchType(Property.ofValue(fetchType)).build();
    }

    private void pages() {
        stubFor(post(urlEqualTo("/job.list")).withRequestBody(equalToJson("{}"))
            .willReturn(okJson("{\"success\":true,\"results\":[{\"id\":\"1\"}],\"moreDataAvailable\":true,\"nextCursor\":\"next\"}")));
        stubFor(post(urlEqualTo("/job.list")).withRequestBody(equalToJson("{\"cursor\":\"next\"}"))
            .willReturn(okJson("{\"success\":true,\"results\":[{\"id\":\"2\"}],\"moreDataAvailable\":false}")));
    }

    @Test
    void storesEveryPageInIon(WireMockRuntimeInfo server) throws Exception {
        pages();
        var runContext = runContextFactory.of();
        var output = task(server, FetchType.STORE).run(runContext);
        assertEquals(2L, output.getSize());
        assertNull(output.getRows());
        try (var reader = runContext.storage().getFile(output.getUri())) {
            assertEquals(List.of(Map.of("id", "1"), Map.of("id", "2")), FileSerde.readAll(reader).collectList().block());
        }
    }

    @Test
    void fetchOneStopsAfterFirstRecord(WireMockRuntimeInfo server) throws Exception {
        pages();
        var output = task(server, FetchType.FETCH_ONE).run(runContextFactory.of());
        assertEquals(Map.of("id", "1"), output.getRow());
        assertEquals(1L, output.getSize());
        verify(1, postRequestedFor(urlEqualTo("/job.list")));
    }

    @Test
    void noneCountsWithoutReturningRecords(WireMockRuntimeInfo server) throws Exception {
        pages();
        var output = task(server, FetchType.NONE).run(runContextFactory.of());
        assertEquals(2L, output.getSize());
        assertNull(output.getRows());
        assertNull(output.getUri());
    }

    @Test
    void rejectsRepeatingAndMissingCursors(WireMockRuntimeInfo server) {
        stubFor(post(urlEqualTo("/job.list")).willReturn(okJson("{\"results\":[],\"moreDataAvailable\":true,\"nextCursor\":\"same\"}")));
        assertTrue(assertThrows(IllegalStateException.class, () -> task(server, FetchType.FETCH).run(runContextFactory.of())).getMessage().contains("repeating"));
        verify(2, postRequestedFor(urlEqualTo("/job.list")));
        stubFor(post(urlEqualTo("/job.list")).willReturn(okJson("{\"results\":[],\"moreDataAvailable\":true}")));
        assertTrue(assertThrows(IllegalStateException.class, () -> task(server, FetchType.FETCH).run(runContextFactory.of())).getMessage().contains("without a nextCursor"));
    }

    @Test
    void rejectsEmptyBodiesAndApiFailures(WireMockRuntimeInfo server) {
        stubFor(post(urlEqualTo("/job.list")).willReturn(ok(" ")));
        assertThrows(IllegalStateException.class, () -> task(server, FetchType.STORE).run(runContextFactory.of()));
        stubFor(post(urlEqualTo("/job.list")).willReturn(okJson("{\"success\":false,\"errorInfo\":{\"message\":\"Denied\"}}")));
        assertEquals("Ashby API request failed: Denied", assertThrows(IllegalStateException.class,
            () -> task(server, FetchType.FETCH).run(runContextFactory.of())).getMessage());
    }

    @Test
    void retriesRateLimitsAndServerErrors(WireMockRuntimeInfo server) throws Exception {
        stubFor(post(urlEqualTo("/job.list")).inScenario("retry").whenScenarioStateIs("Started")
            .willReturn(aResponse().withStatus(429).withHeader("Retry-After", "0")).willSetStateTo("server-error"));
        stubFor(post(urlEqualTo("/job.list")).inScenario("retry").whenScenarioStateIs("server-error")
            .willReturn(aResponse().withStatus(503).withHeader("Retry-After", "0")).willSetStateTo("ok"));
        stubFor(post(urlEqualTo("/job.list")).inScenario("retry").whenScenarioStateIs("ok")
            .willReturn(okJson("{\"results\":[]}")));
        assertEquals(0L, task(server, FetchType.NONE).run(runContextFactory.of()).getSize());
        verify(3, postRequestedFor(urlEqualTo("/job.list")));
    }

    @Test
    void retriesAreBoundedAndAuthenticationFailuresAreImmediate(WireMockRuntimeInfo server) {
        stubFor(post(urlEqualTo("/job.list")).willReturn(aResponse().withStatus(429).withHeader("Retry-After", "0")));
        assertThrows(Exception.class, () -> task(server, FetchType.NONE).run(runContextFactory.of()));
        verify(4, postRequestedFor(urlEqualTo("/job.list")));
        resetAllRequests();
        stubFor(post(urlEqualTo("/job.list")).willReturn(aResponse().withStatus(401)));
        assertThrows(Exception.class, () -> task(server, FetchType.NONE).run(runContextFactory.of()));
        verify(1, postRequestedFor(urlEqualTo("/job.list")));
    }

    @Test
    void retryAfterSupportsHttpDatesAndFallback() {
        var headers = java.net.http.HttpHeaders.of(Map.of("Retry-After", List.of(ZonedDateTime.now().plusSeconds(10).format(DateTimeFormatter.RFC_1123_DATE_TIME))), (a, b) -> true);
        var delay = AshbyClient.retryDelay(HttpResponse.builder().headers(headers).build(), 1);
        assertTrue(delay > 0 && delay <= 10000);
        assertEquals(2000, AshbyClient.retryDelay(HttpResponse.builder().headers(java.net.http.HttpHeaders.of(Map.of(), (a, b) -> true)).build(), 2));
    }
}
