# GEMINI.md - Hướng Dẫn & Quy Chuẩn Dự Án Hà Giang Go

Tài liệu này cung cấp toàn bộ ngữ cảnh sản phẩm, kiến trúc mã nguồn, ngôn ngữ thiết kế (**Design System**) và các quy tắc kỹ thuật bắt buộc dành cho AI Agent và lập trình viên khi phát triển dự án **Hà Giang Go** (`com.reed.hagianggo`).

---

## 1. Tổng Quan Dự Án (Project Overview)

* **Tên ứng dụng**: **Hà Giang Go** (`com.reed.hagianggo`)
* **Mục đích**: Trợ lý phượt đèo núi **Offline-First** chuyên biệt cho hành trình **Hà Giang Loop (350–380 km)** qua 8 cụm chặng:
  1. `tp_ha_giang` (TP. Hà Giang – Km0, 120m)
  2. `quan_ba` (Quản Bạ – Cổng Trời, 1.150m)
  3. `yen_minh` (Yên Minh – Rừng Thông, 750m)
  4. `sung_la_pho_cao` (Thung lũng Sủng Là – Phố Cáo – Dinh Vua Mèo, 1.320m)
  5. `lung_cu` (Cột Cờ Quốc Gia Lũng Cú – Lô Lô Chải, 1.470m)
  6. `dong_van` (Phố Cổ Đồng Văn, 1.050m)
  7. `ma_pi_leng_meo_vac` (Đèo Mã Pì Lèng – Sông Nho Quế – Mèo Vạc, 1.500m)
  8. `du_gia` (Mậu Duệ – Du Già – Đường Thượng, 680m)
* **Triết lý cốt lõi**:
  * **100% Hoạt động độc lập khi mất sóng**: Mọi tính năng sống còn (Bản đồ địa hình, Định vị GPS vệ tinh, Gợi ý "Làm gì bây giờ?", Cảnh báo tầm xăng, Cứu hộ SOS 2G, Sổ tay chi tiêu) phải chạy mượt mà với độ trễ `0ms` khi rớt mạng 4G giữa đèo cao.
  * **An toàn sinh tồn thực chiến**: Cảnh báo sương mù, tính toán giờ hạ sơn trước khi trời tối (17:30), cảnh báo "điểm mù cây xăng" (>40 km không có trạm xăng), và phát tín hiệu cứu hộ qua sóng SMS 2G cơ bản.

---

## 2. Thông Số Kỹ Thuật (Tech Stack & Environment)

* **Ngôn ngữ**: Kotlin (JVM Target 11)
* **UI Framework**: Jetpack Compose (Material 3 + Hệ thống Component tùy biến `ShadcnComponents`)
* **Android SDK**:
  * `minSdk`: 24 (Android 7.0)
  * `compileSdk` / `targetSdk`: 37
* **Lưu trữ dữ liệu**:
  * **SQLite thuần (`SQLiteOpenHelper`)**: Phiên bản Schema hiện tại là **`v4`** (`hagiang_go.db`), quản lý 10 bảng dữ liệu có hỗ trợ Migration bảo toàn dữ liệu người dùng và xuất/nhập JSON Backup.
  * **OSMDroid (`org.osmdroid:osmdroid-android:6.1.20`)**: Lưu trữ mảnh bản đồ vĩnh viễn trong `filesDir/osmdroid/tiles/cache.db`.
* **Định vị & Cảm biến**:
  * Sử dụng trực tiếp `android.location.LocationManager` đa nguồn (`GPS_PROVIDER` + `NETWORK_PROVIDER` + `PASSIVE_PROVIDER`), không phụ thuộc cứng vào Google Play Services.
  * `RideTrackerService` (`ForegroundService` loại `location`) ghi nhận hành trình, tốc độ, độ cao và mặt cắt địa hình theo thời gian thực.

---

## 3. Ngôn Ngữ & Phong Cách Thiết Kế (Design System & UI/UX)

### 3.1. Triết Lý Thẩm Mỹ: **AllTrails × Airbnb × Flighty × shadcn/ui (Clean Light Mode)**
Ứng dụng sử dụng giao diện **Sáng Tươi Tắn, Sắc Nét & Dịu Mắt (Clean Light Mode)** làm mặc định để đạt độ tương phản cao nhất khi người dùng nhìn màn hình dưới nắng gắt vùng cao:
* **Không gian mở (Spatial UI)**: Ưu tiên bản đồ tràn viền, các thanh công cụ nổi dạng viên thuốc kính mờ (`SurfaceGlass`), tấm kéo trượt đa nấc (`3-Detent Fluid Sheet`) và bố cục lưới **Bento Grid**.
* **Viền mảnh tinh tế (Hairline Borders)**: Sử dụng đường viền `1.dp` (`BorderSubtle`) kết hợp bóng đổ mềm tự nhiên (`SoftShadowColor`) thay vì lạm dụng đổ bóng đen đậm kiểu cũ.
* **Chống rối mắt (Anti-Clutter & Progressive Disclosure)**:
  * Không nhồi nhét quá nhiều thông tin lên một thẻ.
  * Tự động thích ứng theo ngữ cảnh: Ví dụ ở tab **Sổ tay**, nếu đi 1 mình (*Solo*) thì ẩn hoàn toàn bộ chọn chia tiền nhóm; chỉ khi thêm `≥ 2` thành viên mới mở rộng giao diện **Splitbill**.

