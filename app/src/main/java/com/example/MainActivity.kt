package com.example

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.text.TextUtils
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Accessibility
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.BatterySaver
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.VolumeDown
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.ui.theme.AmberAlert
import com.example.ui.theme.CyanAccent
import com.example.ui.theme.CyanPrimary
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.MintActive
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.SlateBorder
import com.example.ui.theme.SlateDarkBg
import com.example.ui.theme.SlateSurface
import com.example.ui.theme.SlateSurfaceCard
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                SnapCropMainScreen()
            }
        }
    }
}

/**
 * Main Onboarding and Setup UI
 */
@Composable
fun SnapCropMainScreen() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var isAccessibilityEnabled by remember { mutableStateOf(checkAccessibilityEnabled(context)) }
    var isBatteryIgnored by remember { mutableStateOf(checkBatteryIgnored(context)) }
    var triggerMode by remember { mutableStateOf(TriggerPreferenceManager.getTriggerMode(context)) }

    // Re-check service & battery permissions whenever user returns to the app
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                isAccessibilityEnabled = checkAccessibilityEnabled(context)
                isBatteryIgnored = checkBatteryIgnored(context)
                triggerMode = TriggerPreferenceManager.getTriggerMode(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .testTag("setup_screen"),
        contentWindowInsets = WindowInsets.safeDrawing,
        containerColor = SlateDarkBg
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            // App Bar / Title Header
            HeaderSection()

            // Hero Graphic & Overview Banner
            HeroBannerSection()

            // Real-Time Status Summary Strip
            StatusOverviewCard(
                isAccessibilityEnabled = isAccessibilityEnabled,
                isBatteryIgnored = isBatteryIgnored
            )

            // Trigger Mode Configuration (Volume Down default for 100% untouched OS touches)
            TriggerSelectionCard(
                currentMode = triggerMode,
                onModeSelected = { newMode ->
                    triggerMode = newMode
                    KeyCaptureService.updateTriggerMode(context, newMode)
                    val message = when (newMode) {
                        TriggerPreferenceManager.TriggerMode.VOLUME_DOWN_ONLY ->
                            "Volume Down trigger active (100% untouched OS screen touches)"
                        TriggerPreferenceManager.TriggerMode.THREE_FINGER_SWIPE ->
                            "3-Finger gesture overlay active"
                        TriggerPreferenceManager.TriggerMode.BOTH ->
                            "Both Volume Down and 3-Finger triggers active"
                    }
                    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                }
            )

            // Step 1: Accessibility Service Card
            SetupStepCard(
                stepNumber = "1",
                title = stringResource(R.string.step_accessibility_title),
                description = stringResource(R.string.step_accessibility_desc),
                icon = Icons.Default.Accessibility,
                isCompleted = isAccessibilityEnabled,
                statusText = if (isAccessibilityEnabled) stringResource(R.string.status_enabled) else stringResource(R.string.status_disabled),
                buttonText = stringResource(R.string.btn_open_accessibility),
                buttonTestTag = "btn_accessibility",
                onAction = { openAccessibilitySettings(context) }
            )

            // Step 2: Battery Optimization Card
            SetupStepCard(
                stepNumber = "2",
                title = stringResource(R.string.step_battery_title),
                description = stringResource(R.string.step_battery_desc),
                icon = Icons.Default.BatterySaver,
                isCompleted = isBatteryIgnored,
                statusText = if (isBatteryIgnored) stringResource(R.string.status_unrestricted) else stringResource(R.string.status_restricted),
                buttonText = stringResource(R.string.btn_battery_optimization),
                buttonTestTag = "btn_battery",
                onAction = { requestIgnoreBatteryOptimization(context) }
            )

            // Step 3: Key Hold Gesture Instructions
            GestureGuideCard()

            // Step 4: 3-Finger Swipe Gesture Instructions
            ThreeFingerGestureCard()

            // Emergency Safety Kill-Switch Card
            EmergencyKillSwitchCard()

            // Quick Test Button
            TestTriggerCard(
                onTestClick = {
                    val triggered = KeyCaptureService.triggerScreenshot(context)
                    if (!triggered) {
                        // Launch CropOverlayActivity directly with simulated preview for instant emulator testing
                        val intent = Intent(context, CropOverlayActivity::class.java).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(intent)
                    }
                }
            )

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun HeaderSection() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(SlateSurfaceCard)
                    .border(1.dp, SlateBorder, RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(id = R.drawable.ic_app_icon),
                    contentDescription = "App Icon",
                    modifier = Modifier.size(32.dp),
                    contentScale = ContentScale.Fit
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = stringResource(R.string.app_name),
                    color = TextPrimary,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = stringResource(R.string.hero_badge),
                    color = CyanAccent,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 0.5.sp
                )
            }
        }
    }
}

