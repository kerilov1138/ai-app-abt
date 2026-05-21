package com.example.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf

// Standard Material 3 tokens
val Purple80 = Color(0xFFD0BCFF)
val PurpleGrey80 = Color(0xFFCCC2DC)
val Pink80 = Color(0xFFEFB8C8)

val Purple40 = Color(0xFF6650a4)
val PurpleGrey40 = Color(0xFF625b71)
val Pink40 = Color(0xFF7D5260)

// Raw theme values for static setups
val LightBgVal = Color(0xFFFDF8FD)
val LightMutedBgVal = Color(0xFFF3EDF7)
val LightCardBgVal = Color(0xFFFFFFFF)
val LightPrimaryVal = Color(0xFF6750A4)
val LightAccentVal = Color(0xFF7C64BD)
val LightTextPrimaryVal = Color(0xFF1D1B20)
val LightTextSecondaryVal = Color(0xFF48454F)
val LightUserBubbleVal = Color(0xFFE8DEF8)
val LightAiBubbleVal = Color(0xFFF3EDF7)
val LightBorderVal = Color(0xFFCECAE3)
val LightGreenActiveVal = Color(0xFF2E7D32)

val DarkBgVal = Color(0xFF0F0C29)
val DarkBg2Val = Color(0xFF120E38)
val DarkMutedBgVal = Color(0xFF1E1A3A)
val DarkCardBgVal = Color(0xFF1C183B)
val DarkCardOuterVal = Color(0xFF25204C)
val DarkPrimaryVal = Color(0xFFBB86FC)
val DarkAccentVal = Color(0xFF9B59B6)
val DarkTextPrimaryVal = Color(0xFFFFFFFF)
val DarkTextSecondaryVal = Color(0xFFB3B3B3)
val DarkUserBubbleVal = Color(0xFF2D1F5E)
val DarkAiBubbleVal = Color(0xFF161230)
val DarkBorderVal = Color(0xFF302B63)
val DarkGreenActiveVal = Color(0xFF4CAF50)

data class CustomThemeColors(
    val bg: Color,
    val bg2: Color,
    val mutedBg: Color,
    val cardBg: Color,
    val cardOuter: Color,
    val primary: Color,
    val accent: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val userBubble: Color,
    val aiBubble: Color,
    val border: Color,
    val greenActive: Color
)

val LightColors = CustomThemeColors(
    bg = LightBgVal,
    bg2 = LightBgVal,
    mutedBg = LightMutedBgVal,
    cardBg = LightCardBgVal,
    cardOuter = LightCardBgVal,
    primary = LightPrimaryVal,
    accent = LightAccentVal,
    textPrimary = LightTextPrimaryVal,
    textSecondary = LightTextSecondaryVal,
    userBubble = LightUserBubbleVal,
    aiBubble = LightAiBubbleVal,
    border = LightBorderVal,
    greenActive = LightGreenActiveVal
)

val DarkColors = CustomThemeColors(
    bg = DarkBgVal,
    bg2 = DarkBg2Val,
    mutedBg = DarkMutedBgVal,
    cardBg = DarkCardBgVal,
    cardOuter = DarkCardOuterVal,
    primary = DarkPrimaryVal,
    accent = DarkAccentVal,
    textPrimary = DarkTextPrimaryVal,
    textSecondary = DarkTextSecondaryVal,
    userBubble = DarkUserBubbleVal,
    aiBubble = DarkAiBubbleVal,
    border = DarkBorderVal,
    greenActive = DarkGreenActiveVal
)

val LocalAppColors = staticCompositionLocalOf { LightColors }

val CosmicDarkBg: Color @Composable get() = LocalAppColors.current.bg
val CosmicDarkBg2: Color @Composable get() = LocalAppColors.current.bg2
val CosmicMutedBg: Color @Composable get() = LocalAppColors.current.mutedBg
val CosmicCardBg: Color @Composable get() = LocalAppColors.current.cardBg
val CosmicCardOuter: Color @Composable get() = LocalAppColors.current.cardOuter
val PurplePrimary: Color @Composable get() = LocalAppColors.current.primary
val PurpleAccent: Color @Composable get() = LocalAppColors.current.accent
val TextWhite: Color @Composable get() = LocalAppColors.current.textPrimary
val TextGray: Color @Composable get() = LocalAppColors.current.textSecondary
val UserBubbleBg: Color @Composable get() = LocalAppColors.current.userBubble
val AiBubbleBg: Color @Composable get() = LocalAppColors.current.aiBubble
val BorderColor: Color @Composable get() = LocalAppColors.current.border
val GreenActive: Color @Composable get() = LocalAppColors.current.greenActive
