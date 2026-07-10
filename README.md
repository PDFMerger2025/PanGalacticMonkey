<div align="center">
<img src="https://iili.io/ClKGwts.png" width="50%"/>
<br/>

![Platform](https://img.shields.io/badge/Platform-Fire%20TV%20%7C%20Android%20TV-6a0dad?style=for-the-badge&logo=androidtv&logoColor=white)
![Min SDK](https://img.shields.io/badge/Min%20Android-5.0%20(API%2021)-00d4ff?style=for-the-badge&logo=android&logoColor=white)
![Core](https://img.shields.io/badge/Core-WebView%20%7C%20GeckoView-1a1a2e?style=for-the-badge&logo=googlechrome&logoColor=white)
</div>

<img src="https://iili.io/C0Dfvgj.jpg" width="90%"/>

#  PanGalactic Monkey 👽🐵
A remote‑friendly, full‑featured web browser built specifically for **Amazon Fire TV** and **Android TV**. No mouse. No keyboard. Just a remote — and the whole internet.

---

# *Introducing Slim & Chunky!*

The PanGalactic Monkey browser family now comes in **two editions**, each designed for different needs while sharing the same core experience.

## 🐒 PanGalactic Monkey Slim (WebView Edition — ~3MB)
The original lightweight browser — now officially named **Slim**.

- Built on **Android WebView**
- Ultra‑tiny footprint (~3MB)
- Fast, simple, minimal
- Universal compatibility across all Android devices
- Ideal for users who want speed and minimal storage usage

## 🦍 PanGalactic Monkey Chunky (GeckoView Edition — ~90MB)
The new heavyweight version — **Chunky** — powered by **GeckoView**.

- Full **Firefox extension (.xpi)** support  
  - uBlock Origin  
  - Tampermonkey  
  - Any compatible Firefox extension  
- More powerful rendering engine  
- Better standards support  
- Same familiar Monkey interface  
- Requires correct 32‑bit or 64‑bit build (download both if unsure)

### Missing (for now):
- Cursor speed control
- Zoom In/Out 
- Built‑in adblock + userscript engines + user agent switcher + Javascript toggle (extensions replace these)

---

# ✨ Unified Core Features (Slim + Chunky)

PanGalactic Monkey provides a complete TV‑optimized browsing experience across both Slim (WebView) and Chunky (GeckoView). All new features and improvements are fully merged below.

## 🖱️ Dual Navigation Modes
- **Cursor Mode** — on‑screen cursor controlled with the D‑pad  
- **Tab Navigation Mode** — highlight‑based navigation without a cursor  
- Instant toggle via **Play/Pause** or the top bar  
- Smooth GPU‑accelerated cursor movement with natural velocity ramping  
- Continuous movement system using `Choreographer.FrameCallback`  
- **Slim:** Includes cursor speed control (+1 fine‑tune, +5 via Fire remote)  
- **Chunky:** Cursor speed control coming later  

## 🗂️ Multiple Tabs
- Unlimited independent tabs  
- Tab switcher with close, switch, and new tab options  
- Tabs respect dynamic JavaScript settings  

## 🔖 Bookmarks
- One‑tap star icon  
- Full bookmark manager  
- Add from menu or top bar  

## 🕘 History
- Tracks your last 20 pages  
- Delete individual entries or clear all  

## 🕵️ Private Browsing
- No cookies  
- No history  
- Auto‑clears on exit  

## ⬇️ Download Manager
- Confirm downloads before saving  
- Shows full file path  
- Install / Open / Delete actions  
- Custom download location support  
- Works identically in Slim & Chunky  

## 📸 Long‑Press Save
- Hold **OK** for ~0.5s  
- Save images, videos, audio, PDFs, and direct file links  

## 🔍 Zoom In / Out (Slim Only)
- Quick access from the menu  

## 🏠 Custom Homepage
- One‑tap home button  
- Fully configurable  

## ⚙️ JavaScript Toggle (Slim Only)
- JavaScript ON/OFF switch with persistent storage  
- Loads JS preference before first tab opens  
- Tabs dynamically respect JS state  
- New menu item: **JavaScript: ON/OFF**  

## 🖥️ Request Desktop Site (Slim Only)
- Toggle desktop user‑agent mode  
- Uses full desktop UA:  
  `Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36`  
- Improves compatibility with desktop‑optimized websites  

## 🛡️ Ad‑Block Support (Slim Only)
- Enabled by default  
- Uses **EasyList**  
- Supports custom filter lists  
- Multiple presets including “annoyances”  
- Long‑press hyperlinks to copy filterlist URLs or `.txt` list URLs  

## ✔️ Whitelist Support (Slim Only)
- Per‑site ad whitelisting  
- Toggle ads on/off for individual domains  

## 📜 Userscript Support
### Slim (Built‑in)
- Install from `.js` links  
- Install from device  
- Install from URL  
- Full userscript manager  

### Chunky (Extension‑based)
- Use **Tampermonkey (.xpi)**  
- No built‑in engine (by design) 

## 🧩 Extension Support (Chunky Only)
- Works with any compatible Firefox extension  
- Install from `.xpi` links 
- Install from URL 
- Full extensions manager   
- Replaces built‑in adblock/userscript/user agent engines  

---

# 🆚 Slim vs Chunky — Comparison Table

| Feature | **Slim (WebView)** | **Chunky (GeckoView)** |
|--------|---------------------|-------------------------|
| Engine | WebView | GeckoView |
| Size | ~3MB | ~90MB |
| Extensions | ❌ None | ✅ Full Firefox (.xpi) |
| Ad‑Block | ✔️ Built‑in (EasyList) | ❌ Use uBlock Origin extension |
| Userscripts | ✔️ Built‑in | ❌ Use Standalone extension |
| Cursor Speed Control | ✔️ Yes | ❌ Not yet |
| Zoom In/Out | ✔️ Yes | ❌ Not yet |
| Javacript Toggle | ✔️ Yes | ❌ Not yet |
| Request Desktop Site | ✔️ Built‑in | ❌ Use Standalone extension |
| Performance | Fast & lightweight | Heavy but powerful |
| Compatibility | Universal | Requires correct 32/64‑bit build |
| Ideal For | Minimalist users | Power users / extension lovers |

---

# 🎮 Remote Control Guide

<div align="center">

| Button | Action |
|:---:|:---|
| 🕹️ **D‑Pad** | Move cursor / navigate links |
| ✅ **OK (tap)** | Click |
| ✅ **OK (hold)** | Save media |
| ◀️ **Back (tap)** | Go back / exit |
| ◀️ **Back (hold)** | Jump to URL bar |
| ⏯️ **Play/Pause** | Toggle Cursor vs Tab Nav |
| ⏪⏩ **Rewind / Fast Forward** | Page back / forward |
| ☰ **Menu** | Open menu |

</div>

---

# 🧭 Top Bar

<div align="center">

`⌂ Home` · `URL Bar` · `★ Bookmark` · `◀ ▶ Back/Forward` · `↻ Reload` · `Scripts (Slim Only)` · `Cursor ON/OFF` · `Tabs (n)` · `☰ Menu`

</div>

---

# ☰ Menu Options

<div align="center">

`View Bookmarks` · `Add Current Page` · `Change Homepage` · `Downloads` · `Change Download Location` · `History` · `Private Browsing` · `Zoom In/Out (Slim Only)` · `Cursor & Scroll Speed (Slim Only)` · `JavaScript ON/OFF (Slim Only)` `Ad Block Filters (Slim Only)` · `Whitelist Current Site (Slim Only)` · `Manage Whitelist (Slim Only)` · `Extensions (Chunky Only)` ·

</div>

---

# ⚙️ Technical Notes

<details>
<summary><b>Click to expand engineering details 🔧</b></summary>
<br/>

### Slim (WebView)
- Native Android WebView with isolated per‑tab instances  

### Chunky (GeckoView)
- Full GeckoView integration  
- Extension manager for `.xpi` installs  

</details>

---

# 📦 Package Info

<div align="center">

| Key | Value |
|:---|:---|
| **Package** | `com.example.tvbrowser` |
| **Min Android** | 5.0 (API 21) |
| **Platform** | Fire TV / Android TV |

</div>

---

<div align="center">

### *Made for exploring the galaxy, one webpage at a time.*

<img src="https://capsule-render.vercel.app/api?type=waving&color=0:00d4ff,50:6a0dad,100:1a1a2e&height=120&section=footer" width="100%"/>

