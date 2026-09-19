# Better Signal

An **unofficial** fork of [Signal Android](https://github.com/signalapp/Signal-Android) with extra chat UI animations and polish. It is not affiliated with or endorsed by Signal Messenger, LLC.

- **Based on:** Signal Android **8.28.3** (upstream commit `7b67f2b3ee`)
- **Latest release:** [v8.28.3-better.1](https://github.com/IM-TECHNO/better-signal/releases/latest)

Release tags follow `v<upstream version>-better.<n>`, so `v8.28.3-better.1` is the first Better Signal release built on Signal Android 8.28.3.

## What is different from upstream

- Balloon pop-in for sent and received messages, and pop-out when a message is deleted
- Smoother fade and scale transition when opening a chat from the list
- The round scroll-to-bottom button is replaced by a pill above the compose bar that shows the unread count or "Scroll to the bottom"
- Pop animation for the unread badge on the mention button
- Shimmer over attachment thumbnails while they download

Everything else is upstream Signal Android unchanged.

## Try it

Download an APK from the [Releases page](https://github.com/IM-TECHNO/better-signal/releases):

- `arm64-v8a`: for almost all modern phones (recommended)
- `universal`: works on any device, but is larger

Verify the download against `SHA256SUMS.txt` on the release page.

**Before installing:**

- This build has had no independent security review. Use it at your own risk.
- It uses the same app ID as the official Signal app, so it cannot be installed over it. You must uninstall Signal first, which deletes local chat history unless you have a backup. Do not do this on a device you rely on without one.
- Updates between Better Signal releases work, because they are signed with the same key.

## Building

This is a standard Signal Android build. You need JDK 21 and the Android SDK (see `.tool-versions` and `gradle/libs.versions.toml` for versions). Then:

```
./gradlew :Signal-Android:assemblePlayProdDebug
```

The debug APK is written to `app/build/outputs/apk/playProd/debug/`. For the build used in releases, run `assembleGithubProdRelease` and sign the result yourself.

## Upstream and license

All credit for Signal goes to Signal Messenger, LLC and its contributors. For the official app, bug reports, translations, and full commit history, see https://github.com/signalapp/Signal-Android and https://signal.org.

This repository does not include upstream commit history. It starts from a snapshot of upstream because GitHub push protection blocks old OpenSSL test keys in that history.

Licensed under the [AGPLv3](LICENSE), the same as upstream. See also `NOTICE`.
