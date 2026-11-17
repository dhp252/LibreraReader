package com.foobnix.assist.fiction;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class FictionNamesTest {

    private static FictionCast.Person person(String name, boolean minor, String... aliases) {
        final FictionCast.Person p = new FictionCast.Person();
        p.id = FictionCast.slug(name);
        p.name = name;
        p.minor = minor;
        p.aliases.addAll(Arrays.asList(aliases));
        return p;
    }

    private static List<FictionCast.Person> people() {
        final List<FictionCast.Person> all = new ArrayList<>();
        all.add(person("Phileas Fogg", false, "Mr. Fogg", "the gentleman"));
        all.add(person("Passepartout", false, "Jean Passepartout"));
        all.add(person("Aouda", false, "Mrs. Aouda"));
        all.add(person("Fix", false, "the detective"));
        all.add(person("Sir Francis Cromarty", true));
        all.add(person("Lord Albemarle", true));
        return all;
    }

    private static String name(String selection) {
        final FictionCast.Person p = FictionNames.match(people(), selection);
        return p == null ? null : p.name;
    }

    @Test
    public void aNameOrAnAliasInAnyCase() {
        assertEquals("Aouda", name("Aouda"));
        assertEquals("Aouda", name("AOUDA"));
        assertEquals("Aouda", name(" aouda, "));
        assertEquals("Phileas Fogg", name("Mr. Fogg"));
        assertEquals("Phileas Fogg", name("mr fogg"));
        assertEquals("Fix", name("The detective"));
        assertEquals("Fix", name("detective"));
    }

    @Test
    public void aPossessiveIsTheName() {
        assertEquals("Aouda", name("Aouda’s"));
        assertEquals("Aouda", name("Aouda's"));
        assertEquals("Phileas Fogg", name("Phileas Fogg’s"));
    }

    @Test
    public void wholeWordsOfAName() {
        assertEquals("Phileas Fogg", name("Fogg"));
        assertEquals("Phileas Fogg", name("Phileas"));
        assertEquals("Sir Francis Cromarty", name("Cromarty"));
        // part of a word is no name
        assertNull(name("Fog"));
        assertNull(name("Aoud"));
        assertNull(name("Fixed"));
        assertNull(name("pass"));
    }

    @Test
    public void aTitleAloneNamesNobody() {
        assertNull(name("Mr."));
        assertNull(name("Sir"));
        assertNull(name("the"));
        assertNull(name("Lord"));
    }

    @Test
    public void aFewWordsHoldingTheWholeName() {
        assertEquals("Passepartout", name("said Passepartout"));
        assertEquals("Phileas Fogg", name("Phileas Fogg understood"));
        // a passage is no name
        assertNull(name("Phileas Fogg understood what was passing in Aouda’s mind"));
        // two people in it: nobody
        assertNull(name("Fogg and Aouda"));
    }

    @Test
    public void nothingForOtherWordsOrNoPeople() {
        assertNull(name("elephant"));
        assertNull(name(""));
        assertNull(name(null));
        assertNull(FictionNames.match(null, "Aouda"));
        assertNull(FictionNames.match(new ArrayList<FictionCast.Person>(), "Aouda"));
    }

    @Test
    public void ofTwoWithTheSameNameTheMainOneAndElseNobody() {
        final List<FictionCast.Person> all = new ArrayList<>();
        all.add(person("John Bennet", true));
        all.add(person("Elizabeth Bennet", false));
        assertEquals("Elizabeth Bennet", FictionNames.match(all, "Bennet").name);
        all.add(person("Jane Bennet", false));
        assertNull(FictionNames.match(all, "Bennet"));
        assertEquals("Jane Bennet", FictionNames.match(all, "Jane").name);
    }
}