@Composable
private fun HeroBannerSection() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = SlateSurface),
        border = CardDefaults.outlinedCardBorder().copy(brush = Brush.linearGradient(listOf(SlateBorder, CyanPrimary.copy(alpha = 0.3f))))
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp)
                    .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
            ) {
                Image(
                    painter = painterResource(id = R.drawable.ic_hero_banner),
                    contentDescription = "Hero Banner",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Transparent, SlateSurface.copy(alpha = 0.9f))
                            )
                        )
                )
            }
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Instant Silent Screen Cropping",
                    color = TextPrimary,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.hero_subtitle),
                    color = TextSecondary,
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )
            }
        }
    }
}

@Composable
private fun StatusOverviewCard(
    isAccessibilityEnabled: Boolean,
    isBatteryIgnored: Boolean
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = SlateSurfaceCard),
        border = CardDefaults.outlinedCardBorder().copy(brush = Brush.horizontalGradient(listOf(SlateBorder, SlateBorder)))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            StatusPill(
                label = "Service",
                status = if (isAccessibilityEnabled) "ACTIVE" else "OFF",
                isActive = isAccessibilityEnabled,
                testTag = "status_accessibility"
            )
            Box(
                modifier = Modifier
                    .height(36.dp)
                    .width(1.dp)
                    .background(SlateBorder)
            )
            StatusPill(
                label = "Battery",
                status = if (isBatteryIgnored) "UNRESTRICTED" else "OPTIMIZED",
                isActive = isBatteryIgnored,
                testTag = "status_battery"
            )
            Box(
                modifier = Modifier
                    .height(36.dp)
                    .width(1.dp)
                    .background(SlateBorder)
            )
            StatusPill(
                label = "Triggers",
                status = "Vol & Swipe",
                isActive = true,
                testTag = "status_gesture"
            )
        }
    }
}

@Composable
private fun StatusPill(
    label: String,
    status: String,
    isActive: Boolean,
    testTag: String
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.testTag(testTag)
    ) {
        Text(
            text = label,
            color = TextMuted,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium
        )
        Spacer(modifier = Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(if (isActive) MintActive else AmberAlert)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = status,
                color = if (isActive) MintActive else AmberAlert,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun SetupStepCard(
    stepNumber: String,
    title: String,
    description: String,
    icon: ImageVector,
    isCompleted: Boolean,
    statusText: String,
    buttonText: String,
    buttonTestTag: String,
    onAction: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = SlateSurface),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = Brush.horizontalGradient(
                if (isCompleted) listOf(MintActive.copy(alpha = 0.5f), SlateBorder)
                else listOf(AmberAlert.copy(alpha = 0.5f), SlateBorder)
            )
        )
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isCompleted) MintActive.copy(alpha = 0.15f) else AmberAlert.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = if (isCompleted) MintActive else AmberAlert,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = title,
                        color = TextPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (isCompleted) MintActive.copy(alpha = 0.15f) else AmberAlert.copy(alpha = 0.15f),
                    border = CardDefaults.outlinedCardBorder().copy(
                        brush = Brush.horizontalGradient(
                            listOf(if (isCompleted) MintActive else AmberAlert, if (isCompleted) MintActive else AmberAlert)
                        )
                    )
                ) {
                    Text(
                        text = statusText,
                        color = if (isCompleted) MintActive else AmberAlert,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = description,
                color = TextSecondary,
                fontSize = 13.sp,
                lineHeight = 18.sp
            )

            Spacer(modifier = Modifier.height(14.dp))
            Button(
                onClick = onAction,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(buttonTestTag),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isCompleted) SlateSurfaceCard else CyanPrimary,
                    contentColor = if (isCompleted) CyanPrimary else SlateDarkBg
                ),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp)
            ) {
                Icon(
                    imageVector = if (isCompleted) Icons.Default.CheckCircle else Icons.Default.Settings,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = buttonText,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp
                )
            }
        }
    }
}

@Composable
private fun GestureGuideCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = SlateSurface),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = Brush.horizontalGradient(listOf(SlateBorder, CyanAccent.copy(alpha = 0.4f)))
        )
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(CyanPrimary.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.VolumeDown,
                        contentDescription = null,
                        tint = CyanPrimary,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = stringResource(R.string.step_gesture_title),
                    color = TextPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = stringResource(R.string.step_gesture_desc),
                color = TextSecondary,
                fontSize = 13.sp,
                lineHeight = 18.sp
            )

            Spacer(modifier = Modifier.height(12.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(SlateSurfaceCard)
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                GestureInstructionRow(num = "•", text = "Press & hold Volume Down for 700ms")
                GestureInstructionRow(num = "•", text = "Volume level popup is automatically suppressed")
                GestureInstructionRow(num = "•", text = "Full screen silent capture triggers instantly")
                GestureInstructionRow(num = "•", text = "Drag your finger across screen to select region")
                GestureInstructionRow(num = "•", text = "Release to crop and open native Share Sheet")
            }
        }
    }
}

