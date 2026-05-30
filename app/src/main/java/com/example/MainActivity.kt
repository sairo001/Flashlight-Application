package com.example

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        FlashlightController.initialize(this)

        // Automatically launch background notification service
        val serviceIntent = Intent(this, FlashlightService::class.java).apply {
            action = FlashlightService.ACTION_START
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent)
            } else {
                startService(serviceIntent)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        setContent {
            MyApplicationTheme {
                Scaffold(
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("main_screen")
                ) { innerPadding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color(0xFF000000))
                            .padding(innerPadding)
                    ) {
                        FlashlightScreen(modifier = Modifier.fillMaxSize())
                    }
                }
            }
        }
    }
}

@SuppressLint("InlinedApi")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FlashlightScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val scrollState = rememberScrollState()

    // Collect states from controller
    val isTorchOn by FlashlightController.isTorchOn.collectAsState()
    val isStrobeActive by FlashlightController.isStrobeActive.collectAsState()
    val isSosActive by FlashlightController.isSosActive.collectAsState()

    // Screen brightness control (range 0.1f to 1.0f)
    var brightnessValue by remember { mutableStateOf(0.85f) }

    // Setup real window brightness controller
    val activity = context as? ComponentActivity
    LaunchedEffect(brightnessValue) {
        activity?.window?.attributes?.let { layoutParams ->
            layoutParams.screenBrightness = brightnessValue
            activity.window.attributes = layoutParams
        }
    }

    // Modal Sheet or dialog for instructions/details
    var showSettingsDialog by remember { mutableStateOf(false) }

    // Notification permission tracking
    var hasNotificationPermission by remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            mutableStateOf(
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
            )
        } else {
            mutableStateOf(true)
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { isGranted ->
            hasNotificationPermission = isGranted
            if (isGranted) {
                val serviceIntent = Intent(context, FlashlightService::class.java).apply {
                    action = FlashlightService.ACTION_START
                }
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        context.startForegroundService(serviceIntent)
                    } else {
                        context.startService(serviceIntent)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    )

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasNotificationPermission) {
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    // Interactive button animation states
    val scaleMultiplier = remember { Animatable(1f) }
    LaunchedEffect(isTorchOn) {
        scaleMultiplier.animateTo(
            targetValue = 0.95f,
            animationSpec = tween(50, easing = LinearEasing)
        )
        scaleMultiplier.animateTo(
            targetValue = 1f,
            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium)
        )
    }

    val themeColor = Color(0xFFFFD600) // Precise FFD600 Yellow
    val isAnyBeamActive = isTorchOn || isStrobeActive || isSosActive

    // Glow scale for atmospheric background blur
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseGlowScale by infiniteTransition.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_glow"
    )

    Column(
        modifier = modifier
            .background(Color(0xFF000000))
            .padding(horizontal = 24.dp)
            .verticalScroll(scrollState),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // App Layout: Title Header (matches Tailwind Mockup)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp, bottom = 24.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Logo Icon "L" with yellow backdrop
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(themeColor),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "L",
                        color = Color.Black,
                        fontWeight = FontWeight.Black,
                        fontSize = 18.sp
                    )
                }
                Column {
                    Text(
                        text = "Lumina",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp
                        ),
                        color = Color.White
                    )
                    Text(
                        text = "PRESTIGE EDITION",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Medium,
                            letterSpacing = 1.2.sp
                        ),
                        color = themeColor.copy(alpha = 0.8f)
                    )
                }
            }

            // High contrast action settings/about button
            IconButton(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    showSettingsDialog = true
                },
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF18181B))
                    .testTag("app_info_settings_btn")
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "App Settings Info",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        // Center visual sector containing main toggle
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(290.dp),
            contentAlignment = Alignment.Center
        ) {
            // Atmospheric Glow Effect mimicking Tailwinds opacity-10 blur-3xl when active
            if (isAnyBeamActive) {
                val alphaAnimation by infiniteTransition.animateFloat(
                    initialValue = 0.08f,
                    targetValue = 0.16f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(1500, easing = EaseInOutSine),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "alpha"
                )
                Canvas(
                    modifier = Modifier
                        .size(300.dp)
                        .graphicsLayer {
                            scaleX = pulseGlowScale
                            scaleY = pulseGlowScale
                        }
                ) {
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                themeColor.copy(alpha = alphaAnimation),
                                themeColor.copy(alpha = 0.03f),
                                Color.Transparent
                            )
                        ),
                        radius = size.width / 1.7f
                    )
                }
            }

            // Power tactile switch circle (w-48 h-48 Tailwind match)
            val animatedBorderColor by animateColorAsState(
                targetValue = if (isAnyBeamActive) themeColor else Color(0xFF27272A),
                animationSpec = tween(250), label = "border_color"
            )
            val animatedButtonBgColor by animateColorAsState(
                targetValue = if (isAnyBeamActive) Color(0xFF18181B) else Color(0xFF0C0C0E),
                animationSpec = tween(250), label = "btn_bg_color"
            )

            Box(
                modifier = Modifier
                    .size(192.dp)
                    .graphicsLayer {
                        scaleX = scaleMultiplier.value
                        scaleY = scaleMultiplier.value
                    }
                    .clip(CircleShape)
                    .background(animatedButtonBgColor)
                    .border(4.dp, animatedBorderColor, CircleShape)
                    .clickable {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        FlashlightController.toggleTorch()
                    }
                    .testTag("flashlight_toggle_button"),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "⏻",
                        fontSize = 58.sp,
                        fontWeight = FontWeight.Light,
                        color = if (isAnyBeamActive) themeColor else Color(0xFF3F3F46),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.offset(y = -4.dp)
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = if (isAnyBeamActive) "Flashlight ON" else "Flashlight OFF",
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp,
                            fontSize = 11.sp
                        ),
                        color = if (isAnyBeamActive) themeColor else Color(0xFFA1A1AA)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Secondary interactive widgets (Brightness, Strobe & SOS modes)
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Brightness Control Widget
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF18181B).copy(alpha = 0.5f)),
                border = BorderStroke(1.dp, Color(0xFF27272A)),
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(20.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Brightness",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                            color = Color(0xFFA1A1AA)
                        )
                        Text(
                            text = "${(brightnessValue * 100).toInt()}%",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                            color = themeColor
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Slider(
                        value = brightnessValue,
                        onValueChange = { brightnessValue = it },
                        valueRange = 0.1f..1.0f,
                        colors = SliderDefaults.colors(
                            activeTrackColor = themeColor,
                            inactiveTrackColor = Color(0xFF27272A),
                            thumbColor = themeColor
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            // Mode Modules Grid (Strobe & SOS modes)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // STROBE BUTTON
                val strobeBorder by animateColorAsState(
                    targetValue = if (isStrobeActive) themeColor else Color(0xFF27272A),
                    label = "strobe_border"
                )
                val strobeBg by animateColorAsState(
                    targetValue = if (isStrobeActive) Color(0xFF1D1B06) else Color(0xFF0F0F11),
                    label = "strobe_bg"
                )
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(24.dp))
                        .background(strobeBg)
                        .border(1.dp, strobeBorder, RoundedCornerShape(24.dp))
                        .clickable {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            FlashlightController.toggleStrobe()
                        }
                        .padding(vertical = 18.dp, horizontal = 12.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "⚡",
                        fontSize = 18.sp,
                        modifier = Modifier.padding(end = 6.dp)
                    )
                    Text(
                        text = "STROBE",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        ),
                        color = if (isStrobeActive) themeColor else Color.White
                    )
                }

                // SOS BUTTON
                val sosBorder by animateColorAsState(
                    targetValue = if (isSosActive) themeColor else Color(0xFF27272A),
                    label = "sos_border"
                )
                val sosBg by animateColorAsState(
                    targetValue = if (isSosActive) Color(0xFF1D1B06) else Color(0xFF0F0F11),
                    label = "sos_bg"
                )
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(24.dp))
                        .background(sosBg)
                        .border(1.dp, sosBorder, RoundedCornerShape(24.dp))
                        .clickable {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            FlashlightController.toggleSos()
                        }
                        .padding(vertical = 18.dp, horizontal = 12.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "🆘",
                        fontSize = 18.sp,
                        modifier = Modifier.padding(end = 6.dp)
                    )
                    Text(
                        text = "SOS MODE",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        ),
                        color = if (isSosActive) themeColor else Color.White
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(28.dp))

        // Permanent Notification Tray Preview (Footer area)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp)
        ) {
            Text(
                text = "NOTIFICATION PREVIEW",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.2.sp,
                    fontSize = 9.sp
                ),
                color = Color(0xFF55555C),
                modifier = Modifier.padding(start = 6.dp, bottom = 8.dp)
            )

            // Preview Container Card
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF18181A)),
                border = BorderStroke(1.dp, Color(0xFF27272A)),
                shape = RoundedCornerShape(28.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        // Small icon tray preview
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(Color(0xFF27272A))
                                .border(1.dp, Color(0xFF3F3F46), RoundedCornerShape(14.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(text = "🔦", fontSize = 18.sp)
                        }
                        Column {
                            Text(
                                text = "Flashlight Utility",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                color = Color.White
                            )
                            Text(
                                text = if (isAnyBeamActive) "Flashlight is ACTIVE" else "Ready for quick access",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isAnyBeamActive) themeColor else Color(0xFFFFD600)
                            )
                        }
                    }

                    // Test toggle button
                    Button(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            FlashlightController.toggleTorch()
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = themeColor,
                            contentColor = Color.Black
                        ),
                        shape = RoundedCornerShape(14.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                        modifier = Modifier.height(38.dp)
                    ) {
                        Text(
                            text = "TOGGLE",
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                letterSpacing = 0.5.sp
                            )
                        )
                    }
                }
            }

            // Notification permission warning banner if disabled
            if (!hasNotificationPermission) {
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xFF271415))
                        .border(1.dp, Color(0xFF7E2A2F), RoundedCornerShape(16.dp))
                        .clickable {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            } else {
                                val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                                    putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                                }
                                context.startActivity(intent)
                            }
                        }
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Notifications,
                        contentDescription = "Permission Warn Icon",
                        tint = Color(0xFFFF5252),
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Tray toggle requires notification approval. Click to enable.",
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                        color = Color(0xFFFF8A8A)
                    )
                }
            } else {
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF34C759))
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Notification active",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF34C759)
                    )
                }
            }

            // Small accent spacer simulating Android gesture bar
            Spacer(modifier = Modifier.height(16.dp))
            Box(
                modifier = Modifier
                    .width(120.dp)
                    .height(5.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF27272A))
                    .align(Alignment.CenterHorizontally)
            )
        }
    }

    // Material 3 Custom Info/Settings Dialog
    if (showSettingsDialog) {
        AlertDialog(
            onDismissRequest = { showSettingsDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = "Lumina Premium Info", fontWeight = FontWeight.Bold, color = themeColor)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "This flashlight features high-contrast nighttime visibility styled under the Vibrant Palette aesthetic.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White
                    )
                    HorizontalDivider(color = Color(0xFF27272A))
                    Text(
                        text = "• Persistent Quick Toggle: Keep flashlight controls right in your notification shade for rapid utility.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFA1A1AA)
                    )
                    Text(
                        text = "• Brightness: Dynamic sliding controller works synchronously to adjust window screen glare.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFA1A1AA)
                    )
                    Text(
                        text = "• Strobe Mode (⚡): Rapid flashing mode utilizing optimized background loops.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFA1A1AA)
                    )
                    Text(
                        text = "• SOS Emergency (🆘): Self-signaling emergency loop pulsing standard International Morse Code.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFA1A1AA)
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showSettingsDialog = false }) {
                    Text("DISMISS", fontWeight = FontWeight.Bold, color = themeColor)
                }
            },
            containerColor = Color(0xFF18181B),
            shape = RoundedCornerShape(28.dp)
        )
    }
}
