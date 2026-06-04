# Teknik Anlatım — Projeyi Baştan Sona Anlamak

Bu dosya, projedeki her dosya ve kararın **neden öyle olduğunu**, **ne anlama
geldiğini** ve **hocaya nasıl anlatılacağını** açıklar. Sunuma hazırlanırken bu
dosyayı baştan sona okumak yeterli.

İçindekiler:
1. Büyük resim ve mimari
2. Docker katmanı
3. FastAPI servisi (servis tarafı)
4. Maven test projesi (test tarafı)
5. Her test sınıfının analizi
6. DevOps bağlantısı (4 katman)
7. AI destekli test mühendisliği
8. Sunum akışı ve hocaya söylenecek cümleler

---

## 1. Büyük Resim

İki ayrı bileşen, HTTP üzerinden konuşuyor:

```
+-------------------------------+    HTTP    +------------------------------+
|  TEST SÜRÜCÜSÜ                | ---------> | TEST EDİLEN SERVİS           |
|  Java 17 + Maven + JUnit 5    |  istek     | Python 3.12 + FastAPI        |
|  + Rest Assured kütüphanesi   | <--------- | Docker container içinde      |
|  Konum: tests/                |   cevap    | Konum: service/              |
+-------------------------------+            +------------------------------+
```

- **Servis**: Bizim yazdığımız mini bir REST API. Adı **Deployment Tracker** —
  CI/CD pipeline'ları bir deployment yapınca bu API'ye kaydeder; sonra başka
  adımlar bu kayıtları sorgular. Gerçek hayatta Spinnaker, Argo Rollouts,
  Backstage gibi araçların yaptığı işin minik örneği.
- **Test sürücüsü**: Java/Maven/JUnit/Rest Assured ile yazılmış otomatik
  regresyon testleri. `mvn test` deyince servise gerçek HTTP istekleri atıyor,
  dönen cevabı doğruluyor.

**Niye böyle ayrı?** Çünkü gerçek hayatta da böyle. Test edilen kod ve testleri
yazan kod ayrı projeler/diller olabilir. "Black-box API testing" budur:
testler servisin içine bakmaz, sadece dışarıdan HTTP konuşur.

---

## 2. Docker Katmanı

Hocanın ilk soracağı: "Niye Docker kullandın?"

### Üç parçalı cevap

1. **Tekrarlanabilirlik**: Servis Python 3.12 + FastAPI bağımlılıklarıyla
   çalışıyor. Hocanın bilgisayarında Python kurulu olmayabilir, farklı versiyon
   olabilir. Docker container'ı her bilgisayarda **bire bir aynı** çalışır.
2. **DevOps gerçekliği**: 2026 yılında ciddi bir backend Docker'sız deploy
   edilmiyor. CI/CD pipeline'larında "build → image push → deploy" akışının
   merkezinde container var.
3. **Test izolasyonu**: Servisi container'da çalıştırınca, testler bittikten
   sonra `docker compose down` ile tek komutla temiz duruma dönüyoruz. Host
   makineye hiç bulaşmıyor.

### 2.1 [service/Dockerfile](../service/Dockerfile)

```dockerfile
FROM python:3.12-slim
WORKDIR /app
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt
COPY app ./app
EXPOSE 8002
HEALTHCHECK --interval=5s --timeout=3s --start-period=5s --retries=5 \
    CMD python -c "import urllib.request,sys; sys.exit(0 if urllib.request.urlopen('http://localhost:8002/health').status==200 else 1)"
CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8002"]
```

**Satır satır mantığı:**

- **`FROM python:3.12-slim`**: Base image olarak Debian üzerinde minimal Python.
  Tam Python imajı ~900MB, slim ~120MB. CI/CD'de imaj boyutu = deploy hızı =
  para.
- **`WORKDIR /app`**: Container içindeki "ev dizinimiz". Buradan sonraki tüm
  komutlar burada çalışır.
- **`COPY requirements.txt` → `RUN pip install` → SONRA `COPY app`**: Bu
  sıralama önemli! Docker layer cache mantığı: requirements değişmediği sürece
  `pip install` adımı cache'den gelir, sadece kod kopyalanır. Build saniyeler
  sürer. **Tipik bir DevOps optimizasyonu.**
