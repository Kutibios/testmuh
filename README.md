# Deployment Tracker — API Regresyon Test Projesi

Yazılım Test Mühendisliği dersi proje ödevi.
**Rest Assured + JUnit 5 + Maven** ile, kendi yazdığımız bir **FastAPI** mini-servisinin
otomatik API regresyon testleri.

> **Tema:** DevOps pipeline'larında API regresyon testleri ve AI destekli test mühendisliği.

---

## Mimari

```
+-----------------------------+         +------------------------------+
|  Rest Assured Test Sürücüsü |  HTTP   |  Deployment Tracker API      |
|  (Java 17 + Maven + JUnit5) +-------->+  (Python + FastAPI)          |
|  tests/                     |         |  service/ (Docker container) |
+-----------------------------+         +------------------------------+
```

- **Servis (`service/`)**: CI/CD pipeline'larının deployment kayıtlarını tuttuğu
  küçük bir REST servisidir. `GET /health`, `GET /deployments`,
  `GET /deployments/{id}`, `POST /deployments`, `DELETE /deployments/{id}`
  endpoint'lerini sunar.
- **Testler (`tests/`)**: Servise karşı status code, response body alanları ve
  response time kontrollerini içeren regresyon testleri.

## Önkoşullar

- Docker + Docker Compose
- Java 17 veya üzeri
- Maven 3.8+

## Çalıştırma

**1) Servisi ayağa kaldır**
```bash
docker compose up -d --build
curl http://localhost:8002/health
# {"status":"ok"}
```

Swagger UI: <http://localhost:8002/docs>

**2) Testleri koş**
```bash
cd tests
mvn test
```

Beklenen çıktı (özet):
```
Tests run: 9, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

**3) Tek bir test sınıfını çalıştırmak**
```bash
mvn -Dtest=DeploymentPostTest test
```

**4) Test raporları**
```
tests/target/surefire-reports/
```

**5) Servisi durdur**
```bash
docker compose down
```

## Test İçeriği

| Sınıf | Test | Kontroller |
|---|---|---|
| `HealthTest` | `health_endpoint_ok_donmeli` | 200, `status=ok`, `time<1000ms` |
| `DeploymentGetTest` | `deployment_listesi_alinabilmeli` | 200, array, `time<2000ms` |
| `DeploymentGetTest` | `var_olmayan_deployment_404_donmeli` | 404, hata mesajı |
| `DeploymentPostTest` | `yeni_deployment_kaydedilebilmeli` | 201, body alanları, `time<2000ms` |
| `DeploymentPostTest` | `eklenen_deployment_get_ile_dogrulanabilmeli` | e2e POST→GET regresyon zinciri |
| `DeploymentPostTest` | `eksik_alan_gonderilince_422_donmeli` | 422 validation (negatif test) |
| `DeploymentDeleteTest` | `var_olan_deployment_silinebilmeli` | 204, `time<2000ms` |
| `DeploymentDeleteTest` | `silinen_deployment_get_ile_bulunamamali` | DELETE → GET 404 zinciri |
| `DeploymentDeleteTest` | `var_olmayan_deployment_silinmeye_calisilinca_404_donmeli` | 404 (negatif) |

Ödev gerekleri ile eşleme:
- **Status code kontrolü** → tüm testlerde
- **Response body değer kontrolü** → tüm testlerde (Hamcrest matchers)
- **Response time kontrolü** → Health + Get + Post + Delete pozitif senaryoları
- **GET örneği** → `HealthTest`, `DeploymentGetTest`
- **POST örneği + request body** → `DeploymentPostTest`
- **DELETE örneği** → `DeploymentDeleteTest` (bonus — CRUD'un dördüncü köşesi)

## CI/CD Pipeline

Proje [GitHub Actions](.github/workflows/test.yml) ile CI'a hazır şekilde
yapılandırılmıştır. Repo'ya push edildiğinde otomatik olarak şu adımlar koşar:

1. Checkout
2. `docker compose up -d --build` ile servisi ayağa kaldır
3. `/health` endpoint'i ile servisin hazır olmasını bekle
4. Java 17 + Maven kur (Maven bağımlılıkları cache'lenir)
5. `mvn test` ile 9 Rest Assured testini koş
6. Surefire raporlarını artifact olarak yükle
7. Servisi temiz kapat

Bu workflow'un mantığı **laptop'ta `docker compose up -d && cd tests && mvn test`
ile aynıdır** — containerization sayesinde geliştirme ortamı = CI ortamı.

## Sunum

Marp Markdown ile yazılmıştır.

- Kaynak: [sunum/sunum.md](sunum/sunum.md)
- Slayt (PPTX): [sunum/sunum.pptx](sunum/sunum.pptx)

Slaytı yeniden üretmek için (Docker ile, Node kurmaya gerek yok):
```bash
docker run --rm --init -v "$PWD/sunum:/home/marp/app" -e MARP_USER="$(id -u):$(id -g)" \
    marpteam/marp-cli sunum.md -o sunum.pptx
```

## Proje yapısı

```
testmuh/
├── docker-compose.yml
├── service/                          # FastAPI mini-servis
│   ├── Dockerfile
│   ├── requirements.txt
│   └── app/
│       ├── main.py
│       └── store.py
├── tests/                            # Java/Maven test projesi
│   ├── pom.xml
│   └── src/test/
│       ├── java/com/testmuh/deployments/
│       │   ├── BaseTest.java
│       │   ├── HealthTest.java
│       │   ├── DeploymentGetTest.java
│       │   ├── DeploymentPostTest.java
│       │   └── DeploymentDeleteTest.java
│       └── resources/testdata/
│           └── yeni-deployment.json
└── sunum/
    ├── sunum.md
    └── sunum.pptx
```
