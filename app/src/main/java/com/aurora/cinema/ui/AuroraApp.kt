package com.aurora.cinema.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aurora.cinema.app.AppContainer
import com.aurora.cinema.core.nativebridge.NativeCore
import com.aurora.cinema.settings.AppSettings
import com.aurora.cinema.settings.AppSettingsRepository
import com.aurora.cinema.ui.theme.AuroraTheme
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

@Composable
fun AuroraApp(appContainer: AppContainer) {
    val settings by appContainer.settingsRepository.settings.collectAsStateWithLifecycle(
        initialValue = AppSettings(),
    )
    val scope = rememberCoroutineScope()

    if (!settings.firstRunAcknowledged) {
        SafetyAcknowledgementScreen(
            onContinue = {
                scope.launch {
                    appContainer.settingsRepository.setFirstRunAcknowledged(true)
                }
            },
        )
        return
    }

    AuroraShell(
        appContainer = appContainer,
        settings = settings,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AuroraShell(
    appContainer: AppContainer,
    settings: AppSettings,
) {
    var selectedScreen by rememberSaveable { mutableStateOf(AppScreen.Library) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(selectedScreen.title) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                ),
            )
        },
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                AppScreen.entries.forEach { screen ->
                    NavigationBarItem(
                        selected = selectedScreen == screen,
                        onClick = { selectedScreen = screen },
                        label = { Text(screen.navLabel) },
                        icon = { Text(screen.icon) },
                    )
                }
            }
        },
    ) { innerPadding ->
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            color = MaterialTheme.colorScheme.background,
        ) {
            when (selectedScreen) {
                AppScreen.Library -> LibraryScreen(onOpenDetails = { selectedScreen = AppScreen.VideoDetails })
                AppScreen.Player -> PlayerScreen()
                AppScreen.VideoDetails -> VideoDetailsScreen()
                AppScreen.Settings -> SettingsScreen(
                    settings = settings,
                    repository = appContainer.settingsRepository,
                )
                AppScreen.Calibration -> CalibrationScreen()
                AppScreen.About -> AboutScreen(packageName = appContainer.applicationContext.packageName)
            }
        }
    }
}

@Composable
private fun SafetyAcknowledgementScreen(onContinue: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        ScreenColumn(verticalArrangement = Arrangement.Center) {
            Text(
                text = "Aurora",
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Use seated in a safe space. Start with short sessions, keep brightness comfortable, and stop if you feel discomfort.",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.78f),
            )
            Spacer(modifier = Modifier.height(28.dp))
            Button(onClick = onContinue) {
                Text("I understand")
            }
        }
    }
}

@Composable
private fun LibraryScreen(onOpenDetails: () -> Unit) {
    ScreenColumn {
        ScreenHeader(
            title = "Library",
            subtitle = "Your offline cinema library will appear here once local import is added.",
        )
        ActionPanel(
            title = "No videos yet",
            body = "Video import will use Android's file and folder picker.",
            actionLabel = "View details placeholder",
            onAction = onOpenDetails,
        )
    }
}

@Composable
private fun PlayerScreen() {
    ScreenColumn {
        ScreenHeader(
            title = "Player",
            subtitle = "Normal phone playback and VR entry controls will live here.",
        )
        StatusRow(label = "Mode", value = "Phone playback placeholder")
        StatusRow(label = "VR entry", value = "Not connected yet")
    }
}

@Composable
private fun VideoDetailsScreen() {
    ScreenColumn {
        ScreenHeader(
            title = "Video Details",
            subtitle = "Selected video metadata, codec warnings, and resume state will appear here.",
        )
        StatusRow(label = "Title", value = "No video selected")
        StatusRow(label = "Aspect ratio", value = "Source")
        StatusRow(label = "Playback position", value = "0:00")
    }
}

