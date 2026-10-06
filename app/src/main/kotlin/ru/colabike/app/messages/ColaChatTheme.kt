package ru.colabike.app.messages

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import io.getstream.chat.android.compose.state.messages.attachments.GalleryPickerMode
import io.getstream.chat.android.compose.state.messages.attachments.MediaType
import io.getstream.chat.android.compose.ui.components.messageoptions.MessageActionsOptionsVisibility
import io.getstream.chat.android.compose.ui.theme.AttachmentPickerConfig
import io.getstream.chat.android.compose.ui.theme.ChatTheme
import io.getstream.chat.android.compose.ui.theme.ChatUiConfig
import io.getstream.chat.android.compose.ui.theme.ComposerConfig
import io.getstream.chat.android.compose.ui.theme.MessageActionsConfig
import io.getstream.chat.android.compose.ui.theme.StreamDesign
import io.getstream.chat.android.compose.ui.theme.TranslationConfig

/**
 * The chat SDK's screens in ColaBike's look: its palette is built from this app's colour scheme (so
 * light and dark, and any later change of the theme, carry over), its type is the app's, and what
 * the SDK offers beyond what the server allows is switched off (see [colaChatConfig]).
 */
@Composable
fun ColaChatTheme(content: @Composable () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    // The app's theme may be chosen by the person, not the system: dark is what the page looks
    // like.
    val dark = scheme.background.luminance() < 0.5f
    val family = MaterialTheme.typography.bodyLarge.fontFamily
    val colors = remember(scheme, dark) { colaChatColors(scheme, dark) }
    val typography = remember(family) { StreamDesign.Typography.default(fontFamily = family) }
    ChatTheme(
        isInDarkMode = dark,
        config = colaChatConfig,
        colors = colors,
        typography = typography,
        content = content,
    )
}

/**
 * What the SDK may offer. Only photos (through the system picker, no storage permission), no
 * camera, files, polls or commands; no voice messages (the microphone is not asked for), no
 * translation, no link previews in the composer. Muting and blocking a user at the provider are not
 * offered: who may write to whom is ColaBike's, and the server decides it (a block is made on the
 * person's page and reaches the provider from there, docs/adr/0021). Flagging a message is: it is
 * how a message is reported, and the provider's moderation queue is the owner's.
 */
internal val colaChatConfig =
    ChatUiConfig(
        translation = TranslationConfig(enabled = false, showOriginalEnabled = false),
        composer = ComposerConfig(audioRecordingEnabled = false, linkPreviewEnabled = false),
        attachmentPicker =
            AttachmentPickerConfig(
                useSystemPicker = true,
                modes = listOf(GalleryPickerMode(mediaType = MediaType.ImagesOnly)),
            ),
        messageActions =
            MessageActionsConfig(
                optionsVisibility =
                    MessageActionsOptionsVisibility(
                        isFlagMessageVisible = true,
                        isPinMessageVisible = false,
                        isMuteUserVisible = false,
                        isBlockUserVisible = false,
                    )
            ),
    )

/**
 * The SDK's two scales (a ramp of the accent and a ramp of neutrals) from the app's scheme; every
 * other colour of the SDK is derived from them by the SDK itself, which is why the palette is made
 * by its constructor and not patched afterwards (a patched value would not reach the colours the
 * SDK derived from the old one). Light: the neutrals run from the card colour down to the ink;
 * dark: from the page up to the parchment.
 */
