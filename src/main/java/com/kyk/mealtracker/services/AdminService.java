package com.kyk.mealtracker.services;

import com.kyk.mealtracker.entity.BotUser;
import com.kyk.mealtracker.repository.BotUserRepository;
import com.kyk.mealtracker.repository.MealRatingRepository;
import com.kyk.mealtracker.repository.MealRepository;
import com.kyk.mealtracker.util.CityHelper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AdminService {

    private static final ZoneId TURKEY_ZONE = ZoneId.of("Europe/Istanbul");

    private final BotUserRepository botUserRepository;
    private final MealRepository mealRepository;
    private final MealRatingRepository mealRatingRepository;

    public boolean isAdmin(Long chatId) {
        // Eğer veritabanında henüz hiç admin yoksa, bota ilk yazan kişi otomatik olarak admin sayılır
        long adminCount = botUserRepository.countByIsAdminTrue();
        if (adminCount == 0) {
            botUserRepository.findById(chatId).ifPresent(u -> {
                u.setIsAdmin(true);
                botUserRepository.save(u);
            });
            return true;
        }

        return botUserRepository.findById(chatId)
                .map(user -> user.getIsAdmin() != null && user.getIsAdmin())
                .orElse(false);
    }

    public String getBotStats() {
        LocalDateTime now = LocalDateTime.now(TURKEY_ZONE);
        LocalDate today = LocalDate.now(TURKEY_ZONE);

        long totalUsers = botUserRepository.count();
        long adminCount = botUserRepository.countByIsAdminTrue();
        long activeNotifications = botUserRepository.countByNotificationsEnabledTrue();
        long disabledNotifications = Math.max(0, totalUsers - activeNotifications);
        double notifPercent = totalUsers > 0 ? (activeNotifications * 100.0 / totalUsers) : 0.0;

        long active24h = botUserRepository.countByLastActivityDateAfter(now.minusHours(24));
        long active7d = botUserRepository.countByLastActivityDateAfter(now.minusDays(7));
        long active30d = botUserRepository.countByLastActivityDateAfter(now.minusDays(30));
        long new24h = botUserRepository.countByLastInteractionDateAfter(now.minusHours(24));

        // Şehir dağılımı (Top 5)
        List<Object[]> cityCounts = botUserRepository.countUsersByCity();
        int activeCityCount = cityCounts.size();
        StringBuilder topCitiesSb = new StringBuilder();
        String[] medals = {"🥇", "🥈", "🥉", "4️⃣", "5️⃣"};
        for (int i = 0; i < Math.min(5, cityCounts.size()); i++) {
            Object[] row = cityCounts.get(i);
            int plate = ((Number) row[0]).intValue();
            long count = ((Number) row[1]).longValue();
            double pct = totalUsers > 0 ? (count * 100.0 / totalUsers) : 0.0;
            topCitiesSb.append(String.format("%s *%02d %s:* %d öğrenci _(%%%.0f)_\n",
                    medals[i], plate, CityHelper.getCityName(plate), count, pct));
        }
        if (topCitiesSb.isEmpty()) {
            topCitiesSb.append("_Henüz şehir verisi yok_\n");
        }

        // Menü & Veritabanı durumu
        long totalMeals = mealRepository.count();
        long todayReadyCities = mealRepository.countDistinctCitiesWithMealsOnDate(today);

        // Yemek Puanlama (Memnuniyet) istatistikleri
        long totalRatings = mealRatingRepository.count();
        long todayRatings = mealRatingRepository.countByDate(today);
        Double todayAvg = mealRatingRepository.getOverallAverageScoreByDate(today);
        Double allTimeAvg = mealRatingRepository.getOverallAverageScoreAllTime();

        String todayRatingStr = (todayAvg != null && todayRatings > 0)
                ? String.format("⭐ %.1f / 5.0 (%d oy)", todayAvg, todayRatings)
                : "Henüz oy yok";
        String allTimeRatingStr = (allTimeAvg != null && totalRatings > 0)
                ? String.format("⭐ %.1f / 5.0 (%d oy)", allTimeAvg, totalRatings)
                : "Henüz oy yok";

        String dateStr = today.format(DateTimeFormatter.ofPattern("dd.MM.yyyy"));

        return String.format("""
                📊 *KYK BOT 2.0 — YÖNETİCİ İSTATİSTİKLERİ*
                ━━━━━━━━━━━━━━━━━━━━━━

                👥 *Kullanıcı & Bildirim Durumu*
                • Toplam Öğrenci: *%d* _(👑 %d Admin)_
                • 🔔 Bildirimi Açık: *%d* _(%%%.0f)_
                • 🔕 Bildirimi Kapalı: *%d*

                ⚡ *Aktiflik & Büyüme*
                • Son 24 Saat Aktif: *%d* _(➕ %d Yeni Kayıt)_
                • Son 7 Gün Aktif: *%d*
                • Son 30 Gün Aktif: *%d*

                📍 *En Yoğun İlk 5 Şehir (%d/81 İl Aktif)*
                %s
                🍽️ *Menü Havuzu (%s)*
                • Kayıtlı Toplam Öğün: *%d*
                • Bugün Menüsü Hazır İl: *%d / %d aktif il*

                ⭐ *Yemek Memnuniyet Ortalaması*
                • Bugün: *%s*
                • Tüm Zamanlar: *%s*
                """,
                totalUsers, adminCount,
                activeNotifications, notifPercent,
                disabledNotifications,
                active24h, new24h,
                active7d,
                active30d,
                activeCityCount,
                topCitiesSb.toString(),
                dateStr,
                totalMeals,
                todayReadyCities, Math.max(1, activeCityCount),
                todayRatingStr,
                allTimeRatingStr
        );
    }

    public int getTotalUserPages() {
        long count = botUserRepository.count();
        return (int) Math.max(1, Math.ceil(count / 10.0));
    }

    public String getUserList(int page) {
        int safePage = Math.max(0, page);
        Page<BotUser> users = botUserRepository.findAll(
                PageRequest.of(safePage, 10, Sort.by(Sort.Direction.DESC, "lastActivityDate"))
        );
        int totalPages = Math.max(1, users.getTotalPages());

        StringBuilder message = new StringBuilder();
        message.append("👥 Kullanıcı Listesi (Toplam: ").append(users.getTotalElements()).append(")\n");
        message.append("━━━━━━━━━━━━━━━━━━━━━━\n\n");

        users.getContent().forEach(user -> {
            String adminBadge = (user.getIsAdmin() != null && user.getIsAdmin()) ? "👑 [ADMİN] " : "👤 ";
            int plate = user.getCityId() != null ? user.getCityId() : 1;
            String cityName = CityHelper.getCityName(plate);
            int bfHour = user.getBreakfastHour() != null ? user.getBreakfastHour() : 7;
            int dnHour = user.getDinnerHour() != null ? user.getDinnerHour() : 15;

            String notifInfo = user.isNotificationsEnabled()
                    ? String.format("Açık ✅ (%02d:00 / %02d:00)", bfHour, dnHour)
                    : "Kapalı ❌";

            message.append(String.format("%s%s %s (@%s)\n🆔 Chat ID: %d\n📍 Şehir: %02d - %s\n🔔 Bildirim: %s\n\n",
                    adminBadge,
                    user.getFirstName() != null ? user.getFirstName() : "İsimsiz",
                    user.getLastName() != null ? user.getLastName() : "",
                    user.getUsername() != null ? user.getUsername() : "kullanici_adi_yok",
                    user.getChatId(),
                    plate,
                    cityName,
                    notifInfo));
        });

        message.append(String.format("📄 Sayfa %d / %d (Son aktifliğe göre sıralı)", safePage + 1, totalPages));
        return message.toString();
    }

    public List<BotUser> getAllAdmins() {
        return botUserRepository.findByIsAdminTrue();
    }

    public String formatAdminList(Long requesterChatId) {
        List<BotUser> admins = getAllAdmins();
        StringBuilder sb = new StringBuilder("👑 *Mevcut Yöneticiler (" + admins.size() + ")*\n");
        sb.append("━━━━━━━━━━━━━━━━━━━━━━\n\n");

        for (BotUser admin : admins) {
            boolean isSelf = admin.getChatId().equals(requesterChatId);
            String name = (admin.getFirstName() != null ? admin.getFirstName() : "")
                    + (admin.getLastName() != null ? " " + admin.getLastName() : "");
            String uname = admin.getUsername() != null ? "@" + admin.getUsername() : "kullanıcı adı yok";
            sb.append("• *").append(name.trim().isEmpty() ? "İsimsiz" : name.trim()).append("* (").append(uname).append(")\n");
            sb.append("  🆔 `").append(admin.getChatId()).append("`").append(isSelf ? " _(Siz)_" : "").append("\n\n");
        }

        sb.append("💡 _Aşağıdaki butonlardan mevcut bir adminin yetkisini alabilir veya yeni admin ekleyebilirsiniz._");
        return sb.toString();
    }

    @Transactional
    public java.util.Optional<BotUser> grantAdmin(String identifier) {
        if (identifier == null || identifier.isBlank()) return java.util.Optional.empty();
        String clean = identifier.trim();
        java.util.Optional<BotUser> userOpt;

        if (clean.startsWith("@")) {
            userOpt = botUserRepository.findFirstByUsernameIgnoreCase(clean.substring(1));
        } else {
            try {
                Long targetChatId = Long.parseLong(clean);
                userOpt = botUserRepository.findById(targetChatId);
            } catch (NumberFormatException e) {
                userOpt = botUserRepository.findFirstByUsernameIgnoreCase(clean);
            }
        }

        userOpt.ifPresent(u -> {
            u.setIsAdmin(true);
            botUserRepository.save(u);
        });
        return userOpt;
    }

    @Transactional
    public java.util.Optional<BotUser> revokeAdmin(Long targetChatId, Long requesterChatId) {
        if (targetChatId == null || targetChatId.equals(requesterChatId)) {
            return java.util.Optional.empty(); // Kendi yetkisini yanlışlıkla silemesin
        }
        java.util.Optional<BotUser> userOpt = botUserRepository.findById(targetChatId);
        userOpt.ifPresent(u -> {
            u.setIsAdmin(false);
            botUserRepository.save(u);
        });
        return userOpt;
    }

    @Transactional
    public void broadcastMessage(String message) {
        List<BotUser> allUsers = botUserRepository.findAll();
        // Bu liste KykMealBot'a gönderilecek
    }
}
