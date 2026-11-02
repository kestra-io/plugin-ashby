package io.kestra.plugin.ashby.triggers;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import io.kestra.core.http.HttpRequest;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.ExecutionTrigger;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.property.Property;
import io.kestra.core.runners.RunContextFactory;
import io.kestra.core.services.WebhookService;
import io.kestra.plugin.core.trigger.WebhookContext;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@KestraTest
class WebhookTest {
    private static final String SECRET = "signing-secret";
    private static final String BODY = "{\"action\":\"candidateHire\",\"data\":{\"id\":\"candidate-1\"}}";

    @Inject
    RunContextFactory runContextFactory;

    private String signature() throws Exception {
        var mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return "sha256=" + HexFormat.of().formatHex(mac.doFinal(BODY.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void evaluatesAuthenticWebhookAndSubmitsExecution() throws Exception {
        var submitted = new AtomicBoolean();
        var output = new AtomicReference<io.kestra.plugin.core.trigger.Webhook.Output>();
        var trigger = trigger(Property.ofValue(SECRET));
        var flow = flow();
        // No direct call to startExecution: this fixture also runs unchanged against the 2.0 return type.
        var service = mock(WebhookService.class, invocation -> {
            switch (invocation.getMethod().getName()) {
                case "runContext":
                    return runContextFactory.of(flow, trigger);
                case "newExecution":
                    output.set(invocation.getArgument(3));
                    return Optional.of(Execution.builder().id("execution").tenantId(flow.getTenantId())
                        .namespace(flow.getNamespace()).flowId(flow.getId())
                        .trigger(ExecutionTrigger.of(trigger, output.get())).build());
                case "startExecution":
                    if (invocation.getMethod().getReturnType() == void.class) {
                        submitted.set(true);
                        return null;
                    }
                    var eventType = (Class<?>) ((java.lang.reflect.ParameterizedType) invocation.getMethod().getGenericReturnType()).getActualTypeArguments()[0];
                    return Mono.fromSupplier(() -> {
                        submitted.set(true);
                        return mock(eventType);
                    });
                default:
                    return RETURNS_DEFAULTS.answer(invocation);
            }
        });
        var response = trigger.evaluate(context(trigger, flow, service, BODY, signature())).block();
        assertNotNull(response);
        assertEquals(200, response.getStatus().getCode());
        assertTrue(submitted.get());
        assertEquals("candidateHire", ((Map<?, ?>) output.get().getBody()).get("action"));
        assertEquals(List.of(signature()), output.get().getHeaders().get("Ashby-Signature"));
    }

    @Test
    void tamperedOrMissingSignatureNeverCreatesAnExecution() throws Exception {
        var trigger = trigger(Property.ofValue(SECRET));
        var flow = flow();
        var service = mock(WebhookService.class);
        when(service.runContext(flow, trigger)).thenReturn(runContextFactory.of(flow, trigger));
        assertEquals(401, trigger.evaluate(context(trigger, flow, service, BODY + " ", signature())).block().getStatus().getCode());
        assertEquals(401, trigger.evaluate(context(trigger, flow, service, BODY, "")).block().getStatus().getCode());
        verify(service, never()).newExecution(any(), any(), any(), any());
    }

    @Test
    void configurationAndSubmissionFailuresReturnServerError() throws Exception {
        var trigger = trigger(Property.ofValue(""));
        var flow = flow();
        var service = mock(WebhookService.class);
        when(service.runContext(flow, trigger)).thenReturn(runContextFactory.of(flow, trigger));
        assertEquals(500, trigger.evaluate(context(trigger, flow, service, BODY, signature())).block().getStatus().getCode());

        var configured = trigger(Property.ofValue(SECRET));
        when(service.runContext(flow, configured)).thenReturn(runContextFactory.of(flow, configured));
        when(service.newExecution(any(), any(), any(), any())).thenThrow(new IllegalStateException("Queue unavailable"));
        assertEquals(500, configured.evaluate(context(configured, flow, service, BODY, signature())).block().getStatus().getCode());
    }

    @Test
    void queueFailuresAreLoggedAndReturnServerError() throws Exception {
        var trigger = trigger(Property.ofValue(SECRET));
        var flow = flow();
        var runContext = spy(runContextFactory.of(flow, trigger));
        var logger = mock(org.slf4j.Logger.class);
        doReturn(logger).when(runContext).logger();
        var service = mock(WebhookService.class, invocation -> {
            switch (invocation.getMethod().getName()) {
                case "runContext":
                    return runContext;
                case "newExecution":
                    return Optional.of(Execution.builder().id("execution").build());
                case "startExecution":
                    var error = new io.kestra.core.queues.QueueException("Queue unavailable");
                    if (invocation.getMethod().getReturnType() == void.class) {
                        throw error;
                    }
                    return Mono.error(error);
                default:
                    return RETURNS_DEFAULTS.answer(invocation);
            }
        });
        assertEquals(500, trigger.evaluate(context(trigger, flow, service, BODY, signature())).block().getStatus().getCode());
        verify(logger).warn("Ashby webhook execution submission failed: Kestra returned HTTP {}", 500);
    }

    private Webhook trigger(Property<String> secret) {
        return Webhook.builder().id("webhook").type(Webhook.class.getName()).key("webhook-key").signingSecret(secret).build();
    }

    private Flow flow() {
        return Flow.builder().id("webhook-flow").namespace("company.people").tenantId("tenant").revision(1).build();
    }

    private WebhookContext context(Webhook trigger, Flow flow, WebhookService service, String body, String signature) {
        return WebhookContext.builder().trigger(trigger).flow(flow).webhookService(service)
            .request(HttpRequest.builder().method("POST").uri(URI.create("https://kestra.example/api/v1/tenant/executions/webhook/company.people/webhook-flow/webhook-key"))
                .addHeader("Ashby-Signature", signature).body(HttpRequest.StringRequestBody.of(body)).build()).build();
    }
}
