package com.amplitude;

import java.util.concurrent.TimeUnit;

public final class AmplitudeHelloWorld {
  public static void main(String[] args) throws Exception {
    String apiKey = System.getenv("AMPLITUDE_API_KEY");
    if (apiKey == null || apiKey.trim().isEmpty()) {
      throw new IllegalArgumentException("Set AMPLITUDE_API_KEY before running this example");
    }

    AmplitudeOptions options = new AmplitudeOptions();
    String serverUrl = System.getenv("AMPLITUDE_SERVER_URL");
    if (serverUrl != null && !serverUrl.isEmpty()) {
      options.serverUrl = serverUrl;
    }

    Amplitude amplitude = new Amplitude();
    amplitude.init(apiKey, options);
    AmplitudeEvent event = new AmplitudeEvent("Hello World", "server-amplitude-user-java");
    event.sessionId = System.currentTimeMillis();
    amplitude.track(event).get(15, TimeUnit.SECONDS);

    System.out.println("Hello World accepted by Amplitude");
    System.out.println("User ID: " + event.userId);
    System.out.println("Session ID: " + event.sessionId);
  }
}
