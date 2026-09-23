package com.mujeer.businesserp;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * One configured "line" in a Generate Entries session.
 *
 * For Purchase/Sale, a line is one item: refId is the item id, unitValue is
 * its price. For Payment/Expense, a line is one party-or-description:
 * refId is the party id (null for Expense, which has no party), unitValue
 * is the amount registered per tap.
 *
 * Either way, dateCounts is built up by CalendarTapView - one entry per
 * date the user tapped, with the current tap count for that date.
 */
public class GenerateEntryLine {

	public Integer refId;
	public String label;

	// Purchase/Sale only: per-item party override, used when the session
	// is in "different party per item" mode instead of one shared party.
	public Integer linePartyId;
	public String linePartyName;

	public double unitValue;

	// Purchase/Sale only, and only when the item has variety groups: the
	// value picked from each group's dropdown (group id -> value id), and
	// the variety_combos row they resolve to. Null/empty when the item
	// has no variety groups.
	public Map<Integer, Integer> varietySelections = new LinkedHashMap<Integer, Integer>();
	public Integer comboId;

	public Map<String, Integer> dateCounts = new LinkedHashMap<String, Integer>();

	public int totalCount() {

		int total = 0;

		for (int c : dateCounts.values()) {
			total += c;
		}

		return total;
	}

	public double totalValue() {
		return totalCount() * unitValue;
	}
}
