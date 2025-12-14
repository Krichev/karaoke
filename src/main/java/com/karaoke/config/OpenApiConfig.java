package com.karaoke.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.servers.Server;
import io.swagger.v3.oas.models.tags.Tag;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI karaokeOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Karaoke Rating API")
                        .description("Professional karaoke performance rating system with pitch accuracy, rhythm analysis, and voice similarity scoring")
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("API Support")
                                .email("support@karaokeapi.com"))
                        .license(new License()
                                .name("Apache 2.0")
                                .url("https://www.apache.org/licenses/LICENSE-2.0.html")))
                .servers(List.of(
                        new Server()
                                .url("http://localhost:8080")
                                .description("Development Server"),
                        new Server()
                                .url("https://api.karaokeapp.com")
                                .description("Production Server")))
                .tags(List.of(
                        new Tag()
                                .name("Songs")
                                .description("Song management and reference audio operations"),
                        new Tag()
                                .name("Performances")
                                .description("User performance upload and scoring operations")));
    }
}
