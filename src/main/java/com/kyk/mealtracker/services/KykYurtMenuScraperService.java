package com.kyk.mealtracker.services;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kyk.mealtracker.entity.Meal;
import com.kyk.mealtracker.util.CalorieHelper;
import com.kyk.mealtracker.util.CityHelper;
import lombok.RequiredArgsConstructor;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class KykYurtMenuScraperService {

    private static final Logger logger = LoggerFactory.getLogger(KykYurtMenuScraperService.class);
    private static final String BASE_URL = "https://www.kykyurtmenu.com";
    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36 KykMealBot/2.0";
    private static final Locale TR_LOCALE = Locale.forLanguageTag("tr-TR");

    // Next.js RSC script içindeki tam öğün verisi (slash ile ayrılmış tam yemek isimleri ve gerçek kaloriler burada bulunur)
    private static final Pattern NEXT_ITEM_PATTERN = Pattern.compile(
            "\\\\\"date\\\\\":\\\\\"(\\d{4}-\\d{2}-\\d{2})\\\\\",\\\\\"mealType\\\\\":(\\d+),\\\\\"cityId\\\\\":(\\d+)," +
            "\\\\\"first\\\\\":\\\\\"(.*?)\\\\\",\\\\\"firstCalories\\\\\":\\\\\"(.*?)\\\\\"," +
            "\\\\\"second\\\\\":\\\\\"(.*?)\\\\\",\\\\\"secondCalories\\\\\":\\\\\"(.*?)\\\\\"," +
            "\\\\\"third\\\\\":\\\\\"(.*?)\\\\\",\\\\\"thirdCalories\\\\\":\\\\\"(.*?)\\\\\"," +
            "\\\\\"fourth\\\\\":\\\\\"(.*?)\\\\\",\\\\\"fourthCalories\\\\\":\\\\\"(.*?)\\\\\"," +
            "\\\\\"totalCalories\\\\\":(\\d+)"
    );

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Belirtilen şehir ve ay için aylık arşiv sayfasından tüm kahvaltı ve akşam yemeklerini çeker.
     * URL örneği: https://www.kykyurtmenu.com/adana/arsiv/kasim-2026
     */
    public List<Meal> scrapeMonthlyMenu(int cityId, YearMonth yearMonth) {
        CityHelper.CityInfo cityInfo = CityHelper.getByPlate(cityId)
                .orElseThrow(() -> new IllegalArgumentException("Geçersiz plaka kodu: " + cityId));

        String monthSlug = getTurkishMonthSlug(yearMonth.getMonthValue());
        String url = String.format("%s/%s/arsiv/%s-%d", BASE_URL, cityInfo.slug(), monthSlug, yearMonth.getYear());

        List<Meal> meals = new ArrayList<>();
        logger.info("[kykyurtmenu.com] Aylık menü çekiliyor: Şehir={}, URL={}", cityInfo.name(), url);

        try {
            Document doc = Jsoup.connect(url)
                    .userAgent(USER_AGENT)
                    .timeout(15000)
                    .get();

            Elements dateLinks = doc.select("a[href*=?date=]");
            logger.info("[kykyurtmenu.com] Bulunan gün bağlantısı adedi: {}", dateLinks.size());

            for (Element dateLink : dateLinks) {
                String href = dateLink.attr("href");
                LocalDate date = parseDateFromHref(href);
                if (date == null) continue;

                Element dayCard = findParentCard(dateLink);
                if (dayCard == null) continue;

                // Kahvaltı bloğu
                Element breakfastBlock = findMealSection(dayCard, "Kahvaltı");
                if (breakfastBlock != null) {
                    Meal breakfast = parseMealBlock(breakfastBlock, date, 0, cityId);
                    if (breakfast != null) meals.add(breakfast);
                }

                // Akşam yemeği bloğu
                Element dinnerBlock = findMealSection(dayCard, "Akşam");
                if (dinnerBlock != null) {
                    Meal dinner = parseMealBlock(dinnerBlock, date, 1, cityId);
                    if (dinner != null) meals.add(dinner);
                }
            }

            logger.info("[kykyurtmenu.com] Arşivden çekilen toplam öğün: {} (Şehir: {})", meals.size(), cityInfo.name());

        } catch (Exception e) {
            logger.error("[kykyurtmenu.com] Aylık menü çekme hatası (URL: {}): {}", url, e.getMessage());
        }

        // Eğer arşivden hiç çekilemediyse güncel detay sayfası dene
        if (meals.isEmpty()) {
            logger.info("[kykyurtmenu.com] Arşiv boş veya erişilemedi, güncel detay sayfasından çekim deneniyor...");
            scrapeDailyDetailedMeals(cityInfo, meals);
        }

        return meals;
    }

    /**
     * Sitenin ana şehir sayfasındaki (Next.js JSON payload) zengin kalori verilerini çeker.
     */
    public void scrapeDailyDetailedMeals(CityHelper.CityInfo cityInfo, List<Meal> targetList) {
        LocalDate today = LocalDate.now();
        Meal dinner = scrapeSingleDayMeal(cityInfo.plateCode(), today, 1);
        if (dinner != null) targetList.add(dinner);
        Meal breakfast = scrapeSingleDayMeal(cityInfo.plateCode(), today, 0);
        if (breakfast != null) targetList.add(breakfast);
    }

    /**
     * Belirtilen gün ve öğün için detay sayfasından TAM (kesilmemiş, "/" alternatifli ve gerçek kalorili) menüyü çeker.
     * ÖNEMLİ: Önce Next.js RSC script içindeki tam JSON'a bakar (çünkü LD+JSON ItemList "/" karakterinden sonrasını kesmektedir).
     */
    public Meal scrapeSingleDayMeal(int cityId, LocalDate date, int mealType) {
        CityHelper.CityInfo cityInfo = CityHelper.getByPlate(cityId).orElse(null);
        if (cityInfo == null || date == null) return null;

        String mealSlug = mealType == 0 ? "kahvalti" : "aksam";
        String url = String.format("%s/%s/%s?date=%s", BASE_URL, cityInfo.slug(), mealSlug, date);

        try {
            Document doc = Jsoup.connect(url).userAgent(USER_AGENT).timeout(10000).get();
            // 1. Öncelik: Next.js script içindeki tam ve kesilmemiş veri (tarih doğrulamasıyla birlikte!)
            Meal nextJsMeal = parseMealFromNextJsScript(doc.html(), date, mealType, cityId);
            if (nextJsMeal != null) {
                return nextJsMeal;
            }
        } catch (Exception e) {
            logger.warn("[kykyurtmenu.com] Tekil gün detay çekilemedi ({}): {}", url, e.getMessage());
        }
        return null;
    }

    private Meal parseMealFromNextJsScript(String html, LocalDate expectedDate, int mealType, int cityId) {
        Matcher m = NEXT_ITEM_PATTERN.matcher(html);
        while (m.find()) {
            try {
                LocalDate parsedDate = LocalDate.parse(m.group(1));
                int parsedMealType = Integer.parseInt(m.group(2));

                // Sitede o gün veri yoksa 2026-11-01'e (ayın ilk gününe) fallback yapıyor; tarih eşleşmiyorsa reddet!
                if (expectedDate != null && !parsedDate.equals(expectedDate)) {
                    continue;
                }
                if (parsedMealType != mealType) {
                    continue;
                }

                String f = fixTruncatedDishName(unescapeJson(m.group(4)));
                String fc = unescapeJson(m.group(5));
                String s = fixTruncatedDishName(unescapeJson(m.group(6)));
                String sc = unescapeJson(m.group(7));
                String t = fixTruncatedDishName(unescapeJson(m.group(8)));
                String tc = unescapeJson(m.group(9));
                String fo = fixTruncatedDishName(unescapeJson(m.group(10)));
                String foc = unescapeJson(m.group(11));
                String totalCal = m.group(12);

                return Meal.builder()
                        .date(parsedDate)
                        .mealType(mealType)
                        .cityId(cityId)
                        .first(f).firstCalories(fc)
                        .second(s).secondCalories(sc)
                        .third(t).thirdCalories(tc)
                        .fourth(fo).fourthCalories(foc)
                        .totalCalories(totalCal)
                        .build();
            } catch (Exception ignored) {}
        }
        return null;
    }

    private String unescapeJson(String input) {
        if (input == null) return null;
        return input.replace("\\/", "/").replace("\\\"", "\"").trim();
    }

    /**
     * Arşiv sayfasında "/" karakterinden bölündüğü için tek kelimede yarım kalan yemek isimlerini tamamlar.
     * Örn: "Meyveli" -> "Meyveli / Çikolatalı Yaş Pasta", "Zeytinli" -> "Zeytinli / Peynirli Açma", "Peynirli" -> "Peynirli / Ispanaklı Börek"
     */
    public static String fixTruncatedDishName(String name) {
        if (name == null || name.isBlank()) return name;
        String trimmed = name.trim();
        String lower = trimmed.toLowerCase(TR_LOCALE);

        return switch (lower) {
            case "meyveli" -> "Meyveli / Çikolatalı Yaş Pasta";
            case "çikolatalı" -> "Çikolatalı / Meyveli Yaş Pasta";
            case "zeytinli" -> "Zeytinli / Peynirli Açma";
            case "peynirli" -> "Peynirli / Ispanaklı Börek";
            case "kaşarlı" -> "Kaşarlı / Peynirli Börek";
            case "patatesli" -> "Patatesli / Peynirli Börek";
            case "ıspanaklı" -> "Ispanaklı / Peynirli Börek";
            case "kıymalı" -> "Kıymalı / Peynirli Börek";
            default -> trimmed;
        };
    }

    private Element findParentCard(Element element) {
        Element curr = element;
        for (int i = 0; i < 6; i++) {
            if (curr.parent() == null) break;
            curr = curr.parent();
            if (curr.select("ul").size() >= 1 || curr.text().contains("Kahvaltı") || curr.text().contains("Akşam")) {
                return curr;
            }
        }
        return element.parent();
    }

    private Element findMealSection(Element card, String keyword) {
        for (Element div : card.select("div")) {
            Element p = div.selectFirst("> p");
            if (p != null && p.text().toLowerCase(TR_LOCALE).contains(keyword.toLowerCase(TR_LOCALE))) {
                return div;
            }
        }
        return null;
    }

    private Meal parseMealBlock(Element section, LocalDate date, int mealType, int cityId) {
        Elements listItems = section.select("ul li");
        List<String> items = new ArrayList<>();

        for (Element li : listItems) {
            String text = li.text().trim();
            if (!text.isEmpty() && !text.equalsIgnoreCase("Veri yok")) {
                items.add(fixTruncatedDishName(text));
            }
        }

        if (items.isEmpty()) return null;

        Meal meal = Meal.builder()
                .date(date)
                .mealType(mealType)
                .cityId(cityId)
                .first(items.size() > 0 ? items.get(0) : null)
                .second(items.size() > 1 ? items.get(1) : null)
                .third(items.size() > 2 ? items.get(2) : null)
                .fourth(items.size() > 3 ? items.get(3) : null)
                .build();

        // Kalorileri belirle / hesapla
        int c1 = CalorieHelper.getOrEstimateCalories(meal.getFirst(), null);
        int c2 = CalorieHelper.getOrEstimateCalories(meal.getSecond(), null);
        int c3 = CalorieHelper.getOrEstimateCalories(meal.getThird(), null);
        int c4 = CalorieHelper.getOrEstimateCalories(meal.getFourth(), null);

        meal.setFirstCalories(c1 > 0 ? String.valueOf(c1) : null);
        meal.setSecondCalories(c2 > 0 ? String.valueOf(c2) : null);
        meal.setThirdCalories(c3 > 0 ? String.valueOf(c3) : null);
        meal.setFourthCalories(c4 > 0 ? String.valueOf(c4) : null);
        meal.setTotalCalories(String.valueOf(c1 + c2 + c3 + c4));

        return meal;
    }

    private LocalDate parseDateFromHref(String href) {
        try {
            int idx = href.indexOf("date=");
            if (idx != -1) {
                String dateStr = href.substring(idx + 5);
                if (dateStr.contains("&")) {
                    dateStr = dateStr.substring(0, dateStr.indexOf("&"));
                }
                return LocalDate.parse(dateStr.trim());
            }
        } catch (Exception ignored) {}
        return null;
    }

    private String getTurkishMonthSlug(int month) {
        return switch (month) {
            case 1 -> "ocak";
            case 2 -> "subat";
            case 3 -> "mart";
            case 4 -> "nisan";
            case 5 -> "mayis";
            case 6 -> "haziran";
            case 7 -> "temmuz";
            case 8 -> "agustos";
            case 9 -> "eylul";
            case 10 -> "ekim";
            case 11 -> "kasim";
            case 12 -> "aralik";
            default -> "kasim";
        };
    }
}
