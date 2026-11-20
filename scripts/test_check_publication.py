"""Offline regression tests; all token/request examples are synthetic."""
import unittest

from check_publication import REQUIRED_FILES, content_issues, mode_issues, path_issues, required_issues


class PublicationPathTests(unittest.TestCase):
    def test_source_build_inputs_and_licenses_are_allowed(self):
        for path in ["app/src/main/java/com/hikari/app/HomeScreen.kt",
                     "gradle/wrapper/gradle-wrapper.jar", "LICENSE.md",
                     "app/src/main/assets/licenses/MPL-2.0.txt", ".env.example",
                     ".env.development.example", "scripts/check_publication.py",
                     "README.md", "docs/PROJECT_GUIDE.md", "docs/assets/hikari-logo.svg",
                     "THIRD_PARTY_NOTICES.md", "app/src/main/assets/licenses/Hikari-LICENSE.md",
                     "licenses/MPL-2.0-NOTICES.md"]:
            with self.subTest(path=path):
                self.assertEqual([], path_issues(path))

    def test_environment_overrides_are_not_publication_inputs(self):
        for path in [".env", ".env.local", "nested/.env.production", ".env.example.local"]:
            with self.subTest(path=path):
                self.assertTrue(path_issues(path))

    def test_generated_private_and_legacy_files_are_rejected(self):
        for path in ["artifacts/run/home.png", "app/build/output.apk", ".idea/misc.xml",
                     "android/.settings/preferences", "local.properties", "signing.pem",
                     "backups/library.json", ".playwright-mcp/browser.log", "test_yt.json",
                     "new_dump.html"]:
            with self.subTest(path=path):
                self.assertTrue(path_issues(path))

    def test_parent_and_absolute_paths_are_rejected(self):
        self.assertTrue(path_issues("../outside.txt"))
        self.assertTrue(path_issues("/absolute.txt"))


class PublicationManifestTests(unittest.TestCase):
    def test_unexecutable_gradle_wrapper_is_rejected(self):
        self.assertTrue(mode_issues("gradlew", "100644"))

    def test_executable_wrapper_and_regular_source_are_accepted(self):
        self.assertEqual([], mode_issues("gradlew", "100755"))
        self.assertEqual([], mode_issues("README.md", "100644"))

    def test_complete_native_publication_manifest_is_accepted(self):
        self.assertEqual([], required_issues(REQUIRED_FILES))

    def test_missing_license_or_native_rewrite_is_rejected(self):
        for missing in ["LICENSE.md", "app/src/main/java/com/hikari/app/MainActivity.kt",
                        "licenses/Nanojson-Apache-2.0.txt", "licenses/Protobuf-BSD-3-Clause.txt",
                        "app/src/main/assets/licenses/Nanojson-Apache-2.0.txt",
                        "app/src/main/assets/licenses/Protobuf-BSD-3-Clause.txt"]:
            with self.subTest(missing=missing):
                self.assertEqual([(missing, "required native source/license input missing")],
                                 required_issues(REQUIRED_FILES - {missing}))


class PublicationContentTests(unittest.TestCase):
    def test_private_key_header_is_rejected(self):
        self.assertIn("private-key material", content_issues(b"-----BEGIN " + b"RSA PRIVATE KEY-----"))

    def test_synthetic_token_is_rejected(self):
        self.assertIn("GitHub token", content_issues(b"ghp_" + b"x" * 36))

    def test_captured_requests_are_rejected_in_url_and_encoded_form(self):
        for prefix in [b"&", b"%26", b"\\u0026"]:
            for assignment in [b"ip=", b"ip%3D"]:
                data = b"https://media.example/preview?x=y" + prefix + assignment + b"203.0.113.7"
                with self.subTest(prefix=prefix, assignment=assignment):
                    self.assertIn("IP-bearing captured request", content_issues(data))

    def test_captured_visitor_token_is_rejected(self):
        data = b'{"visitorData": "' + b"X" * 40 + b'"}'
        self.assertIn("captured visitor token", content_issues(data))

    def test_machine_specific_path_is_rejected(self):
        self.assertIn("machine-specific home path", content_issues(b"/home/" + b"private-user/project/"))

    def test_generic_documentation_paths_and_public_key_configuration_are_not_called_secrets(self):
        for data in [b"/home/developer/project/", b"/home/user/project/",
                     b"/Users/<username>/project/", b"AIza" + b"x" * 35]:
            with self.subTest(data=data):
                self.assertEqual([], content_issues(data))

    def test_binary_font_or_wrapper_data_is_not_treated_as_text(self):
        self.assertEqual([], content_issues(b"\x00binary" + b"ghp_" + b"x" * 36))

    def test_large_files_require_review_even_if_binary(self):
        self.assertTrue(content_issues(b"\x00" * (5 * 1024 * 1024 + 1)))


if __name__ == "__main__":
    unittest.main()
