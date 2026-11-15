# ⚡ Item Counter — Minecraft 26.x (Fabric)

[![Minecraft 26.2](https://img.shields.io/badge/Minecraft-26.x%20%7C%2026.2-brightgreen.svg)](https://fabricmc.net/)
[![Fabric](https://img.shields.io/badge/Loader-Fabric-blue.svg)](https://fabricmc.net/)
[![Cloth Config](https://img.shields.io/badge/Config-Cloth%20Config-orange.svg)](https://curseforge.com/minecraft/mc-mods/cloth-config)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)

**Item Counter** is a farm item tracker, flow rate monitor, and live HUD mod for Minecraft Fabric 26.x. Converts any standard in-game sign board into an active real-time farm item counter simply by writing `counter` on it.

---

## ✨ Features

- 🪵 **Instant Sign Activation**: Place any sign board (standing, wall, or hanging) along your farm stream and type `counter` on any line. It activates instantly!
- 📺 **Movable Live Watch Sidebar HUD**: Sleek on-screen display tracking items counted, flow rate per minute (`~X/m`), farm value earned, and countdown timers.
- 🌌 **3D In-World Floating Holograms**: Renders real-time floating 3D holographic statistics directly above your sign boards.
- 🧊 **Optimized Stream Detection**: Multi-layer continuous trajectory sweeping engineered specifically for high-velocity ice streams, packed ice chutes, and water streams.
- 💵 **Economy & Farm Valuation**: Assign custom dollar values per item to calculate real-time earnings and hourly farm profit (`$/hr`).
- 🌐 **Remote Stream Tracking**: Continues monitoring and displaying calibrated farm rates on your HUD even across chunk-loaded distances.
- 🔒 **Streamer Privacy**: Coordinate protection ensures farm coordinates are never leaked to chat.
- 🖱️ **Interactive Drag & Drop HUD**: Run `/counter hud` to reposition the live sidebar anywhere on your screen with real-time preview and boundary snapping.
- ⌨️ **Multi-Key Keybinds**: Configure custom single or combo shortcuts (e.g. `Ctrl + O`) via Cloth Config & ModMenu.

---

## 🚀 Quick Start Guide

1. Place any sign board directly in front of or above your farm's item flow.
2. Type **`counter`** on any line of the sign face.
3. The sign immediately illuminates into an active item counter with a floating 3D hologram!
4. **Right-Click** the sign to cycle modes:
   - `● [LIVE]` — Continuous, uninterrupted flow monitoring.
   - `⏳ [10s]` — 10-second flow calibration benchmark.
   - `⏳ [30s]` — 30-second benchmark.
   - `⏳ [60s]` — 60-second standard farm rate test.
5. **Shift + Right-Click** the sign to quickly reset the count to zero.

---

## 🎮 Commands

| Command | Description |
|---|---|
| `/counter` or `/itemcounter` | Opens the full in-game settings GUI |
| `/counter hud` or `/counter move` | Opens the interactive drag-and-drop HUD position editor |
| `/counter list` | Lists all active counters and flow rates in chat |
| `/counter reset` | Restarts the countdown of the nearest counter |
| `/counter clear` | Clears all registered counters |
| `/counter help` | Displays interactive clickable help menu |

---

## ⚙️ In-Game Configuration

Open the settings menu anytime with `/counter gui` or via **ModMenu**:
- **Sign Boards & Farms**: Rename individual counters, configure test durations, filter specific item types (e.g., `iron_ingot`), and set custom item prices.
- **3D Hologram**: Toggle floating text, adjust vertical offset height (-10 to +10 blocks), and tweak text scale.
- **Live Watch HUD**: Toggle sidebar display, adjust screen margins, and set your currency symbol (e.g. `$`, `€`, `£`, `Coins`).
- **Shortcuts**: Assign custom keybinds to open the menu or quickly reset the nearest counter.
