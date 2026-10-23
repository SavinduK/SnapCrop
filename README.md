# ✂️ SnapCrop

**Instant Screen Capture & AI for Android**

SnapCrop lets you capture any part of your screen in one tap, crop it, copy the text, take long scrolling screenshots, and send the image to your favourite AI app (Gemini, Google Search/Lens, ChatGPT, or Claude).

---

## 📑 Table of Contents

- [✨ Features](#-features)
- [📱 Requirements](#-requirements)
- [📥 Installation](#-installation)
- [♿ Enable the Accessibility Service](#-enable-the-accessibility-service)
- [🔐 Other Permissions](#-other-permissions)
- [🚀 How to Use](#-how-to-use)
- [🤖 Choosing an AI Model](#-choosing-an-ai-model)
- [🛠️ Troubleshooting](#️-troubleshooting)
- [🔒 Privacy](#-privacy)
- [🤝 Contributing](#-contributing)
- [📄 License](#-license)

---

## ✨ Features

| Feature | What it does |
|---|---|
| 📏 **Edge Panel** | A small bar on the edge of your screen. Tap or swipe it to start a capture. |
| ⚡ **3x Power Button Tap** | Press the power button 3 times to capture instantly. |
| 📜 **Long Screenshot** | Scroll and stitch full web pages, long chats, or documents into one image. |
| 🔤 **Select Text (OCR)** | Recognise text from your selection, copy it, or search it on Google. |
| 📐 **Math Solver** | Identify math equations, convert to LaTeX, and calculate step-by-step solutions. |
| ✨ **Ask AI** | Explain, summarise, translate, or solve anything you crop with one tap. |
| 📤 **Quick Share** | Send the cropped image to any app with an instant preview. |
| 📋 **Copy & Save** | Copy to clipboard or save to your photo gallery. |
| 🗑️ **Saved Clips** | Recent clips are kept for 24 hours, then deleted automatically. |

---

## 📱 Requirements

- 📲 Android phone
- 🔓 Permission to install apps from outside the Play Store (if you install the APK)
- ♿ Accessibility Service access (explained below)

---

## 📥 Installation

### Option 1: Install the APK (easiest) 📦

1. Go to the [**Releases**](../../releases) page of this repository.
2. Download the latest `SnapCrop.apk`.
3. Open the file on your phone.
4. If asked, allow **"Install from this source"** for your browser or file manager.
5. Tap **Install**, then **Open**.

### Option 2: Build it yourself 🧑‍💻

```bash
# 1. Clone the project
git clone https://github.com/<your-username>/SnapCrop.git

# 2. Go into the folder
cd SnapCrop

# 3. Build and install on a connected phone (Android Studio / Gradle)
./gradlew installDebug
```

> 💡 Replace `<your-username>` with the real GitHub username. If the project uses a different build tool, follow its usual build steps instead.

---

## ♿ Enable the Accessibility Service

SnapCrop needs the **Accessibility Service** so that the edge bar, the 3x power button trigger, and long screenshots (auto-scrolling) can work. Without it, these features will not run.

### Steps 👣

1. Open the **SnapCrop** app.
2. Tap the permission prompt (or open your phone's **Settings** manually).
3. Go to **Settings → Accessibility**.
4. Find **SnapCrop**:
   - It may be under **Installed apps**, **Downloaded apps**, or **Installed services**.
5. Tap **SnapCrop** and switch it **ON** 🟢.
6. Confirm by tapping **Allow** / **OK** on the warning pop-up.
7. Return to SnapCrop. The status at the top should show **Ready** ✅.

### ⚠️ If the switch is greyed out ("Restricted setting")

On Android 13 and newer, apps installed from an APK may be blocked from using Accessibility at first. To fix this:

1. Long-press the SnapCrop icon → **App info** (ⓘ).
2. Tap the **⋮** menu (top right).
3. Tap **Allow restricted settings**.
4. Go back to **Settings → Accessibility** and turn SnapCrop on.

### Why does SnapCrop need this? 🤔

- 👆 To detect the edge panel taps and the power button triple-press.
- 📜 To scroll the screen automatically for long screenshots.

SnapCrop does **not** use it to read your passwords or personal messages.

---

## 🔐 Other Permissions

| Permission | Why it's needed |
|---|---|
| 🎥 **Screen Capture** | Lets the floating edge bar take screenshots. |
| 🪟 **Display over other apps** | Shows the edge bar and crop tools on top of other apps. |
| 🔋 **Keep Active in Background** | Stops Android from closing SnapCrop when the phone sleeps. Tap **Enable** on the main screen. |

> 💡 On Xiaomi, Oppo, Vivo, and similar phones, also turn on **Autostart** and set battery usage to **No restrictions** for SnapCrop.

---

## 🚀 How to Use

1. **Open SnapCrop** and make sure it says **Ready** ✅.
2. **Pick a trigger** 👇
   - 🔹 **Edge Panel**: tap or swipe the thin bar on the edge of your screen.
   - 🔹 **3 Times Tap Power Button**: press the power button 3 times quickly.
3. **Drag to crop** the area you want.
4. **Choose an action** from the crop dock:
   - 📜 Long screenshot
   - 🔤 Select text (OCR)
   - ✨ Ask AI
   - 📤 Share
   - 📋 Copy or 💾 Save

### 📜 Taking a Long Screenshot

1. Turn on **Long Screenshot** in the **Crop Modes & Actions** section.
2. Open the page, chat, or article you want to capture.
3. Trigger SnapCrop and tap the long screenshot option.
4. Scroll page-by-page or finish to stitch the full document into a seamless image.

### 📐 Solving Math Equations

1. Enable **Math Equation Solver** in the **Crop Modes & Actions** section.
2. Trigger SnapCrop and crop any equation or arithmetic expression on your screen.
3. Tap the **Math (∑ / fx)** icon on the crop toolbar.
4. SnapCrop recognizes the formula with Google ML Kit, renders the **LaTeX formula**, and calculates step-by-step solutions and the final answer.
5. Tap **Copy**, **Copy LaTeX**, or **Share** to export the solution.

---

## 🤖 Choosing an AI Model

Under **AI Model to Share**, pick where your cropped image is sent:

- 🌟 **Google Gemini** (default)
- 🔍 **Google Search** (Lens)
- 💬 **ChatGPT**
- 🧡 **Claude**

> ℹ️ The AI app must be installed on your phone. Apps that are installed show an **Installed** tag.

---

## 🛠️ Troubleshooting

| Problem | Fix |
|---|---|
| Edge bar does not appear | Check Accessibility is ON and "Display over other apps" is allowed. |
| Power button trigger does not work | Make sure the Accessibility Service is enabled and the trigger is selected. |
| Long screenshot fails | Turn on **Long Screenshot** in settings and keep the screen unlocked while it scrolls. |
| App stops after a while | Tap **Enable** under **Keep Active in Background** and disable battery optimisation. |
| Accessibility switch is greyed out | Use **Allow restricted settings** (see above). |

---

## 🔒 Privacy

- 🗂️ Saved clips are kept for **24 hours** and then **auto-deleted**.
- 📤 Images are only sent to an AI app when **you** choose to share them.
- 🚫 No hidden screenshots are uploaded.

---

## 🤝 Contributing

Contributions are welcome! 🎉

1. 🍴 Fork the repository
2. 🌿 Create a branch: `git checkout -b feature/my-feature`
3. 💾 Commit your changes: `git commit -m "Add my feature"`
4. 🚀 Push the branch: `git push origin feature/my-feature`
5. 🔃 Open a Pull Request

---

## 📄 License

This project is licensed under the **MIT License**. See the [LICENSE](LICENSE) file for details.

---

⭐ If you like SnapCrop, give the repo a star!