- **`EXPOSE 8002`**: Container 8002 port'unu dinleyecek demektir. (Bunu host'a
  yayınlama işini docker-compose yapar.)
- **`HEALTHCHECK`**: Docker'a "bu container sağlıklı mı?" sorusunun cevabını
  öğretiyoruz. Her 5 saniyede bir `/health` endpoint'ine ping atıyor. Bu
  **Kubernetes liveness/readiness probe'larının küçük bir karşılığı** — gerçek
  üretimde Kubernetes, sağlıksız pod'u öldürüp yenisini başlatır.
- **`CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8002"]`**:
  Container başladığında çalışacak komut. **Uvicorn** = ASGI web sunucusu
  (FastAPI'nin önerdiği). `0.0.0.0` derken: container'ın tüm network
  arayüzlerini dinle (sadece localhost değil), yoksa dışarıdan ulaşılamaz.

### 2.2 [docker-compose.yml](../docker-compose.yml)

```yaml
services:
  deployment-tracker:
    build: ./service
    container_name: deployment-tracker
    ports:
      - "8002:8002"
    restart: unless-stopped
    healthcheck: ...
```

- `build: ./service` → bu klasördeki Dockerfile'la imaj kurulacak
- `ports: "8002:8002"` → **host:container** — host'un 8002 portundan
  container'ın 8002'ine yönlendir
- `restart: unless-stopped` → crash olursa otomatik yeniden başlat
- `healthcheck` → compose seviyesinde de sağlık kontrolü

**`docker compose up -d --build`** komutu:
1. `service/` klasöründeki Dockerfile ile **imaj build edilir**.
2. Container oluşturulur, **8002 portu host'a açılır**.
3. `-d` (detached) sayesinde arka planda çalışır.

**`docker compose down`** → container durdurulur ve silinir (imaj kalır).

---

## 3. FastAPI Servisi — Test Edilen Hedef

### 3.1 [service/requirements.txt](../service/requirements.txt)

```
fastapi==0.115.5
uvicorn[standard]==0.32.1
pydantic==2.10.3
```

- **FastAPI**: Modern Python web framework. Tip ipuçlarından otomatik Swagger
  UI, otomatik validation, otomatik JSON serialization yapıyor.
- **Uvicorn**: FastAPI'yi çalıştıran ASGI sunucusu. `[standard]` = performans
  için extra C kütüphaneleri.
- **Pydantic**: Data validation. FastAPI bunu içeride kullanıyor.

**Versiyonlar sabit (`==`): "pinning".** DevOps açısından önemli — yarın yeni
versiyon çıksa testlerin kırılmasın diye. Reproducible build.

### 3.2 [service/app/store.py](../service/app/store.py)

In-memory veri ambarı — basit bir Python dict'ini sarmalayan sınıf:

```python
class DeploymentStore:
    def __init__(self):
        self._items: Dict[str, dict] = {}
    def add(self, deployment): self._items[deployment["id"]] = deployment
    def get(self, deployment_id): return self._items.get(deployment_id)
    def list_all(self): return list(self._items.values())
    def delete(self, deployment_id): return self._items.pop(deployment_id, None) is not None
    def clear(self): self._items.clear()
```

**Niye in-memory?** Container restart olunca veriler silinir. Gerçek üretimde
Postgres olurdu. Ama bizim için bu **bir özellik**: testler her başladığında
temiz bir slate'ten başlayabilir. "Test izolasyonu" — DevOps testlerinin altın
kuralı.

Son satırda `store = DeploymentStore()` — **singleton**. Tek bir instance var,
tüm endpoint'ler bunu paylaşıyor.

### 3.3 [service/app/main.py](../service/app/main.py) — Asıl API

#### Veri modeli (Pydantic)

```python
class DeploymentIn(BaseModel):
    service: str = Field(..., min_length=1, examples=["payment-api"])
    version: str = Field(..., min_length=1, examples=["v1.4.2"])
    environment: Literal["dev", "staging", "prod"]
    status: Literal["success", "failed", "in_progress"] = "in_progress"

class Deployment(DeploymentIn):
    id: str
    created_at: str
```

- `DeploymentIn` = istemcinin POST'ta göndereceği body (id ve created_at
  server tarafından üretilir).
