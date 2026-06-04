---
marp: true
theme: default
paginate: true
size: 16:9
header: 'Yazılım Test Mühendisliği — Proje Ödevi'
footer: 'Levent Kutay Sezer'
style: |
  section {
    font-size: 26px;
  }
  h1 { color: #1f3a93; }
  h2 { color: #1f3a93; }
  code { font-size: 22px; }
  table { font-size: 22px; }
---

<!-- _class: lead -->
# DevOps Pipeline'larında<br>API Regresyon Testleri ve<br>AI Destekli Test Mühendisliği

**Levent Kutay Sezer**
Yazılım Test Mühendisliği — Proje Ödevi

GitHub: `github.com/<kullanici>/testmuh` *(repo URL'i en sona da koyacağım)*

---

## Sunum Akışı

1. **Yazılım Test Mühendisliği nedir, neden var?**
2. Test piramidi ve **API testlerinin yeri**
3. **Regresyon testi** ve otomasyon zorunluluğu
4. **DevOps & CI/CD** pipeline'da testin konumu
5. **Rest Assured** — Java ekosisteminin standardı
6. **AI Destekli Test Mühendisliği** — bugünkü manzara
7. **Proje Demo** — kod + canlı çalıştırma

---

## Yazılım Test Mühendisliği Nedir?

> Yazılımın **beklenen davranışı sergilediğini** ve **beklenmeyen
> davranışlardan kaçındığını** sistematik biçimde doğrulayan disiplin.

**Neden var?**
- Yazılım karmaşıklığı her sürümde artar
- Hatanın geç bulunması, erken bulunmasından **10-100x** daha pahalıdır
- Manuel test ölçeklenmez — sürekli teslimat (CI/CD) testin otomatize olmasını **zorunlu kılar**

**Test Mühendisi ≠ "manuel tıklayıcı"**
Test stratejisi tasarlayan, otomasyon kuran, kaliteye sahiplenen mühendis.

---

## Test Piramidi

```
                      /\
                     /e2\           ← yavaş, kırılgan, az sayıda
                    /UI  \
                   /------\
                  /  API   \        ← TATLI NOKTA: hızlı + güvenilir
                 /integration\
                /-------------\
               /     Unit       \   ← çok sayıda, milisaniye seviyesi
              /__________________\
```

- **Unit**: tek fonksiyon, izole, ms cinsinden
- **Integration / API**: servisler arası sözleşme
- **End-to-End (UI)**: tüm sistem, en pahalısı

API testleri, **maliyet/değer oranı en yüksek** olan katmandır.

---

## Regresyon Testi Nedir?

**Regresyon** = "geri gitme" — daha önce çalışan bir özelliğin, yeni bir değişiklik
yüzünden bozulması.

- Her yeni feature, var olan bir özelliği **istemeden kırabilir**
- Manuel olarak her sürümde her özelliği yeniden test etmek **imkansız**
- **Otomatik regresyon suite'i** = her commit'te koşan güvenlik ağı

**İyi bir regresyon test suite'i:**
- Hızlı çalışmalı (geliştiriciyi yavaşlatmamalı)
- Deterministik olmalı (flaky değil)
- Kritik iş akışlarını kapsamalı (her edge case'i değil)

---

## DevOps & CI/CD'de Testin Yeri

```
   Geliştirici → commit → CI Pipeline
                            │
                            ├─ 1. Lint / Static Analysis
                            ├─ 2. Unit Tests        (saniyeler)
                            ├─ 3. Build / Package
                            ├─ 4. Container Image
                            ├─ 5. API/Integration Tests   ← BURADAYIZ
                            ├─ 6. Deploy to staging
                            └─ 7. E2E Smoke Tests
```

**Anahtar kavramlar:**
- **Shift-left**: testi geliştiriciye en yakın yere taşı
- **Fail-fast**: hatalı build canlıya gitmesin
- **Pipeline gating**: testler yeşil değilse merge yok

---

## Containerized Testing — Bizim Projemiz

Test edilen servis ve test koşucusu **bağımsız container'lar** halinde:

```
+------------------------------+        +------------------------------+
| Rest Assured Test Sürücüsü   |  HTTP  | Deployment Tracker API       |
| Java 17 + Maven + JUnit 5    +------->+ Python + FastAPI (Docker)    |
+------------------------------+        +------------------------------+
```

**Avantajları:**
- Her ortamda **aynı şekilde** çalışır (laptop = CI = staging)
- Servisi `docker compose up` ile tek komutta ayağa kaldırırız
- Testler `localhost:8002`'e konuşur — gerçek HTTP, mock değil

---

## Rest Assured Nedir?

Java için **fiili standart** REST API test kütüphanesi. BDD-tarzı okunabilir sözdizimi:

```java
given()
    .contentType(JSON)
    .body(yeniDeployment)
.when()
    .post("/deployments")
.then()
    .statusCode(201)
    .time(lessThan(2000L))
    .body("service", equalTo("payment-api"))
    .body("id", notNullValue());
```

- HTTP isteği, doğrulama, raporlama → **tek bir akış**
- JUnit, TestNG, Maven, Gradle ile sorunsuz entegre
- JSON Path + Hamcrest matchers ile esnek body doğrulama

---

## AI Destekli Test Mühendisliği — Bugün

Test mühendisliğinde AI artık deneysel değil, **takım üyesi**.

| Alan | Klasik | AI Destekli |
|---|---|---|
| Test case yazma | Mühendis spec'i okur, elle yazar | LLM spec'ten taslak üretir, mühendis düzeltir |
| Locator'lar (UI) | Sabit selector, kırılgan | Self-healing — DOM değişince adapte olur |
| Test verisi | Manuel veya factory | Üretici modeller ile sınır/edge senaryo |
| Önceliklendirme | "Hep aynı suite" | Risk-bazlı: değişen kodu vuran testleri öne al |
| Hata triyajı | Mühendis log okur | LLM log+diff özetler, kök neden önerir |

---

## LLM ile Test Üretimi — Pratik

**Girdi:** OpenAPI/Swagger şeması veya servis kodu
**Çıktı:** Çalışan test taslakları + edge case önerileri

```
[Geliştirici]    "Bu endpoint için Rest Assured testi yaz, status, body
                  ve response time kontrolleri olsun, negatif case da ekle"
       │
       ▼
[LLM (Claude)]   PetGetTest.java, DeploymentPostTest.java taslakları
       │
       ▼
[Mühendis]       Kodu okur, projeye uyarlar, edge case'leri tamamlar
```

**Önemli:** AI ilk taslağı verir, **doğrulama hâlâ mühendisin sorumluluğu**.

---

## Self-Healing Tests

UI/API şeması değişti, test eski locator/alanı arıyor → klasik suite kırmızı.
**Self-healing araç** (Mabl, Testim, Functionize):

1. Eski locator/alanı bulamadı
2. Benzerlik (semantic + DOM) ile **muhtemel yeni karşılığı** buldu
3. Testi **kendi başına güncelledi**, mühendise notify etti

**Risk:** otomatik onarım yanlış olabilir → mutlaka **insan onayı**.
Bu yüzden AI'ı **tamamlayıcı** olarak görmek lazım, **ikame** olarak değil.

---

## AI Araç Manzarası (2026)

| Araç | Güçlü olduğu yer |
|---|---|
| **GitHub Copilot / Claude Code** | Kod içinde test taslağı üretimi, refactor |
| **Testim, Mabl, Functionize** | UI testleri için self-healing |
| **Diffblue Cover** | Java unit test otomatik üretimi |
| **Applitools Visual AI** | Görsel regresyon (pixel değil, anlam) |
| **CodiumAI / Qodo** | IDE içinde "test bu kodu kapsıyor mu?" analizi |

> Bizim projede testleri yazarken Claude Code'dan **iskelet, edge case
> önerisi, Türkçe yorum üretimi** için faydalandık.

---

## Riskler & Sınırlar

- **Halüsinasyon** — LLM olmayan bir alan/endpoint uydurabilir
- **False positive / negative** — test geçti diye ürün doğru çalışıyor demek değil
- **Aşırı güven** — "AI yazdı, kontrol etmedim" = canlıda bug
- **Veri gizliliği** — kapalı kaynak/üretim verisi LLM'e gönderilmemeli

**Pratik kural:**
> AI bir **junior takım üyesi** gibi davranır:
> hızlı, üretken ama her çıktısı **kıdemli mühendis tarafından review** edilmeli.

---

## Proje — Dizin Yapısı

```
testmuh/
├── docker-compose.yml
├── service/                       # FastAPI mini-servis
│   ├── Dockerfile
│   ├── requirements.txt
│   └── app/main.py, store.py
├── tests/                         # Java/Maven test projesi
│   ├── pom.xml
│   └── src/test/java/com/testmuh/deployments/
│       ├── BaseTest.java
│       ├── HealthTest.java
│       ├── DeploymentGetTest.java
│       ├── DeploymentPostTest.java
│       └── DeploymentDeleteTest.java
└── sunum/sunum.md / sunum.pptx
```

---

## Proje — Test Akışı (9 test, **CRUD'in dört köşesi**)

| # | Test | Kontroller |
|---|---|---|
| 1 | `HealthTest.health_endpoint_ok_donmeli` | 200 · `status=ok` · `<1000ms` |
| 2 | `DeploymentGetTest.deployment_listesi_alinabilmeli` | 200 · array · `<2000ms` |
| 3 | `DeploymentGetTest.var_olmayan_deployment_404_donmeli` | 404 · hata mesajı |
| 4 | `DeploymentPostTest.yeni_deployment_kaydedilebilmeli` | 201 · body alanları · `<2000ms` |
| 5 | `DeploymentPostTest.eklenen_deployment_get_ile_dogrulanabilmeli` | POST→GET zinciri |
| 6 | `DeploymentPostTest.eksik_alan_gonderilince_422_donmeli` | 422 (negatif) |
| 7 | `DeploymentDeleteTest.var_olan_deployment_silinebilmeli` | 204 · `<2000ms` |
| 8 | `DeploymentDeleteTest.silinen_deployment_get_ile_bulunamamali` | DELETE → GET 404 zinciri |
| 9 | `DeploymentDeleteTest.var_olmayan_deployment_silinmeye_calisilinca_404_donmeli` | 404 (negatif) |

Ödev gereği: **status + body + response time + GET + POST + request body** → hepsi karşılanıyor (+ DELETE bonus).

---

## Canlı Demo

**1) Servisi ayağa kaldır**
```bash
docker compose up -d --build
curl http://localhost:8002/health
```

**2) Testleri koş**
```bash
cd tests && mvn test
```

**3) Bilerek bir testi kır → kırmızı → düzelt → yeşil**
"Regresyon testi gerçekten yakalıyor mu?" canlı kanıt.

---

## Özet

- API regresyon testleri, **DevOps pipeline'ının kalp atışıdır**
- **Rest Assured + JUnit 5** Java ekosisteminin pratik standardıdır
- **AI**, test mühendisini değiştirmiyor — **güçlendiriyor**: iskelet, edge case,
  triaj, refactor önerileri
- Test mühendisinin yeni rolü: **AI çıktısını eleştirel okuyup kaliteyi sahiplenmek**

---

<!-- _class: lead -->
## Teşekkürler

**GitHub Repo:**
`github.com/<kullanici>/testmuh`

Sorular?
