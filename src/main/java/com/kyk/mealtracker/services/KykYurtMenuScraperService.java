package com.kyk.mealtracker.services;

import com.kyk.mealtracker.entity.Meal;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class KykYurtMenuScraperService {

    private static final Logger logger = LoggerFactory.getLogger(KykYurtMenuScraperService.class);
    private static final String BASE_URL = "https://www.kykyurtmenu.com";
    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36 KykMealBot/2.0";

    // Kalori yakalama desenleri: "118 / 227 kcal", "348 kcal" veya JSON içindeki "totalCalories":1000
    private static final Pattern NEXT_DATA_PATTERN = Pattern.compile("item\":\\{(.*?\"totalCalories\":\\s*(\\d+).*?)\\}");
    private static final Pattern CALORIE_LABEL_PATTERN = Pattern.compile("(\\d+(?:\\s*/\\s*\\d+)?)\\s*(?:kcal|kalori)", Pattern.CASE_INSENSITIVE);

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
        logger.info("Aylık menü çekiliyor: Şehir={}, URL={}", cityInfo.name(), url);

        try {
            Document doc = Jsoup.connect(url)
                    .userAgent(USER_AGENT)
                    .timeout(15000)
                    .get();

            Elements dateLinks = doc.select("a[href*=?date=]");
            logger.info("Bulunan gün bağlantısı adedi: {}", dateLinks.size());

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

            logger.info("Arşivden çekilen toplam öğün: {} (Şehir: {})", meals.size(), cityInfo.name());

        } catch (Exception e) {
            logger.error("Aylık menü çekme hatası (URL: {}): {}", url, e.getMessage());
        }

        // Eğer arşivden hiç çekilemediyse veya güncel detay sayfası denenmek istenirse
        if (meals.isEmpty()) {
            logger.info("Arşiv boş veya erişilemedi, güncel detay sayfasından çekim deneniyor...");
            scrapeDailyDetailedMeals(cityInfo, meals);
        }

        return meals;
    }

    /**
     * Sitenin ana şehir sayfasındaki (Next.js JSON payload) zengin kalori verilerini çeker.
     * URL: https://www.kykyurtmenu.com/adana ve /adana/kahvalti
     */
    public void scrapeDailyDetailedMeals(CityHelper.CityInfo cityInfo, List<Meal> targetList) {
        scrapeSinglePageJson(cityInfo.slug(), "aksam", 1, cityInfo.plateCode(), targetList);
        scrapeSinglePageJson(cityInfo.slug(), "kahvalti", 0, cityInfo.plateCode(), targetList);
    }

    public Meal scrapeSingleDayMeal(int cityId, LocalDate date, int mealType) {
        CityHelper.CityInfo cityInfo = CityHelper.getByPlate(cityId).orElse(null);
        if (cityInfo == null) return null;

        String mealSlug = mealType == 0 ? "kahvalti" : "aksam";
        String url = String.format("%s/%s/%s?date=%s", BASE_URL, cityInfo.slug(), mealSlug, date);

        try {
            Document doc = Jsoup.connect(url).userAgent(USER_AGENT).timeout(10000).get();
            String html = doc.html();
            return parseMealFromNextJsScript(html, date, mealType, cityId);
        } catch (Exception e) {
            logger.warn("Tekil gün detay çekilemedi: {}", e.getMessage());
            return null;
        }
    }

    private void scrapeSinglePageJson(String citySlug, String mealSlug, int mealType, int cityId, List<Meal> targetList) {
        String url = String.format("%s/%s/%s", BASE_URL, citySlug, mealSlug);
        try {
            Document doc = Jsoup.connect(url).userAgent(USER_AGENT).timeout(10000).get();
            String html = doc.html();
            Meal meal = parseMealFromNextJsScript(html, LocalDate.now(), mealType, cityId);
            if (meal != null) {
                targetList.add(meal);
            }
        } catch (Exception e) {
            logger.warn("Tekil sayfa çekilemedi ({}): {}", url, e.getMessage());
        }
    }

    private Meal parseMealFromNextJsScript(String html, LocalDate fallbackDate, int mealType, int cityId) {
        // Next.js script içinde: \"first\":\"...\",\"firstCalories\":\"118 / 227\",...,\"totalCalories\":1000
        Pattern p = Pattern.compile("\\\\\"first\\\\\":\\\\\"(.*?)\\\\\".*?\\\\\"firstCalories\\\\\":\\\\\"(.*?)\\\\\".*?\\\\\"second\\\\\":\\\\\"(.*?)\\\\\".*?\\\\\"secondCalories\\\\\":\\\\\"(.*?)\\\\\".*?\\\\\"third\\\\\":\\\\\"(.*?)\\\\\".*?\\\\\"thirdCalories\\\\\":\\\\\"(.*?)\\\\\".*?\\\\\"fourth\\\\\":\\\\\"(.*?)\\\\\".*?\\\\\"fourthCalories\\\\\":\\\\\"(.*?)\\\\\".*?\\\\\"totalCalories\\\\\":(\\d+)");
        Matcher m = p.matcher(html);
        if (m.find()) {
            return Meal.builder()
                    .date(fallbackDate)
                    .mealType(mealType)
                    .cityId(cityId)
                    .first(unescapeJson(m.group(1)))
                    .firstCalories(unescapeJson(m.group(2)))
                    .second(unescapeJson(m.group(3)))
                    .secondCalories(unescapeJson(m.group(4)))
                    .third(unescapeJson(m.group(5)))
                    .thirdCalories(unescapeJson(m.group(6)))
                    .fourth(unescapeJson(m.group(7)))
                    .fourthCalories(unescapeJson(m.group(8)))
                    .totalCalories(m.group(9))
                    .build();
        }
        return null;
    }

    private String unescapeJson(String input) {
        if (input == null) return null;
        return input.replace("\\/", "/").replace("\\\"", "\"").trim();
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
            Element p = div.selectFirst("p");
            if (p != null && p.text().toLowerCase().contains(keyword.toLowerCase())) {
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
                items.add(text);
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

        // Eğer başlıkta veya öğelerde kalori varsa çek
        Matcher matcher = CALORIE_LABEL_PATTERN.matcher(section.text());
        if (matcher.find()) {
            meal.setTotalCalories(matcher.group(1));
        }

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
