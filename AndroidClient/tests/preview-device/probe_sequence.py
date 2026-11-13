"""Ensure synthetic cleanup runs even if claim, write, or read is interrupted."""


class CleanupFailure(RuntimeError):
    pass


AUTH_REQUIRED = object()
SAME_PROCESS_FAILED = object()


def run_sequence(phase, authenticate=False):
    claim_attempted = False
    try:
        claim_attempted = True
        if not phase("claim") or not phase("prepare"):
            return "skipped"
        same_process_failed = False
        written = phase("write")
        if written is AUTH_REQUIRED:
            if not authenticate:
                return "authentication_required"
            authenticated = phase("auth")
            if not authenticated:
                return "skipped"
            same_process_failed = authenticated is SAME_PROCESS_FAILED
            written = phase("write")
            if written is AUTH_REQUIRED:
                return "authentication_required"
        if not written:
            return "skipped"
        if not phase("read"):
            return "skipped"
        return "same_process_failed" if same_process_failed else "passed"
    finally:
        if claim_attempted:
            try:
                if not phase("cleanup"):
                    raise RuntimeError("Synthetic cleanup unexpectedly skipped")
            except BaseException as error:
                raise CleanupFailure("Synthetic cleanup failed") from error
