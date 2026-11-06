package com.kyk.mealtracker.services;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kyk.mealtracker.entity.Meal;
import com.kyk.mealtracker.util.CalorieHelper;
import com.kyk.mealtracker.util.CityHelper;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * kykyemek.com üzerinden aylık 30 günlük tam menü ve detaylı kalori verilerini çeken servis.
 */
@Service
public class KykYemekComScraperService {

    private static final Logger logger = LoggerFactory.getLogger(KykYemekComScraperService.class);
    private static final String BASE_URL = "https://kykyemek.com/";
    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36";
    private static final Locale TR_LOCALE = Locale.forLanguageTag("tr-TR");
    private static final Pattern DATE_PATTERN = Pattern.compile("(\\d{1,2})\\s+([A-Za-zÇçĞğİıÖöŞşÜü]+)\\s+(\\d{4})");
    private static final Pattern KCAL_NUMBER_PATTERN = Pattern.compile("(\\d+(?:\\s*-\\s*\\d+)?)\\s*kcal", Pattern.CASE_INSENSITIVE);

    private final ObjectMapper objectMapper = new ObjectMapper();

    public List<Meal> scrapeMonthlyMenu(int cityId, YearMonth yearMonth) {
        CityHelper.CityInfo cityInfo = CityHelper.getByPlate(cityId).orElse(null);
        if (cityInfo == null) return List.of();

        List<Meal> meals = new ArrayList<>();
        // Kahvaltı (mealType = false -> 0) ve Akşam (mealType = true -> 1)
        meals.addAll(scrapeByMealType(cityInfo, yearMonth, false, 0));
        meals.addAll(scrapeByMealType(cityInfo, yearMonth, true, 1));

        if (!meals.isEmpty()) {
            logger.info("[kykyemek.com] {} için toplam {} öğün çekildi.", cityInfo.name(), meals.size());
        }
        return meals;
    }

    private List<Meal> scrapeByMealType(CityHelper.CityInfo cityInfo, YearMonth targetMonth, boolean isDinnerCookie, int mealType) {
        List<Meal> result = new ArrayList<>();
        String encodedCity = URLEncoder.encode(cityInfo.name(), StandardCharsets.UTF_8).replace("+", "%20");

        try {
            Document doc = Jsoup.connect(BASE_URL)
                    .userAgent(USER_AGENT)
                    .cookie("city", encodedCity)
                    .cookie("mealType", String.valueOf(isDinnerCookie))
                    .timeout(12000)
                    .get();

            // Sitenin gerçekten istenen şehri seçtiğini doğrula (desteklenmeyen şehirlerde İstanbul'a düşer)
            Element selectedOption = doc.selectFirst("select#navbarDropdown option[selected]");
            if (selectedOption == null || !isSameCityName(selectedOption.text(), cityInfo.name())) {
                return result;
            }

            Elements cards = doc.select("div.card.cardStyle");
            for (Element card : cards) {
                Element dateEl = card.selectFirst("p.cardDate");
                if (dateEl == null) continue;

                LocalDate date = parseTurkishDate(dateEl.text());
                if (date == null || !YearMonth.from(date).equals(targetMonth)) continue;

                Meal meal = parseCardToMeal(card, date, mealType, cityInfo.plateCode());
                if (meal != null) {
                    result.add(meal);
                }
            }
        } catch (Exception e) {
            logger.warn("[kykyemek.com] {} (öğün={}) çekilirken hata: {}", cityInfo.name(), mealType, e.getMessage());
        }
        return result;
    }

