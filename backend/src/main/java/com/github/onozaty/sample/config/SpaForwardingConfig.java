package com.github.onozaty.sample.config;

import static org.springframework.web.servlet.function.RequestPredicates.path;
import static org.springframework.web.servlet.function.RouterFunctions.route;

import org.springframework.boot.autoconfigure.condition.ConditionalOnResource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.web.servlet.function.RequestPredicate;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.ServerResponse;

@Configuration
public class SpaForwardingConfig {

  // bootRun などフロントエンド未ビルド時は static/index.html が存在しないため SPA ルーティングを登録しない
  @Bean
  @ConditionalOnResource(resources = "classpath:static/index.html")
  RouterFunction<ServerResponse> spaRouter() {
    var index = new ClassPathResource("static/index.html");
    var staticResources = new ClassPathResource("static/");
    RequestPredicate spaPredicate =
        path("/api/**") // REST API
            .or(path("/error")) // Spring のエラーハンドリング
            .or(path("/swagger-ui.html")) // springdoc: SwaggerWelcomeWebMvc (リダイレクト元)
            .or(path("/swagger-ui/**")) // springdoc: SwaggerWelcomeWebMvc (リダイレクト先)
            .or(path("/v3/api-docs/**")) // springdoc: AbstractOpenApiResource
            .or(path("/webjars/**")) // springdoc: Swagger UI の静的リソース (SwaggerWebMvcConfigurer)
            .negate();
    return route().resources("/**", staticResources).resource(spaPredicate, index).build();
  }
}
