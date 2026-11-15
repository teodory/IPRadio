# Station availability in the edit list

## Intent

Make stations quick to disable and restore. Disabled stations stay below every enabled station; a restored station returns at the end of the enabled section.

## Design

- In edit mode, each station row has a direct enabled switch beside Edit and the drag handle. The station dialog edits station details, without an enabled switch.
- Toggling off moves that station to the end of the disabled section. Toggling on moves it just after the last enabled station. Persist both changes before updating the screen.
- The catalog keeps enabled stations before disabled stations, including when loading an older saved list. Dragging is limited to each station's section, so disabled stations cannot end up between enabled stations.
- All stations, including custom stations, can be disabled and restored. Outside edit mode, only enabled stations are listed and playable.
- A failed save leaves the prior state and order intact and shows an error.

## Approaches

- **Chosen:** Update availability and order together in the catalog. This makes the saved order, playback order, and edit list agree after restart.
- Sort only in the UI: smaller catalog change, but the saved order and the position after re-enabling can disagree with what the user saw.

## Scope

Use existing Compose controls and catalog persistence. Keep station details, playback controls, and settings behavior otherwise as they are.