### 3.2. Bảng Màu Chuẩn (`ui/theme/Color.kt`)
Luôn sử dụng các token màu ngữ nghĩa trong `Color.kt`, **không hardcode mã hex rời rạc** trong các màn hình:

| Nhóm | Token màu | Mã Hex | Mục đích sử dụng |
| :--- | :--- | :--- | :--- |
| **Bề mặt (Surfaces)** | `AppBackground` | `#F6F8FA` | Nền tổng thể kem lạnh dịu mắt, làm nổi bật thẻ trắng |
| | `SurfaceWhite` | `#FFFFFF` | Bề mặt Card, BottomSheet, Dialog, Pill |
| | `SurfaceGlass` | `#EBFFFFFF` | Kính mờ 92% cho Header nổi, Nav Bar & nút điều khiển bản đồ |
| | `SurfaceMuted` | `#F1F5F9` | Nền phụ cho Chip, Segmented Tab, ô thông số Bento |
| **Đường viền (Borders)** | `BorderSubtle` | `#E2E8F0` | Viền mảnh `1.dp` cho Card, Divider, thanh công cụ |
| | `BorderMedium` | `#CBD5E1` | Viền Input, thanh kéo Sheet |
| **Chữ (Typography)** | `TextPrimary` | `#0F172A` | Tiêu đề & số liệu chính (Slate-900 tương phản cao) |
| | `TextSecondary` | `#475569` | Nội dung mô tả, mẹo thực tế |
| | `TextMuted` | `#64748B` | Nhãn phụ, đơn vị đo, tọa độ |
| **Điểm nhấn (Accents)** | `ElectricOrange` | `#F97316` | Màu chủ đạo (Nút hành động chính, lộ trình đèo, Tab đang chọn) |
| | `EmeraldGreen` | `#10B981` | Tín hiệu GPS Live, Đã ghé thăm, Bản đồ Offline sẵn sàng, An toàn |
| | `AmberWarning` | `#F59E0B` | Cảnh báo sương mù/trời tối, Cây xăng, Quán cà phê |
| | `RoseDestructive` | `#F43F5E` | Cứu hộ khẩn cấp **SOS 2G**, Cảnh báo cạn xăng, Xóa |
| | `SkyTelemetry` | `#0284C7` | Thông số đo đạc Telemetry, Độ cao, Thời tiết & Săn mây |

### 3.3. Typography & Vi Tương Tác (`ui/theme/Type.kt` & `ShadcnComponents.kt`)
* **Số liệu đo đạc (`TelemetryTextStyle`)**: Mọi thông số tốc độ (`km/h`), độ cao (`m`), cự ly (`km`), tọa độ GPS và tiền tệ (`đ`) phải dùng `FontFamily.Monospace` (`tabular-nums`) hoặc `RollingNumberText` để số nhảy mượt không bị rung lắc khung.
* **Phản hồi xúc giác & đàn hồi (`springClickable`)**: Dùng modifier `.springClickable { ... }` cho các thẻ, nút nổi và chip lọc để tạo hiệu ứng nhún lò xo vật lý (`scale = 0.96f`, `dampingRatio = 0.6f`).

---

## 4. Kiến Trúc Mã Nguồn Trọng Tâm

