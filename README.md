# Smart Drug Box (IoT) 💊📦

> **Smart Medication Management System for the Elderly** > *IoT Lab Project - Summer 2025 (1404)* > *Sharif University of Technology*

## 📖 About the Project

The **Smart Drug Box** is an IoT-based solution designed to assist elderly individuals in managing their daily medication. It addresses the common issue of forgetfulness by providing timely reminders and monitoring pill consumption in real-time.

The system consists of a smart container with 4 compartments, each equipped with sensors to detect the presence of medication. It connects to a mobile application via WiFi to send alerts to the user and their caregivers (family members or nurses).

### Key Objectives
* **Safety:** Prevent overdose or missed doses.
* **Monitoring:** Allow remote tracking of medication intake.
* **Automation:** Reduce the need for constant manual supervision.

## ✨ Features

* **4-Compartment Storage:** Separate slots for different medication times or types.
* **Smart Detection:** Uses **IR Sensors (LM393)** to detect if pills are present or have been removed.
* **WiFi Connectivity:** Powered by **ESP32**, allowing the device to communicate with the internet.
* **Mobile Notifications:** Sends push notifications to a mobile app when it's time to take medicine.
* **Local Alarms:** Activates a buzzer/alarm on the box itself to alert the user.
* **Caregiver Alerts:** If medication is *not* taken within a set time, a secondary alert is sent to the caregiver.
* **Battery Powered:** Portable design using Li-ion batteries.

## 🛠️ Hardware Components

| Component | Quantity | Description |
| :--- | :---: | :--- |
| **ESP32-WROOM32** | 1 | Main microcontroller with WiFi/Bluetooth capabilities. |
| **IR Sensor Module (LM393)** | 4 | Detects the presence/removal of pills in each compartment. |
| **Li-ion Battery (18650)** | 2 | Power source for portability. |
| **Battery Charge/Discharge Module** | 1 | Manages battery power and charging. |
| **Buzzer/Alarm** | 1 | Audio output for reminders. |

## 🧩 Architecture

The system operates in the following flow:
1.  **Schedule:** Medication times are set via the Mobile App/Code.
2.  **Alert:** At the scheduled time, the ESP32 triggers the local alarm and sends a notification via WiFi.
3.  **Action:** The user opens the box and takes the pill.
4.  **Verification:** The IR sensor detects the removal of the pill.
5.  **Logging:** The status ("Taken" or "Missed") is updated in the app.
6.  **Escalation:** If the pill is not removed, a warning is sent to the caregiver.

## 🚀 Getting Started

### Prerequisites
* [Arduino IDE](https://www.arduino.cc/en/software) or [PlatformIO](https://platformio.org/)
* ESP32 Board support installed in your IDE.
* Required Libraries (e.g., `WiFi.h`, `PubSubClient` for MQTT, etc. - *Update this list based on your actual code*).

### Installation

1.  **Clone the Repo:**
    ```bash
    git clone [https://github.com/erfanteymoyri/IoT-Project-Smart-Drug-Box.git](https://github.com/erfanteymoyri/IoT-Project-Smart-Drug-Box.git)
    ```
2.  **Hardware Setup:**
    * Connect the IR sensors to the ESP32 GPIO pins defined in the code.
    * Connect the Buzzer and Battery module.
3.  **Firmware:**
    * Open the `.ino` file in Arduino IDE.
    * Update the `SSID` and `PASSWORD` variables with your WiFi credentials.
    * Upload the code to the ESP32.
4.  **Mobile App:**
    * Install the companion app (or configure the dashboard if using a web interface).

## 👥 Team Members

This project was developed by the **"Aaj" (عاج)** team:

* **Amirreza Jafari** (402105835) - *Hardware & Design*
* **Seyed Ahmad Mousavi Aval** (402106648) - *ESP32 Programming & Frontend & Communication Between ESP32 and App*
* **Erfan Teymouri** (402105813) - *Mobile Application & Backend*

## 🏫 Course Info

* **University:** Sharif University of Technology
* **Department:** Computer Engineering
* **Course:** Internet of Things (IoT) Laboratory
* **Instructor:** Eng. Javadi
* **Semester:** Summer 1404

## 📄 License

This project is open-source. Please check the [LICENSE](https://github.com/erfanteymoyri/IoT-Project-Smart-Drug-Box/blob/main/LICENSE) file for more details.
