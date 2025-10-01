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
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

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

        logger.info("Günlük yemek kontrolü başlatıl��yor... Tarih: {}", today);

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

        // Menü güncellemesi bittikten sonra bildirimleri gönder
        sendDailyNotifications();
    }

    // Her sabah 07:00'de günlük menü bildirimlerini gönder
    @Scheduled(cron = "0 0 7 * * ?")
    public void sendDailyNotifications() {
        LocalDate today = LocalDate.now();
        List<Meal> todaysMeals = mealService.getMealsByDate(today);

        if (todaysMeals.isEmpty()) {
            logger.info("Bugün için menü bulunamadı: {}", today);
            return;
        }

        // Bildirimleri açık olan tüm kullanıcıları bul
        List<BotUser> activeUsers = botUserRepository.findByNotificationsEnabledTrue();

        for (BotUser user : activeUsers) {
            try {
                StringBuilder messageBuilder = new StringBuilder();
                String dateStr = today.format(DateTimeFormatter.ofPattern("dd MMMM yyyy", new Locale("tr")));

                messageBuilder.append("🌟 Günaydın! İşte bugünün menüsü:\n\n");
                messageBuilder.append("📅 ").append(dateStr).append("\n\n");

                // Kahvaltı menüsü
                todaysMeals.stream()
                    .filter(meal -> meal.getMealType() == 0)
                    .findFirst()
                    .ifPresent(breakfast -> appendMealDetails(messageBuilder, "🌅 KAHVALTI", breakfast));

                messageBuilder.append("\n");

                // Akşam yemeği menüsü
                todaysMeals.stream()
                    .filter(meal -> meal.getMealType() == 1)
                    .findFirst()
                    .ifPresent(dinner -> appendMealDetails(messageBuilder, "🌙 AKŞAM YEMEĞİ", dinner));

                kykMealBot.execute(org.telegram.telegrambots.meta.api.methods.send.SendMessage.builder()
                    .chatId(user.getChatId())
                    .text(messageBuilder.toString())
                    .build());

                logger.info("Bildirim gönderildi - ChatId: {}", user.getChatId());

            } catch (Exception e) {
                logger.error("Bildirim gönderilemedi - ChatId: " + user.getChatId(), e);
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
                      .findFirst()
                      .ifPresent(mealService::saveMealIfNotExists);
            }
        } catch (Exception e) {
            logger.error("API çağrısı hatası - URL: {}, Hata: {}", url, e.getMessage());
            throw e;
        }
    }

    private void appendMealDetails(StringBuilder builder, String title, Meal meal) {
        builder.append(title).append(" (").append(meal.getTotalCalories()).append(" kcal)\n");
        builder.append("• ").append(meal.getFirst()).append(" (").append(meal.getFirstCalories()).append(" kcal)\n");
        builder.append("• ").append(meal.getSecond()).append(" (").append(meal.getSecondCalories()).append(" kcal)\n");
        builder.append("• ").append(meal.getThird()).append(" (").append(meal.getThirdCalories()).append(" kcal)\n");
        builder.append("• ").append(meal.getFourth()).append(" (").append(meal.getFourthCalories()).append(" kcal)\n");
    }
}
