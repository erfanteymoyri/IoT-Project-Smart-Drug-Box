#include <WiFi.h>
#include <PubSubClient.h>
#include <WiFiManager.h>
#include <LittleFS.h>
#include <ArduinoJson.h>
#include "HX711.h"

// CHANGED: Use different GPIO for config button (NOT GPIO0 or GPIO2)
#define CONFIG_BUTTON 4  // Using GPIO4 instead

// MQTT Configuration
char mqtt_server[40] = "87.248.152.126";
char mqtt_port[6] = "1883";
char mqtt_user[32] = "AJ_IoT";
char mqtt_pass[32] = "hgsde32993004";

// Topics
const char* topic_app_commands = "esp32/commands";
const char* topic_medication = "esp32/medication/taken";

// CHANGED: Removed GPIO2 from HX711 pins
#define DT1  19
#define SCK1 18
#define LED1 16

#define DT2  25 
#define SCK2 26
#define LED2 17

#define DT3  22
#define SCK3 23
#define LED3 21

#define DT4  35
#define SCK4 32
#define LED4 27


HX711 scale1, scale2, scale3, scale4;

// Weight Variables
float w1 = 0, w2 = 0, w3 = 0, w4 = 0;
float stable_w1, stable_w2, stable_w3, stable_w4;
float sstable_w1, sstable_w2, sstable_w3, sstable_w4;
float prev_w1 = 0, prev_w2 = 0, prev_w3 = 0, prev_w4 = 0;
float prev_stable_w1 = 0, prev_stable_w2 = 0, prev_stable_w3 = 0, prev_stable_w4 = 0;
float sprev_stable_w1 = 0, sprev_stable_w2 = 0, sprev_stable_w3 = 0, sprev_stable_w4 = 0;
float med_w1 = 1000, med_w2 = 1000, med_w3 = 1000, med_w4 = 1000;
bool ret1 = false , ret2 = false, ret3 = false ,ret4 = false;
bool led1 = false, led2 = false, led3 = false, led4 = false;
const float THRESHOLD = 0.5;

WiFiClient espClient;
PubSubClient client(espClient);
WiFiManager wifiManager;

void reset_part(int part) {
  if(part == 1) {
    w1 = 0;
    prev_w1 = 0;
    sprev_stable_w1 = 0;
    prev_stable_w1 = 0;
    med_w1 = 1000;
    ret1 = false;
  }

  if(part == 2) {
    w2 = 0;
    prev_w2 = 0;
    sprev_stable_w2 = 0;
    prev_stable_w2 = 0;
    med_w2 = 1000;
    ret2 = false;
  }

  if(part == 3) {
    w3 = 0;
    prev_w3 = 0;
    sprev_stable_w3 = 0;
    prev_stable_w3 = 0;
    med_w3 = 1000;
    ret3 = false;
  }

  if(part == 4) {
    w4 = 0;
    prev_w4 = 0;
    sprev_stable_w4 = 0;
    prev_stable_w4 = 0;
    med_w4 = 1000;
    ret4 = false;
  }
}

void turn_LED_on(int part) {
  if(part == 1) {
    digitalWrite(LED1, HIGH); 
  }

  if(part == 2) {
    digitalWrite(LED2, HIGH); 
  }

  if(part == 3) {
    digitalWrite(LED3, HIGH); 
  }

  if(part == 4) {
    digitalWrite(LED4, HIGH); 
  }
}

void turn_LED_off(int part) {
  if(part == 1) {
    digitalWrite(LED1, LOW);
  }

  if(part == 2) {
    digitalWrite(LED2, LOW);
  }

  if(part == 3) {
    digitalWrite(LED3, LOW);
  }

  if(part == 4) {
    digitalWrite(LED4, LOW);
  }
}


void setPrevW() {
  prev_w1 = w1;
  prev_w2 = w2;
  prev_w3 = w3;
  prev_w4 = w4;
}

void setup() {
  Serial.begin(115200);
  while(!Serial); // Wait for serial port
  
  // CHANGED: Initialize LittleFS first
  if (!LittleFS.begin(true)) {
    LittleFS.format();
    LittleFS.begin(true);
  }

  // CHANGED: Simplified WiFi setup
  WiFi.mode(WIFI_STA);
  pinMode(CONFIG_BUTTON, INPUT_PULLUP);
  
  if (digitalRead(CONFIG_BUTTON) == LOW) {
    Serial.println("Entering config mode");
    wifiManager.startConfigPortal("ESP32_Config");
  } else {
    wifiManager.autoConnect("ESP32_Config");
  }

  // MQTT Setup
  client.setServer(mqtt_server, atoi(mqtt_port));
  client.setCallback(mqttCallback);

  // CHANGED: Initialize scales AFTER WiFi
  scale1.begin(DT1, SCK1);
  scale2.begin(DT2, SCK2); 
  scale3.begin(DT3, SCK3);
  scale4.begin(DT4, SCK4);

  scale1.set_scale(3650); scale1.tare();
  scale2.set_scale(4420); scale2.tare();
  scale3.set_scale(3500); scale3.tare();
  scale4.set_scale(4400); scale4.tare();

  pinMode(LED1, OUTPUT);
  pinMode(LED2, OUTPUT);
  pinMode(LED3, OUTPUT);
  pinMode(LED4, OUTPUT);

  Serial.println("System ready");
}

void loop() {
  if (!client.connected()) {
    reconnectMQTT();
  }
  client.loop();
  setPrevW();
  measureWeights();
  checkMedication();
  delay(100);
}