@Composable
private fun ThreeFingerGestureCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = SlateSurface),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = Brush.horizontalGradient(listOf(SlateBorder, ElectricBlue.copy(alpha = 0.4f)))
        )
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(ElectricBlue.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.TouchApp,
                        contentDescription = null,
                        tint = ElectricBlue,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = stringResource(R.string.step_three_finger_title),
                    color = TextPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = stringResource(R.string.step_three_finger_desc),
                color = TextSecondary,
                fontSize = 13.sp,
                lineHeight = 18.sp
            )

            Spacer(modifier = Modifier.height(12.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(SlateSurfaceCard)
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                GestureInstructionRow(num = "•", text = "White translucent bar docked at the top-left screen edge")
                GestureInstructionRow(num = "•", text = "Tap the bar anytime to capture screen instantly")
                GestureInstructionRow(num = "•", text = "Drag vertically to reposition the bar along the left edge")
                GestureInstructionRow(num = "•", text = "FLAG_NOT_TOUCH_MODAL ensures 100% of touches outside the bar reach apps")
                GestureInstructionRow(num = "•", text = "Triple-press Volume Up anytime for emergency safety reset")
            }
        }
    }
}

@Composable
private fun GestureInstructionRow(num: String, text: String) {
    Row(verticalAlignment = Alignment.Top) {
        Text(
            text = num,
            color = CyanPrimary,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = text,
            color = TextSecondary,
            fontSize = 13.sp,
            lineHeight = 17.sp
        )
    }
}

@Composable
private fun EmergencyKillSwitchCard() {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("card_kill_switch"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = SlateSurfaceCard),
        border = BorderStroke(1.dp, Color(0xFFEF4444).copy(alpha = 0.4f))
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFFEF4444).copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Security,
                    contentDescription = null,
                    tint = Color(0xFFEF4444),
                    modifier = Modifier.size(22.dp)
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(R.string.emergency_kill_switch_title),
                        color = TextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = Color(0xFFEF4444).copy(alpha = 0.2f)
                    ) {
                        Text(
                            text = stringResource(R.string.emergency_kill_switch_badge),
                            color = Color(0xFFEF4444),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = stringResource(R.string.emergency_kill_switch_desc),
                    color = TextSecondary,
                    fontSize = 12.sp,
                    lineHeight = 16.sp
                )
            }
        }
    }
}

@Composable
private fun TestTriggerCard(onTestClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = SlateSurfaceCard),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = Brush.horizontalGradient(listOf(ElectricBlue.copy(alpha = 0.6f), CyanPrimary.copy(alpha = 0.6f)))
        )
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            Brush.linearGradient(listOf(CyanPrimary, ElectricBlue))
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Crop,
                        contentDescription = null,
                        tint = SlateDarkBg,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Spacer(modifier = Modifier.width(14.dp))
                Column {
                    Text(
                        text = "Interactive Test",
                        color = TextPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Try the cropping canvas & share sheet now",
                        color = TextSecondary,
                        fontSize = 12.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
            Button(
                onClick = onTestClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("btn_test_capture"),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = CyanPrimary,
                    contentColor = SlateDarkBg
                )
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.btn_test_capture),
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }
        }
    }
}

/**
 * Checks whether the KeyCaptureService AccessibilityService is enabled in Settings.
 */
private fun checkAccessibilityEnabled(context: Context): Boolean {
    if (KeyCaptureService.isServiceRunning()) return true

    val expectedServiceName = ComponentName(context, KeyCaptureService::class.java).flattenToString()
    val enabledServices = Settings.Secure.getString(
        context.contentResolver,
        Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
    ) ?: return false

    val splitter = TextUtils.SimpleStringSplitter(':')
    splitter.setString(enabledServices)
    while (splitter.hasNext()) {
        val component = splitter.next()
        if (component.equals(expectedServiceName, ignoreCase = true) ||
            component.contains(KeyCaptureService::class.java.simpleName, ignoreCase = true)
        ) {
            return true
        }
    }
    return false
}

/**
 * Checks if the application is ignoring battery optimizations.
 */
private fun checkBatteryIgnored(context: Context): Boolean {
    val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return false
    return powerManager.isIgnoringBatteryOptimizations(context.packageName)
}

/**
 * Launches the system Accessibility settings screen.
 */
