import android.os.*
import br.com.redesurftank.havalshisuku.services.AndroidAutoNavigationMonitor as Monitor
import br.com.redesurftank.havalshisuku.managers.AndroidAutoClusterController as Controller
import br.com.redesurftank.havalshisuku.managers.AndroidAutoSessionTelemetry as Session
import br.com.redesurftank.havalshisuku.navigation.CarPlayHudPolicy
private val callback = Monitor.javaClass.getDeclaredField("callback").apply { isAccessible = true }.get(Monitor) as Binder
private fun state(value: Int) {
 val p = Parcel.obtain(); p.writeInt(value)
 check(callback.onTransact(7, p, null, 0))
}
private fun guidance() {
 val p = Parcel.obtain()
 // Real monitor code 10 parser: present, no destinations, one present route step.
 p.writeInt(1); p.writeInt(0); p.writeInt(1); p.writeInt(1); p.writeInt(1)
 p.writeString("Synthetic road"); p.writeInt(2); p.writeInt(0); p.writeInt(0)
 // Empty cue and lane lists; no OEM bytes or route data.
 p.writeInt(0); p.writeInt(0)
 check(callback.onTransact(10, p, null, 0))
}
private fun reset() {
 state(2); Handler.reset(); Controller.policy = CarPlayHudPolicy(); Controller.updates.clear()
}
private fun active() = Controller.policy.snapshot(SystemClock.now).active
private var passed = 0
private fun test(name: String, body: () -> Unit) { reset(); body(); passed++; println("PASS $name") }
fun main() {
 test("silence 10-59 seconds preserves guidance") {
  guidance()
  for (second in 10L..59L) { Handler.advance(second * 1000); check(active()) }
  Handler.advance(59999); check(active())
 }
 test("deadline exactly 60 seconds clears and stays inactive") {
  guidance(); Handler.advance(60000); check(!active())
  check(!Controller.updates.last().active)
  Handler.advance(120000); check(!active())
 }
 test("new navigation transaction rearms deadline") {
  guidance(); Handler.advance(59000); guidance()
  Handler.advance(60000); check(active())
  Handler.advance(118999); check(active())
  Handler.advance(119000); check(!active())
 }
 test("live link and passive availability do not prevent expiry") {
  guidance()
  for (second in 1L..60L) {
   Controller.policy.onAndroidAutoAvailability(true, second * 1000)
   Controller.policy.onPassiveAndroidAutoAvailability(true, second * 1000)
   Handler.advance(second * 1000)
  }
  check(!active()); check(!Controller.policy.snapshot(60000, true).active)
 }
 test("snapshot and diagnostic cache reads do not renew monitor lease") {
  guidance()
  for (ms in 1L..60000L) {
   Handler.advance(ms); Controller.policy.snapshot(ms); Controller.policy.diagnostic(ms)
  }
  check(!active())
 }
 test("ordinary AA works without enabled CarPlay capture or observer") {
  check(!Controller.policy.enabled)
  guidance(); check(active()); Handler.advance(59999); check(active())
  Handler.advance(60000); check(!active())
 }
 test("isolated policy intentionally has no AA expiry - monitor is required") {
  Controller.policy.onAndroidAuto(br.com.redesurftank.havalshisuku.managers.AndroidAutoNavigationTelemetry.Directions(true))
  Handler.advance(180001); check(active())
 }
 test("navigation evidence expires after five seconds despite repeated polls") {
  val at = 1000L
  for (now in 1000L..6000L) check(Session.statusWithNavigationEvidence(null, Session.hasRecentNavigationEvidence(at, now)) == 3)
  check(Session.statusWithNavigationEvidence(null, Session.hasRecentNavigationEvidence(at, 6001)) == null)
 }
 test("DCM cached availability does not renew monitor guidance") {
  guidance()
  for (second in 1L..60L) {
   val status = Session.statusForPoll(null, true, true)
   Controller.policy.onAndroidAutoAvailability(Session.isLinkActive(status), second * 1000)
   Handler.advance(second * 1000)
  }
  check(!active())
 }
 test("Claude case1 exact times preserve street and distance outside capture") {
  val d = br.com.redesurftank.havalshisuku.managers.AndroidAutoNavigationTelemetry.Directions(true, street = "Av. X", distance = "300 m")
  Controller.policy.onAndroidAuto(d)
  for (t in listOf(0L, 4999L, 20000L)) {
   val actual = Controller.policy.snapshot(t)
   check(actual.active && actual.street == d.street && actual.distance == d.distance)
  }
 }
 test("Claude case2 exact boundary with live host and link") {
  guidance()
  Handler.advance(59999)
  Controller.policy.onAndroidAutoAvailability(true, 59999)
  check(Controller.policy.snapshot(59999, true).active)
  Handler.advance(60000)
  Controller.policy.onAndroidAutoAvailability(true, 60000)
  check(!Controller.policy.snapshot(60000, true).active)
 }
 test("Claude case3 corrected DCM poll at30s leaves60s guidance deadline") {
  guidance(); Handler.advance(30000)
  // Old navigation alone CANNOT promote status at30s. DCM recent is a separate input.
  check(!Session.hasRecentNavigationEvidence(1, 30000))
  val link = Session.statusForPoll(null, true, true)
  check(Session.isLinkActive(link))
  Controller.policy.onAndroidAutoAvailability(true, 30000)
  Handler.advance(59999); check(active())
  Handler.advance(60000); check(!active())
  Handler.advance(90000); check(!active())
 }
 println("RESULT: $passed scenarios passed; full monitor, real policy/telemetry; Android collaborators simulated")
}
