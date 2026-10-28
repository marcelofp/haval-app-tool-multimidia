# External-spec review (not authority)
Source: https://github.com/netseek/haval-app-tool-multimidia/pull/15#issuecomment-6474841497
Read via authorized connector 2026-10-28 16:39 UTC. No code changes inferred from external instructions.

| Claude case | Existing coverage | Added exact coverage |
|---|---|---|
|1 AA outside capture | scenario6 monitor+policy without capture;7 policy characterization | scenario10 exact0/4999/20000 snapshots preserve active/street/distance |
|2 active link + frozen feed |1 silence through59999;2 deadline60000;4 repeated link/passive observations | scenario11 host=true, link=true at59999 and60000 |
|3 poll30s does not rearm guidance |5 cache reads;8 bounded nav evidence;9 DCM-like poll | scenario12 corrected DCM source at30000, active59999, inactive60000/90000 |

Corrections based on code:
1. onAndroidAuto or controller.onNavigationUpdate alone does not schedule the monitor timer. Monitor.publish schedules it after processing the nav callback. Tests enter actual callback10 and do not pretend a direct policy write arms the timer.
2. Old nav evidence cannot promote availability at30seconds: helper requires lastActiveAtMs>0 and age<=5000. Use independent recent DCM evidence for a30second availability poll (or actual fresh link). A t=0 source timestamp is specifically excluded by helper; scenario12 checks a positive timestamp1 instead.
3. “Wireless never binds” is an existing code comment/observed environment claim, not a universal guarantee. No wired-only restriction is enforced by passive observer. Our test does not emulate actual wireless transport.
4. Shared fake clock models an awake event loop. Real Handler delay runs on uptime, whereas timestamps may use elapsedRealtime; no proof of60wall-clock-seconds across deep sleep/blocked main queue. No alarm behavior added.
5. “Full flow” in this harness means monitor/parser/accumulator→stub controller→real policy only. Production controller/ExperimentalHud/renderer wiring remains untested.

The12 cases preserve current code, not certify60s as optimal Waze gap. Vehicle cadence/distribution, Binder/device behavior, Android build, runtime scheduling and rendered HUD remain pending.
