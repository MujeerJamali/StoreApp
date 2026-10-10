package com.mujeer.businesserp;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Filter;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * ArrayAdapter for AutoCompleteTextView dropdowns that renders two lines
 * per row - the primary text plus a smaller detail line underneath (e.g.
 * price/stock for an item, balance for a party).
 *
 * The adapter is otherwise a plain ArrayAdapter<String>: getItem(),
 * toString() and the built-in text filtering all behave exactly as they
 * would with android.R.layout.simple_dropdown_item_1line, so any existing
 * code that matches the typed/selected text against its own list of
 * strings keeps working unchanged. Only getView() is overridden, to also
 * show the detail line looked up from 'subtitles' by the row's primary
 * text.
 */
public class TwoLineAutoCompleteAdapter extends ArrayAdapter<String> {

	private final Map<String, String> subtitles;

	// The default ArrayAdapter filter only matches text that the item
	// *starts with*, and even a plain substring check misses cases like
	// typing "Soft 7-10" for an item named "...Soft Kapro...7-10" where
	// the typed words aren't contiguous. Keeping our own copy of the
	// unfiltered list lets the overridden filter below match every
	// typed word anywhere in the text (see SearchUtils), in any order.
	private final List<String> allItems;

	// Two (or more) rows can carry the exact same display text - e.g. two
	// items that happen to share a name, or two parties with the same
	// name. Matching a clicked dropdown row back to "the item/party whose
	// name equals this text" (via indexOf on a name list) then silently
	// resolves to whichever one happens to come first, which can be a
	// completely different row than the one actually clicked. So instead
	// of leaving callers to re-derive the original index from text,
	// getOriginalIndex() tracks it directly: for the row currently at
	// filtered position i, originalIndices[i] is that row's index in the
	// unfiltered list this adapter was constructed with, kept in lockstep
	// by the filter below (never by comparing text).
	private final List<Integer> originalIndices;

	// Non-null shows one extra row, always last regardless of what the
	// typed text filters down to (even zero real matches) - "+ Add New
	// Item"/"+ Add New Cost Item"/etc. Selecting it isn't a real item;
	// callers check isAddNewPosition() before trusting getOriginalIndex()
	// or the text AutoCompleteTextView auto-fills on tap (see its own
	// call sites for the create-and-return-selected flow this backs).
	private final String addNewLabel;

	// Extra per-row text (same order/size as 'items') that the filter
	// below also matches typed words against, even though it's never
	// shown - e.g. an item's code, so typing a code finds the item by
	// code even though the dropdown displays the item's name. Null (the
	// 3-/4-arg constructors below) means "nothing extra to search" -
	// filtering then matches the displayed text only, same as before
	// this was added.
	private final List<String> extraSearchText;

	public TwoLineAutoCompleteAdapter(
		Context context,
		List<String> items,
		Map<String, String> subtitles) {

		this(context, items, subtitles, null, null);
	}

	public TwoLineAutoCompleteAdapter(
		Context context,
		List<String> items,
		Map<String, String> subtitles,
		String addNewLabel) {

		this(context, items, subtitles, addNewLabel, null);
	}

	public TwoLineAutoCompleteAdapter(
		Context context,
		List<String> items,
		Map<String, String> subtitles,
		String addNewLabel,
		List<String> extraSearchText) {

		super(context, android.R.layout.simple_dropdown_item_1line, items);

		this.subtitles = subtitles;
		this.allItems = new ArrayList<String>(items);
		this.addNewLabel = addNewLabel;
		this.extraSearchText = extraSearchText;

		this.originalIndices = new ArrayList<Integer>();

		for (int i = 0; i < items.size(); i++) {
			originalIndices.add(i);
		}
	}

	// True when 'position' is the always-last "+ Add New ..." row rather
	// than a real item/party - see addNewLabel.
	public boolean isAddNewPosition(int position) {

		return addNewLabel != null && position == super.getCount();
	}

	@Override
	public int getCount() {

		return super.getCount() + (addNewLabel != null ? 1 : 0);
	}

