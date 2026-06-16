#include <WiFi.h>
#include <WebSocketsClient.h>
#include <ArduinoJson.h>
#include "config.h"

// ── State ─────────────────────────────────────────────────────────────────────

struct Request {
  char id[64];
  char tool[64];
  char machine[64];
  bool active;
};

static Request current;
static bool    wsConnected = false;
static bool    authed      = false;

WebSocketsClient ws;

// ── Button debounce ───────────────────────────────────────────────────────────

struct Button {
  uint8_t      pin;
  bool         lastState;
  unsigned long lastDebounce;
};

static Button btns[3] = {
  {BTN_YES,    HIGH, 0},
  {BTN_ALWAYS, HIGH, 0},
  {BTN_NO,     HIGH, 0},
};

// ── Helpers ───────────────────────────────────────────────────────────────────

static void sendJSON(JsonDocument& doc) {
  String out;
  serializeJson(doc, out);
  ws.sendTXT(out);
}

static void sendDecision(const char* action) {
  if (!authed || !current.active) return;

  StaticJsonDocument<128> doc;
  doc["type"]       = "decide";
  doc["request_id"] = current.id;
  doc["action"]     = action;
  sendJSON(doc);

  Serial.printf("[nod] decision sent: %s for %s\n", action, current.id);
  memset(&current, 0, sizeof(current));
}

static void handleMessage(const char* payload) {
  StaticJsonDocument<512> doc;
  if (deserializeJson(doc, payload) != DeserializationError::Ok) return;

  const char* type = doc["type"] | "";

  if (strcmp(type, "auth_ok") == 0) {
    authed = true;
    Serial.println("[nod] authenticated");
    digitalWrite(LED_BUILTIN_PIN, HIGH);

  } else if (strcmp(type, "request") == 0) {
    strlcpy(current.id,      doc["request_id"] | "", sizeof(current.id));
    strlcpy(current.tool,    doc["tool"]        | "", sizeof(current.tool));
    strlcpy(current.machine, doc["machine_name"]| "", sizeof(current.machine));
    current.active = true;

    Serial.printf("[nod] request: tool=%s machine=%s id=%s\n",
                  current.tool, current.machine, current.id);

  } else if (strcmp(type, "decided") == 0) {
    // Another approver resolved — clear pending request.
    const char* rid = doc["request_id"] | "";
    if (strcmp(rid, current.id) == 0) {
      memset(&current, 0, sizeof(current));
      Serial.println("[nod] request resolved elsewhere");
    }

  } else if (strcmp(type, "clear") == 0) {
    memset(&current, 0, sizeof(current));

  } else if (strcmp(type, "ping") == 0) {
    StaticJsonDocument<32> pong;
    pong["type"] = "pong";
    sendJSON(pong);

  } else if (strcmp(type, "error") == 0) {
    Serial.printf("[nod] server error: %s\n", doc["message"] | "unknown");
  }
}

// ── WebSocket event handler ───────────────────────────────────────────────────

static void onWSEvent(WStype_t etype, uint8_t* payload, size_t length) {
  switch (etype) {
    case WStype_CONNECTED:
      wsConnected = true;
      authed      = false;
      Serial.println("[ws] connected — sending auth");
      {
        StaticJsonDocument<128> doc;
        doc["type"]       = "auth";
        doc["device_key"] = DEVICE_KEY;
        String out;
        serializeJson(doc, out);
        ws.sendTXT(out);
      }
      break;

    case WStype_TEXT:
      handleMessage((const char*)payload);
      break;

    case WStype_DISCONNECTED:
      wsConnected = false;
      authed      = false;
      memset(&current, 0, sizeof(current));
      digitalWrite(LED_BUILTIN_PIN, LOW);
      Serial.println("[ws] disconnected");
      break;

    case WStype_ERROR:
      Serial.println("[ws] error");
      break;

    default:
      break;
  }
}

// ── WiFi ─────────────────────────────────────────────────────────────────────

static void connectWiFi() {
  Serial.printf("[wifi] connecting to %s", WIFI_SSID);
  WiFi.begin(WIFI_SSID, WIFI_PASSWORD);

  unsigned long start = millis();
  while (WiFi.status() != WL_CONNECTED) {
    if (millis() - start > WIFI_TIMEOUT_MS) {
      Serial.println("\n[wifi] timeout — restarting");
      ESP.restart();
    }
    delay(500);
    Serial.print(".");
  }
  Serial.printf("\n[wifi] connected, IP: %s\n", WiFi.localIP().toString().c_str());
}

// ── Button polling ────────────────────────────────────────────────────────────

static void checkButtons() {
  static const char* actions[3] = {"yes", "yes_always", "no"};
  const unsigned long debounce  = 50;

  for (int i = 0; i < 3; i++) {
    bool state = digitalRead(btns[i].pin);
    if (state != btns[i].lastState) {
      btns[i].lastDebounce = millis();
    }
    if ((millis() - btns[i].lastDebounce) > debounce) {
      if (state == LOW && btns[i].lastState == HIGH) {
        // Falling edge = press
        if (authed && current.active) {
          sendDecision(actions[i]);
        }
      }
    }
    btns[i].lastState = state;
  }
}

// ── Setup / loop ──────────────────────────────────────────────────────────────

void setup() {
  Serial.begin(115200);
  delay(100);

  pinMode(BTN_YES,        INPUT_PULLUP);
  pinMode(BTN_ALWAYS,     INPUT_PULLUP);
  pinMode(BTN_NO,         INPUT_PULLUP);
  pinMode(LED_BUILTIN_PIN, OUTPUT);
  digitalWrite(LED_BUILTIN_PIN, LOW);

  connectWiFi();

#if SERVER_TLS
  ws.beginSSL(SERVER_HOST, SERVER_PORT, SERVER_PATH);
#else
  ws.begin(SERVER_HOST, SERVER_PORT, SERVER_PATH);
#endif

  ws.onEvent(onWSEvent);
  ws.setReconnectInterval(RECONNECT_DELAY_MS);
  ws.enableHeartbeat(25000, 5000, 3);

  Serial.println("[nod] ready");
}

void loop() {
  if (WiFi.status() != WL_CONNECTED) {
    Serial.println("[wifi] lost connection — reconnecting");
    connectWiFi();
  }

  ws.loop();
  checkButtons();
}
