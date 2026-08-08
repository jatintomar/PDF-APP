import React, { useState, useEffect } from "react";
import { 
  FileText, 
  Lock, 
  Unlock, 
  Merge, 
  Download, 
  Check, 
  ShieldCheck, 
  Smartphone, 
  Info, 
  Eye, 
  Settings, 
  ArrowRight, 
  FileCode, 
  CheckCircle2,
  FolderSync,
  Sun,
  Moon
} from "lucide-react";
import { AppLogo } from "./components/AppLogo";

export default function App() {
  const [copiedCmd, setCopiedCmd] = useState(false);
  const [theme, setTheme] = useState(() => {
    if (typeof window !== "undefined") {
      return localStorage.getItem("theme") || "light";
    }
    return "light";
  });

  useEffect(() => {
    if (theme === "dark") {
      document.documentElement.classList.add("dark");
    } else {
      document.documentElement.classList.remove("dark");
    }
    localStorage.setItem("theme", theme);
  }, [theme]);

  const handleCopyCommand = () => {
    navigator.clipboard.writeText("./gradlew assembleRelease");
    setCopiedCmd(true);
    setTimeout(() => setCopiedCmd(false), 2000);
  };

  return (
    <div className="min-h-screen bg-slate-50 dark:bg-slate-950 text-slate-800 dark:text-slate-200 antialiased font-sans transition-colors duration-300">
      {/* Top Banner / Announcement */}
      <div className="bg-blue-600 dark:bg-blue-900 text-white text-xs font-semibold py-2 px-4 text-center tracking-wide">
        🎉 Version 1.2.0 Released: Added native DOCX format reading and viewer capability!
      </div>

      {/* Navigation Header */}
      <header className="bg-white dark:bg-slate-900 border-b border-slate-200 dark:border-slate-800 py-4 px-6 md:px-12 flex items-center justify-between shadow-xs transition-colors duration-300">
        <div className="flex items-center gap-3">
          <AppLogo size="md" />
          <div>
            <h1 className="text-lg font-bold text-slate-900 dark:text-white tracking-tight leading-none">Document Utility Tools</h1>
            <p className="text-[10px] text-slate-500 dark:text-slate-400 font-medium mt-1">Lightweight Android Document Processor</p>
          </div>
        </div>

        <div className="flex items-center gap-3">
          <button
            onClick={() => setTheme(theme === "light" ? "dark" : "light")}
            className="p-2.5 text-slate-600 hover:text-slate-900 hover:bg-slate-100 dark:text-slate-400 dark:hover:text-white dark:hover:bg-slate-800 rounded-lg transition-colors cursor-pointer mr-1"
            aria-label="Toggle Theme"
            id="btn_theme_toggle"
          >
            {theme === "light" ? <Moon className="w-4 h-4" /> : <Sun className="w-4 h-4" />}
          </button>
          <a
            href="#install-guide"
            className="hidden md:flex items-center gap-1.5 px-3.5 py-1.5 text-slate-600 dark:text-slate-300 hover:text-slate-900 dark:hover:text-white font-medium text-xs rounded-lg transition-colors"
          >
            <FileCode className="w-4 h-4" />
            Build Guide
          </a>
          <a
            href="#features"
            className="px-4 py-2 bg-slate-900 dark:bg-slate-800 hover:bg-slate-800 dark:hover:bg-slate-700 text-white font-medium text-xs rounded-lg transition-colors shadow-sm flex items-center gap-1.5"
          >
            <Smartphone className="w-3.5 h-3.5" />
            Android Project
          </a>
        </div>
      </header>

      {/* Hero Section */}
      <section className="py-16 px-6 md:px-12 max-w-6xl mx-auto grid grid-cols-1 lg:grid-cols-12 gap-12 items-center">
        <div className="lg:col-span-7 space-y-6">
          <div className="inline-flex items-center gap-1.5 px-2.5 py-1 bg-blue-50 dark:bg-blue-950/40 text-blue-700 dark:text-blue-400 rounded-full text-[10px] font-bold uppercase tracking-wider border border-blue-100/30">
            <ShieldCheck className="w-3 h-3" /> Fully Offline &amp; Secure
          </div>
          <h2 className="text-4xl md:text-5xl font-black text-slate-950 dark:text-white tracking-tight leading-none">
            The ultimate offline <span className="text-blue-600 dark:text-blue-400">Document Utility</span> for Android.
          </h2>
          <p className="text-slate-600 dark:text-slate-300 text-base md:text-lg leading-relaxed max-w-xl">
            A privacy-first, blazing-fast native Android application designed to read, lock, unlock, compress, split, and merge your PDF and DOCX files without ever uploading them to any external servers.
          </p>

          <div className="flex flex-wrap gap-3 pt-2">
            <a
              href="#install-guide"
              className="px-5 py-3 bg-blue-600 hover:bg-blue-700 text-white font-semibold text-sm rounded-xl transition-all hover:translate-y-[-1px] shadow-md shadow-blue-500/15 flex items-center gap-2 cursor-pointer"
            >
              Compile Android App
              <ArrowRight className="w-4 h-4" />
            </a>
            <a
              href="#features"
              className="px-5 py-3 bg-white dark:bg-slate-900 hover:bg-slate-50 dark:hover:bg-slate-800 text-slate-800 dark:text-slate-200 border border-slate-200 dark:border-slate-800 font-semibold text-sm rounded-xl transition-all shadow-xs flex items-center gap-2 cursor-pointer"
            >
              Explore Features
            </a>
          </div>
        </div>

        {/* Project Target & Build Specifications */}
        <div className="lg:col-span-5 flex justify-center w-full">
          <div className="w-full max-w-sm bg-white dark:bg-slate-900 rounded-3xl border border-slate-200 dark:border-slate-800 p-6 shadow-sm space-y-5 transition-all">
            <div className="flex items-center gap-3 border-b border-slate-100 dark:border-slate-800 pb-4">
              <AppLogo size="sm" />
              <div>
                <h4 className="text-sm font-bold text-slate-900 dark:text-white">Android Project Specs</h4>
                <p className="text-[10px] text-slate-500 dark:text-slate-400">Target Environment Configuration</p>
              </div>
            </div>

            <div className="space-y-3">
              <div className="flex justify-between items-center text-xs">
                <span className="text-slate-500 dark:text-slate-400 font-medium">Core Language</span>
                <span className="font-bold text-slate-800 dark:text-slate-200 bg-slate-100 dark:bg-slate-800 px-2 py-0.5 rounded text-[10px]">Kotlin 1.9+</span>
              </div>
              <div className="flex justify-between items-center text-xs">
                <span className="text-slate-500 dark:text-slate-400 font-medium">Architecture</span>
                <span className="font-bold text-slate-800 dark:text-slate-200 bg-slate-100 dark:bg-slate-800 px-2 py-0.5 rounded text-[10px]">MVVM / Clean Arch</span>
              </div>
              <div className="flex justify-between items-center text-xs">
                <span className="text-slate-500 dark:text-slate-400 font-medium">Build Toolchain</span>
                <span className="font-bold text-slate-800 dark:text-slate-200 bg-slate-100 dark:bg-slate-800 px-2 py-0.5 rounded text-[10px]">Gradle Kotlin DSL</span>
              </div>
              <div className="flex justify-between items-center text-xs">
                <span className="text-slate-500 dark:text-slate-400 font-medium">Minimum Android SDK</span>
                <span className="font-bold text-slate-800 dark:text-slate-200 bg-slate-100 dark:bg-slate-800 px-2 py-0.5 rounded text-[10px]">API 24 (Android 7.0)</span>
              </div>
              <div className="flex justify-between items-center text-xs">
                <span className="text-slate-500 dark:text-slate-400 font-medium">Target Android SDK</span>
                <span className="font-bold text-slate-800 dark:text-slate-200 bg-slate-100 dark:bg-slate-800 px-2 py-0.5 rounded text-[10px]">API 34 (Android 14)</span>
              </div>
              <div className="flex justify-between items-center text-xs">
                <span className="text-slate-500 dark:text-slate-400 font-medium">Offline Core Engine</span>
                <span className="font-bold text-emerald-600 dark:text-emerald-400 bg-emerald-50 dark:bg-emerald-950/30 border border-emerald-100 dark:border-emerald-900/40 px-2 py-0.5 rounded text-[10px]">100% Client-Side</span>
              </div>
            </div>

            <div className="pt-3 border-t border-slate-100 dark:border-slate-800 text-[10px] text-slate-500 dark:text-slate-400 leading-relaxed">
              * Fully local processing utilizing <strong>Android Storage Access Framework (SAF)</strong> and sandboxed directory security. Zero external web calls.
            </div>
          </div>
        </div>
      </section>

      {/* Feature Grid Section */}
      <section id="features" className="py-16 bg-white dark:bg-slate-900 border-y border-slate-200 dark:border-slate-800 transition-colors duration-300">
        <div className="max-w-6xl mx-auto px-6 md:px-12">
          <div className="text-center max-w-xl mx-auto space-y-3 mb-12">
            <h3 className="text-xs font-bold text-blue-600 dark:text-blue-400 uppercase tracking-widest">Premium Core Engine</h3>
            <h4 className="text-2xl md:text-3xl font-black text-slate-950 dark:text-white tracking-tight">
              Powerful tools, built directly into the native Android package.
            </h4>
          </div>

          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-8">
            {/* Feature 1 */}
            <div className="p-6 bg-slate-50 dark:bg-slate-950 rounded-2xl border border-slate-100 dark:border-slate-800 hover:border-slate-200 dark:hover:border-slate-700 transition-all space-y-4">
              <div className="w-10 h-10 bg-blue-100 dark:bg-blue-950 text-blue-600 dark:text-blue-400 rounded-xl flex items-center justify-center font-bold">
                <FileText className="w-5 h-5" />
              </div>
              <h5 className="font-bold text-slate-900 dark:text-white text-base">Advanced PDF &amp; DOCX Reader</h5>
              <p className="text-slate-600 dark:text-slate-300 text-sm leading-relaxed">
                Seamlessly read both standard PDF files and Microsoft Word DOCX files side-by-side. Featuring real-time page-by-page rendering, zoom controls, and text search integration.
              </p>
            </div>

            {/* Feature 2 */}
            <div className="p-6 bg-slate-50 dark:bg-slate-950 rounded-2xl border border-slate-100 dark:border-slate-800 hover:border-slate-200 dark:hover:border-slate-700 transition-all space-y-4">
              <div className="w-10 h-10 bg-emerald-100 dark:bg-emerald-950 text-emerald-600 dark:text-emerald-400 rounded-xl flex items-center justify-center font-bold">
                <Merge className="w-5 h-5" />
              </div>
              <h5 className="font-bold text-slate-900 dark:text-white text-base">Lossless Merger &amp; Splitter</h5>
              <p className="text-slate-600 dark:text-slate-300 text-sm leading-relaxed">
                Combine an unlimited number of PDF documents with custom sequencing, or split a heavy document into exact page ranges or single-page PDF elements.
              </p>
            </div>

            {/* Feature 3 */}
            <div className="p-6 bg-slate-50 dark:bg-slate-950 rounded-2xl border border-slate-100 dark:border-slate-800 hover:border-slate-200 dark:hover:border-slate-700 transition-all space-y-4">
              <div className="w-10 h-10 bg-amber-100 dark:bg-amber-950 text-amber-600 dark:text-amber-400 rounded-xl flex items-center justify-center font-bold">
                <Lock className="w-5 h-5" />
              </div>
              <h5 className="font-bold text-slate-900 dark:text-white text-base">Cryptographic Locker &amp; Decryptor</h5>
              <p className="text-slate-600 dark:text-slate-300 text-sm leading-relaxed">
                Protect private information by encrypting files using standard 128-bit PDF user and owner passwords, or quickly strip lock protection if you have the key.
              </p>
            </div>

            {/* Feature 4 */}
            <div className="p-6 bg-slate-50 dark:bg-slate-950 rounded-2xl border border-slate-100 dark:border-slate-800 hover:border-slate-200 dark:hover:border-slate-700 transition-all space-y-4">
              <div className="w-10 h-10 bg-pink-100 dark:bg-pink-950 text-pink-600 dark:text-pink-400 rounded-xl flex items-center justify-center font-bold">
                <Settings className="w-5 h-5" />
              </div>
              <h5 className="font-bold text-slate-900 dark:text-white text-base">High-Performance Compressor</h5>
              <p className="text-slate-600 dark:text-slate-300 text-sm leading-relaxed">
                Shrink bulky files by up to 80% with our multi-step compression pipeline, and adjust image compression parameters natively inside the application.
              </p>
            </div>

            {/* Feature 5 */}
            <div className="p-6 bg-slate-50 dark:bg-slate-950 rounded-2xl border border-slate-100 dark:border-slate-800 hover:border-slate-200 dark:hover:border-slate-700 transition-all space-y-4">
              <div className="w-10 h-10 bg-indigo-100 dark:bg-indigo-950 text-indigo-600 dark:text-indigo-400 rounded-xl flex items-center justify-center font-bold">
                <FolderSync className="w-5 h-5" />
              </div>
              <h5 className="font-bold text-slate-900 dark:text-white text-base">Fidelity Image Converters</h5>
              <p className="text-slate-600 dark:text-slate-300 text-sm leading-relaxed">
                Convert high-resolution camera images into clean, structured PDF sheets or export PDF page grids as standard JPG or PNG images instantly.
              </p>
            </div>

            {/* Feature 6 */}
            <div className="p-6 bg-slate-50 dark:bg-slate-950 rounded-2xl border border-slate-100 dark:border-slate-800 hover:border-slate-200 dark:hover:border-slate-700 transition-all space-y-4">
              <div className="w-10 h-10 bg-teal-100 dark:bg-teal-950 text-teal-600 dark:text-teal-400 rounded-xl flex items-center justify-center font-bold">
                <ShieldCheck className="w-5 h-5" />
              </div>
              <h5 className="font-bold text-slate-900 dark:text-white text-base">Storage Access Framework</h5>
              <p className="text-slate-600 dark:text-slate-300 text-sm leading-relaxed">
                Fully integrates with Google Storage Access Framework (SAF) to let you specify target directory writes securely with system-wide persistency.
              </p>
            </div>
          </div>
        </div>
      </section>

      {/* Developer and Build Instruction Section */}
      <section id="install-guide" className="py-16 bg-slate-50 dark:bg-slate-950 transition-colors duration-300">
        <div className="max-w-4xl mx-auto px-6">
          <div className="bg-white dark:bg-slate-900 rounded-3xl border border-slate-200 dark:border-slate-800 p-8 shadow-sm space-y-6 transition-colors duration-300">
            <div className="flex items-center gap-3">
              <div className="w-10 h-10 bg-slate-950 dark:bg-slate-800 text-white rounded-xl flex items-center justify-center font-bold">
                <FileCode className="w-5 h-5" />
              </div>
              <div>
                <h4 className="text-xl font-black text-slate-950 dark:text-white">Android Build &amp; Run Manual</h4>
                <p className="text-xs text-slate-500 dark:text-slate-400 font-medium">How to compile the source code of the Android App</p>
              </div>
            </div>

            <p className="text-slate-600 dark:text-slate-300 text-sm leading-relaxed">
              This repository contains a lightweight, fully compliant native Android app project built with Kotlin and Gradle. To build the application APK from source, follow the standard steps below:
            </p>

            <div className="space-y-4 pt-2">
              <div className="flex gap-4">
                <div className="w-6 h-6 rounded-full bg-slate-100 dark:bg-slate-800 text-slate-800 dark:text-slate-200 text-xs font-bold flex items-center justify-center shrink-0 mt-0.5">
                  1
                </div>
                <div>
                  <h5 className="font-bold text-slate-900 dark:text-white text-sm">Download the Source Project</h5>
                  <p className="text-slate-600 dark:text-slate-400 text-xs mt-1 leading-relaxed">
                    Download the repository ZIP containing the Android project structure. The core Android app lies inside the <code>/app</code> directory.
                  </p>
                </div>
              </div>

              <div className="flex gap-4">
                <div className="w-6 h-6 rounded-full bg-slate-100 dark:bg-slate-800 text-slate-800 dark:text-slate-200 text-xs font-bold flex items-center justify-center shrink-0 mt-0.5">
                  2
                </div>
                <div>
                  <h5 className="font-bold text-slate-900 dark:text-white text-sm">Open in Android Studio</h5>
                  <p className="text-slate-600 dark:text-slate-400 text-xs mt-1 leading-relaxed">
                    Launch Android Studio (Hedgehog or later recommended). Select <strong>Open Project</strong>, navigate to the root directory, and open it. Allow the build system to sync the Gradle dependencies automatically.
                  </p>
                </div>
              </div>

              <div className="flex gap-4">
                <div className="w-6 h-6 rounded-full bg-slate-100 dark:bg-slate-800 text-slate-800 dark:text-slate-200 text-xs font-bold flex items-center justify-center shrink-0 mt-0.5">
                  3
                </div>
                <div>
                  <h5 className="font-bold text-slate-900 dark:text-white text-sm">Compile and Assemble Release</h5>
                  <p className="text-slate-600 dark:text-slate-400 text-xs mt-1 leading-relaxed">
                    Use the terminal or Android Studio's Gradle tab to execute the build command. Run:
                  </p>

                  <div className="mt-2.5 bg-slate-950 dark:bg-slate-950 text-slate-100 font-mono text-xs p-3 rounded-xl flex items-center justify-between border border-slate-800 dark:border-slate-800 shadow-inner">
                    <span>./gradlew assembleRelease</span>
                    <button 
                      onClick={handleCopyCommand}
                      className="text-[10px] bg-slate-800 hover:bg-slate-700 text-slate-300 font-bold px-2.5 py-1 rounded transition-colors cursor-pointer"
                    >
                      {copiedCmd ? "Copied!" : "Copy"}
                    </button>
                  </div>
                </div>
              </div>

              <div className="flex gap-4">
                <div className="w-6 h-6 rounded-full bg-slate-100 dark:bg-slate-800 text-slate-800 dark:text-slate-200 text-xs font-bold flex items-center justify-center shrink-0 mt-0.5">
                  4
                </div>
                <div>
                  <h5 className="font-bold text-slate-900 dark:text-white text-sm">Deploy or Install</h5>
                  <p className="text-slate-600 dark:text-slate-400 text-xs mt-1 leading-relaxed">
                    Once the compilation is complete, you can find the build APK at <code>/app/build/outputs/apk/release/app-release-unsigned.apk</code>. Sideload this file onto any compatible Android device (Android 7.0 / API 24 or later) to install the app.
                  </p>
                </div>
              </div>
            </div>

            <div className="p-4 bg-emerald-50 dark:bg-emerald-950/20 border border-emerald-100 dark:border-emerald-900/40 rounded-2xl flex gap-3">
              <CheckCircle2 className="w-5 h-5 text-emerald-600 dark:text-emerald-400 shrink-0 mt-0.5" />
              <div>
                <h6 className="font-bold text-emerald-900 dark:text-emerald-300 text-xs">Automated GitHub Compilation</h6>
                <p className="text-emerald-700 dark:text-emerald-400 text-[11px] mt-0.5 leading-relaxed">
                  The repository includes a preconfigured GitHub Actions continuous integration workflow. Push this codebase to GitHub, and the workflow will compile and publish the installable APK directly as a release artifact.
                </p>
              </div>
            </div>
          </div>
        </div>
      </section>

      {/* Clean Footer with absolutely no AI labels */}
      <footer className="bg-white dark:bg-slate-900 border-t border-slate-200 dark:border-slate-800 py-8 px-6 text-center text-xs text-slate-500 dark:text-slate-400 font-medium tracking-tight transition-colors duration-300">
        <div>&copy; 2026 Document Utility Tools. All Rights Reserved.</div>
        <div className="text-[10px] text-slate-400 dark:text-slate-500 mt-1">Made in offline developer workspace. Premium compilation available.</div>
      </footer>
    </div>
  );
}
