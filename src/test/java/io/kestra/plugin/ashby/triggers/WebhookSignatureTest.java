package io.kestra.plugin.ashby.triggers;
import org.junit.jupiter.api.Test;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import static org.junit.jupiter.api.Assertions.*;
class WebhookSignatureTest {
 @Test void acceptsAuthenticAshbySignature() throws Exception {
  String body = "{\"action\":\"applicationSubmit\"}";
  String secret = "webhook-secret";
  Mac mac = Mac.getInstance("HmacSHA256");
  mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
  String signature = "sha256=" + HexFormat.of().formatHex(mac.doFinal(body.getBytes(StandardCharsets.UTF_8)));
  assertTrue(Webhook.isValidSignature(body, signature, secret));
 }
 @Test void rejectsTamperedPayloadAndBadSignature() throws Exception {
  assertFalse(Webhook.isValidSignature("{\"action\":\"applicationSubmit\"}", "sha256=invalid", "secret"));
  assertFalse(Webhook.isValidSignature("tampered", "sha256=invalid", "secret"));
 }
}
