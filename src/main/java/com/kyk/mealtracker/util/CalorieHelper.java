package com.kyk.mealtracker.util;

import com.kyk.mealtracker.entity.Meal;

import java.util.Locale;
import java.util.Map;

public final class CalorieHelper {

    private static final Locale TR = new Locale("tr", "TR");

    // Standart KYK Yemek Kalori Sözlüğü (Porsiyon bazlı ortalama kcal)
    private static final Map<String, Integer> FOOD_CALORIES = Map.ofEntries(
            // Çorbalar
            Map.entry("mercimek çorba", 145),
            Map.entry("süzme mercimek", 140),
            Map.entry("ezogelin çorba", 150),
            Map.entry("ezogelin", 150),
            Map.entry("yayla çorba", 135),
            Map.entry("yayla", 135),
            Map.entry("tarhana çorba", 130),
            Map.entry("tarhana", 130),
            Map.entry("domates çorba", 115),
            Map.entry("şehriye çorba", 125),
            Map.entry("düğün çorba", 160),
            Map.entry("tavuk suyu çorba", 155),
            Map.entry("mantar çorba", 140),
            Map.entry("kelle paça", 220),
            Map.entry("çorba", 140),

            // Ana Yemekler - Et & Tavuk
            Map.entry("adana kebap", 360),
            Map.entry("urfa kebap", 350),
            Map.entry("tavuk sote", 310),
            Map.entry("tavuk baget", 290),
            Map.entry("tavuk döner", 360),
            Map.entry("et döner", 410),
            Map.entry("kuru fasulye", 330),
            Map.entry("nohut", 340),
            Map.entry("kıymalı bezelye", 270),
            Map.entry("izmir köfte", 350),
            Map.entry("dalyan köfte", 340),
            Map.entry("kuru köfte", 360),
            Map.entry("karnıyarık", 320),
            Map.entry("musakka", 300),
            Map.entry("tas kebabı", 370),
            Map.entry("orman kebabı", 350),
            Map.entry("tavuk şnitzel", 340),
            Map.entry("tavuk kavurma", 330),
            Map.entry("etli türlü", 280),
            Map.entry("kıymalı ıspanak", 230),
            Map.entry("çiğ köfte", 240),

            // Pilav, Makarna, Hamur
            Map.entry("pirinç pilavı", 280),
            Map.entry("şehriyeli pirinç", 290),
            Map.entry("bulgur pilavı", 230),
            Map.entry("mercimekli bulgur", 250),
            Map.entry("makarna", 270),
            Map.entry("soslu makarna", 290),
            Map.entry("kıymalı makarna", 340),
            Map.entry("erişte", 280),
            Map.entry("mantı", 380),
            Map.entry("su böreği", 360),
            Map.entry("sigara böreği", 260),

            // Yan Ürünler & Salata
            Map.entry("cacık", 95),
            Map.entry("yoğurt", 120),
            Map.entry("ayran", 75),
            Map.entry("mevsim salata", 65),
            Map.entry("çoban salata", 75),
            Map.entry("mor lahana", 60),
            Map.entry("turşu", 30),
            Map.entry("meyve", 75),
            Map.entry("elma", 70),
            Map.entry("portakal", 65),
            Map.entry("mandalina", 50),
            Map.entry("muz", 110),

            // Tatlılar
            Map.entry("baklava", 420),
            Map.entry("kemalpaşa", 310),
            Map.entry("revani", 340),
            Map.entry("şekerpare", 330),
            Map.entry("triliçe", 280),
            Map.entry("sütlaç", 250),
            Map.entry("fırın sütlaç", 260),
            Map.entry("kazandibi", 240),
            Map.entry("tavukgöğsü", 230),
            Map.entry("puding", 210),
            Map.entry("supangle", 260),
            Map.entry("irmik helvası", 320),
            Map.entry("tulumba", 350)
    );

    private CalorieHelper() {}

    /**
     * Yemek isminden kalori tahmin eder veya mevcut sayısal değeri döndürür.
     */
    public static int getOrEstimateCalories(String itemName, String currentCalStr) {
        Integer parsed = parseCalorieNumber(currentCalStr);
        if (parsed != null && parsed > 0) return parsed;
        if (itemName == null || itemName.isBlank()) return 0;

        String lower = itemName.toLowerCase(TR).trim();
        for (Map.Entry<String, Integer> entry : FOOD_CALORIES.entrySet()) {
            if (lower.contains(entry.getKey())) {
                return entry.getValue();
            }
        }
        // Genel kategori bazlı tahminler
        if (lower.contains("çorba")) return 140;
        if (lower.contains("kebap") || lower.contains("köfte") || lower.contains("et") || lower.contains("tavuk") || lower.contains("balık")) return 330;
        if (lower.contains("pilav") || lower.contains("makarna") || lower.contains("börek")) return 270;
        if (lower.contains("tatlı") || lower.contains("helva") || lower.contains("pasta")) return 300;
        if (lower.contains("salata") || lower.contains("cacık") || lower.contains("meyve") || lower.contains("ayran")) return 80;

        return 180; // Varsayılan orta porsiyon
    }

