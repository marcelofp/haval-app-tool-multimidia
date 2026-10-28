package android.os
object SystemClock { var now = 0L; fun elapsedRealtime() = now }
class Looper { companion object { fun getMainLooper() = Looper() } }
class Handler(looper: Looper) {
    fun postDelayed(r: Runnable, delay: Long): Boolean { jobs.add(SystemClock.now + delay to r); return true }
    fun removeCallbacks(r: Runnable) { jobs.removeAll { it.second === r } }
    companion object {
        private val jobs = mutableListOf<Pair<Long, Runnable>>()
        fun reset() { jobs.clear(); SystemClock.now = 0L }
        fun advance(to: Long) {
            require(to >= SystemClock.now)
            while (true) {
                val job = jobs.filter { it.first <= to }.minByOrNull { it.first } ?: break
                jobs.remove(job); SystemClock.now = job.first; job.second.run()
            }
            SystemClock.now = to
        }
    }
}
interface IInterface { fun asBinder(): IBinder }
interface IBinder {
    fun interface DeathRecipient { fun binderDied() }
    val isBinderAlive: Boolean get() = true
    fun linkToDeath(recipient: DeathRecipient, flags: Int) {}
    fun transact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean = true
}
open class Binder: IBinder {
    fun attachInterface(owner: IInterface, descriptor: String) {}
    open fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int) = false
    companion object { const val INTERFACE_TRANSACTION = 1598968902 }
}
class Parcel {
    private val values = mutableListOf<Any?>(); private var cursor = 0
    fun writeInt(v: Int) { values.add(v) }; fun readInt() = values[cursor++] as Int
    fun writeString(v: String?) { values.add(v) }; fun readString() = values[cursor++] as String?
    fun readLong() = values[cursor++] as Long
    fun readByteArray(v: ByteArray) { error("Unused Parcel path") }
    fun writeInterfaceToken(v: String) {}; fun enforceInterface(v: String) {}
    fun writeStrongBinder(v: IBinder) {}; fun recycle() {}
    companion object { fun obtain() = Parcel() }
}
