package com.foobnix.ai;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class SideMatterTest {

    @Test
    public void sideMatterTitles() {
        for (String title : Arrays.asList("Copyright", "Copyright and Credits", "Contributors", "Table of Contents",
                "Contents", "Index", "About the Author", "About the Authors and Reviewers", "ABOUT THE AUTHOR",
                "Other Books You May Enjoy", "Also by Morgan Housel", "Share Your Thoughts",
                "Join our book community on Discord", "Download a free PDF copy of this book", "Why subscribe?",
                "Acknowledgments", "Acknowledgements", "Dedication", "Bibliography", "References", "Glossary",
                "Praise for The Psychology of Money", "Index of Names", "Cover", "Title Page",
                "12. Index", "Chapter 14: Bibliography", "B. Glossary", "  Contents  ",
                "Mục lục", "Lời cảm ơn", "Về tác giả", "Tài liệu tham khảo", "Chỉ mục", "Bảng thuật ngữ")) {
            assertTrue(title, SideMatter.isSideTitle(title));
        }
    }

    @Test
    public void theBooksTextIsNotSideMatter() {
        for (String title : Arrays.asList("Preface", "Foreword", "Introduction", "Notes", "Endnotes", "Appendix A",
                "Epilogue", "Afterword", "Index Funds", "Contents of a Good Life", "Chapter 1: Copyright Law",
                "The Author's Dilemma", "Join the Revolution", "Bonus Chapter: Habits", "Glossary of Feelings Is a Myth",
                "Lời nói đầu", "Lời giới thiệu", "Phụ lục", "Chú thích", "", null, "Chapter 3")) {
            assertFalse(String.valueOf(title), SideMatter.isSideTitle(title));
        }
    }

    @Test
    public void copyrightPagesNearTheEnds() {
        final List<String> page = Arrays.asList("The Psychology of Money", "Copyright © 2020 Morgan Housel",
                "All rights reserved.", "ISBN 978-0-85719-768-9");
        assertTrue(SideMatter.isSidePage(page, 3, 300));
        assertTrue("the ads at the end", SideMatter.isSidePage(
                Collections.singletonList("Learn Python ISBN: 9781803234567"), 290, 300));
        assertFalse("a page in the middle of the book is its text", SideMatter.isSidePage(page, 150, 300));
        assertFalse(SideMatter.isSidePage(Arrays.asList("Money is the greatest show on earth.",
                "It rewards patience."), 3, 300));
    }

    @Test
    public void contentsAndIndexPagesAreListsOfPageNumbers() {
        final List<String> contents = new ArrayList<>();
        contents.add("Contents");
        for (int i = 1; i <= 12; i++) {
            contents.add("Chapter " + i + " A title of the chapter ........ " + (i * 17));
        }
        assertTrue(SideMatter.isSidePage(contents, 5, 300));

        final List<String> index = Arrays.asList("Index", "anchoring, 12, 45–47", "Buffett, Warren, 3, 88",
                "compounding, 51", "confounding compounding, 52-55", "debt, 101", "fat tails, 140",
                "freedom, 9, 77", "greed, xii", "luck and risk, 31");
        assertTrue(SideMatter.isSidePage(index, 295, 300));

        final List<String> text = Arrays.asList("In 1999 the market rose by 21", "percent and most people",
                "thought it would go on rising", "for ever. It did not, and by 2002", "the losses had wiped out",
                "the gains of the decade before", "for many of them, even those", "who had been careful.");
        assertFalse("prose ending in a number now and then", SideMatter.isSidePage(text, 5, 300));
        assertFalse("not at either end", SideMatter.isSidePage(contents, 150, 300));
        assertFalse("too few lines to tell", SideMatter.isSidePage(Arrays.asList("Part One 12", "Part Two 40"), 5, 300));
    }

    @Test
    public void aShortBooksFrontIsItsFirstPages() {
        final List<String> page = Collections.singletonList("All rights reserved.");
        assertTrue(SideMatter.isSidePage(page, 8, 20));
        assertTrue(SideMatter.isSidePage(page, 16, 20));
    }
}
