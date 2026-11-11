"""Exercises the real Mac launcher script with controlled command fixtures under Git Bash."""
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest

BASH = Path(r"C:\Program Files\Git\bin\bash.exe")
SCRIPT = Path(__file__).resolve().parents[1] / "macos/Attach-Handoff.command"


@unittest.skipUnless(os.name == "nt" and BASH.is_file(), "Requires controlled Git Bash fixture environment")
class MacAttachScriptTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory(prefix="handoff attach fixture ")
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.home, self.package, self.bin = [self.root / n for n in ["home", "package with spaces", "bin"]]
        for p in [self.home, self.package, self.bin]: p.mkdir()
        shutil.copyfile(SCRIPT, self.package / SCRIPT.name)
        (self.package / "handoff-runtime-agent-0.1.2.jar").write_text("fixture", encoding="utf-8")
        self.executable(self.bin / "uname", "#!/bin/bash\necho Darwin\n")
        self.runtime = self.home / "Library/Application Support/Triggers Client/runtime"
        self.env = dict(os.environ)
        self.env.update(HOME=self.posix(self.home), FIXTURE_CAPTURE=self.posix(self.root / "selected"),
                        FIXTURE_DIRTY=self.posix(self.root / "dirty"), JAVA_TOOL_OPTIONS="TEST_ONLY",
                        JDK_JAVA_OPTIONS="TEST_ONLY", _JAVA_OPTIONS="TEST_ONLY")
        self.env.pop("JAVA_HOME", None)

    def posix(self, p):
        return subprocess.check_output([str(BASH), "-c", 'cygpath -u "$1"', "path", str(p)], text=True).strip()

    @staticmethod
    def executable(path, text):
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(text, encoding="utf-8", newline="\n")
        path.chmod(0o755)

    def java(self, folder, major=21, attach=True):
        p = folder / "bin/java"
        self.executable(p, f'''#!/bin/bash
if [ "${{JAVA_TOOL_OPTIONS+x}}${{JDK_JAVA_OPTIONS+x}}${{_JAVA_OPTIONS+x}}" != "" ]; then echo dirty >> "$FIXTURE_DIRTY"; fi
if [ "${{1-}}" = -version ]; then echo 'openjdk version "{major}.0.1"' >&2; exit 0; fi
if [ "${{1-}}" = --list-modules ]; then echo 'java.base@{major}'; {"echo 'jdk.attach@21';" if attach else ""} exit 0; fi
printf '%s\\n' "$0" "$@" > "$FIXTURE_CAPTURE"
''')
        return p

    def run_script(self):
        r = subprocess.run([str(BASH), "--noprofile", "--norc", "-c",
                            'export PATH="$1:/usr/bin:/bin"; exec /bin/bash "$2"', "fixture",
                            self.posix(self.bin), self.posix(self.package / SCRIPT.name)], env=self.env,
                           capture_output=True, text=True, timeout=60)
        self.assertEqual(0, r.returncode, r.stdout + r.stderr)
        return (self.root / "selected").read_text(encoding="utf-8").splitlines()

    def test_skips_old_java_and_runtime_without_attach(self):
        self.java(self.runtime / "a-old", 17)
        self.java(self.runtime / "b-stripped", 21, attach=False)
        expected = self.java(self.runtime / "c-full", 21)
        self.assertEqual(self.posix(expected), self.run_script()[0])

    def test_passes_only_agent_and_instance_paths_with_spaces(self):
        self.java(self.runtime / "full")
        args = self.run_script()[1:]
        self.assertEqual(["-Xmx64m", "--add-modules", "jdk.attach", "-jar",
                          self.posix(self.package / "handoff-runtime-agent-0.1.2.jar"), "--game-dir",
                          self.posix(self.home / "Library/Application Support/Triggers Client/instances/Sword Art Online")], args)

    def test_clears_jvm_option_environment_before_probes(self):
        self.java(self.runtime / "full")
        self.run_script()
        self.assertFalse((self.root / "dirty").exists())

    def test_uses_java_home_when_bundled_runtime_lacks_attach(self):
        self.java(self.runtime / "stripped", attach=False)
        home = self.root / "full jdk"
        expected = self.java(home)
        self.env["JAVA_HOME"] = self.posix(home)
        self.assertEqual(self.posix(expected), self.run_script()[0])


if __name__ == "__main__": unittest.main()
