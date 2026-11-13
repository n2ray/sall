"""Cross-process exclusion for temporary replacement of a device's app."""
import contextlib
import fcntl
import hashlib
import os
from pathlib import Path
import stat


@contextlib.contextmanager
def device_package_lock(serial, package):
    # A fixed location prevents different TMPDIR values from bypassing exclusion.
    directory = Path("/tmp").resolve() / f"sill-preview-locks-{os.getuid()}"
    directory.mkdir(mode=0o700, exist_ok=True)
    info = directory.lstat()
    if not stat.S_ISDIR(info.st_mode) or info.st_uid != os.getuid() or info.st_mode & 0o077:
        raise RuntimeError("Unsafe preview lock directory")
    name = hashlib.sha256((serial + "\0" + package).encode()).hexdigest() + ".lock"
    fd = os.open(directory / name, os.O_CREAT | os.O_RDWR | os.O_NOFOLLOW, 0o600)
    try:
        info = os.fstat(fd)
        if not stat.S_ISREG(info.st_mode) or info.st_uid != os.getuid():
            raise RuntimeError("Unsafe preview lock file")
        try:
            fcntl.flock(fd, fcntl.LOCK_EX | fcntl.LOCK_NB)
        except BlockingIOError as exc:
            raise RuntimeError("Another preview probe is using this device and package") from exc
        yield
    finally:
        os.close(fd)
