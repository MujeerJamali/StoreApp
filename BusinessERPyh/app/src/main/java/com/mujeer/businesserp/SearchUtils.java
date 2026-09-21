package com.mujeer.businesserp;

import java.util.Locale;

/**
 * Shared search-matching logic used by every list/dropdown filter in the
 * app (items, parties, transactions, payments, expenses).
 *
 * A plain "does the whole typed string appear as one substring" check
 * (the old behavior) fails as soon as the typed words don't appear
 * contiguously in the target text - e.g. typing "Soft 7-10" would not
 * match "Shoe Simple Soft Kapro 2pata Black M 7-10", even though both
 * words the user typed clearly do appear in it, just with other words
 * in between.
 *
 * matchesTokensAcrossFields() instead splits the typed query into
 * individual words and requires every one of them to show up
 * *somewhere* - in any order, in any of the given fields - which is
 * what makes "Soft 7-10" match a name like the one above (it contains
 * "soft" and it contains "7-10", just not next to each other).
 */
public class SearchUtils {

	private SearchUtils() {
	}

	// =====================
	// True if every whitespace-separated word in 'query' is found (as a
	// case-insensitive substring, in any order) somewhere across the
	// given fields. An empty/blank query always matches (nothing typed
	// yet - show everything). Any null field is simply skipped.
	// =====================
	public static boolean matchesTokensAcrossFields(
		String query,
		String... fields) {

		if (query == null || query.trim().length() == 0) {
			return true;
		}

		String[] tokens =
			query.trim().toLowerCase(Locale.getDefault()).split("\\s+");

		for (String token : tokens) {

			if (token.length() == 0) {
				continue;
			}

			boolean tokenFoundInSomeField = false;

			for (String field : fields) {

				if (field != null &&
					field.toLowerCase(Locale.getDefault()).contains(token)) {

					tokenFoundInSomeField = true;
					break;
				}
			}

			if (!tokenFoundInSomeField) {
				return false;
			}
		}

		return true;
	}
}
