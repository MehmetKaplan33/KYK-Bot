package com.kyk.mealtracker.bot;

import com.kyk.mealtracker.entity.BotUser;
import com.kyk.mealtracker.entity.Meal;
import com.kyk.mealtracker.repository.BotUserRepository;
import com.kyk.mealtracker.services.MealService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.User;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

@Component
public class KykMealBot extends TelegramLongPollingBot {

    private static final Logger logger = LoggerFactory.getLogger(KykMealBot.class);
    private final MealService mealService;
    private final BotUserRepository botUserRepository;

    @Value("${telegram.bot.username}")
    private String botUsername;

    @Value("${telegram.bot.token}")
    private String botToken;

    public KykMealBot(MealService mealService, BotUserRepository botUserRepository) {
        this.mealService = mealService;
        this.botUserRepository = botUserRepository;
    }

    @Override
    public String getBotUsername() {
        return botUsername;
    }

    @Override
    public String getBotToken() {
        return botToken;
    }

    @Override
    public void onUpdateReceived(Update update) {
        if (!update.hasMessage() || !update.getMessage().hasText()) {
            return;
        }

        String messageText = update.getMessage().getText();
        Long chatId = update.getMessage().getChatId();
        User user = update.getMessage().getFrom();

        try {
            // Her mesajda kullanıcıyı kaydet/güncelle
            saveOrUpdateUser(chatId, user);

            switch (messageText) {
                case "/start":
                    sendWelcomeMessage(chatId);
                    break;
                case "/bugun":
                    sendTodaysMeals(chatId);
                    break;
                case "/yarin":
                    sendTomorrowsMeals(chatId);
                    break;
                case "/bildirim_ac":
                    enableNotifications(chatId);
                    break;
                case "/bildirim_kapat":
                    disableNotifications(chatId);
                    break;
                case "/yardim":
                    sendHelpMessage(chatId);
                    break;
                default:
                    sendMessage(chatId, "Anlaşılamadı. Komutları görmek için /yardim yazın.");
            }
        } catch (Exception e) {
            logger.error("Message handling error for chatId: " + chatId, e);
            try {
                sendMessage(chatId, "Bir hata oluştu. Lütfen daha sonra tekrar deneyin.");
            } catch (TelegramApiException ex) {
                logger.error("Error sending error message", ex);
            }
        }
    }

    private void saveOrUpdateUser(Long chatId, User user) {
        BotUser botUser = botUserRepository.findById(chatId).orElse(new BotUser());
        botUser.setChatId(chatId);
        botUser.setUsername(user.getUserName());
        botUser.setFirstName(user.getFirstName());
        botUser.setLastName(user.getLastName());
        botUser.setLastInteractionDate(LocalDateTime.now());
        if (!botUserRepository.existsById(chatId)) {
            botUser.setNotificationsEnabled(true); // Varsayılan olarak bildirimleri aç
        }
        botUserRepository.save(botUser);
    }

    private void enableNotifications(Long chatId) throws TelegramApiException {
        BotUser user = botUserRepository.findById(chatId).orElseThrow();
        user.setNotificationsEnabled(true);
        botUserRepository.save(user);
        sendMessage(chatId, "✅ Günlük menü bildirimleri açıldı! Her sabah saat 07:00'de menüyü size ileteceğim.");
    }

    private void disableNotifications(Long chatId) throws TelegramApiException {
        BotUser user = botUserRepository.findById(chatId).orElseThrow();
        user.setNotificationsEnabled(false);
        botUserRepository.save(user);
        sendMessage(chatId, "❌ Günlük menü bildirimleri kapatıldı!");
    }

    private void sendWelcomeMessage(Long chatId) throws TelegramApiException {
        String message = """
                🎉 KYK Yemek Botuna Hoş Geldiniz!
                
                Bu bot size günlük yemek menüsünü gösterir ve hatırlatır.
                
                Kullanabileceğiniz komutlar:
                /bugun - Bugünün menüsü
                /yarin - Yarının menüsü
                /bildirim_ac - Günlük bildirimleri aç
                /bildirim_kapat - Günlük bildirimleri kapat
                /yardim - Tüm komutları göster
                
                Varsayılan olarak günlük bildirimler açıktır.
                Her sabah 07:00'de o günün menüsünü size göndereceğim!
                """;
        sendMessage(chatId, message);
    }

    private void sendTodaysMeals(Long chatId) throws TelegramApiException {
        LocalDate today = LocalDate.now();
        List<Meal> meals = mealService.getMealsByDate(today);
        sendMealMessage(chatId, today, meals);
    }

    private void sendTomorrowsMeals(Long chatId) throws TelegramApiException {
        LocalDate tomorrow = LocalDate.now().plusDays(1);
        List<Meal> meals = mealService.getMealsByDate(tomorrow);
        sendMealMessage(chatId, tomorrow, meals);
    }

    private void sendMealMessage(Long chatId, LocalDate date, List<Meal> meals) throws TelegramApiException {
        if (meals.isEmpty()) {
            sendMessage(chatId, date.format(DateTimeFormatter.ofPattern("dd MMMM yyyy", new Locale("tr"))) +
                    " tarihli menü henüz yayınlanmamış.");
            return;
        }

        StringBuilder messageBuilder = new StringBuilder();
        messageBuilder.append("📅 ").append(date.format(DateTimeFormatter.ofPattern("dd MMMM yyyy", new Locale("tr")))).append("\n\n");

        meals.stream()
                .filter(meal -> meal.getMealType() == 0)
                .findFirst()
                .ifPresent(breakfast -> formatMeal(messageBuilder, "🌅 KAHVALTI", breakfast));

        messageBuilder.append("\n");

        meals.stream()
                .filter(meal -> meal.getMealType() == 1)
                .findFirst()
                .ifPresent(dinner -> formatMeal(messageBuilder, "🌙 AKŞAM YEMEĞİ", dinner));

        sendMessage(chatId, messageBuilder.toString());
    }

    private void formatMeal(StringBuilder builder, String title, Meal meal) {
        builder.append(title).append(" (").append(meal.getTotalCalories()).append(" kcal)").append("\n");
        builder.append("• ").append(meal.getFirst()).append(" (").append(meal.getFirstCalories()).append(" kcal)").append("\n");
        builder.append("• ").append(meal.getSecond()).append(" (").append(meal.getSecondCalories()).append(" kcal)").append("\n");
        builder.append("• ").append(meal.getThird()).append(" (").append(meal.getThirdCalories()).append(" kcal)").append("\n");
        builder.append("• ").append(meal.getFourth()).append(" (").append(meal.getFourthCalories()).append(" kcal)").append("\n");
    }

    private void sendHelpMessage(Long chatId) throws TelegramApiException {
        String helpMessage = """
                📋 Kullanılabilir Komutlar:
                
                /bugun - Bugünün menüsünü göster
                /yarin - Yarının menüsünü göster
                /yardim - Bu mesajı göster
                
                ℹ️ Her gün otomatik olarak menü bildirimi alacaksınız.
                """;
        sendMessage(chatId, helpMessage);
    }

    private void sendMessage(Long chatId, String text) throws TelegramApiException {
        SendMessage message = new SendMessage();
        message.setChatId(chatId);
        message.setText(text);
        message.enableHtml(true);
        execute(message);
    }
}
