# Amplitude Java Core

Java bindings for the Rust Amplitude client, packaged alongside Statsig in `statsig-java`. Requires Java 8 or newer.

```java
import com.amplitude.Amplitude;
import com.amplitude.AmplitudeEvent;
import com.amplitude.AmplitudeOptions;

Amplitude amplitude = new Amplitude();
amplitude.init("AMPLITUDE_API_KEY");
amplitude.track(new AmplitudeEvent("Hello World", "java-test-user")).get();
```

`init` configures the client locally. `track` returns a `CompletableFuture<Void>` that completes when the API accepts the event and completes exceptionally on validation, network, or API errors. Each call sends immediately; there is no queue, batching, automatic retry, or background flush. Await tracking before exiting.

Events support `userId` or `deviceId`, `eventProperties`, `userProperties`, `time` (milliseconds since epoch), `insertId`, and `sessionId`. Missing timestamps and insert IDs are generated in Rust. Reuse an explicit insert ID when retrying. Amplitude's default minimum ID length is five characters.

Pass an `AmplitudeOptions` instance to `init(apiKey, options)` to configure `serverUrl` (default `https://api2.amplitude.com/2/httpapi`) and `timeoutMs` (default 10,000).

Amplitude has its own API, JNI entry points, configuration, and Tokio runtime. It does not initialize a Statsig client or use Statsig's Java classes, event queue, or lifecycle. For now, its JNI symbols ship in the repository's shared `statsig_ffi` native binary. Set `AMPLITUDE_NATIVE_LIB` or the `amplitude.native.lib` JVM property to the absolute binary path, or put it on `java.library.path`.

From the repository root, build and run the two local-server smoke tests:

```bash
cargo build -p statsig_ffi
AMPLITUDE_NATIVE_LIB="$PWD/target/debug/libstatsig_ffi.dylib" \
  ./statsig-java/gradlew -p statsig-java test --tests com.amplitude.AmplitudeTest
```

Use `libstatsig_ffi.so` on Linux or `statsig_ffi.dll` on Windows. Amplitude classes are included in the existing Java SDK artifacts, including the Java core and uber JARs. Tests do not send real analytics events.

## Send a real Hello World event

After building the native library, run from the repository root:

```bash
AMPLITUDE_API_KEY='YOUR_API_KEY' \
  ./statsig-java/gradlew -p statsig-java amplitudeHelloWorld --console=plain
```

This sends `Hello World` with user ID `server-amplitude-user-java` and a new session ID. The task defaults to the native library in `target/debug`; `AMPLITUDE_NATIVE_LIB` overrides that path. `AMPLITUDE_SERVER_URL` optionally overrides the endpoint.
