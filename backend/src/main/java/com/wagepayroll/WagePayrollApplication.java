package com.wagepayroll;

import com.wagepayroll.config.InvitationProperties;
import com.wagepayroll.config.MinioStorageProperties;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties({ InvitationProperties.class, MinioStorageProperties.class })
public class WagePayrollApplication {

	public static void main(String[] args) {
		SpringApplication.run(WagePayrollApplication.class, args);
	}
}
