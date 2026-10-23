import json
import unittest
from pathlib import Path

from cold_passthrough_controller import (
    PID_PLACEHOLDER,
    classify_injector_log,
    launcher_template,
    sha256_bytes,
    specialize_launcher,
)


ROOT = Path(__file__).resolve().parent


class ColdPassthroughControllerTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.interceptor = (ROOT / "cold_oem_interceptor.js").read_text(encoding="utf-8")
        cls.profile = json.loads((ROOT / "testdata/oem-cold-profile-v3.json").read_text(encoding="utf-8"))

    def test_pid_specialization_matches_exact_launcher_shape(self):
        template = launcher_template(self.interceptor, self.profile, 4096, "a" * 64)
        exact = specialize_launcher(template, 4242)
        self.assertIn('"expectedPid":4242', exact)
        self.assertNotIn(str(PID_PLACEHOLDER), exact)
        self.assertEqual(1, exact.count('"expectedPid":4242'))

    def test_specialization_rejects_missing_or_ambiguous_placeholder(self):
        with self.assertRaises(ValueError):
            specialize_launcher("", 42)
        with self.assertRaises(ValueError):
            specialize_launcher(f'"expectedPid":{PID_PLACEHOLDER}' * 2, 42)
        with self.assertRaises(ValueError):
            specialize_launcher(f'"expectedPid":{PID_PLACEHOLDER}', 0)

    def test_log_phase_uses_last_state(self):
        log = '\n'.join([
            '{"payload":{"status":{"phase":"armed"}}}',
            '{"payload":{"status":{"phase":"installing"}}}',
            '{"payload":{"status":{"phase":"installed-pinned"}}}',
        ])
        self.assertEqual("installed-pinned", classify_injector_log(log))
        self.assertEqual("unknown", classify_injector_log("plain output"))

    def test_hash_is_stable(self):
        self.assertEqual(
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            sha256_bytes(b"abc"),
        )


if __name__ == "__main__":
    unittest.main()
