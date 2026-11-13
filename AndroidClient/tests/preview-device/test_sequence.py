import unittest

from probe_sequence import CleanupFailure, run_sequence


class ProbeSequenceTest(unittest.TestCase):
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
        self.assertEqual(calls, ["claim", "write", "read", "cleanup"])

    def test_cleanup_failure_is_reported(self):
        def phase(name):
            if name == "cleanup":
                raise TimeoutError("device offline")
            return True

        with self.assertRaises(CleanupFailure):
            run_sequence(phase)


if __name__ == "__main__":
    unittest.main()
