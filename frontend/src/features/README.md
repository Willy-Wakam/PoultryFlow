# Frontend features

Feature-oriented UI modules live here as their user stories are implemented. Each feature owns its typed API boundary, UI, validation, and focused tests; authentication remains in `src/auth`, and application composition remains in `src/app`.

Implemented feature modules:

- `farm` contains the online-only current farm profile editor.
- `users` contains current farm access, membership-aware guards, and online-only user administration.
- `locations` contains online-only location administration for owners and managers; stable IDs remain when locations become inactive.