```text
app/src/main/
├── assets/
│   └── hagiang_loop_roads.json          # Dữ liệu vector ODbL (5.350 điểm uốn đèo QL4C/ĐT176/QL34 & 5 nhánh phụ)
└── java/com/reed/hagianggo/
    ├── MainActivity.kt                  # Khung không gian chính: FlightyHeader + AnimatedContent 4 Tab + FloatingPillNavBar + Global Sheets
    ├── data/
    │   ├── HaGiangDatabase.kt           # SQLite Schema v4 (10 bảng) + Migration v1..v4
    │   ├── HaGiangRepository.kt         # StateFlow trung tâm quản lý dữ liệu & đồng bộ SQLite
    │   ├── InitialSeedData.kt           # Dữ liệu gốc 8 Cụm chặng, 48 Địa điểm thực tế, Lịch trình 3N2Đ/4N3Đ & Checklist
    │   └── JsonBackupManager.kt         # Xuất / Nhập toàn bộ dữ liệu chuyến đi ra file JSON
    ├── domain/
    │   ├── OfflineMapManager.kt         # Quản lý OSMDroid filesDir + Bộ dựng Bản đồ Địa hình Vector Offline trên thiết bị
    │   ├── OfflineLocationTracker.kt    # Định vị GPS/Network đa nguồn, tự động phục hồi khi bật/tắt Vị trí
    │   ├── LocationMath.kt              # Công thức Haversine, ước tính thời gian chạy đèo, Intent Maps/Gọi điện/SMS 2G
    │   ├── WhatNowEngine.kt             # Động cơ gợi ý "Làm gì bây giờ?" theo cụm, thời tiết, quỹ giờ & phương tiện
    │   ├── FuelRangeGuard.kt            # Giám sát tầm xăng còn lại & cảnh báo trước cung đèo vắng cây xăng
    │   ├── MountainWeatherManager.kt    # Dự báo thời tiết 8 cụm đèo (Open-Meteo + SQLite Cache) & thuật toán % Săn mây
    │   ├── SplitbillEngine.kt           # Tính công nợ nhóm phượt & tối ưu số lần chuyển khoản thanh toán
    │   └── TripRecapPosterGenerator.kt  # Vẽ Offline ảnh Poster tổng kết hành trình 1080×1620 px bằng Canvas 2D
    ├── model/
    │   └── Models.kt                    # Toàn bộ Data Class & Enum của ứng dụng
    ├── service/
    │   └── RideTrackerService.kt        # Foreground Service theo dõi Chế độ Phượt, quãng đường, độ cao & trừ tầm xăng tự động
    └── ui/
        ├── MainViewModel.kt             # ViewModel điều phối trạng thái toàn cục giữa Repository, Services & UI
        ├── components/
        │   ├── ShadcnComponents.kt      # Thư viện UI lõi: ShadcnCard, ShadcnButton, ShadcnBadge, FlightyFloatingHeader, FloatingPillNavBar...
        │   ├── RealMapView.kt           # Bản đồ OSMDroid tương tác: 3 lớp Layer (Chuẩn OSM / OpenTopo / Offline Topo), 1.780 điểm đường đèo
        │   ├── HaGiangLoopMap.kt        # Sơ đồ Vector 8 chặng + Biểu đồ mặt cắt độ cao (Elevation Profile)
        │   ├── LiveRideCockpitSheet.kt  # Màn hình HUD đồng hồ tốc độ, cao độ, tầm xăng & mặt cắt đèo trực tiếp
        │   ├── WhatNowBottomSheet.kt    # BottomSheet AI Đèo Núi gợi ý điểm đi tức thì
        │   ├── MountainSosSheet.kt      # BottomSheet Cứu hộ đèo núi, phát SMS 2G kèm tọa độ & danh bạ thợ sửa xe gần nhất
        │   ├── WeatherCloudSheet.kt     # BottomSheet Thời tiết 8 cụm đèo & Tỷ lệ săn biển mây
        │   ├── TripRecapSheet.kt        # BottomSheet Xem trước, ẩn/hiện chi phí & lưu/chia sẻ Poster Hành trình
        │   ├── OfflineMapPackDialog.kt  # Hộp thoại dựng & kiểm tra gói Bản đồ Địa hình Offline trong filesDir
        │   └── PlaceFormDialog.kt       # Hộp thoại Thêm / Sửa địa điểm tùy chỉnh
        ├── screens/
        │   ├── TripScreen.kt            # Tab 0 (Chuyến đi): Bản đồ tràn viền + Tấm kéo trượt 3 nấc (PEEK / HALF / EXPANDED)
        │   ├── TodayScreen.kt           # Tab 1 (Hôm nay): Bento điều khiển nhanh, Thời tiết săn mây, Cảnh báo xăng, Lịch trình theo ngày
        │   ├── NearbyScreen.kt          # Tab 2 (Gần đây): Radar tìm nhanh Cây xăng / Sửa xe / Trú mưa / Quán ăn theo cự ly thực
        │   └── NotebookScreen.kt        # Tab 3 (Sổ tay): Quản lý Ngân sách, Chia tiền nhóm (Splitbill), Nhật ký, Checklist & Sao lưu
        └── theme/
            ├── Color.kt
            ├── Theme.kt
            └── Type.kt
```

---

## 5. Các Quy Tắc Kỹ Thuật Bắt Buộc (Critical Engineering Rules)

