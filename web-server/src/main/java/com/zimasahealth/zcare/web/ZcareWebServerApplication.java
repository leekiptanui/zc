package com.zimasahealth.zcare.web;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/** No default in-memory user: users sign in only through {@code /bff/login}. */
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
@ConfigurationPropertiesScan
public class ZcareWebServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(ZcareWebServerApplication.class, args);
    }
}
