#include "decoder_lease.h"

#include <stdexcept>
#include <utility>

namespace carplay_experiment {

class DecoderLease::Client final : public SecondaryDecoder {
public:
    explicit Client(std::shared_ptr<DecoderLease> lease) : lease_(std::move(lease)) {}
    ~Client() override { stop(); }

    bool configure(std::uint32_t width, std::uint32_t height,
                   const std::vector<std::uint8_t>& avcc) override {
        std::lock_guard<std::mutex> lock(lease_->mutex_);
        if (lease_->revoked_ || !lease_->decoder_) { return false; }
        try {
            if (lease_->decoder_->configure(width, height, avcc)) { return true; }
        } catch (...) {
        }
        lease_->retireLocked();
        return false;
    }

    bool submit(const std::vector<std::uint8_t>& packet, std::uint64_t ticks) override {
        std::lock_guard<std::mutex> lock(lease_->mutex_);
        if (lease_->revoked_ || !lease_->decoder_) { return false; }
        try {
            if (lease_->decoder_->submit(packet, ticks)) { return true; }
        } catch (...) {
        }
        lease_->retireLocked();
        return false;
    }

    void stop() noexcept override { lease_->revoke(); }

private:
    const std::shared_ptr<DecoderLease> lease_;
};

DecoderLease::DecoderLease(Factory factory) : factory_(std::move(factory)) {
    if (!factory_) { throw std::invalid_argument("Decoder factory required"); }
}

DecoderLease::~DecoderLease() { revoke(); }

std::unique_ptr<SecondaryDecoder> DecoderLease::claim() {
    auto self = shared_from_this();
    std::lock_guard<std::mutex> lock(mutex_);
    if (revoked_ || claimed_) { return nullptr; }
    claimed_ = true;
    try {
        decoder_ = factory_();
        factory_ = {};
        if (decoder_) { return std::make_unique<Client>(std::move(self)); }
    } catch (...) {
        retireLocked();
        throw;
    }
    retireLocked();
    return nullptr;
}

void DecoderLease::retireLocked() noexcept {
    revoked_ = true;
    if (decoder_) {
        decoder_->stop();
        decoder_.reset();
    }
    factory_ = {};
}

void DecoderLease::revoke() noexcept {
    std::lock_guard<std::mutex> lock(mutex_);
    retireLocked();
}

}
