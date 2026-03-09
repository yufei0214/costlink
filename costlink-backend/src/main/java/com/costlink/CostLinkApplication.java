package com.costlink;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("com.costlink.mapper")
public class CostLinkApplication {
    public static void main(String[] args) {
        SpringApplication.run(CostLinkApplication.class, args);
    }
}
