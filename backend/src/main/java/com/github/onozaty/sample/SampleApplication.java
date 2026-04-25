package com.github.onozaty.sample;

import com.github.onozaty.sample.config.CookieProperties;
import com.github.onozaty.sample.config.JwtProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.support.SpringBootServletInitializer;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableConfigurationProperties({JwtProperties.class, CookieProperties.class})
@EnableScheduling
public class SampleApplication extends SpringBootServletInitializer {

  public static void main(String[] args) {
    SpringApplication.run(SampleApplication.class, args);
  }

  @Override
  protected SpringApplicationBuilder configure(SpringApplicationBuilder builder) {
    return builder.sources(SampleApplication.class);
  }
}
