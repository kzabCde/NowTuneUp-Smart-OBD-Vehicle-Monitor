import importlib.util
from pathlib import Path
import unittest

spec = importlib.util.spec_from_file_location("sync", Path(__file__).parents[1] / "sync-website-release.py")
sync = importlib.util.module_from_spec(spec)
spec.loader.exec_module(sync)


class WebsiteReleaseTest(unittest.TestCase):
    def release(self):
        return dict(tag_name="v1.18.0", draft=False, prerelease=False, published_at="2026-11-09T00:00:00Z",
                    assets=[dict(name="NowTuneUp-v1.18.0-release.apk", size=123,
                                 browser_download_url=f"https://github.com/{sync.REPOSITORY}/releases/download/v1.18.0/NowTuneUp-v1.18.0-release.apk")])

    def test_uses_actual_artifact_facts(self):
        result = sync.metadata(self.release(), "1.18.0", 28, 123, "a" * 64)
        self.assertEqual(28, result["versionCode"])
        self.assertEqual(123, result["apkSize"])
        self.assertEqual("a" * 64, result["sha256"])

    def test_rejects_unpublished_or_wrong_release(self):
        for changes in [dict(draft=True), dict(prerelease=True), dict(tag_name="v1.17.0"), dict(published_at=None)]:
            with self.subTest(changes=changes), self.assertRaises(ValueError):
                sync.metadata(self.release() | changes, "1.18.0", 28, 123, "a" * 64)

    def test_rejects_size_and_url_mismatches(self):
        for field, value in [("size", 456), ("browser_download_url", "https://example.com/untrusted.apk")]:
            release = self.release()
            release["assets"][0][field] = value
            with self.assertRaises(ValueError):
                sync.metadata(release, "1.18.0", 28, 123, "a" * 64)


if __name__ == "__main__":
    unittest.main()
