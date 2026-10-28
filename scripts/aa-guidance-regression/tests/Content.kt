package android.content
import android.os.IBinder
class ComponentName
class Intent(action: String) { fun setPackage(name: String) {} }
interface ServiceConnection {
 fun onServiceConnected(name: ComponentName?, service: IBinder?)
 fun onServiceDisconnected(name: ComponentName?)
}
open class Context {
 val applicationContext get() = this
 fun bindService(intent: Intent, connection: ServiceConnection, flags: Int) = false
 companion object { const val BIND_AUTO_CREATE = 1 }
}
