#include "oem_runtime_adapter.h"
#include "hook_installation.h"
#include "coordinated_dispatch.h"

#include <cassert>
#include <future>
#include <iostream>
#include <stdexcept>

namespace carplay_experiment {
struct CFLArray { int serial; std::vector<int> displays; };
}

using namespace carplay_experiment;

namespace {
std::atomic<int> alive{0};
std::atomic<int> nextSerial{0};
std::atomic<int> getterCalls{0};
std::atomic<int> copies{0};
int originalFrames = 0;
int originalInitializes = 0;
int decoderCreates = 0;
int decoderStops = 0;
std::atomic<int> reads{0};
int mainKey = 0;
int secondaryKey = 0;
bool getterThrows = false;
bool frameThrows = false;
bool finalizeThrows = false;
bool appendFails = false;
bool getterNull = false;
observed_abi::Status getterStatus = 0;
std::string mainUuid = "main";
std::string secondaryUuid = "secondary";
std::vector<int> released;
std::mutex releasedMutex;
struct Gate {
    std::mutex mutex;
    std::condition_variable changed;
    bool entered = false;
    bool released = false;
    void block() {
        std::unique_lock<std::mutex> lock(mutex);
        entered = true;
        changed.notify_all();
        changed.wait(lock, [this] { return released; });
    }
    void wait() {
        std::unique_lock<std::mutex> lock(mutex);
        assert(changed.wait_for(lock, std::chrono::seconds(2), [this] { return entered; }));
    }
    void release() {
        std::lock_guard<std::mutex> lock(mutex);
        released = true;
        changed.notify_all();
    }
};
std::atomic<Gate*> appendGate{nullptr};
const std::vector<std::uint8_t> configuration{1, 66, 0, 30, 0xff, 0xe1,
    0, 4, 0x67, 66, 0, 30, 1, 0, 2, 0x68, 0x88};
const std::vector<std::uint8_t> packet{0, 0, 0, 2, 0x65, 0x88};

observed_abi::Stream stream(int& value) { return reinterpret_cast<observed_abi::Stream>(&value); }
observed_abi::String string(std::string& value) { return reinterpret_cast<observed_abi::String>(&value); }
DisplayArray array(std::vector<int> values) { ++alive; return new CFLArray{++nextSerial, std::move(values)}; }
void release(DisplayArray value) noexcept {
    assert(value);
    {
        std::lock_guard<std::mutex> lock(releasedMutex);
        released.push_back(value->serial);
    }
    --alive;
    delete value;
}

DisplayArray getter(observed_abi::Status* error) {
    ++getterCalls;
    if (getterThrows) { throw std::runtime_error("synthetic getter failure"); }
    *error = getterStatus;
    return getterNull ? nullptr : array({110, 77});
}

UuidReader reader() {
    return [](observed_abi::String value) -> std::optional<std::string> {
        ++reads;
        if (!value) { return std::nullopt; }
        return *reinterpret_cast<std::string*>(value);
    };
}

observed_abi::VideoCallbacks originals() {
    return {
        [](observed_abi::Stream) { ++originalInitializes; return 0; },
        [](observed_abi::Stream) { if (finalizeThrows) { throw std::runtime_error("synthetic void failure"); } },
        [](const void*, std::uint32_t, std::uint32_t) {},
        [](observed_abi::Stream, std::uint32_t, observed_abi::String) { return 0; },
        [](observed_abi::Stream) {},
        [](const void*, const std::uint8_t*, std::size_t) { return 0; },
        [](observed_abi::Stream, const std::uint8_t* bytes, std::size_t length, std::uint64_t ticks,
            observed_abi::Dictionary, observed_abi::Completion, void*) {
            if (frameThrows) { throw std::runtime_error("synthetic frame failure"); }
            assert(bytes == packet.data() && length == packet.size() && ticks == 1234);
            ++originalFrames;
            return 37;
        }
    };
}

observed_abi::Status originalSession(void*, observed_abi::String uuid, std::uint32_t type, void*) {
    const auto target = type == 111 ? stream(secondaryKey) : stream(mainKey);
    auto status = impulse_carplay_runtime_initialize(target);
    if (status != 0) { return status; }
    impulse_carplay_runtime_dimensions(target, 1280, 720);
    status = impulse_carplay_runtime_avcc(target, configuration.data(), configuration.size());
    return status != 0 ? status : impulse_carplay_runtime_start(target, type, uuid);
}

class Backend final : public SecondaryDecoder {
public:
    Backend() { ++decoderCreates; }
    bool configure(std::uint32_t, std::uint32_t, const std::vector<std::uint8_t>&) override { return true; }
    bool submit(const std::vector<std::uint8_t>&, std::uint64_t) override { return true; }
    void stop() noexcept override { ++decoderStops; }
};

std::unique_ptr<OemRuntimeAdapter> makeAdapter(bool enabled, bool ownedContract = true) {
    SecondaryRuntimeConfig settings;
    settings.enabled = enabled;
    if (!enabled) {
        return std::make_unique<OemRuntimeAdapter>(std::make_unique<SecondaryRuntime>(
            originals(), originalSession, settings, VideoBridge::DecoderFactory{}, UuidReader{}), getter);
    }
    settings.bridge.principal = {110, "main"};
    settings.bridge.secondary = {111, "secondary"};
    settings.display.enabled = true;
    settings.display.principal = settings.bridge.principal;
    settings.display.secondary = settings.bridge.secondary;
    settings.display.secondaryUuid = string(secondaryUuid);
    settings.display.framesPerSecond = 30;
    settings.display.widthPixels = 1280;
    settings.display.heightPixels = 720;
    settings.display.widthMillimeters = 300;
    settings.display.heightMillimeters = 100;
    auto lease = std::make_shared<DecoderLease>([] { return std::make_unique<Backend>(); });
    auto session = std::make_shared<SecondarySession>(true);
    const auto generation = session->arm(lease);
    DisplayAdvertisementApi api{
        [](DisplayArray source) { ++copies; return array(source->displays); }, release,
        [](DisplayArray* target, observed_abi::String, std::uint32_t, std::uint32_t,
            std::uint32_t, std::uint32_t, std::uint32_t, std::uint8_t*, std::uint8_t*,
            std::uint32_t, observed_abi::String) {
            if (appendFails) { return -123; }
            if (auto gate = appendGate.load()) { gate->block(); }
            (*target)->displays.push_back(111);
            return 0;
        }, reader()
    };
    auto runtime = std::make_unique<SecondaryRuntime>(originals(), originalSession, settings,
        [lease](const StreamIdentity&) { return lease->claim(); }, reader(), session, generation,
        api, std::make_shared<int>(1));
    return std::make_unique<OemRuntimeAdapter>(std::move(runtime), getter, release, ownedContract);
}

std::unique_ptr<OemIngress> makeIngress() {
    return std::make_unique<OemIngress>(originals(), originalSession, BridgeConfig{},
        VideoBridge::DecoderFactory{}, UuidReader{});
}

observed_abi::Status send(observed_abi::Stream target) {
    return impulse_carplay_runtime_data(target, packet.data(), packet.size(), 1234, nullptr, nullptr, nullptr);
}

void boundaryFaultsKeepPrincipalAvailable() {
    getterThrows = true;
    observed_abi::Status error = 0;
    const auto faults = impulse_carplay_runtime_faults();
    assert(!impulse_carplay_runtime_displays(&error) && error == VideoBridge::decoderFailure);
    getterThrows = false;
    frameThrows = true;
    assert(send(stream(mainKey)) == VideoBridge::decoderFailure);
    frameThrows = false;
    finalizeThrows = true;
    impulse_carplay_runtime_finalize(stream(mainKey));
    finalizeThrows = false;
    assert(impulse_carplay_runtime_faults() == faults + 3);
    assert(send(stream(mainKey)) == 37);
    assert(!impulse_carplay_runtime_displays(nullptr));
    assert(impulse_carplay_runtime_faults() == faults + 4);
}

void adapterMode(bool enabled) {
    observed_abi::Status error = 0;
    assert(!impulse_carplay_runtime_displays(&error) && error == VideoBridge::notReady);
    if (enabled) {
        try { makeAdapter(true, false); assert(false); } catch (const std::invalid_argument&) {}
    }
    assert(pinRuntimeAdapterForProcess(makeAdapter(enabled)));
    assert(!pinRuntimeAdapterForProcess(makeAdapter(false)));
    assert(!pinIngressForProcess(makeIngress()));
    const auto callbacks = runtimeVideoCallbacks();
    assert(callbacks.initialize == impulse_carplay_runtime_initialize &&
        callbacks.processData == impulse_carplay_runtime_data);
    if (!enabled) {
        assert(impulse_carplay_runtime_adopt_principal(stream(mainKey), 110, string(mainUuid)) != 0);
        assert(reads == 0 && originalInitializes == 0);
    }

    if (enabled) {
        assert(impulse_carplay_runtime_session(nullptr, string(secondaryUuid), 111, nullptr) != 0);
        appendFails = true;
        const auto failed = impulse_carplay_runtime_displays(&error);
        assert(error == 0 && (failed->displays == std::vector<int>{110, 77}));
        assert(impulse_carplay_runtime_advertisement_status() == -123);
        assert(alive == 1);
        release(failed);
        appendFails = false;
    }

    for (int query = 0; query < 3; ++query) {
        const auto priorReleaseCount = released.size();
        const auto value = impulse_carplay_runtime_displays(&error);
        assert(value && error == 0 && alive == 1);
        assert((value->displays == (enabled ? std::vector<int>{110, 77, 111} : std::vector<int>{110, 77})));
        assert(released.size() == priorReleaseCount + (enabled ? 1 : 0));
        release(value);
        assert(alive == 0);
    }
    assert(enabled || (reads == 0 && copies == 0 && decoderCreates == 0));
    auto queryRepeatedly = [enabled] {
        for (int query = 0; query < 12; ++query) {
            observed_abi::Status localError = 0;
            const auto value = impulse_carplay_runtime_displays(&localError);
            assert(value && localError == 0);
            assert((value->displays == (enabled ? std::vector<int>{110, 77, 111} : std::vector<int>{110, 77})));
            release(value);
        }
    };
    auto firstQueries = std::async(std::launch::async, queryRepeatedly);
    auto secondQueries = std::async(std::launch::async, queryRepeatedly);
    firstQueries.get();
    secondQueries.get();
    assert(alive == 0 && decoderCreates == 0);
    assert(impulse_carplay_runtime_session(nullptr, enabled ? string(mainUuid) : nullptr, 110, nullptr) == 0);
    const auto initializeBeforeSecondary = originalInitializes;
    assert(impulse_carplay_runtime_session(nullptr, enabled ? string(secondaryUuid) : nullptr, 111, nullptr) == 0);
    assert(originalInitializes == initializeBeforeSecondary + (enabled ? 0 : 1));
    assert(decoderCreates == (enabled ? 1 : 0));
    assert(send(stream(mainKey)) == 37);
    assert(send(stream(secondaryKey)) == (enabled ? 0 : 37));

    getterStatus = -41;
    const auto errored = impulse_carplay_runtime_displays(&error);
    assert(error == -41 && (errored->displays == std::vector<int>{110, 77}));
    release(errored);
    getterNull = true;
    assert(!impulse_carplay_runtime_displays(&error) && error == -41);
    getterNull = false;
    getterStatus = 0;

    impulse_carplay_runtime_close();
    const auto afterClose = impulse_carplay_runtime_displays(&error);
    assert(error == 0 && (afterClose->displays == std::vector<int>{110, 77}));
    release(afterClose);
    assert(decoderStops == (enabled ? 1 : 0));
    boundaryFaultsKeepPrincipalAvailable();
    assert(alive == 0);
}

void legacyAndContestedRegistration(bool contested) {
    bool legacy = false;
    bool runtime = false;
    if (contested) {
        std::promise<void> start;
        auto ready = start.get_future().share();
        auto legacyFuture = std::async(std::launch::async, [&] { ready.wait(); return pinIngressForProcess(makeIngress()); });
        auto runtimeFuture = std::async(std::launch::async, [&] { ready.wait(); return pinRuntimeAdapterForProcess(makeAdapter(false)); });
        start.set_value();
        legacy = legacyFuture.get();
        runtime = runtimeFuture.get();
    } else {
        legacy = pinIngressForProcess(makeIngress());
        runtime = pinRuntimeAdapterForProcess(makeAdapter(false));
    }
    assert(legacy != runtime);
    assert(!pinIngressForProcess(makeIngress()));
    assert(!pinRuntimeAdapterForProcess(makeAdapter(false)));
    if (runtime) { impulse_carplay_runtime_close(); }
}

void adoptExistingPrincipalWithoutReinitialization() {
    assert(pinRuntimeAdapterForProcess(makeAdapter(true)));
    assert(send(stream(mainKey)) != 0);
    assert(impulse_carplay_runtime_adopt_principal(stream(secondaryKey), 111, string(secondaryUuid)) != 0);
    assert(impulse_carplay_runtime_adopt_principal(stream(mainKey), 110, string(secondaryUuid)) != 0);
    assert(impulse_carplay_runtime_adopt_principal(nullptr, 110, string(mainUuid)) != 0);
    assert(impulse_carplay_runtime_adopt_principal(stream(mainKey), 110, string(mainUuid)) == 0);
    assert(originalInitializes == 0 && send(stream(mainKey)) == 37);
    assert(impulse_carplay_runtime_adopt_principal(stream(mainKey), 110, string(mainUuid)) != 0);
    observed_abi::Status error = 0;
    auto displays = impulse_carplay_runtime_displays(&error);
    assert(displays && error == 0 && displays->displays.back() == 111);
    release(displays);
    assert(impulse_carplay_runtime_session(nullptr, string(secondaryUuid), 111, nullptr) == 0);
    assert(originalInitializes == 0 && decoderCreates == 1);
    assert(send(stream(secondaryKey)) == 0);
    impulse_carplay_runtime_close();
    assert(decoderStops == 1 && send(stream(mainKey)) == 37 && originalInitializes == 0);
    impulse_carplay_runtime_finalize(stream(mainKey));
    assert(alive == 0);
}

void closingDuringGetterPreservesOriginalResponse() {
    assert(pinRuntimeAdapterForProcess(makeAdapter(true)));
    Gate gate;
    appendGate.store(&gate);
    auto query = std::async(std::launch::async, [] {
        observed_abi::Status error = -1;
        auto result = impulse_carplay_runtime_displays(&error);
        assert(error == 0);
        return result;
    });
    gate.wait();
    impulse_carplay_runtime_close();
    appendGate.store(nullptr);
    gate.release();
    const auto result = query.get();
    assert(result && (result->displays == std::vector<int>{110, 77}));
    assert(alive == 1 && copies == 1 && decoderCreates == 0);
    assert(impulse_carplay_runtime_advertisement_status() == VideoBridge::notReady);
    release(result);
    assert(alive == 0);
}

struct InstallationState {
    std::mutex callbacks;
    std::shared_ptr<void> identity = std::make_shared<int>(7);
    int preparations = 0;
    int publications = 0;
    int currencyChecks = 0;
    int staleAt = 0;
    bool invalid = false;
    bool publishFails = false;
    bool captureThrows = false;
    bool quiescent = false;
    bool active = false;
    observed_abi::VideoCallbacks video = originals();
};

class FakeQuiescence final : public QuiescentHookTransaction {
public:
    FakeQuiescence(std::shared_ptr<InstallationState> state, std::unique_lock<std::mutex> lock)
        : state_(std::move(state)), lock_(std::move(lock)) { state_->quiescent = true; }
    ~FakeQuiescence() override { state_->quiescent = false; }
    LivePrincipal capturePrincipal() override {
        assert(state_->quiescent);
        if (state_->captureThrows) { throw std::runtime_error("capture"); }
        return {stream(mainKey), string(mainUuid), 110, state_->invalid ? 0U : 9U, state_->identity};
    }
    bool isCurrent(const LivePrincipal& principal) noexcept override {
        assert(state_->quiescent && principal.epoch == 9);
        return ++state_->currencyChecks != state_->staleAt;
    }
    bool publish(observed_abi::VideoCallbacks video, OriginalStartSession session,
        OriginalGetDisplays displays) noexcept override {
        assert(state_->quiescent && state_->currencyChecks == 2);
        assert(session == impulse_carplay_runtime_session && displays == impulse_carplay_runtime_displays);
        assert(originalInitializes == 0 && send(stream(mainKey)) == 37);
        ++state_->publications;
        if (state_->publishFails) { return false; }
        state_->video = video;
        state_->active = true;
        return true;
    }
private:
    const std::shared_ptr<InstallationState> state_;
    std::unique_lock<std::mutex> lock_;
};

class FakePrepared final : public PreparedHooks {
public:
    explicit FakePrepared(std::shared_ptr<InstallationState> state) : state_(std::move(state)) {}
    std::unique_ptr<OemRuntimeAdapter> makeAdapter() override { return ::makeAdapter(true); }
    std::unique_ptr<QuiescentHookTransaction> tryQuiesce() override {
        std::unique_lock<std::mutex> lock(state_->callbacks, std::try_to_lock);
        if (!lock.owns_lock()) { return {}; }
        state_->currencyChecks = 0;
        return std::make_unique<FakeQuiescence>(state_, std::move(lock));
    }
private:
    const std::shared_ptr<InstallationState> state_;
};

void coordinatedInstallation(bool failPublication, bool contested) {
    auto state = std::make_shared<InstallationState>();
    std::weak_ptr<void> pinnedIdentity = state->identity;
    std::weak_ptr<PreparedHooks> pinnedPrepared;
    Gate preparationGate;
    bool pausePreparation = false;
    HookInstaller installer([&](const HookAuthorization& authorization) -> std::shared_ptr<PreparedHooks> {
        ++state->preparations;
        if (pausePreparation) { preparationGate.block(); }
        if (authorization.callerUid != 7 || authorization.requestId != 9 || authorization.token[0] != 42) {
            return {};
        }
        auto prepared = std::make_shared<FakePrepared>(state);
        pinnedPrepared = prepared;
        return prepared;
    });
    HookAuthorization authorization;
    assert(installer.install(authorization) == HookInstallResult::disabled && state->preparations == 0);
    authorization.enabled = true;
    assert(installer.install(authorization) == HookInstallResult::rejected);
    authorization.callerUid = 7;
    authorization.requestId = 9;
    authorization.token[0] = 42;
    {
        std::unique_lock<std::mutex> activeCallback(state->callbacks);
        auto installation = std::async(std::launch::async, [&] { return installer.install(authorization); });
        assert(installation.get() == HookInstallResult::noQuiescence);
    }
    state->invalid = true;
    assert(installer.install(authorization) == HookInstallResult::invalidPrincipal);
    state->invalid = false;
    state->staleAt = 1;
    assert(installer.install(authorization) == HookInstallResult::stalePrincipal);
    state->staleAt = 2;
    assert(installer.install(authorization) == HookInstallResult::stalePrincipal);
    state->staleAt = 0;
    state->captureThrows = true;
    assert(installer.install(authorization) == HookInstallResult::fault && !state->quiescent);
    state->captureThrows = false;
    assert(state->publications == 0 && originalInitializes == 0 && decoderCreates == 0);
    if (contested) {
        assert(pinIngressForProcess(makeIngress()));
        assert(installer.install(authorization) == HookInstallResult::registrationUnavailable);
        assert(state->publications == 0 && !state->active && !state->quiescent);
        assert(pinnedPrepared.expired());
        assert(state->video.processData(stream(mainKey), packet.data(), packet.size(), 1234,
            nullptr, nullptr, nullptr) == 37);
        return;
    }
    pausePreparation = true;
    state->publishFails = failPublication;
    auto installation = std::async(std::launch::async, [&] { return installer.install(authorization); });
    preparationGate.wait();
    assert(installer.install(authorization) == HookInstallResult::busy);
    assert(state->video.processData(stream(mainKey), packet.data(), packet.size(), 1234,
        nullptr, nullptr, nullptr) == 37);
    preparationGate.release();
    const auto expected = failPublication ? HookInstallResult::publicationFailedPinned : HookInstallResult::installed;
    assert(installation.get() == expected);
    assert(state->publications == 1 && state->active == !failPublication && !state->quiescent);
    assert(installer.install(authorization) == (failPublication ? expected : HookInstallResult::alreadyInstalled));
    assert(!pinnedPrepared.expired());
    state->identity.reset();
    assert(!pinnedIdentity.expired());
    impulse_carplay_runtime_close();
    assert(state->video.processData(stream(mainKey), packet.data(), packet.size(), 1234,
        nullptr, nullptr, nullptr) == 37);
    assert(originalInitializes == 0 && decoderCreates == 0 && alive == 0);
}

std::atomic<Gate*> dispatchDataGate{nullptr};
std::array<int, 9> replacementCalls{};
std::shared_ptr<CoordinatedDispatch> nestedDispatch;

DispatchTargets originalDispatchTargets() {
    auto video = originals();
    video.processData = [](observed_abi::Stream target, const std::uint8_t* bytes, std::size_t length,
        std::uint64_t ticks, observed_abi::Dictionary options, observed_abi::Completion completion, void* context) {
        if (auto gate = dispatchDataGate.load()) { gate->block(); }
        if (nestedDispatch) {
            assert(!nestedDispatch->tryFreeze());
            assert(nestedDispatch->start(target, 110, string(mainUuid)) == 0);
        }
        return originals().processData(target, bytes, length, ticks, options, completion, context);
    };
    return {video, [](void*, observed_abi::String, std::uint32_t, void*) { return 12; }, getter};
}

DispatchTargets replacementDispatchTargets() {
    return {{
        [](observed_abi::Stream) { ++replacementCalls[0]; return 51; },
        [](observed_abi::Stream) { ++replacementCalls[1]; },
        [](const void*, std::uint32_t, std::uint32_t) { ++replacementCalls[2]; },
        [](observed_abi::Stream, std::uint32_t, observed_abi::String) { ++replacementCalls[3]; return 52; },
        [](observed_abi::Stream) { ++replacementCalls[4]; },
        [](const void*, const std::uint8_t*, std::size_t) { ++replacementCalls[5]; return 53; },
        [](observed_abi::Stream, const std::uint8_t*, std::size_t, std::uint64_t,
            observed_abi::Dictionary, observed_abi::Completion, void*) { ++replacementCalls[6]; return 54; }
    }, [](void*, observed_abi::String, std::uint32_t, void*) { ++replacementCalls[7]; return 55; },
    [](observed_abi::Status* error) { ++replacementCalls[8]; *error = 0; return array({111}); }};
}

observed_abi::Status dispatchFrame(CoordinatedDispatch& dispatch) {
    return dispatch.data(stream(mainKey), packet.data(), packet.size(), 1234, nullptr, nullptr, nullptr);
}

void dispatchQuiescence() {
    auto dispatch = std::make_shared<CoordinatedDispatch>(originalDispatchTargets());
    auto invalid = originalDispatchTargets();
    invalid.session = nullptr;
    bool constructorRejected = false;
    try { CoordinatedDispatch rejected(invalid); }
    catch (const std::invalid_argument&) { constructorRejected = true; }
    assert(constructorRejected);
    nestedDispatch = dispatch;
    assert(dispatchFrame(*dispatch) == 37);
    nestedDispatch.reset();
    Gate blocker;
    dispatchDataGate.store(&blocker);
    auto activeFrame = std::async(std::launch::async, [&] { return dispatchFrame(*dispatch); });
    blocker.wait();
    assert(!dispatch->tryFreeze());
    assert(dispatch->start(stream(mainKey), 110, string(mainUuid)) == 0);
    dispatchDataGate.store(nullptr);
    blocker.release();
    assert(activeFrame.get() == 37);
    frameThrows = true;
    bool frameFailed = false;
    try { dispatchFrame(*dispatch); } catch (const std::runtime_error&) { frameFailed = true; }
    assert(frameFailed);
    frameThrows = false;
    {
        auto rollback = dispatch->tryFreeze();
        assert(rollback && !dispatch->tryFreeze());
        assert(!rollback->replace(invalid));
        bool reentryRejected = false;
        try { dispatchFrame(*dispatch); } catch (const std::logic_error&) { reentryRejected = true; }
        assert(reentryRejected);
    }
    assert(dispatchFrame(*dispatch) == 37);
    auto freeze = dispatch->tryFreeze();
    assert(freeze);
    std::promise<void> incoming;
    auto waiting = std::async(std::launch::async, [&] {
        incoming.set_value();
        assert(dispatch->initialize(stream(mainKey)) == 51);
        dispatch->finalize(stream(mainKey));
        dispatch->dimensions(stream(mainKey), 1280, 720);
        assert(dispatch->start(stream(mainKey), 110, string(mainUuid)) == 52);
        dispatch->stop(stream(mainKey));
        assert(dispatch->avcc(stream(mainKey), configuration.data(), configuration.size()) == 53);
        assert(dispatchFrame(*dispatch) == 54);
        assert(dispatch->session(nullptr, string(mainUuid), 110, nullptr) == 55);
        observed_abi::Status error = -1;
        auto result = dispatch->displays(&error);
        assert(error == 0 && result->displays == std::vector<int>{111});
        release(result);
    });
    incoming.get_future().wait();
    assert(waiting.wait_for(std::chrono::milliseconds(10)) == std::future_status::timeout);
    assert(freeze->replace(replacementDispatchTargets()));
    assert(!freeze->replace(originalDispatchTargets()));
    assert(waiting.wait_for(std::chrono::milliseconds(10)) == std::future_status::timeout);
    freeze.reset();
    waiting.get();
    for (const auto count : replacementCalls) { assert(count == 1); }
    assert(alive == 0);
}

void dispatchInstallerIntegration() {
    auto dispatch = std::make_shared<CoordinatedDispatch>(originalDispatchTargets());
    std::weak_ptr<void> pinnedNative;
    auto failures = std::make_shared<std::array<bool, 2>>(std::array<bool, 2>{true, false});
    HookInstaller installer([&](const HookAuthorization&) {
        auto nativeOwners = std::make_shared<int>(1);
        pinnedNative = nativeOwners;
        return std::make_shared<DispatchPreparedHooks>(dispatch, [] { return makeAdapter(true); },
            [failures] {
                if ((*failures)[0]) { throw std::runtime_error("live capture failed"); }
                return LivePrincipal{stream(mainKey), string(mainUuid), 110, 7, std::make_shared<int>(9)};
            },
            [failures](const LivePrincipal& principal) {
                if ((*failures)[1]) { throw std::runtime_error("epoch verification failed"); }
                return principal.epoch == 7;
            }, nativeOwners);
    });
    HookAuthorization authorization;
    authorization.enabled = true;
    assert(dispatchFrame(*dispatch) == 37);
    assert(installer.install(authorization) == HookInstallResult::fault);
    assert(dispatchFrame(*dispatch) == 37);
    (*failures)[0] = false;
    (*failures)[1] = true;
    assert(installer.install(authorization) == HookInstallResult::stalePrincipal);
    assert(dispatchFrame(*dispatch) == 37);
    (*failures)[1] = false;
    assert(installer.install(authorization) == HookInstallResult::installed);
    assert(!pinnedNative.expired());
    assert(dispatchFrame(*dispatch) == 37 && originalInitializes == 0);
    observed_abi::Status error = -1;
    auto result = dispatch->displays(&error);
    assert(error == 0 && result->displays.back() == 111);
    release(result);
    impulse_carplay_runtime_close();
    assert(dispatchFrame(*dispatch) == 37 && originalInitializes == 0 && alive == 0);
}
}

int main(int count, char** arguments) {
    assert(count == 2);
    const std::string mode = arguments[1];
    if (mode == "off" || mode == "enabled") { adapterMode(mode == "enabled"); }
    else if (mode == "adopt") { adoptExistingPrincipalWithoutReinitialization(); }
    else if (mode == "close-getter") { closingDuringGetterPreservesOriginalResponse(); }
    else if (mode == "install" || mode == "install-fail" || mode == "install-contested") {
        coordinatedInstallation(mode == "install-fail", mode == "install-contested");
    }
    else if (mode == "dispatch") { dispatchQuiescence(); }
    else if (mode == "dispatch-install") { dispatchInstallerIntegration(); }
    else { assert(mode == "legacy" || mode == "race"); legacyAndContestedRegistration(mode == "race"); }
    std::cout << "OEM runtime " << mode << ": C boundary, getter ownership/repeat and registration checks passed\n";
}
