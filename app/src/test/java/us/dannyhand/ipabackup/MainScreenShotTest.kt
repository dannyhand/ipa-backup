package us.dannyhand.ipabackup

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.CleaningServices
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.PowerSettingsNew
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material.icons.outlined.Contacts
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.InstallMobile
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Podcasts
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import us.dannyhand.ipabackup.ui.Feature
import us.dannyhand.ipabackup.ui.IpaBackupTheme
import us.dannyhand.ipabackup.ui.MainScreen
import us.dannyhand.ipabackup.ui.UiState

/** Renders the main screen to PNGs (app/build/outputs/roborazzi) so the design can be checked without a device. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
class MainScreenShotTest {
    private val features = listOf(
        Feature("Back up apps", Icons.Outlined.Apps, "Apps") {},
        Feature("Restore", Icons.Outlined.InstallMobile, "Apps") {},
        Feature("Uninstall", Icons.Outlined.DeleteSweep, "Apps") {},
        Feature("Music", Icons.Outlined.LibraryMusic, "Media") {},
        Feature("Photos", Icons.Outlined.PhotoLibrary, "Media") {},
        Feature("Other media", Icons.Outlined.Podcasts, "Media") {},
        Feature("Voice Memos", Icons.Outlined.Mic, "Media") {},
        Feature("Books", Icons.Outlined.MenuBook, "Media") {},
        Feature("Messages", Icons.Outlined.Forum, "Personal data", needsJailbreak = true) {},
        Feature("Contacts, Notes & more", Icons.Outlined.Contacts, "Personal data", needsJailbreak = true) {},
        Feature("Cydia packages", Icons.Outlined.Extension, "Personal data", needsJailbreak = true) {},
        Feature("Device info", Icons.Outlined.Info, "Device") {},
        Feature("Power", Icons.Outlined.PowerSettingsNew, "Device") {},
        Feature("Home screen", Icons.Outlined.Dashboard, "Device") {},
        Feature("System log", Icons.Outlined.Terminal, "Device") {},
        Feature("Crash reports", Icons.Outlined.BugReport, "Device") {},
        Feature("Files", Icons.Outlined.FolderOpen, "Tools") {},
        Feature("Clean up", Icons.Outlined.CleaningServices, "Tools") {},
        Feature("Certificates", Icons.Outlined.VerifiedUser, "Tools") {},
    )
    private val notes = mapOf("Personal data" to "needs a jailbroken device with AFC2")

    private fun shot(name: String, state: UiState) = captureRoboImage("build/outputs/roborazzi/$name.png") {
        IpaBackupTheme { MainScreen(state, features, notes, {}, {}, {}) }
    }

    @Test
    fun idle() = shot("main-idle", UiState().apply {
        device = "Danny Hand Jr · iOS 6.1.6"
        log += listOf("IPA Backup v2.0", "", "Connecting…", "Danny Hand Jr, iOS 6.1.6", "52 user apps installed.")
    })

    @Test
    fun working() = shot("main-working", UiState().apply {
        device = "Danny Hand Jr · iOS 6.1.6"
        busy = true; connected = true; task = "Backing up 3 app(s) to Download/IPA Backup/"
        stopVisible = true; stopLabel = "Stop"
        log += listOf("Backing up 3 app(s) to Download/IPA Backup/", "", "[1/3] Fastar! 1.0.4", "  archiving… 50% (ArchivingApplication)",
            "  saved Download/IPA Backup/Fastar! 1.0.4 (com.catinabox.wfar).ipa (13.5 MB) ✓", "[2/3] Field Goal 1.8.1", "  FAILED: example error",
            "  copying 7.4 MB / 18.3 MB")
    })

    @Test
    @Config(qualifiers = "+night")
    fun dark() = shot("main-dark", UiState().apply { log += "IPA Backup v2.0" })

    @Test
    @Config(qualifiers = "w411dp-h1800dp-xxhdpi")
    fun fullPage() = shot("main-full", UiState().apply {
        device = "Danny Hand Jr · iOS 6.1.6"
        log += listOf("IPA Backup v2.0", "", "Loading the music library…", "6635 songs (42347.9 MB) in the library.")
    })
}
