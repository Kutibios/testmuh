package com.testmuh.deployments;

import io.restassured.response.Response;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.File;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.lessThan;


public class DeploymentDeleteTest extends BaseTest {

    private static final File YENI_DEPLOYMENT_BODY =
            new File("src/test/resources/testdata/yeni-deployment.json");

    @Test
    @DisplayName("DELETE /deployments/{id} -> 204 No Content, 2 saniye altinda")
    public void var_olan_deployment_silinebilmeli() {
        // silincek kayıt oluştururum
        Response postResponse = given()
                .spec(requestSpec)
                .body(YENI_DEPLOYMENT_BODY)
        .when()
                .post("/deployments")
        .then()
                .statusCode(201)
                .extract().response();

        String silinecekId = postResponse.jsonPath().getString("id");

        // silme
        given()
                .spec(requestSpec)
        .when()
                .delete("/deployments/" + silinecekId)
        .then()
                .statusCode(204)
                .time(lessThan(2000L));
    }

    @Test
    @DisplayName("DELETE sonrasi GET ayni id -> 404 donmeli (silindigini dogrular)")
    public void silinen_deployment_get_ile_bulunamamali() {
        // oluşturma
        String silinecekId = given()
                .spec(requestSpec)
                .body(YENI_DEPLOYMENT_BODY)
        .when()
                .post("/deployments")
        .then()
                .statusCode(201)
                .extract().jsonPath().getString("id");

        // Sil
        given()
                .spec(requestSpec)
        .when()
                .delete("/deployments/" + silinecekId)
        .then()
                .statusCode(204);

        // 404 döner
        given()
                .spec(requestSpec)
        .when()
                .get("/deployments/" + silinecekId)
        .then()
                .spec(responseSpec)
                .statusCode(404)
                .body("detail", containsString("Deployment bulunamadi"));
    }

    @Test
    @DisplayName("DELETE /deployments/{bilinmeyen-id} -> 404 (negatif)")
    public void var_olmayan_deployment_silinmeye_calisilinca_404_donmeli() {
        given()
                .spec(requestSpec)
        .when()
                .delete("/deployments/yok-boyle-id-99999")
        .then()
                .spec(responseSpec)
                .statusCode(404)
                .body("detail", containsString("Deployment bulunamadi"));
    }
}
