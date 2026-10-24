package com.amplitude;

import static org.junit.jupiter.api.Assertions.*;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.Test;

class AmplitudeTest {
  @Test
  void initAndTrackThroughRust() throws Exception {
    try (MockWebServer server = new MockWebServer()) {
      server.start();
      server.enqueue(new MockResponse().setBody("{\"code\":200,\"events_ingested\":1}"));
      AmplitudeOptions options = new AmplitudeOptions();
      options.serverUrl = server.url("/2/httpapi").toString();
      Amplitude amplitude = new Amplitude();
      amplitude.init("test-api-key", options);

      AmplitudeEvent event = new AmplitudeEvent("Hello World", "java-test-user");
      Map<String, Object> properties = new HashMap<>();
      properties.put("title", "Hello");
      properties.put("repeat", true);
      properties.put("tags", Arrays.asList("pop", "rock"));
      event.eventProperties = properties;
      event.userProperties = new HashMap<>();
      event.userProperties.put("plan", "free");
      event.time = 1234567890000L;
      event.insertId = "event-123";
      event.sessionId = 1234567890000L;
      amplitude.track(event).get(5, TimeUnit.SECONDS);

      RecordedRequest request = server.takeRequest(5, TimeUnit.SECONDS);
      assertNotNull(request);
      assertEquals("POST", request.getMethod());
      assertEquals("/2/httpapi", request.getPath());
      assertNull(request.getHeader("STATSIG-API-KEY"));
      assertNull(request.getHeader("STATSIG-CLIENT-TIME"));
      JSONObject body = JSON.parseObject(request.getBody().readUtf8());
      assertEquals("test-api-key", body.getString("api_key"));
      JSONObject sent = body.getJSONArray("events").getJSONObject(0);
      assertEquals("Hello World", sent.getString("event_type"));
      assertEquals("java-test-user", sent.getString("user_id"));
      assertEquals(JSON.parseObject(JSON.toJSONString(properties)), sent.getJSONObject("event_properties"));
      assertEquals("free", sent.getJSONObject("user_properties").getString("plan"));
      assertEquals(event.time.longValue(), sent.getLongValue("time"));
      assertEquals(event.sessionId.longValue(), sent.getLongValue("session_id"));
      assertEquals(event.insertId, sent.getString("insert_id"));
      assertTrue(sent.getString("library").startsWith("amplitude-java-core/"));
      assertEquals(1, server.getRequestCount());
    }
  }

  @Test
  void reportsErrorsAndSupportsDeviceOnlyEvents() throws Exception {
    Amplitude amplitude = new Amplitude();
    assertThrows(ExecutionException.class, () -> amplitude.track(new AmplitudeEvent("test")).get());
    assertThrows(IllegalArgumentException.class, () -> amplitude.init(""));
    try (MockWebServer server = new MockWebServer()) {
      server.start();
      AmplitudeOptions options = new AmplitudeOptions();
      options.serverUrl = server.url("/2/httpapi").toString();
      amplitude.init("test-api-key", options);
      assertThrows(ExecutionException.class, () -> amplitude.track(new AmplitudeEvent("test")).get());

      server.enqueue(new MockResponse().setResponseCode(400).setBody("{\"error\":\"Invalid API key\"}"));
      AmplitudeEvent event = new AmplitudeEvent("test");
      event.deviceId = "device-123";
      ExecutionException failure = assertThrows(
          ExecutionException.class, () -> amplitude.track(event).get(5, TimeUnit.SECONDS));
      assertTrue(failure.getCause() instanceof AmplitudeException);
      assertTrue(failure.getCause().getMessage().contains("400"));
      JSONObject sent = JSON.parseObject(server.takeRequest().getBody().readUtf8())
          .getJSONArray("events").getJSONObject(0);
      assertEquals("device-123", sent.getString("device_id"));
      assertTrue(sent.getLongValue("time") > 0);
      UUID.fromString(sent.getString("insert_id"));
      assertEquals(1, server.getRequestCount());
    }
  }

}
