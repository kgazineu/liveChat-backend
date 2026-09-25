package com.example.liveChat;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class LiveChatApplication {

	public static void main(String[] args) {
		SpringApplication.run(LiveChatApplication.class, args);
	}

}
