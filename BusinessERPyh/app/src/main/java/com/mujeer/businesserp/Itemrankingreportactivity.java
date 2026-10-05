package com.mujeer.businesserp;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Locale;

// Ranks items by an overall index: each item gets a separate rank
// (1 = highest sales) within Week, Month, Quarter, 6 Month, 9 Month,
// Year and All Time, and those seven per-window ranks are added
// together into one overall index - e.g. week(7) + month(13) +
// quarter(27) + sixMonth(16) + nineMonth(32) + year(24) +
// allTime(20) = 139. Items are sorted with the lowest (best) index
// first by default.
public class Itemrankingreportactivity extends Activity {

	private Spinner spinner_item_sort;
	private Spinner spinner_shoes_filter;

	private static final int[] SORT_VALUES = {
		DatabaseHelper.RANK_SORT_COMBINED_DESC,
		DatabaseHelper.RANK_SORT_COMBINED_ASC,
		DatabaseHelper.RANK_SORT_NAME_ASC
	};

	private static final String[] SORT_LABELS = {
		"Combined: High to Low", "Combined: Low to High", "Item Name"
	};

	private static final int[] SHOES_FILTER_VALUES = {
		DatabaseHelper.SHOES_FILTER_ALL,
		DatabaseHelper.SHOES_FILTER_SHOES_ONLY,
		DatabaseHelper.SHOES_FILTER_NON_SHOES_ONLY
	};

	private static final String[] SHOES_FILTER_LABELS = {
		"All Items", "Shoes Only", "Non-Shoes Only"
	};

	private TextView tv_empty;
	private ListView lv_ranking;

	private DatabaseHelper db;

	private int selectedSort = DatabaseHelper.RANK_SORT_COMBINED_DESC;
	private int selectedShoesFilter = DatabaseHelper.SHOES_FILTER_ALL;

	// Bumped on every loadReport() call; a background result is only
	// applied if it's still the most recent request by the time it
	// comes back.
	private long loadGeneration = 0;

	private ArrayList<HashMap<String, Object>> rankingList =
	new ArrayList<HashMap<String, Object>>();

	private RankingAdapter adapter;

