package com.kyk.mealtracker.bot;

import com.kyk.mealtracker.entity.BotUser;
import com.kyk.mealtracker.entity.Meal;
import com.kyk.mealtracker.repository.BotUserRepository;
import com.kyk.mealtracker.services.AdminService;
import com.kyk.mealtracker.services.MealService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Message;
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
    private final AdminService adminService;

    @Value("${telegram.bot.username}")
    private String botUsername;

    @Value("${telegram.bot.token}")
    private String botToken;

    public KykMealBot(MealService mealService, BotUserRepository botUserRepository, AdminService adminService) {
        this.mealService = mealService;
        this.botUserRepository = botUserRepository;
        this.adminService = adminService;
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

            // Admin komutları kontrolü
            if (messageText.startsWith("/admin_")) {
                if (!adminService.isAdmin(chatId)) {
                    sendMessage(chatId, "🚫 Bu komutu kullanma yetkiniz bulunmuyor.");
                    return;
                }
                handleAdminCommand(chatId, messageText);
                return;
            }

            handleCommand(update.getMessage());
        } catch (Exception e) {
            logger.error("Message handling error for chatId: " + chatId, e);
            try {
                sendMessage(chatId, "⚠️ Bir hata oluştu. Lütfen daha sonra tekrar deneyin.");
            } catch (TelegramApiException ex) {
                logger.error("Error sending error message", ex);
            }
        }
    }

    private void handleCommand(Message message) throws TelegramApiException {
        String command = message.getText();
        Long chatId = message.getChatId();

        switch (command.split("\\s+")[0]) {
            case "/start":
                sendWelcomeMessage(chatId);
                break;
            case "/yardim":
                sendHelpMessage(chatId);
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
            default:
                sendMessage(chatId, "❓ Komut anlaşılamadı. Yardım için /yardim yazabilirsiniz.");
        }
    }

    private void handleAdminCommand(Long chatId, String command) throws TelegramApiException {
        String[] parts = command.split(" ", 2);
        String cmd = parts[0];

        switch (cmd) {
            case "/admin_list":
                int page = 0;
                if (parts.length > 1) {
                    try {
                        page = Integer.parseInt(parts[1]);
                    } catch (NumberFormatException e) {
                        page = 0;
                    }
                }
                sendMessage(chatId, adminService.getUserList(page));
                break;
            case "/admin_broadcast":
                if (parts.length < 2) {
                    sendMessage(chatId, "⚠️ Kullanım: /admin_broadcast [mesaj]\n\nÖrnek:\n/admin_broadcast Bugün yemekhanede bakım çalışması yapılacaktır.");
                    return;
                }
                int userCount = broadcastMessage(parts[1]);
                sendMessage(chatId, "✅ Mesaj başarıyla gönderildi!\n👥 Toplam " + userCount + " kullanıcıya ulaştırıldı.");
                break;
            case "/admin_stats":
                sendMessage(chatId, adminService.getBotStats());
                break;
            case "/admin_add":
                if (parts.length < 2) {
                    sendMessage(chatId, "⚠️ Kullanım: /admin_add [chatId]\n\nÖrnek:\n/admin_add 123456789");
                    return;
                }
                addAdmin(chatId, parts[1]);
                break;
            case "/admin_remove":
                if (parts.length < 2) {
                    sendMessage(chatId, "⚠️ Kullanım: /admin_remove [chatId]\n\nÖrnek:\n/admin_remove 123456789");
                    return;
                }
                removeAdmin(chatId, parts[1]);
                break;
            default:
                sendMessage(chatId, "❌ Geçersiz komut. /yardim ile komutları görüntüleyin.");
        }
    }

    private int broadcastMessage(String message) {
        List<BotUser> allUsers = botUserRepository.findAll();
        int successCount = 0;

        for (BotUser user : allUsers) {
            try {
                SendMessage sendMessage = new SendMessage();
                sendMessage.setChatId(user.getChatId());
                sendMessage.setText("📢 Duyuru\n\n" + message);
                execute(sendMessage);
                successCount++;
                Thread.sleep(50); // Rate limiting için küçük bir gecikme
            } catch (Exception e) {
                logger.error("Broadcast message failed for user: " + user.getChatId(), e);
            }
        }

        return successCount;
    }

    private void saveOrUpdateUser(Long chatId, User user) {
        BotUser botUser = botUserRepository.findById(chatId).orElse(new BotUser());
        botUser.setChatId(chatId);
        botUser.setUsername(user.getUserName());
        botUser.setFirstName(user.getFirstName());
        botUser.setLastName(user.getLastName());
        botUser.setLastInteractionDate(LocalDateTime.now());
        botUser.setLastActivityDate(LocalDateTime.now());

        // Yeni kullanıcı ise varsayılan değerleri ayarla
        if (!botUserRepository.existsById(chatId)) {
            botUser.setNotificationsEnabled(true);
            botUser.setIsAdmin(false);  // Varsayılan olarak admin değil
        }

        botUserRepository.save(botUser);
    }

    private void enableNotifications(Long chatId) throws TelegramApiException {
        BotUser user = botUserRepository.findById(chatId).orElseThrow();
        user.setNotificationsEnabled(true);
        botUserRepository.save(user);
        sendMessage(chatId, "🔔 Bildirimler aktif edildi!\nHer gün sabah 06:30'da kahvaltı ve öğleden sonra 14:00'te akşam yemeği menüsünü size ileteceğim.");
    }

    private void disableNotifications(Long chatId) throws TelegramApiException {
        BotUser user = botUserRepository.findById(chatId).orElseThrow();
        user.setNotificationsEnabled(false);
        botUserRepository.save(user);
        sendMessage(chatId, "🔕 Bildirimler kapatıldı.");
    }

    private void sendWelcomeMessage(Long chatId) throws TelegramApiException {
        String message = """
                👋 KYK Yemek Menüsü Botuna Hoş Geldiniz!
                
                Bu bot ile günlük yemek menülerini takip edebilir ve bildirim alabilirsiniz.
                
                🔹 Komutlar:
                /bugun - Bugünün menüsü
                /yarin - Yarının menüsü
                /bildirim_ac - Günlük bildirimleri aç
                /bildirim_kapat - Bildirimleri kapat
                /yardim - Komut listesi
                
                💡 İpucu: Bildirimler varsayılan olarak açıktır.
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
        // Geçerli menüleri filtrele
        List<Meal> validMeals = meals.stream()
                .filter(this::isValidMealForDisplay)
                .toList();

        if (validMeals.isEmpty()) {
            String formattedDate = date.format(DateTimeFormatter.ofPattern("dd MMMM yyyy", new Locale("tr")));
            sendMessage(chatId, "📅 " + formattedDate + " tarihine ait menü henüz yayınlanmamış.\n\nMenü yayınlandığında bildirim almak için /bildirim_ac komutunu kullanabilirsiniz.");
            return;
        }

        StringBuilder messageBuilder = new StringBuilder();
        String formattedDate = date.format(DateTimeFormatter.ofPattern("dd MMMM yyyy", new Locale("tr")));
        messageBuilder.append("📅 ").append(formattedDate).append("\n");
        messageBuilder.append("━━━━━━━━━━━━━━━━━━\n\n");

        validMeals.stream()
                .filter(meal -> meal.getMealType() == 0)
                .findFirst()
                .ifPresent(breakfast -> {
                    formatMeal(messageBuilder, "🌅 KAHVALTI", breakfast);
                    messageBuilder.append("\n");
                });

        validMeals.stream()
                .filter(meal -> meal.getMealType() == 1)
                .findFirst()
                .ifPresent(dinner -> formatMeal(messageBuilder, "🍽️ AKŞAM YEMEĞİ", dinner));

        sendMessage(chatId, messageBuilder.toString());
    }

    private void formatMeal(StringBuilder builder, String title, Meal meal) {
        Integer totalCal = meal.getTotalCalories();

        builder.append(title);
        if (totalCal != null && totalCal > 0) {
            builder.append(" (").append(totalCal).append(" kcal)");
        }
        builder.append("\n");
        builder.append("━━━━━━━━━━━━━━━━━━\n");

        formatMealItem(builder, meal.getFirst(), meal.getFirstCalories());
        formatMealItem(builder, meal.getSecond(), meal.getSecondCalories());
        formatMealItem(builder, meal.getThird(), meal.getThirdCalories());
        formatMealItem(builder, meal.getFourth(), meal.getFourthCalories());
    }

    private void formatMealItem(StringBuilder builder, String item, String calories) {
        builder.append("✓ ").append(item);
        Integer cal = parseCalories(calories);
        if (cal != null && cal > 0) {
            builder.append(" (").append(cal).append(" kcal)");
        }
        builder.append("\n");
    }

    // Yemeğin geçerli olup olmadığını kontrol eder
    private boolean isValidMealForDisplay(Meal meal) {
        if (meal == null) {
            return false;
        }

        String[] items = {
                meal.getFirst(),
                meal.getSecond(),
                meal.getThird(),
                meal.getFourth()
        };

        int validItemCount = 0;
        for (String item : items) {
            if (item == null || item.trim().isEmpty()) {
                continue;
            }

            String lowerItem = item.toLowerCase().trim();

            // Email adresi içeriyor mu?
            if (lowerItem.contains("@") || lowerItem.contains("mail")) {
                return false;
            }

            // Bilgilendirme mesajı içeriyor mu?
            if (lowerItem.contains("gönderip") ||
                    lowerItem.contains("katkı sağla") ||
                    lowerItem.contains("uygulamaya") ||
                    lowerItem.contains("listesini") ||
                    lowerItem.contains("daha hızlı") ||
                    lowerItem.contains("girilmesine")) {
                return false;
            }

            // Geçerli bir yemek adı olabilir mi? (en az 3 karakter, en fazla 100 karakter)
            if (item.trim().length() >= 3 && item.trim().length() <= 100) {
                validItemCount++;
            }
        }

        // En az 3 geçerli yemek adı olmalı
        return validItemCount >= 3;
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

    private void sendHelpMessage(Long chatId) throws TelegramApiException {
        String helpMessage = """
                ℹ️ Komut Listesi
                
                📱 Menü Komutları:
                /bugun - Bugünkü menüyü göster
                /yarin - Yarınki menüyü göster
                
                🔔 Bildirim Ayarları:
                /bildirim_ac - Günlük bildirimleri aktif et
                /bildirim_kapat - Bildirimleri kapat
                
                ❓ Diğer:
                /yardim - Bu listeyi göster
                """ + (adminService.isAdmin(chatId) ? """
                
                🔧 Yönetici Komutları:
                /admin_list [sayfa] - Kullanıcı listesi (Chat ID'ler ile)
                /admin_add [chatId] - Admin yetkisi ver
                /admin_remove [chatId] - Admin yetkisini al
                /admin_broadcast [mesaj] - Toplu mesaj gönder
                /admin_stats - Detaylı analiz
                """ : "");
        sendMessage(chatId, helpMessage);
    }

    private void sendMessage(Long chatId, String text) throws TelegramApiException {
        SendMessage message = new SendMessage();
        message.setChatId(chatId);
        message.setText(text);
        message.enableHtml(true);
        execute(message);
    }

    private void addAdmin(Long requestorChatId, String targetChatIdStr) throws TelegramApiException {
        try {
            Long targetChatId = Long.parseLong(targetChatIdStr);
            BotUser user = botUserRepository.findById(targetChatId).orElse(null);

            if (user == null) {
                sendMessage(requestorChatId, "❌ Bu ID'ye sahip kullanıcı bulunamadı.");
                return;
            }

            if (user.getIsAdmin() != null && user.getIsAdmin()) {
                sendMessage(requestorChatId, "⚠️ Bu kullanıcı zaten admin!");
                return;
            }

            user.setIsAdmin(true);
            botUserRepository.save(user);

            sendMessage(requestorChatId, "✅ " + user.getFirstName() + " artık admin!");
            sendMessage(targetChatId, "🔧 Size admin yetkisi verildi!");

        } catch (NumberFormatException e) {
            sendMessage(requestorChatId, "❌ Geçersiz Chat ID formatı!");
        }
    }

    private void removeAdmin(Long requestorChatId, String targetChatIdStr) throws TelegramApiException {
        try {
            Long targetChatId = Long.parseLong(targetChatIdStr);

            // Kendini admin'likten çıkaramaz
            if (requestorChatId.equals(targetChatId)) {
                sendMessage(requestorChatId, "❌ Kendinizi admin'likten çıkaramazsınız!");
                return;
            }

            BotUser user = botUserRepository.findById(targetChatId).orElse(null);

            if (user == null) {
                sendMessage(requestorChatId, "❌ Bu ID'ye sahip kullanıcı bulunamadı.");
                return;
            }

            if (user.getIsAdmin() == null || !user.getIsAdmin()) {
                sendMessage(requestorChatId, "⚠️ Bu kullanıcı zaten admin değil!");
                return;
            }

            user.setIsAdmin(false);
            botUserRepository.save(user);

            sendMessage(requestorChatId, "✅ " + user.getFirstName() + " artık admin değil!");
            sendMessage(targetChatId, "⚠️ Admin yetkiniz kaldırıldı.");

        } catch (NumberFormatException e) {
            sendMessage(requestorChatId, "❌ Geçersiz Chat ID formatı!");
        }
    }
}
