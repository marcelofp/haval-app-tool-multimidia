#pragma once

#include "video_bridge.h"

namespace carplay_experiment {

class DecoderLease final : public std::enable_shared_from_this<DecoderLease> {
public:
    using Factory = std::function<std::unique_ptr<SecondaryDecoder>()>;
    explicit DecoderLease(Factory factory);
    ~DecoderLease();
    std::unique_ptr<SecondaryDecoder> claim();
    void revoke() noexcept;

private:
    class Client;
    void retireLocked() noexcept;
    std::mutex mutex_;
    Factory factory_;
    std::unique_ptr<SecondaryDecoder> decoder_;
    bool claimed_ = false;
    bool revoked_ = false;
};

}
