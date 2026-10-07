package com.zimasahealth.zcare;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/** Entry point of zimasa-zcare-service; the only class in the base package (PKG-02). */
@SpringBootApplication
@ConfigurationPropertiesScan
public class ZimasaZcareApplication {

    public static void main(String[] args) {
        SpringApplication.run(ZimasaZcareApplication.class, args);
    }
}
