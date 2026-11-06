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

    public static String formatDailyMenu(LocalDate date, String cityName, List<Meal> meals) {
        return formatDailyMenu(date, cityName, meals, null, null);
    }

    /**
     * Günlük tam menü mesajı (Bugün / Yarın) - Sade, Okunabilir ve Hızlı Tasarım
     */
    public static String formatDailyMenu(LocalDate date, String cityName, List<Meal> meals, Double ratingScore, Long ratingCount) {
        StringBuilder sb = new StringBuilder();

        sb.append("📍 *").append(cityName).append(" KYK Yurtları*\n");
        sb.append("📅 ").append(date.format(DATE_FMT)).append("\n\n");

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
            appendMealBlock(sb, "🍳 KAHVALTI", breakfast, breakfastTime);
            sb.append("\n");
        } else {
            sb.append("🍳 *KAHVALTI* `(").append(breakfastTime).append(")`\n");
            sb.append("└ _Bu tarihin kahvaltı menüsü henüz yayınlanmadı._\n\n");
        }

        if (dinner != null) {
            appendMealBlock(sb, "🍲 AKŞAM YEMEĞİ", dinner, dinnerTime);
        } else {
            sb.append("🍲 *AKŞAM YEMEĞİ* `(").append(dinnerTime).append(")`\n");
            sb.append("└ _İl müdürlüğü bu tarihin akşam menüsünü henüz yayınlamadı._\n");
        }

        // Öğrenci Puanı Göstergesi
        if (ratingScore != null && ratingCount != null && ratingCount > 0) {
            sb.append("\n⭐ *Öğrenci Puanı:* `").append(String.format(Locale.US, "%.1f", ratingScore))
              .append(" / 5.0` (").append(ratingCount).append(" Oy)\n");
        }

        return sb.toString().trim();
    }

    /**
     * Günlük tekil bildirim mesajı (Kilit ekranında tek bakışta yemekleri gösterir)
     */
    public static String formatSingleMealNotification(LocalDate date, String cityName, Meal meal, String title, String timeRange) {
        StringBuilder sb = new StringBuilder();

        sb.append(title).append(" `(").append(timeRange).append(")`\n");
        sb.append("📍 *").append(cityName).append(" KYK Yurtları*\n\n");

        appendMealItemsAndFooter(sb, meal);

        return sb.toString().trim();
    }

    private static void appendMealBlock(StringBuilder sb, String title, Meal meal, String timeRange) {
        sb.append(title).append(" `(").append(timeRange).append(")`\n");
        appendMealItemsAndFooter(sb, meal);
    }

    private static void appendMealItemsAndFooter(StringBuilder sb, Meal meal) {
        appendItem(sb, meal.getFirst());
        appendItem(sb, meal.getSecond());
        appendItem(sb, meal.getThird());
        appendItem(sb, meal.getFourth());

        int totalCal = CalorieHelper.ensureTotalCalories(meal);
        String smartTag = CalorieHelper.getShortTag(meal);

        sb.append("⚡ `").append(totalCal).append(" kcal`");
        if (!smartTag.isEmpty()) {
            sb.append(" • ").append(smartTag);
        }
        sb.append("\n");
    }

    private static void appendItem(StringBuilder sb, String name) {
        if (name == null || name.isBlank()) return;
        String trimmed = name.trim();
        String badge = detectBadge(trimmed);

        sb.append("• ").append(trimmed);
        if (!badge.isEmpty()) {
            sb.append(" ").append(badge);
        }
        sb.append("\n");
    }

    private static String detectBadge(String item) {
        String lower = item.toLowerCase(TR);
        if (lower.contains("tavuk") || lower.contains("kebap") || lower.contains("köfte") ||
            lower.contains("et") || lower.contains("kavurma") || lower.contains("baget") || lower.contains("döner")) {
            return "🥩";
        }
        if (lower.contains("tatlı") || lower.contains("baklava") || lower.contains("triliçe") ||
            lower.contains("pasta") || lower.contains("puding") || lower.contains("sütlaç") || lower.contains("helva") || lower.contains("kemalpaşa")) {
            return "🍰";
        }
        if (lower.contains("pizza") || lower.contains("hamburger") || lower.contains("sosis") || lower.contains("sucuk")) {
            return "🍕";
        }
        if (lower.contains("fasulye") || lower.contains("nohut") || lower.contains("barbunya") || lower.contains("bezelye") || lower.contains("ıspanak")) {
            return "🥦";
        }
        if (lower.contains("salata") || lower.contains("cacık") || lower.contains("yoğurt") || lower.contains("meyve") || lower.contains("ayran")) {
            return "🥗";
        }
        return "";
    }

    private static boolean isWeekend(LocalDate date) {
        DayOfWeek dow = date.getDayOfWeek();
        return dow == DayOfWeek.SATURDAY || dow == DayOfWeek.SUNDAY;
    }
}
