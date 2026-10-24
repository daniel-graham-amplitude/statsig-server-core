package com.amplitude;

import com.alibaba.fastjson2.JSON;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

public final class Amplitude {
  private volatile Configuration configuration;

  public void init(String apiKey) {
    init(apiKey, new AmplitudeOptions());
  }

  public void init(String apiKey, AmplitudeOptions options) {
    Objects.requireNonNull(options, "options");
    Configuration next = new Configuration(apiKey, options.serverUrl, options.timeoutMs);
    AmplitudeJNI.init(next.apiKey, next.serverUrl, next.timeoutMs);
    configuration = next;
  }

  public CompletableFuture<Void> track(AmplitudeEvent event) {
    CompletableFuture<Void> result = new CompletableFuture<>();
    Configuration current = configuration;
    if (current == null) {
      result.completeExceptionally(new IllegalStateException("Call Amplitude.init before track"));
      return result;
    }
    try {
      Objects.requireNonNull(event, "event");
      AmplitudeJNI.track(
          current.apiKey,
          current.serverUrl,
          current.timeoutMs,
          JSON.toJSONString(event),
          result);
    } catch (RuntimeException error) {
      result.completeExceptionally(error);
    }
    return result;
  }

  private static final class Configuration {
    final String apiKey;
    final String serverUrl;
    final long timeoutMs;

    Configuration(String apiKey, String serverUrl, long timeoutMs) {
      this.apiKey = apiKey;
      this.serverUrl = serverUrl;
      this.timeoutMs = timeoutMs;
    }
  }
}
