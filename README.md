# PDF Utility Tools
> **100% Privacy-First, On-Device Native Android PDF Suite & Document Reader**

[![Platform](https://img.shields.io/badge/platform-Android-3DDC84?style=flat-square&logo=android&logoColor=white)](https://developer.android.com/)
[![Language](https://img.shields.io/badge/language-Kotlin-7F52FF?style=flat-square&logo=kotlin&logoColor=white)](https://kotlinlang.org/)
[![License](https://img.shields.io/badge/license-Apache%202.0-blue?style=flat-square)](https://www.apache.org/licenses/LICENSE-2.0)
[![Build Status](https://img.shields.io/badge/build-passing-brightgreen?style=flat-square&logo=github-actions&logoColor=white)](https://github.com/)
[![Security](https://img.shields.io/badge/security-100%25%20Offline-success?style=flat-square&logo=shield&logoColor=white)](#security--privacy)

**PDF Utility Tools** is a high-performance, privacy-focused native Android application designed to merge, split, compress, protect, rotate, watermark, convert, and view PDF documents entirely on-device with zero cloud dependencies and an enhanced multi-source PDF discovery system.

---

## 📱 App Interface & Visuals

The application utilizes Google's modern **Material You (Material 3)** design principles to deliver an intuitive, clean, and distraction-free document experience.

| Dashboard Overview | PDF Reader & Viewer | Document Discovery & Sources |
| :---: | :---: | :---: |
| <img src="https://images.unsplash.com/photo-1607604276583-eef5d076aa5f?q=80&w=350&auto=format&fit=crop" width="240" alt="Dashboard Screen Mockup" /> | <img src="https://images.unsplash.com/photo-1544383835-bda2bc66a55d?q=80&w=350&auto=format&fit=crop" width="240" alt="Document Reader Screen" /> | <img src="https://images.unsplash.com/photo-1618005182384-a83a8bd57fbe?q=80&w=350&auto=format&fit=crop" width="240" alt="Document Sources" /> |

---

## 🌟 Key Features

### 📄 Fluid PDF Reader & Viewer
* **High-Res Rendering:** Zero-latency continuous page rendering with multi-touch zoom and pinch controls.
* **Night Mode & Inverted Views:** High contrast reading options for low-light environments.
* **Integrated Search & Navigation:** Fast page jump and keyword highlighting across documents.

### 🔗 Document Merger & Splitter
* **Lossless Merging:** Combine multiple PDF documents with custom drag-and-drop page order.
* **Precision Extraction:** Split documents by custom page numbers or specific ranges into lightweight files.

### 🔒 Cryptographic Lock & Decryptor
* **Secure Encryption:** Enforce standard 128-bit PDF user and owner passwords.
* **Unlock & Decrypt:** Instant protection removal for authorized credential holders.

### 📉 Smart PDF & Image Compressor
* **Adaptive Compression:** Reduce file sizes significantly while maintaining crisp typography.
* **Custom Presets:** Interactive sliders for DPI and quality adjustments.

### 🎨 Powerful Image Toolbox Engine (Inspired by T8RIN/ImageToolbox)
* **Preset Filters & Color Tuning:** Grayscale, Sepia, Vintage, Warm, Cool, High Contrast, Invert, Vignette, and Black & White document filter chains.
* **Sliders & Controls:** Real-time brightness, contrast multiplier, and saturation tuning with live preview.
* **Transforms:** Rotate 90° CW, Flip Horizontal, and Flip Vertical.
* **Multi-Image Stitching & Stacking:** Seamlessly combine screenshots or photos into vertical continuous strips or horizontal grids.
* **EXIF Sanitization:** Strip location and hardware tracking metadata automatically upon export.
* **Format & Quality Control:** Export to JPEG, PNG, or WEBP with fine-grained compression quality sliders.

### 🔄 Image & PDF Converters
* **Image to PDF:** Convert photos and scanned sheets into organized, single or multi-page PDFs.
* **PDF to Image:** Export individual pages as PNG, JPG, or WebP images.
* **Rotate & Watermark:** Rotate pages in 90° increments and stamp custom security watermarks.

### 📁 Multi-Source PDF Discovery Subsystem
* **Storage Access Framework (SAF):** Direct integration with system file pickers and external storage providers.
* **Folder Categorization:** Auto-indexes device folders including WhatsApp Documents, Telegram, Downloads, and custom folders.
* **Recent Activity Carousel:** Instant access to recently manipulated documents and reading history.

---

## 🔒 Security & Privacy

* **100% On-Device:** All file processing happens locally inside the sandboxed Android process.
* **Zero Telemetry:** No tracking, no ads, and no external server uploads.
* **Scoped Storage Compliant:** Grants granular control over file directory access.

---

## 🛠️ Architecture & Tech Stack

* **Language:** Kotlin 1.9+
* **Design Pattern:** MVVM with Clean Architecture
* **PDF Engines:** MuPDF & Apache PDFBox (Android optimized)
* **UI Framework:** Google Material 3 Components with Jetpack ViewBinding
* **Build System:** Gradle (Kotlin DSL)

---

## ⚙️ Developer Compilation & Build Manual

```bash
# Set execute permissions for the wrapper (Linux/macOS)
chmod +x gradlew

# Assemble the release APK
./gradlew assembleRelease
```

The APK will be generated at:
`app/build/outputs/apk/release/app-release-unsigned.apk`