    private Meal parseCardToMeal(Element card, LocalDate date, int mealType, int cityId) {
        Element itemsContainer = card.selectFirst("div.card-body > div:first-child");
        if (itemsContainer == null) return null;

        Elements foodParas = itemsContainer.select("> p.food-main-text:not(.underline)");
        List<String> items = new ArrayList<>();

        for (Element p : foodParas) {
            String raw = p.text().trim();
            if (raw.isEmpty() || raw.toLowerCase(TR_LOCALE).contains("al götür")) continue;
            String normalized = raw
                    .replaceAll("\\s*/\\s*", " / ")
                    .replaceAll("\\s*\\+\\s*", " + ")
                    .trim();
            items.add(normalized);
        }

        if (items.isEmpty()) return null;

        String first = items.size() > 0 ? items.get(0) : null;
        String second = items.size() > 1 ? items.get(1) : null;
        String third = items.size() > 2 ? items.get(2) : null;
        String fourth = null;
        if (items.size() == 4) {
            fourth = items.get(3);
        } else if (items.size() >= 5) {
            fourth = items.get(3) + " & " + items.get(4);
        }

        // Kalori hesaplama: Önce data-foods JSON'una bak, yoksa metinden / CalorieHelper'dan tahmin et
        int[] courseCals = new int[4];
        Element calBtn = card.selectFirst("button.calorieBtn[data-foods]");
        if (calBtn != null) {
            parseDataFoodsJson(calBtn.attr("data-foods"), courseCals);
        }

        int c1 = courseCals[0] > 0 ? courseCals[0] : CalorieHelper.getOrEstimateCalories(first, null);
        int c2 = courseCals[1] > 0 ? courseCals[1] : CalorieHelper.getOrEstimateCalories(second, null);
        int c3 = courseCals[2] > 0 ? courseCals[2] : CalorieHelper.getOrEstimateCalories(third, null);
        int c4 = courseCals[3] > 0 ? courseCals[3] : CalorieHelper.getOrEstimateCalories(fourth, null);

        int sumCal = c1 + c2 + c3 + c4;
        String totalCaloriesStr = String.valueOf(sumCal);

        // Eğer kartta açıkça kcal yazıyorsa ve toplam 0 ise oradan al
        Element kcalTextEl = card.selectFirst("button.calorieBtn, p.text-end.food-main-text");
        if (kcalTextEl != null) {
            Matcher m = KCAL_NUMBER_PATTERN.matcher(kcalTextEl.text());
            if (m.find() && sumCal == 0) {
                totalCaloriesStr = m.group(1).replaceAll("\\s+", "");
            }
        }

        return Meal.builder()
                .date(date)
                .mealType(mealType)
                .cityId(cityId)
                .first(first)
                .firstCalories(c1 > 0 ? String.valueOf(c1) : null)
                .second(second)
                .secondCalories(c2 > 0 ? String.valueOf(c2) : null)
                .third(third)
                .thirdCalories(c3 > 0 ? String.valueOf(c3) : null)
                .fourth(fourth)
                .fourthCalories(c4 > 0 ? String.valueOf(c4) : null)
                .totalCalories(totalCaloriesStr)
                .build();
    }

    private void parseDataFoodsJson(String json, int[] courseCals) {
        if (json == null || json.isBlank()) return;
        try {
            JsonNode arr = objectMapper.readTree(json);
            if (!arr.isArray()) return;
            boolean[] groupSeen = new boolean[4];
            for (JsonNode node : arr) {
                int cal = node.has("calori") ? node.get("calori").asInt(0) : 0;
                if (cal <= 0) continue;
                if (node.has("group") && !node.get("group").isNull()) {
                    int grp = node.get("group").asInt(-1);
                    if (grp >= 0 && grp < 4 && !groupSeen[grp]) {
                        courseCals[grp] = cal;
                        groupSeen[grp] = true;
                    }
                } else if (courseCals[3] == 0) {
                    courseCals[3] = cal;
                }
            }
        } catch (Exception ignored) {}
    }

    private LocalDate parseTurkishDate(String text) {
        if (text == null) return null;
        Matcher m = DATE_PATTERN.matcher(text.trim());
        if (m.find()) {
            try {
                int day = Integer.parseInt(m.group(1));
                int month = parseTurkishMonth(m.group(2));
                int year = Integer.parseInt(m.group(3));
                if (month >= 1 && month <= 12) {
                    return LocalDate.of(year, month, day);
                }
            } catch (Exception ignored) {}
        }
        return null;
    }

    private int parseTurkishMonth(String monthName) {
        String lower = monthName.toLowerCase(TR_LOCALE).trim();
        return switch (lower) {
            case "ocak" -> 1;
            case "şubat", "subat" -> 2;
            case "mart" -> 3;
            case "nisan" -> 4;
            case "mayıs", "mayis" -> 5;
            case "haziran" -> 6;
            case "temmuz" -> 7;
            case "ağustos", "agustos" -> 8;
            case "eylül", "eylul" -> 9;
            case "ekim" -> 10;
            case "kasım", "kasim" -> 11;
            case "aralık", "aralik" -> 12;
            default -> -1;
        };
    }

    private boolean isSameCityName(String a, String b) {
        if (a == null || b == null) return false;
        return a.trim().toLowerCase(TR_LOCALE).equals(b.trim().toLowerCase(TR_LOCALE));
    }
}

