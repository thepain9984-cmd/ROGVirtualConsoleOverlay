# ROG Virtual Console Overlay (`com.custom.rogcontrols`)

A low-latency Floating Virtual Handheld Gamepad & Touch Keymapper Overlay inspired by the **ASUS ROG Ally console layout** and **BlueStacks Keymapper mechanics**.

The application overlays a customizable handheld controller UI (Dual Analog Thumbsticks, D-Pad, ABXY Action Buttons, and L1/R1 Triggers) over any Android game and translates virtual controller inputs into synthesized screen touch events and custom X/Y sensitivity-multiplied swipe gestures via Android's `AccessibilityService` gesture synthesis pipeline.

---

## 🎮 Key Architectural Highlights

### 1. Right Analog Thumbstick (BlueStacks-Style X/Y Sensitivity Engine)
- **Visual Aesthetic:** Custom `View` rendering an ASUS ROG Cyberpunk RGB outer neon ring with smooth 360-degree rotation animation and an inner movable metallic thumbstick knob.
- **Input Mathematics:**
  - Radial displacement from center $(C_x, C_y)$:
    $$\Delta x = Touch_x - C_x, \quad \Delta y = Touch_y - C_y$$
  - Polar coordinate clamping within maximum radius $R_{max}$:
    $$D = \sqrt{\Delta x^2 + \Delta y^2}$$
    $$\text{If } D > R_{max}: \quad \Delta x = \Delta x \times \frac{R_{max}}{D}, \quad \Delta y = \Delta y \times \frac{R_{max}}{D}$$
  - Independent **X-Axis Sensitivity ($S_x$)** and **Y-Axis Sensitivity ($S_y$, Vertical Drag Boost)** with deadzone filtering:
    $$Target_x = Anchor_x + (\Delta x \times S_x)$$
    $$Target_y = Anchor_y + (\Delta y \times S_y)$$
  - **Touch Synthesis:** Rapid continuous micro-swipes (`GestureDescription.StrokeDescription`) centered around a configurable **Aim Anchor Point** on the right side of the screen for camera look and recoil drag headshot acceleration.

### 2. Left Analog Thumbstick (Movement Stick)
- **Visual Aesthetic:** RGB neon ring on the bottom-left.
- **Touch Synthesis:** Maps 360-degree deflection to a configurable **Movement Anchor Zone** in the game HUD, synthesizing continuous movement drag strokes.

### 3. Action Buttons & Touch Keymapper (`A`, `B`, `X`, `Y`, `L1`, `R1`, `D-Pad`)
- **Paired Target Pointer Nodes:** Each virtual button has a linked `🎯` Target Pointer Node.
- **Edit Mode (`isEditMode == true`):**
  - Users can freely drag both the console buttons and their corresponding `🎯` target pointers anywhere on the screen.
  - A full-screen non-touchable Canvas overlay draws glowing dashed neon laser lines connecting each button to its target pointer.
- **Play Mode (`isEditMode == false`):**
  - Target pointers and connecting lines are hidden.
  - Pressing any button dispatches an immediate low-latency tap gesture at its paired Target Pointer's exact $(X, Y)$ screen coordinates.

### 4. Floating HUD Control Dock & Settings Panel
- **Top Pill Bar:**
  - `🔒 Play Mode` / `⚙️ Edit Mode` toggle
  - `⚡ Sensitivity` calibration modal:
    - Right Stick X-Axis Sensitivity (`0.5x` – `8.0x`)
    - Vertical Drag Boost Y-Axis Sensitivity (`0.5x` – `8.0x`)
    - Joystick Deadzone (`0%` – `30%`)
    - Overlay Opacity (`10%` – `100%`)
  - `💾 Save Profile` (persisted to `SharedPreferences` via JSON)
  - `🔄 Reset Layout` to ROG defaults

---

## 📁 Project Structure

```
ROGVirtualConsoleOverlay/
├── .github/
│   └── workflows/
│       └── build.yml               # CI/CD: JDK 17, Gradle 8.5, assembleDebug & artifact upload
├── app/
│   ├── build.gradle                # AGP 8.2.2, compileSdk 34, minSdk 26, ViewBinding, Gson
│   ├── proguard-rules.pro
│   └── src/
│       └── main/
│           ├── AndroidManifest.xml # SYSTEM_ALERT_WINDOW, FOREGROUND_SERVICE, AccessibilityService
│           ├── java/com/custom/rogcontrols/
│           │   ├── MainActivity.java     # Dark ROG dashboard with live permission monitors
│           │   ├── ConfigManager.java    # JSON serialization of coordinates & X/Y multipliers
│           │   ├── JoystickView.java     # Custom RGB analog stick view with polar math & sensitivity
│           │   └── OverlayService.java   # Core AccessibilityService & WindowManager overlay
│           └── res/
│               ├── drawable/       # Gamepad vector, triggers, D-pad, and button ripple drawables
│               ├── layout/         # activity_main.xml & activity_settings.xml
│               ├── values/         # ROG dark colors, dimensions, strings, Material3 themes
│               └── xml/
│                   └── accessibility_service_config.xml # canPerformGestures="true"
├── build.gradle                    # Top-level Gradle configuration
├── settings.gradle                 # Repository & module declarations
└── gradlew                         # Executable Gradle wrapper
```

---

## 🛠️ Build & CI/CD Verification

The project compiles with:
- **Android SDK:** CompileSDK 34, MinSDK 26, TargetSDK 34
- **Java Compatibility:** Java 17 (Temurin distribution recommended)
- **Gradle:** 8.5 with AGP 8.2.2

### Local Build:
```bash
./gradlew assembleDebug
```
The compiled APK will be generated at:
```
app/build/outputs/apk/debug/app-debug.apk
```

### GitHub Actions CI/CD:
On every `push` or `pull_request` to `main`, GitHub Actions automatically:
1. Checks out the repository using `actions/checkout@v4`.
2. Sets up Eclipse Temurin JDK 17 via `actions/setup-java@v4`.
3. Caches and runs Gradle via `gradle/actions/setup-gradle@v3`.
4. Executes `gradle assembleDebug --no-daemon --stacktrace`.
5. Uploads `app-debug.apk` as a workflow artifact available for download.
