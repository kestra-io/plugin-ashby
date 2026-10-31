package io.kestra.plugin.ashby.triggers;

import io.kestra.core.http.HttpResponse;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.property.Property;
import io.kestra.plugin.core.trigger.AbstractWebhookTrigger;
import io.kestra.plugin.core.trigger.WebhookContext;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;
import reactor.core.publisher.Mono;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

@SuperBuilder
@ToString
@EqualsAndHashCode(callSuper = true)
@Getter
@NoArgsConstructor
@Schema(title = "Ashby webhook", description = "Starts a flow for an Ashby webhook after verifying its HMAC signature.")
@Plugin(examples = {@io.kestra.core.models.annotations.Example(title = "Receive an Ashby webhook", full = true, code = """
id: ashby_webhook
namespace: company.team
triggers:
  - id: ashby
    type: io.kestra.plugin.ashby.triggers.Webhook
    key: '{{ secret(''ASHBY_WEBHOOK_KEY'') }}'
    signingSecret: '{{ secret(''ASHBY_SIGNING_SECRET'') }}'
""")})
public class Webhook extends AbstractWebhookTrigger {
    @Schema(title = "Webhook signing secret", description = "The optional secret configured in Ashby to sign webhook bodies.")
    @PluginProperty(secret = true, group = "connection")
    @NotNull
    @ToString.Exclude
    private Property<String> signingSecret;

    @Override
    public Mono<HttpResponse<?>> evaluate(WebhookContext context) {
        String body = context.request().getBody() == null ? "" : String.valueOf(context.request().getBody().getContent());
        String signature = context.request().getHeaders().firstValue("Ashby-Signature").orElse("");
        try {
            String secret = context.webhookService().runContext(context.flow(), this).render(signingSecret).as(String.class).orElseThrow();
            if (!isValidSignature(body, signature, secret)) return Mono.just(HttpResponse.of(HttpResponse.Status.UNAUTHORIZED));
            Object payload = io.kestra.core.serializers.JacksonMapper.ofJson().readValue(body, Object.class);
            var execution = context.webhookService().newExecution(context, context.flow(), this, new Output(payload, context.request().getHeaders().map()));
            if (execution.isEmpty()) return Mono.just(HttpResponse.of(HttpResponse.Status.CONFLICT));
            context.webhookService().startExecution(execution.get());
            return Mono.just(HttpResponse.of(context.webhookService().executionResponse(execution.get())));
        } catch (Exception e) {
            return Mono.just(HttpResponse.of(HttpResponse.Status.BAD_REQUEST));
        }
    }

    static boolean isValidSignature(String body, String signature, String secret) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        String expected = "sha256=" + HexFormat.of().formatHex(mac.doFinal(body.getBytes(StandardCharsets.UTF_8)));
        return MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), signature.getBytes(StandardCharsets.UTF_8));
    }

    public record Output(@Schema(title = "Webhook payload") Object body, @Schema(title = "Webhook headers") Map<String, List<String>> headers) implements io.kestra.core.models.tasks.Output {}
}
