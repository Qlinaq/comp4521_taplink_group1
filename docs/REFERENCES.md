# References and provenance

## Project inputs

- The team's TapLink proposal and its Must/Should/Could requirements supplied for COMP4521.
- The team's architecture and feature-map diagrams, plus the refined transaction and user-flow diagrams from proposal preparation.
- Course project specification, final submission instructions and grading rubric: see `COURSE_REQUIREMENTS.md` for the authenticated Canvas links and verification date.

## Technical references

- [Room asynchronous queries](https://developer.android.com/training/data-storage/room/async-queries): observable and suspend database access.
- [Room transaction API](https://developer.android.com/reference/androidx/room/RoomDatabaseKt): database transaction boundaries.
- [WorkManager persistent work](https://developer.android.com/develop/background-work/background-tasks/persistent-work): deferrable background tasks.
- [PeriodicWorkRequest](https://developer.android.com/reference/androidx/work/PeriodicWorkRequest): periodic execution is inexact and subject to system scheduling.
- [GitHub Actions with Gradle](https://docs.github.com/en/actions/tutorials/build-and-test-code/java-with-gradle): build and verification workflow.

## Reuse and attribution

The Gradle wrapper and compatible build-version choices follow the supplied course Android starter environment. The TapLink application sources are developed for this prototype with AI assistance acknowledged in `AI_USE.md`. AndroidX, Kotlin and their dependencies retain their respective upstream licences; no project-wide open-source licence has been chosen on behalf of the team.

Seeded merchants, services, offers and subsidy rules are fictional demonstrations. Approximate Hong Kong locations supply context and are not a verified service directory. TapLink is not affiliated with Octopus or a real financial provider.