    public static Integer parseCalorieNumber(String calStr) {
        if (calStr == null || calStr.isBlank()) return null;
        try {
            // "118 / 227 kcal" veya "348 kcal" -> ilk sayıyı al
            String clean = calStr.replaceAll("[^0-9/]", "").trim();
            if (clean.contains("/")) {
                clean = clean.split("/")[0].trim();
            }
            if (!clean.isEmpty()) {
                return Integer.parseInt(clean);
            }
        } catch (Exception ignored) {}
        return null;
    }

    /**
     * Bir öğünün toplam kalorisini kesin olarak belirler (boşsa hesaplar).
     */
    public static int ensureTotalCalories(Meal meal) {
        Integer total = parseCalorieNumber(meal.getTotalCalories());
        if (total != null && total > 0) return total;

        int cal1 = getOrEstimateCalories(meal.getFirst(), meal.getFirstCalories());
        int cal2 = getOrEstimateCalories(meal.getSecond(), meal.getSecondCalories());
        int cal3 = getOrEstimateCalories(meal.getThird(), meal.getThirdCalories());
        int cal4 = getOrEstimateCalories(meal.getFourth(), meal.getFourthCalories());

        int sum = cal1 + cal2 + cal3 + cal4;
        if (sum == 0) {
            sum = (meal.getMealType() != null && meal.getMealType() == 0) ? 650 : 950;
        }
        return sum;
    }

    /**
     * Menü içeriğine göre kısa, şık ve dikkat çekici etiket döndürür.
     */
    public static String getShortTag(Meal meal) {
        String allText = (safe(meal.getFirst()) + " " + safe(meal.getSecond()) + " " + safe(meal.getThird()) + " " + safe(meal.getFourth())).toLowerCase(TR);

        boolean hasMeat = allText.contains("et") || allText.contains("tavuk") || allText.contains("köfte") ||
                allText.contains("kebap") || allText.contains("kavurma") || allText.contains("döner") || allText.contains("baget");
        boolean hasDessert = allText.contains("tatlı") || allText.contains("baklava") || allText.contains("sütlaç") ||
                allText.contains("puding") || allText.contains("kemalpaşa") || allText.contains("revani") || allText.contains("helva");
        boolean hasLegumes = allText.contains("mercimek") || allText.contains("fasulye") || allText.contains("nohut");
        boolean hasCarb = allText.contains("pilav") || allText.contains("makarna") || allText.contains("börek") || allText.contains("mantı");

        if (hasDessert) return "*🍰 Tatlı Var!*";
        if (hasMeat && hasLegumes) return "*💪 Yüksek Protein & Lif*";
        if (hasMeat) return "*🥩 Protein Ağırlıklı*";
        if (hasLegumes && hasCarb) return "*⚡ Enerji Deposu*";
        if (meal.getMealType() != null && meal.getMealType() == 0) return "*🍳 Kahvaltı Menüsü*";
        return "*🥘 Dengeli Menü*";
    }

    /**
     * Menü içeriğine ve kalorisine göre dinamik, zengin beslenme yorumu üretir.
     */
    public static String generateSmartMealComment(Meal meal, int totalCal) {
        String allText = (safe(meal.getFirst()) + " " + safe(meal.getSecond()) + " " + safe(meal.getThird()) + " " + safe(meal.getFourth())).toLowerCase(TR);

        boolean hasMeat = allText.contains("et") || allText.contains("tavuk") || allText.contains("köfte") ||
                allText.contains("kebap") || allText.contains("kavurma") || allText.contains("döner") || allText.contains("baget");
        boolean hasDessert = allText.contains("tatlı") || allText.contains("baklava") || allText.contains("sütlaç") ||
                allText.contains("puding") || allText.contains("kemalpaşa") || allText.contains("revani") || allText.contains("helva");
        boolean hasLegumes = allText.contains("mercimek") || allText.contains("fasulye") || allText.contains("nohut");
        boolean hasCarb = allText.contains("pilav") || allText.contains("makarna") || allText.contains("börek") || allText.contains("mantı");

        if (hasDessert) {
            return "🍰 *Moral Menüsü:* Bugün tatlı var! Yoğun günün yorgunluğunu atmak için birebir.";
        }
        if (hasMeat && hasLegumes) {
            return "💪 *Yüksek Protein & Lif:* Sporcular ve kas kütlesini korumak isteyenler için harika bir menü!";
        }
        if (hasMeat) {
            return "🥩 *Protein Ağırlıklı:* Günün yorgunluğunu unutturan, tok tutan besleyici bir akşam yemeği.";
        }
        if (hasLegumes && hasCarb) {
            return "⚡ *Yüksek Enerji Deposu:* Kompleks karbonhidrat ve bitkisel protein dengesi yüksek, ders çalışırken zinde tutar.";
        }
        if (totalCal < 750) {
            return "🥗 *Hafif ve Dengeli:* Mideyi yormayan, sindirimi kolay ve formunu koruyanlar için ideal bir öğün.";
        }
        return "🥘 *Doyurucu Ev Yemeği:* Günlük kalori ihtiyacını karşılayan klasik ve lezzetli bir KYK menüsü.";
    }

    private static String safe(String s) {
        return s != null ? s : "";
    }
}

