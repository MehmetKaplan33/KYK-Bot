package com.kyk.mealtracker.bot;

import com.kyk.mealtracker.util.CityHelper;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class BotKeyboardHelper {

    public static final String BTN_TODAY = "🍽️ Bugünün Menüsü";
    public static final String BTN_TOMORROW = "🌅 Yarının Menüsü";
    public static final String BTN_WEEKLY = "📅 Haftalık Menü";
    public static final String BTN_SEARCH = "🔍 Yemek Ara";
    public static final String BTN_SETTINGS = "⚙️ Şehir / Ayarlar";
    public static final String BTN_RANDOM = "🎲 Ne Yesem?";
    public static final String BTN_ADMIN = "🔧 Yönetici Paneli";
    public static final String BTN_HELP = "ℹ️ Yardım";

    private BotKeyboardHelper() {}

    /**
     * Sohbet ekranının altında sürekli duran kalıcı menü klavyesi.
     * Kullanıcıların aramayı ve karar çarkını kolayca görmesi için 3 satır tasarlandı.
     */
    public static ReplyKeyboardMarkup getMainReplyKeyboard(boolean isAdmin) {
        ReplyKeyboardMarkup keyboardMarkup = new ReplyKeyboardMarkup();
        keyboardMarkup.setSelective(false);
        keyboardMarkup.setResizeKeyboard(true);
        keyboardMarkup.setOneTimeKeyboard(false);
        keyboardMarkup.setIsPersistent(true);

        List<KeyboardRow> keyboard = new ArrayList<>();

        KeyboardRow row1 = new KeyboardRow();
        row1.add(new KeyboardButton(BTN_TODAY));
        row1.add(new KeyboardButton(BTN_TOMORROW));

        KeyboardRow row2 = new KeyboardRow();
        row2.add(new KeyboardButton(BTN_WEEKLY));
        row2.add(new KeyboardButton(BTN_SEARCH));

        KeyboardRow row3 = new KeyboardRow();
        row3.add(new KeyboardButton(BTN_RANDOM));
        row3.add(new KeyboardButton(BTN_SETTINGS));

        keyboard.add(row1);
        keyboard.add(row2);
        keyboard.add(row3);

        if (isAdmin) {
            KeyboardRow adminRow = new KeyboardRow();
            adminRow.add(new KeyboardButton(BTN_ADMIN));
            keyboard.add(adminRow);
        }

        keyboardMarkup.setKeyboard(keyboard);
        return keyboardMarkup;
    }

    /**
     * Günün yemeği için Puanlama ve Alternatif Butonları
     */
    public static InlineKeyboardMarkup getMealActionKeyboard(LocalDate date, int cityId) {
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        // Reaksiyon / Puanlama Satırı
        List<InlineKeyboardButton> r1 = new ArrayList<>();
        InlineKeyboardButton btnGood = new InlineKeyboardButton();
        btnGood.setText("😋 Çok İyi");
        btnGood.setCallbackData(String.format("rate_meal:5:%s:%d", date, cityId));
        r1.add(btnGood);

        InlineKeyboardButton btnMid = new InlineKeyboardButton();
        btnMid.setText("😐 İdare Eder");
        btnMid.setCallbackData(String.format("rate_meal:3:%s:%d", date, cityId));
        r1.add(btnMid);

        InlineKeyboardButton btnBad = new InlineKeyboardButton();
        btnBad.setText("🤢 Kötü");
        btnBad.setCallbackData(String.format("rate_meal:1:%s:%d", date, cityId));
        r1.add(btnBad);
        rows.add(r1);

        // Ne Yesem Alternatif Butonu
        List<InlineKeyboardButton> r2 = new ArrayList<>();
        InlineKeyboardButton btnRandom = new InlineKeyboardButton();
        btnRandom.setText("🎲 Menüyü Sevmedim, Ne Yesem?");
        btnRandom.setCallbackData("action:random_food");
        r2.add(btnRandom);
        rows.add(r2);

        markup.setKeyboard(rows);
        return markup;
    }

    /**
     * Sayfalamalı Şehir Seçim Klavyesi (Kullanıcı için)
     */
    public static InlineKeyboardMarkup getCitySelectionKeyboard(int page) {
        return buildCityKeyboard(page, "set_city:", "city_page:");
    }

    /**
     * Sayfalamalı Şehir Seçim Klavyesi (Admin tek il senkronizasyonu için)
     */
    public static InlineKeyboardMarkup getAdminCitySyncKeyboard(int page) {
        return buildCityKeyboard(page, "admin_action:sync_city_run:", "admin_action:sync_city_page:");
    }

    private static InlineKeyboardMarkup buildCityKeyboard(int page, String selectPrefix, String pagePrefix) {
        int pageSize = 12;
        int totalCities = 81;
        int totalPages = (int) Math.ceil((double) totalCities / pageSize);
        if (page < 0) page = 0;
        if (page >= totalPages) page = totalPages - 1;

        int startPlate = page * pageSize + 1;
        int endPlate = Math.min(startPlate + pageSize - 1, totalCities);

        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        List<InlineKeyboardButton> currentRow = new ArrayList<>();

        for (int plate = startPlate; plate <= endPlate; plate++) {
            String name = CityHelper.getCityName(plate);
            InlineKeyboardButton btn = new InlineKeyboardButton();
            btn.setText(plate + " - " + name);
            btn.setCallbackData(selectPrefix + plate);
            currentRow.add(btn);

            if (currentRow.size() == 2) {
                rows.add(currentRow);
                currentRow = new ArrayList<>();
            }
        }
        if (!currentRow.isEmpty()) {
            rows.add(currentRow);
        }

        List<InlineKeyboardButton> navRow = new ArrayList<>();
        if (page > 0) {
            InlineKeyboardButton prev = new InlineKeyboardButton();
            prev.setText("◀️ Önceki");
            prev.setCallbackData(pagePrefix + (page - 1));
            navRow.add(prev);
        }

        InlineKeyboardButton pageIndicator = new InlineKeyboardButton();
        pageIndicator.setText((page + 1) + " / " + totalPages);
        pageIndicator.setCallbackData("noop");
        navRow.add(pageIndicator);

        if (page < totalPages - 1) {
            InlineKeyboardButton next = new InlineKeyboardButton();
            next.setText("Sonraki ▶️");
            next.setCallbackData(pagePrefix + (page + 1));
            navRow.add(next);
        }
        rows.add(navRow);

        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        markup.setKeyboard(rows);
        return markup;
    }

    /**
     * Haftalık menü gün butonları
     */
    public static InlineKeyboardMarkup getWeeklyMenuKeyboard(LocalDate startDate) {
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd MMMM EEEE", new Locale("tr", "TR"));

        for (int i = 0; i <= 6; i++) {
            LocalDate date = startDate.plusDays(i);
            InlineKeyboardButton button = new InlineKeyboardButton();
            button.setText(date.format(fmt));
            button.setCallbackData("week_meal:" + date);

            List<InlineKeyboardButton> row = new ArrayList<>();
            row.add(button);
            rows.add(row);
        }

        markup.setKeyboard(rows);
        return markup;
    }

    /**
     * Ayarlar menüsü inline butonları
     */
    public static InlineKeyboardMarkup getSettingsKeyboard(boolean notificationsEnabled, int breakfastHour, int dinnerHour) {
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        List<InlineKeyboardButton> r1 = new ArrayList<>();
        InlineKeyboardButton btnCity = new InlineKeyboardButton();
        btnCity.setText("📍 Şehrimi Değiştir");
        btnCity.setCallbackData("open_city_selector");
        r1.add(btnCity);
        rows.add(r1);

        List<InlineKeyboardButton> r2 = new ArrayList<>();
        InlineKeyboardButton btnNotif = new InlineKeyboardButton();
        if (notificationsEnabled) {
            btnNotif.setText("🔔 Bildirimler: Açık (Kapatmak için tıkla)");
            btnNotif.setCallbackData("toggle_notifications:off");
        } else {
            btnNotif.setText("🔕 Bildirimler: Kapalı (Açmak için tıkla)");
            btnNotif.setCallbackData("toggle_notifications:on");
        }
        r2.add(btnNotif);
        rows.add(r2);

        // Bildirim Saatlerini Ayarlama Butonları
        List<InlineKeyboardButton> r3 = new ArrayList<>();
        InlineKeyboardButton btnBfTime = new InlineKeyboardButton();
        btnBfTime.setText("🍳 Kahvaltı Saati: " + String.format("%02d:00", breakfastHour));
        btnBfTime.setCallbackData("set_notif_time:breakfast");
        r3.add(btnBfTime);
        rows.add(r3);

        List<InlineKeyboardButton> r4 = new ArrayList<>();
        InlineKeyboardButton btnDnTime = new InlineKeyboardButton();
        btnDnTime.setText("🍲 Akşam Saati: " + String.format("%02d:00", dinnerHour));
        btnDnTime.setCallbackData("set_notif_time:dinner");
        r4.add(btnDnTime);
        rows.add(r4);

        markup.setKeyboard(rows);
        return markup;
    }

    /**
     * Kahvaltı Saati Seçim Butonları
     */
    public static InlineKeyboardMarkup getBreakfastHourKeyboard() {
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        int[] hours = {6, 7, 8, 9, 10, 11};
        List<InlineKeyboardButton> row1 = new ArrayList<>();
        List<InlineKeyboardButton> row2 = new ArrayList<>();

        for (int i = 0; i < hours.length; i++) {
            InlineKeyboardButton btn = new InlineKeyboardButton();
            btn.setText(String.format("⏰ %02d:00", hours[i]));
            btn.setCallbackData("save_hour:breakfast:" + hours[i]);
            if (i < 3) row1.add(btn);
            else row2.add(btn);
        }
        rows.add(row1);
        rows.add(row2);

        List<InlineKeyboardButton> backRow = new ArrayList<>();
        InlineKeyboardButton btnBack = new InlineKeyboardButton();
        btnBack.setText("🔙 Ayarlara Dön");
        btnBack.setCallbackData("back_to_settings");
        backRow.add(btnBack);
        rows.add(backRow);

        markup.setKeyboard(rows);
        return markup;
    }

    /**
     * Akşam Yemeği Saati Seçim Butonları
     */
    public static InlineKeyboardMarkup getDinnerHourKeyboard() {
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        int[] hours = {14, 15, 16, 17, 18, 19};
        List<InlineKeyboardButton> row1 = new ArrayList<>();
        List<InlineKeyboardButton> row2 = new ArrayList<>();

        for (int i = 0; i < hours.length; i++) {
            InlineKeyboardButton btn = new InlineKeyboardButton();
            btn.setText(String.format("⏰ %02d:00", hours[i]));
            btn.setCallbackData("save_hour:dinner:" + hours[i]);
            if (i < 3) row1.add(btn);
            else row2.add(btn);
        }
        rows.add(row1);
        rows.add(row2);

        List<InlineKeyboardButton> backRow = new ArrayList<>();
        InlineKeyboardButton btnBack = new InlineKeyboardButton();
        btnBack.setText("🔙 Ayarlara Dön");
        btnBack.setCallbackData("back_to_settings");
        backRow.add(btnBack);
        rows.add(backRow);

        markup.setKeyboard(rows);
        return markup;
    }
}
