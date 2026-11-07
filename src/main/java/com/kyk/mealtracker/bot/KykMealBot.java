package com.kyk.mealtracker.bot;

import com.kyk.mealtracker.entity.BotUser;
import com.kyk.mealtracker.entity.Meal;
import com.kyk.mealtracker.entity.MealRating;
import com.kyk.mealtracker.repository.BotUserRepository;
import com.kyk.mealtracker.repository.MealRatingRepository;
import com.kyk.mealtracker.services.AdminService;
import com.kyk.mealtracker.services.MealService;
import com.kyk.mealtracker.services.MealSyncService;
import com.kyk.mealtracker.util.CityHelper;
import com.kyk.mealtracker.util.MenuFormatter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.send.SendPhoto;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.InputFile;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.User;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class KykMealBot extends TelegramLongPollingBot {

    private static final Logger logger = LoggerFactory.getLogger(KykMealBot.class);
    private static final ZoneId TURKEY_ZONE = ZoneId.of("Europe/Istanbul");

    private final MealService mealService;
    private final BotUserRepository botUserRepository;
    private final AdminService adminService;
    private final MealSyncService mealSyncService;
    private final MealRatingRepository mealRatingRepository;

    private static final List<String> RANDOM_FOOD_IDEAS = List.of(
            "🌯 *Tavuk Döner & Ayran*\n_Öğrencinin ezeli ve ebedi kurtarıcısı. Yanına da biraz turşu!_",
            "🍳 *Kaşarlı & Sucuklu Menemen*\n_Oda arkadaşınla ekmeği banmalık, sıcak ve pratik şaheser!_",
            "🍝 *Kremalı Mantarlı Tavuklu Makarna*\n_Yurtta ocak varsa 15 dakikada restoran konforu._",
            "🌯 *Çiğ Köfte Dürüm (Bol Nar Ekşili)*\n_Cebi yakmaz, mideyi doyurur. Yanına şalgam veya ayran harika gider!_",
            "🍕 *Karışık Pizza veya Çıtır Lahmacun*\n_Bugün kendini ödüllendirme günü! Birkaç arkadaş birleşip söyleyebilirsiniz._",
            "🥪 *Bol Kaşarlı Tost & Tavşan Kanı Çay*\n_Kantin klasiği. Sade ama her zaman mutlu eder._",
            "🍜 *Noodle & Haşlanmış Yumurta*\n_Gece acıkmalarının ve sınav haftalarının baş tacı!_",
            "🍲 *Sıcak Mercimek Çorbası & Kıtır Ekmek*\n_İçini ısıtır, anne eli değmiş gibi hissettirir._"
    );

    private final ConcurrentHashMap<Long, Long> lastMessageTimes = new ConcurrentHashMap<>();

    // Admin Duyuru & Yönetim Sihirbazı Durum Yönetimi (ChatId -> State)
    private enum AdminState { NONE, WAITING_TEXT_BROADCAST, WAITING_IMG_BROADCAST, WAITING_ADD_ADMIN }
    private final ConcurrentHashMap<Long, AdminState> adminStates = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, String> pendingBroadcastText = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, String> pendingBroadcastImageFileId = new ConcurrentHashMap<>();

    @Value("${telegram.bot.username}")
    private String botUsername;

    @Value("${telegram.bot.token}")
    private String botToken;

    public KykMealBot(MealService mealService,
                      BotUserRepository botUserRepository,
                      AdminService adminService,
                      MealSyncService mealSyncService,
                      MealRatingRepository mealRatingRepository) {
        this.mealService = mealService;
        this.botUserRepository = botUserRepository;
        this.adminService = adminService;
        this.mealSyncService = mealSyncService;
        this.mealRatingRepository = mealRatingRepository;
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
        if (update.hasCallbackQuery()) {
            handleCallbackQuery(update.getCallbackQuery());
            return;
        }

        if (!update.hasMessage()) return;

        Message message = update.getMessage();
        Long chatId = message.getChatId();
        User user = message.getFrom();

        String messageText = message.getText();
        if (messageText == null && message.getCaption() != null) {
            messageText = message.getCaption();
        }

        // Anti-spam
        long now = System.currentTimeMillis();
        long lastTime = lastMessageTimes.getOrDefault(chatId, 0L);
        if (now - lastTime < 1000) return;
        lastMessageTimes.put(chatId, now);

        try {
            saveOrUpdateUser(chatId, user);
            boolean isAdmin = adminService.isAdmin(chatId);

            // Admin Duyuru Sihirbazı Yakalama
            if (isAdmin && handleAdminWizardInput(chatId, message, messageText)) {
                return;
            }

            if (messageText == null) return;

            if (messageText.startsWith("/admin_ekle")) {
                if (!isAdmin) {
                    sendPlainMessage(chatId, "🚫 Bu komutu kullanma yetkiniz bulunmuyor.");
                    return;
                }
                String[] parts = messageText.trim().split("\\s+", 2);
                if (parts.length < 2) {
                    sendPlainMessage(chatId, "ℹ️ Kullanım: `/admin_ekle <ChatID veya @kullaniciadi>`");
                    return;
                }
                processGrantAdmin(chatId, parts[1].trim());
                return;
            }

            if (messageText.startsWith("/admin_cikar")) {
                if (!isAdmin) {
                    sendPlainMessage(chatId, "🚫 Bu komutu kullanma yetkiniz bulunmuyor.");
                    return;
                }
                String[] parts = messageText.trim().split("\\s+", 2);
                if (parts.length < 2) {
                    sendPlainMessage(chatId, "ℹ️ Kullanım: `/admin_cikar <ChatID>`");
                    return;
                }
                try {
                    Long targetId = Long.parseLong(parts[1].trim());
                    var revoked = adminService.revokeAdmin(targetId, chatId);
                    if (revoked.isPresent()) {
                        sendPlainMessage(chatId, "✅ `" + targetId + "` ID'li kullanıcının admin yetkisi kaldırıldı.");
                    } else {
                        sendPlainMessage(chatId, "⚠️ Kullanıcı bulunamadı veya kendi yetkinizi kaldırmaya çalıştınız.");
                    }
                } catch (NumberFormatException e) {
                    sendPlainMessage(chatId, "❌ Geçerli bir sayısal Chat ID girin.");
                }
                return;
            }

            if (messageText.equals("/admin") || messageText.equals(BotKeyboardHelper.BTN_ADMIN)) {
                if (!isAdmin) {
                    sendPlainMessage(chatId, "🚫 Bu komutu kullanma yetkiniz bulunmuyor.");
                    return;
                }
                sendAdminDashboard(chatId);
                return;
            }

            handleUserMessage(chatId, messageText, message, isAdmin);

        } catch (Exception e) {
            logger.error("Mesaj işleme hatası (ChatId: " + chatId + ")", e);
            try {
                sendPlainMessage(chatId, "⚠️ Bir hata oluştu. Lütfen biraz sonra tekrar deneyin.");
            } catch (Exception ignored) {}
        }
    }

    private boolean handleAdminWizardInput(Long chatId, Message message, String text) throws TelegramApiException {
        AdminState state = adminStates.getOrDefault(chatId, AdminState.NONE);
        if (state == AdminState.NONE) return false;

        if (text != null && text.equalsIgnoreCase("/iptal")) {
            adminStates.remove(chatId);
            pendingBroadcastText.remove(chatId);
            pendingBroadcastImageFileId.remove(chatId);
            sendPlainMessage(chatId, "❌ İşlem iptal edildi.");
            return true;
        }

        if (state == AdminState.WAITING_TEXT_BROADCAST) {
            if (text == null || text.isBlank()) {
                sendPlainMessage(chatId, "⚠️ Lütfen bir metin girin veya iptal için `/iptal` yazın.");
                return true;
            }
            pendingBroadcastText.put(chatId, text);
            adminStates.remove(chatId);

            SendMessage sm = SendMessage.builder()
                    .chatId(chatId)
                    .text("👁️ *DUYURU ÖNİZLEMESİ:*\n\n" + text + "\n\nBu duyuruyu tüm kullanıcılara göndermek istiyor musunuz?")
                    .parseMode("Markdown")
                    .replyMarkup(AdminKeyboardHelper.getBroadcastConfirmKeyboard())
                    .build();
            execute(sm);
            return true;
        }

        if (state == AdminState.WAITING_IMG_BROADCAST) {
            if (!message.hasPhoto()) {
                sendPlainMessage(chatId, "⚠️ Lütfen bir fotoğraf gönderin (varsa altına açıklama yazabilirsiniz) veya iptal için `/iptal` yazın.");
                return true;
            }
            String fileId = message.getPhoto().get(message.getPhoto().size() - 1).getFileId();
            String caption = (text != null) ? text : "";
            pendingBroadcastImageFileId.put(chatId, fileId);
            pendingBroadcastText.put(chatId, caption);
            adminStates.remove(chatId);

            SendPhoto sp = SendPhoto.builder()
                    .chatId(chatId.toString())
                    .photo(new InputFile(fileId))
                    .caption("👁️ *RESİMLİ DUYURU ÖNİZLEMESİ:*\n\n" + caption + "\n\nBu görseli tüm kullanıcılara göndermek istiyor musunuz?")
                    .parseMode("Markdown")
                    .replyMarkup(AdminKeyboardHelper.getBroadcastConfirmKeyboard())
                    .build();
            execute(sp);
            return true;
        }

        if (state == AdminState.WAITING_ADD_ADMIN) {
            if (text == null || text.isBlank()) {
                sendPlainMessage(chatId, "⚠️ Lütfen eklenecek kişinin Chat ID'sini veya `@kullaniciadi` bilgisini yazın (İptal: `/iptal`).");
                return true;
            }
            adminStates.remove(chatId);
            processGrantAdmin(chatId, text.trim());
            return true;
        }

        return false;
    }

    private void processGrantAdmin(Long requesterChatId, String identifier) throws TelegramApiException {
        var grantedOpt = adminService.grantAdmin(identifier);
        if (grantedOpt.isPresent()) {
            BotUser newAdmin = grantedOpt.get();
            String name = (newAdmin.getFirstName() != null ? newAdmin.getFirstName() : "Kullanıcı");
            sendPlainMessage(requesterChatId, "✅ *" + name + "* (`" + newAdmin.getChatId() + "`) başarıyla **Yönetici (Admin)** yapıldı!");
            try {
                SendMessage notifyMsg = SendMessage.builder()
                        .chatId(newAdmin.getChatId())
                        .text("👑 *Tebrikler!* Bot yöneticisi tarafından size **Admin** yetkisi verildi.\nArtık alt menüdeki `🔧 Yönetici Paneli` butonunu veya `/admin` komutunu kullanabilirsiniz.")
                        .parseMode("Markdown")
                        .replyMarkup(BotKeyboardHelper.getMainReplyKeyboard(true))
                        .build();
                execute(notifyMsg);
            } catch (Exception ignored) {}
        } else {
            sendPlainMessage(requesterChatId, "❌ `" + identifier + "` ile eşleşen kayıtlı bir kullanıcı bulunamadı.\n\n💡 _Not: Admin yapacağınız kişinin bota en az 1 kez `/start` yazmış olması gerekir. Kullanıcı Listesi menüsünden kişinin Chat ID'sini kopyalayabilirsiniz._");
        }

        SendMessage sm = SendMessage.builder()
                .chatId(requesterChatId)
                .text(adminService.formatAdminList(requesterChatId))
                .parseMode("Markdown")
                .replyMarkup(AdminKeyboardHelper.getAdminManagementKeyboard(adminService.getAllAdmins(), requesterChatId))
                .build();
        execute(sm);
    }

    private void handleUserMessage(Long chatId, String text, Message message, boolean isAdmin) throws TelegramApiException {
        String clean = text.trim();

        if (clean.equals("/start")) {
            sendWelcome(chatId, isAdmin);
            return;
        }
        if (clean.equals(BotKeyboardHelper.BTN_TODAY) || clean.equals("/bugun")) {
            sendDailyMeal(chatId, LocalDate.now(TURKEY_ZONE), isAdmin);
            return;
        }
        if (clean.equals(BotKeyboardHelper.BTN_TOMORROW) || clean.equals("/yarin")) {
            sendDailyMeal(chatId, LocalDate.now(TURKEY_ZONE).plusDays(1), isAdmin);
            return;
        }
        if (clean.equals(BotKeyboardHelper.BTN_WEEKLY) || clean.equals("/haftalik")) {
            sendWeeklyMenuSelector(chatId);
            return;
        }
        if (clean.equals(BotKeyboardHelper.BTN_SEARCH)) {
            sendPlainMessage(chatId, "🔍 *Akıllı Yemek Arama*\n\nAramak istediğiniz yemeğin adını sohbete yazıp gönderin.\nÖrnek: `pizza`, `köfte`, `tavuk`, `makarna`, `tatlı`\n\n_Bot, önümüzdeki 30 gün içinde şehrinizde bu yemeğin çıkacağı tarihleri listeleyecektir._");
            return;
        }
        if (clean.equals(BotKeyboardHelper.BTN_RANDOM)) {
            sendRandomFoodSuggestion(chatId, null);
            return;
        }
        if (clean.equals(BotKeyboardHelper.BTN_SETTINGS) || clean.equals("/ayarlar")) {
            sendSettings(chatId);
            return;
        }
        if (clean.equals(BotKeyboardHelper.BTN_HELP) || clean.equals("/yardim")) {
            sendHelp(chatId, isAdmin);
            return;
        }

        if (clean.startsWith("/sehir_sec")) {
            String[] parts = clean.split("\\s+");
            if (parts.length > 1) {
                try {
                    int plate = Integer.parseInt(parts[1]);
                    setUserCity(chatId, plate, isAdmin);
                } catch (NumberFormatException e) {
                    sendPlainMessage(chatId, "❌ Lütfen geçerli bir plaka numarası girin. Örn: `/sehir_sec 01`");
                }
            } else {
                openCitySelector(chatId);
            }
            return;
        }

        if (clean.startsWith("/iletisim")) {
            String[] parts = clean.split("\\s+", 2);
            if (parts.length > 1) {
                forwardFeedbackToAdmins(chatId, message.getFrom(), parts[1]);
            } else {
                sendPlainMessage(chatId, "💬 Mesajınızı komutun yanına yazın.\nÖrnek: `/iletisim Merhaba bot harika!`");
            }
            return;
        }

        // AKILLI YEMEK ARAMA (Örn: "pizza", "tavuk", "köfte")
        if (clean.length() >= 3 && !clean.startsWith("/")) {
            handleMealSearch(chatId, clean);
            return;
        }

        sendPlainMessage(chatId, "❓ Komut anlaşılamadı. Menü butonlarını kullanabilir, bir yemek adı yazıp aratabilir veya /yardim yazabilirsiniz.");
    }

    private void handleMealSearch(Long chatId, String keyword) throws TelegramApiException {
        BotUser user = botUserRepository.findById(chatId).orElseThrow();
        int cityId = user.getCityId() != null ? user.getCityId() : 1;
        String cityName = CityHelper.getCityName(cityId);

        LocalDate today = LocalDate.now(TURKEY_ZONE);
        List<Meal> results = mealService.searchMeals(cityId, today, keyword);

        if (results.isEmpty()) {
            sendPlainMessage(chatId, "🔍 *" + cityName + "* menüsünde `\"" + keyword + "\"` içeren bir yemek bulunamadı.");
            return;
        }

        StringBuilder sb = new StringBuilder();
        sb.append("🔍 *\"").append(keyword).append("\" İçeren Menüler (").append(cityName).append(")*\n");
        sb.append("━━━━━━━━━━━━━━━━━━━━━━\n\n");

        DateTimeFormatter dFmt = DateTimeFormatter.ofPattern("dd MMMM EEEE", new java.util.Locale("tr", "TR"));

        for (Meal m : results.stream().limit(8).toList()) {
            sb.append("📅 *").append(m.getDate().format(dFmt)).append("* (")
              .append(m.getMealType() == 0 ? "🍳 Kahvaltı" : "🍲 Akşam")
              .append(")\n");

            // Eşleşen öğeyi vurgula
            appendMatchingItem(sb, m.getFirst(), keyword);
            appendMatchingItem(sb, m.getSecond(), keyword);
            appendMatchingItem(sb, m.getThird(), keyword);
            appendMatchingItem(sb, m.getFourth(), keyword);
            sb.append("\n");
        }

        sendPlainMessage(chatId, sb.toString());
    }

    private void appendMatchingItem(StringBuilder sb, String item, String keyword) {
        if (item == null || item.isBlank()) return;
        if (item.toLowerCase(new java.util.Locale("tr", "TR")).contains(keyword.toLowerCase(new java.util.Locale("tr", "TR")))) {
            sb.append("  👉 *").append(item.trim()).append("*\n");
        } else {
            sb.append("  • ").append(item.trim()).append("\n");
        }
    }

    private void handleCallbackQuery(CallbackQuery callbackQuery) {
        String data = callbackQuery.getData();
        Long chatId = callbackQuery.getMessage().getChatId();
        Integer messageId = callbackQuery.getMessage().getMessageId();

        try {
            if ("noop".equals(data)) {
                answerCallback(callbackQuery.getId(), null);
                return;
            }

            // Şehir Seçim Sayfaları (Kullanıcı)
            if (data.startsWith("city_page:")) {
                int page = Integer.parseInt(data.substring(10));
                EditMessageText em = EditMessageText.builder()
                        .chatId(chatId)
                        .messageId(messageId)
                        .text("📍 Lütfen KYK yurdunuzun bulunduğu şehri seçin:")
                        .replyMarkup(BotKeyboardHelper.getCitySelectionKeyboard(page))
                        .build();
                execute(em);
                answerCallback(callbackQuery.getId(), null);
                return;
            }

            if (data.startsWith("set_city:")) {
                int plate = Integer.parseInt(data.substring(9));
                setUserCity(chatId, plate, adminService.isAdmin(chatId));
                answerCallback(callbackQuery.getId(), "✅ Şehriniz ayarlandı!");
                return;
            }

            if ("open_city_selector".equals(data)) {
                EditMessageText em = EditMessageText.builder()
                        .chatId(chatId)
                        .messageId(messageId)
                        .text("📍 Lütfen KYK yurdunuzun bulunduğu şehri seçin:")
                        .replyMarkup(BotKeyboardHelper.getCitySelectionKeyboard(0))
                        .build();
                execute(em);
                answerCallback(callbackQuery.getId(), null);
                return;
            }

            if (data.startsWith("toggle_notifications:")) {
                boolean enable = "on".equals(data.substring(21));
                BotUser user = botUserRepository.findById(chatId).orElseThrow();
                user.setNotificationsEnabled(enable);
                botUserRepository.save(user);

                int bfHour = user.getBreakfastHour() != null ? user.getBreakfastHour() : 7;
                int dnHour = user.getDinnerHour() != null ? user.getDinnerHour() : 15;

                EditMessageText em = EditMessageText.builder()
                        .chatId(chatId)
                        .messageId(messageId)
                        .text(enable ? "🔔 Günlük yemek bildirimleri aktif edildi!" : "🔕 Bildirimler kapatıldı.")
                        .replyMarkup(BotKeyboardHelper.getSettingsKeyboard(enable, bfHour, dnHour))
                        .build();
                execute(em);
                answerCallback(callbackQuery.getId(), enable ? "Bildirimler açıldı" : "Bildirimler kapatıldı");
                return;
            }

            if ("set_notif_time:breakfast".equals(data)) {
                EditMessageText em = EditMessageText.builder()
                        .chatId(chatId)
                        .messageId(messageId)
                        .text("🍳 Sabah kahvaltısı bildirimini saat kaçta almak istersiniz?")
                        .replyMarkup(BotKeyboardHelper.getBreakfastHourKeyboard())
                        .build();
                execute(em);
                answerCallback(callbackQuery.getId(), null);
                return;
            }

            if ("set_notif_time:dinner".equals(data)) {
                EditMessageText em = EditMessageText.builder()
                        .chatId(chatId)
                        .messageId(messageId)
                        .text("🍲 Akşam yemeği bildirimini saat kaçta almak istersiniz?")
                        .replyMarkup(BotKeyboardHelper.getDinnerHourKeyboard())
                        .build();
                execute(em);
                answerCallback(callbackQuery.getId(), null);
                return;
            }

            if (data.startsWith("save_hour:breakfast:")) {
                int hour = Integer.parseInt(data.substring(20));
                BotUser user = botUserRepository.findById(chatId).orElseThrow();
                user.setBreakfastHour(hour);
                botUserRepository.save(user);

                int dnHour = user.getDinnerHour() != null ? user.getDinnerHour() : 15;
                EditMessageText em = EditMessageText.builder()
                        .chatId(chatId)
                        .messageId(messageId)
                        .text("✅ Sabah kahvaltısı bildirim saatiniz *" + String.format("%02d:00", hour) + "* olarak ayarlandı!")
                        .parseMode("Markdown")
                        .replyMarkup(BotKeyboardHelper.getSettingsKeyboard(user.isNotificationsEnabled(), hour, dnHour))
                        .build();
                execute(em);
                answerCallback(callbackQuery.getId(), "Saat kaydedildi!");
                return;
            }

            if (data.startsWith("save_hour:dinner:")) {
                int hour = Integer.parseInt(data.substring(17));
                BotUser user = botUserRepository.findById(chatId).orElseThrow();
                user.setDinnerHour(hour);
                botUserRepository.save(user);

                int bfHour = user.getBreakfastHour() != null ? user.getBreakfastHour() : 7;
                EditMessageText em = EditMessageText.builder()
                        .chatId(chatId)
                        .messageId(messageId)
                        .text("✅ Akşam yemeği bildirim saatiniz *" + String.format("%02d:00", hour) + "* olarak ayarlandı!")
                        .parseMode("Markdown")
                        .replyMarkup(BotKeyboardHelper.getSettingsKeyboard(user.isNotificationsEnabled(), bfHour, hour))
                        .build();
                execute(em);
                answerCallback(callbackQuery.getId(), "Saat kaydedildi!");
                return;
            }

            if ("back_to_settings".equals(data)) {
                BotUser user = botUserRepository.findById(chatId).orElseThrow();
                int bfHour = user.getBreakfastHour() != null ? user.getBreakfastHour() : 7;
                int dnHour = user.getDinnerHour() != null ? user.getDinnerHour() : 15;

                EditMessageText em = EditMessageText.builder()
                        .chatId(chatId)
                        .messageId(messageId)
                        .text("⚙️ *Kullanıcı Ayarları*\n\nBildirim saatlerinizi veya şehrinizi güncelleyebilirsiniz:")
                        .parseMode("Markdown")
                        .replyMarkup(BotKeyboardHelper.getSettingsKeyboard(user.isNotificationsEnabled(), bfHour, dnHour))
                        .build();
                execute(em);
                answerCallback(callbackQuery.getId(), null);
                return;
            }

            if (data.startsWith("week_meal:")) {
                LocalDate date = LocalDate.parse(data.substring(10));
                sendDailyMeal(chatId, date, adminService.isAdmin(chatId));
                answerCallback(callbackQuery.getId(), null);
                return;
            }

            if (data.startsWith("nav_day:")) {
                LocalDate date = LocalDate.parse(data.substring(8));
                BotUser user = botUserRepository.findById(chatId).orElseThrow();
                int cityId = user.getCityId() != null ? user.getCityId() : 1;
                String cityName = CityHelper.getCityName(cityId);

                List<Meal> meals = mealSyncService.ensureMealsForDateAndCity(date, cityId);
                Double avgScore = mealRatingRepository.getAverageScore(date, 1, cityId);
                long ratingCount = mealRatingRepository.countByDateAndMealTypeAndCityId(date, 1, cityId);
                String updatedText = MenuFormatter.formatDailyMenu(date, cityName, meals, avgScore, ratingCount);

                try {
                    EditMessageText em = EditMessageText.builder()
                            .chatId(chatId)
                            .messageId(messageId)
                            .text(updatedText)
                            .parseMode("Markdown")
                            .replyMarkup(BotKeyboardHelper.getMealActionKeyboard(date, cityId))
                            .build();
                    execute(em);
                } catch (Exception ignored) {}
                answerCallback(callbackQuery.getId(), null);
                return;
            }

            if (data.startsWith("rate_meal:")) {
                String[] parts = data.split(":");
                int score = Integer.parseInt(parts[1]);
                LocalDate date = LocalDate.parse(parts[2]);
                int cityId = Integer.parseInt(parts[3]);

                MealRating rating = mealRatingRepository
                        .findByUserChatIdAndDateAndMealTypeAndCityId(chatId, date, 1, cityId)
                        .orElse(MealRating.builder()
                                .userChatId(chatId)
                                .date(date)
                                .mealType(1)
                                .cityId(cityId)
                                .build());
                rating.setScore(score);
                mealRatingRepository.save(rating);

                answerCallback(callbackQuery.getId(), "Puanınız kaydedildi! ⭐ " + score + "/5");

                // Menüyü yeni puanla anında güncelle
                List<Meal> meals = mealService.getMealsByDate(date).stream()
                        .filter(m -> m.getCityId() != null && m.getCityId().equals(cityId))
                        .toList();
                String cityName = CityHelper.getCityName(cityId);
                Double avgScore = mealRatingRepository.getAverageScore(date, 1, cityId);
                long ratingCount = mealRatingRepository.countByDateAndMealTypeAndCityId(date, 1, cityId);
                String updatedText = MenuFormatter.formatDailyMenu(date, cityName, meals, avgScore, ratingCount);

                try {
                    EditMessageText em = EditMessageText.builder()
                            .chatId(chatId)
                            .messageId(messageId)
                            .text(updatedText)
                            .parseMode("Markdown")
                            .replyMarkup(BotKeyboardHelper.getMealActionKeyboard(date, cityId))
                            .build();
                    execute(em);
                } catch (Exception ignored) {}
                return;
            }

            if ("action:random_food".equals(data)) {
                sendRandomFoodSuggestion(chatId, messageId);
                answerCallback(callbackQuery.getId(), null);
                return;
            }

            // YÖNETİCİ PANELİ BUTON EYLEMLERİ
            if (data.startsWith("admin_action:")) {
                if (!adminService.isAdmin(chatId)) {
                    answerCallback(callbackQuery.getId(), "🚫 Yetkiniz yok.");
                    return;
                }
                handleAdminAction(chatId, messageId, data.substring(13));
                answerCallback(callbackQuery.getId(), null);
            }

        } catch (Exception e) {
            logger.error("CallbackQuery işleme hatası", e);
        }
    }

    private void handleAdminAction(Long chatId, Integer messageId, String action) throws TelegramApiException {
        if ("dashboard".equals(action)) {
            sendAdminDashboardEdit(chatId, messageId);
            return;
        }

        if ("stats".equals(action)) {
            String stats = adminService.getBotStats();
            try {
                EditMessageText em = EditMessageText.builder()
                        .chatId(chatId)
                        .messageId(messageId)
                        .text(stats)
                        .parseMode("Markdown")
                        .replyMarkup(AdminKeyboardHelper.getAdminDashboardKeyboard())
                        .build();
                execute(em);
            } catch (Exception ignored) {}
            return;
        }

        if (action.startsWith("users:")) {
            int page = Integer.parseInt(action.substring(6));
            String userList = adminService.getUserList(page);
            int totalPages = adminService.getTotalUserPages();
            try {
                EditMessageText em = EditMessageText.builder()
                        .chatId(chatId)
                        .messageId(messageId)
                        .text(userList)
                        .replyMarkup(AdminKeyboardHelper.getUserListNavKeyboard(page, totalPages))
                        .build();
                execute(em);
            } catch (Exception ignored) {}
            return;
        }

        if ("sync_all".equals(action)) {
            sendPlainMessage(chatId, "⏳ Tüm illerin menü senkronizasyonu başlatıldı...");
            mealSyncService.fetchMealsForAllActiveCities();
            sendPlainMessage(chatId, "✅ Tüm aktif iller güncellendi!");
            return;
        }

        // Tek İl Çekme Sayfalaması
        if (action.startsWith("sync_city_page:")) {
            int page = Integer.parseInt(action.substring(15));
            EditMessageText em = EditMessageText.builder()
                    .chatId(chatId)
                    .messageId(messageId)
                    .text("📍 Menüsünü anında çekmek istediğiniz ili seçin:")
                    .replyMarkup(BotKeyboardHelper.getAdminCitySyncKeyboard(page))
                    .build();
            execute(em);
            return;
        }

        // Seçilen ili anında çek
        if (action.startsWith("sync_city_run:")) {
            int plate = Integer.parseInt(action.substring(14));
            String cityName = CityHelper.getCityName(plate);
            sendPlainMessage(chatId, "⏳ *" + cityName + "* menüsü çekiliyor...");
            int count = mealSyncService.syncCityMenu(plate, YearMonth.now());
            sendPlainMessage(chatId, "✅ *" + cityName + "* için *" + count + "* adet öğün başarıyla kaydedildi!");
            return;
        }

        // Duyuru Sihirbazı Başlatma
        if ("bc_prompt_text".equals(action)) {
            adminStates.put(chatId, AdminState.WAITING_TEXT_BROADCAST);
            sendPlainMessage(chatId, "📝 Lütfen göndermek istediğiniz **Duyuru Metnini** buraya yazıp gönderin.\n\n_İptal etmek için:_ `/iptal`");
            return;
        }

        if ("bc_prompt_img".equals(action)) {
            adminStates.put(chatId, AdminState.WAITING_IMG_BROADCAST);
            sendPlainMessage(chatId, "🖼️ Lütfen göndermek istediğiniz **Fotoğrafı** gönderin (açıklama ekleyebilirsiniz).\n\n_İptal etmek için:_ `/iptal`");
            return;
        }

        // Duyuru Onay & İptal
        if ("bc_confirm".equals(action)) {
            String fileId = pendingBroadcastImageFileId.remove(chatId);
            String text = pendingBroadcastText.remove(chatId);
            if (fileId != null) {
                int count = broadcastImage(fileId, text);
                sendPlainMessage(chatId, "🚀 Resimli duyuru *" + count + "* kullanıcıya ulaştırıldı!");
            } else if (text != null) {
                int count = broadcastMessage(text);
                sendPlainMessage(chatId, "🚀 Duyuru *" + count + "* kullanıcıya ulaştırıldı!");
            }
            return;
        }

        if ("bc_cancel".equals(action)) {
            pendingBroadcastText.remove(chatId);
            pendingBroadcastImageFileId.remove(chatId);
            sendPlainMessage(chatId, "❌ Duyuru iptal edildi.");
            return;
        }

        // Admin Yönetimi (Listele / Ekle / Çıkar)
        if ("manage_admins".equals(action)) {
            EditMessageText em = EditMessageText.builder()
                    .chatId(chatId)
                    .messageId(messageId)
                    .text(adminService.formatAdminList(chatId))
                    .parseMode("Markdown")
                    .replyMarkup(AdminKeyboardHelper.getAdminManagementKeyboard(adminService.getAllAdmins(), chatId))
                    .build();
            execute(em);
            return;
        }

        if ("prompt_add_admin".equals(action)) {
            adminStates.put(chatId, AdminState.WAITING_ADD_ADMIN);
            sendPlainMessage(chatId, "👑 *Yeni Admin Ekleme*\n\nLütfen yönetici yapmak istediğiniz kullanıcının **Chat ID** numarasını (Örn: `123456789`) veya **Telegram kullanıcı adını** (Örn: `@kullaniciadi`) yazıp gönderin.\n\n_İptal etmek için:_ `/iptal`");
            return;
        }

        if (action.startsWith("remove_admin:")) {
            Long targetChatId = Long.parseLong(action.substring(13));
            var revokedOpt = adminService.revokeAdmin(targetChatId, chatId);
            if (revokedOpt.isPresent()) {
                BotUser removed = revokedOpt.get();
                try {
                    SendMessage notifyRemoved = SendMessage.builder()
                            .chatId(removed.getChatId())
                            .text("ℹ️ Yönetici yetkiniz sonlandırılmıştır.")
                            .replyMarkup(BotKeyboardHelper.getMainReplyKeyboard(false))
                            .build();
                    execute(notifyRemoved);
                } catch (Exception ignored) {}
            }

            EditMessageText em = EditMessageText.builder()
                    .chatId(chatId)
                    .messageId(messageId)
                    .text(adminService.formatAdminList(chatId))
                    .parseMode("Markdown")
                    .replyMarkup(AdminKeyboardHelper.getAdminManagementKeyboard(adminService.getAllAdmins(), chatId))
                    .build();
            execute(em);
        }
    }

    private void sendAdminDashboard(Long chatId) throws TelegramApiException {
        SendMessage sm = SendMessage.builder()
                .chatId(chatId)
                .text("🔧 *YÖNETİCİ KONTROL PANELİ 2.0*\n\nBotu yönetmek için aşağıdaki butonları kullanabilirsiniz:")
                .parseMode("Markdown")
                .replyMarkup(AdminKeyboardHelper.getAdminDashboardKeyboard())
                .build();
        execute(sm);
    }

    private void sendAdminDashboardEdit(Long chatId, Integer messageId) throws TelegramApiException {
        EditMessageText em = EditMessageText.builder()
                .chatId(chatId)
                .messageId(messageId)
                .text("🔧 *YÖNETİCİ KONTROL PANELİ 2.0*\n\nBotu yönetmek için aşağıdaki butonları kullanabilirsiniz:")
                .parseMode("Markdown")
                .replyMarkup(AdminKeyboardHelper.getAdminDashboardKeyboard())
                .build();
        execute(em);
    }

    private void sendWelcome(Long chatId, boolean isAdmin) throws TelegramApiException {
        BotUser user = botUserRepository.findById(chatId).orElse(null);
        int cityPlate = (user != null && user.getCityId() != null) ? user.getCityId() : 1;
        String cityName = CityHelper.getCityName(cityPlate);
        int bfHour = (user != null && user.getBreakfastHour() != null) ? user.getBreakfastHour() : 7;
        int dnHour = (user != null && user.getDinnerHour() != null) ? user.getDinnerHour() : 15;

        String notifStatus = (user != null && user.isNotificationsEnabled())
                ? String.format("Açık ✅ (%02d:00 & %02d:00)", bfHour, dnHour)
                : "Kapalı ❌";

        String text = String.format("""
                👋 *KYK Yemek Takip Botu 2.0'a Hoş Geldiniz!*

                81 ildeki devlet yurtlarının günlük kahvaltı ve akşam yemeği menülerini, kalori detaylarını ve öğrenci puanlarını bu bot üzerinden takip edebilirsiniz.

                📍 *Seçili Şehriniz:* %02d - %s
                🔔 *Bildirimler:* %s

                💡 _Şehrinizi veya bildirim saatlerinizi değiştirmek için_ `⚙️ Şehir / Ayarlar` _butonunu kullanabilirsiniz._ ⬇️
                """,
                cityPlate,
                cityName,
                notifStatus
        );

        SendMessage sm = SendMessage.builder()
                .chatId(chatId)
                .text(text)
                .parseMode("Markdown")
                .replyMarkup(BotKeyboardHelper.getMainReplyKeyboard(isAdmin))
                .build();
        execute(sm);
    }

    private void sendDailyMeal(Long chatId, LocalDate date, boolean isAdmin) throws TelegramApiException {
        BotUser user = botUserRepository.findById(chatId).orElseThrow();
        int cityId = user.getCityId() != null ? user.getCityId() : 1;
        String cityName = CityHelper.getCityName(cityId);

        List<Meal> meals = mealSyncService.ensureMealsForDateAndCity(date, cityId);

        Double avgScore = mealRatingRepository.getAverageScore(date, 1, cityId);
        long ratingCount = mealRatingRepository.countByDateAndMealTypeAndCityId(date, 1, cityId);

        String formatted = MenuFormatter.formatDailyMenu(date, cityName, meals, avgScore, ratingCount);

        SendMessage sm = SendMessage.builder()
                .chatId(chatId)
                .text(formatted)
                .parseMode("Markdown")
                .replyMarkup(BotKeyboardHelper.getMealActionKeyboard(date, cityId))
                .build();
        execute(sm);
    }

    private void sendRandomFoodSuggestion(Long chatId, Integer messageId) throws TelegramApiException {
        int index = java.util.concurrent.ThreadLocalRandom.current().nextInt(RANDOM_FOOD_IDEAS.size());
        String food = RANDOM_FOOD_IDEAS.get(index);

        String text = "🎲 *BUGÜN NE YESEM? (Öğrenci Tavsiyesi)*\n━━━━━━━━━━━━━━━━━━━━━━\n\n"
                + food
                + "\n\n━━━━━━━━━━━━━━━━━━━━━━\n💡 _Beğenmediysen butona basıp çarkı tekrar çevir!_";

        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        List<InlineKeyboardButton> row = new ArrayList<>();
        InlineKeyboardButton btnReroll = new InlineKeyboardButton();
        btnReroll.setText("🎲 Başka Bir Şey Öner");
        btnReroll.setCallbackData("action:random_food");
        row.add(btnReroll);
        rows.add(row);
        markup.setKeyboard(rows);

        if (messageId != null) {
            try {
                EditMessageText em = EditMessageText.builder()
                        .chatId(chatId)
                        .messageId(messageId)
                        .text(text)
                        .parseMode("Markdown")
                        .replyMarkup(markup)
                        .build();
                execute(em);
                return;
            } catch (Exception ignored) {}
        }

        SendMessage sm = SendMessage.builder()
                .chatId(chatId)
                .text(text)
                .parseMode("Markdown")
                .replyMarkup(markup)
                .build();
        execute(sm);
    }

    private void sendWeeklyMenuSelector(Long chatId) throws TelegramApiException {
        LocalDate today = LocalDate.now(TURKEY_ZONE);
        SendMessage sm = SendMessage.builder()
                .chatId(chatId)
                .text("📅 Önümüzdeki 7 günden menüsünü görmek istediğiniz günü seçin:")
                .replyMarkup(BotKeyboardHelper.getWeeklyMenuKeyboard(today))
                .build();
        execute(sm);
    }

    private void sendSettings(Long chatId) throws TelegramApiException {
        BotUser user = botUserRepository.findById(chatId).orElseThrow();
        int cityId = user.getCityId() != null ? user.getCityId() : 1;
        String cityName = CityHelper.getCityName(cityId);
        int bfHour = user.getBreakfastHour() != null ? user.getBreakfastHour() : 7;
        int dnHour = user.getDinnerHour() != null ? user.getDinnerHour() : 15;

        String text = String.format("""
                ⚙️ *Kullanıcı Ayarları*

                📍 *Mevcut Şehir:* %d - %s
                🔔 *Günlük Bildirimler:* %s
                🍳 *Kahvaltı Bildirimi:* %02d:00
                🍲 *Akşam Bildirimi:* %02d:00

                Aşağıdaki butonları kullanarak şehrinizi ve bildirim saatlerinizi kişiselleştirebilirsiniz:
                """,
                cityId,
                cityName,
                user.isNotificationsEnabled() ? "Açık ✅" : "Kapalı ❌",
                bfHour,
                dnHour
        );

        SendMessage sm = SendMessage.builder()
                .chatId(chatId)
                .text(text)
                .parseMode("Markdown")
                .replyMarkup(BotKeyboardHelper.getSettingsKeyboard(user.isNotificationsEnabled(), bfHour, dnHour))
                .build();
        execute(sm);
    }

    private void openCitySelector(Long chatId) throws TelegramApiException {
        SendMessage sm = SendMessage.builder()
                .chatId(chatId)
                .text("📍 Lütfen KYK yurdunuzun bulunduğu şehri seçin:")
                .replyMarkup(BotKeyboardHelper.getCitySelectionKeyboard(0))
                .build();
        execute(sm);
    }

    private void setUserCity(Long chatId, int plate, boolean isAdmin) throws TelegramApiException {
        if (plate < 1 || plate > 81) {
            sendPlainMessage(chatId, "❌ Geçersiz plaka kodu. Lütfen 1-81 arasında bir sayı girin.");
            return;
        }

        BotUser user = botUserRepository.findById(chatId).orElseThrow();
        user.setCityId(plate);
        botUserRepository.save(user);

        String cityName = CityHelper.getCityName(plate);
        mealSyncService.syncCityMenuAsync(plate);

        String msg = String.format("✅ Şehriniz *%d - %s* olarak güncellendi!\nMenüleri görmek için `🍽️ Bugünün Menüsü` butonuna basabilirsiniz.", plate, cityName);
        SendMessage sm = SendMessage.builder()
                .chatId(chatId)
                .text(msg)
                .parseMode("Markdown")
                .replyMarkup(BotKeyboardHelper.getMainReplyKeyboard(isAdmin))
                .build();
        execute(sm);
    }

    private void sendHelp(Long chatId, boolean isAdmin) throws TelegramApiException {
        String help = """
                ℹ️ *KYK Yemek Takip Botu 2.0 — Kullanım Rehberi*

                🍽️ *Menü & Keşif Butonları:*
                • `🍽️ Bugünün Menüsü` : Bugünün kahvaltı ve akşam menüsü (◀️ ▶️ ile 30 gün gezinme)
                • `🌅 Yarının Menüsü` : Yarının menüsü
                • `📅 Haftalık Menü` : 7 günlük hızlı takvim seçimi
                • `🔍 Yemek Ara` : Sohbete yemek adı yazarak (örn: `pizza`, `köfte`) bu ay hangi gün çıkacağını bulma
                • `🎲 Ne Yesem?` : Yurt yemekhanesi dışında öğrenci dostu yemek önerisi çarkı
                • `⚙️ Şehir / Ayarlar` : 81 il seçimi ve kişisel bildirim saati ayarları

                💬 *Kısayol Komutları:*
                • `/sehir_sec [plaka]` : Şehrinizi doğrudan plaka koduyla ayarlayın (Örn: `/sehir_sec 06`)
                • `/iletisim [mesaj]` : Bot geliştiricilerine öneri veya hata bildirin
                • `/start` : Ana menüyü ve klavyeyi yeniler
                """;
        if (isAdmin) {
            help += """

                    🔧 *Yönetici Kısayolları:*
                    • `🔧 Yönetici Paneli` veya `/admin` : İstatistikler, duyuru, il senkronizasyonu ve admin yönetimi
                    • `/admin_ekle [@kullanıcı veya ID]` : Hızlı admin ekleme
                    • `/admin_cikar [ID]` : Admin yetkisi kaldırma
                    """;
        }
        sendPlainMessage(chatId, help);
    }

    private void forwardFeedbackToAdmins(Long chatId, User from, String feedbackText) throws TelegramApiException {
        List<BotUser> admins = botUserRepository.findByIsAdminTrue();
        String msg = String.format("📩 *YENİ GERİ BİLDİRİM*\nKimden: %s (@%s, ID: `%d`)\n\nMesaj:\n%s",
                from.getFirstName(), from.getUserName(), chatId, feedbackText);

        for (BotUser admin : admins) {
            try {
                SendMessage sm = SendMessage.builder()
                        .chatId(admin.getChatId())
                        .text(msg)
                        .parseMode("Markdown")
                        .build();
                execute(sm);
            } catch (Exception ignored) {}
        }
        sendPlainMessage(chatId, "✅ Mesajınız yöneticilere iletildi. Geri bildiriminiz için teşekkür ederiz!");
    }

    private int broadcastMessage(String message) {
        List<BotUser> users = botUserRepository.findAll();
        int success = 0;
        for (BotUser u : users) {
            try {
                SendMessage sm = SendMessage.builder()
                        .chatId(u.getChatId())
                        .text("📢 *Duyuru*\n\n" + message)
                        .parseMode("Markdown")
                        .build();
                execute(sm);
                success++;
                Thread.sleep(40);
            } catch (Exception ignored) {}
        }
        return success;
    }

    private int broadcastImage(String fileId, String caption) {
        List<BotUser> users = botUserRepository.findAll();
        int success = 0;
        for (BotUser u : users) {
            try {
                SendPhoto sp = SendPhoto.builder()
                        .chatId(u.getChatId().toString())
                        .photo(new InputFile(fileId))
                        .caption(caption.isBlank() ? "📢 Duyuru" : "📢 *Duyuru*\n\n" + caption)
                        .parseMode("Markdown")
                        .build();
                execute(sp);
                success++;
                Thread.sleep(40);
            } catch (Exception ignored) {}
        }
        return success;
    }

    private void saveOrUpdateUser(Long chatId, User user) {
        BotUser botUser = botUserRepository.findById(chatId).orElse(null);
        if (botUser == null) {
            botUser = BotUser.builder()
                    .chatId(chatId)
                    .username(user.getUserName())
                    .firstName(user.getFirstName())
                    .lastName(user.getLastName())
                    .notificationsEnabled(true)
                    .isAdmin(false)
                    .cityId(1)
                    .lastInteractionDate(LocalDateTime.now())
                    .lastActivityDate(LocalDateTime.now())
                    .build();
        } else {
            botUser.setUsername(user.getUserName());
            botUser.setFirstName(user.getFirstName());
            botUser.setLastName(user.getLastName());
            botUser.setLastActivityDate(LocalDateTime.now());
            if (botUser.getCityId() == null) botUser.setCityId(1);
            if (botUser.getBreakfastHour() == null) botUser.setBreakfastHour(7);
            if (botUser.getDinnerHour() == null) botUser.setDinnerHour(15);
            if (botUser.getIsAdmin() == null) botUser.setIsAdmin(false);
        }
        botUserRepository.save(botUser);
    }

    private void sendPlainMessage(Long chatId, String text) throws TelegramApiException {
        boolean isAdmin = adminService.isAdmin(chatId);
        SendMessage sm = SendMessage.builder()
                .chatId(chatId)
                .text(text)
                .parseMode("Markdown")
                .replyMarkup(BotKeyboardHelper.getMainReplyKeyboard(isAdmin))
                .build();
        execute(sm);
    }

    private void answerCallback(String callbackId, String text) {
        try {
            AnswerCallbackQuery acq = new AnswerCallbackQuery();
            acq.setCallbackQueryId(callbackId);
            if (text != null) acq.setText(text);
            execute(acq);
        } catch (Exception ignored) {}
    }
}
