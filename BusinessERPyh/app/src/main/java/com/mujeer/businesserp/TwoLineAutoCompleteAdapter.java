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

	public TwoLineAutoCompleteAdapter(
		Context context,
		List<String> items,
		Map<String, String> subtitles) {

		super(context, android.R.layout.simple_dropdown_item_1line, items);

		this.subtitles = subtitles;
		this.allItems = new ArrayList<String>(items);

		this.originalIndices = new ArrayList<Integer>();

		for (int i = 0; i < items.size(); i++) {
			originalIndices.add(i);
		}
	}

	// The unfiltered-list index of the row currently shown at
	// 'position' in the dropdown (i.e. what onItemClick's own
	// 'position' argument actually points at right now) - the only
	// correct way to resolve a clicked row back to its real item/party,
	// since two rows can share the same display text.
	public int getOriginalIndex(int position) {

		if (position < 0 || position >= originalIndices.size()) {
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

		String primary = getItem(position);

		tvLine1.setText(primary);

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

						if (SearchUtils.matchesTokensAcrossFields(search, item)) {

							matches.add(item);
							matchedIndices.add(i);
						}
					}
				}

				results.values = new Object[]{matches, matchedIndices};
				results.count = matches.size();

				return results;
			}

			@Override
			@SuppressWarnings("unchecked")
			protected void publishResults(CharSequence constraint, FilterResults results) {

				clear();
				originalIndices.clear();

				if (results != null && results.count > 0) {

					Object[] valuePair = (Object[]) results.values;

					addAll((List<String>) valuePair[0]);
					originalIndices.addAll((List<Integer>) valuePair[1]);
				}

				if (results != null && results.count > 0) {

					notifyDataSetChanged();

				} else {

					notifyDataSetInvalidated();
				}
			}
		};
	}
}
