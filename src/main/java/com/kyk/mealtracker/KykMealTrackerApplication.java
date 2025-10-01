package com.kyk.mealtracker;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.client.RestTemplate;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;
import com.kyk.mealtracker.bot.KykMealBot;
import org.springframework.context.annotation.Configuration;

@SpringBootApplication
@EnableScheduling
@Configuration
public class KykMealTrackerApplication {

    public static void main(String[] args) {
        SpringApplication.run(KykMealTrackerApplication.class, args);
    }

    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }

    @Bean
    public TelegramBotsApi telegramBotsApi(KykMealBot kykMealBot) throws TelegramApiException {
        TelegramBotsApi api = new TelegramBotsApi(DefaultBotSession.class);
        api.registerBot(kykMealBot);
        return api;
    }
}
