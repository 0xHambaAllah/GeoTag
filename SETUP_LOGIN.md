# Fix Google Sign-In

## Problem
Login Google gagal - setelah pilih akun tetap di menu login.

## Root Cause
SHA-1 fingerprint belum ditambahkan ke Firebase project.

## Solution

### Step 1: Add SHA-1 to Firebase
1. Buka Firebase Console: https://console.firebase.google.com/project/maps-642b7
2. Klik ⚙️ (Settings) → **Project settings**
3. Scroll ke bawah → bagian **Your apps** → app Android `com.geotag.app`
4. Di bagian **SHA certificate fingerprints**, klik **Add fingerprint**
5. Paste SHA-1 ini:
   ```
   7D:FC:AF:B8:5E:C9:B2:28:E9:93:0F:98:7A:25:34:D5:FF:87:9D:BB
   ```
6. Klik **Save**

### Step 2: Verify Firebase Setup
1. **Authentication** → Sign-in method → pastikan **Google** sudah **Enabled**
2. **Realtime Database** → pastikan sudah ada database (buat jika belum, pilih test mode)

### Step 3: Test Login
1. Uninstall APK lama (jika ada)
2. Install APK baru
3. Coba login dengan Google

## Troubleshooting

### Masih gagal login?
Cek logcat error:
```bash
adb logcat | grep -i "auth\|firebase\|google"
```

Error umum:
- `DEVELOPER_ERROR` / `10` → SHA-1 belum ditambahkan atau salah
- `INVALID_CLIENT` → Web Client ID salah di strings.xml
- `SIGN_IN_FAILED` → Package name tidak match (`com.geotag.app`)

### Verify Web Client ID
Di Firebase Console → Project settings → General → scroll ke **Web API Key**, pastikan web client ID di app match:
```
323244718562-pm872fdtv8hdm24daelan9v13f4a3nvh.apps.googleusercontent.com
```
