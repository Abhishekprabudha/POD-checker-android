# POD Delivery Validator Android App

An Android demo app that validates proof-of-delivery using:
- **Waybill lookup**
- **Live camera capture only** (no image upload flow)
- **Mandatory GPS/location-on check**
- **Cross-validation against mapped delivery coordinates stored inside the repo**
- **Face/person risk check** using on-device ML Kit face detection

## What this app does
1. User enters a **waybill number**.
2. The app loads the expected delivery point from `app/src/main/assets/deliveries.json`.
3. The app requires **camera + location permissions**.
4. The app requires **location services to be ON**.
5. The app allows only **live photo capture**.
6. The app checks the captured image for a **visible face**.
7. The app fetches the phone's current GPS coordinates.
8. The app compares the live GPS with the mapped delivery coordinates.
9. It returns either:
   - **GENUINE DELIVERY**, or
   - **NOT GENUINE / NEED REVIEW**

## Important practical note
The repo currently blocks obvious human presence using **face detection**. That is strong for visible faces, but it is not a full-body human detector. So for production, you would usually replace or extend this with a stronger person-detection model.

## Sample waybills in repo
The app ships with 10 sample waybills:
- WB1001 to WB1010

All mapped coordinates are stored in:
- `app/src/main/assets/deliveries.json`

## GitHub web-only deployment flow
You said **GitHub on the web, not local machine or terminal**, so use this exact flow:

### 1) Create a new empty repository on GitHub
Example name:
- `pod-delivery-validator-android`

Do **not** initialize with README, `.gitignore`, or license.

### 2) Upload this repo ZIP content through GitHub web
- Download and extract the ZIP provided by ChatGPT.
- Open your empty GitHub repo.
- Click **Add file** → **Upload files**.
- Drag all extracted files and folders into the GitHub upload page.
- Commit directly to the `main` branch.

### 3) Let GitHub Actions build the APK
This repo already contains the workflow:
- `.github/workflows/android-apk.yml`

After upload:
- Open the **Actions** tab.
- Open the latest workflow run named **Build Android APK**.
- Wait for it to finish.

### 4) Download the APK from GitHub Actions
Inside the successful workflow run:
- Open the **Artifacts** section.
- Download `pod-validator-debug-apk`.
- Inside it, you will find `app-debug.apk`.

### 5) Install on Android
- Transfer `app-debug.apk` to the Android phone.
- Allow installation from unknown sources if Android asks.
- Install and test.

## Repo structure
```text
pod_delivery_validator_android/
├── .github/
│   └── workflows/
│       └── android-apk.yml         # GitHub Actions APK build
├── app/
│   ├── build.gradle.kts            # App-level Android config
│   └── src/
│       └── main/
│           ├── AndroidManifest.xml # Permissions + app entry
│           ├── assets/
│           │   └── deliveries.json # 10 waybills + mapped GPS data
│           ├── java/com/example/podvalidator/
│           │   ├── MainActivity.kt
│           │   ├── Models.kt
│           │   ├── DeliveryRepository.kt
│           │   └── TaskAwait.kt
│           └── res/values/
│               └── themes.xml
├── build.gradle.kts                # Root Gradle config
├── gradle.properties
├── settings.gradle.kts
└── README.md
```

## How the validation logic works
The app marks a delivery as genuine only when:
- the waybill exists,
- location services are ON,
- live location is fetched successfully,
- the current location is within the allowed radius of the mapped delivery point,
- and no visible face is detected in the captured image.

## Production enhancements you would likely add later
- stronger human/person detection instead of only face detection
- tamper checks / mock-location detection
- backend API for real waybill lookup instead of repo JSON
- stronger fraud scoring rules
- encrypted audit trail with timestamp + image hash
- supervisor override and manual review queue
