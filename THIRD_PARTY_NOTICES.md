# Third-party components

Hikari uses NewPipeExtractor v0.27.1 by Team NewPipe, licensed under GPL-3.0-or-later.

- Source: https://github.com/TeamNewPipe/NewPipeExtractor/tree/v0.27.1
- License: licenses/NewPipeExtractor-GPL-3.0.txt (also bundled in the APK)

Hikari's original source is also [GPL-3.0-or-later](LICENSE.md). The GPL includes corresponding-source requirements for combined distributions.

NewPipeExtractor also includes:

- Nanojson (Team NewPipe fork), revision `e9d656ddb49a412a5a0a5d5ef20ca7ef09549996`: Apache-2.0. Copyright 2011 The nanojson Authors. [License and copyright notice](licenses/Nanojson-Apache-2.0.txt); [versioned source](https://github.com/TeamNewPipe/nanojson/tree/e9d656ddb49a412a5a0a5d5ef20ca7ef09549996).
- Protocol Buffers Lite (`protobuf-javalite` 4.36.2, upstream v36.2): BSD-3-Clause. Copyright 2008 Google Inc. All rights reserved. [License and copyright notice](licenses/Protobuf-BSD-3-Clause.txt); [versioned source](https://github.com/protocolbuffers/protobuf/tree/2c74169b34066ceb8ddb6b882fcb3fb32d737a55/java).

Copies of these license and notice files are also bundled in `app/src/main/assets/licenses/`.

AndroidX/Jetpack Compose/Media3, Kotlin, OkHttp and Coil are used under their respective upstream licenses. Their Maven artifacts include upstream license metadata.

NewPipeExtractor v0.27.1 also depends on Rhino and rhino-engine 1.8.1, primarily licensed under MPL-2.0. Rhino's additional V8, Raffaello Giulietti and Lucent Technologies notices are retained in [Rhino-NOTICE.txt](licenses/Rhino-NOTICE.txt) and [Rhino-DToA-NOTICE.txt](licenses/Rhino-DToA-NOTICE.txt). OkHttp 4.12.0's client code is Apache-2.0-licensed, but its bundled Public Suffix List data is MPL-2.0-licensed; its original [data notice](licenses/OkHttp-Public-Suffix-List-NOTICE.txt) is retained separately. See the [MPL-2.0 license](licenses/MPL-2.0.txt) and [component notices and source references](licenses/MPL-2.0-NOTICES.md). Copies of these files are also provided in `app/src/main/assets/licenses/`.

Manrope is bundled as static font weights under the SIL Open Font License 1.1. Its license is included in `app/src/main/assets/licenses/Manrope-OFL.txt`: Copyright 2019 The Manrope Project Authors (https://github.com/googlefonts/manrope). Video thumbnails and creator avatars come from the existing YouTube data sources.

Lottie Compose 6.7.1 by Airbnb is used under the Apache License 2.0. Source: https://github.com/airbnb/lottie-android/tree/v6.7.1. The license is bundled at `app/src/main/assets/licenses/Lottie-Apache-2.0.txt`. The floating collection illustration in `res/raw/collection_empty.json` is original Hikari vector artwork, rendered locally.

AndroidX WebKit 1.14.0 is used under Apache-2.0 for document-start script support.

Hikari uses NewPipeExtractor for video extraction and YouTube's embedded player for embedded playback. Retrieved content retains its original ownership and licensing. SponsorBlock and Return YouTube Dislike are accessed through their APIs; their server software is not bundled with Hikari.
