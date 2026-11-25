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

Drag the grip (⋮⋮) at the start of the toolbar to move it anywhere on the screen. Its position is remembered.

## Slides (pages)

The ◀ 1 ▶ buttons in the toolbar switch between slide pages. Every page has its own drawings,
texts and undo history. ▶ on the last page opens an empty page; ◀ brings back the previous
page's annotations. Pages are kept in memory until the app is closed. The app cannot see which
slide PowerPoint shows, so pages are switched with these buttons (e.g. turn the slide in
pass-through mode, then tap ▶).

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
