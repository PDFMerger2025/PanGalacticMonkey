<div align="center">
<img src="https://iili.io/ClKGwts.png" width="50%"/>

<br/>

![Platform](https://img.shields.io/badge/Platform-Fire%20TV%20%7C%20Android%20TV-6a0dad?style=for-the-badge&logo=androidtv&logoColor=white)
![Min SDK](https://img.shields.io/badge/Min%20Android-5.0%20(API%2021)-00d4ff?style=for-the-badge&logo=android&logoColor=white)
![Core](https://img.shields.io/badge/Core-Android%20WebView-1a1a2e?style=for-the-badge&logo=googlechrome&logoColor=white)
</div>
<br/>

## 📡 Table of Contents

<div align="center">

| [✨ Features](#-features) | [🎮 Remote Guide](#-remote-control-guide) | [🧭 Top Bar](#-top-bar) | [☰ Menu](#-menu) | [⚙️ Tech](#️-technical-notes) | [📦 Package](#-package-info) |
|:---:|:---:|:---:|:---:|:---:|:---:|

</div>

<br/>

> PanGalactic Monkey is a full-featured, remote-control-friendly web browser designed from the ground up for **Amazon Fire TV** and **Android TV** devices. No mouse. No keyboard. Just a remote — and the whole internet.

<br/>

---

## ✨ Features

<br/>

<table>
<tr>
<td width="50%" valign="top">

### 🖱️ Dual Navigation Modes
- **Cursor Mode** — on-screen cursor steered with the D-pad
- **Tab Navigation Mode** — cursor-free, highlights clickable elements one at a time
- Toggle instantly via the top bar button or remote `Play/Pause`

</td>
<td width="50%" valign="top">

### 🗂️ Multiple Tabs
- Unlimited tabs, each fully independent
- **Tabs (n)** button opens the switcher
- Switch, close (`✕`), or spin up new tabs on the fly

</td>
</tr>

<tr>
<td width="50%" valign="top">

### 🔖 Bookmarks
- Star icon toggles instantly from any page
- Full bookmark manager tucked in the menu

</td>
<td width="50%" valign="top">

### 🕘 History
- Tracks your last 20 pages
- Revisit, delete individually, or clear all

</td>
</tr>

<tr>
<td width="50%" valign="top">

### 🕵️ Private Browsing
- Zero cookies, zero history
- Auto-wiped the moment the app closes

</td>
<td width="50%" valign="top">

### ⬇️ Download Manager
- Confirms before every download
- Shows full file path
- `Install` / `Open` + `Delete` + `Done` actions
- Custom save location support

</td>
</tr>

<tr>
<td width="50%" valign="top">

### 📸 Long-Press Save
- Hold `OK` for ~0.5s on any image, video, audio, PDF, etc.
- Works on direct file links too

</td>
<td width="50%" valign="top">

### 🔍 Zoom In / Out
- Quick access straight from the menu

</td>
</tr>

<tr>
<td width="50%" valign="top">

### 🧩 Userscripts
- Install directly from `.js` links
- Manage them anytime, anywhere

</td>
<td width="50%" valign="top">

### 🏠 Homepage
- One-tap quick-jump button
- Fully customizable in the menu

</td>
</tr>
</table>

<br/>

---

## 🎮 Remote Control Guide

<div align="center">

| Button | Action |
|:---:|:---|
| 🕹️ **D-Pad** | Move cursor / navigate links |
| ✅ **OK** *(tap)* | Click |
| ✅ **OK** *(hold ~0.5s)* | "Right-click" — save media |
| ◀️ **Back** *(tap)* | Go back / exit (press twice to confirm exit) |
| ◀️ **Back** *(hold)* | Jump to URL bar |
| ⏯️ **Play/Pause** | Toggle Cursor vs Tab Nav mode |
| ⏪⏩ **Rewind / Fast Forward** | Page back / forward |
| ☰ **Menu** | Open hamburger menu |

</div>

<br/>

---

## 🧭 Top Bar

<div align="center">

`⌂ Home`  ·  `URL bar`  ·  `★ Bookmark`  ·  `◀ ▶ Back/Forward`  ·  `↻ Reload`  ·  `Scripts`  ·  `Cursor ON/OFF`  ·  `Tabs (n)`  ·  `☰ Menu`

</div>

<br/>

---

## ☰ Menu

<div align="center">

`View Bookmarks` · `Add Current Page` · `Change Homepage` · `Downloads` · `Change Download Location` · `History` · `Private Browsing` · `Zoom In/Out` · `Cursor & Scroll Speed`

</div>

<br/>

---

## ⚙️ Technical Notes

<details>
<summary><b>Click to expand engineering details 🔧</b></summary>
<br/>

- Native **Android WebView** core, with per-tab isolated instances
- Downloads handled via the system `DownloadManager`, saved to public storage
- `FileProvider`-based secure file sharing for installs and opens

</details>

<br/>

---

## 📦 Package Info

<div align="center">

| Key | Value |
|:---|:---|
| **Package** | `com.example.tvbrowser` |
| **Min Android** | 5.0 (API 21) |
| **Platform** | Fire TV / Android TV (Leanback supported) |

</div>

<br/>

---

<div align="center">

### *Made for exploring the galaxy, one webpage at a time.* 

<img src="https://capsule-render.vercel.app/api?type=waving&color=0:00d4ff,50:6a0dad,100:1a1a2e&height=120&section=footer" width="100%"/>

</div>
