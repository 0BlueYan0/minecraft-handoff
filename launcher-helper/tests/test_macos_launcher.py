"""Runs the real Bash script with controlled macOS command/runtime fixtures on Windows."""
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest

BASH = r"C:\Program Files\Git\bin\bash.exe"
SCRIPT = Path(__file__).resolve().parents[1] / "macos" / "Start-Handoff.command"


@unittest.skipUnless(os.name == "nt" and Path(BASH).is_file(), "Controlled Git Bash/macOS-command fixture harness")
class MacLauncherTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory(prefix="handoff shell fixture ")
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.home = self.root / "home"
        self.package = self.root / "package with spaces"
        self.bin = self.root / "bin"
        for p in [self.home, self.package, self.bin]:
            p.mkdir()
        shutil.copyfile(SCRIPT, self.package / "Start-Handoff.command")
        for name in ["minecraft-handoff-0.1.0.jar", "handoff-launcher-helper-0.1.1.jar"]:
            (self.package / name).write_text("fixture", encoding="utf-8")
        self.executable(self.bin / "uname", "#!/bin/bash\nprintf 'Darwin\\n'\n")
        self.runtime = self.home / "Library/Application Support/Triggers Client/runtime"
        self.env = dict(os.environ)
        self.env.update(HOME=self.posix(self.home), PATH=f"{self.posix(self.bin)}:/usr/bin:/bin",
                        FIXTURE_CAPTURE=self.posix(self.root / "selected"),
                        FIXTURE_PROBES=self.posix(self.root / "probes"),
                        FIXTURE_DIRTY=self.posix(self.root / "dirty"),
                        JAVA_TOOL_OPTIONS="TEST_ONLY_FLAGS", JDK_JAVA_OPTIONS="TEST_ONLY_FLAGS", _JAVA_OPTIONS="TEST_ONLY_FLAGS")
        self.env.pop("JAVA_HOME", None)

    def posix(self, path):
        return subprocess.check_output([BASH, "-c", 'cygpath -u "$1"', "path", str(path)], text=True).strip()

    @staticmethod
    def executable(path, text):
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(text, encoding="utf-8", newline="\n")
        path.chmod(0o755)

    def java(self, folder, major):
        java = folder / "bin/java"
        self.executable(java, f'''#!/bin/bash
if [ "${{JAVA_TOOL_OPTIONS+x}}${{JDK_JAVA_OPTIONS+x}}${{_JAVA_OPTIONS+x}}" != "" ]; then echo dirty >> "$FIXTURE_DIRTY"; fi
if [ "${{1-}}" = -version ]; then
    echo probe >> "$FIXTURE_PROBES"
    echo 'openjdk version "{major}.0.1"' >&2
    exit 0
fi
printf '%s\\n' "$0" > "$FIXTURE_CAPTURE"
printf '%s\\n' "$@" >> "$FIXTURE_CAPTURE"
''')
        return java

    def run_script(self):
        result = subprocess.run([BASH, "--noprofile", "--norc", "-c",
                                 'export PATH="$1:/usr/bin:/bin"; exec /bin/bash "$2"',
                                 "fixture", self.posix(self.bin), self.posix(self.package / "Start-Handoff.command")], env=self.env,
                                capture_output=True, text=True, timeout=15)
        self.assertEqual(0, result.returncode, result.stdout + result.stderr)
        return (self.root / "selected").read_text(encoding="utf-8").splitlines()

    def test_skips_java17_and_selects_java21(self):
        old = self.java(self.runtime / "a-java17", 17)
        expected = self.java(self.runtime / "b-java21", 21)
        actual = self.run_script()
        self.assertEqual(self.posix(expected), actual[0])
        self.assertNotEqual(self.posix(old), actual[0])

    def test_clears_options_before_version_probe(self):
        self.java(self.runtime / "java21", 21)
        self.run_script()
        self.assertTrue((self.root / "probes").is_file(), "Must verify runtime version before execution")
        self.assertFalse((self.root / "dirty").exists())

    def test_preserves_package_and_game_paths_with_spaces(self):
        self.java(self.runtime / "java21", 21)
        actual = self.run_script()
        self.assertEqual(["-Xmx64m", "-jar", self.posix(self.package / "handoff-launcher-helper-0.1.1.jar"),
                          "--game-dir", self.posix(self.home / "Library/Application Support/Triggers Client/instances/Sword Art Online"),
                          "--mod", self.posix(self.package / "minecraft-handoff-0.1.0.jar")], actual[1:])

    def test_uses_compatible_java_home_after_old_bundled_runtime(self):
        self.java(self.runtime / "java17", 17)
        home = self.root / "registered jdk21"
        expected = self.java(home, 21)
        self.env["JAVA_HOME"] = self.posix(home)
        self.assertEqual(self.posix(expected), self.run_script()[0])


if __name__ == "__main__":
    unittest.main()
