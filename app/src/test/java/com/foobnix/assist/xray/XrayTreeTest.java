package com.foobnix.assist.xray;

import com.foobnix.ai.AiCategory;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class XrayTreeTest {

    static XrayIdea idea(int id, AiCategory category, int parent) {
        return new XrayIdea(id, "idea " + id, category, parent, true, id, Collections.singletonList("words " + id),
                "words " + id, id);
    }

    /** The canvas's chapter: a claim, evidence, three causes with their evidence, a caveat read early. */
    static List<XrayIdea> smith() {
        return Arrays.asList(
                idea(1, AiCategory.MAIN, -1),
                idea(2, AiCategory.EVIDENCE, 1),
                idea(3, AiCategory.CAVEAT, 1),
                idea(4, AiCategory.MAIN, 1),
                idea(5, AiCategory.EVIDENCE, 4),
                idea(6, AiCategory.MAIN, 1),
                idea(7, AiCategory.EXAMPLE, 6),
                idea(8, AiCategory.MAIN, 1),
                idea(9, AiCategory.EVIDENCE, 8));
    }

    private static List<Integer> ids(List<XrayTree.Node> nodes) {
        final List<Integer> out = new ArrayList<>();
        for (XrayTree.Node n : nodes) {
            out.add(n.idea.id);
        }
        return out;
    }

    @Test
    public void claimThenCausesWithTheirEvidenceAndCaveatsLast() {
        final XrayTree tree = XrayTree.build(smith());
        assertEquals(Arrays.asList(1, 2, 4, 5, 6, 7, 8, 9, 3), ids(tree.order));
        assertEquals(XrayTree.Role.MAIN_CLAIM, tree.node(1).role);
        assertEquals(XrayTree.Role.EVIDENCE, tree.node(2).role);
        assertEquals(XrayTree.Role.WHY, tree.node(4).role);
        assertEquals(XrayTree.Role.EXAMPLE, tree.node(7).role);
        assertEquals(XrayTree.Role.CAVEAT, tree.node(3).role);
        assertEquals(2, tree.node(9).depth);
        assertSame(tree.node(1), tree.mainClaim());
    }

    @Test
    public void countsEachKind() {
        final Map<XrayKind, Integer> counts = XrayTree.build(smith()).counts();
        assertEquals(4, (int) counts.get(XrayKind.CLAIM));
        assertEquals(3, (int) counts.get(XrayKind.EVIDENCE));
        assertEquals(1, (int) counts.get(XrayKind.EXAMPLE));
        assertEquals(1, (int) counts.get(XrayKind.CAVEAT));
        assertEquals(0, (int) counts.get(XrayKind.OTHER));
    }

    @Test
    public void unknownSelfAndLoopingParentsMakeRoots() {
        final XrayTree tree = XrayTree.build(Arrays.asList(
                idea(1, AiCategory.EVIDENCE, 99),
                idea(2, AiCategory.MAIN, 2),
                idea(3, AiCategory.CONCEPT, 4),
                idea(4, AiCategory.EXAMPLE, 3)));
        assertEquals(3, tree.roots.size());
        assertEquals(4, tree.order.size());
        assertEquals(XrayTree.Role.EVIDENCE, tree.node(1).role);
        // the first main idea at the top is the main claim, even after another root
        assertEquals(XrayTree.Role.MAIN_CLAIM, tree.node(2).role);
        assertEquals(1, Math.abs(tree.node(3).depth - tree.node(4).depth));
    }

    @Test
    public void secondTopMainIdeaIsAClaim() {
        final XrayTree tree = XrayTree.build(Arrays.asList(idea(1, AiCategory.MAIN, -1), idea(2, AiCategory.MAIN, -1)));
        assertEquals(XrayTree.Role.MAIN_CLAIM, tree.node(1).role);
        assertEquals(XrayTree.Role.CLAIM, tree.node(2).role);
    }

    @Test
    public void causesAreTheBoxesUnderTheWeakSpotsClaim() {
        final XrayTree tree = XrayTree.build(smith());
        final List<XrayTree.Node> causes = tree.causesAround(9, 6);
        assertEquals(Arrays.asList(2, 4, 6, 8), ids(causes));
        assertSame(tree.node(8), XrayTree.causeHolding(causes, tree.node(9)));
        assertNull(XrayTree.causeHolding(causes, tree.node(1)));
        assertEquals(Arrays.asList(2, 4), ids(tree.causesAround(-1, 2)));
        assertEquals(Collections.singletonList(1), ids(tree.causesAround(1, 6)));
    }

    @Test
    public void strengthIsOwnRatingElseTheWeakestBelow() {
        final XrayTree tree = XrayTree.build(smith());
        final XrayRatings r = new XrayRatings();
        r.entries.put(5, new XrayRatings.Entry(5, XrayRatings.Rating.STRONG, "", ""));
        r.entries.put(9, new XrayRatings.Entry(9, XrayRatings.Rating.WEAK, "", ""));
        r.entries.put(6, new XrayRatings.Entry(6, XrayRatings.Rating.FAIR, "", ""));
        assertEquals(XrayRatings.Rating.STRONG, XrayTree.strength(tree.node(4), r));
        assertEquals(XrayRatings.Rating.FAIR, XrayTree.strength(tree.node(6), r));
        assertEquals(XrayRatings.Rating.WEAK, XrayTree.strength(tree.node(8), r));
        assertEquals(XrayRatings.Rating.WEAK, XrayTree.strength(tree.node(1), r));
        assertNull(XrayTree.strength(tree.node(3), r));
        assertNull(XrayTree.strength(tree.node(1), null));
    }

    @Test
    public void kindsOfCategories() {
        assertEquals(XrayKind.CLAIM, XrayKind.of(AiCategory.MAIN));
        assertEquals(XrayKind.EVIDENCE, XrayKind.of(AiCategory.EVIDENCE));
        assertEquals(XrayKind.EXAMPLE, XrayKind.of(AiCategory.EXAMPLE));
        assertEquals(XrayKind.CAVEAT, XrayKind.of(AiCategory.CAVEAT));
        assertEquals(XrayKind.OTHER, XrayKind.of(AiCategory.CONCEPT));
        assertEquals(XrayKind.OTHER, XrayKind.of(AiCategory.APPLY));
    }
}
