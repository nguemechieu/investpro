import unittest
from types import SimpleNamespace

from predictor.model import HeuristicMarketPredictor


class HeuristicMarketPredictorTest(unittest.TestCase):
    def test_predicts_up_for_bullish_inputs(self):
        predictor = HeuristicMarketPredictor()
        request = SimpleNamespace(
            open=100.0,
            close=105.0,
            high=106.0,
            low=99.0,
            volume=1200.0,
            rsi=42.0,
            atr=1.5,
            macd=0.8,
            stoch=25.0,
            bb_upper=108.0,
            bb_lower=98.0,
        )

        prediction = predictor.predict(request)

        self.assertEqual("BUY", prediction.label)
        self.assertGreater(prediction.confidence, 0.5)
        self.assertGreater(prediction.probability_up, prediction.probability_down)

    def test_predicts_down_for_bearish_inputs(self):
        predictor = HeuristicMarketPredictor()
        request = SimpleNamespace(
            open=105.0,
            close=100.0,
            high=106.0,
            low=99.0,
            volume=1200.0,
            rsi=72.0,
            atr=1.5,
            macd=-0.8,
            stoch=88.0,
            bb_upper=101.0,
            bb_lower=92.0,
        )

        prediction = predictor.predict(request)

        self.assertEqual("SELL", prediction.label)
        self.assertGreater(prediction.confidence, 0.5)
        self.assertGreater(prediction.probability_down, prediction.probability_up)


if __name__ == "__main__":
    unittest.main()
