from pathlib import Path
import unittest
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]

class BrandingContractTest(unittest.TestCase):
    def test_display_name_in_every_existing_locale(self):
        for file in (ROOT / "app/src/main/res").glob("values*/strings.xml"):
            values = {s.attrib.get("name"): s.text for s in ET.parse(file).getroot().findall("string")}
            self.assertEqual("LocProxyTV", values["app_name"], str(file))

    def test_technical_identity_and_signer_are_preserved(self):
        gradle = (ROOT / "app/build.gradle").read_text()
        self.assertIn('applicationId "org.proxydroid"', gradle)
        self.assertEqual("0749fd8dc3df58d0b268d95f7bd43110e8314fd07ad61edb5672ee1406977657", (ROOT / "signing-certificate.sha256").read_text().strip())
        catalog = (ROOT / "app/src/main/java/org/proxydroid/update/ReleaseCatalog.kt").read_text()
        updater = (ROOT / "app/src/main/java/org/proxydroid/update/GitHubUpdater.kt").read_text()
        self.assertIn("/LeonidYasin/proxydroid/releases/download/", catalog)
        self.assertIn("https://api.github.com/repos/LeonidYasin/proxydroid/releases", updater)
        metadata = (ROOT / "scripts/build_metadata.py").read_text()
        self.assertIn("LocProxyTV v", metadata)
        self.assertIn("app='LocProxyTV'", metadata)
        self.assertIn("proxydroid-v", catalog)
        self.assertIn("org.proxydroid", catalog)

    def test_pages_sources_preserve_repository_url(self):
        site = (ROOT / "scripts/build_site.py").read_text()
        self.assertIn("LocProxyTV", site)
        self.assertIn("https://github.com/LeonidYasin/proxydroid", site)
        self.assertNotIn("https://github.com/LeonidYasin/locproxytv", site.lower())
