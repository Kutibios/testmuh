package com.testmuh.deployments;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.lessThan;


public class HealthTest extends BaseTest {

    @Test
    @DisplayName("GET /health -> 200 OK, body status=ok, 1 saniye altinda cevap")
    public void health_endpoint_ok_donmeli() {
        given()
                .spec(requestSpec)
        .when()
                .get("/health")
        .then()
                .spec(responseSpec)
                .statusCode(200)
                .time(lessThan(1000L))
                .body("status", equalTo("ok"));
    }
}
