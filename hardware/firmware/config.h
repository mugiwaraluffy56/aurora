#pragma once

// WiFi credentials
#define WIFI_SSID     "your_ssid"
#define WIFI_PASSWORD "your_password"

// Nod server
#define SERVER_HOST "your-server.com"
#define SERVER_PORT 443
#define SERVER_PATH "/ws/device"
#define SERVER_TLS  true

// Hardware device key — generated via POST /devices/hardware
#define DEVICE_KEY "nod_your_key_here"

// Pin map (ESP32 DevKit C)
#define BTN_YES    18
#define BTN_ALWAYS 19
#define BTN_NO     21

// LED (built-in on GPIO2, optional)
#define LED_BUILTIN_PIN 2

// Timeouts
#define WIFI_TIMEOUT_MS    10000
#define RECONNECT_DELAY_MS  5000
