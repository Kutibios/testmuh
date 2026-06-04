# Proje Özeti — Şu Ana Kadar Ne Yaptık?

Bu dosya, **Yazılım Test Mühendisliği** ödevi için sıfırdan kurduğumuz projenin
zaman çizelgesi formatında özetidir. Hangi kararı neden aldığımız, hangi dosyayı
hangi aşamada ürettiğimiz ve mevcut durumun tam olarak ne olduğu burada yazılı.

---

## 1. Başlangıç: Ödev metni ve hedefler

Ödevin gerekleri:

1. **Sunum** — Yazılım Test Mühendisliği + AI destekli test mühendisliği üzerine.
2. **Kod projesi** — Java + Maven + JUnit + Rest Assured ile bir REST servisin
   otomatik regresyon testleri.
3. **Test gerekleri** — En az bir istekte:
   - Status code kontrolü
   - Response body değer kontrolleri
   - Response time (x süre altında cevap) kontrolü
   - İdealde GET ve POST'tan en az birer örnek, en az birinde request body
4. **Demo** — Kodu çalıştırıp testin nasıl yazıldığını anlatmak.
5. **Teslim** — Public GitHub repo + sunum dosyası.

---

## 2. Karar Noktaları

Sıfırdan başlarken aldığımız temel kararlar:

### 2.1 Sunum teması
**"DevOps Pipeline'larında API Regresyon Testleri ve AI Destekli Test Mühendisliği"**

- Klasik test mühendisliği temelleri ~60%
- AI destekli test mühendisliği ~40%
- DevOps teması her iki yarıya da nüfuz ediyor (CI/CD pipeline'da testin yeri,
  containerized testing, shift-left)

### 2.2 Test edilen servis — neden Petstore değil, kendi servisimiz?
İlk öneri Petstore Swagger idi (public, klasik). Kullanıcı haklı olarak iki şey
sordu:
1. "Başkalarıyla aynı olmasın" — Petstore çok yaygın, ayırt edici değil.
2. "DevOps nerde?" — Petstore "evcil hayvan dükkanı", DevOps'la bağlantı zayıf.

Karar: **Kendi yazacağımız mini "Deployment Tracker" servisi**. CI/CD
pipeline'ları bir deployment yapınca bu API'ye kaydeder; gerçek hayatta Argo
Rollouts, Spinnaker, Backstage benzeri araçların yaptığı işin minik bir
örneği. Bu seçim:
- "Kendi geliştirdiğim servise yazdım" demeyi mümkün kılıyor (ödev metni
  bunu açıkça izin veriyor).
- DevOps temasını yapay değil organik kılıyor.
- Başkasıyla aynı olma ihtimalini sıfıra indiriyor.

### 2.3 Mini-API teknolojisi: Python + FastAPI
Seçenekler: Python/FastAPI, Java/Spring Boot, Go/Gin, Node/Express.

**FastAPI tercih edildi** çünkü:
- 30-40 satır kodla full CRUD servis kurulabiliyor.
- Otomatik Swagger UI (`/docs`) bonus puan getiriyor.
- Pydantic validation ile 422 negatif test örneği bedavaya geliyor.
- Test sürücüsü zaten Java/Rest Assured, **dil farklılığı black-box testing
  hikayesini güçlendiriyor** — testler servisin içine bakmıyor, sadece HTTP
  konuşuyor.

### 2.4 Containerization: Docker + docker-compose
Demo'da:
- Servis Docker container'ında çalışsın → ortamdan bağımsız, tekrarlanabilir.
- `docker compose up -d --build` tek komutla aya kalksın.
- Healthcheck eklensin (Kubernetes liveness/readiness probe'larının küçük
  versiyonu).

### 2.5 Sunum formatı: Marp (Markdown → PPTX)
- Markdown kaynağı versiyon kontrol altında tutulabiliyor.
- Docker imajı `marpteam/marp-cli` ile Node kurmaya gerek kalmadan PPTX
  üretilebiliyor.
- Kullanıcı PowerPoint/LibreOffice'te sonradan düzenleyebiliyor.

### 2.6 DELETE endpoint sonradan eklendi
Bir arkadaşın projesi karşılaştırması yapıldı (`anilsrml/Software-Testing-Project`).
Arkadaşının jsonplaceholder'a karşı GET + POST + DELETE testi vardı; bizde ilk
versiyonda DELETE yoktu. Eksiklik olmasın diye **DELETE endpoint'i ve 3 yeni
test sınıfı eklendi**.

---

