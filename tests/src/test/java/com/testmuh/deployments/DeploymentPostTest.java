package com.testmuh.deployments;

import io.restassured.response.Response;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.File;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.lessThan;
import static org.hamcrest.Matchers.notNullValue;

/**
 * POST endpoint regresyon testleri.
 * - Yeni kayit eklenebilmeli (request body ile)
 * - Eklenen kayit GET ile dogrulanabilmeli (end-to-end regresyon zinciri)
 * - Eksik alan gonderilince FastAPI 422 dondurmeli (negatif senaryo)
 */
public class DeploymentPostTest extends BaseTest {

    private static final File YENI_DEPLOYMENT_BODY =
            new File("src/test/resources/testdata/yeni-deployment.json");

    @Test
    @DisplayName("POST /deployments -> 201 Created, body alanlari dogru, 2 saniye altinda")
    public void yeni_deployment_kaydedilebilmeli() {
        given()
                .spec(requestSpec)
                .body(YENI_DEPLOYMENT_BODY)
        .when()
                .post("/deployments")
        .then()
                .spec(responseSpec)
                .statusCode(201)
                .time(lessThan(2000L))
                .body("id", notNullValue())
                .body("service", equalTo("payment-api"))
                .body("version", equalTo("v1.4.2"))
                .body("environment", equalTo("prod"))
                .body("status", equalTo("in_progress"))
                .body("created_at", notNullValue());
    }

    @Test
    @DisplayName("POST sonrasi GET /deployments/{id} -> ayni kaydi dondurmeli (e2e)")
    public void eklenen_deployment_get_ile_dogrulanabilmeli() {
        // 1) Once POST ile yeni kayit olustur, donen id'yi yakala
        Response postResponse = given()
                .spec(requestSpec)
                .body(YENI_DEPLOYMENT_BODY)
        .when()
                .post("/deployments")
        .then()
                .spec(responseSpec)
                .statusCode(201)
                .extract().response();

        String yeniId = postResponse.jsonPath().getString("id");

        // 2) Ayni id ile GET yapip kaydin gercekten saklandigini dogrula
        given()
                .spec(requestSpec)
        .when()
                .get("/deployments/" + yeniId)
        .then()
                .spec(responseSpec)
                .statusCode(200)
                .body("id", equalTo(yeniId))
                .body("service", equalTo("payment-api"))
                .body("version", equalTo("v1.4.2"));
    }

    @Test
    @DisplayName("POST eksik body -> 422 Unprocessable Entity (FastAPI validation)")
    public void eksik_alan_gonderilince_422_donmeli() {
        String eksikBody = "{\"service\": \"only-service-field\"}";

        given()
                .spec(requestSpec)
                .body(eksikBody)
        .when()
                .post("/deployments")
        .then()
                .spec(responseSpec)
                .statusCode(422)
                .body("detail", notNullValue());
    }
}
