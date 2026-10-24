package com.amplitude;

import com.alibaba.fastjson2.annotation.JSONField;
import java.util.Map;

public final class AmplitudeEvent {
  @JSONField(name = "event_type")
  public String eventType;

  @JSONField(name = "user_id")
  public String userId;

  @JSONField(name = "device_id")
  public String deviceId;

  @JSONField(name = "event_properties")
  public Map<String, Object> eventProperties;

  @JSONField(name = "user_properties")
  public Map<String, Object> userProperties;

  public Long time;

  @JSONField(name = "insert_id")
  public String insertId;

  @JSONField(name = "session_id")
  public Long sessionId;

  public AmplitudeEvent(String eventType) {
    this.eventType = eventType;
  }

  public AmplitudeEvent(String eventType, String userId) {
    this(eventType);
    this.userId = userId;
  }
}
