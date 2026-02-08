package com.example.clothesstoreagent.domains;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DomainsConfig {
    @Bean
    public DomainRouter domainRouter() {
        return new DomainRouter();
    }
}




