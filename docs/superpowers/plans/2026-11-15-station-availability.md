# Station Availability Implementation Plan

> **For agentic workers:** Use superpowers:executing-plans to implement these tasks. Steps use checkbox syntax for tracking.

**Goal:** Toggle each station from the edit list and keep disabled stations below active ones.

**Architecture:** `StationCatalog` owns the saved enabled state and section ordering. `RadioScreen` calls the catalog from a row switch. `StationEditor` handles station details only.

**Tech Stack:** Kotlin, Jetpack Compose, JUnit, Android instrumentation tests.

**Spec:** `docs/superpowers/specs/2026-11-15-station-availability-design.md`

## Global Constraints

- Preserve persisted station details and playback order.
- Keep enabled stations before disabled stations after toggle, drag, and reload.
- Use the running writable emulator for UI verification.

## Review Focus

- Existing saved catalogs with disabled stations in the middle are displayed in sections.
- Toggling fails without changing memory or disk when writing fails.
- Custom stations can be toggled.
- Dragging cannot put a disabled station among enabled stations.
- Re-enabled stations land after the last enabled one.

### Task 1: Catalog ordering

**Files:** `app/src/main/java/com/example/ipradio/StationCatalog.kt`, `app/src/test/java/com/example/ipradio/StationCatalogTest.kt`

**Interface:** `suspend fun setEnabled(id: String, enabled: Boolean): Result<Boolean>`.

- [x] Add failing unit tests for toggling a built-in and a custom station, restoring at the enabled boundary, reload, failed writes, old order normalization, and section-limited moves.
- [x] Run `ANDROID_HOME=$HOME/Library/Android/sdk ./gradlew :app:testDebugUnitTest --console=plain` and verify the new tests fail for the missing behavior.
- [x] Implement atomic toggle and catalog section ordering. Keep `save` compatible with existing callers.
- [x] Rerun unit tests and verify they pass.

### Task 2: Direct row control

**Files:** `app/src/main/java/com/example/ipradio/RadioScreen.kt`, `app/src/main/java/com/example/ipradio/MainActivity.kt`, `app/src/main/res/values/strings.xml`, `app/src/main/res/values-bg/strings.xml`, `app/src/androidTest/java/com/example/ipradio/RadioEditorTest.kt`

**Interface:** `RadioScreen` accepts `setStationEnabled: suspend (String, Boolean) -> Result<Boolean>`.

- [x] Change the existing UI test to toggle from the row and check that the dialog has no availability switch; verify the test fails first.
- [x] Add the row switch, connect it to the catalog, and show save failures. Restrict drag preview and accessible move actions to the station's section.
- [x] Build and run the unit tests plus `RadioEditorTest` on the existing emulator; verify all pass.
