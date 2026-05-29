package com.ibpms;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class PoliticaNegocioApplication {

	public static void main(String[] args) {
		SpringApplication.run(PoliticaNegocioApplication.class, args);
	}

}
