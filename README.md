# 📱 Live Screen Translator Overlay (Anlık Ekran Çevirmeni)

Bu proje, cihazınızdaki herhangi bir uygulamayı (sosyal medya, mesajlaşma uygulamaları, PDF'ler, web siteleri veya videolar) kullanırken dil bariyerini ortadan kaldırmak için tasarlanmış, **tamamen çevrimdışı (offline)** çalışan ve sistem kaynaklarını (RAM/CPU) minimum seviyede tüketen native bir Android "Overlay" (Ekran Üzeri Görünüm) uygulamasıdır.

## 🚀 Özellikler

* **Hedefli Metin Algılama (Smart Crop):** Ekranda sürüklenebilir ve yeniden boyutlandırılabilir yeşil bir "hedef çerçevesi" bulunur. Uygulama, tüm ekranı okumak yerine sadece bu çerçevenin içindeki metinleri işleyerek maksimum performans sağlar.
* **Çevrimdışı OCR & Çeviri:** Google ML Kit altyapısı kullanılarak metin algılama (OCR) ve İngilizce-Türkçe çeviri işlemleri tamamen cihaz üzerinde (on-device) gerçekleşir. İnternet paketinizi harcamaz ve metinlerinizi sunuculara göndermediği için gizliliğinizi korur.
* **Akıllı UX & Gizlenebilir Çubuk:** Sürekli ekranda durup ekran görüşünüzü engellememesi için, aktif kullanılmadığı zamanlarda (kullanıcının belirlediği süre sonunda) ekranın kenarına yaslanarak ince, şeffaf bir çubuğa dönüşür (Collapse).
* **Özelleştirilebilir Arayüz:** Çubuğa dönüşme süresi ve bekleme anındaki saydamlık (opaklık) seviyesi ana menüden ayarlanıp hafızaya kaydedilir.
* **Düşük RAM Tüketimi:** Native Kotlin ve Foreground Service kullanılarak geliştirildiği için arka planda sistemi yormaz. Telefonların yerleşik "Kenar Çubuğu (Edge Panel)" özellikleri ile tam uyumlu çalışır.

## 🛠️ Kullanılan Teknolojiler

* **Dil:** Kotlin (Native Android)
* **API Seviyesi:** Android 11+ (API 30 ve üzeri)
* **Ekran Yakalama:** MediaProjection API (Gerçek piksel metrikleri ile kaymasız ekran alıntısı)
* **Görüntü İşleme ve Yapay Zeka:** * `com.google.android.gms:play-services-mlkit-text-recognition` (Metin Algılama)
  * `com.google.mlkit:translate` (Yerel Çeviri Modeli)
* **Arayüz Yönetimi:** WindowManager (Sürüklenebilir System Alert Window mimarisi)

## 📱 Nasıl Çalışır?

1. Uygulamayı başlatıp gerekli ekran yakalama ve "diğer uygulamaların üzerinde gösterme" izinlerini verin.
2. Ekranda beliren turuncu baloncuğu ve şeffaf yeşil çerçeveyi, okutmak istediğiniz metnin (örneğin bir mesajlaşma kutusu, makale veya görsel) üzerine konumlandırın.
3. Çerçeveyi sağ alt köşesinden tutarak okutmak istediğiniz alanın boyutuna göre ayarlayın.
4. Turuncu balona bir kez dokunun. Sadece yeşil alanın içindeki yabancı metinler anında Türkçeye çevrilip şık bir siyah kutu içerisinde ekranda belirecektir.
5. Sonuç kutusunu kapatmak için üzerine bir kez dokunun. Yeşil çerçeveyi tamamen gizlemek veya açmak için turuncu balona **uzun basın**.
