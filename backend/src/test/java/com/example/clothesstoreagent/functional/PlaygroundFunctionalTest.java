package com.example.clothesstoreagent.functional;

import com.example.clothesstoreagent.ClothesStoreAgentApplication;
import com.example.clothesstoreagent.entity.Person;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.annotation.DirtiesContext;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        webEnvironment = WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.profiles.active=h2"
        },
        classes = ClothesStoreAgentApplication.class
)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class PlaygroundFunctionalTest {

    @Autowired
    TestRestTemplate restTemplate;

    @Test
    void getPeopleReturnsSeededPeopleWithAddresses() {
        ResponseEntity<Person[]> resp = restTemplate.getForEntity("/api/people", Person[].class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody()).isNotNull();

        List<Person> people = List.of(resp.getBody());
        assertThat(people).extracting(Person::getName).contains("Devon", "Mina");

        Optional<Person> devon = people.stream().filter(p -> "Devon".equals(p.getName())).findFirst();
        assertThat(devon).isPresent();
        assertThat(devon.get().getAddresses()).hasSize(2);
    }

    @Test
    void postPeopleAddsPeopleAndPersistsAddresses() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        List<Map<String, Object>> payload = List.of(
                Map.of(
                        "name", "Sachin",
                        "age", 4,
                        "addresses", List.of(
                                Map.of("city", "Mumbai"),
                                Map.of("city", "Vizag")
                        )
                )
        );

        ResponseEntity<Void> postResp = restTemplate.postForEntity(
                "/api/people",
                new HttpEntity<>(payload, headers),
                Void.class
        );
        assertThat(postResp.getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<Person[]> getResp = restTemplate.getForEntity("/api/people", Person[].class);
        assertThat(getResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(getResp.getBody()).isNotNull();

        List<Person> people = List.of(getResp.getBody());
        Optional<Person> sachin = people.stream().filter(p -> "Sachin".equals(p.getName())).findFirst();
        assertThat(sachin).isPresent();
        assertThat(sachin.get().getAddresses()).extracting(a -> a.getCity()).containsExactlyInAnyOrder("Mumbai", "Vizag");
    }

    @Test
    void reactiveEndpointsReturnPeopleAsJsonArrays() {
        ResponseEntity<Person[]> mvcResp = restTemplate.getForEntity("/api/people", Person[].class);
        ResponseEntity<Person[]> monoResp = restTemplate.getForEntity("/api/people/mono", Person[].class);
        ResponseEntity<Person[]> fluxResp = restTemplate.getForEntity("/api/people/flux", Person[].class);

        assertThat(mvcResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(monoResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(fluxResp.getStatusCode()).isEqualTo(HttpStatus.OK);

        assertThat(mvcResp.getBody()).isNotNull();
        assertThat(monoResp.getBody()).isNotNull();
        assertThat(fluxResp.getBody()).isNotNull();

        assertThat(monoResp.getBody().length).isEqualTo(mvcResp.getBody().length);
        assertThat(fluxResp.getBody().length).isEqualTo(mvcResp.getBody().length);
    }
}
