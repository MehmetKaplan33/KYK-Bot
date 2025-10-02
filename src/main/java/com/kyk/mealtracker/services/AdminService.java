package com.kyk.mealtracker.services;

import com.kyk.mealtracker.entity.BotUser;
import com.kyk.mealtracker.repository.BotUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AdminService {

    private final BotUserRepository botUserRepository;

    public boolean isAdmin(Long chatId) {
        return botUserRepository.findById(chatId)
                .map(user -> user.getIsAdmin() != null && user.getIsAdmin())
                .orElse(false);
    }

    public String getBotStats() {
        long totalUsers = botUserRepository.count();
        long activeNotifications = botUserRepository.countByNotificationsEnabledTrue();
        long last24HoursActive = botUserRepository.countByLastActivityDateAfter(LocalDateTime.now().minusHours(24));
        long last24HoursNew = botUserRepository.countByLastInteractionDateAfter(LocalDateTime.now().minusHours(24));

        return String.format("""
                📊 Bot İstatistikleri:

                👥 Toplam Kullanıcı: %d
                🔔 Bildirim Alan Kullanıcı: %d
                🔕 Bildirimi Kapalı Kullanıcı: %d

                📅 Son 24 Saat:
                - Aktif Kullanıcı: %d
                - Yeni Kayıt: %d
                """,
                totalUsers,
                activeNotifications,
                totalUsers - activeNotifications,
                last24HoursActive,
                last24HoursNew);
    }

    public String getUserList(int page) {
        Page<BotUser> users = botUserRepository.findAll(PageRequest.of(page, 10));
        StringBuilder message = new StringBuilder("👥 Kullanıcı Listesi:\n\n");

        users.getContent().forEach(user -> {
            String adminBadge = (user.getIsAdmin() != null && user.getIsAdmin()) ? "🔧 " : "";
            message.append(String.format("%s%s %s (@%s)\n📱 Chat ID: %d\n🔔 Bildirim: %s\n\n",
                    adminBadge,
                    user.getFirstName(),
                    user.getLastName() != null ? user.getLastName() : "",
                    user.getUsername() != null ? user.getUsername() : "isimsiz",
                    user.getChatId(),
                    user.isNotificationsEnabled() ? "Açık ✅" : "Kapalı ❌"));
        });

        message.append(String.format("Sayfa %d/%d", page + 1, users.getTotalPages()));
        return message.toString();
    }

    @Transactional
    public void broadcastMessage(String message) {
        List<BotUser> allUsers = botUserRepository.findAll();
        // Bu liste KykMealBot'a gönderilecek
    }
}
