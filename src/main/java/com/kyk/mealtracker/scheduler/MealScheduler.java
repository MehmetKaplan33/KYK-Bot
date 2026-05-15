package com.kyk.mealtracker.scheduler;

import com.kyk.mealtracker.bot.KykMealBot;
import com.kyk.mealtracker.entity.BotUser;
import com.kyk.mealtracker.entity.Meal;
import com.kyk.mealtracker.repository.BotUserRepository;
import com.kyk.mealtracker.services.MealService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

@Component
@RequiredArgsConstructor
public class MealScheduler {

    private final MealService mealService;
    private final KykMealBot kykMealBot;
    private final BotUserRepository botUserRepository;
    private final com.kyk.mealtracker.services.MealSyncService mealSyncService;
    private static final Logger logger = LoggerFactory.getLogger(MealScheduler.class);

    // Her gün sabah 06:00'da menüleri çek
    @Scheduled(cron = "0 0 6 * * ?")
    public void fetchDailyMeals() {
        mealSyncService.fetchMealsForAllActiveCities();
    }

    // Her sabah 06:30'de sadece KAHVALTI menüsü bildirimi gönder
    @Scheduled(cron = "0 30 6 * * ?")
    public void sendBreakfastNotifications() {
        LocalDate today = LocalDate.now();
        List<Meal> todaysMeals = mealService.getMealsByDate(today);

        List<Meal> validMeals = todaysMeals.stream()
                .filter(this::isValidMealForDisplay)
                .toList();

        if (validMeals.isEmpty()) {
            logger.info("Bugün için geçerli menü bulunamadı: {}", today);
            return;
        }

        // Bildirimleri açık olan tüm kullanıcıları bul
        List<BotUser> activeUsers = botUserRepository.findByNotificationsEnabledTrue();

        for (BotUser user : activeUsers) {
            try {
                StringBuilder messageBuilder = new StringBuilder();
                String dateStr = today.format(DateTimeFormatter.ofPattern("dd MMMM yyyy", new Locale("tr")));

                messageBuilder.append("🌟 Günaydın! İşte bugünün kahvaltı menüsü:\n\n");
                messageBuilder.append("📅 ").append(dateStr).append("\n\n");

                // Kahvaltı menüsü
                validMeals.stream()
                        .filter(meal -> meal.getMealType() == 0)
                        .findFirst()
                        .ifPresent(breakfast -> appendMealDetails(messageBuilder, "🌅 KAHVALTI", breakfast));

                kykMealBot.execute(org.telegram.telegrambots.meta.api.methods.send.SendMessage.builder()
                        .chatId(user.getChatId())
                        .text(messageBuilder.toString())
                        .build());

                logger.info("Kahvaltı bildirimi gönderildi - ChatId: {}", user.getChatId());

            } catch (Exception e) {
                logger.error("Kahvaltı bildirimi gönderilemedi - ChatId: " + user.getChatId(), e);
            }
        }
    }

    // Her öğleden sonra 14:00'te sadece AKŞAM YEMEĞİ bildirimi gönder
    @Scheduled(cron = "0 0 14 * * ?")
    public void sendDinnerNotifications() {
        LocalDate today = LocalDate.now();
        List<Meal> todaysMeals = mealService.getMealsByDate(today);

        List<Meal> validMeals = todaysMeals.stream()
                .filter(this::isValidMealForDisplay)
                .toList();

        if (validMeals.isEmpty()) {
            logger.info("Bugün için geçerli menü bulunamadı: {}", today);
            return;
        }

        // Bildirimleri açık olan tüm kullanıcıları bul
        List<BotUser> activeUsers = botUserRepository.findByNotificationsEnabledTrue();

        for (BotUser user : activeUsers) {
            try {
                StringBuilder messageBuilder = new StringBuilder();
                String dateStr = today.format(DateTimeFormatter.ofPattern("dd MMMM yyyy", new Locale("tr")));

                messageBuilder.append("🌟 Afiyet olsun! İşte bugünün akşam yemeği menüsü:\n\n");
                messageBuilder.append("📅 ").append(dateStr).append("\n\n");

                // Akşam yemeği menüsü
                validMeals.stream()
                        .filter(meal -> meal.getMealType() == 1)
                        .findFirst()
                        .ifPresent(dinner -> appendMealDetails(messageBuilder, "🌙 AKŞAM YEMEĞİ", dinner));

                kykMealBot.execute(org.telegram.telegrambots.meta.api.methods.send.SendMessage.builder()
                        .chatId(user.getChatId())
                        .text(messageBuilder.toString())
                        .build());

                logger.info("Akşam yemeği bildirimi gönderildi - ChatId: {}", user.getChatId());

            } catch (Exception e) {
                logger.error("Akşam yemeği bildirimi gönderilemedi - ChatId: " + user.getChatId(), e);
            }
        }
    }

    private boolean isValidMealForDisplay(Meal meal) {
        if (meal == null) return false;
        String[] items = {meal.getFirst(), meal.getSecond(), meal.getThird(), meal.getFourth()};
        int validItemCount = 0;
        for (String item : items) {
            if (item == null || item.trim().isEmpty()) continue;
            String lowerItem = item.toLowerCase().trim();
            if (lowerItem.contains("@") || lowerItem.contains("mail") || lowerItem.contains("gönderip") ||
                lowerItem.contains("katkı sağla") || lowerItem.contains("uygulamaya") ||
                lowerItem.contains("listesini") || lowerItem.contains("daha hızlı") ||
                lowerItem.contains("girilmesine")) return false;
            if (item.trim().length() >= 3 && item.trim().length() <= 100) validItemCount++;
        }
        return validItemCount >= 3;
    }

    private void appendMealDetails(StringBuilder builder, String title, Meal meal) {
        String totalCal = meal.getTotalCalories(); // Integer yerine String

        builder.append(title);
        if (totalCal != null && !totalCal.trim().isEmpty()) {
            builder.append(" (").append(totalCal).append(" kcal)");
        }
        builder.append("\n");

        appendMealItem(builder, meal.getFirst(), meal.getFirstCalories());
        appendMealItem(builder, meal.getSecond(), meal.getSecondCalories());
        appendMealItem(builder, meal.getThird(), meal.getThirdCalories());
        appendMealItem(builder, meal.getFourth(), meal.getFourthCalories());
    }

    private void appendMealItem(StringBuilder builder, String item, String calories) {
        if (item == null || item.trim().isEmpty()) return;

        builder.append("• ").append(item);
        if (calories != null && !calories.trim().isEmpty()) {
            builder.append(" (").append(calories).append(" kcal)");
        }
        builder.append("\n");
    }

}