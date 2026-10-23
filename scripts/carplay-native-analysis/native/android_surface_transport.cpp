#include "android_surface_transport.h"
#include "decoder_lease.h"

#include <android/native_window_jni.h>
#include <jni.h>
#include <limits>
#include <unordered_map>
#include <utility>

namespace carplay_experiment {
namespace {
using Window = std::shared_ptr<ANativeWindow>;
struct SurfaceRecord {
    Window window;
    std::shared_ptr<DecoderLease> lease;
};
std::mutex registryMutex;
std::unordered_map<std::int64_t, SurfaceRecord> registry;
std::int64_t nextHandle = 1;

std::int64_t importSurface(JNIEnv* environment, jobject surface) {
    if (!surface) { return 0; }
    Window window(ANativeWindow_fromSurface(environment, surface), [](ANativeWindow* value) {
        if (value) { ANativeWindow_release(value); }
    });
    if (!window || environment->ExceptionCheck()) { return 0; }
    std::lock_guard<std::mutex> lock(registryMutex);
    if (registry.size() >= 8 || nextHandle == std::numeric_limits<std::int64_t>::max()) { return 0; }
    const auto handle = nextHandle++;
    registry.emplace(handle, SurfaceRecord{std::move(window), nullptr});
    return handle;
}

void revokeSurface(std::int64_t handle) {
    SurfaceRecord retired;
    {
        std::lock_guard<std::mutex> lock(registryMutex);
        const auto iterator = registry.find(handle);
        if (iterator == registry.end()) { return; }
        retired = std::move(iterator->second);
        registry.erase(iterator);
    }
    if (retired.lease) { retired.lease->revoke(); }
}
}

VideoBridge::DecoderFactory surfaceDecoderFactory(
    std::int64_t handle, AndroidSecondaryDecoder::TickConverter converter) {
    if (!converter) { return {}; }
    std::lock_guard<std::mutex> lock(registryMutex);
    const auto iterator = registry.find(handle);
    if (iterator == registry.end() || iterator->second.lease) { return {}; }
    const auto window = iterator->second.window;
    auto lease = std::make_shared<DecoderLease>([window, converter = std::move(converter)] {
        return std::make_unique<AndroidSecondaryDecoder>(window.get(), converter);
    });
    VideoBridge::DecoderFactory factory = [lease](const StreamIdentity&) { return lease->claim(); };
    iterator->second.lease = std::move(lease);
    return factory;
}

}

extern "C" JNIEXPORT jlong JNICALL
Java_br_com_redesurftank_havalshisuku_experimental_CarPlaySurfaceEndpoint_nativeImportSurface(
    JNIEnv* environment, jclass, jobject surface) noexcept {
    try { return carplay_experiment::importSurface(environment, surface); }
    catch (...) { return 0; }
}

extern "C" JNIEXPORT void JNICALL
Java_br_com_redesurftank_havalshisuku_experimental_CarPlaySurfaceEndpoint_nativeRevokeSurface(
    JNIEnv*, jclass, jlong handle) noexcept {
    try { carplay_experiment::revokeSurface(handle); }
    catch (...) {
    }
}
