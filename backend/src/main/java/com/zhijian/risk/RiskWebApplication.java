package com.zhijian.risk;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 智鉴风控官 Web 启动入口。
 */
@SpringBootApplication
@MapperScan("com.zhijian.risk.mapper")
public class RiskWebApplication {
    private static final Logger log = LoggerFactory.getLogger(RiskWebApplication.class);

    /**
     * 应用启动入口。
     */
    public static void main(String[] args) {
        log.info("main start");

        SpringApplication.run(RiskWebApplication.class, args);
    }
}
