# Offline CarPlay native audit

Read-only ELF analysis, not an Android feature, native patch or deployment tool.
Requires Python 3.11+, with the pinned dependencies in requirements.txt installed
in an isolated virtual environment. OEM binaries are private inputs, never bundled.

```sh
python3 -m venv /tmp/carplay-audit
/tmp/carplay-audit/bin/pip install -r scripts/carplay-native-analysis/requirements.txt
/tmp/carplay-audit/bin/python scripts/carplay-native-analysis/audit.py "$LIBRARY" \
  --sha256 "$EXPECTED_SHA256" --function _ZL18_getScreenDisplaysPi
CARPLAY_NATIVE_DIR="$PRIVATE_CAPTURE_DIRECTORY" /tmp/carplay-audit/bin/python \
  -m unittest discover -s scripts/carplay-native-analysis -v
```

Without CARPLAY_NATIVE_DIR the five captured-artifact tests are skipped explicitly.
The five portable tests are synthetic checks of branch classification and hash rejection.
The captured tests pin the exact 2026-10-22 ARM64 stack hash, confirm main-screen calls
in the selected function, recover seven callback slots and check input integrity.
They also check the explicit CoreUtils proxy loader and its symbol-name literals.
Literal extraction is limited to adjacent ADRP/ADD pairs into printable .rodata;
it is not a general dataflow or string-pointer analysis.

test_dual_decoder.py additionally requires FFmpeg with libx264. It generates small
temporary synthetic H.264 inputs and tests separate Linux decoder processes across
secondary stop/restart/resolution changes. The test is skipped if FFmpeg is absent;
a missing libx264 encoder is reported as failure. This is not an AMediaCodec,
OEM integration or CarPlay test. No Android build runs on the coordinator.

The CLI rejects unexpected hashes, architecture, PLT layout, missing/ambiguous
functions, incomplete disassembly and unsupported callback relocations. It validates
each PLT address against the relocation GOT address before naming a transfer.
Symbol sizes and function names come from the artifact; stripped libraries are
unsupported. Direct calls and external tail branches are recorded. Indirect
transfers remain unresolved; conditionals are not treated as direct calls.
Missing AltScreen calls in one function do not prove their absence everywhere.

When gVideoCallback exists, the tool requires the observed 56-byte table and seven
AArch64 relative relocations. This recovers addresses, not a callable C ABI:
do not cast the table or replace callbacks based on this JSON alone.

No artifact is executed, uploaded or patched. Output can include binary-derived
instructions and symbols; treat it as untrusted data. A successful audit is not
proof of negotiation, a second stream, decoder isolation or vehicle readiness.

## Native host components

native/observed_video_abi.h records the seven callback signatures from the exact
ARM64 stack's DWARF, with 64-bit size/offset assertions. It is not a hook, complete
SDK header or runtime ABI verifier. Opaque types remain opaque.

native/packet_conversion.cpp is a standalone C++17 length-prefix-to-Annex-B
converter. Width is an explicit input (1, 2 or 4), not inferred from packet bytes.
Validation precedes allocation; malformed input produces no partial output.
Output is bounded by the caller, including start-code expansion. It supports
multiple NAL units; that does not imply the OEM accepts this format.

These are Linux host builds only, not Android/NDK builds:

```sh
g++ -std=c++17 -Wall -Wextra -Werror -fsanitize=address,undefined \
  -fno-omit-frame-pointer -g scripts/carplay-native-analysis/native/packet_conversion.cpp \
  scripts/carplay-native-analysis/native/packet_conversion_test.cpp \
  -o /tmp/carplay-packet-conversion-test
/tmp/carplay-packet-conversion-test
g++ -std=c++17 -Wall -Wextra -Werror -fsanitize=address,undefined \
  -fno-omit-frame-pointer scripts/carplay-native-analysis/native/packet_conversion.cpp \
  scripts/carplay-native-analysis/native/packet_conversion_cli.cpp \
  -o /tmp/carplay-packet-converter
CARPLAY_NATIVE_DIR="$PRIVATE_CAPTURE_DIRECTORY" \
CARPLAY_PACKET_CONVERTER=/tmp/carplay-packet-converter /tmp/carplay-audit/bin/python \
  -m unittest discover -s scripts/carplay-native-analysis -v
```

The native fixture executable covers 14 fixed cases plus 10,000 seeded malformed
inputs under ASan/UBSan. The CLI is a bounded host test adapter accepting a file
of four-byte-prefixed NALs, not the full AVCDecoderConfigurationRecord (avcC).
The additional Python test encodes ten synthetic H.264 frames, repackages NALs,
runs the actual native converter, then compares decoded framemd5 output.
With both optional inputs present, all twelve Python tests passed.

