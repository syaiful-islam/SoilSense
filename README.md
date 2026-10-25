# SoilSense — pembaca sensor tanah 7-in-1 (CWT-SOIL-NPKPHCTH-S) via USB-RS485

Aplikasi Android (Kotlin + Jetpack Compose) yang membaca probe tanah ComWinTop 7-in-1 lewat adaptor
USB-RS485 yang dicolokkan ke ponsel dengan OTG. Menampilkan kelembapan, suhu, EC, pH, N, P, K
(plus salinitas & TDS bila firmware probe mendukung), merekam riwayat, dan ekspor CSV.

## Perangkat keras

| Kabel sensor | Sambungkan ke |
|---|---|
| Cokelat (V+) | Catu daya **5–24 V DC (+)** (datasheet: 4,5–30 V) |
| Hitam (GND) | Catu daya (−) **dan** GND adaptor (referensi bersama) |
| Kuning (A+) | Terminal A+ / 485+ adaptor |
| Biru (B−) | Terminal B− / 485− adaptor |

- Adaptor biru di foto memakai chip **CH340** (USB VID:PID `1A86:7523`), didukung langsung.
- Ponsel harus mendukung **USB OTG (host)**. Sensor hanya 0,5 W, tetapi tetap sarankan catu daya
  eksternal (mis. adaptor 12 V) agar pembacaan stabil; jangan ambil daya sensor dari ponsel kecuali
  adaptor Anda memang punya terminal 5 V keluaran.
- Default sensor: **ID 1, 4800 bps, 8N1**.
- Jika "Sensor tidak merespons": cek daya, **tukar A/B**, cek ID & baud rate (menu Pengaturan →
  *Deteksi ID* memakai alamat 0xFF dari datasheet).

## Protokol (dari manual V1.4)

- Modbus RTU, fungsi **0x03** (baca) dan **0x06** (tulis). Tabel di manual menulis "0x30/0x60",
  tetapi semua contoh frame di manual memakai 0x03/0x06 — itulah yang dipakai.
- Register 0x0000–0x0008: kelembapan (0,1 %), suhu (0,1 °C, *signed*), EC (µS/cm), pH (0,1),
  N, P, K (mg/kg), salinitas, TDS (mg/L).
- Aplikasi membaca 9 register; bila probe menolak/diam, otomatis kembali ke 7 register.
- Semua 9 contoh frame di manual sudah dicek CRC-nya dan dijadikan test unit.

## Arsitektur

```
:core:modbus   Kotlin/JVM murni — CRC16, ModbusRtuClient, SoilSensor (decoder + fallback 9→7 register)
:app           Android — Compose UI, Hilt, Room, DataStore, usb-serial-for-android
  data/usb       UsbSerialConnector (izin USB, hot-plug, buka port), UsbSerialPortTransport
  data/sensor    UsbSoilSensorRepository — state koneksi + polling Flow (berhenti saat UI tidak aktif)
  data/history   Room (riwayat) + ekspor CSV via Storage Access Framework
  data/settings  DataStore (ID slave, baud, interval, rekam)
  ui/            Dashboard (Langsung), Riwayat, Pengaturan — NavigationSuiteScaffold adaptif
```

Polling hanya berjalan selama layar Langsung terbuka (`WhileSubscribed(5 s)`), sehingga tidak
menguras baterai di latar belakang. Perekaman di latar belakang (foreground service) belum dibuat.

## Build

Butuh Android Studio terbaru (mendukung AGP 9.4) / JDK 17+.

```bash
./gradlew :core:modbus:test :app:testDebugUnitTest   # unit test
./gradlew :app:lintDebug
./gradlew :app:assembleDebug                         # APK di app/build/outputs/apk/debug/
./gradlew :app:connectedDebugAndroidTest             # Room + Compose UI test (butuh perangkat)
```

Atau push ke GitHub: workflow `.github/workflows/android.yml` menjalankan test, lint, build, dan
mengunggah APK sebagai artifact.

| Komponen | Versi |
|---|---|
| AGP / Gradle | 9.4.0 / 9.8.2 |
| Kotlin / KSP | 2.3.21 / 2.3.12 |
| Compose BOM | 2026.09.00 |
| compileSdk / targetSdk / minSdk | 37 / 36 / 24 |
| Hilt | 2.60.1 |
| Room / DataStore | 2.8.5 / 1.3.0 |
| usb-serial-for-android | 3.11.0 (JitPack) |

`applicationId` = `id.soilsense.app` — ganti sebelum rilis pertama ke Play Store bila perlu.
