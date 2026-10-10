package com.mujeer.businesserp;

import java.util.ArrayList;
import java.util.HashMap;

// =====================
// Shared "recently sold pinned to top of item picker" reordering
// (approved feature list row #17 - "apply to the whole app") -
// originally only on Transactioneditactivity's Add/Edit Item dialog,
// extracted here so every other screen that lets the user browse/pick
// from the full items list (GenerateEntriesActivity, and any future
// one) gets the exact same behavior instead of a copy-pasted version
// that could drift out of sync.
// =====================
public class ItemPickerUtils {

	// Moves whatever items were sold most recently to the front of the
	// given list, most-recently-sold first, leaving every other item in
	// its existing order behind them - a fast-selling item is exactly
	// the one worth restocking too. An AutoCompleteTextView's default
	// filter preserves whatever order it's handed among the names that
	// match what's typed, so reordering the source list is enough to
	// pin them at the top of the dropdown too; a plain ListView picker
	// shows the effect directly.
	public static void pinRecentlySoldItemsFirst(
		DatabaseHelper db, ArrayList<HashMap<String, Object>> items) {

		ArrayList<Integer> recentItemIds = db.getRecentlySoldItemIds(8);

		if (recentItemIds.isEmpty()) {
			return;
		}

		ArrayList<HashMap<String, Object>> remaining =
			new ArrayList<HashMap<String, Object>>(items);
		ArrayList<HashMap<String, Object>> reordered =
			new ArrayList<HashMap<String, Object>>();

		for (Integer recentItemId : recentItemIds) {

			for (int i = 0; i < remaining.size(); i++) {

				if (recentItemId.equals(remaining.get(i).get("id"))) {

					reordered.add(remaining.remove(i));
					break;
				}
			}
		}

		reordered.addAll(remaining);

		items.clear();
		items.addAll(reordered);
	}
}
