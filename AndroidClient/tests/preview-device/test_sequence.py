import unittest

from probe_sequence import AUTH_REQUIRED, SAME_PROCESS_FAILED, CleanupFailure, run_sequence


class ProbeSequenceTest(unittest.TestCase):
    def test_crash_sequence_checks_both_recovery_points(self):
        calls = []
        self.assertEqual(run_sequence(lambda name: calls.append(name) or True, crash=True), "passed")
        self.assertEqual(calls, ["claim", "prepare", "write", "crash_before", "verify_before", "crash_after", "verify_after", "read", "cleanup"])

    def test_cleanup_after_claim_timeout(self):
        calls = []

        def phase(name):
            calls.append(name)
            if name == "claim":
                raise TimeoutError("response lost after claim")
            return True

        with self.assertRaises(TimeoutError):
            run_sequence(phase)
        self.assertEqual(calls, ["claim", "cleanup"])

    def test_skip_and_read_failure_cleanup(self):
        calls = []
        run_sequence(lambda name: calls.append(name) or (name != "claim"))
        self.assertEqual(calls, ["claim", "cleanup"])
        calls.clear()

        def fail_read(name):
            calls.append(name)
            if name == "read":
                raise RuntimeError("read failure")
            return True

        with self.assertRaises(RuntimeError):
            run_sequence(fail_read)
        self.assertEqual(calls, ["claim", "prepare", "write", "read", "cleanup"])

    def test_authentication_is_opt_in_and_retries_same_phase(self):
        calls = []

        def needs_auth(name):
            calls.append(name)
            return AUTH_REQUIRED if name == "write" else True

        self.assertEqual(run_sequence(needs_auth), "authentication_required")
        self.assertEqual(calls, ["claim", "prepare", "write", "cleanup"])
        calls.clear()

        def authenticated(name):
            calls.append(name)
            return AUTH_REQUIRED if name == "write" and calls.count("write") == 1 else True

        self.assertEqual(run_sequence(authenticated, authenticate=True), "passed")
        self.assertEqual(calls, ["claim", "prepare", "write", "auth", "write", "read", "cleanup"])

    def test_auth_cancel_and_still_unauthorized_are_not_pass(self):
        calls = []

        def cancelled(name):
            calls.append(name)
            if name == "write": return AUTH_REQUIRED
            return name != "auth"

        self.assertEqual(run_sequence(cancelled, authenticate=True), "skipped")
        self.assertEqual(calls, ["claim", "prepare", "write", "auth", "cleanup"])
        calls.clear()

        def still_unauthorized(name):
            calls.append(name)
            return AUTH_REQUIRED if name == "write" else True

        self.assertEqual(run_sequence(still_unauthorized, authenticate=True), "authentication_required")
        self.assertEqual(calls, ["claim", "prepare", "write", "auth", "write", "cleanup"])

    def test_cleanup_failure_is_reported(self):
        def phase(name):
            if name == "cleanup":
                raise TimeoutError("device offline")
            return True

        with self.assertRaises(CleanupFailure):
            run_sequence(phase)

    def test_same_process_failure_still_checks_cross_process_and_cleans(self):
        calls = []
        def phase(name):
            calls.append(name)
            if name == "write" and calls.count("write") == 1: return AUTH_REQUIRED
            if name == "auth": return SAME_PROCESS_FAILED
            return True
        self.assertEqual(run_sequence(phase, authenticate=True), "same_process_failed")
        self.assertEqual(calls, ["claim", "prepare", "write", "auth", "write", "read", "cleanup"])

    def test_credential_only_prompts_even_if_key_already_authorized(self):
        calls = []
        self.assertEqual(run_sequence(lambda name: calls.append(name) or True, credential_only=True), "passed")
        self.assertEqual(calls, ["claim", "prepare", "auth", "write", "read", "cleanup"])

    def test_credential_only_cancel_and_failure_keep_cleanup(self):
        calls = []
        def cancelled(name):
            calls.append(name)
            return name != "auth"
        self.assertEqual(run_sequence(cancelled, credential_only=True), "skipped")
        self.assertEqual(calls, ["claim", "prepare", "auth", "cleanup"])
        calls.clear()
        def needs_auth(name):
            calls.append(name)
            return AUTH_REQUIRED if name == "write" else True
        self.assertEqual(run_sequence(needs_auth, credential_only=True), "authentication_required")
        self.assertEqual(calls, ["claim", "prepare", "auth", "write", "cleanup"])
        with self.assertRaises(ValueError):
            run_sequence(lambda name: True, authenticate=True, credential_only=True)


if __name__ == "__main__":
    unittest.main()
