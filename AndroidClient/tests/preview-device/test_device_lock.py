#!/usr/bin/env python3
"""Exercise actual cross-process locking without contacting a device."""
import subprocess
import sys
import unittest
import uuid
from pathlib import Path

from device_lock import device_package_lock


class DeviceLockTest(unittest.TestCase):
    def test_same_device_and_package_rejected_until_owner_exits(self):
        serial = "synthetic-" + uuid.uuid4().hex
        package = "io.github.n2ray.sall"
        code = (
            "import sys; from device_lock import device_package_lock; "
            "guard=device_package_lock(sys.argv[1], sys.argv[2]); guard.__enter__(); "
            "print('locked', flush=True); sys.stdin.readline(); guard.__exit__(None,None,None)"
        )
        process = subprocess.Popen([sys.executable, "-c", code, serial, package],
                                   stdin=subprocess.PIPE, stdout=subprocess.PIPE, text=True,
                                   cwd=Path(__file__).resolve().parent)
        try:
            self.assertEqual(process.stdout.readline().strip(), "locked")
            with self.assertRaisesRegex(RuntimeError, "Another preview probe"):
                with device_package_lock(serial, package):
                    self.fail("same target should be locked")
            with device_package_lock(serial, package + ".other"):
                pass
        finally:
            process.stdin.close()
            process.wait(timeout=10)
            process.stdout.close()
        self.assertEqual(process.returncode, 0)
        with device_package_lock(serial, package):
            pass


if __name__ == "__main__":
    unittest.main()
