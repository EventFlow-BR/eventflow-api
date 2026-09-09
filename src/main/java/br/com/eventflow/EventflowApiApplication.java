package br.com.eventflow;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class EventflowApiApplication {

	public static void main(String[] args) {
		SpringApplication.run(EventflowApiApplication.class, args);
	}

}
