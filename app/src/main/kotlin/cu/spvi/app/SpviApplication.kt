package cu.spvi.app

import android.app.Application
import cu.spvi.app.notificacion.AvisoTurno
import cu.spvi.data.sync.ArranqueSync
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class SpviApplication : Application() {

    /** P29: aviso «Turno abierto» en la barra de estado mientras la app está en segundo plano. */
    @Inject lateinit var avisoTurno: AvisoTurno

    /** P37: servidor (principal con secundarias) o cliente automático (secundaria) mientras la app esté abierta. */
    @Inject lateinit var arranqueSync: ArranqueSync

    override fun onCreate() {
        super.onCreate()
        avisoTurno.iniciar(this)
        arranqueSync.iniciar()
    }
}
