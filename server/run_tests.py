import sys
import unittest

from app.tests.test_whatsapp_parser import (
    test_whatsapp_parsing_bracketed_and_multiline,
    test_whatsapp_parsing_dash_and_system_filtering,
)
from app.tests.test_instagram_parser import test_instagram_parsing_reactions_and_shares
from app.tests.test_turn_merger_and_style import (
    test_consecutive_turn_merging,
    test_style_profiler_metrics,
)


class TestChatPilot(unittest.TestCase):
    def test_whatsapp_bracketed(self):
        test_whatsapp_parsing_bracketed_and_multiline()

    def test_whatsapp_dash_system(self):
        test_whatsapp_parsing_dash_and_system_filtering()

    def test_instagram_parser(self):
        test_instagram_parsing_reactions_and_shares()

    def test_turn_merging(self):
        test_consecutive_turn_merging()

    def test_style_metrics(self):
        test_style_profiler_metrics()


if __name__ == "__main__":
    suite = unittest.TestLoader().loadTestsFromTestCase(TestChatPilot)
    runner = unittest.TextTestRunner(verbosity=2)
    result = runner.run(suite)
    sys.exit(0 if result.wasSuccessful() else 1)