### 5.1. Bản Đồ Offline & Tuân Thủ Chính Sách OpenStreetMap (Chống Lỗi `403 Access Blocked`)
* **User-Agent chuẩn**: Luôn giữ `Configuration.getInstance().userAgentValue = context.packageName` (`"com.reed.hagianggo"`). **TUYỆT ĐỐI KHÔNG** đặt từ khóa `"Offline"`, `"Scraper"` hay `"Bulk"` vào `userAgentValue` (tường lửa Fastly CDN của OSM sẽ chặn và trả về ảnh cảnh báo 403 với mã HTTP 200 làm hỏng `cache.db`).
* **Cấm cào hàng loạt (No Bulk Scraping)**: Không bao giờ dùng vòng lặp tải hàng nghìn mảnh bản đồ từ `tile.openstreetmap.org`, CARTO hay Esri.
  * Khi có mạng: Chỉ để `MapView` tải tự nhiên các ô người dùng đang xem và lưu vĩnh viễn vào `filesDir/osmdroid/tiles/cache.db` (`expirationOverrideDuration = 1 năm`).
  * Khi bấm **"Lưu trọn bộ Hà Giang"**: Sử dụng bộ dựng vector nội bộ trong `OfflineMapManager.renderOfflineTopoTilePng()` để vẽ các mảnh bản đồ địa hình từ `assets/hagiang_loop_roads.json` vào nguồn `HA_GIANG_OFFLINE_TILE_SOURCE` (`"HaGiangOfflineTopo"`, Zoom 8–15).
* **Tự động ngắt mạng khi xem lớp Offline**: Khi hiển thị `MapLayerStyle.OFFLINE_TOPO` hoặc khi mất kết nối Internet (`!hasValidatedInternet`), bắt buộc gọi `mapView.setUseDataConnection(false)` để OSMDroid đọc thẳng từ `SqlTileWriter` trong `0ms` và tự động phóng đại mượt mà (`MapTileApproximater`) khi zoom sâu.

### 5.2. Định Vị GPS Tự Phục Hồi & Trải Nghiệm Ngoài Tỉnh Hà Giang
* **Tự động bắt vị trí (Zero-Restart GPS)**: `OfflineLocationTracker` phải duy trì `BroadcastReceiver` lắng nghe `LocationManager.PROVIDERS_CHANGED_ACTION` kết hợp `onProviderEnabled` và `MainActivity.onResume()`. Khi người dùng bật GPS sau khi đã mở app, tọa độ phải tự động cập nhật ngay lập tức như Google Maps.
* **Khoảng cách an toàn khi ở xa Hà Giang (>85 km)**: Khi người dùng mở app ở tỉnh khác (ví dụ Hà Nội, Hải Dương...), `isOutsideHaGiang` sẽ là `true`. Khi đó:
  * Các thuật toán tính tầm xăng (`FuelRangeGuard`) phải lấy tâm của cụm chặng đang chọn (`activeZone`) làm mốc tham chiếu thay vì lấy khoảng cách hàng trăm km từ tỉnh khác.
  * Khi vẽ Poster tổng kết (`TripRecapPosterGenerator`), chỉ vẽ các vệt tọa độ nằm trong khung giới hạn địa lý của Hà Giang (`lat 22.72..23.45`, `lng 104.80..105.55`).

### 5.3. An Toàn Nâng Cấp Cơ Sở Dữ Liệu (`HaGiangDatabase.kt`)
* Mọi thay đổi cấu trúc bảng trong `HaGiangDatabase` phải tăng `DATABASE_VERSION` và bổ sung khối `if (oldVersion < X)` trong `onUpgrade()` sử dụng `CREATE TABLE IF NOT EXISTS` hoặc `ALTER TABLE ... ADD COLUMN` bọc trong `try-catch`. Không bao giờ `DROP TABLE` làm mất dữ liệu lịch trình, chi tiêu hay địa điểm riêng của người dùng.

---

## 6. Lệnh Phát Triển, Cài Đặt & Kiểm Thử (Windows PowerShell)

> **Lưu ý quan trọng trên Windows PowerShell**:
> 1. Sử dụng dấu chấm phẩy `;` để nối lệnh (không dùng `&&` trên PowerShell 5.1).
> 2. Khi chụp ảnh màn hình qua ADB, **KHÔNG** dùng toán tử chuyển hướng `>` (`adb exec-out screencap -p > file.png`) vì PowerShell sẽ ghi đè mã hóa UTF-16LE làm hỏng file ảnh PNG. Luôn chụp vào `/sdcard/` rồi dùng `adb pull`.

```powershell
# 1. Biên dịch bản Debug APK
.\gradlew.bat assembleDebug

# 2. Cài đặt và khởi chạy lên thiết bị đang kết nối
adb install -r "app\build\outputs\apk\debug\app-debug.apk"
adb shell am start -n com.reed.hagianggo/.MainActivity

# 3. Chụp màn hình thiết bị an toàn (không hỏng định dạng PNG)
adb shell screencap -p /sdcard/screen.png
adb pull /sdcard/screen.png .\screen.png

# 4. Kiểm tra dung lượng kho bản đồ Offline trong filesDir
adb shell run-as com.reed.hagianggo ls -la files/osmdroid/tiles
```
