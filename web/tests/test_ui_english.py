"""Regression policy for app-owned English UI; user data/docs are outside scope."""
from pathlib import Path
import re
import unittest

ROOT = Path(__file__).resolve().parents[2]
CYRILLIC = re.compile(r"[\u0400-\u052f]")

def has_cyrillic(text):
    text = re.sub(r"\\u([0-9a-fA-F]{4})", lambda m: chr(int(m[1], 16)), text)
    return bool(CYRILLIC.search(text))

class EnglishUiPolicy(unittest.TestCase):
    def test_owned_runtime_sources_are_english(self):
        paths = list((ROOT / "app/src/main").rglob("*.java"))
        paths += list((ROOT / "app/src/main/res").rglob("*.xml"))
        paths += list((ROOT / "web/src").rglob("*.js"))
        paths += list((ROOT / "web").glob("patch*.py"))
        failures = []
        for path in paths:
            for number, line in enumerate(path.read_text().splitlines(), 1):
                if has_cyrillic(line):
                    failures.append(str(path.relative_to(ROOT)) + ":" + str(number))
        self.assertEqual(failures, [], "Non-English app-owned labels: " + ", ".join(failures))

    def test_detects_literal_and_escaped_cyrillic(self):
        self.assertFalse(has_cyrillic('button("Add to message")'))
        self.assertTrue(has_cyrillic(chr(0x0420)))
        self.assertTrue(has_cyrillic('button("' + chr(92) + 'u0420")'))

if __name__ == "__main__":
    unittest.main()