## 3. Üretilen Dosyalar (Aşama Aşama)

### Aşama 1: Dizin yapısı
```
testmuh/
├── service/app/
├── tests/src/test/java/com/testmuh/deployments/
├── tests/src/test/resources/testdata/
└── sunum/img/
```

### Aşama 2: FastAPI mini-servisi
| Dosya | İçerik |
|---|---|
| [service/requirements.txt](../service/requirements.txt) | FastAPI 0.115.5, Uvicorn 0.32.1, Pydantic 2.10.3 (versiyonlar pinned) |
| [service/app/store.py](../service/app/store.py) | In-memory `DeploymentStore` sınıfı, singleton instance |
| [service/app/main.py](../service/app/main.py) | FastAPI uygulaması, Pydantic modelleri, 5 endpoint |
| [service/Dockerfile](../service/Dockerfile) | Python 3.12-slim, uvicorn, healthcheck, EXPOSE 8002 |
| [docker-compose.yml](../docker-compose.yml) | Tek servis, port 8002:8002, healthcheck, restart policy |

**İlk smoke test**: `docker compose up -d --build` → `curl localhost:8002/health` →
`{"status":"ok"}`, response time 2.8ms.

### Aşama 3: Maven test projesi
| Dosya | İçerik |
|---|---|
| [tests/pom.xml](../tests/pom.xml) | Rest Assured 5.4.0, JUnit 5.10.2, Hamcrest 2.2, Surefire 3.2.5, Java 17 |
| [tests/src/test/java/com/testmuh/deployments/BaseTest.java](../tests/src/test/java/com/testmuh/deployments/BaseTest.java) | `@BeforeAll` global setup, requestSpec/responseSpec, logging filters |
| [tests/src/test/resources/testdata/yeni-deployment.json](../tests/src/test/resources/testdata/yeni-deployment.json) | POST için örnek body |

### Aşama 4: İlk test sınıfları (6 test)
| Sınıf | Test sayısı | Kapsama |
|---|---|---|
| [HealthTest.java](../tests/src/test/java/com/testmuh/deployments/HealthTest.java) | 1 | GET /health, 200, status=ok, <1000ms |
| [DeploymentGetTest.java](../tests/src/test/java/com/testmuh/deployments/DeploymentGetTest.java) | 2 | Liste alma (pozitif) + 404 (negatif) |
| [DeploymentPostTest.java](../tests/src/test/java/com/testmuh/deployments/DeploymentPostTest.java) | 3 | Kayıt ekleme + POST→GET zinciri + 422 (negatif) |

**İlk koşum**: `mvn test` → `Tests run: 6, Failures: 0, Errors: 0` → BUILD SUCCESS.

### Aşama 5: Destek dosyaları
| Dosya | İçerik |
|---|---|
| [README.md](../README.md) | Türkçe; mimari, önkoşullar, çalıştırma, test tablosu, ödev gereği eşlemesi |
| [.gitignore](../.gitignore) | `target/`, `__pycache__/`, `.idea/`, `.vscode/`, `node_modules/` vb. |

### Aşama 6: Sunum (~18 slayt, Türkçe)
| Dosya | İçerik |
|---|---|
| [sunum/sunum.md](../sunum/sunum.md) | Marp Markdown — kaynak |
| [sunum/sunum.pptx](../sunum/sunum.pptx) | Docker üzerinden marpteam/marp-cli ile üretildi (~3 MB) |

Slayt akışı:
- **Bölüm A** (60%): Test mühendisliği temelleri + DevOps & CI/CD pipeline
- **Bölüm B** (40%): AI destekli test (LLM ile test üretimi, self-healing,
  araç manzarası, riskler)
- **Bölüm C**: Proje demo (mimari, komutlar, test akışı)

### Aşama 7: DELETE endpoint eklendi (arkadaş repo karşılaştırması sonrası)
| Değişiklik | Dosya |
|---|---|
| `DeploymentStore.delete()` metodu eklendi | [service/app/store.py](../service/app/store.py) |
| `DELETE /deployments/{id}` endpoint'i eklendi (204/404) | [service/app/main.py](../service/app/main.py) |
| Yeni test sınıfı: 3 test | [DeploymentDeleteTest.java](../tests/src/test/java/com/testmuh/deployments/DeploymentDeleteTest.java) |

**Yeni testler**:
1. `var_olan_deployment_silinebilmeli` — 204, response time
2. `silinen_deployment_get_ile_bulunamamali` — DELETE→GET 404 zinciri
3. `var_olmayan_deployment_silinmeye_calisilinca_404_donmeli` — negatif

