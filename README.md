An open-source, background system utility for Android that lets you silently capture, crop, adjust, and share any section of your screen. Inspired by Samsung’s Galaxy AI Select, this lightweight tool replaces invasive full-screen touch gestures with a subtle side trigger bar and volume hardware shortcuts.
Features
 * Left Side Trigger Bar: A translucent, low-profile sidebar docked on the left screen edge. Swipe inward from any app to trigger an instant selection overlay.
 * Volume Key Shortcut: Press and hold Volume Down for 700ms to launch screen selection without touching the screen.
 * Interactive Crop Box: Drag to create a selection box, fine-tune using corner and edge resize handles, or drag inside the box to reposition it.
 * Reselect & Reset: Tap outside the box to clear your selection and start drawing a new one instantly.
 * Direct Share Sheet Integration: Tap the floating Share button beneath your selection to crop the bitmap and launch the native Android Share sheet (ACTION_SEND).
 * Zero Input Blocking: Unlike full-screen gesture overlays, the side bar uses precise window bounds so 100% of your screen interactions, typing, and gaming remain unaffected.
 * 100% On-Device & Private: Screen captures are processed entirely on-device with zero network calls or data tracking.
Technical Highlights
 * Silent Screen Capture: Utilizes Android 11+ (API 30+) AccessibilityService.takeScreenshot() to capture display contents without repetitive MediaProjection runtime prompts.
 * Minimal Touch Footprint: Uses WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY pinned tightly to the left edge of the screen, ensuring underlying apps receive uninterrupted touch inputs.
 * Hardware Key Interception: Implements onKeyEvent() filtering in AccessibilityService to handle long-press detection on KEYCODE_VOLUME_DOWN.
 * Dynamic Bitmaps & File Sharing: Processes raw hardware buffers, renders selection overlays, and exposes cropped PNGs safely via FileProvider (content:// URIs).
Architecture Overview
├── app/src/main/
    ├── java/com/example/screenselect/
    │   ├── KeyCaptureService.kt      # Core Accessibility Service & Volume listener
    │   ├── SidebarOverlayManager.kt  # Left side trigger bar overlay logic
    │   ├── CropOverlayActivity.kt    # Interactive crop, resize & share UI
    │   └── MainActivity.kt           # Permissions onboarding screen
    └── res/
        └── xml/
            ├── accessibility_service_config.xml
            └── file_paths.xml

Requirements
 * Android Version: Android 11 (API level 30) or higher.
 * Permissions Required:
   * Accessibility Service: Enables silent screenshot capture and hardware key monitoring.
   * Display Over Other Apps (SYSTEM_ALERT_WINDOW): Allows rendering the side trigger bar and translucent crop layer.
   * Battery Optimization Exemption: Keeps the service alive in the background.
Setup & Usage
 * Download the latest APK from the Releases tab or build the project directly in Android Studio.
 * Launch the app and tap Enable Accessibility Service.
 * Grant Display over other apps and exclude the app from Battery Optimization.
 * Swipe inward from the left edge trigger bar (or hold Volume Down) on any screen to capture, crop, and share!
License
Distributed under the MIT License. See LICENSE for more information.
