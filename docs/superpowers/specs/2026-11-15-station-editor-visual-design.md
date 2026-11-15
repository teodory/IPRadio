# Station editor visual clarity

## Intent

Make the existing Edit Radio and Add New Radio dialogs easier to scan visually, without adding explanatory copy or changing their fields and actions.

## Approaches

- **Chosen by the user: tonal panels.** Each text field has its own softly colored rounded container, a subtle edge, and space from its neighbors. Station volume occupies a matching container. This matches option Б in the approved visual mockup.
- Outlined fields would separate fields with stronger strokes but feel more rigid. The user chose the softer tonal treatment.

## Design

Keep Name, URL, Image URL, Info URL, Station volume, Cancel, and Save in the existing order. Replace the text fields' underline-only appearance with distinct rounded surfaces and remove their indicator lines. Group the volume label, value, and slider in one similarly styled surface. Use existing theme colors so dark and light modes both show clear boundaries. Preserve scrolling, editing, validation, and accessibility semantics.
