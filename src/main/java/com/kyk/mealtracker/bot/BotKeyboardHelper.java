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
    public static final String BTN_SETTINGS = "⚙️ Şehir / Ayarlar";
    public static final String BTN_ADMIN = "🔧 Yönetici Paneli";
    public static final String BTN_HELP = "ℹ️ Yardım";

    private BotKeyboardHelper() {}

    /**
     * Sohbet ekranının altında sürekli duran kalıcı menü klavyesi.
     * Admin ise ekstra "🔧 Yönetici Paneli" satırı eklenir.
     */
    public static ReplyKeyboardMarkup getMainReplyKeyboard(boolean isAdmin) {
        ReplyKeyboardMarkup keyboardMarkup = new ReplyKeyboardMarkup();
        keyboardMarkup.setSelective(false);
        keyboardMarkup.setResizeKeyboard(true);
        keyboardMarkup.setOneTimeKeyboard(false);
        keyboardMarkup.setIsPersistent(true); // Asla kaybolmaz

        List<KeyboardRow> keyboard = new ArrayList<>();

        KeyboardRow row1 = new KeyboardRow();
        row1.add(new KeyboardButton(BTN_TODAY));
        row1.add(new KeyboardButton(BTN_TOMORROW));

        KeyboardRow row2 = new KeyboardRow();
        row2.add(new KeyboardButton(BTN_WEEKLY));
        row2.add(new KeyboardButton(BTN_SETTINGS));

        keyboard.add(row1);
        keyboard.add(row2);

        if (isAdmin) {
            KeyboardRow adminRow = new KeyboardRow();
            adminRow.add(new KeyboardButton(BTN_ADMIN));
            keyboard.add(adminRow);
        }

        keyboardMarkup.setKeyboard(keyboard);
        return keyboardMarkup;
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
    public static InlineKeyboardMarkup getSettingsKeyboard(boolean notificationsEnabled) {
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

        markup.setKeyboard(rows);
        return markup;
    }
}