	@Override
	public String getItem(int position) {

		if (isAddNewPosition(position)) {
			return addNewLabel;
		}

		return super.getItem(position);
	}

	// The unfiltered-list index of the row currently shown at
	// 'position' in the dropdown (i.e. what onItemClick's own
	// 'position' argument actually points at right now) - the only
	// correct way to resolve a clicked row back to its real item/party,
	// since two rows can share the same display text. Returns -1 for
	// the "+ Add New ..." row (see isAddNewPosition()), same as an
	// out-of-range position.
	public int getOriginalIndex(int position) {

		if (isAddNewPosition(position) || position < 0 || position >= originalIndices.size()) {
			return -1;
		}

		return originalIndices.get(position);
	}

	@Override
	public View getView(int position, View convertView, ViewGroup parent) {

		if (convertView == null) {

			convertView = LayoutInflater.from(getContext()).inflate(
				R.layout.autocomplete_two_line_item,
				parent,
				false
			);
		}

		TextView tvLine1 = convertView.findViewById(R.id.tv_line1);
		TextView tvLine2 = convertView.findViewById(R.id.tv_line2);

		if (isAddNewPosition(position)) {

			tvLine1.setText(addNewLabel);
			tvLine1.setTextColor(
				convertView.getResources().getColor(R.color.primary)
			);
			tvLine1.setTypeface(null, android.graphics.Typeface.BOLD);

			tvLine2.setVisibility(View.GONE);

			return convertView;
		}

		String primary = getItem(position);

		tvLine1.setText(primary);
		tvLine1.setTextColor(
			convertView.getResources().getColor(R.color.text_primary)
		);
		tvLine1.setTypeface(null, android.graphics.Typeface.NORMAL);

		String secondary =
			primary == null ?
			null :
			subtitles.get(primary);

		if (secondary == null || secondary.length() == 0) {

			tvLine2.setVisibility(View.GONE);

		} else {

			tvLine2.setVisibility(View.VISIBLE);
			tvLine2.setText(secondary);
		}

		return convertView;
	}

	@Override
	public Filter getFilter() {

		return new Filter() {

			@Override
			protected FilterResults performFiltering(CharSequence constraint) {

				FilterResults results = new FilterResults();

				ArrayList<String> matches = new ArrayList<String>();
				ArrayList<Integer> matchedIndices = new ArrayList<Integer>();

				if (constraint == null || constraint.length() == 0) {

					matches.addAll(allItems);

					for (int i = 0; i < allItems.size(); i++) {
						matchedIndices.add(i);
					}

				} else {

					String search = constraint.toString();

					for (int i = 0; i < allItems.size(); i++) {

						String item = allItems.get(i);

						String extra =
							extraSearchText != null && i < extraSearchText.size() ?
							extraSearchText.get(i) : null;

						if (SearchUtils.matchesTokensAcrossFields(search, item, extra)) {

							matches.add(item);
							matchedIndices.add(i);
						}
					}
				}

				results.values = new Object[]{matches, matchedIndices};

				// +1 so AutoCompleteTextView's own popup-visibility check
				// (driven by this count, separately from the adapter's
				// getCount()) still opens the dropdown to show the
				// "+ Add New ..." row even when nothing real matches.
				results.count = matches.size() + (addNewLabel != null ? 1 : 0);

				return results;
			}

			@Override
			@SuppressWarnings("unchecked")
			protected void publishResults(CharSequence constraint, FilterResults results) {

				clear();
				originalIndices.clear();

				int realMatchCount = 0;

				if (results != null && results.values != null) {

					Object[] valuePair = (Object[]) results.values;
					List<String> matches = (List<String>) valuePair[0];

					realMatchCount = matches.size();

					addAll(matches);
					originalIndices.addAll((List<Integer>) valuePair[1]);
				}

				if (realMatchCount > 0 || addNewLabel != null) {

					notifyDataSetChanged();

				} else {

					notifyDataSetInvalidated();
				}
			}
		};
	}
}
