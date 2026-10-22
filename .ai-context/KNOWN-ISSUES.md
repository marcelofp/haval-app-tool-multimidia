# Known issues

- CarPlay guidance external Binder permission and real callback delivery have not been validated on the vehicle.
- A successful oneway registration is transport acceptance, not evidence of iPhone link or active route.
- The device-side Parcel self-test only executes when the user starts capture; a successful release build cannot prove it passed on Android.
- The legacy CarPlay regression checker has known base drift; compare its exact output with the 74122 base and do not weaken it.
- Private capture ends on process death or sixty minutes and rotates after three MiB; retrieve it promptly.
- No second CarPlay video feed or map-clean functionality is provided by this diagnostic release.
