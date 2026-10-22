# Known issues

- Vehicle session on 2026-10-22 confirmed external Binder delivery of two inactive/default Waze payloads; active-route delivery remains unvalidated.
- A successful oneway registration is transport acceptance, not evidence of iPhone link or active route.
- The device-side Parcel self-test passed 102 checks in that vehicle session; future firmware compatibility still requires a fresh capture, not just a successful build.
- The legacy CarPlay regression checker has known base drift; compare its exact output with the 74122 base and do not weaken it.
- Private capture ends on process death or sixty minutes and rotates after three MiB; retrieve it promptly.
- No second CarPlay video feed or map-clean functionality is provided by this diagnostic release.
