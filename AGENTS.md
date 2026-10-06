# Integration adapters

Optional integrations with other mods must activate automatically when the mod is detected and its API is supported. Do not add opt-in switches or require manual activation unless the user explicitly requests an exception. Keep absent or incompatible APIs isolated within their adapters.

# Release versions

Use MAJOR.MINOR.PATCH based on user impact: PATCH for fixes, optimization, tests and documentation; MINOR for new features/settings while preserving existing configuration and data; MAJOR for incompatible changes requiring owners to manually change configuration, data or their pack. Validate wire protocols and negotiated features separately; release numbers alone do not establish compatibility. Prefer explicit bounded requirements (>=1.2.0 <2.0.0) or exact requirements (=1.2.0) in new seeds. Preserve legacy bare-release requirements as minimum releases within their major.

# Server configuration updates

On upgrades, add new server settings and their explanatory comments to the existing config file automatically. Preserve the owner's values, comments and credentials; validate before writing and keep a backup. Owners must be able to configure every new server option in the file, rather than rely on invisible defaults.

# Server configuration naming

Use concise names within their section (for example display.nameplates, chat.coordinates, database.database). Avoid redundant Enabled or allow prefixes. Keep units explicit for numeric durations and sizes. Update all readers, the template and the server guide together.

# Visual regression coverage

Run complete UI flows in the default theme at three GUI scales. Cover light and high-contrast profiles separately; sample every other theme on representative screens at GUI 3 instead of multiplying every scenario by every theme. Keep palette contrast checks for all themes. Each suite reports its planned frame count; CI verifies the exact count and rejects missing or extra PNGs.
