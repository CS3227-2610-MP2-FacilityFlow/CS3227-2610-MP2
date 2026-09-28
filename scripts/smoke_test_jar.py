"""Check that the packaged JavaFX application starts and remains open."""

from __future__ import annotations

import subprocess
import sys
import tempfile
import time
from pathlib import Path
import os
import sqlite3
import stat


def database_path(workspace: str) -> Path:
    if os.name == "nt":
        return Path(workspace, "local-app-data", "FacilityFlow", "facilityflow.db")
    if sys.platform == "darwin":
        return Path(workspace, "Library", "Application Support", "FacilityFlow", "facilityflow.db")
    return Path(workspace, "xdg-data", "FacilityFlow", "facilityflow.db")


def main() -> int:
    if len(sys.argv) != 2:
        print("Usage: python smoke_test_jar.py <application.jar>", file=sys.stderr)
        return 2

    jar_path = Path(sys.argv[1])
    if not jar_path.is_file():
        print(f"JAR not found: {jar_path}", file=sys.stderr)
        return 2

    with tempfile.TemporaryDirectory(
        prefix="facilityflow-smoke-", ignore_cleanup_errors=True
    ) as workspace:
        environment = os.environ.copy()
        environment["LOCALAPPDATA"] = str(Path(workspace) / "local-app-data")
        environment["XDG_DATA_HOME"] = str(Path(workspace) / "xdg-data")

        with tempfile.TemporaryFile(mode="w+b") as output:
            process = subprocess.Popen(
                ["java", f"-Duser.home={workspace}", "-jar", str(jar_path.resolve())],
                env=environment,
                stdout=output,
                stderr=subprocess.STDOUT,
            )
            try:
                deadline = time.monotonic() + 15
                while time.monotonic() < deadline and process.poll() is None:
                    time.sleep(0.25)

                output.seek(0)
                messages = output.read().decode("utf-8", errors="replace")
                if process.poll() is not None:
                    print(messages, file=sys.stderr)
                    print(
                        f"Application exited during startup with code {process.returncode}.",
                        file=sys.stderr,
                    )
                    return 1

                startup_errors = (
                    "Error initializing QuantumRenderer",
                    "No toolkit found",
                    "UnsatisfiedLinkError",
                    "UnsupportedClassVersionError",
                    "NoClassDefFoundError",
                )
                if any(error in messages for error in startup_errors):
                    print(messages, file=sys.stderr)
                    print("Application reported a JavaFX or dependency startup error.", file=sys.stderr)
                    return 1

                database = database_path(workspace)
                if not database.is_file():
                    print(messages, file=sys.stderr)
                    print(f"Application did not create its database: {database}", file=sys.stderr)
                    return 1
                with sqlite3.connect(database) as connection:
                    integrity = connection.execute("PRAGMA integrity_check").fetchone()[0]
                    accounts = connection.execute("SELECT COUNT(*) FROM user_accounts").fetchone()[0]
                    requests = connection.execute("SELECT COUNT(*) FROM maintenance_requests").fetchone()[0]
                if (integrity, accounts, requests) != ("ok", 6, 6):
                    print(f"Unexpected workspace state: {integrity=}, {accounts=}, {requests=}",
                          file=sys.stderr)
                    return 1

                print("Application stayed open, seeded six accounts and requests, and passed SQLite integrity check.")
                return 0
            finally:
                if process.poll() is None:
                    process.terminate()
                    try:
                        process.wait(timeout=5)
                    except subprocess.TimeoutExpired:
                        process.kill()
                        process.wait(timeout=5)
                if os.name == "nt":
                    for cached_path in Path(workspace).rglob("*"):
                        cached_path.chmod(cached_path.stat().st_mode | stat.S_IWRITE)


if __name__ == "__main__":
    raise SystemExit(main())
