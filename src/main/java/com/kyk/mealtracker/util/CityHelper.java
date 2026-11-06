package com.kyk.mealtracker.util;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

public final class CityHelper {

    private static final Map<Integer, CityInfo> CITIES_BY_PLATE = new LinkedHashMap<>();
    private static final Map<String, CityInfo> CITIES_BY_SLUG = new LinkedHashMap<>();

    public record CityInfo(int plateCode, String name, String slug) {}

    static {
        register(1, "Adana", "adana");
        register(2, "Adıyaman", "adiyaman");
        register(3, "Afyonkarahisar", "afyonkarahisar");
        register(4, "Ağrı", "agri");
        register(5, "Amasya", "amasya");
        register(6, "Ankara", "ankara");
        register(7, "Antalya", "antalya");
        register(8, "Artvin", "artvin");
        register(9, "Aydın", "aydin");
        register(10, "Balıkesir", "balikesir");
        register(11, "Bilecik", "bilecik");
        register(12, "Bingöl", "bingol");
        register(13, "Bitlis", "bitlis");
        register(14, "Bolu", "bolu");
        register(15, "Burdur", "burdur");
        register(16, "Bursa", "bursa");
        register(17, "Çanakkale", "canakkale");
        register(18, "Çankırı", "cankiri");
        register(19, "Çorum", "corum");
        register(20, "Denizli", "denizli");
        register(21, "Diyarbakır", "diyarbakir");
        register(22, "Edirne", "edirne");
        register(23, "Elazığ", "elazig");
        register(24, "Erzincan", "erzincan");
        register(25, "Erzurum", "erzurum");
        register(26, "Eskişehir", "eskisehir");
        register(27, "Gaziantep", "gaziantep");
        register(28, "Giresun", "giresun");
        register(29, "Gümüşhane", "gumushane");
        register(30, "Hakkari", "hakkari");
        register(31, "Hatay", "hatay");
        register(32, "Isparta", "isparta");
        register(33, "Mersin", "mersin");
        register(34, "İstanbul", "istanbul");
        register(35, "İzmir", "izmir");
        register(36, "Kars", "kars");
        register(37, "Kastamonu", "kastamonu");
        register(38, "Kayseri", "kayseri");
        register(39, "Kırklareli", "kirklareli");
        register(40, "Kırşehir", "kirsehir");
        register(41, "Kocaeli", "kocaeli");
        register(42, "Konya", "konya");
        register(43, "Kütahya", "kutahya");
        register(44, "Malatya", "malatya");
        register(45, "Manisa", "manisa");
        register(46, "Kahramanmaraş", "kahramanmaras");
        register(47, "Mardin", "mardin");
        register(48, "Muğla", "mugla");
        register(49, "Muş", "mus");
        register(50, "Nevşehir", "nevsehir");
        register(51, "Niğde", "nigde");
        register(52, "Ordu", "ordu");
        register(53, "Rize", "rize");
        register(54, "Sakarya", "sakarya");
        register(55, "Samsun", "samsun");
        register(56, "Siirt", "siirt");
        register(57, "Sinop", "sinop");
        register(58, "Sivas", "sivas");
        register(59, "Tekirdağ", "tekirdag");
        register(60, "Tokat", "tokat");
        register(61, "Trabzon", "trabzon");
        register(62, "Tunceli", "tunceli");
        register(63, "Şanlıurfa", "sanliurfa");
        register(64, "Uşak", "usak");
        register(65, "Van", "van");
        register(66, "Yozgat", "yozgat");
        register(67, "Zonguldak", "zonguldak");
        register(68, "Aksaray", "aksaray");
        register(69, "Bayburt", "bayburt");
        register(70, "Karaman", "karaman");
        register(71, "Kırıkkale", "kirikkale");
        register(72, "Batman", "batman");
        register(73, "Şırnak", "sirnak");
        register(74, "Bartın", "bartin");
        register(75, "Ardahan", "ardahan");
        register(76, "Iğdır", "igdir");
        register(77, "Yalova", "yalova");
        register(78, "Karabük", "karabuk");
        register(79, "Kilis", "kilis");
        register(80, "Osmaniye", "osmaniye");
        register(81, "Düzce", "duzce");
    }

    private static void register(int plate, String name, String slug) {
        CityInfo info = new CityInfo(plate, name, slug);
        CITIES_BY_PLATE.put(plate, info);
        CITIES_BY_SLUG.put(slug, info);
    }

    private CityHelper() {}

    public static Optional<CityInfo> getByPlate(Integer plate) {
        if (plate == null) return Optional.empty();
        return Optional.ofNullable(CITIES_BY_PLATE.get(plate));
    }

    public static Optional<CityInfo> getBySlug(String slug) {
        if (slug == null) return Optional.empty();
        return Optional.ofNullable(CITIES_BY_SLUG.get(slug.toLowerCase().trim()));
    }

    public static String getCityName(Integer plate) {
        return getByPlate(plate).map(CityInfo::name).orElse("Bilinmeyen Şehir");
    }

    public static Map<Integer, CityInfo> getAllCities() {
        return Collections.unmodifiableMap(CITIES_BY_PLATE);
    }
}