void mqttCallback(char* topic, byte* payload, unsigned int length) {

  // Parse the incoming message
  StaticJsonDocument<200> doc;
  deserializeJson(doc, payload, length);

  if (strcmp(topic, topic_app_commands) == 0) {
    const char* command = doc["command"];
    int part = doc["part"];

    if (strcmp(command, "start_tracking") == 0) {
      reset_part(part);
    } else if (strcmp(command, "alarm_on") == 0) {
      turn_LED_on(part);
    } else if (strcmp(command, "alarm_off") == 0) {
      turn_LED_off(part);
    }
  }
}


void reconnectMQTT() {
  while (!client.connected()) {
    if (client.connect("ESP32Client", mqtt_user, mqtt_pass)) {
      client.subscribe(topic_app_commands);
    } else {
      delay(2000);
    }
  }
}

void measureWeights() {
  w1 = scale1.get_units(3);
  w2 = scale2.get_units(3);
  w3 = scale3.get_units(3);
  w4 = scale4.get_units(3);
  if (w1 < 0){
    w1 = 0;
  } 
  if (w2 < 0) { 
    w2 = 0;
  }
  if (w3 < 0) { 
    w3 = 0;
  } 
  if (w3 < 0) { 
    w3 = 0;
  } 

  if (abs(w1 - prev_w1) > THRESHOLD) {
    stable_w1 = scale1.get_units(20);
    while(abs(stable_w1 - prev_stable_w1) > THRESHOLD) {
      prev_stable_w1 = stable_w1;
      stable_w1 = scale1.get_units(20);
    }
    sstable_w1 = stable_w1;
  }

  if (abs(w2 - prev_w2) > THRESHOLD) {
    stable_w2 = scale2.get_units(20);
    while(abs(stable_w2 - prev_stable_w2) > THRESHOLD) {
      prev_stable_w2 = stable_w2;
      stable_w2 = scale2.get_units(20);
    }
    sstable_w2 = stable_w2;
  }

  // if (abs(w3 - prev_w3) > THRESHOLD) {
  //   stable_w3 = scale3.get_units(20);
  //   while(abs(stable_w3 - prev_stable_w3) > THRESHOLD) {
  //     prev_stable_w3 = stable_w3;
  //     stable_w3 = scale3.get_units(20);
  //   }
  //   sstable_w3 = stable_w3;
  // }

  if (abs(w4 - prev_w4) > THRESHOLD) {
    stable_w4 = scale4.get_units(20);
    while(abs(stable_w4 - prev_stable_w1) > THRESHOLD) {
      prev_stable_w4 = stable_w4;
      stable_w4 = scale1.get_units(20);
    }
    sstable_w4 = stable_w4;
  }

  if (sstable_w1 < 0){
    sstable_w1 = 0;
    scale1.set_scale(3650); scale1.tare();
  } 

  if (sstable_w2 < 0) { 
    sstable_w2 = 0;
    scale2.set_scale(4420); scale2.tare();
  }
  if (sstable_w3 < 0) { 
    sstable_w3 = 0;
    scale3.set_scale(3500); scale3.tare();
  } 
  if (sstable_w4 < 0) { 
    sstable_w4 = 0;
    scale4.set_scale(4400); scale4.tare();
  } 
}

void checkMedication() {
  // برداشتن
  if (sprev_stable_w1 - sstable_w1 > THRESHOLD) {
    sprev_stable_w1 = sstable_w1;
    ret1 = false;
  }
  // گذاشتن
  if (sstable_w1 - sprev_stable_w1 > THRESHOLD) {
    sprev_stable_w1 = sstable_w1;
    ret1 = true;
  } //مصرف
  if(med_w1 - sstable_w1 > THRESHOLD && ret1) {
    StaticJsonDocument<100> doc;
    doc["part"] = 1;
    char buffer[100];
    serializeJson(doc, buffer);
    client.publish(topic_medication, buffer);
    med_w1 = sstable_w1;
  }

  //برداشتن
  if (sprev_stable_w2 - sstable_w2 > THRESHOLD) {
    sprev_stable_w2 = sstable_w2;
    ret2 = false;
  }
  //گذاشتن
  if (sstable_w2 - sprev_stable_w2 > THRESHOLD) {
    sprev_stable_w2 = sstable_w2;
    ret2 = true;
  }
  //مصرف
  if(med_w2 - sstable_w2 > THRESHOLD && ret2) {
    StaticJsonDocument<100> doc;
    doc["part"] = 2;
    char buffer[100];
    serializeJson(doc, buffer);
    client.publish(topic_medication, buffer);
    med_w2 = sstable_w2;
  }


//3
  //گذاشتن
  if (sstable_w3 - sprev_stable_w3 > THRESHOLD) {
    sprev_stable_w3 = sstable_w3;
    ret3 = true;
  }
  //برداشتن
  if (sprev_stable_w3 - sstable_w3 > THRESHOLD) {
    sprev_stable_w3 = sstable_w3;
    ret3 = false;
  }
  //مصرف
  if(med_w3 - sstable_w3 > THRESHOLD && ret3) {
    StaticJsonDocument<100> doc;
    doc["part"] = 3;
    char buffer[100];
    serializeJson(doc, buffer);
    client.publish(topic_medication, buffer);
    med_w3 = sstable_w3;
  }

    // برداشتن
  if (sprev_stable_w4 - sstable_w4 > THRESHOLD) {
    sprev_stable_w4 = sstable_w4;
    ret4 = false;
  }
  // گذاشتن
  if (sstable_w4 - sprev_stable_w4 > THRESHOLD) {
    sprev_stable_w4 = sstable_w4;
    ret4 = true;
  } 
  //مصرف
  if(med_w4 - sstable_w4 > THRESHOLD && ret4) {
    StaticJsonDocument<100> doc;
    doc["part"] = 4;
    char buffer[100];
    serializeJson(doc, buffer);
    client.publish(topic_medication, buffer);
    med_w4 = sstable_w4;
  }
}