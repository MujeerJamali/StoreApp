package com.mujeer.businesserp;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Covers the token-matching rule every list/dropdown search box in the app
 * relies on (SearchUtils.matchesTokensAcrossFields): every typed word must
 * appear somewhere, in any order, across the given fields.
 */
public class SearchUtilsTest {

	@Test
	public void nullQuery_matchesEverything() {
		assertTrue(SearchUtils.matchesTokensAcrossFields(null, "anything"));
	}

	@Test
	public void emptyQuery_matchesEverything() {
		assertTrue(SearchUtils.matchesTokensAcrossFields("", "anything"));
	}

	@Test
	public void blankQuery_matchesEverything() {
		assertTrue(SearchUtils.matchesTokensAcrossFields("   ", "anything"));
	}

	@Test
	public void singleToken_matchesWhenSubstringPresent() {
		assertTrue(SearchUtils.matchesTokensAcrossFields("soft", "Shoe Simple Soft Kapro"));
	}

	@Test
	public void singleToken_caseInsensitive() {
		assertTrue(SearchUtils.matchesTokensAcrossFields("SOFT", "shoe simple soft kapro"));
	}

	@Test
	public void singleToken_noMatch_returnsFalse() {
		assertFalse(SearchUtils.matchesTokensAcrossFields("xyz", "Shoe Simple Soft Kapro"));
	}

	@Test
	public void multipleTokens_outOfOrder_stillMatch() {
		// The exact motivating example from the class javadoc: both words
		// appear, just not next to each other.
		assertTrue(SearchUtils.matchesTokensAcrossFields(
			"Soft 7-10", "Shoe Simple Soft Kapro 2pata Black M 7-10"));
	}

	@Test
	public void multipleTokens_oneMissing_returnsFalse() {
		assertFalse(SearchUtils.matchesTokensAcrossFields(
			"Soft Missing", "Shoe Simple Soft Kapro 2pata Black M 7-10"));
	}

	@Test
	public void tokensMayEachMatchADifferentField() {
		// e.g. item code in one field, name in another - a token only
		// needs to be found in *some* field, not all of them.
		assertTrue(SearchUtils.matchesTokensAcrossFields("A001 chair", "A001", "Office Chair"));
	}

	@Test
	public void nullFieldInList_isSkippedSafely() {
		assertTrue(SearchUtils.matchesTokensAcrossFields("chair", (String) null, "Office Chair"));
	}

	@Test
	public void extraWhitespaceBetweenTokens_isIgnored() {
		assertTrue(SearchUtils.matchesTokensAcrossFields("soft   7-10", "Soft Kapro 7-10"));
	}

	@Test
	public void noFieldsGiven_withNonEmptyQuery_returnsFalse() {
		assertFalse(SearchUtils.matchesTokensAcrossFields("anything"));
	}
}
