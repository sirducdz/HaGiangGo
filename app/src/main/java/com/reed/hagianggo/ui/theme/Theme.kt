package com.reed.hagianggo.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// Giao diện Sáng (Clean Light Mode - mặc định)
// Tươi sáng, thanh lịch, nền trắng & xám nhạt dịu mắt, viền mảnh 1px
private val LightColorScheme = lightColorScheme(
    primary = ElectricOrange,
    onPrimary = Color.White,
    primaryContainer = ElectricOrangeSoft,
    onPrimaryContainer = ElectricOrangeHover,
    secondary = EmeraldGreen,
    onSecondary = Color.White,
    background = AppBackground,
    onBackground = TextPrimary,
    surface = SurfaceWhite,
    onSurface = TextPrimary,
    surfaceVariant = SurfaceMuted,
    onSurfaceVariant = TextSecondary,
    outline = BorderSubtle,
    outlineVariant = BorderMedium,
    error = RoseDestructive,
    onError = Color.White
)

// Giao diện Tối
private val DarkColorScheme = darkColorScheme(
    primary = ElectricOrange,
    onPrimary = Color.White,
    primaryContainer = Zinc800,
    onPrimaryContainer = ElectricOrange,
    secondary = EmeraldGreen,
    onSecondary = Zinc950,
    background = Zinc950,
    onBackground = Zinc100,
    surface = Zinc900,
    onSurface = Zinc100,
    surfaceVariant = Zinc800,
    onSurfaceVariant = Zinc400,
    outline = Zinc800,
    outlineVariant = Zinc700,
    error = RoseDestructive,
    onError = Color.White
)

@Composable
fun HaGiangGoTheme(
    // Mặc định giao diện Sáng tươi tắn, dịu mắt, hiện đại theo phong cách Notion/Linear/Airbnb
    darkTheme: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val view = LocalView.current

    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.surface.toArgb()
            window.navigationBarColor = colorScheme.surface.toArgb()
            val controller = WindowCompat.getInsetsController(window, view)
            // Khi lightTheme = true thì thanh trạng thái hiển thị icon màu đen sắc nét
            controller.isAppearanceLightStatusBars = !darkTheme
            controller.isAppearanceLightNavigationBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
