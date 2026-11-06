package com.kyk.mealtracker.services;

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

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * yurtmenu.net üzerinden 81 il için tarih bazlı yedek menü çeken servis.
 */
@Service
public class YurtMenuNetScraperService {

    private static final Logger logger = LoggerFactory.getLogger(YurtMenuNetScraperService.class);
    private static final String BASE_URL = "https://yurtmenu.net";
    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36";
    private static final Locale TR_LOCALE = Locale.forLanguageTag("tr-TR");

    /**
     * Belirtilen şehir ve tarih için kahvaltı (0) ve akşam yemeği (1) öğünlerini çeker.
     */
    public List<Meal> scrapeDayMeals(int cityId, LocalDate date) {
        CityHelper.CityInfo cityInfo = CityHelper.getByPlate(cityId).orElse(null);
        if (cityInfo == null || date == null) return List.of();

        String url = String.format("%s/%s?date=%s", BASE_URL, cityInfo.slug(), date);
        List<Meal> meals = new ArrayList<>();

        try {
            Document doc = Jsoup.connect(url)
                    .userAgent(USER_AGENT)
                    .timeout(10000)
                    .get();

            if (doc.selectFirst("div.data-availability-panel") != null) {
                return meals;
            }

            Element bfCard = doc.selectFirst("article#breakfast-card");
            if (bfCard != null) {
                Meal bf = parseMenuCard(bfCard, "ul#breakfast-menu li.menu-item .menu-item-name", date, 0, cityId);
                if (bf != null) meals.add(bf);
            }

            Element dnCard = doc.selectFirst("article#dinner-card");
            if (dnCard != null) {
                Meal dn = parseMenuCard(dnCard, "ul#dinner-menu li.menu-item .menu-item-name", date, 1, cityId);
                if (dn != null) meals.add(dn);
            }

            if (!meals.isEmpty()) {
                logger.info("[yurtmenu.net] {} ({}) için {} öğün kurtarıldı.", cityInfo.name(), date, meals.size());
            }
        } catch (Exception e) {
            logger.warn("[yurtmenu.net] {} ({}) çekilemedi: {}", cityInfo.name(), date, e.getMessage());
        }

        return meals;
    }

    private Meal parseMenuCard(Element card, String itemSelector, LocalDate date, int mealType, int cityId) {
        Elements itemEls = card.select(itemSelector);
        List<String> items = new ArrayList<>();

        for (Element el : itemEls) {
            String text = el.text().trim();
            if (text.isEmpty() || isStandardWaterOrBread(text)) continue;
            items.add(text);
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

        int c1 = CalorieHelper.getOrEstimateCalories(first, null);
        int c2 = CalorieHelper.getOrEstimateCalories(second, null);
        int c3 = CalorieHelper.getOrEstimateCalories(third, null);
        int c4 = CalorieHelper.getOrEstimateCalories(fourth, null);
        int sumCal = c1 + c2 + c3 + c4;

        String totalCalStr = String.valueOf(sumCal);
        Element metaCal = card.selectFirst(".menu-card-meta");
        if (metaCal != null) {
            Integer parsedMeta = CalorieHelper.parseCalorieNumber(metaCal.text());
            if (parsedMeta != null && parsedMeta > 0) {
                totalCalStr = String.valueOf(parsedMeta);
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
                .totalCalories(totalCalStr)
                .build();
    }

    private boolean isStandardWaterOrBread(String text) {
        String lower = text.toLowerCase(TR_LOCALE).trim();
        return lower.equals("500 ml su")
                || lower.equals("200 ml bardak su")
                || lower.equals("su")
                || lower.equals("çeyrek ekmek")
                || lower.equals("ekmek")
                || lower.contains("veri yok")
                || lower.contains("bulunamadı");
    }
}