@Composable
private fun SettingsScreen(
    settings: AppSettings,
    repository: AppSettingsRepository,
) {
    val scope = rememberCoroutineScope()

    ScreenColumn {
        ScreenHeader(
            title = "Settings",
            subtitle = "Persistent viewing preferences for comfort and cinema setup.",
        )
        SettingSwitchRow(
            label = "Comfort mode",
            body = "Keep conservative defaults for brightness, motion, and session comfort.",
            checked = settings.comfortModeEnabled,
            onCheckedChange = { enabled ->
                scope.launch { repository.setComfortModeEnabled(enabled) }
            },
        )
        Spacer(modifier = Modifier.height(20.dp))
        Text(
            text = "Default screen distance: ${settings.defaultScreenDistanceMeters.toInt()} m",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Slider(
            value = settings.defaultScreenDistanceMeters,
            onValueChange = { distance ->
                scope.launch { repository.setDefaultScreenDistanceMeters(distance) }
            },
            valueRange = 3.0f..20.0f,
        )
    }
}

@Composable
private fun CalibrationScreen() {
    ScreenColumn {
        ScreenHeader(
            title = "Headset Calibration",
            subtitle = "Headset profile, IPD, FOV, and lens distortion controls will be added here.",
        )
        StatusRow(label = "Profile", value = "Default mobile headset")
        StatusRow(label = "IPD", value = "Not calibrated")
        StatusRow(label = "Distortion", value = "Not calibrated")
    }
}

@Composable
private fun AboutScreen(packageName: String) {
    ScreenColumn {
        ScreenHeader(
            title = "About / Diagnostics",
            subtitle = "App and runtime diagnostics for the Aurora cinema engine.",
        )
        StatusRow(label = "Package", value = packageName)
        StatusRow(label = "Native core", value = NativeCore.engineName())
        StatusRow(label = "App shell", value = "Ready")
    }
}

@Composable
private fun ScreenColumn(
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = verticalArrangement,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(
            modifier = Modifier.widthIn(max = 720.dp),
            content = content,
        )
    }
}

@Composable
private fun ScreenHeader(title: String, subtitle: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.headlineMedium,
        fontWeight = FontWeight.Bold,
    )
    Spacer(modifier = Modifier.height(8.dp))
    Text(
        text = subtitle,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.72f),
    )
    Spacer(modifier = Modifier.height(24.dp))
}

@Composable
private fun ActionPanel(
    title: String,
    body: String,
    actionLabel: String,
    onAction: () -> Unit,
) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.SemiBold,
    )
    Spacer(modifier = Modifier.height(8.dp))
    Text(
        text = body,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.72f),
    )
    Spacer(modifier = Modifier.height(16.dp))
    Button(onClick = onAction) {
        Text(actionLabel)
    }
}

@Composable
private fun SettingSwitchRow(
    label: String,
    body: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.68f),
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
        )
    }
}

@Composable
private fun StatusRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.64f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
        )
    }
    Spacer(modifier = Modifier.height(12.dp))
    HorizontalDivider(color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.08f))
    Spacer(modifier = Modifier.height(12.dp))
}

private enum class AppScreen(
    val title: String,
    val navLabel: String,
    val icon: String,
) {
    Library("Library", "Library", "L"),
    Player("Player", "Player", "P"),
    VideoDetails("Video Details", "Details", "D"),
    Settings("Settings", "Settings", "S"),
    Calibration("Headset Calibration", "Calibrate", "C"),
    About("About / Diagnostics", "About", "A"),
}

@Preview(showBackground = true)
@Composable
private fun AuroraAppPreview() {
    AuroraTheme {
        AuroraApp(
            appContainer = object : AppContainer {
                override val applicationContext = androidx.compose.ui.platform.LocalContext.current
                override val settingsRepository = object : AppSettingsRepository {
                    override val settings: Flow<AppSettings> = MutableStateFlow(
                        AppSettings(firstRunAcknowledged = true),
                    )

                    override suspend fun setFirstRunAcknowledged(acknowledged: Boolean) = Unit

                    override suspend fun setComfortModeEnabled(enabled: Boolean) = Unit

                    override suspend fun setDefaultScreenDistanceMeters(distanceMeters: Float) = Unit
                }
            },
        )
    }
}
