package io.kestra.plugin.ashby.triggers;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import io.kestra.core.http.HttpResponse;
import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.property.Property;
import io.kestra.core.runners.RunContext;
import io.kestra.plugin.core.trigger.WebhookContext;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;
import reactor.core.publisher.Mono;

@SuperBuilder
@ToString
@EqualsAndHashCode(callSuper = true)
@Getter
@NoArgsConstructor
@Schema(title = "Receive signed Ashby webhooks", description = "Verifies the Ashby HMAC signature, then delegates execution creation and submission to Kestra. The payload is available in trigger.body and headers in trigger.headers.")
@Plugin(examples = {@Example(title = "Receive an Ashby webhook", full = true, code = """
    id: ashby_webhook
    namespace: company.team
    tasks:
      - id: log
        type: io.kestra.plugin.core.log.Log
        message: "{{ trigger.body }}"
    triggers:
      - id: ashby
        type: io.kestra.plugin.ashby.triggers.Webhook
        key: "{{ secret('ASHBY_WEBHOOK_KEY') }}"
        signingSecret: "{{ secret('ASHBY_SIGNING_SECRET') }}"
    """)})
public class Webhook extends io.kestra.plugin.core.trigger.Webhook {
    @Schema(title = "Webhook signing secret", description = "Required secret configured in Ashby. Ashby-Signature must contain sha256= followed by the lowercase hexadecimal HMAC-SHA256 digest of the raw request body.")
    @PluginProperty(secret = true, group = "connection")
    @NotNull
    @ToString.Exclude
    private Property<String> signingSecret;

    @Override
    public Mono<HttpResponse<?>> evaluate(WebhookContext context) {
        var runContext = context.webhookService().runContext(context.flow(), this);
        var body = context.request().getBody() == null ? "" : String.valueOf(context.request().getBody().getContent());
        var signature = context.request().getHeaders().firstValue("Ashby-Signature").orElse("");
        String secret = null;
        try {
            secret = runContext.render(signingSecret).as(String.class).filter(value -> !value.isBlank()).orElseThrow(() -> new IllegalStateException("Missing Ashby signing secret"));
            if (!isValidSignature(body, signature, secret)) {
                return Mono.just(HttpResponse.of(HttpResponse.Status.UNAUTHORIZED));
            }
            // The runtime's implementation owns startExecution, whose return type differs between Kestra 1.3 and 2.0.
            var rSecret = secret;
            return super.evaluate(context)
                .doOnNext(response -> {
                    if (response.getStatus().getCode() >= 500) {
                        runContext.logger().warn("Ashby webhook execution submission failed: Kestra returned HTTP {}", response.getStatus().getCode());
                    }
                })
                .onErrorResume(e -> failure(runContext, e, body, signature, rSecret));
        } catch (Exception e) {
            return failure(runContext, e, body, signature, secret);
        }
    }

    private Mono<HttpResponse<?>> failure(RunContext runContext, Throwable error, String body, String signature, String secret) {
        var message = error.getMessage() == null ? "No message" : error.getMessage();
        for (var sensitive : new String[] {body, signature, secret}) {
            if (sensitive != null && !sensitive.isEmpty()) {
                message = message.replace(sensitive, "[redacted]");
            }
        }
        runContext.logger().warn("Ashby webhook failed ({}): {}", error.getClass().getSimpleName(), message);
        return Mono.just(HttpResponse.of(HttpResponse.Status.INTERNAL_SERVER_ERROR));
    }

    static boolean isValidSignature(String body, String signature, String secret) throws Exception {
        var mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        var expected = "sha256=" + HexFormat.of().formatHex(mac.doFinal(body.getBytes(StandardCharsets.UTF_8)));
        return MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), signature.getBytes(StandardCharsets.UTF_8));
    }
}