	private final SimpleDateFormat dateFormat =
	new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.itemrankingreportactivity);

		setTitle("Item Ranking");

		InfoBubbleView info_bubble = findViewById(R.id.info_bubble);
		info_bubble.setInfo(
			"Item Ranking", "Each item's Today + Week + Month + Quarter + Year + All Time sales are added together into one combined score, and items are ranked by that score."
		);

		spinner_item_sort = findViewById(R.id.spinner_item_sort);
		spinner_shoes_filter = findViewById(R.id.spinner_shoes_filter);

		tv_empty = findViewById(R.id.tv_empty);
		lv_ranking = findViewById(R.id.lv_ranking);

		db = new DatabaseHelper(this);

		adapter = new RankingAdapter(this, rankingList, "item_name");
		lv_ranking.setAdapter(adapter);

		lv_ranking.setOnItemClickListener(new AdapterView.OnItemClickListener() {
				@Override
				public void onItemClick(AdapterView<?> parent, View view, int position, long id) {

					HashMap<String, Object> row = rankingList.get(position);

					Intent intent = new Intent(Itemrankingreportactivity.this, Itemviewactivity.class);
					intent.putExtra("item_id", (Integer) row.get("entity_id"));

					startActivity(intent);
				}
			});

		ArrayAdapter<String> sortAdapter = new ArrayAdapter<String>(
			this, android.R.layout.simple_spinner_item, SORT_LABELS
		);

		sortAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
		spinner_item_sort.setAdapter(sortAdapter);

		spinner_item_sort.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
				@Override
				public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
					selectedSort = SORT_VALUES[position];
					loadReport();
				}

				@Override
				public void onNothingSelected(AdapterView<?> parent) {
				}
			});

		ArrayAdapter<String> shoesFilterAdapter = new ArrayAdapter<String>(
			this, android.R.layout.simple_spinner_item, SHOES_FILTER_LABELS
		);

		shoesFilterAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
		spinner_shoes_filter.setAdapter(shoesFilterAdapter);

		spinner_shoes_filter.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
				@Override
				public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
					selectedShoesFilter = SHOES_FILTER_VALUES[position];
					loadReport();
				}

				@Override
				public void onNothingSelected(AdapterView<?> parent) {
				}
			});

		loadReport();
	}

	@Override
	protected void onResume() {
		super.onResume();
		loadReport();
	}

	// Week/Month/Quarter/6 Month/9 Month/Year are all rolling windows
	// that end today and reach back their respective duration (e.g.
	// "one month" means one month back from today, not the start of
	// the calendar month) - All Time has no start bound.
	private String[] computeRangeStarts() {

		Calendar today = Calendar.getInstance();
		today.set(Calendar.HOUR_OF_DAY, 0);
		today.set(Calendar.MINUTE, 0);
		today.set(Calendar.SECOND, 0);
		today.set(Calendar.MILLISECOND, 0);

		Calendar week = (Calendar) today.clone();
		week.add(Calendar.DAY_OF_YEAR, -7);

		Calendar month = (Calendar) today.clone();
		month.add(Calendar.MONTH, -1);

		Calendar quarter = (Calendar) today.clone();
		quarter.add(Calendar.MONTH, -3);

		Calendar sixMonth = (Calendar) today.clone();
		sixMonth.add(Calendar.MONTH, -6);

		Calendar nineMonth = (Calendar) today.clone();
		nineMonth.add(Calendar.MONTH, -9);

		Calendar year = (Calendar) today.clone();
		year.add(Calendar.MONTH, -12);

		return new String[]{
			dateFormat.format(week.getTime()),
			dateFormat.format(month.getTime()),
			dateFormat.format(quarter.getTime()),
			dateFormat.format(sixMonth.getTime()),
			dateFormat.format(nineMonth.getTime()),
			dateFormat.format(year.getTime())
		};
	}

	private void loadReport() {

		final String[] starts = computeRangeStarts();
		final int sort_forQuery = selectedSort;
		final int shoesFilter_forQuery = selectedShoesFilter;
		final long myGeneration = ++loadGeneration;

		new Thread(new Runnable() {
				@Override
				public void run() {

					final ArrayList<HashMap<String, Object>> result =
						db.getItemSalesRanking(
						starts[0],
						starts[1],
						starts[2],
						starts[3],
						starts[4],
						starts[5],
						sort_forQuery,
						shoesFilter_forQuery
					);

					runOnUiThread(new Runnable() {
							@Override
							public void run() {

								if (myGeneration != loadGeneration || isFinishing()) {
									return;
								}

								rankingList.clear();
								rankingList.addAll(result);

								adapter.notifyDataSetChanged();

								if (rankingList.isEmpty()) {

									tv_empty.setVisibility(View.VISIBLE);
									lv_ranking.setVisibility(View.GONE);

								} else {

									tv_empty.setVisibility(View.GONE);
									lv_ranking.setVisibility(View.VISIBLE);

									setListViewHeightBasedOnChildren(lv_ranking);
								}
							}
						});
				}
			}).start();
	}

	// A ListView inside a ScrollView doesn't scroll on its own, so it
	// needs to be sized to wrap all of its rows (measured at the
	// list's real width) instead of clipping - same fix used in
	// Partysalesreportactivity, Transactionviewactivity, Itemviewactivity.
	private void setListViewHeightBasedOnChildren(ListView listView) {

		android.widget.ListAdapter listAdapter = listView.getAdapter();

		if (listAdapter == null || listAdapter.getCount() == 0) {
			return;
		}

		int listViewWidth = listView.getWidth();

		if (listViewWidth <= 0) {

			android.util.DisplayMetrics metrics = getResources().getDisplayMetrics();
			int paddingPx = (int) (32 * metrics.density);
			listViewWidth = metrics.widthPixels - paddingPx;
		}

		int widthSpec = View.MeasureSpec.makeMeasureSpec(
			listViewWidth, View.MeasureSpec.EXACTLY);

		int heightSpec = View.MeasureSpec.makeMeasureSpec(
			0, View.MeasureSpec.UNSPECIFIED);

		int totalHeight = 0;

		for (int i = 0; i < listAdapter.getCount(); i++) {

			View listItem = listAdapter.getView(i, null, listView);

			listItem.measure(widthSpec, heightSpec);

			totalHeight += listItem.getMeasuredHeight();
		}

		android.view.ViewGroup.LayoutParams params =
			listView.getLayoutParams();

		params.height =
			totalHeight +
			(listView.getDividerHeight() *
			(listAdapter.getCount() - 1));

		listView.setLayoutParams(params);

		listView.requestLayout();
	}
}
