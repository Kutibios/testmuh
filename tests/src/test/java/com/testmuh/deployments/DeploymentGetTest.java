package com.testmuh.deployments;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.lessThan;


public class DeploymentGetTest extends BaseTest {

    @Test
    @DisplayName("GET /deployments -> 200 OK, JSON array, 2 saniye altinda")
    public void deployment_listesi_alinabilmeli() {
        given()
                .spec(requestSpec)
        .when()
                .get("/deployments")
        .then()
                .spec(responseSpec)
                .statusCode(200)
                .time(lessThan(2000L))
                .body("size()", greaterThanOrEqualTo(0));
    }

    @Test
    @DisplayName("GET /deployments/{bilinmeyen-id} -> 404, detail mesaji icermeli")
    public void var_olmayan_deployment_404_donmeli() {
        given()
                .spec(requestSpec)
        .when()
                .get("/deployments/yok-boyle-id-12345")
        .then()
                .spec(responseSpec)
                .statusCode(404)
                .body("detail", containsString("Deployment bulunamadi"));
    }
}