internal fun colaChatColors(scheme: ColorScheme, dark: Boolean): StreamDesign.Colors {
    val chrome = chromeScale(scheme, dark)
    val brand = brandScale(scheme, dark)
    val primary = scheme.primary
    return StreamDesign.Colors(
        brand = brand,
        chrome = chrome,
        accentPrimary = if (dark) brand.s400 else brand.s500,
        accentError = scheme.error,
        accentSuccess = scheme.tertiary,
        accentWarning = scheme.secondary,
        accentNeutral = chrome.s500,
        textPrimary = scheme.onSurface,
        textSecondary = chrome.s700,
        textTertiary = chrome.s500,
        textDisabled = chrome.s300,
        textOnAccent = scheme.onPrimary,
        textOnInverse = scheme.inverseOnSurface,
        textLink = if (dark) brand.s600 else brand.s500,
        backgroundCoreElevation0 = scheme.background,
        backgroundCoreElevation1 = scheme.surfaceContainerLow,
        backgroundCoreElevation2 = scheme.surfaceContainer,
        backgroundCoreElevation3 = scheme.surfaceContainerHigh,
        backgroundCoreSurfaceDefault = scheme.surfaceContainer,
        backgroundCoreSurfaceSubtle = scheme.surfaceContainerLow,
        backgroundCoreSurfaceStrong = scheme.surfaceContainerHigh,
        backgroundCoreSurfaceCard = scheme.surfaceContainer,
        backgroundCoreInverse = scheme.inverseSurface,
        backgroundCoreOnAccent = scheme.onPrimary,
        backgroundCoreScrim = scheme.scrim.copy(alpha = 0.5f),
        backgroundCoreOverlayDark = scheme.scrim.copy(alpha = 0.25f),
        backgroundCoreOverlayLight = scheme.surface.copy(alpha = 0.75f),
        backgroundCoreHighlight = lerp(scheme.background, primary, 0.18f),
        backgroundCoreApp = scheme.background,
        backgroundUtilitySelected = primary.copy(alpha = 0.16f),
        backgroundUtilityDisabled = scheme.surfaceContainerHigh,
        borderCoreDefault = scheme.outlineVariant,
        borderCoreStrong = chrome.s300,
        borderCoreSubtle = scheme.outlineVariant.copy(alpha = 0.6f),
        borderCoreOpacitySubtle = scheme.onSurface.copy(alpha = 0.10f),
        borderCoreOpacityStrong = scheme.onSurface.copy(alpha = 0.25f),
        borderCoreOnAccent = scheme.onPrimary,
        borderCoreOnInverse = scheme.inverseOnSurface,
        borderCoreOnSurface = chrome.s300,
        borderUtilitySelected = primary.copy(alpha = 0.3f),
        borderUtilityFocused = brand.s300,
        borderUtilityActive = primary,
        borderUtilityDisabled = scheme.outlineVariant,
        borderUtilityDisabledOnSurface = scheme.outlineVariant,
        borderUtilityError = scheme.error,
        borderUtilityWarning = scheme.secondary,
        borderUtilitySuccess = scheme.tertiary,
        avatarBgPlaceholder = scheme.surfaceContainerHigh,
        avatarPaletteBg1 = scheme.primaryContainer,
        avatarPaletteBg2 = scheme.secondaryContainer,
        avatarPaletteBg3 = scheme.tertiaryContainer,
        avatarPaletteBg4 = scheme.surfaceContainerHigh,
        avatarPaletteBg5 = scheme.surfaceContainerHighest,
        avatarPaletteText1 = scheme.onPrimaryContainer,
        avatarPaletteText2 = scheme.onSecondaryContainer,
        avatarPaletteText3 = scheme.onTertiaryContainer,
        avatarPaletteText4 = scheme.onSurface,
        avatarPaletteText5 = scheme.onSurface,
        avatarTextPlaceholder = chrome.s700,
        avatarPresenceBorder = scheme.background,
        skeletonLoadingBase = scheme.surfaceContainerHigh,
        skeletonLoadingHighlight = scheme.surfaceContainerHighest,
        systemCaret = primary,
    )
}

/**
 * The neutrals: [StreamDesign.ChromeScale.s0] is the surface of a raised card in light and the page
 * in dark, [StreamDesign.ChromeScale.s900] the text colour, s1000 the inverse surface. The stops
 * between are mixes, with the steps that carry text (500, 700) dark enough to be read.
 */
private fun chromeScale(scheme: ColorScheme, dark: Boolean): StreamDesign.ChromeScale {
    val text = scheme.onSurface
    return if (dark) {
        val s200 = scheme.surfaceContainerHighest
        fun toward(fraction: Float) = lerp(s200, text, fraction)
        StreamDesign.ChromeScale(
            s0 = scheme.background,
            s50 = scheme.surfaceContainerLow,
            s100 = scheme.surfaceContainer,
            s150 = scheme.surfaceContainerHigh,
            s200 = s200,
            s300 = toward(0.20f),
            s400 = toward(0.35f),
            s500 = toward(0.55f),
            s600 = toward(0.70f),
            s700 = toward(0.80f),
            s800 = toward(0.90f),
            s900 = text,
            s1000 = scheme.inverseSurface,
        )
    } else {
        val s150 = scheme.surfaceContainerHighest
        fun toward(fraction: Float) = lerp(s150, text, fraction)
        StreamDesign.ChromeScale(
            s0 = scheme.surfaceContainerLowest,
            s50 = scheme.background,
            s100 = scheme.surfaceContainerHigh,
            s150 = s150,
            s200 = toward(0.12f),
            s300 = toward(0.30f),
            s400 = toward(0.45f),
            s500 = toward(0.62f),
            s600 = toward(0.72f),
            s700 = toward(0.80f),
            s800 = toward(0.90f),
            s900 = text,
            s1000 = scheme.inverseSurface,
        )
    }
}

/**
 * The accent ramp: pale tints of the primary colour for the bubbles of one's own messages, the
 * primary itself in the middle, shades for text on those bubbles at the dark end (light theme);
 * mirrored in dark.
 */
private fun brandScale(scheme: ColorScheme, dark: Boolean): StreamDesign.ColorScale {
    val primary = scheme.primary
    return if (dark) {
        val page = scheme.background
        fun tint(f: Float) = lerp(page, primary, f)
        fun light(f: Float) = lerp(primary, Color.White, f)
        StreamDesign.ColorScale(
            s50 = tint(0.10f),
            s100 = tint(0.16f),
            s150 = tint(0.22f),
            s200 = tint(0.30f),
            s300 = tint(0.45f),
            s400 = primary,
            s500 = light(0.15f),
            s600 = light(0.30f),
            s700 = light(0.50f),
            s800 = light(0.70f),
            s900 = light(0.85f),
        )
    } else {
        val page = scheme.background
        fun tint(f: Float) = lerp(page, primary, f)
        fun shade(f: Float) = lerp(primary, Color.Black, f)
        StreamDesign.ColorScale(
            s50 = tint(0.06f),
            s100 = tint(0.12f),
            s150 = tint(0.18f),
            s200 = tint(0.26f),
            s300 = tint(0.45f),
            s400 = tint(0.70f),
            s500 = primary,
            s600 = shade(0.15f),
            s700 = shade(0.30f),
            s800 = shade(0.45f),
            s900 = shade(0.60f),
        )
    }
}
