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
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

@Component
@RequiredArgsConstructor
public class MealScheduler {

    private final MealService mealService;
    private final RestTemplate restTemplate;
    private final KykMealBot kykMealBot;
    private final BotUserRepository botUserRepository;
    private static final Logger logger = LoggerFactory.getLogger(MealScheduler.class);

    // Her gün sabah 06:00'da menüleri çek
    @Scheduled(cron = "0 0 6 * * ?")
    public void fetchDailyMeals() {
        LocalDate today = LocalDate.now();
        LocalDate endOfMonth = today.withDayOfMonth(today.lengthOfMonth());

        logger.info("Günlük yemek kontrolü başlatılıyor... Tarih: {}", today);

        // Bugünden ay sonuna kadar olan tarihleri kontrol et
        for (LocalDate date = today; !date.isAfter(endOfMonth); date = date.plusDays(1)) {
            try {
                // Kahvaltı menüsü
                checkAndSaveMeal(date, 0);
                // Akşam yemeği menüsü
                checkAndSaveMeal(date, 1);

                logger.info("{} tarihli menüler kontrol edildi.", date);
            } catch (Exception e) {
                logger.error("{} tarihli menü kontrolünde hata: {}", date, e.getMessage());
            }
        }

        logger.info("Günlük yemek kontrolü tamamlandı.");
    }

    // Her sabah 06:30'de sadece KAHVALTI menüsü bildirimi gönder
    @Scheduled(cron = "0 30 6 * * ?")
    public void sendBreakfastNotifications() {
        LocalDate today = LocalDate.now();
        List<Meal> todaysMeals = mealService.getMealsByDate(today);

        // Geçerli menüleri filtrele
        List<Meal> validMeals = todaysMeals.stream()
                .filter(this::isValidMeal)
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

        // Geçerli menüleri filtrele
        List<Meal> validMeals = todaysMeals.stream()
                .filter(this::isValidMeal)
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

    private void checkAndSaveMeal(LocalDate date, int mealType) {
        String url = String.format("https://kykyemekliste.com/api/menu/liste?cityId=1&mealType=%d", mealType);
        try {
            Meal[] meals = restTemplate.getForObject(url, Meal[].class);
            if (meals != null) {
                Arrays.stream(meals)
                        .filter(meal -> meal.getDate().equals(date))
                        .filter(this::isValidMeal)
                        .findFirst()
                        .ifPresent(mealService::saveMealIfNotExists);
            }
        } catch (Exception e) {
            logger.error("API çağrısı hatası - URL: {}, Hata: {}", url, e.getMessage());
            throw e;
        }
    }

    // Yemeğin geçerli olup olmadığını kontrol eder
    private boolean isValidMeal(Meal meal) {
        if (meal == null) {
            return false;
        }

        // Tüm yemek alanlarını kontrol et
        String[] items = {
                meal.getFirst(),
                meal.getSecond(),
                meal.getThird(),
                meal.getFourth()
        };

        // Null veya boş kontrolü
        int validItemCount = 0;
        for (String item : items) {
            if (item == null || item.trim().isEmpty()) {
                continue;
            }

            String lowerItem = item.toLowerCase().trim();

            // Email adresi içeriyor mu?
            if (lowerItem.contains("@") || lowerItem.contains("mail")) {
                logger.warn("Geçersiz menü tespit edildi (email içeriyor): {} - {}", meal.getDate(), item);
                return false;
            }

            // Bilgilendirme mesajı içeriyor mu?
            if (lowerItem.contains("gönderip") ||
                    lowerItem.contains("katkı sağla") ||
                    lowerItem.contains("uygulamaya") ||
                    lowerItem.contains("listesini") ||
                    lowerItem.contains("daha hızlı") ||
                    lowerItem.contains("girilmesine")) {
                logger.warn("Geçersiz menü tespit edildi (bilgilendirme mesajı): {} - {}", meal.getDate(), item);
                return false;
            }

            // Geçerli bir yemek adı olabilir mi? (en az 3 karakter, en fazla 100 karakter)
            if (item.trim().length() >= 3 && item.trim().length() <= 100) {
                validItemCount++;
            }
        }

        // En az 3 geçerli yemek adı olmalı
        if (validItemCount < 3) {
            logger.warn("Geçersiz menü tespit edildi (yetersiz yemek sayısı): {} - Geçerli sayı: {}",
                    meal.getDate(), validItemCount);
            return false;
        }

        logger.info("Geçerli menü bulundu: {} - Tip: {} - Yemekler: {}",
                meal.getDate(), meal.getMealType(), validItemCount);
        return true;
    }

    private void appendMealDetails(StringBuilder builder, String title, Meal meal) {
        Integer totalCal = meal.getTotalCalories();

        builder.append(title);
        if (totalCal != null && totalCal > 0) {
            builder.append(" (").append(totalCal).append(" kcal)");
        }
        builder.append("\n");

        appendMealItem(builder, meal.getFirst(), meal.getFirstCalories());
        appendMealItem(builder, meal.getSecond(), meal.getSecondCalories());
        appendMealItem(builder, meal.getThird(), meal.getThirdCalories());
        appendMealItem(builder, meal.getFourth(), meal.getFourthCalories());
    }

    private void appendMealItem(StringBuilder builder, String item, String calories) {
        builder.append("• ").append(item);
        Integer cal = parseCalories(calories);
        if (cal != null && cal > 0) {
            builder.append(" (").append(cal).append(" kcal)");
        }
        builder.append("\n");
    }

    // Yardımcı metod: String kaloriyi Integer'a çevirir
    private Integer parseCalories(String calorieStr) {
        if (calorieStr == null || calorieStr.trim().isEmpty()) {
            return null;
        }
        try {
            return Integer.parseInt(calorieStr.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}