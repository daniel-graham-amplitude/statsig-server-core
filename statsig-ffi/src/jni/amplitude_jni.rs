use jni::{
    objects::{GlobalRef, JClass, JObject, JString, JValue},
    sys::jlong,
    JNIEnv, JavaVM,
};
use statsig_rust::{Amplitude, AmplitudeEvent, AmplitudeOptions};
use tokio::runtime::Runtime;

lazy_static::lazy_static! {
    static ref AMPLITUDE_RUNTIME: Result<Runtime, String> = tokio::runtime::Builder::new_multi_thread()
        .worker_threads(2)
        .thread_name("amplitude-java")
        .enable_all()
        .build()
        .map_err(|error| error.to_string());
}

fn create_client(
    env: &mut JNIEnv,
    api_key: JString,
    server_url: JString,
    timeout_ms: jlong,
) -> Result<Amplitude, String> {
    if timeout_ms <= 0 {
        return Err("timeout_ms must be positive".into());
    }
    let api_key: String = env.get_string(&api_key).map_err(|e| e.to_string())?.into();
    let server_url: String = env
        .get_string(&server_url)
        .map_err(|e| e.to_string())?
        .into();
    let mut client = Amplitude::new();
    client
        .init(
            &api_key,
            Some(AmplitudeOptions {
                server_url,
                timeout_ms: timeout_ms as u64,
            }),
        )
        .map_err(|e| e.to_string())?;
    Ok(client)
}

fn throw_input_error(env: &mut JNIEnv, message: String) {
    if !env.exception_check().unwrap_or(true) {
        let _ = env.throw_new("java/lang/IllegalArgumentException", message);
    }
}

#[no_mangle]
pub extern "system" fn Java_com_amplitude_AmplitudeJNI_init(
    mut env: JNIEnv,
    _class: JClass,
    api_key: JString,
    server_url: JString,
    timeout_ms: jlong,
) {
    if let Err(error) = create_client(&mut env, api_key, server_url, timeout_ms) {
        throw_input_error(&mut env, error);
    }
}

#[no_mangle]
pub extern "system" fn Java_com_amplitude_AmplitudeJNI_track(
    mut env: JNIEnv,
    _class: JClass,
    api_key: JString,
    server_url: JString,
    timeout_ms: jlong,
    event_json: JString,
    future: JObject,
) {
    let prepared = (|| -> Result<_, String> {
        let client = create_client(&mut env, api_key, server_url, timeout_ms)?;
        let event_json: String = env
            .get_string(&event_json)
            .map_err(|e| e.to_string())?
            .into();
        let mut event: AmplitudeEvent =
            serde_json::from_str(&event_json).map_err(|e| e.to_string())?;
        event.library = Some(format!("amplitude-java-core/{}", env!("CARGO_PKG_VERSION")));
        let vm = env.get_java_vm().map_err(|e| e.to_string())?;
        let future = env.new_global_ref(future).map_err(|e| e.to_string())?;
        let exception_class = env
            .find_class("com/amplitude/AmplitudeException")
            .map_err(|e| e.to_string())?;
        let exception_class = env
            .new_global_ref(exception_class)
            .map_err(|e| e.to_string())?;
        let runtime = AMPLITUDE_RUNTIME.as_ref().map_err(|e| e.clone())?;
        Ok((client, event, vm, future, exception_class, runtime))
    })();
    let (client, event, vm, future, exception_class, runtime) = match prepared {
        Ok(prepared) => prepared,
        Err(error) => {
            throw_input_error(&mut env, error);
            return;
        }
    };
    runtime.spawn(async move {
        let error = client.track(event).await.err().map(|e| e.to_string());
        if let Err(error) = complete_future(&vm, &future, &exception_class, error) {
            eprintln!("Amplitude could not complete Java tracking future: {error}");
        }
    });
}

fn complete_future(
    vm: &JavaVM,
    future: &GlobalRef,
    exception_class: &GlobalRef,
    error: Option<String>,
) -> jni::errors::Result<()> {
    let mut env = vm.attach_current_thread()?;
    if let Some(error) = error {
        let message = JObject::from(env.new_string(error)?);
        let exception = env.new_object(
            <&JClass>::from(exception_class.as_obj()),
            "(Ljava/lang/String;)V",
            &[JValue::Object(&message)],
        )?;
        env.call_method(
            future.as_obj(),
            "completeExceptionally",
            "(Ljava/lang/Throwable;)Z",
            &[JValue::Object(&exception)],
        )?;
    } else {
        env.call_method(
            future.as_obj(),
            "complete",
            "(Ljava/lang/Object;)Z",
            &[JValue::Object(&JObject::null())],
        )?;
    }
    Ok(())
}
