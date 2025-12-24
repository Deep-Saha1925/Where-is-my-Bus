package com.deep.WIMB;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.web.client.RestTemplate;

@SpringBootApplication
public class WhereIsMyBusApplication {

	public static void main(String[] args) {
		SpringApplication.run(WhereIsMyBusApplication.class, args);
	}

	@Bean
	public RestTemplate restTemplate(){
		RestTemplate restTemplate = new RestTemplate();
		restTemplate.getInterceptors().add((req, body, exec) -> {
			req.getHeaders().add("User-Agent", "WhereIsMyBus-App");
			return exec.execute(req, body);
		});
		return restTemplate;
	}


}
