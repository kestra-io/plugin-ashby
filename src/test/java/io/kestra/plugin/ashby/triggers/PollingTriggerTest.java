package io.kestra.plugin.ashby.triggers;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.Label;
import io.kestra.core.models.conditions.ConditionContext;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.triggers.TriggerContext;
import io.kestra.core.runners.RunContextFactory;
import io.kestra.core.runners.RunContextInitializer;
import io.kestra.core.runners.DefaultRunContext;
import io.kestra.core.utils.IdUtils;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.junit.jupiter.api.Assertions.*;

@KestraTest
@WireMockTest
class PollingTriggerTest {
    @Inject
    RunContextFactory runContextFactory;

    @Inject
    RunContextInitializer runContextInitializer;

    private ConditionContext condition(AbstractAshbyPollingTrigger trigger, TriggerContext context) {
        var flow = Flow.builder().id(context.getFlowId()).namespace(context.getNamespace()).tenantId(context.getTenantId())
            .revision(3).variables(Map.of("team", "people")).build();
        return ConditionContext.builder().flow(flow)
            .runContext(runContextInitializer.forScheduler((DefaultRunContext) runContextFactory.of(flow, trigger), context, trigger))
            .build();
    }

    private TriggerContext context(String triggerId) {
        return TriggerContext.builder().tenantId("tenant").namespace("company." + IdUtils.create().toLowerCase())
            .flowId("hiring").triggerId(triggerId).build();
    }

    private void applications(String request, String response) {
        stubFor(post(urlEqualTo("/application.list")).withRequestBody(equalToJson(request)).willReturn(okJson(response)));
    }

    @Test
    void hiredTriggerSeedsAllPagesPersistsFinalTokenAndOnlyFiresForTransitions(WireMockRuntimeInfo server) throws Exception {
        var trigger = CandidateHiredTrigger.builder().id("hired").type(CandidateHiredTrigger.class.getName())
            .labels(List.of(new Label("team", "people"))).apiKey(Property.ofValue("test-key"))
            .baseUrl(Property.ofValue(server.getHttpBaseUrl())).build();
        assertEquals(Duration.ofMinutes(15), trigger.getInterval());
        var context = context(trigger.getId());
        var condition = condition(trigger, context);
        applications("{}", "{\"results\":[{\"id\":\"old\",\"status\":\"Hired\"}],\"moreDataAvailable\":true,\"nextCursor\":\"seed-page-2\"}");
        applications("{\"cursor\":\"seed-page-2\"}", "{\"results\":[{\"id\":\"active\",\"status\":\"Active\"}],\"syncToken\":\"t1\"}");
        assertTrue(trigger.evaluate(condition, context).isEmpty());
        assertEquals("t1", trigger.readState(condition.getRunContext(), context).orElseThrow().get("syncToken"));

        applications("{\"syncToken\":\"t1\"}", "{\"results\":[{\"id\":\"old\",\"status\":\"Hired\",\"note\":\"updated\"}],\"moreDataAvailable\":true,\"nextCursor\":\"changed-page-2\"}");
        applications("{\"syncToken\":\"t1\",\"cursor\":\"changed-page-2\"}", "{\"results\":[{\"id\":\"active\",\"status\":\"Hired\"}],\"syncToken\":\"t2\"}");
        var execution = trigger.evaluate(condition, context).orElseThrow();
        assertEquals(context.getTenantId(), execution.getTenantId());
        assertEquals(3, execution.getFlowRevision());
        assertEquals(Map.of("team", "people"), execution.getVariables());
        assertTrue(execution.getLabels().contains(new Label("team", "people")));
        assertEquals(List.of(Map.of("id", "active", "status", "Hired")), execution.getTrigger().getVariables().get("events"));
        assertEquals("t2", execution.getTrigger().getVariables().get("syncToken"));
        applications("{\"syncToken\":\"t2\"}", "{\"results\":[{\"id\":\"active\",\"status\":\"Hired\",\"note\":\"another update\"}],\"syncToken\":\"t3\"}");
        assertTrue(trigger.evaluate(condition, context).isEmpty());
        assertEquals("t3", trigger.readState(condition.getRunContext(), context).orElseThrow().get("syncToken"));
        verify(5, postRequestedFor(urlEqualTo("/application.list")));
    }

    @Test
    void expiredTokenRestartsFullSyncAndReseedsWithoutFiring(WireMockRuntimeInfo server) throws Exception {
        var trigger = CandidateHiredTrigger.builder().id("hired").type(CandidateHiredTrigger.class.getName())
            .apiKey(Property.ofValue("test-key")).baseUrl(Property.ofValue(server.getHttpBaseUrl())).build();
        var context = context(trigger.getId());
        var condition = condition(trigger, context);
        applications("{}", "{\"results\":[{\"id\":\"old\",\"status\":\"Hired\"}],\"syncToken\":\"t1\"}");
        assertTrue(trigger.evaluate(condition, context).isEmpty());
        applications("{\"syncToken\":\"t1\"}", "{\"results\":[{\"id\":\"partial\",\"status\":\"Hired\"}],\"moreDataAvailable\":true,\"nextCursor\":\"page2\"}");
        applications("{\"syncToken\":\"t1\",\"cursor\":\"page2\"}", "{\"success\":false,\"errors\":[\"sync_token_expired\"],\"errorInfo\":{\"code\":\"sync_token_expired\",\"message\":\"Expired\"}}");
        applications("{}", "{\"results\":[{\"id\":\"new\",\"status\":\"Hired\"}],\"moreDataAvailable\":true,\"nextCursor\":\"seed2\"}");
        applications("{\"cursor\":\"seed2\"}", "{\"results\":[],\"syncToken\":\"t2\"}");
        assertTrue(trigger.evaluate(condition, context).isEmpty());
        var state = trigger.readState(condition.getRunContext(), context).orElseThrow();
        assertEquals("t2", state.get("syncToken"));
        assertEquals(List.of("new"), state.get("hiredIds"));
        applications("{\"syncToken\":\"t2\"}", "{\"results\":[{\"id\":\"new\",\"status\":\"Hired\"}],\"syncToken\":\"t3\"}");
        assertTrue(trigger.evaluate(condition, context).isEmpty());
    }

