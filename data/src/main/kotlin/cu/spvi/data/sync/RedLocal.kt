package cu.spvi.data.sync

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import dagger.hilt.android.qualifiers.ApplicationContext
import java.net.Inet4Address
import java.net.NetworkInterface
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull

/**
 * P37: red local sin permisos nuevos.
 *  - Direcciones: IPv4 privadas de las interfaces activas (wifi del local o zona wifi de este teléfono).
 *  - Descubrimiento: NSD (mDNS, `_spvi._tcp`) por si la principal cambió de dirección desde que se leyó el QR.
 * Ni NetworkInterface ni NsdManager necesitan permisos aparte de INTERNET (ya declarado).
 */
@Singleton
class RedLocal @Inject constructor(@ApplicationContext private val context: Context) {

    private val nsd: NsdManager? get() = context.getSystemService(Context.NSD_SERVICE) as? NsdManager
    private var registro: NsdManager.RegistrationListener? = null

    /** IPv4 privadas (192.168.x, 10.x, 172.16–31.x), sin la de loopback ni las de datos móviles (no son privadas). */
    fun direcciones(): List<String> = runCatching {
        NetworkInterface.getNetworkInterfaces()?.toList().orEmpty()
            .filter { it.isUp && !it.isLoopback }
            .flatMap { it.inetAddresses.toList() }
            .filterIsInstance<Inet4Address>()
            .filter { it.isSiteLocalAddress }
            .mapNotNull { it.hostAddress }
            .distinct()
    }.getOrDefault(emptyList())

    fun anunciar(negocioId: String, puerto: Int) {
        dejarDeAnunciar()
        val m = nsd ?: return
        val info = NsdServiceInfo().apply {
            serviceName = nombreServicio(negocioId)
            serviceType = TIPO
            port = puerto
        }
        val l = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(s: NsdServiceInfo) = Unit
            override fun onRegistrationFailed(s: NsdServiceInfo, e: Int) = Unit
            override fun onServiceUnregistered(s: NsdServiceInfo) = Unit
            override fun onUnregistrationFailed(s: NsdServiceInfo, e: Int) = Unit
        }
        runCatching { m.registerService(info, NsdManager.PROTOCOL_DNS_SD, l) }.onSuccess { registro = l }
    }

    fun dejarDeAnunciar() {
        val l = registro ?: return
        registro = null
        runCatching { nsd?.unregisterService(l) }
    }

    /** Busca la principal de [negocioId] en la red local. null si no aparece en [ms]. */
    suspend fun buscar(negocioId: String, ms: Long = 6_000): Pair<String, Int>? {
        val m = nsd ?: return null
        val nombre = nombreServicio(negocioId)
        var escucha: NsdManager.DiscoveryListener? = null
        val encontrado = (try {
            withTimeoutOrNull(ms) {
            suspendCancellableCoroutine<NsdServiceInfo?> { cont ->
                val l = object : NsdManager.DiscoveryListener {
                    override fun onDiscoveryStarted(t: String) = Unit
                    override fun onDiscoveryStopped(t: String) = Unit
                    override fun onStartDiscoveryFailed(t: String, e: Int) { if (cont.isActive) cont.resume(null) }
                    override fun onStopDiscoveryFailed(t: String, e: Int) = Unit
                    override fun onServiceLost(s: NsdServiceInfo) = Unit
                    override fun onServiceFound(s: NsdServiceInfo) {
                        if (s.serviceName == nombre && cont.isActive) cont.resume(s)
                    }
                }
                escucha = l
                runCatching { m.discoverServices(TIPO, NsdManager.PROTOCOL_DNS_SD, l) }.onFailure { escucha = null; if (cont.isActive) cont.resume(null) }
            }
            }
        } finally {
            escucha?.let { l -> runCatching { m.stopServiceDiscovery(l) } }
        }) ?: return null
        return withTimeoutOrNull(ms) { resolver(m, encontrado) }
    }

    @Suppress("DEPRECATION") // resolveService sigue funcionando en API 34+; la alternativa no existe en minSdk 26.
    private suspend fun resolver(m: NsdManager, s: NsdServiceInfo): Pair<String, Int>? = suspendCancellableCoroutine { cont ->
        runCatching {
            m.resolveService(s, object : NsdManager.ResolveListener {
                override fun onResolveFailed(s: NsdServiceInfo, e: Int) { if (cont.isActive) cont.resume(null) }
                override fun onServiceResolved(s: NsdServiceInfo) {
                    val host = s.host?.hostAddress
                    if (cont.isActive) cont.resume(host?.let { it to s.port })
                }
            })
        }.onFailure { if (cont.isActive) cont.resume(null) }
    }

    companion object {
        const val TIPO = "_spvi._tcp."
        fun nombreServicio(negocioId: String) = "SPVI-" + negocioId.take(12)
    }
}
