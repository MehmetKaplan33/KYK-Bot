package com.kyk.mealtracker.scheduler;

import com.kyk.mealtracker.bot.KykMealBot;
import com.kyk.mealtracker.entity.BotUser;
import com.kyk.mealtracker.entity.Meal;
import com.kyk.mealtracker.repository.BotUserRepository;
import com.kyk.mealtracker.services.MealService;
import com.kyk.mealtracker.services.MealSyncService;
import com.kyk.mealtracker.util.CityHelper;
import com.kyk.mealtracker.util.MealValidator;
import com.kyk.mealtracker.util.MenuFormatter;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

@Component
@RequiredArgsConstructor
public class MealScheduler {

    private static final Logger logger = LoggerFactory.getLogger(MealScheduler.class);
    private static final ZoneId TURKEY_ZONE = ZoneId.of("Europe/Istanbul");

    private final MealService mealService;
    private final KykMealBot kykMealBot;
    private final BotUserRepository botUserRepository;
    private final MealSyncService mealSyncService;

    // Ayın 1'inde gece 03:00'te tüm aktif şehirlerin aylık menülerini çek
    @Scheduled(cron = "0 0 3 1 * ?", zone = "Europe/Istanbul")
    public void fetchMonthlyMeals() {
        logger.info("Aylık menü senkronizasyonu başlatılıyor...");
        mealSyncService.fetchMealsForAllActiveCities();
    }

    // Her gün sabah 05:00'te eksik menü kontrolü (günlük tazeleyici)
    @Scheduled(cron = "0 0 5 * * ?", zone = "Europe/Istanbul")
    public void fetchDailyMeals() {
        logger.info("Günlük eksik menü kontrolü yapılıyor...");
        mealSyncService.fetchMealsForAllActiveCities();
    }

    // Her sabah 06:30'da sadece KAHVALTI bildirimi (Kullanıcının kendi şehrine özel)
    @Scheduled(cron = "0 30 6 * * ?", zone = "Europe/Istanbul")
    public void sendBreakfastNotifications() {
        sendMealNotifications(0, "🌅 GÜNAYDIN! BUGÜNÜN KAHVALTISI", "⏰ 06:30 - 12:00");
    }

    // Her gün 14:00'te sadece AKŞAM YEMEĞİ bildirimi (Kullanıcının kendi şehrine özel)
    @Scheduled(cron = "0 0 14 * * ?", zone = "Europe/Istanbul")
    public void sendDinnerNotifications() {
        sendMealNotifications(1, "🍲 AFİYET OLSUN! AKŞAM YEMEĞİ", "⏰ 16:00 - 22:30");
    }

    private void sendMealNotifications(int mealType, String title, String timeRange) {
        LocalDate today = LocalDate.now(TURKEY_ZONE);
        List<BotUser> activeUsers = botUserRepository.findByNotificationsEnabledTrue();
        logger.info("Bildirim gönderimi başlatılıyor. Öğün: {}, Kullanıcı Sayısı: {}", mealType, activeUsers.size());

        for (BotUser user : activeUsers) {
            int cityId = user.getCityId() != null ? user.getCityId() : 1;
            String cityName = CityHelper.getCityName(cityId);

            List<Meal> meals = mealService.getMealsByDateAndType(today, mealType).stream()
                    .filter(m -> m.getCityId() != null && m.getCityId().equals(cityId))
                    .filter(MealValidator::isValidMeal)
                    .toList();

            if (meals.isEmpty()) {
                // Kullanıcının şehrine ait menü yoksa boş başlık atmıyoruz
                continue;
            }

            Meal meal = meals.get(0);
            String text = MenuFormatter.formatSingleMealNotification(today, cityName, meal, title, timeRange);

            try {
                SendMessage sm = SendMessage.builder()
                        .chatId(user.getChatId())
                        .text(text)
                        .parseMode("Markdown")
                        .build();
                kykMealBot.execute(sm);
                Thread.sleep(40); // Telegram flood limit koruması (25 msg/sec)
            } catch (Exception e) {
                logger.error("Bildirim gönderilemedi (ChatId: {}): {}", user.getChatId(), e.getMessage());
            }
        }
        logger.info("Bildirim gönderimi tamamlandı. Öğün: {}", mealType);
    }
}
