package com.kyk.mealtracker.bot;

import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;

import java.util.ArrayList;
import java.util.List;

public final class AdminKeyboardHelper {

    private AdminKeyboardHelper() {}

    /**
     * İnteraktif Yönetici Ana Kontrol Paneli 2.0
     */
    public static InlineKeyboardMarkup getAdminDashboardKeyboard() {
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        // Satır 1: İstatistikler ve Kullanıcı Listesi
        List<InlineKeyboardButton> r1 = new ArrayList<>();
        InlineKeyboardButton btnStats = new InlineKeyboardButton();
        btnStats.setText("📊 Bot İstatistikleri");
        btnStats.setCallbackData("admin_action:stats");
        r1.add(btnStats);

        InlineKeyboardButton btnUsers = new InlineKeyboardButton();
        btnUsers.setText("👥 Kullanıcı Listesi");
        btnUsers.setCallbackData("admin_action:users:0");
        r1.add(btnUsers);
        rows.add(r1);

        // Satır 2: Menü Senkronizasyonu & İl Listesi
        List<InlineKeyboardButton> r2 = new ArrayList<>();
        InlineKeyboardButton btnSyncAll = new InlineKeyboardButton();
        btnSyncAll.setText("🔄 Tüm İlleri Senkronize Et");
        btnSyncAll.setCallbackData("admin_action:sync_all");
        r2.add(btnSyncAll);

        InlineKeyboardButton btnSyncCity = new InlineKeyboardButton();
        btnSyncCity.setText("📍 Tek İl Menüsü Çek");
        btnSyncCity.setCallbackData("admin_action:sync_city_page:0");
        r2.add(btnSyncCity);
        rows.add(r2);

        // Satır 3: Toplu Duyuru Seçenekleri
        List<InlineKeyboardButton> r3 = new ArrayList<>();
        InlineKeyboardButton btnBroadcastText = new InlineKeyboardButton();
        btnBroadcastText.setText("📝 Metin Duyurusu");
        btnBroadcastText.setCallbackData("admin_action:bc_prompt_text");
        r3.add(btnBroadcastText);

        InlineKeyboardButton btnBroadcastImg = new InlineKeyboardButton();
        btnBroadcastImg.setText("🖼️ Resimli Duyuru");
        btnBroadcastImg.setCallbackData("admin_action:bc_prompt_img");
        r3.add(btnBroadcastImg);
        rows.add(r3);

        markup.setKeyboard(rows);
        return markup;
    }

    /**
     * Duyuru Onay ve İptal Butonları
     */
    public static InlineKeyboardMarkup getBroadcastConfirmKeyboard() {
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        List<InlineKeyboardButton> r1 = new ArrayList<>();
        InlineKeyboardButton btnConfirm = new InlineKeyboardButton();
        btnConfirm.setText("✅ Onayla ve Herkese Gönder");
        btnConfirm.setCallbackData("admin_action:bc_confirm");
        r1.add(btnConfirm);

        InlineKeyboardButton btnCancel = new InlineKeyboardButton();
        btnCancel.setText("❌ İptal Et");
        btnCancel.setCallbackData("admin_action:bc_cancel");
        r1.add(btnCancel);

        rows.add(r1);
        markup.setKeyboard(rows);
        return markup;
    }

    /**
     * Admin Kullanıcı Listesi Sayfalama
     */
    public static InlineKeyboardMarkup getUserListNavKeyboard(int currentPage, int totalPages) {
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        List<InlineKeyboardButton> navRow = new ArrayList<>();

        if (currentPage > 0) {
            InlineKeyboardButton prev = new InlineKeyboardButton();
            prev.setText("◀️ Önceki");
            prev.setCallbackData("admin_action:users:" + (currentPage - 1));
            navRow.add(prev);
        }

        InlineKeyboardButton curr = new InlineKeyboardButton();
        curr.setText((currentPage + 1) + " / " + totalPages);
        curr.setCallbackData("noop");
        navRow.add(curr);

        if (currentPage < totalPages - 1) {
            InlineKeyboardButton next = new InlineKeyboardButton();
            next.setText("Sonraki ▶️");
            next.setCallbackData("admin_action:users:" + (currentPage + 1));
            navRow.add(next);
        }
        rows.add(navRow);

        List<InlineKeyboardButton> backRow = new ArrayList<>();
        InlineKeyboardButton btnBack = new InlineKeyboardButton();
        btnBack.setText("🔙 Yönetici Paneline Dön");
        btnBack.setCallbackData("admin_action:dashboard");
        backRow.add(btnBack);
        rows.add(backRow);

        markup.setKeyboard(rows);
        return markup;
    }
}
