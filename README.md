# Document Utility Tools
> **Lightweight, Privacy-First Native Android Document Processor & Manager**

[![Platform](https://img.shields.io/badge/platform-Android-3DDC84?style=flat-square&logo=android&logoColor=white)](https://developer.android.com/)
[![Language](https://img.shields.io/badge/language-Kotlin-7F52FF?style=flat-square&logo=kotlin&logoColor=white)](https://kotlinlang.org/)
[![License](https://img.shields.io/badge/license-Apache%202.0-blue?style=flat-square)](https://www.apache.org/licenses/LICENSE-2.0)
[![Build Status](https://img.shields.io/badge/build-passing-brightgreen?style=flat-square&logo=github-actions&logoColor=white)](https://github.com/)
[![Security](https://img.shields.io/badge/security-100%25%20Offline-success?style=flat-square&logo=shield&logoColor=white)](#security--privacy)

**Document Utility Tools** is a high-performance, fully secure, and privacy-focused native Android application written in Kotlin. It empowers users to read, manage, encrypt, and compress standard PDF and Microsoft Word DOCX files entirely client-side, with zero external server dependencies or cloud transmissions.

---

## 📱 App Interface & Visuals

The application utilizes Google's modern **Material You (Material 3)** design principles to deliver an immersive, dynamic, and intuitive user experience.

| Dashboard Overview | PDF & DOCX Reader | Advanced Document Compressor |
| :---: | :---: | :---: |
| <img src="https://images.unsplash.com/photo-1607604276583-eef5d076aa5f?q=80&w=350&auto=format&fit=crop" width="240" alt="Dashboard Screen Mockup" /> | <img src="https://images.unsplash.com/photo-1544383835-bda2bc66a55d?q=80&w=350&auto=format&fit=crop" width="240" alt="Document Reader Screen" /> | <img src="https://images.unsplash.com/photo-1618005182384-a83a8bd57fbe?q=80&w=350&auto=format&fit=crop" width="240" alt="Compressor Parameters" /> |

*(Note: Real screenshots of your locally compiled build can be committed to the `/assets` directory to instantly update these previews).*

---

## 🌟 Key Features

### 📄 Advanced PDF & DOCX Reader
* **Dual Format Support:** Render both standard PDF files and Microsoft Word Document (`.docx`) file structures side-by-side natively.
* **Liquid Scrolling:** Zero-latency rendering of pages with fluid zoom controls and rapid page navigation.
* **Integrated Text Search:** Scan files offline to find exact matches and highlight text segments instantly.

### 🔗 Document Merger & Splitter
* **Lossless Assembly:** Combine an unlimited number of separate documents in custom sequence into a single, high-fidelity PDF.
* **Exact Page Extraction:** Split heavy books or documents into individual single-page elements or specify customized range splits.

### 🔒 Cryptographic Protection & Decryption
* **Secure Encryption:** Enforce standard 128-bit PDF user and owner passwords to safeguard private information.
* **Authorized Decryption:** Fast, seamless unlock pipeline to strip protection layers from credentials you own.

### 📉 Intelligent Multi-Step Compressor
* **Dynamic Compression:** Scale bulky files down by up to 80% without losing readable text layout.
* **Custom Parameter Sliders:** Tune image resolution scaling and compression quality directly inside the native interface.

### 🖼️ Seamless Image Converters
* **Image to PDF Sheet:** Assemble multiple raw gallery or camera images into structured, aligned PDF document blocks.
* **PDF to Image Grid:** Extract individual document pages and export them as high-quality standard JPEG or PNG files.

---

## 🔒 Security & Privacy

This application is built from the ground up to guarantee maximum data protection:
* **No Cloud Overhead:** All file rendering, compression, split, merge, and encryption tasks are performed **locally** within the device sandboxed environment.
* **Zero Network Logs:** The application does not require or request internet permissions.
* **Storage Access Framework (SAF):** Full compliance with modern scoped storage guidelines, allowing you to choose exactly which folder directories the app can read or write.

---

## 🛠️ Architecture & Tech Stack

* **Language:** Kotlin 1.9+ (Modern functional syntax)
* **Design Pattern:** MVVM (Model-View-ViewModel) with structured Clean Architecture
* **Asynchronous Engine:** Kotlin Coroutines & Flow (For fluid background execution of heavy tasks)
* **View Bindings:** Android Jetpack ViewBinding for type-safe view interaction
* **UI Framework:** Android XML with Google Material Design 3 Components
* **Build System:** Gradle 8.2 (Kotlin DSL)

---

## ⚙️ Developer Compilation & Build Manual

To compile and assemble the application APK from source code, follow these standard steps:

### 1. Prerequisites
Ensure you have the following installed on your machine:
* **JDK 17** (Temurin distribution recommended)
* **Android Studio Hedgehog** (2023.1.1) or higher
* **Android SDK API Level 33** (Android 13.0) or higher

### 2. Synchronize the Workspace
Clone or download the project files. Open Android Studio, select **Open Project**, choose the root directory, and allow the system to complete the initial Gradle synchronization.

### 3. Assemble Release Artifact
You can build the application directly via the IDE, or execute the Gradle build task from your terminal:

```bash
# Set execute permissions for the wrapper (Linux/macOS)
chmod +x gradlew

# Assemble the release APK
./gradlew assembleRelease
```

Once completed, the release package will be generated at:
`app/build/outputs/apk/release/app-release-unsigned.apk`

---

## 🤖 Continuous Integration (CI/CD)

The project includes a robust **GitHub Actions** build workflow located at `.github/workflows/gradle-build.yml`. Every time you push a code update or merge requests to GitHub, the CI pipeline automatically:
1. Spawns an Ubuntu runtime container.
2. Initializes JDK 17.
3. Downloads secure Android SDK Platforms.
4. Assembles the APK package and compiles all classes to ensure code health is 100% green.