**Güncel koşum**: `mvn test` → `Tests run: 9, Failures: 0, Errors: 0` → BUILD SUCCESS.

### Aşama 8: Dokümantasyon güncellemesi
- README.md ve sunum.md DELETE testlerini yansıtacak şekilde güncellendi.
- sunum.pptx yeniden üretildi.

---

## 4. Mevcut Durum

### Çalışan parçalar
- FastAPI servisi Docker container'ında ayakta, `localhost:8002` üzerinden
  erişilebilir.
- Swagger UI: `http://localhost:8002/docs`
- 9 Rest Assured testi yeşil koşuyor (HealthTest 1, GetTest 2, PostTest 3,
  DeleteTest 3).
- Sunum Marp ile üretildi, PPTX hazır.

### Test kapsamı — ödev gereklerine eşleme
| Gereklilik | Karşılayan |
|---|---|
| Status code kontrolü | 9 testin hepsi |
| Response body değer kontrolü | 9 testin hepsi (Hamcrest matchers) |
| Response time kontrolü | Health + Get listesi + Post + Delete pozitifleri |
| GET örneği | HealthTest, DeploymentGetTest |
| POST + request body | DeploymentPostTest (`yeni-deployment.json`) |
| DELETE örneği (bonus) | DeploymentDeleteTest |
| Negatif testler | 404 (Get yoksa), 422 (eksik body), 404 (Delete yoksa) |
| E2E zincir | POST→GET, DELETE→GET 404 |

### Açık kalan işler
- **Git init + GitHub'a push**: Kullanıcı bunu kendi terminalinden yapacağını
  belirtti. Yapıldığında repo URL'i [sunum/sunum.md](../sunum/sunum.md)
  içindeki iki placeholder yere yazılmalı (kapak slaytı + son slayt), ardından
  sunum.pptx yeniden üretilmeli:
  ```bash
  cd ~/Desktop/testmuh
  docker run --rm --init -v "$PWD/sunum:/home/marp/app" \
      -e MARP_USER="$(id -u):$(id -g)" \
      marpteam/marp-cli sunum.md -o sunum.pptx --allow-local-files
  ```
- **Sunum provası**: Bir kere baştan sona oku, slaytlardaki cümleleri sesli
  geç.
- **Çevrimdışı yedek**: `tests/target/surefire-reports/` çıktısının ve bir
  terminal screenshot'ının ayrı bir yerde saklanması iyi olur (sunum sırasında
  internet/Docker problemi olursa).

---

## 5. Çalıştırma Akışı (Sunumda)

```bash
# 1) Temiz başla
cd ~/Desktop/testmuh
docker compose down
docker compose up -d --build

# 2) Servis ayakta mı?
curl http://localhost:8002/health        # {"status":"ok"}

# 3) Testleri koş
cd tests
mvn test                                  # Tests run: 9, BUILD SUCCESS

# 4) Sunumu aç
xdg-open ../sunum/sunum.pptx
```

**Bilerek kırma demosu** (regresyonun canlı yakaladığını göstermek için):
1. [HealthTest.java](../tests/src/test/java/com/testmuh/deployments/HealthTest.java)
   içindeki `equalTo("ok")` → `equalTo("OK")` yap.
2. `mvn test` koş → kırmızı.
3. Geri al → `mvn test` → yeşil.
4. "Regresyon testi yanlış bir değişikliği canlı yakaladı" de.

---

## 6. Mimari Hatırlatma

```
+-----------------------------+    HTTP    +-------------------------------+
|  TEST SÜRÜCÜSÜ              | ---------> | TEST EDİLEN SERVİS            |
|  Java 17 + Maven + JUnit 5  |   istek    | Python 3.12 + FastAPI         |
|  + Rest Assured             | <--------- | Docker container içinde       |
|  Konum: tests/              |   cevap    | Konum: service/               |
+-----------------------------+            +-------------------------------+
```

İki tarafın **farklı dillerde** olması black-box API testing'in doğal hâli.
Testler servisin içine bakmıyor, sadece HTTP üzerinden konuşuyor — gerçek
DevOps pipeline'larında olduğu gibi.

---

## 7. Detaylı Teknik Anlatım

Her dosyanın içindeki kodun **niye öyle yazıldığı**, hocaya nasıl anlatılacağı
ve DevOps ile bağlantısı için [TEKNIK-ANLATIM.md](TEKNIK-ANLATIM.md) dosyasına
bak.
