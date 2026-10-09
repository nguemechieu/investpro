package org.investpro.ai;

import org.junit.jupiter.api.Test;
import java.net.http.HttpClient;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AssistantVoiceTextTest {
    @Test void shortResponseIsOneChunk() {
        assertEquals(java.util.List.of("The entire answer."), AssistantVoice.speechChunks("The entire answer."));
    }

    @Test void paragraphsTakePriorityOverLaterSentences() {
        String paragraph = "a".repeat(900) + "\r\n \r\n";
        String text = paragraph + "b".repeat(300) + ". " + "c".repeat(1600);
        assertEquals(paragraph, AssistantVoice.speechChunks(text).getFirst());
        assertEquals(text, String.join("", AssistantVoice.speechChunks(text)));
    }

    @Test void sentencesTakePriorityOverLaterWhitespace() {
        String sentence = "a".repeat(1000) + "! ";
        String text = sentence + "b".repeat(300) + " " + "c".repeat(1600);
        assertEquals(sentence, AssistantVoice.speechChunks(text).getFirst());
        assertEquals(text, String.join("", AssistantVoice.speechChunks(text)));
    }

    @Test void whitespaceAndHardLimitAreFallbacks() {
        String text = "a".repeat(1000) + " " + "b".repeat(2000);
        assertEquals(1001, AssistantVoice.speechChunks(text).getFirst().length());
        assertEquals(text, String.join("", AssistantVoice.speechChunks(text)));
        assertEquals(1500, AssistantVoice.speechChunks("a".repeat(3000)).getFirst().length());
    }

    @Test void decimalPricesDoNotBecomeSentenceBoundaries() {
        String text = "a".repeat(1000) + " 0.00123 " + "b".repeat(2000);
        assertTrue(AssistantVoice.speechChunks(text).getFirst().endsWith("0.00123 "));
    }

    @Test void surrogatePairsAreNeverSplitAtTheHardLimit() {
        String text = "x".repeat(1499) + "\uD83D\uDCC8" + "x".repeat(2000);
        var chunks = AssistantVoice.speechChunks(text);
        assertEquals(text, String.join("", chunks));
        for (String chunk : chunks) {
            assertTrue(chunk.length() <= 1500);
            assertFalse(Character.isHighSurrogate(chunk.charAt(chunk.length() - 1)));
            assertFalse(Character.isLowSurrogate(chunk.charAt(0)));
        }
    }

    @Test void markdownFormattingIsRemovedButReadableTextAndCodeRemain() {
        String markdown = "# Analysis\n**Bitcoin** is trading at `$65,000`. See [source](https://example.com).\n"
                + "```java\nint total = a * b;\n```\nFinal paragraph.";
        assertEquals("Analysis\nBitcoin is trading at $65,000. See source (https://example.com).\n"
                + "\nint total = a * b;\n\nFinal paragraph.", AssistantVoice.sanitizeForSpeech(markdown));
    }

    @Test void plainTextAndFinancialNotationArePreserved() {
        String text = "P/L: -1.23%. EPS $0.00123. 2 * 3 = 6.\n\nLast line.";
        assertEquals(text, AssistantVoice.sanitizeForSpeech(text));
        assertEquals("Call __init__ and multiply a * b.",
                AssistantVoice.sanitizeForSpeech("Call `__init__` and multiply ```a * b```."));
    }

    @Test void blankInputDoesNotContactTheApi() throws Exception {
        var client = mock(HttpClient.class);
        try (var voice = new AssistantVoice(client, () -> "unused")) {
            voice.speak(null); voice.speak(""); voice.speak(" \n\t");
        }
        verifyNoInteractions(client);
        assertTrue(AssistantVoice.speechChunks(null).isEmpty());
    }

    @Test void gainDefaultsToFourDbAndRejectsInvalidValues() {
        try (var voice = new AssistantVoice(mock(HttpClient.class), () -> "unused")) {
            assertEquals(4.0f, voice.getPlaybackGainDb());
            voice.setPlaybackGainDb(-6); assertEquals(-6, voice.getPlaybackGainDb());
            assertThrows(IllegalArgumentException.class, () -> voice.setPlaybackGainDb(Float.NaN));
            assertThrows(IllegalArgumentException.class, () -> voice.setPlaybackGainDb(Float.POSITIVE_INFINITY));
        }
    }
}
