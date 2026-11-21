package com.reed.hagianggo.domain

import com.reed.hagianggo.model.GoldenHourAssessment
import com.reed.hagianggo.model.GoldenHourMatch
import com.reed.hagianggo.model.GoldenHourProfile
import com.reed.hagianggo.model.Place
import com.reed.hagianggo.model.PlaceCategory

/**
 * Cố vấn "Khung Giờ Vàng" & Góc Chụp Ảnh Đèo Hà Giang (100% Offline).
 * Đối chiếu trực tiếp giờ dự kiến đến/đi từ TimePacerEngine với khung ánh sáng thực tế tại 20+ điểm nổi bật.
 */
object GoldenHourAdvisor {

    private fun hm(hour: Int, min: Int): Int = hour * 60 + min

    private val CURATED_PROFILES: Map<String, GoldenHourProfile> = mapOf(
        "pl_km0" to GoldenHourProfile(
            bestStartMin = hm(6, 30),
            bestEndMin = hm(9, 0),
            windowLabel = "06:30–09:00",
            lightingReason = "Sáng sớm nắng dịu và vắng đoàn, dễ chụp cột mốc Km0 không dính người.",
            photoAngleTip = "Hạ góc máy ngang yên xe hướng chéo công viên để lấy trọn cột mốc Km0 và hàng cây xanh."
        ),
        "pl_cafe_nui_cam" to GoldenHourProfile(
            bestStartMin = hm(16, 0),
            bestEndMin = hm(17, 30),
            windowLabel = "16:00–17:30",
            lightingReason = "Chiều muộn ngắm hoàng hôn nhuộm vàng dòng sông Lô và toàn cảnh TP. Hà Giang.",
            photoAngleTip = "Ngồi lan can góc Tây Nam, lấy tiền cảnh ly cà phê nhìn xuống thung lũng."
        ),
        "pl_doc_bac_sum" to GoldenHourProfile(
            bestStartMin = hm(6, 30),
            bestEndMin = hm(9, 30),
            windowLabel = "06:30–09:30",
            lightingReason = "Buổi sáng sớm sương nhẹ giăng dưới thung lũng làm nổi bật các tầng dốc chữ S.",
            photoAngleTip = "Chụp từ đài ngắm trên đỉnh dốc hướng xuống trọn 3 tầng cua tay áo Bắc Sum."
        ),
        "pl_cong_troi_quan_ba" to GoldenHourProfile(
            bestStartMin = hm(6, 0),
            bestEndMin = hm(8, 30),
            windowLabel = "06:00–08:30",
            lightingReason = "Giờ vàng săn biển mây cuộn qua khe núi Tam Sơn; sau 10h mây tan hoặc nắng gắt.",
            photoAngleTip = "Đứng ở bậc thang lầu vọng cảnh trên cao nhìn xuyên khe núi về phía thung lũng."
        ),
        "pl_cafe_cong_troi" to GoldenHourProfile(
            bestStartMin = hm(6, 30),
            bestEndMin = hm(9, 0),
            windowLabel = "06:30–09:00",
            lightingReason = "Sáng sớm ban công ngập biển mây rất ăn ảnh cùng tách trà thảo dược nóng.",
            photoAngleTip = "Lấy khung cửa gỗ làm viền tự nhiên nhìn ra triền mây Quản Bạ."
        ),
        "pl_nui_doi_quan_ba" to GoldenHourProfile(
            bestStartMin = hm(7, 0),
            bestEndMin = hm(10, 0),
            windowLabel = "07:00–10:00",
            lightingReason = "Nắng xiên buổi sáng giúp ruộng lúa quanh Núi Đôi lên màu xanh mướt và nổi khối rõ.",
            photoAngleTip = "Dùng zoom 2x từ chòi ngắm để 2 đỉnh núi tròn nổi bật trên nền thung lũng Tam Sơn."
        ),
        "pl_cay_nghien_co_don" to GoldenHourProfile(
            bestStartMin = hm(8, 0),
            bestEndMin = hm(11, 0),
            windowLabel = "08:00–11:00",
            lightingReason = "Ánh sáng hướng Đông Nam chiếu thuận lên thân cây và vách đá vôi xám phía sau.",
            photoAngleTip = "Chụp từ mép đường bên đối diện, đặt cây lệch 1/3 khung hình cạnh dải đường đèo."
        ),
        "pl_rung_thong_yen_minh" to GoldenHourProfile(
            bestStartMin = hm(8, 30),
            bestEndMin = hm(11, 30),
            windowLabel = "08:30–11:30",
            lightingReason = "Tia nắng xuyên qua tán thông cao rọi vệt sáng đẹp giữa thảm cỏ và dải đường nhựa.",
            photoAngleTip = "Chụp dọc theo cung đường thông uốn cong ở độ mở khẩu nhỏ để thấy tia nắng xuyên lá."
        ),
        "pl_doc_tham_ma" to GoldenHourProfile(
            bestStartMin = hm(7, 30),
            bestEndMin = hm(10, 30),
            windowLabel = "07:30–10:30",
            lightingReason = "Buổi sáng nắng chiếu thuận sườn núi bên phải, làm rõ nét 9 tầng cua dốc Thẩm Mã.",
            photoAngleTip = "Đứng ở bãi dừng đỉnh dốc hướng ống kính xuống trọn vẹn khúc cua uốn lượn phía dưới."
        ),
        "pl_nha_cua_pao" to GoldenHourProfile(
            bestStartMin = hm(8, 30),
            bestEndMin = hm(11, 30),
            windowLabel = "08:30–11:30",
            lightingReason = "Sáng muộn thung lũng Sủng Là ngập nắng ấm, tường trình vàng và cổng gỗ cực nét.",
            photoAngleTip = "Chụp chính diện qua cổng gỗ mái ngói âm dương hướng ra vườn hoa trước sân."
        ),
        "pl_pho_bang_co_tran" to GoldenHourProfile(
            bestStartMin = hm(8, 0),
            bestEndMin = hm(11, 0),
            windowLabel = "08:00–11:00",
            lightingReason = "Buổi sáng không gian thị trấn cổ yên tĩnh, ánh nắng dịu tôn màu tường gạch mộc.",
            photoAngleTip = "Đi dọc tuyến phố nhà trình tường vàng có giấy đỏ dán cửa, chụp chiều sâu con ngõ."
        ),
        "pl_dinh_vua_meo" to GoldenHourProfile(
            bestStartMin = hm(9, 0),
            bestEndMin = hm(15, 0),
            windowLabel = "09:00–15:00",
            lightingReason = "Giữa trưa và đầu chiều ánh sáng lọt sâu vào giếng trời và sân đá dinh thự không bị tối.",
            photoAngleTip = "Chụp từ hành lang tầng 2 nhìn xuống sân giếng trời hoặc từ ngoài cổng nhìn hàng sa mộc cổ."
        ),
        "pl_cot_co_lung_cu" to GoldenHourProfile(
            bestStartMin = hm(7, 30),
            bestEndMin = hm(10, 30),
            windowLabel = "07:30–10:30",
            lightingReason = "Buổi sáng trời trong nhất, nhìn rõ 2 hồ mắt rồng và gió nhẹ giúp lá cờ tung bay đẹp.",
            photoAngleTip = "Hướng từ bậc đá gần chân kỳ đài lên lấy toàn bộ lá cờ đỏ trên nền trời xanh."
        ),
        "pl_lo_lo_chai" to GoldenHourProfile(
            bestStartMin = hm(15, 0),
            bestEndMin = hm(17, 15),
            windowLabel = "15:00–17:15",
            lightingReason = "Nắng chiều tà chiếu vàng rực lên mái ngói âm dương và tường rào đá bản Lô Lô Chải.",
            photoAngleTip = "Đứng từ ngõ đá cổ hướng ngược lên Cột cờ Lũng Cú phía xa sau rặng núi."
        ),
        "pl_cafe_cuc_bac" to GoldenHourProfile(
            bestStartMin = hm(15, 0),
            bestEndMin = hm(17, 15),
            windowLabel = "15:00–17:15",
            lightingReason = "Hoàng hôn cực Bắc buông xuống bản nhà trình tường tạo ánh vàng ấm rất điện ảnh.",
            photoAngleTip = "Chụp từ hiên nhà trình tường vàng cùng lá cờ Tổ quốc và ly cà phê phin."
        ),
        "pl_pho_co_dong_van" to GoldenHourProfile(
            bestStartMin = hm(16, 0),
            bestEndMin = hm(20, 30),
            windowLabel = "16:00–20:30",
            lightingReason = "Đẹp nhất lúc chuyển giao hoàng hôn sang tối khi đèn lồng đỏ phố cổ bật sáng.",
            photoAngleTip = "Chụp ở sân giữa trước khu chợ cổ và dãy nhà cổ trên 100 năm tuổi."
        ),
        "pl_deo_ma_pi_leng" to GoldenHourProfile(
            bestStartMin = hm(8, 30),
            bestEndMin = hm(11, 30),
            windowLabel = "08:30–11:30",
            lightingReason = "Sáng muộn ánh nắng rọi sáng dòng sông Nho Quế bên dưới mà chưa bị sương mù chiều che phủ.",
            photoAngleTip = "Đứng ở mỏm đá đài dừng chân Mã Pì Lèng lấy độ sâu hẻm vực chữ V và sông uốn khúc."
        ),
        "pl_vach_da_trang" to GoldenHourProfile(
            bestStartMin = hm(8, 0),
            bestEndMin = hm(11, 30),
            windowLabel = "08:00–11:30",
            lightingReason = "Buổi sáng vắng gió mạnh, vách đá trắng hứng nắng rất sáng và an toàn khi đi bộ đường mòn.",
            photoAngleTip = "Lấy dải đường mòn bám vách đá uốn quanh triền núi nhìn xuống hẻm Tu Sản."
        ),
        "pl_cafe_panorama" to GoldenHourProfile(
            bestStartMin = hm(9, 0),
            bestEndMin = hm(15, 0),
            windowLabel = "09:00–15:00",
            lightingReason = "Nắng cao chiếu xuống đáy hẻm vực tạo tương phản xanh ngọc nổi bật cho ảnh nhìn từ ban công.",
            photoAngleTip = "Chụp chếch từ tầng ban công hướng về phía hẻm núi bên trái."
        ),
        "pl_ben_thuyen_ta_lang" to GoldenHourProfile(
            bestStartMin = hm(9, 30),
            bestEndMin = hm(14, 0),
            windowLabel = "09:30–14:00",
            lightingReason = "Nắng đứng chiếu thẳng xuống hẻm vực Tu Sản giúp mặt sông lên đúng màu xanh ngọc bích; sau 15:30 hẻm tối sầm.",
            photoAngleTip = "Đứng ở mũi thuyền ngay giữa hai vách đá dựng đứng hẻm Tu Sản chụp góc rộng 0.5x–1x."
        ),
        "pl_cua_chu_m" to GoldenHourProfile(
            bestStartMin = hm(8, 0),
            bestEndMin = hm(11, 30),
            windowLabel = "08:00–11:30",
            lightingReason = "Nắng sáng chiếu từ phía Đông làm nổi rõ đường nhựa chữ M giữa sườn núi đá vôi.",
            photoAngleTip = "Chụp từ chòi tre cao trên cua nhìn trọn 2 vòng cua tay áo kép."
        ),
        "pl_thac_du_gia" to GoldenHourProfile(
            bestStartMin = hm(10, 0),
            bestEndMin = hm(14, 30),
            windowLabel = "10:00–14:30",
            lightingReason = "Giữa ngày nắng rọi lọt khe suối, nước thác trong xanh nhất và bớt lạnh nếu tắm suối.",
            photoAngleTip = "Đứng bên phiến đá thấp phía hạ lưu để lấy trọn tầng thác đổ và hồ nước xanh."
        ),
        "pl_lang_pa_vi" to GoldenHourProfile(
            bestStartMin = hm(15, 30),
            bestEndMin = hm(17, 30),
            windowLabel = "15:30–17:30",
            lightingReason = "Chiều muộn nắng dịu phía sau đỉnh Mã Pì Lèng rọi xuống khu làng lục giác rực rỡ hoa đào/tam giác mạch.",
            photoAngleTip = "Chụp từ cổng chào hoặc đoạn đường chính xuyên qua hai dãy nhà trình tường."
        )
    )

