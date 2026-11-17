package com.foobnix.assist.xray;

import com.foobnix.ai.AiCategory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The argument map of a chapter, built from its stored key-passage ideas: each idea under the one
 * it supports (main claim → why → evidence, examples; caveats last among their siblings), in
 * reading order otherwise.
 */
public final class XrayTree {

    /** What a box of the map is called. */
    public enum Role {
        MAIN_CLAIM, CLAIM, WHY, EVIDENCE, EXAMPLE, CAVEAT, CONCEPT, APPLY
    }

    public static final class Node {
        public final XrayIdea idea;
        public final int depth;
        public final Role role;
        public final Node up;
        public final List<Node> children = new ArrayList<>();

        Node(XrayIdea idea, int depth, Role role, Node up) {
            this.idea = idea;
            this.depth = depth;
            this.role = role;
            this.up = up;
        }

        /** The box at the top of its branch. */
        public Node root() {
            Node n = this;
            while (n.up != null) {
                n = n.up;
            }
            return n;
        }
    }

    public final List<Node> roots = new ArrayList<>();
    /** Every box, top to bottom as the map draws them. */
    public final List<Node> order = new ArrayList<>();
    private final Map<Integer, Node> byId = new HashMap<>();

    private XrayTree() {
    }

    /** The map of a chapter's ideas (in reading order). A parent that is unknown, itself, or in a loop makes a root. */
    public static XrayTree build(List<XrayIdea> ideas) {
        final XrayTree tree = new XrayTree();
        final Map<Integer, XrayIdea> ids = new LinkedHashMap<>();
        for (XrayIdea idea : ideas) {
            if (!ids.containsKey(idea.id)) {
                ids.put(idea.id, idea);
            }
        }
        // the parent each idea hangs under, loops cut where they close
        final Map<Integer, Integer> parentOf = new HashMap<>();
        for (XrayIdea idea : ids.values()) {
            int parent = idea.parent;
            if (parent == idea.id || !ids.containsKey(parent)) {
                parent = -1;
            }
            parentOf.put(idea.id, parent);
        }
        for (Integer id : ids.keySet()) {
            final Set<Integer> seen = new HashSet<>();
            int at = id;
            while (at >= 0 && seen.add(at)) {
                at = parentOf.get(at);
            }
            if (at >= 0) {
                // a loop: the idea where it closes becomes a root
                parentOf.put(at, -1);
            }
        }
        final Map<Integer, List<XrayIdea>> childrenOf = new HashMap<>();
        final List<XrayIdea> top = new ArrayList<>();
        for (XrayIdea idea : ids.values()) {
            final int parent = parentOf.get(idea.id);
            if (parent < 0) {
                top.add(idea);
            } else {
                List<XrayIdea> list = childrenOf.get(parent);
                if (list == null) {
                    list = new ArrayList<>();
                    childrenOf.put(parent, list);
                }
                list.add(idea);
            }
        }
        boolean mainSeen = false;
        for (XrayIdea idea : caveatsLast(top)) {
            final Role role;
            if (idea.category == AiCategory.MAIN) {
                role = mainSeen ? Role.CLAIM : Role.MAIN_CLAIM;
                mainSeen = true;
            } else {
                role = roleOf(idea.category, 0);
            }
            final Node node = tree.add(idea, 0, role, null);
            tree.roots.add(node);
            tree.addChildren(node, childrenOf);
        }
        return tree;
    }

    private Node add(XrayIdea idea, int depth, Role role, Node up) {
        final Node node = new Node(idea, depth, role, up);
        byId.put(idea.id, node);
        order.add(node);
        if (up != null) {
            up.children.add(node);
        }
        return node;
    }

    private void addChildren(Node node, Map<Integer, List<XrayIdea>> childrenOf) {
        final List<XrayIdea> children = childrenOf.get(node.idea.id);
        if (children == null) {
            return;
        }
        for (XrayIdea child : caveatsLast(children)) {
            addChildren(add(child, node.depth + 1, roleOf(child.category, node.depth + 1), node), childrenOf);
        }
    }

    private static List<XrayIdea> caveatsLast(List<XrayIdea> ideas) {
        final List<XrayIdea> out = new ArrayList<>();
        final List<XrayIdea> caveats = new ArrayList<>();
        for (XrayIdea idea : ideas) {
            (idea.category == AiCategory.CAVEAT ? caveats : out).add(idea);
        }
        out.addAll(caveats);
        return out;
    }

    static Role roleOf(AiCategory category, int depth) {
        if (category == null) {
            return depth == 0 ? Role.CLAIM : Role.WHY;
        }
        switch (category) {
            case EVIDENCE:
                return Role.EVIDENCE;
            case EXAMPLE:
                return Role.EXAMPLE;
            case CAVEAT:
                return Role.CAVEAT;
            case CONCEPT:
                return Role.CONCEPT;
            case APPLY:
                return Role.APPLY;
            default:
                return depth == 0 ? Role.CLAIM : Role.WHY;
        }
    }

    public boolean isEmpty() {
        return order.isEmpty();
    }

    public Node node(int id) {
        return byId.get(id);
    }

    /** The chapter's main claim, or null when no idea of it is a main idea at the top. */
    public Node mainClaim() {
        for (Node n : roots) {
            if (n.role == Role.MAIN_CLAIM) {
                return n;
            }
        }
        return null;
    }

    public Set<Integer> ids() {
        return Collections.unmodifiableSet(byId.keySet());
    }

    /** How many ideas do each job (claim, evidence, example, caveat, other). */
    public Map<XrayKind, Integer> counts() {
        final Map<XrayKind, Integer> out = new LinkedHashMap<>();
        for (XrayKind kind : XrayKind.values()) {
            out.put(kind, 0);
        }
        for (Node n : order) {
            final XrayKind kind = n.idea.kind();
            out.put(kind, out.get(kind) + 1);
        }
        return out;
    }

    /**
     * The causes the weak spot card compares: the boxes under the same top box as {@code id}
     * (caveats left out), or the top boxes when it is one; at most {@code max}.
     */
    public List<Node> causesAround(int id, int max) {
        final Node node = byId.get(id);
        final List<Node> from;
        if (node == null) {
            final Node main = mainClaim();
            from = main != null ? main.children : roots;
        } else if (node.up == null) {
            from = roots;
        } else {
            from = node.root().children;
        }
        final List<Node> out = new ArrayList<>();
        for (Node n : from) {
            if (n.role != Role.CAVEAT && out.size() < max) {
                out.add(n);
            }
        }
        return out;
    }

    /** The cause in {@code causes} that holds {@code id} (itself or below it), or null. */
    public static Node causeHolding(List<Node> causes, Node node) {
        for (Node n = node; n != null; n = n.up) {
            if (causes.contains(n)) {
                return n;
            }
        }
        return null;
    }

    /**
     * How well a box is supported: its own rating, else the weakest of the boxes under it; null
     * when nothing of it is rated.
     */
    public static XrayRatings.Rating strength(Node node, XrayRatings ratings) {
        if (ratings == null) {
            return null;
        }
        final XrayRatings.Rating own = ratings.rating(node.idea.id);
        if (own != null) {
            return own;
        }
        XrayRatings.Rating weakest = null;
        for (Node child : node.children) {
            if (child.role == Role.CAVEAT) {
                continue;
            }
            final XrayRatings.Rating r = strength(child, ratings);
            if (r != null && r.weakerThan(weakest)) {
                weakest = r;
            }
        }
        return weakest;
    }
}
