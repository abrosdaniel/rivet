# Integration adapters

Optional integrations with other mods must activate automatically when the mod is detected and its API is supported. Do not add opt-in switches or require manual activation unless the user explicitly requests an exception. Keep absent or incompatible APIs isolated within their adapters.

# Release versions

Use MAJOR.MINOR.PATCH based on user impact: PATCH for fixes, optimization, tests and documentation; MINOR for new features/settings while preserving existing configuration and data; MAJOR for incompatible changes requiring owners to manually change configuration, data or their pack. Validate wire protocols and negotiated features separately; release numbers alone do not establish compatibility. Prefer explicit bounded requirements (>=1.2.0 <2.0.0) or exact requirements (=1.2.0) in new seeds. Preserve legacy bare-release requirements as minimum releases within their major.

# Server configuration updates

On upgrades, add new server settings and their explanatory comments to the existing config file automatically. Preserve the owner's values, comments and credentials; validate before writing and keep a backup. Owners must be able to configure every new server option in the file, rather than rely on invisible defaults.

# Server configuration naming

Use concise names within their section (for example display.nameplates, chat.coordinates, database.database). Avoid redundant Enabled or allow prefixes. Keep units explicit for numeric durations and sizes. Update all readers, the template and the server guide together.

# Visual regression coverage

Run complete UI flows in the default theme at three GUI scales. Cover light and high-contrast profiles separately; sample every other theme on representative screens at GUI 3 instead of multiplying every scenario by every theme. Keep palette contrast checks for all themes. Each suite reports successful scenario completion. CI renders and checks the flows with RIVET_UI_SCREENSHOTS=false, verifies the completion marker, and retains diagnostic logs on failure. PNG capture and exact frame-count validation are local opt-in review tools, not CI requirements. Use a fixed 1280×1024 client viewport for full-flow tests; adaptive scenarios resize it explicitly.

# UI design system

Use the shared Rivet UI Kit documented in UI_KIT.md for every new or changed screen.
Reuse semantic colours, spacing, actions, fields, navigation, states and workspace slots.
Do not introduce page-local bevels, ornament textures or custom button/field renderers.
Keep native keyboard editing, focus, narration and user preferences. Add new reusable
components and their states to the live catalogue before using them across screens.

# UI flow preservation

Improve styling, spacing and responsive layout without changing existing entry points,
turning dialogs into pages, or adding main navigation sections. Keep profiles, settings
and edit forms modal unless the user explicitly requests a structural change. Ask before
making uncertain flow changes. Use the My Tasks page as the alignment reference for
list/detail pages: shared content top and boundaries, list controls inside the list column.

# Reordering UI

Use drag-and-drop with the shared UiDragHandle for every user-controlled list order.
Do not add up/down buttons or move-up/move-down menu entries. Keep visibility controls
separate from drag handles; cancel on Esc or a drop outside valid rows.
