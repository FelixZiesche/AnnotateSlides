# Annotate Slides

Annotate Slides is a fork of [DrawAnywhere](https://github.com/DrawAnywhere/DrawAnywhere) by shezik.
It adds a **text tool** so you can type on top of any app (PowerPoint, Google Slides, a browser)
with a Bluetooth keyboard or the on-screen keyboard, for example while presenting from a tablet
through a mirrored projector.

## Text tool

- **T** button in the toolbar switches to the text tool (press again to go back to the pen).
- Tap the canvas to create a text box and start typing. Enter starts a new line, **Esc**, Back,
  the ✓ button or a tap next to the box finishes editing.
- Tap an existing text to edit it, drag it to move it, long-press it (or use the trash button
  while editing) to delete it.
- Font size (default 28 sp), opacity and an optional semi-transparent white background are in the
  tool controls; colour comes from the colour picker.
- Text works with undo/redo and "clear canvas", and stays when the canvas is hidden or switched to
  pass-through (for turning slides).

## Toolbar

From left to right: grip (⋮⋮, drag to move the toolbar anywhere), show/hide canvas, slide page,
then **T** (text), tools, colour, undo, clear, pass-through. The arrow opens the "more" drawer with
redo, zoom lock and settings. Active modes (text tool, pass-through) are highlighted.

## Slides (pages)

Every slide gets its own annotation page: drawings, texts and undo history. Pages switch
automatically when you turn the slide with a Bluetooth keyboard or presenter clicker
(→ ↓ Page Down = next, ← ↑ Page Up = previous); the keys still reach PowerPoint unchanged.
Going back restores the earlier page. Pages are kept in memory until the app is closed.
The number in the toolbar shows the current page.

This uses an accessibility service that only observes these keys (no screen content is read).
While it is not enabled, the toolbar shows a red "!" — tap it to open the accessibility settings.
Swipes on the screen are not detected; PowerPoint animation steps on the same keys also switch pages.

### Enabling the accessibility service on Xiaomi HyperOS

The red "!" in the toolbar opens these steps with shortcut buttons.

1. Settings → Additional settings → Accessibility → Downloaded apps → Annotate Slides → try to switch it on
   (Android blocks it once — this step is needed so the next option appears).
2. Open the app's **App info** page (long-press the app icon → ⓘ, or the "Open app info" button in the
   "!" popup) → ⋮ (top right) → "Allow restricted settings". Wait for the countdown and confirm.
   On HyperOS the Xiaomi "Manage apps" page may not show this menu; use the standard App info page.
3. Repeat step 1.

If step 2 is impossible, use a computer with ADB (USB debugging on):

```
adb shell appops set de.ziesche.annotateslides ACCESS_RESTRICTED_SETTINGS allow
```

then repeat step 1.

The overlay window is only focusable while a text box is being edited, so outside of typing all
touches and keys go to the app below as before.

## Install / updates

Signed APKs are published on the [Releases](https://github.com/FelixZiesche/AnnotateSlides/releases)
page for every tag `vX.Y.Z`. To make a new release, either push such a tag or open
Actions → Release → "Run workflow" and enter the new version (e.g. `v1.0.1`); the
versionCode is derived from it (`v1.2.3` → 10203), so each higher version updates cleanly. Add `https://github.com/FelixZiesche/AnnotateSlides` to
[Obtainium](https://github.com/ImranR98/Obtainium) to get updates. The app id is
`de.ziesche.annotateslides`, so it installs next to the original DrawAnywhere.

## License

GNU Affero General Public License v3.0 or later, same as the original. See [LICENSE](LICENSE).
Original work © 2025–2026 shezik; text tool and modifications © 2026 FelixZiesche.
