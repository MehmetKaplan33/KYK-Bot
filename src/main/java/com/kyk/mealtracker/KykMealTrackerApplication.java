package com.kyk.mealtracker;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;
import com.kyk.mealtracker.bot.KykMealBot;
import org.springframework.context.annotation.Configuration;

@SpringBootApplication
@EnableScheduling
@EnableAsync
@Configuration
public class KykMealTrackerApplication {

    public static void main(String[] args) {
        SpringApplication.run(KykMealTrackerApplication.class, args);
    }

    @Bean
    public TelegramBotsApi telegramBotsApi(KykMealBot kykMealBot) throws TelegramApiException {
        TelegramBotsApi api = new TelegramBotsApi(DefaultBotSession.class);
        api.registerBot(kykMealBot);
        return api;
    }
}
