package com.example.ui

import android.graphics.Bitmap
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.IosShare
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.gps.SpeedUnit
import com.example.track.ActivityMode
import com.example.track.SocialBgPreset
import com.example.track.SocialCardBitmapRenderer
import com.example.track.SocialCardConfig
import com.example.track.SocialOverlayBackdrop
import com.example.track.TrackSessionDetail
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SocialShareCardScreen(
    detail: TrackSessionDetail,
    speedUnit: SpeedUnit,
    onBack: () -> Unit,
    onSharePngUri: (Uri) -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onBack)

    val context = LocalContext.current
    val cs = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    val mode = detail.session.activityMode
    val isKart = mode == ActivityMode.KARTING
    val isOffroad = mode == ActivityMode.OFFROAD_CROSSING

    var config by remember(detail.session.id) {
        mutableStateOf(
            SocialCardConfig(
                useImportedPhoto = false,
                bgPreset = when (mode) {
                    ActivityMode.KARTING -> SocialBgPreset.OBSIDIAN
                    ActivityMode.HIKING -> SocialBgPreset.FOREST_GREEN
                    ActivityMode.OFFROAD_CROSSING -> SocialBgPreset.SUNSET_AMBER
                },
                useLightInk = true,
                overlayBackdrop = SocialOverlayBackdrop.SCRIM,
                showRoute = true,
                showTitle = true,
                showDistance = true,
                showDuration = true,
                showElevation = !isKart,
                showLaps = isKart,
                showSpeed = true,
                showHeartRate = detail.session.avgHeartRate != null,
                showPeakG = (isKart || isOffroad) && detail.session.peakTotalG > 0.05f,
                showClimbDifficulty = true,
                showSurfaceComplexity = mode.isElevationTrail,
                showBodyPerformance = mode == ActivityMode.HIKING || mode == ActivityMode.KARTING,
                showF1Sectors = isKart,
                showMiniProfile = true
            )
        )
    }

    var importedPhoto by remember { mutableStateOf<Bitmap?>(null) }
    var previewBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isExporting by remember { mutableStateOf(false) }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val decoded = withContext(Dispatchers.IO) {
                    SocialCardBitmapRenderer.decodeSampledPhoto(context, uri, maxDim = 1440)
                }
                if (decoded != null) {
                    importedPhoto = decoded
                    config = config.copy(
                        useImportedPhoto = true,
                        useLightInk = true
                    )
                } else {
                    Toast.makeText(
                        context,
                        context.getString(R.string.toast_social_photo_failed),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    // Re-render crisp WYSIWYG preview bitmap whenever options or photo change
    LaunchedEffect(detail, speedUnit, config, importedPhoto) {
        val rendered = withContext(Dispatchers.Default) {
            SocialCardBitmapRenderer.renderBitmap(
                detail = detail,
                speedUnit = speedUnit,
                config = config,
                photoBitmap = importedPhoto,
                targetWidth = 760
            )
        }
        previewBitmap = rendered
    }

    val filePrefix = when (mode) {
        ActivityMode.KARTING -> "karting_social"
        ActivityMode.HIKING -> "hiking_social"
        ActivityMode.OFFROAD_CROSSING -> "offroad_social"
    }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .testTag("social_share_card_screen"),
        containerColor = cs.background,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.social_title),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Medium)
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("btn_social_back")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.social_back_cd)
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = cs.background,
                    titleContentColor = cs.onSurface
                )
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 600.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Live WYSIWYG Preview over checkerboard (reveals true alpha when Transparent PNG is active)
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    color = cs.surfaceContainerLow
                ) {
                    val bmp = previewBitmap
                    val aspect = if (bmp != null && bmp.height > 0) {
                        (bmp.width.toFloat() / bmp.height.toFloat()).coerceIn(0.68f, 1.35f)
                    } else {
                        0.8f
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .aspectRatio(aspect)
                            .testTag("social_preview_container"),
                        contentAlignment = Alignment.Center
                    ) {
                        TransparencyCheckerboard(modifier = Modifier.fillMaxSize())
                        if (bmp != null) {
                            Image(
                                bitmap = bmp.asImageBitmap(),
                                contentDescription = stringResource(R.string.social_title),
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            CircularProgressIndicator(
                                modifier = Modifier.size(28.dp),
                                strokeWidth = 2.5.dp,
                                color = cs.primary
                            )
                        }
                    }
                }

                // Photo Import Controls (Zero-permission Android Photo Picker)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    FilledTonalButton(
                        onClick = {
                            photoPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("btn_social_import_photo"),
                        shape = RoundedCornerShape(50),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = cs.primaryContainer,
                            contentColor = cs.onPrimaryContainer
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.AddPhotoAlternate,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = stringResource(
                                if (importedPhoto != null) R.string.social_replace_photo else R.string.social_import_photo
                            ),
                            style = MaterialTheme.typography.labelLarge
                        )
                    }

                    if (importedPhoto != null) {
                        FilledTonalButton(
                            onClick = {
                                importedPhoto = null
                                config = config.copy(
                                    useImportedPhoto = false,
                                    useLightInk = config.bgPreset.defaultLightInk
                                )
                            },
                            modifier = Modifier
                                .height(48.dp)
                                .testTag("btn_social_clear_photo"),
                            shape = RoundedCornerShape(50),
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = cs.surfaceContainerHigh,
                                contentColor = cs.onSurfaceVariant
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = stringResource(R.string.social_clear_photo),
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    }
                }

                // Background Style & Color Selector (Photo / Transparent PNG / 6 Color Presets + Ink & Backdrop)
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(22.dp),
                    color = cs.surfaceContainerLow
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(R.string.social_section_background),
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                                color = cs.onSurface
                            )

                            // Light / Dark Ink Toggle (especially useful for Transparent PNG & Photos)
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(50))
                                    .background(cs.surfaceContainerHigh)
                                    .padding(2.dp)
                            ) {
                                CompactSegmentPill(
                                    label = stringResource(R.string.social_ink_light),
                                    selected = config.useLightInk,
                                    onClick = { config = config.copy(useLightInk = true) },
                                    tag = "btn_ink_light"
                                )
                                CompactSegmentPill(
                                    label = stringResource(R.string.social_ink_dark),
                                    selected = !config.useLightInk,
                                    onClick = { config = config.copy(useLightInk = false) },
                                    tag = "btn_ink_dark"
                                )
                            }
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (importedPhoto != null) {
                                BackgroundSwatchItem(
                                    label = stringResource(R.string.social_bg_photo),
                                    selected = config.useImportedPhoto,
                                    onClick = { config = config.copy(useImportedPhoto = true) },
                                    tag = "bg_swatch_photo"
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(cs.primaryContainer),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Image,
                                            contentDescription = null,
                                            tint = cs.onPrimaryContainer,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            }

                            SocialBgPreset.entries.forEach { preset ->
                                val isSelected = !config.useImportedPhoto && config.bgPreset == preset
                                BackgroundSwatchItem(
                                    label = if (preset.isTransparent) {
                                        stringResource(R.string.social_bg_transparent)
                                    } else {
                                        preset.labelZh
                                    },
                                    selected = isSelected,
                                    onClick = {
                                        config = config.copy(
                                            useImportedPhoto = false,
                                            bgPreset = preset,
                                            useLightInk = if (preset.isTransparent) config.useLightInk else preset.defaultLightInk
                                        )
                                    },
                                    tag = "bg_swatch_${preset.name.lowercase()}"
                                ) {
                                    if (preset.isTransparent) {
                                        TransparencyCheckerboard(modifier = Modifier.fillMaxSize(), cellDp = 5f)
                                    } else {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .background(
                                                    Brush.verticalGradient(
                                                        listOf(
                                                            Color(preset.topColorArgb),
                                                            Color(preset.bottomColorArgb)
                                                        )
                                                    )
                                                )
                                        )
                                    }
                                }
                            }
                        }

                        // Overlay Backdrop Style (Transparent floating / Scrim / Frosted card)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            SocialOverlayBackdrop.entries.forEach { backdrop ->
                                val selected = config.overlayBackdrop == backdrop
                                FilterChip(
                                    selected = selected,
                                    onClick = { config = config.copy(overlayBackdrop = backdrop) },
                                    label = {
                                        Text(
                                            text = backdrop.labelZh,
                                            style = MaterialTheme.typography.labelMedium
                                        )
                                    },
                                    shape = RoundedCornerShape(50),
                                    modifier = Modifier.testTag("backdrop_${backdrop.name.lowercase()}")
                                )
                            }
                        }
                    }
                }

                // Selectable Data Overlay Chips
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(22.dp),
                    color = cs.surfaceContainerLow
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.social_section_overlays),
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = cs.onSurface
                        )

                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            OverlayDataChip(
                                label = stringResource(R.string.social_chip_route),
                                selected = config.showRoute,
                                onToggle = { config = config.copy(showRoute = !config.showRoute) },
                                tag = "chip_overlay_route"
                            )
                            OverlayDataChip(
                                label = stringResource(R.string.social_chip_title),
                                selected = config.showTitle,
                                onToggle = { config = config.copy(showTitle = !config.showTitle) },
                                tag = "chip_overlay_title"
                            )
                            OverlayDataChip(
                                label = stringResource(R.string.social_chip_distance),
                                selected = config.showDistance,
                                onToggle = { config = config.copy(showDistance = !config.showDistance) },
                                tag = "chip_overlay_distance"
                            )
                            OverlayDataChip(
                                label = stringResource(R.string.social_chip_duration),
                                selected = config.showDuration,
                                onToggle = { config = config.copy(showDuration = !config.showDuration) },
                                tag = "chip_overlay_duration"
                            )
                            if (isKart) {
                                OverlayDataChip(
                                    label = stringResource(R.string.social_chip_f1_sectors),
                                    selected = config.showF1Sectors,
                                    onToggle = { config = config.copy(showF1Sectors = !config.showF1Sectors) },
                                    tag = "chip_overlay_f1_sectors"
                                )
                                OverlayDataChip(
                                    label = stringResource(R.string.social_chip_laps),
                                    selected = config.showLaps,
                                    onToggle = { config = config.copy(showLaps = !config.showLaps) },
                                    tag = "chip_overlay_laps"
                                )
                            } else {
                                OverlayDataChip(
                                    label = stringResource(R.string.social_chip_elevation),
                                    selected = config.showElevation,
                                    onToggle = { config = config.copy(showElevation = !config.showElevation) },
                                    tag = "chip_overlay_elevation"
                                )
                            }
                            OverlayDataChip(
                                label = stringResource(R.string.social_chip_speed),
                                selected = config.showSpeed,
                                onToggle = { config = config.copy(showSpeed = !config.showSpeed) },
                                tag = "chip_overlay_speed"
                            )
                            if (detail.session.avgHeartRate != null) {
                                OverlayDataChip(
                                    label = stringResource(R.string.social_chip_hr),
                                    selected = config.showHeartRate,
                                    onToggle = { config = config.copy(showHeartRate = !config.showHeartRate) },
                                    tag = "chip_overlay_hr"
                                )
                            }
                            if (detail.session.peakTotalG > 0.05f) {
                                OverlayDataChip(
                                    label = stringResource(R.string.social_chip_peak_g),
                                    selected = config.showPeakG,
                                    onToggle = { config = config.copy(showPeakG = !config.showPeakG) },
                                    tag = "chip_overlay_peak_g"
                                )
                            }
                            OverlayDataChip(
                                label = stringResource(
                                    when (mode) {
                                        ActivityMode.OFFROAD_CROSSING -> R.string.social_chip_climb_difficulty
                                        ActivityMode.HIKING -> R.string.social_chip_hike_difficulty
                                        ActivityMode.KARTING -> R.string.social_chip_kart_difficulty
                                    }
                                ),
                                selected = config.showClimbDifficulty,
                                onToggle = { config = config.copy(showClimbDifficulty = !config.showClimbDifficulty) },
                                tag = "chip_overlay_climb_difficulty"
                            )
                            if (mode.isElevationTrail) {
                                OverlayDataChip(
                                    label = stringResource(R.string.social_chip_surface_complexity),
                                    selected = config.showSurfaceComplexity,
                                    onToggle = { config = config.copy(showSurfaceComplexity = !config.showSurfaceComplexity) },
                                    tag = "chip_overlay_surface_complexity"
                                )
                            }
                            if (mode == ActivityMode.HIKING || mode == ActivityMode.KARTING) {
                                OverlayDataChip(
                                    label = stringResource(R.string.social_chip_body_performance),
                                    selected = config.showBodyPerformance,
                                    onToggle = { config = config.copy(showBodyPerformance = !config.showBodyPerformance) },
                                    tag = "chip_overlay_body_performance"
                                )
                            }
                            OverlayDataChip(
                                label = stringResource(R.string.social_chip_profile),
                                selected = config.showMiniProfile,
                                onToggle = { config = config.copy(showMiniProfile = !config.showMiniProfile) },
                                tag = "chip_overlay_profile"
                            )
                        }
                    }
                }

                // Export Buttons: Save PNG to Gallery + Share PNG
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        enabled = !isExporting,
                        onClick = {
                            isExporting = true
                            scope.launch {
                                val saved = withContext(Dispatchers.IO) {
                                    val fullBmp = SocialCardBitmapRenderer.renderBitmap(
                                        detail = detail,
                                        speedUnit = speedUnit,
                                        config = config,
                                        photoBitmap = importedPhoto,
                                        targetWidth = 1080
                                    )
                                    val ok = SocialCardBitmapRenderer.savePngToGallery(context, fullBmp, filePrefix)
                                    fullBmp.recycle()
                                    ok
                                }
                                isExporting = false
                                Toast.makeText(
                                    context,
                                    context.getString(
                                        if (saved) R.string.toast_social_saved else R.string.toast_social_save_failed
                                    ),
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp)
                            .minimumInteractiveComponentSize()
                            .testTag("btn_social_save_gallery"),
                        shape = RoundedCornerShape(50),
                        contentPadding = PaddingValues(horizontal = 16.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Download,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = stringResource(R.string.social_save_gallery),
                            style = MaterialTheme.typography.labelLarge
                        )
                    }

                    FilledTonalButton(
                        enabled = !isExporting,
                        onClick = {
                            isExporting = true
                            scope.launch {
                                val uri = withContext(Dispatchers.IO) {
                                    runCatching {
                                        val fullBmp = SocialCardBitmapRenderer.renderBitmap(
                                            detail = detail,
                                            speedUnit = speedUnit,
                                            config = config,
                                            photoBitmap = importedPhoto,
                                            targetWidth = 1080
                                        )
                                        val outUri = SocialCardBitmapRenderer.exportPngToShareUri(
                                            context = context,
                                            bitmap = fullBmp,
                                            fileNamePrefix = filePrefix
                                        )
                                        fullBmp.recycle()
                                        outUri
                                    }.getOrNull()
                                }
                                isExporting = false
                                if (uri != null) {
                                    onSharePngUri(uri)
                                } else {
                                    Toast.makeText(
                                        context,
                                        context.getString(R.string.toast_social_save_failed),
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp)
                            .minimumInteractiveComponentSize()
                            .testTag("btn_social_share_png"),
                        shape = RoundedCornerShape(50),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = cs.secondaryContainer,
                            contentColor = cs.onSecondaryContainer
                        ),
                        contentPadding = PaddingValues(horizontal = 16.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.IosShare,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = stringResource(R.string.social_share_png),
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun BackgroundSwatchItem(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    tag: String,
    swatchContent: @Composable () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(5.dp),
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(4.dp)
            .testTag(tag)
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .border(
                    width = if (selected) 2.5.dp else 1.dp,
                    color = if (selected) cs.primary else cs.outlineVariant,
                    shape = CircleShape
                )
                .padding(3.dp)
                .clip(CircleShape),
            contentAlignment = Alignment.Center
        ) {
            swatchContent()
            if (selected) {
                Box(
                    modifier = Modifier
                        .size(18.dp)
                        .clip(CircleShape)
                        .background(cs.primary),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Check,
                        contentDescription = null,
                        tint = cs.onPrimary,
                        modifier = Modifier.size(12.dp)
                    )
                }
            }
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 11.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
            ),
            color = if (selected) cs.primary else cs.onSurfaceVariant
        )
    }
}

@Composable
private fun OverlayDataChip(
    label: String,
    selected: Boolean,
    onToggle: () -> Unit,
    tag: String
) {
    FilterChip(
        selected = selected,
        onClick = onToggle,
        label = { Text(text = label, style = MaterialTheme.typography.labelMedium) },
        leadingIcon = if (selected) {
            {
                Icon(
                    imageVector = Icons.Rounded.Check,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
            }
        } else {
            null
        },
        shape = RoundedCornerShape(50),
        modifier = Modifier.testTag(tag)
    )
}

@Composable
private fun CompactSegmentPill(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    tag: String
) {
    val cs = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (selected) cs.primaryContainer else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 4.dp)
            .testTag(tag),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
            ),
            color = if (selected) cs.onPrimaryContainer else cs.onSurfaceVariant
        )
    }
}

@Composable
private fun TransparencyCheckerboard(
    modifier: Modifier = Modifier,
    cellDp: Float = 10f
) {
    val lightCell = Color(0xFFE3E7EE)
    val darkCell = Color(0xFFC9D0DC)
    Canvas(modifier = modifier) {
        val cellPx = cellDp.dp.toPx().coerceAtLeast(4f)
        val cols = (size.width / cellPx).toInt() + 1
        val rows = (size.height / cellPx).toInt() + 1
        for (r in 0 until rows) {
            for (c in 0 until cols) {
                val color = if ((r + c) % 2 == 0) lightCell else darkCell
                drawRect(
                    color = color,
                    topLeft = Offset(c * cellPx, r * cellPx),
                    size = Size(cellPx, cellPx)
                )
            }
        }
    }
}
