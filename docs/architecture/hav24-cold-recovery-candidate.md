# HAV-24 — local cold-recovery candidate (2026-10-28)

Base remotely verified: PR #15, rafaelcs28/haval-app-tool-multimidia,
839e944409725ce3b6fc667ad00e15194f1a5c3c. No remote write or installation.

## Scope

- Retain the in-memory cold/clear observation across recovery only for the exact
  boot/PID/starttime/ticket. App-process recovery with no prior proof starts at zero.
- Renew only from a new bounded observation with exact identity, allowed bridge,
  known-clear CarPlay/AA visibility, and all reviewed OEM modules still absent.
- A warm sample can retain only the existing unexpired lease, never extend it.
- Unknown, visible, foreign or slow observations revoke that cold proof.
- Discard replaced publisher snapshots and revalidate freshness under publication lock.
- Existing 2.5-second TTL, opt-in, cancellation and warm-restart gates remain unchanged.
  No Surface, OEM, bridge protocol, AA behavior or runtime configuration modification.

## Validation and limitations

Five host JUnit tests cover exact recovery identity/ticket, TTL boundary and regression,
renewal vs warm transition, unknown/active/foreign observations, publication and
terminal cancellation. These execute production proof helper/publication gate,
not the Android manager or OEM installation pipeline.

Existing Kotlin 2.0.21/JUnit toolchain was reused. No usable Android SDK/Gradle
project for this exact snapshot: :app:testDebugUnitTest and :app:assembleDebug
have NOT run. No physical/OEM testing was attempted.
Resident observations can be slow; candidate fails closed if lease expires while
those calls run. This is a partial local correction, NOT proof the temporal failure
is eliminated. Validate manager scheduling/timing on a supported Android build.

AGENTS.md and regression/architecture docs were read. AGENTS-referenced
.ai-context/HANDOFF.md, .ai-context/CHANGELOG-AI.md, .ai-context/README.md and QA/docs
skill files returned 404 at this head; no history was invented.

Additional regression run: 29 JUnit tests passed total (5 new + 24 existing publication-gate/bootstrap-policy tests). Independent review confirmed stale publisher snapshot fix. Android manager compilation remains unverified.
