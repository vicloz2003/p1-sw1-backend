package com.ibpms;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

import java.util.TimeZone;

@SpringBootApplication
@EnableAsync
public class PoliticaNegocioApplication {

	public static void main(String[] args) {
		// Single-region deployment (Bolivia, UTC-4, no DST). Pin the JVM default zone
		// BEFORE Spring starts so every LocalDateTime.now() records Bolivia wall-clock
		// time regardless of the host OS zone — EC2 runs in UTC, which otherwise shifts
		// every stored/displayed timestamp +4h. Override via -Dapp.timezone=... or the
		// APP_TIMEZONE env var if ever deployed for another region.
		String zone = System.getProperty("app.timezone",
				System.getenv().getOrDefault("APP_TIMEZONE", "America/La_Paz"));
		TimeZone.setDefault(TimeZone.getTimeZone(zone));

		SpringApplication.run(PoliticaNegocioApplication.class, args);
	}

}
