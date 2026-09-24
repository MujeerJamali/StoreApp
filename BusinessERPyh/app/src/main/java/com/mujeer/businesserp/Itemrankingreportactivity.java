package com.mujeer.businesserp;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ListView;
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

	private Button btn_sort_combined_desc;
	private Button btn_sort_combined_asc;
	private Button btn_sort_name;

	private Button btn_shoes_all;
	private Button btn_shoes_only;
	private Button btn_shoes_non;

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

		btn_sort_combined_desc = findViewById(R.id.btn_sort_combined_desc);
		btn_sort_combined_asc = findViewById(R.id.btn_sort_combined_asc);
		btn_sort_name = findViewById(R.id.btn_sort_name);

		btn_shoes_all = findViewById(R.id.btn_shoes_all);
		btn_shoes_only = findViewById(R.id.btn_shoes_only);
		btn_shoes_non = findViewById(R.id.btn_shoes_non);

		tv_empty = findViewById(R.id.tv_empty);
		lv_ranking = findViewById(R.id.lv_ranking);

		db = new DatabaseHelper(this);

		adapter = new RankingAdapter(this, rankingList, "item_name");
		lv_ranking.setAdapter(adapter);

		btn_sort_combined_desc.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					selectSort(DatabaseHelper.RANK_SORT_COMBINED_DESC);
				}
			});

		btn_sort_combined_asc.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					selectSort(DatabaseHelper.RANK_SORT_COMBINED_ASC);
				}
			});

		btn_sort_name.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					selectSort(DatabaseHelper.RANK_SORT_NAME_ASC);
				}
			});

		btn_shoes_all.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					selectShoesFilter(DatabaseHelper.SHOES_FILTER_ALL);
				}
			});

		btn_shoes_only.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					selectShoesFilter(DatabaseHelper.SHOES_FILTER_SHOES_ONLY);
				}
			});

		btn_shoes_non.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					selectShoesFilter(DatabaseHelper.SHOES_FILTER_NON_SHOES_ONLY);
				}
			});

		selectSort(DatabaseHelper.RANK_SORT_COMBINED_DESC);
	}

	private void selectShoesFilter(int filter) {

		selectedShoesFilter = filter;

		Button[] buttons = {btn_shoes_all, btn_shoes_only, btn_shoes_non};

		int[] filters = {
			DatabaseHelper.SHOES_FILTER_ALL,
			DatabaseHelper.SHOES_FILTER_SHOES_ONLY,
			DatabaseHelper.SHOES_FILTER_NON_SHOES_ONLY
		};

		for (int i = 0; i < buttons.length; i++) {

			if (filters[i] == filter) {

				buttons[i].setBackgroundResource(R.drawable.bg_button_primary);
				buttons[i].setTextColor(getResources().getColor(R.color.text_on_primary));

			} else {

				buttons[i].setBackgroundResource(R.drawable.bg_button_outline);
				buttons[i].setTextColor(getResources().getColor(R.color.primary));
			}
		}

		loadReport();
	}

	@Override
	protected void onResume() {
		super.onResume();
		loadReport();
	}

	private void selectSort(int sort) {

		selectedSort = sort;

		Button[] buttons = {
			btn_sort_combined_desc,
			btn_sort_combined_asc,
			btn_sort_name
		};

		int[] sorts = {
			DatabaseHelper.RANK_SORT_COMBINED_DESC,
			DatabaseHelper.RANK_SORT_COMBINED_ASC,
			DatabaseHelper.RANK_SORT_NAME_ASC
		};

		for (int i = 0; i < buttons.length; i++) {

			if (sorts[i] == sort) {

				buttons[i].setBackgroundResource(R.drawable.bg_button_primary);
				buttons[i].setTextColor(getResources().getColor(R.color.text_on_primary));

			} else {

				buttons[i].setBackgroundResource(R.drawable.bg_button_outline);
				buttons[i].setTextColor(getResources().getColor(R.color.primary));
			}
		}

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
