package com.deep.WIMB;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class WhereIsMyBusApplication {

	public static void main(String[] args) {
		SpringApplication.run(WhereIsMyBusApplication.class, args);
	}
}
