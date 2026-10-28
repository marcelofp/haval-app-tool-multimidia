package br.com.redesurftank.havalshisuku.experimental

/** Short-lived observation, not an admission token. Never renewed from resident phase alone. */
internal object CarPlayColdAttachProof {
    fun fresh(observedAtMs: Long, nowMs: Long): Boolean =
        observedAtMs > 0 && nowMs - observedAtMs in 0..2_500

    fun recover(previous: CarPlaySafetyPublicationGate.Key?, current: CarPlaySafetyPublicationGate.Key,
        observedAtMs: Long): Long = if (previous == current) observedAtMs else 0

    fun observe(previousMs: Long, startedAtMs: Long, finishedAtMs: Long,
        sameIdentity: Boolean, cold: Boolean?, bridgeMatches: Boolean,
        visualKnown: Boolean, carPlayVisible: Boolean, androidAutoVisible: Boolean): Long {
        if (!sameIdentity || cold == null || !bridgeMatches || !visualKnown ||
            carPlayVisible || androidAutoVisible || !fresh(startedAtMs, finishedAtMs)) return 0
        // Modules can load after gate release. Keep only the already-issued bounded
        // lease during that transition; a warm observation must never extend it.
        return if (cold) startedAtMs else previousMs.takeIf { fresh(it, finishedAtMs) } ?: 0
    }
}
