package com.ketangpai;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@SpringBootApplication
@EnableJpaAuditing
public class KetangpaiApplication {

    public static void main(String[] args) {
        SpringApplication.run(KetangpaiApplication.class, args);
    }
}
