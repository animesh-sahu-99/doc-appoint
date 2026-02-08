package com.clinic.doc_appointment;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.retry.annotation.EnableRetry;

@SpringBootApplication
@EnableRetry
public class DocAppointmentApplication {

	public static void main(String[] args) {
		SpringApplication.run(DocAppointmentApplication.class, args);
	}

}