## Experimental bridge and Android backend

native/video_bridge.{h,cpp} routes through the exact original callback table by
default (BridgeConfig.enabled=false); it never creates a secondary decoder in
that mode. Enabled mode requires explicit principal/secondary identities captured
before Initialize. Missing identities, duplicate identities and unknown streams
fail closed without touching OEM. Identity is type+UUID, not arrival order.

The secondary gets its own decoder from a factory. Stream routes never change on
failure or shutdown; late secondary data cannot fall back into OEM. Configuration
and frame sizes are bounded. Raw input is copied and validated before submission.
Secondary shutdown waits for an in-flight submission, stops/destroys only that
decoder and leaves principal delivery independent. Finalize releases the route.
The adapter must quiesce callbacks before reusing a stream address or destroying
the bridge; the OEM ABI itself has no generation field. Decoder factories and
backends must not reenter the same bridge while called under its per-stream lock.

avc_configuration.cpp parses a conservative avcC subset into Annex-B SPS/PPS.
Malformed input, missing sets, reserved prefix width and trailing extensions
are rejected. It checks structural framing, not full SPS bitstream semantics.
android_secondary_decoder.cpp owns one AMediaCodec, output worker and window
reference per instance. It requires an explicit raw-ticks→microseconds converter;
none is guessed. Its caller must supply an independently owned Surface, not the
principal output. Backpressure, output-size changes or codec errors fail closed.
This is a conservative prototype, not a tuned realtime video pipeline.

Secondary metadata/completion/context variants not observed in the captured
branch are rejected. Principal callbacks pass all original arguments/returns
unchanged. No completion ownership contract is invented for secondary input.

Host bridge validation (uses a synthetic backend, not AMediaCodec):

```sh
g++ -std=c++17 -Wall -Wextra -Werror -pthread -fsanitize=address,undefined \
  -fno-omit-frame-pointer -g scripts/carplay-native-analysis/native/packet_conversion.cpp \
  scripts/carplay-native-analysis/native/avc_configuration.cpp \
  scripts/carplay-native-analysis/native/video_bridge.cpp \
  scripts/carplay-native-analysis/native/video_bridge_test.cpp \
  -o /tmp/carplay-video-bridge-test
/tmp/carplay-video-bridge-test
g++ -std=c++17 -Wall -Wextra -Werror -fsanitize=address,undefined \
  -fno-omit-frame-pointer -g scripts/carplay-native-analysis/native/avc_configuration.cpp \
  scripts/carplay-native-analysis/native/avc_configuration_test.cpp \
  -o /tmp/carplay-avc-configuration-test
/tmp/carplay-avc-configuration-test
```

The app's CMake build compiles/links the Android target only on ARM64, using the
pinned NDK/CMake. The resulting libcarplay_video_bridge.so is explicitly excluded
from the APK, and the C++ runtime is linked statically. This validates compilation
without adding a loadable runtime dependency or restricting existing APK ABIs.
All Android builds go through the audited Mac Mini queue, never the coordinator.

Not implemented: actual OEM interception/loader installation, Surface transfer,
runtime clock calibration, secondary-display advertisement, watchdog/visibility
ownership or a user-facing toggle. Removing the APK exclusion alone is not a safe
deployment procedure.

## Pre-creation ingress and bounded observer

`native/oem_ingress.{h,cpp}` adds exception-contained C trampolines around the
observed seven-slot ABI and StartSession(session, UUID, type, context). A bounded
thread-local scope carries an owned identity across synchronous Initialize;
cross-thread or missing scopes reject secondary setup. Disabled mode forwards
without interpreting UUIDs. The captured CFL string API uses 32-bit lengths,
not the conventional 64-bit CFIndex. Host tests exercise nested scopes, exception
unwinding, malformed strings and independent retirement under ASan/UBSan and TSan.

Registration is one-shot and intentionally process-lifetime pinned: the module
and its original OEM targets must remain loaded. Supply real unmodified targets
or interception trampolines, never already-patched entry points. There is no
unload/rebind protocol. Callback quiescence remains an external requirement.
The C bootstrap installs only passthrough state; it does not patch OEM callbacks,
load the module, create a Surface or enable the secondary decoder.

