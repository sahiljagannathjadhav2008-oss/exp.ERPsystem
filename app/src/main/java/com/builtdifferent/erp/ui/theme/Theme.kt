package com.builtdifferent.erp.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val ErpGreen = Color(0xFF2ECC71)
private val ErpGreenDark = Color(0xFF17A362)
private val ErpBackground = Color(0xFF0B0F14)
private val ErpSurface = Color(0xFF141A21)
private val ErpError = Color(0xFFE74C3C)

private val DarkColors = darkColorScheme(
    primary = ErpGreen,
    onPrimary = Color.Black,
    secondary = ErpGreenDark,
    background = ErpBackground,
    surface = ErpSurface,
    onBackground = Color(0xFFE6E9EC),
    onSurface = Color(0xFFE6E9EC),
    error = ErpError
)

private val LightColors = lightColorScheme(
    primary = ErpGreenDark,
    onPrimary = Color.White,
    secondary = ErpGreen,
    error = ErpError
)

/** The app defaults to dark (matching every other "Built Different" app),
 * but still honours the system light-mode setting rather than forcing dark
 * unconditionally, since accounting software is often used in bright
 * shop-counter lighting where a light theme is genuinely easier to read. */
@Composable
fun OfflineErpTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColors else LightColors
    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