- `Deployment` = server'ın cevap verirken döneceği tam kayıt.
- `Literal["dev", "staging", "prod"]` = bu 3 değerden biri olmalı. Başka bir
  şey gelirse FastAPI **otomatik 422 döner**. Bizim "eksik alan 422" testimiz
  bunu kullanıyor.

#### Endpoint'ler

| HTTP | Path | Ne yapıyor | Status |
|---|---|---|---|
| GET | `/health` | `{"status":"ok"}` döner | 200 |
| GET | `/deployments` | Tüm kayıtların listesi | 200 |
| GET | `/deployments/{id}` | Tek kayıt, yoksa 404 | 200 / 404 |
| POST | `/deployments` | Yeni kayıt, id + timestamp server üretir | 201 |
| DELETE | `/deployments/{id}` | Kaydı sil, yoksa 404 | 204 / 404 |

POST'ta `uuid.uuid4()` ile id, `datetime.now(timezone.utc).isoformat()` ile
timestamp üretiyoruz. UUID seçmemizin nedeni: çakışma yok, dağıtık sistemlerde
güvenli.

**Status code'lar bilinçli seçildi:**
- POST: `201 Created` (200 değil — yeni kaynak yaratıldı diyor)
- DELETE: `204 No Content` (body yok, sadece "tamam silindi")
- Validation hatası: `422 Unprocessable Entity` (FastAPI default'u)
- Bulunamadı: `404 Not Found`

Bu **REST semantik standardı**dır (RFC 7231). Hoca "neden 200 değil 201?" diye
sorabilir — cevabı bu.

#### `/health` neden var?

Bu endpoint testler için değil, **DevOps için**:
- Docker healthcheck'i bunu çağırıyor → container sağlıklı mı?
- Kubernetes liveness/readiness probe'u bunu çağırırdı → pod hazır mı?
- Load balancer bunu çağırırdı → bu instance'a trafik yönlendireyim mi?

Yani: **`/health` = DevOps'un kalp atışı dinleme yeri.**

---

## 4. Maven Test Projesi — Test Sürücüsü

### 4.1 [tests/pom.xml](../tests/pom.xml)

Maven'in proje tanım dosyası. Üç şeye karar verir: **bağımlılıklar**, **derleme
ayarları**, **plugin'ler**.

#### Bağımlılıklar

| Kütüphane | Versiyon | Niye |
|---|---|---|
| rest-assured | 5.4.0 | Ana kütüphane. HTTP isteği + cevap doğrulama. `given().when().then()` BDD sözdizimi. |
| json-schema-validator | 5.4.0 | JSON şema doğrulaması (hazır bekliyor) |
| junit-jupiter | 5.10.2 | JUnit 5 — `@Test`, `@DisplayName`, `@BeforeAll` |
| hamcrest | 2.2 | `equalTo`, `lessThan`, `containsString`, `notNullValue` matcher'lar |
| maven-surefire-plugin | 3.2.5 | Maven'in test koşucusu |

`<scope>test</scope>` = bunlar sadece test sırasında lazım, derlenmiş JAR'a
dahil edilmesin.

#### Surefire plugin önemi

```xml
<systemPropertyVariables>
    <api.baseUri>${api.baseUri}</api.baseUri>
</systemPropertyVariables>
```

`-Dapi.baseUri=...` parametresini Java tarafına geçiriyoruz. Yani test komut
satırında base URL'i override edebiliyorsun:

```bash
mvn test -Dapi.baseUri=https://staging.example.com
```

Bu **CI/CD'de kritik** — aynı test suite'i farklı ortamlara karşı koşuyorsun.

### 4.2 [tests/.../BaseTest.java](../tests/src/test/java/com/testmuh/deployments/BaseTest.java)

**Test mühendisliğinin altın kuralı: DRY (Don't Repeat Yourself).** Tüm testlerin
paylaşacağı kurulumu buraya topladık.

```java
public abstract class BaseTest {
    protected static RequestSpecification requestSpec;
    protected static ResponseSpecification responseSpec;

    @BeforeAll
    public static void globalSetup() {
        RestAssured.baseURI = System.getProperty("api.baseUri", "http://localhost:8002");

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
```

**Üç önemli şey:**

1. **`RestAssured.baseURI`**: Tüm istekler bu URL'in altında olacak. Sistem
   property'sinden okuyoruz, yoksa `localhost:8002`.
2. **`RequestSpecification`**: Her istekte tekrar edecek ayarlar — Content-Type,
   logging. Her test bunu `given().spec(requestSpec)` ile kullanıyor.
3. **`RequestLoggingFilter` + `ResponseLoggingFilter`**: İstek ve cevabı
   **konsola basıyor**. Demo sırasında çok değerli — hoca ne gönderildiğini, ne
   döndüğünü görür.

`abstract class` çünkü direkt instance edilmesin, miras alınsın. Test sınıfları
`extends BaseTest` yapıyor.

### 4.3 [tests/.../yeni-deployment.json](../tests/src/test/resources/testdata/yeni-deployment.json)

```json
{
  "service": "payment-api",
  "version": "v1.4.2",
  "environment": "prod",
  "status": "in_progress"
}
```

Test verisini koddan ayırdık. Niye?
- **Bakım**: Veriyi değiştirmek için Java kodunu yeniden derlemek gerekmez.
- **Çoklu senaryo**: Yarın "büyük payload", "özel karakterler" testleri istersek
  farklı JSON dosyalarıyla çoğaltırız.
- **Okunabilirlik**: Test koduna 20 satır `String body = "{...}"` koymaktansa
  dosyaya işaret etmek temiz.

---

## 5. Test Sınıfları — Her Birini Tek Tek

### 5.1 [HealthTest.java](../tests/src/test/java/com/testmuh/deployments/HealthTest.java)

```java
@Test
public void health_endpoint_ok_donmeli() {
    given().spec(requestSpec)
    .when().get("/health")
    .then().spec(responseSpec)
        .statusCode(200)
        .time(lessThan(1000L))
        .body("status", equalTo("ok"));
}
```

**Pipeline'ın smoke test'i.** "Servis ayakta mı?" sorusunun en hızlı cevabı.

- `given()` = isteği hazırlıyorum
- `.when()` = isteği atıyorum (HTTP fiili burada — `.get("/health")`)
- `.then()` = cevabı doğruluyorum
- **3 kontrol birden**: status code + response time + body

`body("status", equalTo("ok"))` ifadesi **JSON Path**: Rest Assured cevabı parse
edip alanı çekiyor, Hamcrest matcher ile karşılaştırıyor.

### 5.2 [DeploymentGetTest.java](../tests/src/test/java/com/testmuh/deployments/DeploymentGetTest.java)

İki test: **pozitif** (liste alınabiliyor mu) ve **negatif** (var olmayan id 404 mü).

```java
@Test
public void deployment_listesi_alinabilmeli() {
    given().spec(requestSpec)
    .when().get("/deployments")
    .then().spec(responseSpec)
        .statusCode(200)
        .time(lessThan(2000L))
        .body("size()", greaterThanOrEqualTo(0));
}
```

`body("size()", greaterThanOrEqualTo(0))` = JSON array'in eleman sayısı 0 veya
daha fazla. Esas kontrol "200 döndü ve array oldu" — sıfırdan az olamayacağı
için bu satır şunu garanti ediyor: **dönen şey gerçekten bir array**.

```java
@Test
public void var_olmayan_deployment_404_donmeli() {
    given().spec(requestSpec)
    .when().get("/deployments/yok-boyle-id-12345")
    .then().spec(responseSpec)
        .statusCode(404)
        .body("detail", containsString("Deployment bulunamadi"));
}
```

**Negatif test = en değerli test türlerinden biri.** Servis "hata durumunda
doğru hata mesajı veriyor mu" sorusunu cevaplıyor.

### 5.3 [DeploymentPostTest.java](../tests/src/test/java/com/testmuh/deployments/DeploymentPostTest.java)

Üç test: kayıt ekleme, ekleneni doğrulama (e2e zincir), eksik body.

#### Test 1 — Kayıt ekleme (6 doğrulama)

```java
given().spec(requestSpec).body(YENI_DEPLOYMENT_BODY)
.when().post("/deployments")
.then().spec(responseSpec)
    .statusCode(201)
    .time(lessThan(2000L))
    .body("id", notNullValue())
    .body("service", equalTo("payment-api"))
    .body("version", equalTo("v1.4.2"))
    .body("environment", equalTo("prod"))
    .body("status", equalTo("in_progress"))
    .body("created_at", notNullValue());
```

Tek test → 6 farklı doğrulama: status, time, ve 5 farklı body alanı.

#### Test 2 — E2E zincir

```java
// 1) POST ile kayıt yarat, id'yi yakala
Response postResponse = given().spec(requestSpec).body(...)
    .when().post("/deployments")
    .then().statusCode(201).extract().response();

String yeniId = postResponse.jsonPath().getString("id");

// 2) Aynı id ile GET yap, kayıt gerçekten saklandı mı doğrula
given().spec(requestSpec)
.when().get("/deployments/" + yeniId)
.then().spec(responseSpec)
    .statusCode(200)
    .body("id", equalTo(yeniId))
    .body("service", equalTo("payment-api"));
```

**Bu çok önemli.** Çünkü servis "201 döndüm" diyebilir ama aslında kaydı
saklamamış olabilir. POST → GET zinciri bu **silent failure** durumunu yakalıyor.
Test mühendisliği literatüründe buna **"data integrity test"** denir.

#### Test 3 — Eksik body → 422

```java
String eksikBody = "{\"service\": \"only-service-field\"}";

given().spec(requestSpec).body(eksikBody)
.when().post("/deployments")
.then().spec(responseSpec)
    .statusCode(422)
    .body("detail", notNullValue());
```

FastAPI **otomatik olarak** validation yapıp 422 dönüyor (`version`,
`environment` zorunlu alanlar). Test bu otomatik davranışın **gerçekten
çalıştığını** doğruluyor. Yarın biri Pydantic modelini bozarsa bu test kırmızı
düşer.

### 5.4 [DeploymentDeleteTest.java](../tests/src/test/java/com/testmuh/deployments/DeploymentDeleteTest.java)

Üç test: silme, silindi mi zinciri, var olmayan id ile silme.

#### Test 1 — Silme (test bağımsızlığı)

```java
// Önce silinecek kaydı oluştur
String silinecekId = given().spec(requestSpec).body(YENI_DEPLOYMENT_BODY)
    .when().post("/deployments")
    .then().statusCode(201).extract().jsonPath().getString("id");

// Sil
given().spec(requestSpec)
.when().delete("/deployments/" + silinecekId)
.then()
    .statusCode(204)
    .time(lessThan(2000L));
```

Test kendi kaydını yaratıp kendi silmesi → **test bağımsızlığı**. Diğer
testlerden veri beklemiyor. Bu, paralel test koşumu için kritik.

#### Test 2 — DELETE→GET 404 zinciri

Sildikten sonra aynı id'yi GET edince 404 dönmeli. **Silmenin gerçekten
gerçekleştiğini** doğruluyor. POST→GET'in kardeşi.

#### Test 3 — Var olmayan id

Servis "ben her DELETE'e 204 derim" diye davranamasın diye negatif testi.

---

## 6. DevOps Bağlantısı (4 Katman)

Bu projede DevOps **dört katmanda** mevcut:

### 6.1 Containerization
Servis Docker imajı olarak paketleniyor. Bu, modern DevOps'un en temel pratiği.
Yarın aynı imaj `docker push registry/...` ile container registry'sine atılır,
oradan Kubernetes'e deploy edilir. Aynı imaj geliştirici laptop'unda, CI'da,
staging'de, production'da çalışır — **build once, run anywhere**.

### 6.2 Health endpoint
`/health` endpoint'i Kubernetes liveness/readiness probe'larının küçük versiyonu.
Gerçek üretimde Kubernetes pod'a "hazır mısın?" diye sorar, hazır değilse trafik
yönlendirmez. Bizim Docker healthcheck'imiz aynı mantığın küçük örneği.

### 6.3 Otomatik regresyon testi = CI/CD'nin kalbi

Tipik bir CI/CD pipeline'ı:

```
Geliştirici commit ─► CI Pipeline başlar
                       │
                       ├── 1. Lint (kod stili)
                       ├── 2. Unit tests (saniyeler)
                       ├── 3. Docker image build
                       ├── 4. Container ayağa kaldır
                       ├── 5. API/Integration tests   ◄── BİZİM TESTLER BURADA
                       ├── 6. Image'ı registry'e push
                       └── 7. Staging'e deploy + smoke test
```

Bizim testler bu pipeline'ın **5. adımına** dropluyor. Aynı
`docker compose up -d && cd tests && mvn test` komutu hem laptop'ta hem GitHub
Actions'ta hem GitLab CI'da aynı şekilde çalışıyor.

### 6.4 Shift-left testing
Klasik dünyada test "sonda" yapılırdı. DevOps "shift-left" der: testi solu,
yani **geliştirme aşamasına** kaydır. Geliştirici commit etmeden testleri kendi
laptop'unda koşabilmeli. Bizim proje tam böyle:

- `docker compose up -d` → 10 saniyede servis hazır
- `mvn test` → 5 saniyede 9 test
- Toplam ~15 saniye, geliştiricinin akışını bozmadan

Bu zaman bütçesi yüzünden seçtiğimiz teknolojiler de mantıklı: in-memory store
(DB kurulumu yok), Python (compile yok), hafif Docker imajı.

### Bonus: Twelve-Factor App ilkelerine yakınlık

[12factor.net](https://12factor.net) DevOps'un kutsal kitabı sayılır. Projemiz
birkaç ilkesine uyuyor:
- **Config in environment** — `api.baseUri` system property'sinden okunuyor,
  kodda hardcoded değil
- **Disposability** — container hızlı başlıyor, hızlı duruyor
- **Dev/prod parity** — local'de Docker, prod'da da Docker olur

---

## 7. AI Destekli Test Mühendisliği (Sunum B Bölümü)

Slayt akışında anlatacağın konular:

- **LLM ile test üretimi**: OpenAPI/Swagger şemasından otomatik test taslağı.
  FastAPI'nin `/docs` endpoint'i tam bu şemayı veriyor (bonus puan!)
- **Self-healing tests**: Locator/şema değişince testin kendini onarması
  (Mabl, Testim, Functionize)
- **Risk-bazlı önceliklendirme**: AI değişen kodu vuran testleri öne alıyor —
  pipeline'da boş yere uzun süre koşmuyor
- **Triaj**: Test kırmızı düşünce LLM log + diff'i okuyup kök neden öneriyor
- **Riskler**: Halüsinasyon, false positive, gözden geçirme zorunluluğu

**Demo'da açıkça söyleyebileceğin cümleler:**

> "Servisi ve test iskeletini Claude Code ile birlikte yazdım, ama her testin
> ne doğruladığını ben review ettim. Negatif testleri, e2e zincirleri ben
> tasarladım. AI bir junior takım üyesi gibi davranıyor — kıdemli mühendis hâlâ
> benim."

Bu cümle hem dürüst hem güçlü — sunumun teması zaten bu.

---

## 8. Sunum Akışı

### 8.1 Çalıştırma komutları

```bash
# 1) Temiz başla
cd ~/Desktop/testmuh
docker compose down                    # önceki state'i temizle
docker compose up -d --build           # build + ayağa kaldır
docker compose ps                      # container "healthy" mi göster

# 2) Servisin var olduğunu kanıtla
curl http://localhost:8002/health      # {"status":"ok"}
# Tarayıcıda: http://localhost:8002/docs (Swagger UI — bonus puan)

# 3) Testleri koş
cd tests
mvn test                               # 9 test, BUILD SUCCESS

# 4) Rapor göster
ls target/surefire-reports/            # XML + TXT rapor dosyaları
```

### 8.2 Bilerek kırma demosu

Regresyonun gerçekten yakaladığını göstermek için:

1. [HealthTest.java](../tests/src/test/java/com/testmuh/deployments/HealthTest.java)
   içindeki `equalTo("ok")` → `equalTo("OK")` yap
2. `mvn test` koş → kırmızı (`HealthTest` failed, expected "OK" but got "ok")
3. Geri al → tekrar koş → yeşil
4. "Regresyon testi gerçekten çalışıyor, yanlış bir değişikliği yakaladı" de.

### 8.3 Hocaya söyleyebileceğin etkili cümleler

> "Test ettiğim servisi de ben yazdım — DevOps gerçekliğinde test mühendisi
> sıklıkla hem servisi hem testini gözden geçirir."

> "Mimari kasıtlı olarak ayrık: Python servis, Java test sürücüsü. Black-box
> API testing yaklaşımı bunu zorunlu kılıyor — testler servisin içine bakmıyor,
> sadece HTTP konuşuyor."

> "9 testin 3'ü negatif senaryo, 2'si end-to-end zincir. Yani sadece happy path
> değil, **bug yakalamayı garanti eden** kontroller var."

> "Response time SLA olarak 2 saniye seçtim — gerçek production SLA'larıyla
> uyumlu büyüklük."

> "AI destekli test mühendisliği bölümünde anlattığım gibi, kodun bir kısmını
> Claude Code ile birlikte yazdım. Mühendisin yeni rolü AI çıktısını review edip
> kaliteyi sahiplenmek — bu projede tam olarak yaptığım şey bu."

### 8.4 Olası soru-cevap

**"Neden Petstore değil, kendi servisini yazdın?"**
> "Hem ödev metni kendi servise izin veriyor, hem DevOps temasını yapay yapmamak
> için. Petstore evcil hayvan dükkanı, benim hikayem CI/CD pipeline'larında
> deployment kayıtları."

**"In-memory store gerçek hayatta olur mu?"**
> "Hayır, gerçekte Postgres veya Redis olurdu. Burada test izolasyonu ve
> sunum basitliği için kasıtlı bir seçim. Pydantic modelini değiştirmeden DB'ye
> geçilebilir."

**"AI yazdı, sen sadece kopyaladın mı?"**
> "AI iskelet ve taslak verdi; e2e zincirleri, negatif test stratejisini,
> response time SLA değerini, FastAPI status code seçimlerini ben kararlaştırdım.
> Sunumun B bölümünde tam olarak bu konuyu işliyorum — AI bir takım üyesi,
> kalite hâlâ mühendisin sorumluluğu."

**"Niye 201, 204, 422 farklı kodlar?"**
> "REST semantik standardı (RFC 7231). POST yeni kaynak yarattığı için 201,
> DELETE body döndürmediği için 204, validation hatası için 422 (ki bu FastAPI
> default'u)."

**"Response time 2 saniye nasıl seçildi?"**
> "Gerçek prod SLA'larında 99. yüzdebirlik için yaygın bir hedef. Burada
> in-memory store'la çok hızlı, ama gerçek DB olsaydı bile bu eşiğin altında
> kalmamız gerekir — testin değeri burada."

---

## 9. Dosya Haritası (Tek Bakışta)

```
testmuh/
├── docker-compose.yml          # Servisi tek komutla ayağa kaldıran orkestra
├── .gitignore                  # Git'in görmezden geleceği dosyalar
├── README.md                   # Projenin tanıtımı + çalıştırma adımları
│
├── service/                    # ── PYTHON FASTAPI SERVİSİ ──
│   ├── Dockerfile              # Container imajı reçetesi
│   ├── requirements.txt        # Python bağımlılıkları (sabit versiyon)
│   └── app/
│       ├── main.py             # FastAPI uygulaması, 5 endpoint
│       └── store.py            # In-memory veri ambarı
│
├── tests/                      # ── JAVA MAVEN TEST PROJESİ ──
│   ├── pom.xml                 # Maven konfigürasyonu + bağımlılıklar
│   └── src/test/
│       ├── java/com/testmuh/deployments/
│       │   ├── BaseTest.java                # Ortak Rest Assured kurulumu
│       │   ├── HealthTest.java              # /health endpoint testi (1 test)
│       │   ├── DeploymentGetTest.java       # GET pozitif + negatif (2 test)
│       │   ├── DeploymentPostTest.java      # POST + e2e + 422 (3 test)
│       │   └── DeploymentDeleteTest.java    # DELETE + zincir + negatif (3 test)
│       └── resources/testdata/
│           └── yeni-deployment.json         # POST için örnek body
│
├── sunum/
│   ├── sunum.md                # Marp Markdown — slayt kaynağı
│   └── sunum.pptx              # PowerPoint export
│
└── docs/                       # ── BU DOKÜMANTASYON ──
    ├── PROJE-OZETI.md          # Yaptıklarımızın özeti
    └── TEKNIK-ANLATIM.md       # (bu dosya)
```

Toplam: **9 Rest Assured testi**, hepsi yeşil, BUILD SUCCESS.
