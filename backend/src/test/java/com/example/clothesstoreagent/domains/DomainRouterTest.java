package com.example.clothesstoreagent.domains;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DomainRouterTest {

    @Test
    void respectsHint() {
        DomainRouter r = new DomainRouter();
        assertThat(r.route(DomainHint.GENERAL, "show revenue")).isEqualTo(Domain.GENERAL);
        assertThat(r.route(DomainHint.ANALYTICS_SQL, "hello")).isEqualTo(Domain.ANALYTICS_SQL);
    }

    @Test
    void autoRoutesAnalyticsSqlOnKeywords() {
        DomainRouter r = new DomainRouter();
        assertThat(r.route(DomainHint.AUTO, "top products by revenue last month"))
                .isEqualTo(Domain.ANALYTICS_SQL);
        assertThat(r.route(DomainHint.AUTO, "show schema for orders table"))
                .isEqualTo(Domain.ANALYTICS_SQL);
        assertThat(r.route(DomainHint.AUTO, "what is the return policy?"))
                .isEqualTo(Domain.GENERAL);
    }
}



