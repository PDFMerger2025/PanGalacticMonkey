# 👽🐒 PanGalactic Monkey
**A cosmic web browser built for Fire TV & Android TV**

PanGalactic Monkey is a full-featured, remote-control-friendly web browser 
designed from the ground up for Amazon Fire TV and Android TV devices.

## ✨ Features

### 🖱️ Dual Navigation Modes
- Cursor Mode: on-screen cursor steered with the D-pad
- Tab Navigation Mode: cursor-free, highlights clickable elements one at a time
- Toggle via the top bar button or remote Play/Pause

### 🗂️ Multiple Tabs
- Unlimited tabs, each independent
- Tabs (n) button shows switcher: switch, close (✕), or open new tabs

### 🔖 Bookmarks — star icon toggles instantly, full manager in menu
### 🕘 History — last 20 pages, revisit/delete/clear all
### 🕵️ Private Browsing — no cookies/history, auto-wiped on app close
### ⬇️ Download Manager — confirms before downloading, shows full file 
   path, Install/Open + Delete + Done buttons, custom save location
### 📸 Long-Press Save — hold OK ~0.5s on any image/video/audio/pdf/etc 
   to save it, works on direct file links too
### 🔍 Zoom In/Out — from the menu
### 🧩 Userscripts — install from .js links, manage anytime
### 🏠 Homepage — quick-jump button + customizable in menu

## 🎮 Remote Control Guide
| Button | Action |
|---|---|
| D-Pad | Move cursor / navigate links |
| OK (tap) | Click |
| OK (hold ~0.5s) | "Right-click" — save media |
| Back (tap) | Go back / exit (press twice to confirm exit) |
| Back (hold) | Jump to URL bar |
| Play/Pause | Toggle Cursor vs Tab Nav mode |
| Rewind / Fast Forward | Page back / forward |
| Menu | Open hamburger menu |

## 🧭 Top Bar
⌂ Home · URL bar · ★ Bookmark · ◀▶ Back/Forward · ↻ Reload · 
Scripts · Cursor ON/OFF · Tabs (n) · ☰ Menu

## ☰ Menu
View Bookmarks · Add Current Page · Change Homepage · Downloads · 
Change Download Location · History · Private Browsing · Zoom In/Out · 
Cursor & Scroll Speed

## ⚙️ Technical Notes
- Native Android WebView core, per-tab isolated instances
- Downloads via system DownloadManager, saved to public storage
- FileProvider-based secure file sharing for installs/opens

## 📦 Package Info
- Package: com.example.tvbrowser
- Min Android: 5.0 (API 21)
- Platform: Fire TV / Android TV (Leanback supported)

*Made for exploring the galaxy, one webpage at a time.* 🚀🐒
