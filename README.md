<img src="assets/icon.svg" width="64" height="64" alt="">

# StorageViewer

A read-only storage inspector for Android, styled after Samsung One UI 9 and built for foldables (Galaxy Z Fold8).

- **Storage**: a size-sorted tree of internal storage with share bars, plus a summary of what uses the device (apps, images, videos, documents, audio, archives, system, free). The overview shows the selected folder as a treemap, its contents as a list, its file types and its largest files. Files show thumbnails where they have one: photos, video frames, album art, the first page of a PDF and the icon inside an APK.
- **Apps**: every installed app sorted by size, with app, data and cache split, filters (downloaded by default, all, system), search and a treemap overview.

## Read-only by design

StorageViewer never writes, moves or deletes anything. Every action is handed to the app that normally handles it:

| Tap | Goes to |
|---|---|
| Folder | Opens it in the tree and, on the inner screen, shows it on the right (long-press: *Open in My Files*) |
| File | Its default viewer, with a read-only grant |
| App | Android's App info page |

The scan only calls `readdir` and `lstat`. Thumbnails open files read-only and stay in memory; nothing is cached on disk, not even in MediaStore's thumbnail cache. The `FileProvider` that hands files to viewers refuses anything but read access. Android has no read-only "All files access", so the app holds the standard permission but has no code path that uses it to write.

## Permissions

| Permission | Why |
|---|---|
| All files access (`MANAGE_EXTERNAL_STORAGE`) | Measure folder sizes in shared storage |
| Usage access (`PACKAGE_USAGE_STATS`) | Read each app's data and cache size |
| `QUERY_ALL_PACKAGES` | List every installed app |

Both special accesses are requested in-app and granted in Settings. Without usage access, apps show their install size only. `Android/data` and `Android/obb` cannot be read by any app; their contents are counted under each app's size.

## Layouts

| Screen | Layout |
|---|---|
| Cover screen (folded) | One pane, One UI large title, floating tab bar; overview opens as its own screen |
| Inner screen (unfolded / book posture) | Side rail; folder tree on the left, the selected folder's treemap and contents on the right, split at the hinge |
| Tabletop (half-folded, rotated) | Overview above the fold, list below |

Folding and unfolding keeps state: the activity handles size changes itself instead of restarting.

## Build

Requires the Android SDK (API 37) and a JDK 17 or newer (Android Studio's bundled JBR works).

```sh
./gradlew assembleRelease        # app/build/outputs/apk/release/app-release.apk
./gradlew testDebugUnitTest      # unit tests
./gradlew lintDebug
adb install -r app/build/outputs/apk/release/app-release.apk
```

Use the release build on the phone: debug builds run Compose unoptimised and stutter at 120 Hz. Release is shrunk with R8 and ships baseline profiles. To get compiled performance right away rather than after Android's background optimisation, run `adb shell cmd package compile -m speed-profile -f wtf.buddyworks.storageviewer`.

### Signing

Release builds are signed with the key named in `keystore.properties` next to `settings.gradle.kts` (gitignored):

```properties
storeFile=C:/Users/you/.android/storageviewer-release.jks
storePassword=…
keyAlias=storageviewer
keyPassword=…
```

CI reads the same values from `SIGNING_*` environment variables. Without either, release builds fall back to the debug key, which Android will not install over a release-signed copy (and vice versa). Keep a backup of the keystore: updates only install over an existing copy signed with the same key.

### Releases

The version lives in `gradle.properties` (`storageviewer.version`); the version code is derived from it. To publish a release:

1. Bump `storageviewer.version` and merge.
2. Tag the merge commit `v<version>` and push the tag: `git tag v0.2.0 && git push origin v0.2.0`.

The [Release APK](.github/workflows/release.yml) workflow runs the unit tests, builds the signed APK and attaches `StorageViewer-<version>.apk` to a GitHub release for the tag (it fails if the tag and `storageviewer.version` differ). Pull requests run the same build and keep the APK as a run artifact. It needs these repository secrets: `SIGNING_KEYSTORE_BASE64` (the keystore, base64), `SIGNING_STORE_PASSWORD`, `SIGNING_KEY_ALIAS`, `SIGNING_KEY_PASSWORD`.

Stack: Kotlin, Jetpack Compose with a custom One UI theme (Material 3 underneath), AndroidX Window for hinge and posture. Strings are in English and German.

Created in [T3 Code](https://t3.codes).
