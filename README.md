# Appnea - Sleep Apnea Detection System

## Demo Video

<iframe src="https://drive.google.com/file/d/1kk584zM3JfrLd97sR-CtKHF59ajFwHDq/preview" width="640" height="480" allow="autoplay"></iframe>

## Overview
Appnea is a smart monitoring system designed to detect sleep apnea and hypopnea from the comfort of home.
It leverages modern technology to monitor vital signs indicative of these conditions during sleep, aiming to make detection accessible and affordable.

For more information regarding the technical details, please refer to the **project_summary.pdf** file. 

## Features
- **Real-time Monitoring:** Tracks breathing rate and oxygen levels using an ESP-32 microcontroller and sensors.
- **Android Application:** Provides a user-friendly interface for interaction and data visualization.
- **Data Analysis:** Utilizes Python for sophisticated data processing like signal filtering and peak detection.

## Technologies Used
- ESP-32 Microcontroller
- Accelerometer
- Pulse Oximeter
- Android Studio
- Python
- Arduino IDE

## System Architecture
- **ESP-32:** Manages sensor data transmission via Bluetooth.
- **Accelerometer:** Monitors chest movements to detect breathing patterns.
- **Pulse Oximeter:** Measures blood oxygen levels and heart rate.
- **Android App:** Acts as the interface for data visualization and user interaction.

## Installation
1. Clone the code in this repository.
2. Use Android Studio to upload it to your phone.
3. Make sure you downloaded the Arduino IDE code and uploaded it to your microcontroller.
4. Make sure that the sensors function correctly.

## Usage
- Turn on the device before going to sleep.
- Make sure the app is set up to receive data from the device.
- Data and events will be displayed in real-time within the app.

 

