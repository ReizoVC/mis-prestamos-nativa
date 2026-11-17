package pe.reizo.prestamos

import android.graphics.Color
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.DisposableEffect
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import pe.reizo.prestamos.ui.App
import pe.reizo.prestamos.ui.AppVM
import pe.reizo.prestamos.ui.TemaApp
import pe.reizo.prestamos.ui.TemaPrestamos

private const val CINCO_MINUTOS = 5 * 60_000L

class MainActivity : FragmentActivity() {
    private val vm: AppVM by viewModels()
    private var salioEn = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            val oscuro = when (vm.tema) {
                TemaApp.CLARO -> false
                TemaApp.OSCURO -> true
                TemaApp.SISTEMA -> isSystemInDarkTheme()
            }
            // Los íconos de la barra de arriba (hora, batería) siguen al tema de la app, no al del celular
            DisposableEffect(oscuro) {
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { oscuro },
                    navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { oscuro },
                )
                onDispose {}
            }
            TemaPrestamos(oscuro, vm.letra.escala) { App(vm, ::pedirDesbloqueo) }
        }
    }

    override fun onStart() {
        super.onStart()
        val ahora = System.currentTimeMillis()
        // Tras 5 minutos fuera, se vuelve a pedir la huella o la clave
        if (salioEn != 0L && ahora - salioEn > CINCO_MINUTOS && vm.bloqueoActivo) vm.bloqueado = true
        vm.ahora = ahora
        vm.nube.pedir(0)
        vm.buscarActualizacion()
    }

    override fun onStop() {
        super.onStop()
        salioEn = System.currentTimeMillis()
    }

    /** Usa el bloqueo del propio celular: huella, rostro, patrón o clave. */
    private fun pedirDesbloqueo() {
        val aviso = BiometricPrompt(
            this, ContextCompat.getMainExecutor(this),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    vm.bloqueado = false
                }
            },
        )
        aviso.authenticate(
            BiometricPrompt.PromptInfo.Builder()
                .setTitle("Mis Préstamos")
                .setSubtitle("Use su huella o la clave de este celular")
                .setAllowedAuthenticators(AppVM.FORMAS_DE_DESBLOQUEO)
                .build(),
        )
    }
}
