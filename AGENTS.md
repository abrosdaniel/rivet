# Integration adapters

Optional integrations with other mods must activate automatically when the mod is detected and its API is supported. Do not add opt-in switches or require manual activation unless the user explicitly requests an exception. Keep absent or incompatible APIs isolated within their adapters.

# Release versions

Use A.B.C: C for fixes, B for compatible features, A for incompatible changes or a required manual migration. Validate wire protocols and negotiated features separately; equal major versions alone do not establish compatibility. Prefer explicit bounded requirements (>=1.1.0 <2.0.0) or exact requirements (=1.1.0) in new seeds. Preserve legacy bare-release requirements as minimum releases within their major.
