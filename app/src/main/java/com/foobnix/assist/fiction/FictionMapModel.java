package com.foobnix.assist.fiction;

import java.util.ArrayList;
import java.util.List;

/**
 * What the places map draws for a stored timeline and a reader's place, free of Android. With at
 * least {@link FictionMapMath#MIN_LOCATED} visited places that have coordinates it is a map: those
 * places where they are, the route so far through them, and the plan's legs between places that
 * have coordinates. Otherwise it is a schematic route: every visited place in order of visit.
 * <p>
 * Invented places have no coordinates: on a map they are left out (they stay in the chips), in a
 * schematic route they stand in their order like the others.
 */
public final class FictionMapModel {

    public static final class Dot {
        public final String name;
        public final double lat;
        public final double lon;
        /** The story has been there (else it is only a stop of the plan). */
        public final boolean visited;

        Dot(String name, double lat, double lon, boolean visited) {
            this.name = name;
            this.lat = lat;
            this.lon = lon;
            this.visited = visited;
        }
    }

    /** A map with coordinates; false: the schematic route. */
    public final boolean geographic;
    public final List<Dot> dots = new ArrayList<>();
    /** The route so far: indexes into {@link #dots}, in order of visit. */
    public final List<Integer> route = new ArrayList<>();
    /** The plan's legs that can be drawn: pairs of indexes into {@link #dots}. */
    public final List<int[]> plan = new ArrayList<>();
    /** The dot the story is at, or -1 (no place yet, the reader went back, or a map cannot place where it is). */
    public final int here;

    private FictionMapModel(boolean geographic, int here) {
        this.geographic = geographic;
        this.here = here;
    }

    /**
     * @param visited  the places the reader may see, in order of visit
     * @param timeline for the plan's legs and stops; the plan is drawn only when {@code withPlan}
     * @param markHere whether the last place is where the story is now
     */
    public static FictionMapModel of(List<FictionTimeline.Place> visited, FictionTimeline timeline, boolean withPlan,
                                     boolean markHere) {
        final List<String> located = new ArrayList<>();
        for (FictionTimeline.Place p : visited) {
            if (p.located && !contains(located, p.name)) {
                located.add(p.name);
            }
        }
        final boolean geographic = located.size() >= FictionMapMath.MIN_LOCATED;
        final List<Dot> dots = new ArrayList<>();
        final List<Integer> route = new ArrayList<>();
        for (FictionTimeline.Place p : visited) {
            if (geographic && !p.located) {
                continue;
            }
            int at = geographic ? index(dots, p.name) : -1;
            if (at < 0) {
                dots.add(new Dot(p.name, p.lat, p.lon, true));
                at = dots.size() - 1;
            }
            if (route.isEmpty() || route.get(route.size() - 1) != at) {
                route.add(at);
            }
        }
        // on a map the story is marked only where it really is: not when its latest place has no coordinates
        final boolean lastDrawn = !visited.isEmpty() && (!geographic || visited.get(visited.size() - 1).located);
        final FictionMapModel m = new FictionMapModel(geographic,
                markHere && lastDrawn && !route.isEmpty() ? route.get(route.size() - 1) : -1);
        m.dots.addAll(dots);
        m.route.addAll(route);
        if (geographic && withPlan && timeline.plan != null) {
            for (FictionTimeline.Leg leg : timeline.plan.legs) {
                final int a = m.planDot(timeline, leg.from);
                final int b = m.planDot(timeline, leg.to);
                if (a >= 0 && b >= 0 && a != b) {
                    m.plan.add(new int[]{a, b});
                }
            }
        }
        return m;
    }

    /** The dot of an end of a plan's leg: one already there, else a stop of the plan that has coordinates. */
    private int planDot(FictionTimeline timeline, String name) {
        final int at = index(dots, name);
        if (at >= 0) {
            return at;
        }
        for (FictionTimeline.Place s : timeline.stops) {
            if (s.located && FictionCast.same(s.name, name)) {
                dots.add(new Dot(s.name, s.lat, s.lon, false));
                return dots.size() - 1;
            }
        }
        return -1;
    }

    private static int index(List<Dot> dots, String name) {
        for (int i = 0; i < dots.size(); i++) {
            if (FictionCast.same(dots.get(i).name, name)) {
                return i;
            }
        }
        return -1;
    }

    private static boolean contains(List<String> names, String name) {
        for (String n : names) {
            if (FictionCast.same(n, name)) {
                return true;
            }
        }
        return false;
    }
}
