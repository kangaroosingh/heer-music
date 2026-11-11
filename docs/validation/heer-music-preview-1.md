# Heer Music Preview 1 validation

This is a signed test preview, not a stable release. Listening quality and Android Auto compatibility need validation on the phone and car.

## Automated checks

- Formatting and the final affected JVM regression pass succeeded: 1,042 passed, five existing fixture tests skipped.
- The broader suite exercised 5,574 cases: 5,527 passed, 46 skipped, one WebDAV pairing timeout. All five tests in that pairing class passed on an isolated rerun. Portable path checks, private-store classification, repository links and explicit music-only navigation contracts were corrected without dropping their checks.
- Twelve real-application integration checks passed on an API 36 Android 16 emulator in four isolated instrumentation invocations: four playback/car-browser checks, three storage/feedback checks, four Smart Downloads checks and one Song/Video/Like/Dislike check. Debug instrumentation was precompiled with ART; it is not a startup-performance measurement.
- The native checks included a real audio download and airplane-mode playback, generated offline audio with the screen off, playlist database persistence, persisted GB and network choices, one automatic download through the actual queue, video-track selection, Like downloading audio and Dislike persisting exclusion and deleting that file. The Dislike button waits for its feedback/deletion job before requesting an advance, and does not request another advance if the current song already changed.
- GB-budget tests cover estimates, reservations, final-file limits, lowering the budget, legacy count-limit migration and exclusion of manual downloads. No multi-GB download was performed for testing.

## Recommendation benchmark

The music pipeline benchmark matched the saved baseline: quick-picks and discovery hit rate 1.000, no blocked leaks and the original regression floors retained. The generic recommendation benchmark also passed its existing floors; its scores varied between runs. Synthetic results do not establish improved real-world recommendation quality.

## Release checks

The baseline and startup profiles were regenerated successfully (two collection journeys). Cold, warm and hot startup benchmarks completed successfully (three tests) on the API 36 emulator. Emulator time-to-initial-display medians were approximately 1,890 ms cold, 933 ms warm and 118 ms hot. These are emulator diagnostics, not a phone performance claim. The APK hash, signing identity and upgrade-install check are recorded in the GitHub release notes.

## Limits

The tests use an emulator, not the user's phone or an Android Auto head unit. Online catalogues, video matching and playback still depend on upstream services. Smart Downloads is optional and constrained by the selected network/charging rules, battery and free space. Its GB value is a ceiling; it fills eligible tracks in bounded batches. Lowering it keeps existing files. New manual or liked downloads are outside the automatic budget; an already automatic file retains its automatic origin.

Dislike removes downloaded audio from this phone. It does not delete any Navidrome/server file or block the artist. Undo restores recommendation eligibility; Like queues audio again. All offline music requests are audio only, including music-video sources.
