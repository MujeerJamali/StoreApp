package com.mujeer.businesserp;

import android.app.Activity;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.AdapterView;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.HashMap;

// =====================
// Pick an item, see what it's frequently bought together with (other
// items that showed up in the same Sale, ranked by how often) and,
// the reverse direction, what it's rarely bought together with (every
// other item that has sold at all, ranked by the same co-occurrence
// count ascending - a possible missed cross-sell opportunity). See
// DatabaseHelper.getCrossSellInsight().
// =====================
public class CrossSellReportActivity extends Activity {

	private EditText et_search_item;
	private ListView lv_search_results;
	private TextView tv_selected_item;

	private LinearLayout container_cross_sell_results;
	private TextView tv_frequently_empty;
	private LinearLayout container_frequently_together;
	private TextView tv_rarely_empty;
	private LinearLayout container_rarely_together;

	private DatabaseHelper db;

	private final ArrayList<HashMap<String, Object>> allSoldItems =
		new ArrayList<HashMap<String, Object>>();

	private ArrayAdapter<String> searchAdapter;
	private final ArrayList<HashMap<String, Object>> searchMatches =
		new ArrayList<HashMap<String, Object>>();

	private long loadGeneration = 0;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.cross_sell_report_activity);

		setTitle("Cross-Sell Insight");

		InfoBubbleView info_bubble = findViewById(R.id.info_bubble);
		info_bubble.setInfo(
			"Cross-Sell Insight",
			"Pick an item to see what it's frequently bought together with (other items that showed up in the same Sale, ranked by how often) and, the reverse, what it's rarely bought together with - every other sold item, ranked the same way ascending - a possible missed cross-sell opportunity."
		);

		et_search_item = findViewById(R.id.et_search_item);
		lv_search_results = findViewById(R.id.lv_search_results);
		tv_selected_item = findViewById(R.id.tv_selected_item);

		container_cross_sell_results = findViewById(R.id.container_cross_sell_results);
		tv_frequently_empty = findViewById(R.id.tv_frequently_empty);
		container_frequently_together = findViewById(R.id.container_frequently_together);
		tv_rarely_empty = findViewById(R.id.tv_rarely_empty);
		container_rarely_together = findViewById(R.id.container_rarely_together);

		db = new DatabaseHelper(this);

		searchAdapter = new ArrayAdapter<String>(
			this, android.R.layout.simple_list_item_1, new ArrayList<String>()
		);

		lv_search_results.setAdapter(searchAdapter);

		loadSoldItems();

		et_search_item.addTextChangedListener(new TextWatcher() {

				@Override
				public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

				@Override
				public void onTextChanged(CharSequence s, int start, int before, int count) {}

				@Override
				public void afterTextChanged(Editable s) {
					filterResults(s.toString());
				}
			}
		);

		lv_search_results.setOnItemClickListener(new AdapterView.OnItemClickListener() {
				@Override
				public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
					selectItem(searchMatches.get(position));
				}
			}
		);
	}

	private void loadSoldItems() {

		new Thread(new Runnable() {
				@Override
				public void run() {

					final ArrayList<HashMap<String, Object>> items = db.getItems(
						DatabaseHelper.ITEM_ACTIVE_FILTER_ACTIVE_ONLY
					);

					runOnUiThread(new Runnable() {
							@Override
							public void run() {

								allSoldItems.clear();
								allSoldItems.addAll(items);
							}
						}
					);
				}
			}
		).start();
	}

	private void filterResults(String query) {

		searchMatches.clear();

		if (query.trim().length() >= 1) {

			for (HashMap<String, Object> item : allSoldItems) {

				String code = String.valueOf(item.get("code"));
				String name = String.valueOf(item.get("name"));

				if (SearchUtils.matchesTokensAcrossFields(query, code, name)) {
					searchMatches.add(item);
				}
			}
		}

		ArrayList<String> labels = new ArrayList<String>();

		for (HashMap<String, Object> item : searchMatches) {
			labels.add(item.get("code") + " - " + item.get("name"));
		}

		searchAdapter.clear();
		searchAdapter.addAll(labels);
		searchAdapter.notifyDataSetChanged();
	}

	private void selectItem(HashMap<String, Object> item) {

		final int itemId = (Integer) item.get("id");

		et_search_item.setText("");
		lv_search_results.setVisibility(View.GONE);

		tv_selected_item.setText("Selected: " + item.get("name"));
		tv_selected_item.setVisibility(View.VISIBLE);

		container_cross_sell_results.setVisibility(View.VISIBLE);

		final long myGeneration = ++loadGeneration;

		new Thread(new Runnable() {
				@Override
				public void run() {

					final HashMap<String, Object> result = db.getCrossSellInsight(itemId);

					runOnUiThread(new Runnable() {
							@Override
							public void run() {

								if (myGeneration != loadGeneration || isFinishing()) {
									return;
								}

								applyResult(result);
							}
						}
					);
				}
			}
		).start();
	}

	@SuppressWarnings("unchecked")
	private void applyResult(HashMap<String, Object> result) {

		ArrayList<HashMap<String, Object>> frequentlyTogether =
			(ArrayList<HashMap<String, Object>>) result.get("frequently_together");

		ArrayList<HashMap<String, Object>> rarelyTogether =
			(ArrayList<HashMap<String, Object>>) result.get("rarely_together");

		fillRows(container_frequently_together, frequentlyTogether, true);
		tv_frequently_empty.setVisibility(frequentlyTogether.isEmpty() ? View.VISIBLE : View.GONE);
		container_frequently_together.setVisibility(frequentlyTogether.isEmpty() ? View.GONE : View.VISIBLE);

		fillRows(container_rarely_together, rarelyTogether, false);
		tv_rarely_empty.setVisibility(rarelyTogether.isEmpty() ? View.VISIBLE : View.GONE);
		container_rarely_together.setVisibility(rarelyTogether.isEmpty() ? View.GONE : View.VISIBLE);
	}

	private void fillRows(
		LinearLayout container, ArrayList<HashMap<String, Object>> rows, boolean highlightPositive) {

		container.removeAllViews();

		for (int i = 0; i < rows.size(); i++) {

			HashMap<String, Object> row = rows.get(i);

			View view = LayoutInflater.from(this).inflate(
				R.layout.discount_stop_restock_row, container, false
			);

			TextView tv_name = view.findViewById(R.id.tv_row_name);
			TextView tv_detail = view.findViewById(R.id.tv_row_detail);
			TextView tv_badge = view.findViewById(R.id.tv_row_badge);

			tv_name.setText(String.valueOf(row.get("name")));
			tv_detail.setText("Code " + row.get("code"));

			int count = (Integer) row.get("co_occurrence_count");
			tv_badge.setText(String.valueOf(count));

			tv_badge.setTextColor(
				getResources().getColor(
					(highlightPositive && count > 0) ? R.color.success : R.color.text_secondary
				)
			);

			container.addView(view);

			if (i < rows.size() - 1) {

				View divider = new View(this);

				divider.setLayoutParams(new LinearLayout.LayoutParams(
					LinearLayout.LayoutParams.MATCH_PARENT, 1
				));

				divider.setBackgroundColor(getResources().getColor(R.color.stroke));

				container.addView(divider);
			}
		}
	}
}
