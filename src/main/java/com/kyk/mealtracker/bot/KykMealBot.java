package com.kyk.mealtracker.bot;

import com.kyk.mealtracker.entity.BotUser;
import com.kyk.mealtracker.entity.Meal;
import com.kyk.mealtracker.repository.BotUserRepository;
import com.kyk.mealtracker.services.AdminService;
import com.kyk.mealtracker.services.MealService;
import com.kyk.mealtracker.services.MealSyncService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.send.SendPhoto;
import org.telegram.telegrambots.meta.api.objects.InputFile;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.User;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class KykMealBot extends TelegramLongPollingBot {

    private static final Logger logger = LoggerFactory.getLogger(KykMealBot.class);
    private final MealService mealService;
    private final BotUserRepository botUserRepository;
    private final AdminService adminService;
    private final MealSyncService mealSyncService;

    // Anti-spam (ChatID -> Timestamp)
    private final ConcurrentHashMap<Long, Long> lastMessageTimes = new ConcurrentHashMap<>();

    @Value("${telegram.bot.username}")
    private String botUsername;

    @Value("${telegram.bot.token}")
    private String botToken;

    public KykMealBot(MealService mealService, BotUserRepository botUserRepository, AdminService adminService, MealSyncService mealSyncService) {
        this.mealService = mealService;
        this.botUserRepository = botUserRepository;
        this.adminService = adminService;
        this.mealSyncService = mealSyncService;
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
        if (!update.hasMessage()) {
            return;
        }

        Message message = update.getMessage();
        String messageText = message.getText();
        if (messageText == null && message.getCaption() != null) {
            messageText = message.getCaption();
        }

        if (messageText == null) return;

        Long chatId = message.getChatId();
        User user = message.getFrom();

        // Rate Limiting (3 saniyede 1 mesaj)
        long currentTime = System.currentTimeMillis();
        long lastTime = lastMessageTimes.getOrDefault(chatId, 0L);
        if (currentTime - lastTime < 3000) {
            return; // Ignore spam
        }
        lastMessageTimes.put(chatId, currentTime);

        try {
            saveOrUpdateUser(chatId, user);

            if (messageText.startsWith("/admin_")) {
                if (!adminService.isAdmin(chatId)) {
                    sendMessage(chatId, "🚫 Bu komutu kullanma yetkiniz bulunmuyor.");
                    return;
                }
                handleAdminCommand(chatId, messageText, message);
                return;
            }

            handleCommand(message, messageText);
        } catch (Exception e) {
            logger.error("Message handling error for chatId: " + chatId, e);
            try {
                sendMessage(chatId, "⚠️ Bir hata oluştu. Lütfen daha sonra tekrar deneyin.");
            } catch (TelegramApiException ex) {
                logger.error("Error sending error message", ex);
            }
        }
    }

    private void handleCommand(Message message, String messageText) throws TelegramApiException {
        String[] parts = messageText.split("\\s+", 2);
        String command = parts[0];
        Long chatId = message.getChatId();

        switch (command) {
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
            case "/haftalik":
                sendWeeklyMeals(chatId);
                break;
            case "/sehir_sec":
                if (parts.length < 2) {
                    sendMessage(chatId, "Lütfen şehir plaka kodunu girin. Örnek: /sehir_sec 1");
                } else {
                    setCity(chatId, parts[1]);
                }
                break;
            case "/iletisim":
                if (parts.length < 2) {
                    sendMessage(chatId, "Lütfen mesajınızı komutun yanına yazın. Örnek:\n/iletisim Merhaba, bot harika çalışıyor!");
                } else {
                    sendFeedback(chatId, message.getFrom(), parts[1]);
                }
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

    private void handleAdminCommand(Long chatId, String messageText, Message message) throws TelegramApiException {
        String[] parts = messageText.split("\\s+", 2);
        String cmd = parts[0];

        switch (cmd) {
            case "/admin_list":
                int page = 1;
                if (parts.length > 1) {
                    try { 
                        page = Integer.parseInt(parts[1].trim()); 
                        if (page < 1) page = 1;
                    } catch (Exception ignored) {}
                }
                sendMessage(chatId, adminService.getUserList(page - 1));
                break;
            case "/admin_broadcast":
                if (parts.length < 2) {
                    sendMessage(chatId, "⚠️ Kullanım: /admin_broadcast [mesaj]");
                    return;
                }
                int userCount = broadcastMessage(parts[1]);
                sendMessage(chatId, "✅ Mesaj başarıyla gönderildi!\n👥 Toplam " + userCount + " kullanıcıya ulaştırıldı.");
                break;
            case "/admin_broadcast_image":
                if (!message.hasPhoto()) {
                    sendMessage(chatId, "Lütfen bu komutu bir resim ile birlikte caption (açıklama) alanına yazarak gönderin.");
                    return;
                }
                String fileId = message.getPhoto().get(message.getPhoto().size() - 1).getFileId();
                String caption = parts.length > 1 ? parts[1] : "";
                int countImg = broadcastImage(fileId, caption);
                sendMessage(chatId, "✅ Resimli duyuru " + countImg + " kişiye gönderildi.");
                break;
            case "/admin_stats":
                sendMessage(chatId, adminService.getBotStats());
                break;
            case "/admin_add":
                if (parts.length < 2) return;
                addAdmin(chatId, parts[1]);
                break;
            case "/admin_remove":
                if (parts.length < 2) return;
                removeAdmin(chatId, parts[1]);
                break;
            case "/admin_fetch":
                sendMessage(chatId, "⏳ Yemekler apiden çekiliyor...");
                mealSyncService.fetchMealsForAllActiveCities();
                sendMessage(chatId, "✅ Yemekler başarıyla güncellendi.");
                break;
            case "/admin_delete_meal":
                if (parts.length < 2) {
                    sendMessage(chatId, "Kullanım: /admin_delete_meal YYYY-MM-DD");
                    return;
                }
                try {
                    LocalDate date = LocalDate.parse(parts[1].trim());
                    mealService.deleteMealsByDate(date);
                    sendMessage(chatId, "✅ " + date + " tarihli menüler silindi.");
                } catch (Exception e) {
                    sendMessage(chatId, "Hatalı tarih formatı. YYYY-MM-DD olmalı.");
                }
                break;
            case "/admin_user":
                if (parts.length < 2) {
                    sendMessage(chatId, "⚠️ Kullanım: /admin_user [chatId]");
                    return;
                }
                sendUserDetails(chatId, parts[1].trim());
                break;
            default:
                sendMessage(chatId, "❌ Geçersiz komut. /yardim ile komutları görüntüleyin.");
        }
    }

    private void sendUserDetails(Long adminChatId, String targetChatIdStr) throws TelegramApiException {
        try {
            Long targetChatId = Long.parseLong(targetChatIdStr);
            BotUser user = botUserRepository.findById(targetChatId).orElse(null);
            if (user == null) {
                sendMessage(adminChatId, "❌ Kullanıcı bulunamadı.");
                return;
            }
            String details = String.format("""
                    👤 Kullanıcı Detayları:
                    ID: %d
                    Ad Soyad: %s %s
                    Username: @%s
                    Şehir Kodu: %d
                    Bildirimler: %s
                    İlk Kayıt (yaklaşık): %s
                    Son Etkileşim: %s
                    Admin mi?: %s
                    """,
                    user.getChatId(),
                    user.getFirstName() != null ? user.getFirstName() : "",
                    user.getLastName() != null ? user.getLastName() : "",
                    user.getUsername() != null ? user.getUsername() : "yok",
                    user.getCityId() != null ? user.getCityId() : 1,
                    user.isNotificationsEnabled() ? "Açık ✅" : "Kapalı ❌",
                    user.getLastInteractionDate() != null ? user.getLastInteractionDate().toString() : "Bilinmiyor",
                    user.getLastActivityDate() != null ? user.getLastActivityDate().toString() : "Bilinmiyor",
                    (user.getIsAdmin() != null && user.getIsAdmin()) ? "Evet" : "Hayır");
            sendMessage(adminChatId, details);
        } catch (NumberFormatException e) {
            sendMessage(adminChatId, "❌ Geçersiz Chat ID.");
        }
    }

    private void sendFeedback(Long chatId, User user, String text) throws TelegramApiException {
        List<BotUser> admins = botUserRepository.findByIsAdminTrue();
        String msg = String.format("📩 YENİ GERİ BİLDİRİM\nKimden: %s (@%s, ID: %d)\n\nMesaj: %s",
                user.getFirstName(), user.getUserName(), chatId, text);
        for (BotUser admin : admins) {
            try {
                sendMessage(admin.getChatId(), msg);
            } catch (Exception ignored) {}
        }
        sendMessage(chatId, "✅ Mesajınız yöneticilere iletildi. Geri bildiriminiz için teşekkürler!");
    }

    private void setCity(Long chatId, String codeStr) throws TelegramApiException {
        try {
            int code = Integer.parseInt(codeStr.trim());
            if (code < 1 || code > 81) {
                sendMessage(chatId, "❌ Geçersiz plaka kodu. Lütfen 1 ile 81 arasında bir kod girin.");
                return;
            }
            BotUser user = botUserRepository.findById(chatId).orElseThrow();
            user.setCityId(code);
            botUserRepository.save(user);
            sendMessage(chatId, "✅ Şehriniz " + code + " plaka kodu olarak ayarlandı.\nGüncel menüleri çekmek biraz zaman alabilir, lütfen daha sonra /bugun komutunu deneyin.");
            // Fetch immediately for this user's city
            try {
                mealSyncService.fetchMealsForAllActiveCities(); // This handles finding the active city id
            } catch (Exception ignored) {}
            
        } catch (NumberFormatException e) {
            sendMessage(chatId, "❌ Lütfen sadece sayısal bir plaka kodu girin (Örn: /sehir_sec 34).");
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
                Thread.sleep(50);
            } catch (Exception e) {
                logger.error("Broadcast message failed for user: " + user.getChatId(), e);
            }
        }
        return successCount;
    }

    private int broadcastImage(String fileId, String caption) {
        List<BotUser> allUsers = botUserRepository.findAll();
        int successCount = 0;
        for (BotUser user : allUsers) {
            try {
                SendPhoto sendPhoto = new SendPhoto();
                sendPhoto.setChatId(user.getChatId());
                sendPhoto.setPhoto(new InputFile(fileId));
                sendPhoto.setCaption("📢 Duyuru\n\n" + caption);
                execute(sendPhoto);
                successCount++;
                Thread.sleep(50);
            } catch (Exception e) {
                logger.error("Broadcast image failed for user: " + user.getChatId(), e);
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

        if (!botUserRepository.existsById(chatId)) {
            botUser.setNotificationsEnabled(true);
            botUser.setIsAdmin(false);
            botUser.setCityId(1);
        }
        botUserRepository.save(botUser);
    }

    private void enableNotifications(Long chatId) throws TelegramApiException {
        BotUser user = botUserRepository.findById(chatId).orElseThrow();
        user.setNotificationsEnabled(true);
        botUserRepository.save(user);
        sendMessage(chatId, "🔔 Bildirimler aktif edildi!");
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
                
                🔹 Komutlar:
                /bugun - Bugünün menüsü
                /yarin - Yarının menüsü
                /haftalik - Önümüzdeki 7 günün menüsü
                /sehir_sec [plaka] - Şehrinizi seçin (Örn: /sehir_sec 34)
                /iletisim [mesaj] - Bizimle iletişime geçin
                /bildirim_ac - Bildirimleri aç
                /bildirim_kapat - Bildirimleri kapat
                /yardim - Komut listesi
                """ + (adminService.isAdmin(chatId) ? "\n🔧 Yönetici yetkiniz aktif." : "") + """
                
                💡 İpucu: Bildirimler varsayılan olarak 1 (Adana) plakasına göre açıktır.
                """;
        sendMessage(chatId, message);
    }

    private void sendTodaysMeals(Long chatId) throws TelegramApiException {
        LocalDate today = LocalDate.now();
        BotUser user = botUserRepository.findById(chatId).orElseThrow();
        List<Meal> meals = mealService.getMealsByDate(today).stream()
                .filter(m -> m.getCityId() != null && m.getCityId().equals(user.getCityId() != null ? user.getCityId() : 1))
                .toList();
        sendMealMessage(chatId, today, meals);
    }

    private void sendTomorrowsMeals(Long chatId) throws TelegramApiException {
        LocalDate tomorrow = LocalDate.now().plusDays(1);
        BotUser user = botUserRepository.findById(chatId).orElseThrow();
        List<Meal> meals = mealService.getMealsByDate(tomorrow).stream()
                .filter(m -> m.getCityId() != null && m.getCityId().equals(user.getCityId() != null ? user.getCityId() : 1))
                .toList();
        sendMealMessage(chatId, tomorrow, meals);
    }

    private void sendWeeklyMeals(Long chatId) throws TelegramApiException {
        LocalDate today = LocalDate.now();
        LocalDate nextWeek = today.plusDays(6);
        BotUser user = botUserRepository.findById(chatId).orElseThrow();
        int cityId = user.getCityId() != null ? user.getCityId() : 1;
        
        List<Meal> meals = mealService.getMealsByDate(today); // Fallback if no between available
        // Actually, I need a specific query for this or I can iterate.
        // Let's iterate.
        sendMessage(chatId, "🗓️ Önümüzdeki 7 Günlük Menü Özeti yükleniyor...");
        StringBuilder sb = new StringBuilder();
        boolean hasAny = false;
        
        for (int i = 0; i <= 6; i++) {
            LocalDate date = today.plusDays(i);
            List<Meal> dayMeals = mealService.getMealsByDate(date).stream()
                .filter(m -> m.getCityId() != null && m.getCityId() == cityId)
                .filter(this::isValidMealForDisplay)
                .toList();
            
            if (!dayMeals.isEmpty()) {
                hasAny = true;
                sb.append("📅 *").append(date.format(DateTimeFormatter.ofPattern("dd MMMM EEEE", new Locale("tr")))).append("*\n");
                
                dayMeals.stream().filter(m -> m.getMealType() == 0).findFirst().ifPresent(m -> {
                    sb.append("🌅 K: ").append(m.getFirst()).append(", ").append(m.getSecond()).append("\n");
                });
                
                dayMeals.stream().filter(m -> m.getMealType() == 1).findFirst().ifPresent(m -> {
                    sb.append("🍽️ A: ").append(m.getFirst()).append(", ").append(m.getSecond()).append("\n");
                });
                sb.append("────────────────\n");
            }
        }
        
        if (hasAny) {
            SendMessage sm = new SendMessage();
            sm.setChatId(chatId);
            sm.setText(sb.toString());
            sm.setParseMode("Markdown");
            execute(sm);
        } else {
            sendMessage(chatId, "❌ Bu hafta için sistemde hiç menü bulunamadı.");
        }
    }

    private void sendMealMessage(Long chatId, LocalDate date, List<Meal> meals) throws TelegramApiException {
        List<Meal> validMeals = meals.stream().filter(this::isValidMealForDisplay).toList();
        if (validMeals.isEmpty()) {
            String formattedDate = date.format(DateTimeFormatter.ofPattern("dd MMMM yyyy", new Locale("tr")));
            sendMessage(chatId, "📅 " + formattedDate + " tarihine ait menü henüz yayınlanmamış.");
            return;
        }

        StringBuilder messageBuilder = new StringBuilder();
        String formattedDate = date.format(DateTimeFormatter.ofPattern("dd MMMM yyyy", new Locale("tr")));
        messageBuilder.append("📅 ").append(formattedDate).append("\n━━━━━━━━━━━━━━━━━━\n\n");

        validMeals.stream().filter(meal -> meal.getMealType() == 0).findFirst().ifPresent(breakfast -> {
            formatMeal(messageBuilder, "🌅 KAHVALTI", breakfast);
            messageBuilder.append("\n");
        });

        validMeals.stream().filter(meal -> meal.getMealType() == 1).findFirst().ifPresent(dinner -> formatMeal(messageBuilder, "🍽️ AKŞAM YEMEĞİ", dinner));

        sendMessage(chatId, messageBuilder.toString());
    }

    private void formatMeal(StringBuilder builder, String title, Meal meal) {
        String totalCal = meal.getTotalCalories();
        builder.append(title);
        if (totalCal != null && !totalCal.trim().isEmpty()) {
            builder.append(" (").append(totalCal).append(" kcal)");
        }
        builder.append("\n━━━━━━━━━━━━━━━━━━\n");
        formatMealItem(builder, meal.getFirst(), meal.getFirstCalories());
        formatMealItem(builder, meal.getSecond(), meal.getSecondCalories());
        formatMealItem(builder, meal.getThird(), meal.getThirdCalories());
        formatMealItem(builder, meal.getFourth(), meal.getFourthCalories());
    }

    private void formatMealItem(StringBuilder builder, String item, String calories) {
        if (item == null || item.trim().isEmpty()) return;
        builder.append("✓ ").append(item);
        Integer cal = parseCalories(calories);
        if (cal != null && cal > 0) {
            builder.append(" (").append(cal).append(" kcal)");
        }
        builder.append("\n");
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

    private Integer parseCalories(String calorieStr) {
        if (calorieStr == null || calorieStr.trim().isEmpty()) return null;
        try { return Integer.parseInt(calorieStr.trim()); } catch (NumberFormatException e) { return null; }
    }

    private void sendHelpMessage(Long chatId) throws TelegramApiException {
        String helpMessage = """
                ℹ️ Komut Listesi
                
                📱 Menü Komutları:
                /bugun - Bugünkü menüyü göster
                /yarin - Yarınki menüyü göster
                /haftalik - Önümüzdeki 7 günün özetini göster
                /sehir_sec [plaka] - Şehrinizi seçin
                
                🔔 Bildirim & Diğer:
                /iletisim [mesaj] - Yönetime mesaj atın
                /bildirim_ac - Bildirimleri aktif et
                /bildirim_kapat - Bildirimleri kapat
                /yardim - Bu listeyi göster
                """ + (adminService.isAdmin(chatId) ? """
                
                🔧 Yönetici Komutları:
                /admin_list [sayfa] - Kullanıcı listesi
                /admin_user [chatId] - Kullanıcı detayı
                /admin_fetch - Tüm aktif şehirlerin güncel menülerini API'den çeker
                /admin_delete_meal [YYYY-MM-DD] - Belirtilen günün tüm menülerini siler
                /admin_add [chatId] - Admin yetkisi ver
                /admin_remove [chatId] - Admin yetkisi al
                /admin_broadcast [mesaj] - Toplu mesaj
                /admin_broadcast_image [caption] - (Resme yanıt olarak atın) Toplu resimli duyuru
                /admin_stats - İstatistikler
                """ : "");
        sendMessage(chatId, helpMessage);
    }

    private void sendMessage(Long chatId, String text) throws TelegramApiException {
        SendMessage message = new SendMessage();
        message.setChatId(chatId);
        message.setText(text);
        message.enableHtml(false); // To avoid weird unescaped char errors unless needed
        execute(message);
    }

    private void addAdmin(Long requestorChatId, String targetChatIdStr) throws TelegramApiException {
        try {
            Long targetChatId = Long.parseLong(targetChatIdStr);
            BotUser user = botUserRepository.findById(targetChatId).orElse(null);
            if (user == null) { sendMessage(requestorChatId, "❌ Bulunamadı."); return; }
            user.setIsAdmin(true);
            botUserRepository.save(user);
            sendMessage(requestorChatId, "✅ Admin eklendi!");
        } catch (NumberFormatException e) { sendMessage(requestorChatId, "❌ Format hatalı!"); }
    }

    private void removeAdmin(Long requestorChatId, String targetChatIdStr) throws TelegramApiException {
        try {
            Long targetChatId = Long.parseLong(targetChatIdStr);
            BotUser user = botUserRepository.findById(targetChatId).orElse(null);
            if (user == null) { sendMessage(requestorChatId, "❌ Bulunamadı."); return; }
            user.setIsAdmin(false);
            botUserRepository.save(user);
            sendMessage(requestorChatId, "✅ Admin silindi!");
        } catch (NumberFormatException e) { sendMessage(requestorChatId, "❌ Format hatalı!"); }
    }
}