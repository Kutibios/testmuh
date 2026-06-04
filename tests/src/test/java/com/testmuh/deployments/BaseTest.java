package com.testmuh.deployments;

import io.restassured.RestAssured;
import io.restassured.builder.RequestSpecBuilder;
import io.restassured.builder.ResponseSpecBuilder;
import io.restassured.filter.log.RequestLoggingFilter;
import io.restassured.filter.log.ResponseLoggingFilter;
import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;
import io.restassured.specification.ResponseSpecification;
import org.junit.jupiter.api.BeforeAll;

/**
 * Tum test siniflarinin paylasacagi Rest Assured konfigurasyonu.
 *
 * - baseURI: -Dapi.baseUri=... ile override edilebilir (CI'da farkli ortam).
 * - Request/response loglari konsola basilir: sunumda gorunurluk icin onemli.
 * - requestSpec/responseSpec: tekrar eden header ve genel beklentileri tek yerde toplar.
 */
public abstract class BaseTest {

    protected static RequestSpecification requestSpec;
    protected static ResponseSpecification responseSpec;

    @BeforeAll
    public static void globalSetup() {
        RestAssured.baseURI = System.getProperty(
                "api.baseUri",
                "http://localhost:8002"
        );

        requestSpec = new RequestSpecBuilder()
                .setContentType(ContentType.JSON)
                .addFilter(new RequestLoggingFilter())
                .addFilter(new ResponseLoggingFilter())
                .build();

        responseSpec = new ResponseSpecBuilder()
                .expectContentType(ContentType.JSON)
                .build();
    }
}
