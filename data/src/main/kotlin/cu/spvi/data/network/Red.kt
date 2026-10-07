package cu.spvi.data.network

import java.io.IOException
import java.util.concurrent.TimeUnit
import okhttp3.Interceptor
import okhttp3.OkHttpClient

object Red {
    /** GitHub pide identificar la app en el User-Agent. No incluye datos del usuario ni del equipo. */
    const val USER_AGENT = "SPVI/0.27 (Android)"

    /**
     * Cliente único. Límites generosos como tope duro. Sin caché, sin cookies, sin reintentos silenciosos,
     * y solo https (además de `usesCleartextTraffic=false` en el manifiesto).
     */
    fun cliente(soloHttps: Boolean = true): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .callTimeout(30, TimeUnit.SECONDS)
        .retryOnConnectionFailure(false)
        .followSslRedirects(false) // nunca https → http
        .addInterceptor(Interceptor { ch ->
            ch.proceed(ch.request().newBuilder().header("User-Agent", USER_AGENT).header("Accept", "application/json").build())
        })
        .apply { if (soloHttps) { addInterceptor(SoloHttps); addNetworkInterceptor(SoloHttps) } } // app: antes de conectar; red: cada salto de una redirección
        .build()

    /** Defensa en profundidad: rechaza cualquier petición que no sea https (cada salto, también tras redirecciones). Desactivable solo en pruebas con MockWebServer. */
    object SoloHttps : Interceptor {
        override fun intercept(chain: Interceptor.Chain) =
            if (chain.request().url.isHttps) chain.proceed(chain.request()) else throw IOException("solo https")
    }
}
