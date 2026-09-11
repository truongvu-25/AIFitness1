# Changelog

## Unreleased

### Fixed

- Preserve workout arguments through permissions and correct BMI return navigation.
- Deliver auth/profile callbacks to the original resumed view and keep failed reads from restarting onboarding.
- Save initial profile/schedule and plan resets atomically; preserve unrelated metric fields.
- Use one local-first session writer; retain concurrent feedback edits and reject old-plan completion.
- Retain asynchronous camera inputs through inference, release camera/media resources, move bounded gallery decoding off UI and handle corrupt/short videos.
- Handle daily step rollover/reset and foreground-service failure.
- Bound reminder reads and package both switchable locales.

### Maintenance

- Consolidate Android plugin configuration, remove verified unused resources and reuse UI styles/colors.
- Add regression tests, GitHub CI/templates and current documentation.
