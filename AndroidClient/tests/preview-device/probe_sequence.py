"""Ensure synthetic cleanup runs even if claim, write, or read is interrupted."""


class CleanupFailure(RuntimeError):
    pass


def run_sequence(phase):
    claim_attempted = False
    try:
        claim_attempted = True
        if phase("claim") and phase("write"):
            phase("read")
    finally:
        if claim_attempted:
            try:
                if not phase("cleanup"):
                    raise RuntimeError("Synthetic cleanup unexpectedly skipped")
            except BaseException as error:
                raise CleanupFailure("Synthetic cleanup failed") from error
