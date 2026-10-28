"""Negative controls operate on disposable source copies only."""
import os, pathlib, shutil, subprocess
root = pathlib.Path(__file__).resolve().parent
source_root = pathlib.Path(os.environ.get("SOURCE_ROOT", root / "source" if (root / "source").exists() else root / "../../app/src/main/java/br/com/redesurftank/havalshisuku")).resolve()
for name, old, new, expected in [
    ("ten-second-cutoff", "STALE_GUIDANCE_MS = 60_000L", "STALE_GUIDANCE_MS = 10_000L", "silence"),
    ("missing-expiration", "retryHandler.postDelayed(staleGuidanceRunnable, STALE_GUIDANCE_MS)", "Unit", "deadline"),
]:
    dst = root / "build" / name
    for relative in ["services/AndroidAutoNavigationMonitor.kt", "managers/AndroidAutoNavigationTelemetry.kt", "managers/AndroidAutoSessionTelemetry.kt", "navigation/CarPlayGuidance.kt", "navigation/CarPlayHudPolicy.kt"]:
        target = dst / relative
        target.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(source_root / relative, target)
    p = dst / "services/AndroidAutoNavigationMonitor.kt"
    source = p.read_text()
    assert source.count(old) == 1
    p.write_text(source.replace(old, new))
    env = dict(os.environ, SOURCE_ROOT=str(dst))
    result = subprocess.run(["sh", str(root / "run.sh")], env=env, capture_output=True, text=True)
    log = result.stdout + result.stderr
    (root / (name + ".log")).write_text(log)
    assert result.returncode != 0 and "Check failed" in log, log
    if expected == "silence": assert "PASS silence" not in log
    else: assert "PASS silence" in log and "PASS deadline" not in log
    print("PASS negative control detected:", name)
