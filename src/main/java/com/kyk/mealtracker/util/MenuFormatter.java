package com.kyk.mealtracker.util;

import com.kyk.mealtracker.entity.Meal;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

public final class MenuFormatter {

    private static final Locale TR = new Locale("tr", "TR");
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd MMMM yyyy, EEEE", TR);

    private MenuFormatter() {}

    /**
     * Günlük tam menü mesajı (Bugün / Yarın)
     */
    public static String formatDailyMenu(LocalDate date, String cityName, List<Meal> meals) {
        StringBuilder sb = new StringBuilder();
        
        sb.append("╭━━━━━━━━━━━━━━━━━━━━━╮\n");
        sb.append("  🏛 *").append(cityName.toUpperCase(TR)).append(" KYK YURTLARI*\n");
        sb.append("  📅 ").append(date.format(DATE_FMT)).append("\n");
        sb.append("╰━━━━━━━━━━━━━━━━━━━━━╯\n\n");

        Meal breakfast = meals.stream()
                .filter(m -> m.getMealType() != null && m.getMealType() == 0)
                .filter(MealValidator::isValidMeal)
                .findFirst()
                .orElse(null);

        Meal dinner = meals.stream()
                .filter(m -> m.getMealType() != null && m.getMealType() == 1)
                .filter(MealValidator::isValidMeal)
                .findFirst()
                .orElse(null);

        if (breakfast == null && dinner == null) {
            sb.append("ℹ️ *Bu tarih için menü bilgisi henüz sisteme girilmemiştir.*\n\n");
            sb.append("💡 _Not: Yurt idaresi yemek listesini güncellediğinde otomatik olarak burada görünecektir._\n");
            return sb.toString();
        }

        String breakfastTime = isWeekend(date) ? "06:00 - 12:30" : "06:00 - 12:00";
        String dinnerTime = "16:00 - 23:00";

        if (breakfast != null) {
            appendRichMealBlock(sb, "🍳 SABAH KAHVALTISI", breakfast, breakfastTime);
            sb.append("\n");
        } else {
            sb.append("🍳 *SABAH KAHVALTISI*\n");
            sb.append("└ _Kahvaltı menüsü henüz girilmedi._\n\n");
        }

        if (dinner != null) {
            appendRichMealBlock(sb, "🍲 AKŞAM YEMEĞİ", dinner, dinnerTime);
        } else {
            sb.append("🍲 *AKŞAM YEMEĞİ*\n");
            sb.append("└ _Akşam menüsü henüz girilmedi._\n");
        }

        sb.append("\n━━━━━━━━━━━━━━━━━━━━━━\n");
        sb.append("✨ *Afiyet olsun!*");
        return sb.toString();
    }

    /**
     * Sabah veya Akşam tek öğün bildirim mesajı
     */
    public static String formatSingleMealNotification(LocalDate date, String cityName, Meal meal, String title, String timeRange) {
        StringBuilder sb = new StringBuilder();
        sb.append("📢 *GÜNLÜK YEMEK BİLDİRİMİ*\n");
        sb.append("📍 *").append(cityName).append(" KYK Yurtları*\n");
        sb.append("📅 ").append(date.format(DATE_FMT)).append("\n");
        sb.append("━━━━━━━━━━━━━━━━━━━━━━\n\n");

        appendRichMealBlock(sb, title, meal, timeRange);

        sb.append("\n━━━━━━━━━━━━━━━━━━━━━━\n");
        sb.append("✨ *Afiyet olsun!*");
        return sb.toString();
    }

    private static void appendRichMealBlock(StringBuilder sb, String title, Meal meal, String timeRange) {
        sb.append(title);
        if (meal.getTotalCalories() != null && !meal.getTotalCalories().isBlank()) {
            sb.append("  ⚡️ *").append(meal.getTotalCalories()).append(" kcal*");
        }
        sb.append("\n");
        sb.append("⏰ *Servis:* `").append(timeRange).append("`\n");
        sb.append("───────────────────────\n");

        appendItem(sb, "🥣 1.", meal.getFirst(), meal.getFirstCalories());
        appendItem(sb, "🥩 2.", meal.getSecond(), meal.getSecondCalories());
        appendItem(sb, "🍚 3.", meal.getThird(), meal.getThirdCalories());
        appendItem(sb, "🥗 4.", meal.getFourth(), meal.getFourthCalories());
    }

    private static void appendItem(StringBuilder sb, String prefix, String name, String cal) {
        if (name == null || name.isBlank()) return;
        sb.append(prefix).append(" *").append(name.trim()).append("*");
        if (cal != null && !cal.isBlank()) {
            sb.append("  `(").append(cal.trim()).append(" kcal)`");
        }
        sb.append("\n");
    }

    private static boolean isWeekend(LocalDate date) {
        DayOfWeek dow = date.getDayOfWeek();
        return dow == DayOfWeek.SATURDAY || dow == DayOfWeek.SUNDAY;
    }
}
