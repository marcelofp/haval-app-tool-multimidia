#pragma once

#include "android_secondary_decoder.h"

namespace carplay_experiment {

VideoBridge::DecoderFactory surfaceDecoderFactory(
    std::int64_t handle, AndroidSecondaryDecoder::TickConverter converter);

}
