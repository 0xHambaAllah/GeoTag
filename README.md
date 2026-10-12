# GeoTag 📍

Location tagging Android app with photo capture, GPS coordinates, and Google Maps navigation.

## Features

- 📸 **Photo Capture** — Take photos with CameraX and auto-tag with GPS location
- 📍 **GPS Location** — Automatic geocoding with address resolution
- 🗺️ **Navigation** — One-tap Google Maps navigation to saved locations
- 🔍 **Search** — Find saved locations by name or phone number
- ☁️ **Cloud Sync** — Firebase Firestore + Storage backup with offline-first Room database
- 🔐 **Google Sign-In** — Firebase Authentication with Google account
- 📏 **Distance** — Real-time distance calculation from current position
- ✏️ **Edit/Delete** — Full CRUD operations on saved locations

## Tech Stack

- **Language:** Kotlin
- **UI:** Jetpack Compose + Material3
- **Architecture:** MVVM + Repository pattern
- **Local DB:** Room
- **Cloud:** Firebase (Auth, Firestore, Storage)
- **Camera:** CameraX
- **Location:** Google Play Services Location
- **Image Loading:** Coil
- **Background Sync:** WorkManager
- **Build:** Gradle 8.7 + AGP 8.5.0 + KSP

## Setup

### Prerequisites
- Android Studio Hedgehog or later
- JDK 17
- Android SDK 35

### Firebase Setup
1. Go to [Firebase Console](https://console.firebase.google.com/)
2. Open project `maps-642b7`
3. Enable **Authentication** → Google Sign-In
4. Enable **Cloud Firestore**
5. Enable **Firebase Storage**
6. The `google-services.json` is already included

### Build
```bash
# Clone
git clone https://github.com/0xHambaAllah/GeoTag.git
cd GeoTag

# Build debug APK
./gradlew assembleDebug

# Install on device
./gradlew installDebug
```

### CI/CD
GitHub Actions automatically builds a debug APK on every push to `main`. Download the artifact from the Actions tab.

## Project Structure

```
app/src/main/kotlin/com/geotag/app/
├── GeoTagApplication.kt          # App entry point
├── MainActivity.kt               # Single activity (Compose)
├── data/
│   ├── model/GeoLocation.kt      # Room entity
│   ├── local/
│   │   ├── LocationDao.kt        # Room DAO
│   │   └── GeoTagDatabase.kt     # Room database
│   ├── remote/
│   │   ├── FirebaseService.kt    # Firebase operations
│   │   └── SyncWorker.kt         # Background sync worker
│   └── repository/
│       └── LocationRepository.kt # Data repository
├── util/
│   ├── LocationUtils.kt          # GPS & geocoding utilities
│   └── PhotoUtils.kt             # Photo compression & EXIF
└── ui/
    ├── theme/                     # Material3 theming
    ├── navigation/NavGraph.kt     # Navigation routes
    └── screens/
        ├── auth/LoginScreen.kt    # Google Sign-In
        ├── home/                  # Location list + search
        ├── camera/                # Photo capture + save
        └── detail/                # View + edit location
```

## License

Private project.