    private val GENERIC_VIEWPOINT_PROFILE = GoldenHourProfile(
        bestStartMin = hm(7, 0),
        bestEndMin = hm(10, 30),
        windowLabel = "07:00–10:30",
        lightingReason = "Ánh nắng xiên buổi sáng giúp dãy núi đá tai mèo lên khối rõ, màu trời trong và ít sương mù.",
        photoAngleTip = "Đứng chếch 45° so với hướng mặt trời, tận dụng dải đường đèo hoặc hàng rào đá làm tiền cảnh."
    )

    fun getProfileFor(place: Place): GoldenHourProfile? {
        if (place.category == PlaceCategory.FUEL || place.category == PlaceCategory.MECHANIC_SOS) {
            return null
        }
        CURATED_PROFILES[place.id]?.let { return it }
        return if (place.category == PlaceCategory.CHECKIN_VIEW) {
            GENERIC_VIEWPOINT_PROFILE
        } else {
            null
        }
    }

    fun evaluate(
        place: Place,
        arrivalMin: Int,
        departMin: Int
    ): GoldenHourAssessment? {
        val profile = getProfileFor(place) ?: return null
        val safeDepart = departMin.coerceAtLeast(arrivalMin)
        val overlaps = arrivalMin <= profile.bestEndMin && safeDepart >= profile.bestStartMin

        return when {
            overlaps -> GoldenHourAssessment(
                profile = profile,
                match = GoldenHourMatch.OPTIMAL,
                statusLabel = "☀️ Trúng giờ vàng (${profile.windowLabel})",
                detailHint = profile.lightingReason
            )
            safeDepart < profile.bestStartMin -> GoldenHourAssessment(
                profile = profile,
                match = GoldenHourMatch.SUBOPTIMAL_EARLY,
                statusLabel = "🌤️ Đẹp nhất ${profile.windowLabel}",
                detailHint = "Dự kiến đến sớm lúc ${TimePacerEngine.formatMinutesOfDay(arrivalMin)}. ${profile.lightingReason}"
            )
            else -> GoldenHourAssessment(
                profile = profile,
                match = GoldenHourMatch.SUBOPTIMAL_LATE,
                statusLabel = "🌤️ Đẹp nhất ${profile.windowLabel}",
                detailHint = "Dự kiến đến lúc ${TimePacerEngine.formatMinutesOfDay(arrivalMin)} hơi muộn so với nắng đẹp. ${profile.lightingReason}"
            )
        }
    }
}
