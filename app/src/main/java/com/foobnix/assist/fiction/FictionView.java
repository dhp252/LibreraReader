package com.foobnix.assist.fiction;

import java.util.ArrayList;
import java.util.List;

/**
 * What the Who's who screen shows of a stored cast for a reader's place, free of Android: the
 * main people by group, the ties between people the reader may see, the minor people ("also
 * met"), and how many are hidden because the reader went back ({@link FictionWhere#BEFORE}).
 * <p>
 * When the reader went back, a person first met in a later chapter is in none of the lists, a tie
 * that began later (or has a hidden end) is left out, and a card carries where the person was
 * first met instead of their role, which was written with later chapters in mind.
 */
public final class FictionView {

    /** A main person's card. */
    public static final class Card {
        public final FictionCast.Person person;
        public final String initials;
        /** The one line under the name. */
        public final String line;

        Card(FictionCast.Person person, String line) {
            this.person = person;
            this.initials = person.initials();
            this.line = line;
        }
    }

    /** Main people sharing a group (a family, a crew); {@code name} is "" for those in none. */
    public static final class Group {
        public final String name;
        public final List<Card> cards = new ArrayList<>();

        Group(String name) {
            this.name = name;
        }
    }

    /** "Phileas Fogg — the £20,000 bet — Reform Club". */
    public static final class Tie {
        public final String a;
        public final String label;
        public final String b;

        Tie(String a, String label, String b) {
            this.a = a;
            this.label = label;
            this.b = b;
        }
    }

    public final List<Group> groups = new ArrayList<>();
    public final List<Tie> ties = new ArrayList<>();
    /** Minor people: "Sir Francis Cromarty · left at Benares". */
    public final List<Card> alsoMet = new ArrayList<>();
    /** People the reader may see, main and minor: what a selected name is matched against. */
    public final List<FictionCast.Person> visible = new ArrayList<>();
    /** People left out because the reader went back before they were met. */
    public int hidden;

    private FictionView() {
    }

    public static FictionView build(FictionCast cast, FictionChapters chapters, FictionWhere where) {
        final FictionView v = new FictionView();
        final boolean back = where.state == FictionWhere.BEFORE;
        boolean anyMain = false;
        for (FictionCast.Person p : cast.people) {
            if (!where.sees(firstIndex(cast, chapters, p))) {
                v.hidden++;
                continue;
            }
            v.visible.add(p);
            anyMain |= !p.minor;
        }
        for (FictionCast.Person p : v.visible) {
            final String met = firstMet(cast, chapters, p);
            if (p.minor && anyMain) {
                v.alsoMet.add(new Card(p, back || p.role.isEmpty() ? "" : p.role));
                continue;
            }
            final String line = back || p.role.isEmpty() ? (met.isEmpty() ? "" : "First met in " + met) : p.role;
            final String groupName = back ? "" : p.group;
            Group group = null;
            for (Group g : v.groups) {
                if (FictionCast.same(g.name, groupName)) {
                    group = g;
                    break;
                }
            }
            if (group == null) {
                group = new Group(groupName);
                // people in no group come first
                v.groups.add(groupName.isEmpty() ? 0 : v.groups.size(), group);
            }
            group.cards.add(new Card(p, line));
        }
        for (FictionCast.Relation r : cast.relations) {
            if (back && !where.sees(chapters.indexOf(r.key, r.ch, cast.mark.chapters))) {
                continue;
            }
            final String a = end(cast, v, r.a);
            final String b = end(cast, v, r.b);
            if (a != null && b != null && !FictionCast.same(a, b)) {
                v.ties.add(new Tie(a, r.label, b));
            }
        }
        return v;
    }

    /** The index, in the chapters at hand, of the chapter a person is first met in; -1 when unknown. */
    public static int firstIndex(FictionCast cast, FictionChapters chapters, FictionCast.Person p) {
        return chapters.indexOf(p.firstKey, p.first, cast.mark.chapters);
    }

    /** "Ch. 12" for the chapter a person is first met in, or "". */
    public static String firstMet(FictionCast cast, FictionChapters chapters, FictionCast.Person p) {
        return chapters.label(firstIndex(cast, chapters, p));
    }

    /** A person's story so far as the reader may see it: every line, or those up to the reader's chapter. */
    public static List<FictionCast.Moment> moments(FictionCast cast, FictionChapters chapters, FictionWhere where,
                                                   FictionCast.Person p) {
        final List<FictionCast.Moment> out = new ArrayList<>();
        for (FictionCast.Moment m : p.moments) {
            if (where.sees(chapters.indexOf(m.key, m.ch, cast.mark.chapters))) {
                out.add(m);
            }
        }
        return out;
    }

    /**
     * The name of one end of a tie: a visible person's name, or a group's name as given. Null when
     * it is a person the reader may not see (by id, name or alias), so the tie is left out.
     */
    private static String end(FictionCast cast, FictionView v, String end) {
        FictionCast.Person p = cast.person(end);
        if (p == null) {
            p = cast.byName(end);
        }
        if (p != null) {
            return v.visible.contains(p) ? p.name : null;
        }
        return end == null || end.trim().isEmpty() ? null : end.trim();
    }
}
