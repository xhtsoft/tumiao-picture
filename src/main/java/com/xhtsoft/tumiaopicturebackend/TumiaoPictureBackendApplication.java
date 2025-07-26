package com.xhtsoft.tumiaopicturebackend;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
@MapperScan("com.xhtsoft.tumiaopicturebackend.mapper")
@EnableAspectJAutoProxy(exposeProxy = true)
public class TumiaoPictureBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(TumiaoPictureBackendApplication.class, args);
    }

}
