# Build GeoTag di Termux (HP Android)

## ⚠️ Persyaratan
- HP Android (minimal 4GB RAM recommended, 6-8GB RAM ideal)
- Storage kosong minimal 3GB
- Koneksi internet stabil
- Waktu setup: 30-60 menit pertama kali

---

## 📱 Step 1: Install Termux

1. **Download Termux dari F-Droid** (JANGAN dari Play Store, versi lama!)
   - Buka: https://f-droid.org/en/packages/com.termux/
   - Klik **Download APK** → Install

2. **Buka Termux** → tunggu sampai muncul prompt `$`

---

## 🔧 Step 2: Setup Environment

Copy-paste command ini **SATU PER SATU** (tekan Enter setelah paste):

```bash
# Update packages
pkg update -y && pkg upgrade -y

# Install tools yang dibutuhkan (sekitar 10-15 menit)
pkg install -y git openjdk-17 wget

# Buat direktori kerja
mkdir -p ~/projects
cd ~/projects

# Clone repository
git clone https://github.com/0xHambaAllah/GeoTag.git
cd GeoTag
```

---

## 🔑 Step 3: Generate Keystore (PENTING!)

Ini akan membuat keystore yang SHA-1-nya bisa kita ambil:

```bash
# Generate keystore
keytool -genkeypair -v \
  -keystore ~/geotag-release.keystore \
  -alias geotag \
  -keyalg RSA \
  -keysize 2048 \
  -validity 10000 \
  -storepass geotag123 \
  -keypass geotag123 \
  -dname "CN=GeoTag,OU=Mobile,O=GeoTag,L=Jakarta,ST=Jakarta,C=ID"
```

**CATAT password ini:**
- Store password: `geotag123`
- Key password: `geotag123`
- Alias: `geotag`

---

## 🔍 Step 4: Extract SHA-1

Ambil SHA-1 fingerprint dari keystore yang baru dibuat:

```bash
keytool -list -v -keystore ~/geotag-release.keystore -storepass geotag123 | grep SHA1:
```

**Output akan seperti:**
```
SHA1: AB:CD:EF:12:34:56:78:90:AB:CD:EF:12:34:56:78:90:AB:CD:EF:12
```

**COPY SHA-1 ini!** Kamu perlu tambahkan ke Firebase Console.

---

## 🔥 Step 5: Update Firebase dengan SHA-1 Baru

1. **Tambahkan SHA-1 ke Firebase:**
   - Buka: https://console.firebase.google.com/project/maps-642b7/settings/general
   - Scroll ke **"Your apps"** → `com.geotag.app`
   - Klik **"Add fingerprint"**
   - Paste SHA-1 dari step 4
   - **Save**

2. **Download google-services.json terbaru**
   - Di halaman yang sama, klik download icon untuk `com.geotag.app`
   - Save file

3. **Upload google-services.json ke Termux:**
   - Ada 2 cara:
   
   **Cara A: Via termux-setup-storage (recommended)**
   ```bash
   # Di Termux, jalankan:
   termux-setup-storage
   # Izinkan akses storage saat popup muncul
   
   # Copy google-services.json dari Downloads ke project
   cp ~/storage/downloads/google-services.json ~/projects/GeoTag/app/
   ```
   
   **Cara B: Via GitHub (lebih lambat)**
   - Upload google-services.json ke GitHub Gist atau repo lain
   - Download di Termux dengan wget:
   ```bash
   cd ~/projects/GeoTag/app/
   wget -O google-services.json "URL_FILE"
   ```

---

## 🏗️ Step 6: Configure Signing

Buat file konfigurasi untuk signing:

```bash
# Buat keystore.properties
cat > ~/projects/GeoTag/keystore.properties << 'EOF'
storeFile=/data/data/com.termux/files/home/geotag-release.keystore
storePassword=geotag123
keyAlias=geotag
keyPassword=geotag123
EOF
```

Update `build.gradle.kts` untuk pakai keystore:

