"""Regression checks for the packaged-app startup and database smoke gate."""

from contextlib import nullcontext, redirect_stderr, redirect_stdout
from importlib.util import module_from_spec, spec_from_file_location
from io import StringIO
from pathlib import Path
from tempfile import TemporaryDirectory
from unittest import TestCase
from unittest.mock import patch
import sqlite3


SCRIPT = Path(__file__).resolve().parents[3] / "scripts" / "smoke_test_jar.py"
SPEC = spec_from_file_location("smoke_test_jar", SCRIPT)
smoke = module_from_spec(SPEC)
SPEC.loader.exec_module(smoke)


class RunningApplication:
    def __init__(self, command, output, message=""):
        self.command = command
        output.write(message.encode())
        self.returncode = None
        self.terminated = False

    def poll(self):
        return self.returncode

    def terminate(self):
        self.terminated = True
        self.returncode = 0

    def wait(self, timeout=None):
        return self.returncode


class SmokeTestJarTest(TestCase):
    def run_smoke(self, workspace, message="", exit_code=None, crash_log=None):
        jar = workspace / "application.jar"
        jar.touch()
        isolated_home = workspace / "isolated-home"
        isolated_home.mkdir(exist_ok=True)
        process = None

        def launch(command, *, env, stdout, stderr):
            nonlocal process
            process = RunningApplication(command, stdout, message)
            process.returncode = exit_code
            if crash_log is not None:
                (isolated_home / "hs_err_pid123.log").write_text(crash_log)
            return process

        output = StringIO()
        errors = StringIO()
        with patch.object(smoke.sys, "argv", ["smoke_test_jar.py", str(jar)]), \
                patch.object(smoke.tempfile, "TemporaryDirectory",
                             return_value=nullcontext(str(isolated_home))), \
                patch.object(smoke.subprocess, "Popen", side_effect=launch), \
                patch.object(smoke.time, "monotonic", side_effect=[0, 16]), \
                redirect_stdout(output), redirect_stderr(errors):
            result = smoke.main()
        return result, output.getvalue(), errors.getvalue(), process

    def test_javafx_native_error_fails_even_if_process_remains_open(self):
        with TemporaryDirectory() as temporary:
            result, output, errors, process = self.run_smoke(
                Path(temporary), "java.lang.UnsatisfiedLinkError: wrong architecture")

        self.assertEqual(1, result)
        self.assertIn("JavaFX or dependency startup error", errors)
        self.assertEqual("", output)
        self.assertTrue(process.terminated)

    def test_early_jvm_exit_surfaces_crash_log(self):
        with TemporaryDirectory() as temporary:
            result, output, errors, process = self.run_smoke(
                Path(temporary), "JVM exited unexpectedly", exit_code=134,
                crash_log="A fatal error occurred in the Java Runtime Environment")

        self.assertEqual(1, result)
        self.assertIn("JVM exited unexpectedly", errors)
        self.assertIn("A fatal error occurred in the Java Runtime Environment", errors)
        self.assertIn("Application exited during startup with code 134", errors)
        self.assertEqual("", output)
        self.assertIn("-Dprism.verbose=true", process.command)
        self.assertTrue(any(arg.startswith("-XX:ErrorFile=") for arg in process.command))

    def test_missing_workspace_database_fails_smoke_gate(self):
        with TemporaryDirectory() as temporary:
            result, output, errors, process = self.run_smoke(Path(temporary))

        self.assertEqual(1, result)
        self.assertIn("did not create its database", errors)
        self.assertEqual("", output)
        self.assertTrue(process.terminated)

    def test_seeded_valid_workspace_passes_smoke_gate(self):
        with TemporaryDirectory() as temporary:
            workspace = Path(temporary)
            database = smoke.database_path(str(workspace / "isolated-home"))
            database.parent.mkdir(parents=True)
            with sqlite3.connect(database) as connection:
                connection.execute("CREATE TABLE user_accounts (id INTEGER)")
                connection.execute("CREATE TABLE maintenance_requests (id INTEGER)")
                connection.executemany("INSERT INTO user_accounts VALUES (?)",
                                       [(number,) for number in range(6)])
                connection.executemany("INSERT INTO maintenance_requests VALUES (?)",
                                       [(number,) for number in range(6)])
            result, output, errors, process = self.run_smoke(workspace)

        self.assertEqual(0, result)
        self.assertIn("passed SQLite integrity check", output)
        self.assertEqual("", errors)
        self.assertTrue(process.terminated)

    def test_incomplete_seed_fails_smoke_gate(self):
        with TemporaryDirectory() as temporary:
            workspace = Path(temporary)
            database = smoke.database_path(str(workspace / "isolated-home"))
            database.parent.mkdir(parents=True)
            with sqlite3.connect(database) as connection:
                connection.execute("CREATE TABLE user_accounts (id INTEGER)")
                connection.execute("CREATE TABLE maintenance_requests (id INTEGER)")
            result, output, errors, process = self.run_smoke(workspace)

        self.assertEqual(1, result)
        self.assertIn("Unexpected workspace state", errors)
        self.assertEqual("", output)
        self.assertTrue(process.terminated)
