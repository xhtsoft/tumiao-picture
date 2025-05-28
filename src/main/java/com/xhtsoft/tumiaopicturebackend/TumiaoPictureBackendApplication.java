package com.xhtsoft.tumiaopicturebackend;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("com.xhtsoft.tumiaopicturebackend.mapper")
public class TumiaoPictureBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(TumiaoPictureBackendApplication.class, args);
    }

}
