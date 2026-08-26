package com.moyu.zeuspaymentagent;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
public class ZeusPaymentAgentApplication {

    public static void main(String[] args) {
        SpringApplication.run(ZeusPaymentAgentApplication.class, args);
    }

}