private fun openAccessibilitySettings(context: Context) {
    try {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    } catch (e: Exception) {
        Toast.makeText(context, "Unable to open Accessibility Settings", Toast.LENGTH_SHORT).show()
    }
}

/**
 * Requests system battery optimization exemption.
 */
private fun requestIgnoreBatteryOptimization(context: Context) {
    try {
        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
            data = Uri.parse("package:${context.packageName}")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    } catch (e: Exception) {
        // Fallback to general battery optimization screen
        try {
            val fallback = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(fallback)
        } catch (e2: Exception) {
            Toast.makeText(context, "Unable to open Battery Settings", Toast.LENGTH_SHORT).show()
        }
    }
}

/**
 * TriggerSelectionCard
 *
 * Provides a clean switch/selector between Volume Down Long-Press (Default, 100% untouched OS touches)
 * and 3-Finger Drag Overlay.
 */
@Composable
fun TriggerSelectionCard(
    currentMode: TriggerPreferenceManager.TriggerMode,
    onModeSelected: (TriggerPreferenceManager.TriggerMode) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("card_trigger_selection"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = SlateSurface),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = Brush.horizontalGradient(listOf(CyanPrimary.copy(alpha = 0.5f), SlateBorder))
        )
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(CyanPrimary.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = null,
                        tint = CyanPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = stringResource(R.string.trigger_selection_title),
                        color = TextPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = stringResource(R.string.trigger_selection_desc),
                        color = TextSecondary,
                        fontSize = 12.sp,
                        lineHeight = 16.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Option 1: Volume Down Long Press (Default & Recommended)
            TriggerOptionItem(
                title = stringResource(R.string.trigger_mode_vol_down),
                badge = stringResource(R.string.trigger_mode_vol_down_badge),
                description = stringResource(R.string.trigger_mode_vol_down_desc),
                isSelected = currentMode == TriggerPreferenceManager.TriggerMode.VOLUME_DOWN_ONLY,
                testTag = "trigger_option_vol_down",
                onClick = { onModeSelected(TriggerPreferenceManager.TriggerMode.VOLUME_DOWN_ONLY) }
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Option 2: 3-Finger Drag Overlay
            TriggerOptionItem(
                title = stringResource(R.string.trigger_mode_three_finger),
                badge = null,
                description = stringResource(R.string.trigger_mode_three_finger_desc),
                isSelected = currentMode == TriggerPreferenceManager.TriggerMode.THREE_FINGER_SWIPE,
                testTag = "trigger_option_three_finger",
                onClick = { onModeSelected(TriggerPreferenceManager.TriggerMode.THREE_FINGER_SWIPE) }
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Option 3: Both Triggers Active
            TriggerOptionItem(
                title = stringResource(R.string.trigger_mode_both),
                badge = null,
                description = stringResource(R.string.trigger_mode_both_desc),
                isSelected = currentMode == TriggerPreferenceManager.TriggerMode.BOTH,
                testTag = "trigger_option_both",
                onClick = { onModeSelected(TriggerPreferenceManager.TriggerMode.BOTH) }
            )
        }
    }
}

@Composable
private fun TriggerOptionItem(
    title: String,
    badge: String?,
    description: String,
    isSelected: Boolean,
    testTag: String,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .testTag(testTag),
        shape = RoundedCornerShape(12.dp),
        color = if (isSelected) SlateSurfaceCard else SlateDarkBg.copy(alpha = 0.5f),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = if (isSelected) Brush.horizontalGradient(listOf(CyanPrimary, ElectricBlue))
            else Brush.horizontalGradient(listOf(SlateBorder, SlateBorder))
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            RadioButton(
                selected = isSelected,
                onClick = onClick,
                colors = RadioButtonDefaults.colors(
                    selectedColor = CyanPrimary,
                    unselectedColor = TextMuted
                ),
                modifier = Modifier.size(22.dp)
            )

            Spacer(modifier = Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = title,
                        color = if (isSelected) TextPrimary else TextSecondary,
                        fontSize = 13.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold
                    )
                    if (badge != null) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MintActive.copy(alpha = 0.15f)
                        ) {
                            Text(
                                text = badge,
                                color = MintActive,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(3.dp))

                Text(
                    text = description,
                    color = if (isSelected) TextSecondary else TextMuted,
                    fontSize = 11.sp,
                    lineHeight = 15.sp
                )
            }
        }
    }
}

/**
 * Preserved for tests and previews
 */
@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(text = "Hello $name!", modifier = modifier)
}

@Preview(showBackground = true)
@Composable
fun SnapCropMainScreenPreview() {
    MyApplicationTheme {
        SnapCropMainScreen()
    }
}
