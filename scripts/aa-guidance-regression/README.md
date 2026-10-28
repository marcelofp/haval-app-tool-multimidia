# HAV-24 Android Auto guidance regression (local, test-only)
Source commit: netseek/haval-app-tool-multimidia@839e944409725ce3b6fc667ad00e15194f1a5c3c, PR15, read 2026-10-28.
Production files in source/ are unmodified connector snapshots. expected-blobs.json records exact verified Git blob IDs (not merely filenames). No OEM artifact/dependency downloaded. Existing Kotlin2.0.21 compiler dependencies reused.

## Reproduce
Local standalone artifact:
    export KOTLIN_DEPS=/workspace/scratch/85dcf7c31a12/haval-carplay-host-harness-20261006/deps
    SOURCE_ROOT="$PWD/source" sh run.sh
    python3 mutation_check.py
    SOURCE_ROOT="$PWD/source" sh run.sh

After applying tests-only.patch to exact source checkout:
    KOTLIN_DEPS=<existing-dependencies-directory> sh scripts/aa-guidance-regression/run.sh
    KOTLIN_DEPS=<existing-dependencies-directory> python3 scripts/aa-guidance-regression/mutation_check.py

No dependency is automatically downloaded. JDK and Kotlin2.0.21 compiler/stdlib with its existing transitive dependencies are prerequisites.

## Results
Twelve deterministic JVM scenarios pass (nine initial cases plus three exact external-spec examples; see SPEC-MAPPING.md):
1. Each silence boundary from10 through59seconds, plus59,999ms, retains active guidance.
2. At60,000ms monitor publishes inactive and remains inactive at120seconds.
3. New route callback at59seconds rearms expiry to119seconds.
4. Repeated live controller/passive-link availability does not prevent expiry.
5.60,000 snapshot/diagnostic cache reads do not renew guidance timer.
6. Ordinary AA path remains available without enabled CarPlay capture, generation or passive observer.
7. Isolated policy has no AA expiry (intentional characterization, not a claim of standalone safety).
8. Repeated polls do not refresh original5second navigation evidence; at5,001ms evidence is stale.
9. Simulated recent DCM link evidence does not refresh navigation timer.

Two disposable negative-control mutations are detected:
-60s changed to10s fails silence scenario.
-Expiration scheduling removed fails exact60s scenario.
Unmodified suite is rerun last. See results.log and mutation-results.log.

## Scope and limitations
Full real AndroidAutoNavigationMonitor, navigation telemetry, session telemetry, CarPlayGuidance and CarPlayHudPolicy compile unchanged. Input enters real monitor Binder callback code10 with synthetic typed Parcel fields; production parser, accumulator, publish, stale runnable and clearNavigation execute.
Android Handler/Looper/SystemClock/Binder/Parcel/Context/logging are host stubs. Scheduler advances deterministically and executes jobs on time; it does NOT emulate suspend, Android queue latency, concurrency or Binder marshalling/permissions. Stub controller forwards monitor output directly to real policy and records it. This intentionally does NOT prove actual controller→ExperimentalHud→HUD wiring, Android renderer clearing, real link poll loop, or observer lifecycle. “AA outside capture” proves monitor+policy behavior under this bridge, not end-to-end Android wiring.
DCM test supplies boolean recent=true; it does NOT validate DCM snapshot acquisition or180s cache implementation. Five-second check executes actual session helper, not full polling controller.
No Android Gradle unit/build task, APK, physical device, Waze cadence measurement, OEM integration, real UI/frame or vehicle test was executed. Code comments reporting09/24 measurements are author reports, not new test evidence.
Initial harness fixture incorrectly expected navigation status ACTIVE alone to create guidance; real accumulator correctly waits for route data. Fixture was corrected to valid synthetic route callback10; no production behavior changed.

## Remote duplication check
Before work: direct PR15 read confirmed open/draft on839e944. Search of open upstream PRs returned14/11/7/6 and no new candidate, but omitted known PR15, so search is incomplete/indexed and cannot prove global absence of work. Parent coordinates parallel Claude specs. This suite stays separate from prior local cold/HUD candidate.
