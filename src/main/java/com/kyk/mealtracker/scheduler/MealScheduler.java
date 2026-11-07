package com.kyk.mealtracker.scheduler;

import com.kyk.mealtracker.bot.BotKeyboardHelper;
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

    /**
     * Uygulama (Coolify/Prod) ayağa kalktığında:
     * 1. Eski sürümden kalan kullanıcıların yeni sütunlarını (breakfast_hour=7, dinner_hour=15, city_id=1) güvenle doldurur.
     * 2. Aktif şehirlerin menülerini arka planda senkronize eder.
     */
    @org.springframework.context.event.EventListener(org.springframework.boot.context.event.ApplicationReadyEvent.class)
    public void onStartup() {
        try {
            List<BotUser> allUsers = botUserRepository.findAll();
            List<BotUser> toUpdate = new java.util.ArrayList<>();
            for (BotUser u : allUsers) {
                boolean changed = false;
                if (u.getCityId() == null) { u.setCityId(1); changed = true; }
                if (u.getBreakfastHour() == null) { u.setBreakfastHour(7); changed = true; }
                if (u.getDinnerHour() == null) { u.setDinnerHour(15); changed = true; }
                if (u.getIsAdmin() == null) { u.setIsAdmin(false); changed = true; }
                if (changed) toUpdate.add(u);
            }
            if (!toUpdate.isEmpty()) {
                botUserRepository.saveAll(toUpdate);
                logger.info("Eski sürümden {} kullanıcının varsayılan bildirim saatleri ve şehir bilgileri güncellendi.", toUpdate.size());
            }
        } catch (Exception e) {
            logger.warn("Kullanıcı varsayılan değer taşıma uyarısı: {}", e.getMessage());
        }

        java.util.concurrent.CompletableFuture.runAsync(() -> {
            try {
                mealSyncService.fetchMealsForAllActiveCities();
            } catch (Exception e) {
                logger.warn("Başlangıç menü senkronizasyonu hatası: {}", e.getMessage());
            }
        });
    }

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

    // Her saat başı çalışır ve o saat dilimini seçmiş kullanıcılara bildirim gönderir
    @Scheduled(cron = "0 0 * * * ?", zone = "Europe/Istanbul")
    public void dispatchHourlyCustomNotifications() {
        java.time.ZonedDateTime nowTr = java.time.ZonedDateTime.now(TURKEY_ZONE);
        int currentHour = nowTr.getHour();
        LocalDate today = nowTr.toLocalDate();

        // Sabah Kahvaltısı: 06:00 - 11:00 arası bu saati seçenlere gönder
        if (currentHour >= 6 && currentHour <= 11) {
            List<BotUser> breakfastUsers = botUserRepository.findByNotificationsEnabledTrueAndBreakfastHour(currentHour);
            if (!breakfastUsers.isEmpty()) {
                logger.info("Saat {}:00 kahvaltı bildirimleri gönderiliyor (Kullanıcı Sayısı: {})", currentHour, breakfastUsers.size());
                sendMealNotificationsToUsers(breakfastUsers, 0, today, "🍳 GÜNAYDIN! GÜNÜN KAHVALTISI", "⏰ 06:00 - 12:00");
            }
        }

        // Akşam Yemeği: 14:00 - 19:00 arası bu saati seçenlere gönder
        if (currentHour >= 14 && currentHour <= 19) {
            List<BotUser> dinnerUsers = botUserRepository.findByNotificationsEnabledTrueAndDinnerHour(currentHour);
            if (!dinnerUsers.isEmpty()) {
                logger.info("Saat {}:00 akşam yemeği bildirimleri gönderiliyor (Kullanıcı Sayısı: {})", currentHour, dinnerUsers.size());
                sendMealNotificationsToUsers(dinnerUsers, 1, today, "🍲 AFİYET OLSUN! AKŞAM YEMEĞİ", "⏰ 16:00 - 23:00");
            }
        }
    }

    private void sendMealNotificationsToUsers(List<BotUser> users, int mealType, LocalDate today, String title, String timeRange) {
        for (BotUser user : users) {
            int cityId = user.getCityId() != null ? user.getCityId() : 1;
            String cityName = CityHelper.getCityName(cityId);

            List<Meal> meals = mealService.getMealsByDateAndType(today, mealType).stream()
                    .filter(m -> m.getCityId() != null && m.getCityId().equals(cityId))
                    .filter(MealValidator::isValidMeal)
                    .toList();

            if (meals.isEmpty()) {
                meals = mealSyncService.ensureMealsForDateAndCity(today, cityId).stream()
                        .filter(m -> m.getMealType() != null && m.getMealType().equals(mealType))
                        .filter(MealValidator::isValidMeal)
                        .toList();
            }

            if (meals.isEmpty()) continue;

            Meal meal = meals.get(0);
            String text = MenuFormatter.formatSingleMealNotification(today, cityName, meal, title, timeRange);

            try {
                SendMessage sm = SendMessage.builder()
                        .chatId(user.getChatId())
                        .text(text)
                        .parseMode("Markdown")
                        .replyMarkup(BotKeyboardHelper.getMealActionKeyboard(today, cityId))
                        .build();
                kykMealBot.execute(sm);
                Thread.sleep(40);
            } catch (Exception e) {
                logger.error("Bildirim hatası (ChatId: {}): {}", user.getChatId(), e.getMessage());
            }
        }
    }
}
