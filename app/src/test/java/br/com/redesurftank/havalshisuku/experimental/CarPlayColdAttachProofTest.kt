package br.com.redesurftank.havalshisuku.experimental

import org.junit.Assert.*
import org.junit.Test

class CarPlayColdAttachProofTest {
    private val key = CarPlaySafetyPublicationGate.Key("boot", 42, 99, "ticket")
    private fun sample(previous: Long = 1000, start: Long = 2000, end: Long = 2001,
        same: Boolean = true, cold: Boolean? = true, bridge: Boolean = true,
        known: Boolean = true, cp: Boolean = false, aa: Boolean = false) =
        CarPlayColdAttachProof.observe(previous, start, end, same, cold, bridge, known, cp, aa)

    @Test fun recoveryPreservesOnlyExactAttempt() {
        assertEquals(1000L, CarPlayColdAttachProof.recover(key, key, 1000))
        for (other in listOf(key.copy(boot="new"), key.copy(pid=43), key.copy(start=100), key.copy(ticket="new"))) {
            assertEquals(0L, CarPlayColdAttachProof.recover(key, other, 1000))
        }
        assertEquals(0L, CarPlayColdAttachProof.recover(null, key, 1000))
    }
    @Test fun leaseExpiresAndRejectsClockRegression() {
        assertTrue(CarPlayColdAttachProof.fresh(1000, 3500))
        assertFalse(CarPlayColdAttachProof.fresh(1000, 3501))
        assertFalse(CarPlayColdAttachProof.fresh(1000, 999))
        assertFalse(CarPlayColdAttachProof.fresh(0, 1))
    }
    @Test fun onlyFreshColdObservationRenews() {
        assertEquals(2000L, sample())
        assertEquals(1000L, sample(cold=false))
        assertEquals(0L, sample(cold=false, end=3501))
        assertEquals(0L, sample(end=4501))
        assertEquals(0L, sample(end=1999))
    }
    @Test fun unsafeUnknownOrForeignObservationRevokes() {
        assertEquals(0L, sample(same=false))
        assertEquals(0L, sample(cold=null))
        assertEquals(0L, sample(bridge=false))
        assertEquals(0L, sample(known=false))
        assertEquals(0L, sample(cp=true))
        assertEquals(0L, sample(aa=true))
    }
    @Test fun coldRecoveryPublicationAndCancellation() {
        val gate = CarPlaySafetyPublicationGate()
        val recovered = CarPlayColdAttachProof.recover(key, key, 1000)
        assertEquals(CarPlaySafetyPublicationGate.Decision.ALLOW,
            gate.decide(key,key,CarPlayColdAttachProof.fresh(recovered,1500),true,true,"preparing"))
        val renewed = sample(recovered, start=3000, end=3001)
        assertEquals(CarPlaySafetyPublicationGate.Decision.ALLOW,
            gate.decide(key,key,CarPlayColdAttachProof.fresh(renewed,4000),true,true,"preparing"))
        assertEquals(CarPlaySafetyPublicationGate.Decision.DENY,
            gate.decide(key,key,CarPlayColdAttachProof.fresh(renewed,5501),true,true,"preparing"))
        gate.cancel(key.ticket)
        assertEquals(CarPlaySafetyPublicationGate.Decision.DENY,
            gate.decide(key,key,true,true,true,"preparing"))
        assertEquals(CarPlaySafetyPublicationGate.Decision.DROP,
            gate.decide(key,key.copy(ticket="new"),true,true,true,"preparing"))
    }
}
