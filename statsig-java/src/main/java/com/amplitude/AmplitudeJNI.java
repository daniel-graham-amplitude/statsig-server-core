package com.amplitude;

import java.util.concurrent.CompletableFuture;

final class AmplitudeJNI {
  static {
    String path = System.getProperty("amplitude.native.lib");
    if (path == null || path.isEmpty()) {
      path = System.getenv("AMPLITUDE_NATIVE_LIB");
    }
    if (path == null || path.isEmpty()) {
      System.loadLibrary("statsig_ffi");
    } else {
      System.load(path);
    }
  }

  static native void init(String apiKey, String serverUrl, long timeoutMs);

  static native void track(
      String apiKey,
      String serverUrl,
      long timeoutMs,
      String eventJson,
      CompletableFuture<Void> result);

  private AmplitudeJNI() {}
}