`probe_profile.py` creates an offline profile from three exact hash-pinned private
ARM64 libraries. `oem_ingress_probe.js` is inert until an explicit RPC start with
that reviewed profile and fresh file-hash attestations. It verifies mapped build
identities and code signatures before installing five observation hooks. It logs
pseudonymous stream/session sequences, UUID hashes and sampled raw 64-bit ticks;
it never copies image payloads. Defaults: 60 seconds/1,000 records; hard limits:
five minutes/3,000 records, bounded stream and scope maps. Stop detaches its hooks.
Finalize records entry, not completed resource release or callback quiescence.

Interceptor.attach temporarily instruments native code and adds callback overhead;
this is not zero-impact observation. First actual use requires a parked controlled
test. No automatic Frida deployment, SELinux change or vehicle operation is added.
Node VM tests validate observer logic, not compatibility with target Frida runtime.

Static decompilation of the captured CoreUtils UpTicks shows CLOCK_MONOTONIC in
nanoseconds. AirPlay's frame path uses UpTicks or synchronized NTP-to-UpTicks;
this is not the navigation ETA field. Runtime clock validation is still required;
the Android decoder continues to demand an explicit converter rather than guessing.

Validation commands in addition to the native bridge tests:

```sh
node --check scripts/carplay-native-analysis/oem_ingress_probe.js
node scripts/carplay-native-analysis/test_probe.js "$PRIVATE_PROFILE"
CARPLAY_NATIVE_DIR="$PRIVATE_CAPTURE_DIRECTORY" \
CARPLAY_PACKET_CONVERTER=/tmp/carplay-packet-converter /tmp/carplay-native-analysis-venv/bin/python \
  -m unittest discover -s scripts/carplay-native-analysis -v
```

The expanded Python suite has 14 tests with both optional private inputs supplied.
Neither these tests nor compilation proves a negotiated secondary CarPlay map.

## Independent Surface transport prototype

`CarPlaySurfaceEndpoint.java` lives exclusively in the existing internalDebug
source set (Debug/leanDebug), without a manifest service, receiver, auto-loading,
service-manager registration or call site. Release code is untouched. A future
authorized OEM loader must load this class and native library in the same class
loader, create the endpoint with one exact producer UID and a fresh cryptographic
32-byte session token, then deliver that Binder through an authenticated bootstrap.
No such loader/bootstrap is installed by this change.

The producer sends a Parcelable Surface, positive generation and a remote owner
Binder. The endpoint enforces UID/token, synchronous protocol, bounded Parcel,
no trailing data and a single grant. It imports the queue using
ANativeWindow_fromSurface, retaining its own native reference, and releases only
the received Java wrapper. Explicit revoke, endpoint close or remote owner death
retires that grant. A local owner Binder cannot provide remote death notification;
the UI owner must also revoke before its Surface is destroyed, hidden or replaced.
No lease renewal/TTL or global registry lookup is exposed to remote callers.

`android_surface_transport.cpp` uses a bounded eight-entry, non-reused positive
handle registry. `surfaceDecoderFactory(handle, explicitTickConverter)` can be
claimed once; no converter means no factory. Closing removes the handle before
stopping its associated decoder, outside the registry lock. The portable
`DecoderLease` serializes submission/revocation and stops only its own codec;
retired factories/clients cannot reopen it. It must not call back into itself.

The caller must create a fresh independent consumer/Surface on the intended
display. Neither different Java/native wrapper pointers nor Binder transfer prove
independence from the principal BufferQueue. This code deliberately does not borrow
the existing CarPlay Surface, create a Presentation, change resolution, move an
Activity or negotiate a display. No Binder/Surface/JNI path has run on Android yet.
The shared library remains excluded from all APKs; invoking this debug endpoint
without a separately authorized loader fails rather than loading code implicitly.

Portable lease tests (fake decoder, not Android Binder or AMediaCodec):

```sh
g++ -std=c++17 -Wall -Wextra -Werror -pthread -fsanitize=address,undefined \
  -fno-omit-frame-pointer -g scripts/carplay-native-analysis/native/decoder_lease.cpp \
  scripts/carplay-native-analysis/native/decoder_lease_test.cpp -o /tmp/carplay-decoder-lease-test
timeout 20 /tmp/carplay-decoder-lease-test
```

Repeat with `-fsanitize=thread` for concurrent revoke/submission isolation. Tests
cover single claim, early/duplicate revoke, factory null/exception, backend failure,
client destruction and independent progress while another submission blocks.

API references: [Surface Parcelable/lifetime](https://developer.android.com/reference/android/view/Surface),
[Binder death](https://developer.android.com/reference/android/os/IBinder.DeathRecipient),
[native window references](https://developer.android.com/ndk/reference/group/a-native-window).