    @Test
    void failedOrIncompletePollDoesNotAdvanceState(WireMockRuntimeInfo server) throws Exception {
        var trigger = CandidateHiredTrigger.builder().id("hired").type(CandidateHiredTrigger.class.getName())
            .apiKey(Property.ofValue("test-key")).baseUrl(Property.ofValue(server.getHttpBaseUrl())).build();
        var context = context(trigger.getId());
        var condition = condition(trigger, context);
        applications("{}", "{\"results\":[],\"syncToken\":\"t1\"}");
        trigger.evaluate(condition, context);
        applications("{\"syncToken\":\"t1\"}", "{\"results\":[{\"id\":\"new\",\"status\":\"Hired\"}],\"moreDataAvailable\":true,\"nextCursor\":\"page2\"}");
        applications("{\"syncToken\":\"t1\",\"cursor\":\"page2\"}", " ");
        assertThrows(IllegalStateException.class, () -> trigger.evaluate(condition, context));
        assertEquals("t1", trigger.readState(condition.getRunContext(), context).orElseThrow().get("syncToken"));
        applications("{\"syncToken\":\"t1\"}", "{\"results\":[]}");
        assertThrows(IllegalStateException.class, () -> trigger.evaluate(condition, context));
        applications("{\"syncToken\":\"t1\"}", "{\"results\":[],\"moreDataAvailable\":true,\"nextCursor\":\"same\"}");
        applications("{\"syncToken\":\"t1\",\"cursor\":\"same\"}", "{\"results\":[],\"moreDataAvailable\":true,\"nextCursor\":\"same\"}");
        assertThrows(IllegalStateException.class, () -> trigger.evaluate(condition, context));
        assertEquals("t1", trigger.readState(condition.getRunContext(), context).orElseThrow().get("syncToken"));
    }

    @Test
    void postingsDetectPublishRepublishAndUnpublishWithoutRefiring(WireMockRuntimeInfo server) throws Exception {
        for (var event : JobPostingTrigger.Event.values()) {
            var trigger = JobPostingTrigger.builder().id("postings").type(JobPostingTrigger.class.getName())
                .event(Property.ofValue(event)).apiKey(Property.ofValue("test-key"))
                .baseUrl(Property.ofValue(server.getHttpBaseUrl())).build();
            var context = context(trigger.getId());
            var condition = condition(trigger, context);
            postings("{\"results\":[{\"id\":\"old\",\"isListed\":true,\"publishedDate\":\"day1\"}],\"moreDataAvailable\":true,\"nextCursor\":\"page2\"}");
            stubFor(post(urlEqualTo("/jobPosting.list")).withRequestBody(equalToJson("{\"listedOnly\":true,\"cursor\":\"page2\"}"))
                .willReturn(okJson("{\"results\":[]}")));
            assertTrue(trigger.evaluate(condition, context).isEmpty());
            postings("{\"results\":[{\"id\":\"new\",\"isListed\":true,\"publishedDate\":\"day2\"}]}");
            var execution = trigger.evaluate(condition, context).orElseThrow();
            var events = (List<?>) execution.getTrigger().getVariables().get("events");
            assertEquals(event == JobPostingTrigger.Event.PUBLISHED ? "new" : "old", ((Map<?, ?>) events.getFirst()).get("id"));
            assertTrue(trigger.evaluate(condition, context).isEmpty());
            postings("{\"results\":[{\"id\":\"new\",\"isListed\":true,\"publishedDate\":\"day3\"}]}");
            assertEquals(event == JobPostingTrigger.Event.PUBLISHED, trigger.evaluate(condition, context).isPresent());
            postings("{\"results\":[{\"id\":\"new\",\"isListed\":false}]}");
            assertEquals(event == JobPostingTrigger.Event.UNPUBLISHED, trigger.evaluate(condition, context).isPresent());
        }
        verify(0, postRequestedFor(urlEqualTo("/jobPosting.list")).withRequestBody(matchingJsonPath("$.syncToken")));
    }

    private void postings(String response) {
        stubFor(post(urlEqualTo("/jobPosting.list")).withRequestBody(equalToJson("{\"listedOnly\":true}"))
            .willReturn(okJson(response)));
    }

    @Test
    void watermarkKeysCannotCollide() {
        var first = TriggerContext.builder().flowId("a-b").triggerId("c").build();
        var second = TriggerContext.builder().flowId("a").triggerId("b-c").build();
        assertNotEquals(AbstractAshbyPollingTrigger.stateKey(first), AbstractAshbyPollingTrigger.stateKey(second));
    }
}
