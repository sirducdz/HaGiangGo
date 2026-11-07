package com.reed.hagianggo.ui.theme

import androidx.compose.ui.graphics.Color

// ==========================================
// Modern Clean Light Palette (shadcn Slate/Zinc Light)
// Tươi sáng, thanh lịch, sắc nét và dịu mắt
// ==========================================

// Bề mặt & Nền
val AppBackground = Color(0xFFF8FAFC)     // Slate-50: Nền sáng nhẹ nhàng, không bị chói lóa
val SurfaceWhite = Color(0xFFFFFFFF)      // Pure White: Bề mặt Card, Header, BottomBar
val SurfaceMuted = Color(0xFFF1F5F9)      // Slate-100: Khay trượt Tab, Ô tìm kiếm, Box phụ
val SurfaceHover = Color(0xFFE2E8F0)      // Slate-200: Trạng thái nhấn, hover

// Đường viền 1px sắc nét (Hairline Borders)
val BorderSubtle = Color(0xFFE2E8F0)      // Slate-200: Viền Card, Divider, BottomBar
val BorderMedium = Color(0xFFCBD5E1)      // Slate-300: Viền input, nút bấm phụ
val BorderFocus  = Color(0xFFF97316)      // Electric Orange: Viền khi focus

// Typography Tương Phản Cao & Dễ Đọc
val TextPrimary   = Color(0xFF0F172A)     // Slate-900: Chữ tiêu đề đen than đậm, sắc nét
val TextSecondary = Color(0xFF475569)     // Slate-600: Chữ nội dung phụ, mô tả
val TextMuted     = Color(0xFF64748B)     // Slate-500: Nhãn mờ, timestamp, đơn vị đo
val TextSubtle    = Color(0xFF94A3B8)     // Slate-400: Placeholder

// ==========================================
// Điểm Nhấn Tươi Tắn (Vibrant Accents)
// ==========================================
val ElectricOrange = Color(0xFFF97316)     // Cam phượt hoàng hôn (Primary Action)
val ElectricOrangeHover = Color(0xFFEA580C)
val ElectricOrangeSoft = Color(0xFFFFF7ED)  // Nền pastel cam nhạt
val ElectricOrangeBorder = Color(0xFFFED7AA)

val EmeraldGreen = Color(0xFF10B981)       // Xanh ngọc GPS Lock / Đã đi / An toàn
val EmeraldGreenSoft = Color(0xFFECFDF5)   // Nền pastel xanh nhạt
val EmeraldGreenBorder = Color(0xFFA7F3D0)
val EmeraldGreenText = Color(0xFF047857)

val AmberWarning = Color(0xFFF59E0B)       // Vàng hổ phách cảnh báo đèo tối / Cây xăng
val AmberWarningSoft = Color(0xFFFFFBEB)   // Nền pastel vàng nhạt
val AmberWarningBorder = Color(0xFFFDE68A)
val AmberWarningText = Color(0xFFB45309)

val RoseDestructive = Color(0xFFF43F5E)    // Đỏ SOS / Cảnh báo nguy hiểm / Xóa
val RoseDestructiveSoft = Color(0xFFFFF1F2) // Nền pastel đỏ nhạt
val RoseDestructiveBorder = Color(0xFFFECDD3)
val RoseDestructiveText = Color(0xFFBE123C)

val SkyTelemetry = Color(0xFF0284C7)       // Xanh dương đo đạc / Cung đường
val SkyTelemetrySoft = Color(0xFFF0F9FF)   // Nền pastel xanh dương nhạt
val SkyTelemetryBorder = Color(0xFFBAE6FD)

// ==========================================
// Tương thích ngược với các tên cũ
// ==========================================
val SunsetOrange = ElectricOrange
val SunsetOrangeLight = ElectricOrangeSoft
val SunsetOrangeDark = ElectricOrangeHover

val HighlandGreen = EmeraldGreen
val HighlandGreenLight = EmeraldGreenSoft

val AlertRed = RoseDestructive
val AlertRedLight = RoseDestructiveSoft

val FuelGold = AmberWarning
val FuelGoldLight = AmberWarningSoft

val AmberGlow = AmberWarning
val SlateBlue = Color(0xFF475569)
val MistBlue = Color(0xFF64748B)
val HaGiangSlate80 = SurfaceWhite
val HaGiangSlate60 = BorderSubtle
val HaGiangSlate20 = SurfaceMuted

// Dark Theme tokens nếu cần
val Zinc950 = Color(0xFF09090B)
val Zinc900 = Color(0xFF18181B)
val Zinc800 = Color(0xFF27272A)
val Zinc700 = Color(0xFF3F3F46)
val Zinc600 = Color(0xFF52525B)
val Zinc500 = Color(0xFF71717A)
val Zinc400 = Color(0xFFA1A1AA)
val Zinc300 = Color(0xFFD4D4D8)
val Zinc200 = Color(0xFFE4E4E7)
val Zinc100 = Color(0xFFF4F4F5)
val Zinc50  = Color(0xFFFAFAFA)