```bash
cd ~/projects/GeoTag/app

# Backup original
cp build.gradle.kts build.gradle.kts.backup

# Tambahkan signing config (manual edit diperlukan)
nano build.gradle.kts
```

Tambahkan ini di dalam `android { }` block (setelah `compileSdk`):

```kotlin
    signingConfigs {
        create("release") {
            val keystorePropertiesFile = rootProject.file("keystore.properties")
            val keystoreProperties = java.util.Properties()
            if (keystorePropertiesFile.exists()) {
                keystoreProperties.load(java.io.FileInputStream(keystorePropertiesFile))
                storeFile = file(keystoreProperties["storeFile"] as String)
                storePassword = keystoreProperties["storePassword"] as String
                keyAlias = keystoreProperties["keyAlias"] as String
                keyPassword = keystoreProperties["keyPassword"] as String
            }
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("release")
        }
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
        }
    }
```

Save dengan: **Ctrl+X** → **Y** → **Enter**

---

## 🚀 Step 7: Build APK

Sekarang build APK (ini akan lama pertama kali, 20-40 menit):

```bash
cd ~/projects/GeoTag

# Berikan permission execute ke gradlew
chmod +x gradlew

# Build debug APK (lebih cepat)
./gradlew assembleDebug

# Atau build release APK (lebih optimal)
./gradlew assembleRelease
```

**Jika RAM habis atau build gagal:**
```bash
# Kurangi memory Gradle
export GRADLE_OPTS="-Xmx1536m"
./gradlew assembleDebug --no-daemon
```

---

## 📦 Step 8: Install APK

Setelah build sukses, APK ada di:
- Debug: `~/projects/GeoTag/app/build/outputs/apk/debug/app-debug.apk`
- Release: `~/projects/GeoTag/app/build/outputs/apk/release/app-release.apk`

**Install ke HP:**

```bash
# Copy APK ke storage yang bisa diakses
cp ~/projects/GeoTag/app/build/outputs/apk/debug/app-debug.apk ~/storage/downloads/GeoTag.apk

# Lalu buka File Manager → Downloads → Install GeoTag.apk
```

Atau pakai ADB jika HP terhubung ke PC:
```bash
adb install ~/projects/GeoTag/app/build/outputs/apk/debug/app-debug.apk
```

---

## ✅ Verifikasi

1. **Install APK**
2. **Buka app**
3. **Login dengan Google** → **SEHARUSNYA BERHASIL!**

Karena SHA-1 APK = SHA-1 yang didaftarkan di Firebase.

---

## 🔄 Build Ulang (Update Code)

Jika ada perubahan code:

```bash
cd ~/projects/GeoTag
git pull origin main
./gradlew assembleDebug
cp app/build/outputs/apk/debug/app-debug.apk ~/storage/downloads/GeoTag-new.apk
```

---

## 🆘 Troubleshooting

### Build Gagal - Out of Memory
```bash
# Tutup app lain
# Gunakan --no-daemon
export GRADLE_OPTS="-Xmx1024m"
./gradlew assembleDebug --no-daemon
```

### Build Gagal - Dependency Error
```bash
# Clear cache
./gradlew clean
rm -rf ~/.gradle/caches
./gradlew assembleDebug
```

### SHA-1 Tidak Match
```bash
# Extract SHA-1 dari APK yang sudah dibuild
unzip -p app/build/outputs/apk/debug/app-debug.apk META-INF/CERT.RSA | keytool -printcert | grep SHA1:
```

Copy SHA-1 yang muncul dan daftarkan ke Firebase.

---

## 📝 Catatan

- **Keystore di `~/geotag-release.keystore`** → BACKUP FILE INI! Jangan hilang!
- **Password: `geotag123`** → Catat di tempat aman
- Build pertama lama (download dependencies ~1GB)
- Build selanjutnya lebih cepat (5-10 menit)

**Jika berhasil build & login, kamu tidak perlu GitHub Actions lagi!** ✅
