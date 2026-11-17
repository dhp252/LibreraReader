package com.foobnix.assist.debate;

import com.foobnix.ai.AiCategory;
import com.foobnix.assist.xray.XrayIdea;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class DebatePromptsTest {

    @Test
    public void theSystemPromptNamesTheAuthorTheRefereeAndTheAnswer() {
        final String system = DebatePrompts.system("Adam Smith", false, "Vietnamese");
        assertTrue(system.contains("as Adam Smith, in the first person"));
        assertTrue(system.contains("at most one direct quote"));
        assertTrue(system.contains("the referee: neutral"));
        assertTrue(system.contains("\"reader\", \"author\", or \"draw\""));
        assertTrue(system.contains("{\"position\":\"...\",\"reply\":\"...\",\"quote\":\"\",\"idea\":3,\"verdict\":"
                + "{\"point\":\"reader\",\"why\":\"...\",\"next\":\"...\"},\"crux\":\"\"}"));
        assertTrue(system.contains("in Vietnamese;"));
        assertTrue(system.contains("next: one question for the next round"));
        assertFalse(system.contains("This is the last round"));
    }

    @Test
    public void theLastRoundAsksWhereTheyReallyDisagree() {
        final String last = DebatePrompts.system("", true, " ");
        assertTrue(last.contains("as the author of the book, in the first person"));
        assertTrue(last.contains("This is the last round"));
        assertTrue(last.contains("where the reader and the author really disagree"));
        assertFalse(last.contains("next: one question for the next round"));
        assertTrue(last.contains("in the language the reader writes in;"));
    }

    @Test
    public void theFirstRequestHoldsTheIdeasTheirMarksAndTheMessage() {
        final DebateSession s = DebateSessionTest.session();
        final String user = DebatePrompts.user(s, "One tiny step,\nall day? Your pin shop makes people worse off.", 600_000);
        assertTrue(user.startsWith("Book: The Wealth of Nations\nAuthor: Adam Smith\nChapter: Of the Division of Labour\n"));
        assertTrue(user.contains("1 [main]: Dividing work multiplies what people make\n"
                + "   \"The greatest improvement in the productive powers of labour\"\n"));
        assertTrue(user.contains("2 [evidence] supports 1: Ten men make 48,000 pins a day\n"));
        assertTrue(user.contains("a second mark of the pin shop"));
        assertFalse(user.contains("a third mark that is never sent"));
        assertTrue(user.contains("4 [caveat] supports 1: One task for life dulls the mind\n"));
        assertTrue(user.contains("The claim under debate: idea 1: Dividing work multiplies what people make\n"));
        assertTrue(user.contains("The reader's position: It makes workers' lives worse\n"));
        assertFalse(user.contains("The author's position"));
        assertFalse(user.contains("The debate so far"));
        assertTrue(user.endsWith("\nRound 1 of 3\nReader: One tiny step, all day? Your pin shop makes people worse off.\n"));
        // the ideas come before the debate
        assertTrue(user.indexOf("Ideas of the chapter") < user.indexOf("The claim under debate"));
    }

    @Test
    public void laterRequestsHoldTheDebateSoFar() {
        final DebateSession s = DebateSessionTest.session();
        s.add(new DebateRound("Your pin shop makes people worse off.",
                Arrays.asList(new DebateQuotes.Part("Worse off? I wrote of ", false), new DebateQuotes.Part("forty-eight thousand pins", true),
                        new DebateQuotes.Part(".", false)), "forty-eight thousand pins", 4, true, "Of the Division of Labour",
                DebateReply.Point.AUTHOR, "Smith brings the numbers.", "Is richer the same as better off?"),
                "It makes everyone richer", "");
        s.add(new DebateRound("Richer, maybe. But duller.", Collections.singletonList(new DebateQuotes.Part("There I agree.", false)),
                null, -1, false, "", DebateReply.Point.READER, "Smith concedes the harm.", ""), "", "");
        final String user = DebatePrompts.user(s, "Is one school enough?", 600_000);
        assertTrue(user.contains("The author's position: It makes everyone richer\n"));
        assertTrue(user.contains("\nThe debate so far:\nRound 1\nReader: Your pin shop makes people worse off.\n"
                + "Adam Smith: Worse off? I wrote of “forty-eight thousand pins”.\n"
                + "Referee: point to author. Smith brings the numbers. Next: Is richer the same as better off?\n"
                + "Round 2\nReader: Richer, maybe. But duller.\nAdam Smith: There I agree.\n"
                + "Referee: point to reader. Smith concedes the harm.\n"));
        assertTrue(user.endsWith("\nRound 3 of 3 (the last round)\nReader: Is one school enough?\n"));
        assertEquals(1, count(user, "The debate so far:"));
    }

    @Test
    public void anUnknownAuthorAndALongMessage() {
        final List<XrayIdea> ideas = DebateSessionTest.ideas();
        final DebateSession s = new DebateSession("", "k", "", DebateAuthor.of(""), ideas, ideas.get(2));
        s.readerPosition = "No";
        s.add(new DebateRound("m", Collections.singletonList(new DebateQuotes.Part("r", false)), null, -1, false, "",
                DebateReply.Point.DRAW, "", ""), "", "");
        final StringBuilder longMessage = new StringBuilder();
        for (int i = 0; i < 400; i++) {
            longMessage.append("word ");
        }
        final String user = DebatePrompts.user(s, longMessage.toString(), 600_000);
        assertFalse(user.contains("Book:"));
        assertFalse(user.contains("Author:"));
        assertFalse(user.contains("Chapter:"));
        assertTrue(user.contains("\nThe author: r\nReferee: point to draw\n"));
        final String sent = user.substring(user.lastIndexOf("Reader: ") + 8).trim();
        assertTrue(sent.length() <= DebateSession.MESSAGE_CHARS);
        assertTrue(sent.endsWith("…"));
    }

    @Test
    public void theIdeasShrinkToFitASmallRequest() {
        final List<XrayIdea> many = new ArrayList<>();
        final StringBuilder mark = new StringBuilder();
        for (int i = 0; i < 60; i++) {
            mark.append("mark ");
        }
        for (int i = 1; i <= 60; i++) {
            many.add(new XrayIdea(i, "Idea number " + i, i % 5 == 1 ? AiCategory.MAIN : AiCategory.EVIDENCE,
                    i % 5 == 1 ? -1 : i - (i - 1) % 5, true, i, Arrays.asList(mark.toString() + i, mark.toString() + "second" + i),
                    null, i));
        }
        // everything fits
        final String all = DebatePrompts.ideas(many, 46, 1_000_000);
        assertTrue(all.contains("second60"));
        // one shorter mark an idea
        final String one = DebatePrompts.ideas(many, 46, all.length() / 2);
        assertTrue(one.length() <= all.length() / 2);
        assertTrue(one.contains("60 [evidence] supports 56: Idea number 60\n"));
        assertFalse(one.contains("second"));
        // only what fits, the claim and the ideas around it first
        final String few = DebatePrompts.ideas(many, 46, 2000);
        assertTrue(few.length() <= 2000);
        assertTrue(few.startsWith("46 [main]: Idea number 46\n"));
        assertTrue(few.contains("47 [evidence] supports 46: Idea number 47\n"));
        assertTrue(few.contains("50 [evidence] supports 46"));
        assertFalse(few.contains("Idea number 60"));
        // the claim is sent even when nothing fits
        assertTrue(DebatePrompts.ideas(many, 46, 10).startsWith("46 [main]: Idea number 46\n"));

        // a request to a provider that takes little still carries the claim
        final DebateSession s = new DebateSession("B", "k", "C", DebateAuthor.of("A B"), many, many.get(45));
        s.readerPosition = "No";
        final String user = DebatePrompts.user(s, "msg", 3000);
        assertTrue(user.contains("46 [main]: Idea number 46\n"));
        assertTrue(user.length() < 3000 + DebatePrompts.MIN_IDEAS_CHARS);
    }

    private static int count(String s, String part) {
        int n = 0;
        for (int at = s.indexOf(part); at >= 0; at = s.indexOf(part, at + 1)) {
            n++;
        }
        return n;
    }
}
