package com.sampcity.social

import android.graphics.Bitmap
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.material3.SnackbarHostState
import androidx.navigation.compose.rememberNavController
import com.sampcity.social.ui.LocalNavigator
import com.sampcity.social.ui.Navigator
import com.sampcity.social.ui.components.LocalSnack
import com.sampcity.social.ui.screens.*
import com.sampcity.social.ui.theme.SampTheme
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.io.FileOutputStream

/**
 * Dibuja las pantallas reales (Compose sobre Robolectric) con datos del servidor de pruebas sembrado y guarda una imagen de cada una.
 * Solo corre con -Dsampcity.shots=/carpeta (si no, se omite). Necesita el servidor en http://localhost:3000 con los datos de scripts/sembrar.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w390dp-h844dp-xhdpi")
class ScreenshotTest {
    @get:Rule val rule = createComposeRule()
    private val dir get() = System.getProperty("sampcity.shots").orEmpty()

    private fun login(user: String = "Ana_Lopez") = runBlocking {
        val app = SampApp.instance
        app.session.bootstrap(); app.repo.login(user, "secreta1", null); app.session.bootstrap()
    }

    private fun shot(name: String, waitFor: String? = null, dark: Boolean = true, content: @androidx.compose.runtime.Composable () -> Unit) {
        assumeTrue("sin carpeta de capturas", dir.isNotBlank())
        rule.setContent {
            val nav = Navigator(rememberNavController())
            CompositionLocalProvider(LocalNavigator provides nav, LocalSnack provides remember { SnackbarHostState() }) { SampTheme(if (dark) "dark" else "light") { content() } }
        }
        val t0 = System.currentTimeMillis()
        while (System.currentTimeMillis() - t0 < 12000) {
            rule.waitForIdle()
            if (waitFor == null || runCatching { rule.onNodeWithTextExists(waitFor) }.getOrDefault(false)) break
            Thread.sleep(250)
        }
        Thread.sleep(2500); rule.waitForIdle()      // da tiempo a las imágenes (Coil carga en segundo plano)
        val bmp = rule.onRoot().captureToImage().asAndroidBitmap()
        File(dir).mkdirs(); FileOutputStream(File(dir, "$name.png")).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun androidx.compose.ui.test.junit4.ComposeContentTestRule.onNodeWithTextExists(t: String) = onAllNodes(androidx.compose.ui.test.hasText(t, substring = true)).fetchSemanticsNodes().isNotEmpty()

    @Test fun login() = shot("01-login", "Iniciar sesión") { AuthScreen() }
    @Test fun inicio() { login(); shot("02-inicio", "Primer día de turno") { HomeScreen() } }
    @Test fun inicioClaro() { login(); shot("02b-inicio-claro", "Primer día de turno", dark = false) { HomeScreen() } }
    @Test fun publicacion() { login(); shot("03-publicacion", "Qué buen turno") { PostScreen(1) } }
    @Test fun perfil() { login(); shot("04-perfil", "Taxista") { ProfileScreen("Ana_Lopez", isTab = true) } }
    @Test fun mensajes() { login(); shot("05-mensajes", "Los taxistas") { ChatsScreen() } }
    @Test fun chat() { login(); shot("06-chat", "cómo vas") { ChatScreen(ChatTarget.Player("Beto_Ruiz")) } }
    @Test fun grupoChat() { login(); shot("06b-chat-grupo", "Buenas a todos") { ChatScreen(ChatTarget.Room(1)) } }
    @Test fun avisos() { login(); shot("07-avisos", "Bienvenidos") { NotificationsScreen() } }
    @Test fun amigos() { login(); shot("08-amigos", "Beto") { FriendsScreen() } }
    @Test fun buscar() { login(); shot("09-buscar", "taxi") { SearchScreen("") } }
    @Test fun marketplace() { login(); shot("10-marketplace", "Infernus") { MarketScreen() } }
    @Test fun grupos() { login(); shot("11-grupos", "Taxistas LS") { GroupsScreen() } }
    @Test fun eventos() { login(); shot("12-eventos", "Carrera nocturna") { EventsScreen() } }
    @Test fun configuracion() { login(); shot("13-configuracion", "Cuenta y seguridad") { SettingsScreen() } }
    @Test fun noticias() { login(); shot("14-noticias", "Bienvenidos a la red") { ContentScreen("news") } }
    @Test fun historia() { login(); shot("15-historia", null) { StoryScreen("Ana_Lopez") } }
    @Test fun panel() { login("Caro_Diaz"); shot("16-panel", "Resumen") { PanelScreen() } }
}
