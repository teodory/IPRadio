# Station Editor Visual Clarity Implementation Plan

> **For agentic workers:** Use superpowers:executing-plans to implement this plan. Steps use checkbox syntax for tracking.

**Goal:** Make each station editor control visually distinct without changing behavior or copy.

**Architecture:** Adjust only the Compose `StationEditor` layout and field colors in `MainActivity.kt`. Reuse Material 3 text fields and the existing color scheme.

**Tech Stack:** Kotlin, Jetpack Compose Material 3, Android instrumentation tests.

**Spec:** `docs/superpowers/specs/2026-11-15-station-editor-visual-design.md`

## Global Constraints

- Preserve current fields, labels, actions, validation, and scroll behavior.
- Keep dark and light appearances legible.
- No new dependency.

## Review Focus

- Long URLs remain editable and readable within a single field.
- Empty Info URL still has an obvious field boundary.
- Volume slider remains reachable on small screens.
- Both dark and light theme panels separate from the dialog.

### Task 1: Style the station editor

**Files:** `app/src/main/java/com/example/ipradio/MainActivity.kt`

- [x] Replace underline-only text field presentation with rounded tonal fields, subtle borders, and spacing.
- [x] Wrap Station volume in a matching tonal panel, retaining its semantics and slider range.
- [x] Build and run existing tests for station editing.
- [x] Inspect the resulting dialog in dark and light theme on the running emulator.
