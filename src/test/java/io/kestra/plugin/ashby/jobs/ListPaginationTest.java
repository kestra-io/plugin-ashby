package io.kestra.plugin.ashby.jobs;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.common.FetchType;
import io.kestra.core.runners.RunContextFactory;
import io.kestra.core.utils.IdUtils;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import java.util.Map;
import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.junit.jupiter.api.Assertions.*;

@KestraTest @WireMockTest
class ListPaginationTest {
 @Inject RunContextFactory runContextFactory;
 @Test void followsCursorAndCombinesPages(WireMockRuntimeInfo server) throws Exception {
  stubFor(post(urlEqualTo("/job.list")).withRequestBody(equalToJson("{}"))
   .willReturn(okJson("{\"success\":true,\"results\":[{\"id\":\"1\"}],\"moreDataAvailable\":true,\"nextCursor\":\"next\"}")));
  stubFor(post(urlEqualTo("/job.list")).withRequestBody(equalToJson("{\"cursor\":\"next\"}"))
   .willReturn(okJson("{\"success\":true,\"results\":[{\"id\":\"2\"}],\"moreDataAvailable\":false}")));
  List task = List.builder().id(IdUtils.create()).type(List.class.getName()).apiKey(Property.ofValue("test-key"))
   .baseUrl(Property.ofValue(server.getHttpBaseUrl())).fetchType(Property.ofValue(FetchType.FETCH)).build();
  var output = task.run(runContextFactory.of(Map.of()));
  assertEquals(2L, output.getSize());
  assertEquals("1", ((Map<?, ?>) output.getRows().get(0)).get("id"));
  assertEquals("2", ((Map<?, ?>) output.getRows().get(1)).get("id"));
  verify(2, postRequestedFor(urlEqualTo("/job.list")));
 }
}
