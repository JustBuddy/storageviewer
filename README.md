<img src="assets/icon.svg" width="64" height="64" alt="">

# StorageViewer

A read-only storage inspector for Android, styled after Samsung One UI 9 and built for foldables (Galaxy Z Fold8).

- **Storage**: a size-sorted tree of internal storage with share bars, plus a summary of what uses the device (apps, images, videos, documents, audio, archives, system, free). The overview shows the selected folder as a treemap, its contents as a list, its file types and its largest files.
- **Apps**: every installed app sorted by size, with app, data and cache split, filters (all, downloaded, system), search and a treemap overview.

## Read-only by design

StorageViewer never writes, moves or deletes anything. Every action is handed to the app that normally handles it:

| Tap | Goes to |
|---|---|
| Folder | Opens it in the tree and, on the inner screen, shows it on the right (long-press: *Open in My Files*) |
| File | Its default viewer, with a read-only grant |
| App | Android's App info page |

The only file-system calls are `readdir` and `lstat`. The `FileProvider` that hands files to viewers refuses anything but read access. Android has no read-only "All files access", so the app holds the standard permission but has no code path that uses it to write.

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

Use the release build on the phone: debug builds run Compose unoptimised and stutter at 120 Hz. Release is shrunk with R8, ships baseline profiles and is signed with the debug key, so it installs over a debug build. To get compiled performance right away rather than after Android's background optimisation, run `adb shell cmd package compile -m speed-profile -f wtf.buddyworks.storageviewer`.

Stack: Kotlin, Jetpack Compose with a custom One UI theme (Material 3 underneath), AndroidX Window for hinge and posture. Strings are in English and German.

Created in [T3 Code](https://t3.codes).
