# 🍽️ KYK Yemek Menüsü Botu

KYK yurtlarındaki günlük yemek menülerini Telegram üzerinden takip edebileceğiniz bot.

## 🚀 Özellikler

- 📅 Günlük ve haftalık menü görüntüleme
- 🔔 Otomatik günlük menü bildirimleri (Sabah 07:00)
- 📊 Kalori hesaplaması
- 👥 Kullanıcı yönetimi
- 🔧 Kapsamlı admin paneli

## 🛠️ Teknolojiler

- **Backend:** Java 17, Spring Boot
- **Database:** PostgreSQL
- **Bot Framework:** Telegram Bot API
- **Build Tool:** Maven

## 📦 Yerel Kurulum

### Gereksinimler

- Java 17+
- PostgreSQL 13+
- Maven 3.6+

### Kurulum Adımları

1. **Projeyi klonlayın:**
```bash
git clone https://github.com/kullaniciadi/kyk-meal-bot.git
cd kyk-meal-bot
```

2. **PostgreSQL database oluşturun:**
```sql
CREATE DATABASE kyk_meals;
```

3. **Environment variables ayarlayın:**

```properties
DB_URL=jdbc:postgresql://localhost:5432/kyk_meals
DB_USERNAME=postgres
DB_PASSWORD=your_password
TELEGRAM_BOT_USERNAME=YourBotUsername
TELEGRAM_BOT_TOKEN=your_bot_token
```

4. **Uygulamayı çalıştırın:**
```bash
mvn spring-boot:run
```

## ☁️ Coolify Deployment

### 1. GitHub'a Push

```bash
git add .
git commit -m "Initial commit"
git push origin main
```

### 2. Coolify'da Yeni Proje Oluşturun

1. **New Resource** → **Application**
2. **Git Repository** seçin
3. GitHub repository'nizi bağlayın
4. **Build Pack:** Java 17 + Maven

### 3. PostgreSQL Database Ekleyin

1. **New Resource** → **Database** → **PostgreSQL**
2. Database name: `kyk_meals`
3. Username ve password ayarlayın
4. Connection string'i kopyalayın

### 4. Environment Variables Ekleyin

Coolify'da **Environment** sekmesine şunları ekleyin:

```bash
DB_URL=jdbc:postgresql://postgres-service:5432/kyk_meals
DB_USERNAME=your_db_user
DB_PASSWORD=your_db_password
TELEGRAM_BOT_USERNAME=YourBotUsername
TELEGRAM_BOT_TOKEN=1234567890:ABCdefGHIjklMNOpqrsTUVwxyz
JPA_DDL_AUTO=update
JPA_SHOW_SQL=false
LOG_LEVEL=INFO
PORT=8080
```

### 5. Deploy

**Deploy** butonuna basın! 🚀

## 🤖 Bot Komutları

### 👤 Kullanıcı Komutları

| Komut | Açıklama |
|-------|----------|
| `/start` | Botu başlat |
| `/bugun` | Bugünkü menüyü göster |
| `/yarin` | Yarınki menüyü göster |
| `/bildirim_ac` | Günlük bildirimleri aktif et |
| `/bildirim_kapat` | Bildirimleri kapat |
| `/yardim` | Komut listesini göster |

### 🔧 Admin Komutları

| Komut | Açıklama |
|-------|----------|
| `/admin_list [sayfa]` | Kullanıcı listesini göster (Chat ID'ler ile) |
| `/admin_add [chatId]` | Kullanıcıya admin yetkisi ver |
| `/admin_remove [chatId]` | Kullanıcıdan admin yetkisini al |
| `/admin_broadcast [mesaj]` | Tüm kullanıcılara mesaj gönder |
| `/admin_stats` | Detaylı bot istatistikleri |

## 👨‍💼 İlk Admin Olmak

Database'e bağlanıp kendinizi admin yapın:

```sql
-- Önce botu kullanın ve /start yazın
-- Sonra database'de kendinizi admin yapın:

UPDATE bot_users 
SET is_admin = true 
WHERE chat_id = YOUR_CHAT_ID;
```

Chat ID'nizi öğrenmek için `/admin_list` komutunu kullanın (başka bir adminse) veya database'e bakın.

## 📊 Database Şeması

### bot_users
```sql
- chat_id (PK)
- username
- first_name
- last_name
- notifications_enabled
- is_admin
- last_interaction_date
- last_activity_date
```

### meals
```sql
- id (PK)
- date
- meal_type (0: Kahvaltı, 1: Akşam Yemeği)
- first (1. Öğe)
- second (2. Öğe)
- third (3. Öğe)
- fourth (4. Öğe)
- first_calories
- second_calories
- third_calories
- fourth_calories
- total_calories
```

## 🔒 Güvenlik Notları

- ⚠️ **Asla** gerçek token/şifrelerinizi GitHub'a yüklemeyin
- ✅ Environment variables kullanın
- ✅ `.gitignore` dosyasını kontrol edin
- ✅ Production'da güçlü şifreler kullanın
- ✅ Database bağlantılarını SSL ile şifreleyin

## 🐛 Sorun Giderme

### Bot yanıt vermiyor
- Logları kontrol edin
- Database bağlantısını test edin
- Environment variables'ları kontrol edin

### Database bağlantı hatası
- `DB_URL` environment variable'ını kontrol edin
- PostgreSQL servisinin çalıştığından emin olun
- Firewall ayarlarını kontrol edin

### Telegram bot çalışmıyor
- Bot token'ı doğrulayın
- BotFather'da bot ayarlarını kontrol edin
- Network bağlantısını test edin

## 📝 Lisans

MIT License - Detaylar için [LICENSE](LICENSE) dosyasına bakın.

## 🤝 Katkıda Bulunma

1. Fork yapın
2. Feature branch oluşturun (`git checkout -b feature/yeni-ozellik`)
3. Commit yapın (`git commit -am 'Yeni özellik eklendi'`)
4. Push yapın (`git push origin feature/yeni-ozellik`)
5. Pull Request açın

## 📧 İletişim

Sorularınız için [GitHub Issues](https://github.com/kullaniciadi/kyk-meal-bot/issues) kullanabilirsiniz.

---

⭐ Projeyi beğendiyseniz yıldız vermeyi unutmayın!
