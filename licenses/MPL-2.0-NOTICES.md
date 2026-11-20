# MPL-licensed third-party components

These notices cover MPL-2.0 components and Rhino's additional third-party notices. Copies of this file and the accompanying license and notice files are provided in `licenses/` and `app/src/main/assets/licenses/`. MPL-2.0 includes source-availability requirements for covered files and modifications.

## Rhino and rhino-engine 1.8.1

NewPipeExtractor v0.27.1 declares runtime dependencies on `org.mozilla:rhino:1.8.1` and `org.mozilla:rhino-engine:1.8.1`. Both artifacts declare the Mozilla Public License, Version 2.0. Rhino also contains separately licensed number-conversion code.

- MPL-2.0 text: [MPL-2.0.txt](MPL-2.0.txt), copied from https://www.mozilla.org/media/MPL/2.0/index.txt.
- Release source: https://github.com/mozilla/rhino/tree/Rhino1_8_1_Release (commit `f2d1ffef9dafbd625ca1ea79b8893ecbaee07e61`).
- Versioned Rhino source artifact: https://repo.maven.apache.org/maven2/org/mozilla/rhino/1.8.1/rhino-1.8.1-sources.jar.
- Versioned ScriptEngine source artifact: https://repo.maven.apache.org/maven2/org/mozilla/rhino-engine/1.8.1/rhino-engine-1.8.1-sources.jar.
- Upstream release license: https://github.com/mozilla/rhino/blob/f2d1ffef9dafbd625ca1ea79b8893ecbaee07e61/LICENSE.txt.
- Upstream release notice: https://github.com/mozilla/rhino/blob/f2d1ffef9dafbd625ca1ea79b8893ecbaee07e61/NOTICE.txt, reproduced verbatim in [Rhino-NOTICE.txt](Rhino-NOTICE.txt). This retains the V8 project authors' BSD-style notice and Raffaello Giulietti's MIT-style notice, including their copyright text, conditions and disclaimers.
- Additional source-file notice: [Rhino-DToA-NOTICE.txt](Rhino-DToA-NOTICE.txt) reproduces the complete leading notices from `org/mozilla/javascript/DToA.java` in `rhino-1.8.1-sources.jar`, including David M. Gay's authorship and the Lucent Technologies copyright, permission and disclaimer. This notice is additional to the release-level `NOTICE.txt`.

## Public Suffix List data in OkHttp 4.12.0

OkHttp's client code is Apache-2.0-licensed, but its `okhttp3/internal/publicsuffix/publicsuffixes.gz` resource is compiled from the Public Suffix List and is subject to MPL-2.0. The data's license is separate from the surrounding OkHttp code's license.

- MPL-2.0 text: [MPL-2.0.txt](MPL-2.0.txt).
- Upstream data notice: [OkHttp-Public-Suffix-List-NOTICE.txt](OkHttp-Public-Suffix-List-NOTICE.txt), reproduced from `okhttp3/internal/publicsuffix/NOTICE` in the `com.squareup.okhttp3:okhttp:4.12.0` JAR.
- Versioned OkHttp source: https://github.com/square/okhttp/tree/parent-4.12.0 (commit `4984568367caaf359b82c452bd28b5e192824d1c`).
- Version-specific upstream notice: https://github.com/square/okhttp/blob/4984568367caaf359b82c452bd28b5e192824d1c/okhttp/src/main/resources/okhttp3/internal/publicsuffix/NOTICE.
- Version-specific compiled data: https://github.com/square/okhttp/blob/4984568367caaf359b82c452bd28b5e192824d1c/okhttp/src/main/resources/okhttp3/internal/publicsuffix/publicsuffixes.gz. Compiled resource SHA-256: `9af43e9995ec749e7a30eec4e5a87770d7e2027847084a07cca82060eff0c791`.
- Public Suffix List (current, unversioned): https://publicsuffix.org/list/public_suffix_list.dat.
- Public Suffix List source repository: https://github.com/publicsuffix/list.
