package com.mujeer.businesserp;

import android.app.Activity;
import android.app.DatePickerDialog;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Locale;

// =====================
// See DatabaseHelper.getItemMonthlyRankReport() for the rank-of-ranks
// algorithm itself (rank each item within each month it sold in, then
// sum those per-month ranks - lower sum is better).
// =====================
public class ItemMonthlyRankReportActivity extends Activity {

	private static final int RANGE_YESTERDAY = 0;
	private static final int RANGE_MONTH = 1;
	private static final int RANGE_QUARTER = 2;
	private static final int RANGE_YEAR = 3;
	private static final int RANGE_ALL_TIME = 4;
	private static final int RANGE_CUSTOM = 5;

	private Spinner spinner_metric;

	private static final String[] METRIC_LABELS = {"Sales", "Profit"};

	private Spinner spinner_range;

	// Positioned to match the RANGE_* constants above exactly.
	private static final String[] RANGE_LABELS = {
		"Yesterday", "This Month", "Quarter", "Year", "All Time", "Custom Range"
	};

	private Spinner spinner_shoes_filter;

	private static final int[] SHOES_FILTER_VALUES = {
		DatabaseHelper.SHOES_FILTER_ALL,
		DatabaseHelper.SHOES_FILTER_SHOES_ONLY,
		DatabaseHelper.SHOES_FILTER_NON_SHOES_ONLY
	};

	private static final String[] SHOES_FILTER_LABELS = {
		"All Items", "Shoes Only", "Non-Shoes Only"
	};

	private View container_custom_range;
	private EditText et_custom_from;
	private EditText et_custom_to;

	private TextView tv_empty;
	private ListView lv_ranking;
	private SimpleBarChartView chart_monthly_rank;

	private DatabaseHelper db;

	private boolean byProfit = false;
	private int selectedRange = RANGE_MONTH;
	private int selectedShoesFilter = DatabaseHelper.SHOES_FILTER_ALL;

	private long loadGeneration = 0;

	private ArrayList<HashMap<String, Object>> rankingList =
	new ArrayList<HashMap<String, Object>>();

	private MonthlyRankAdapter adapter;

	private final SimpleDateFormat dateFormat =
	new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.item_monthly_rank_report_activity);

		setTitle("Item Monthly Rank");

		InfoBubbleView info_bubble = findViewById(R.id.info_bubble);
		info_bubble.setInfo(
			"Item Monthly Rank", "Each item is ranked against the others within every month it sold in (1 = best), and those per-month ranks are summed - a lower sum means it ranked well more consistently."
		);

		spinner_metric = findViewById(R.id.spinner_metric);
		spinner_range = findViewById(R.id.spinner_range);
		spinner_shoes_filter = findViewById(R.id.spinner_shoes_filter);

		container_custom_range = findViewById(R.id.container_custom_range);
		et_custom_from = findViewById(R.id.et_custom_from);
		et_custom_to = findViewById(R.id.et_custom_to);

		tv_empty = findViewById(R.id.tv_empty);
		lv_ranking = findViewById(R.id.lv_ranking);
		chart_monthly_rank = findViewById(R.id.chart_monthly_rank);

		db = new DatabaseHelper(this);

		adapter = new MonthlyRankAdapter(this, rankingList);
		lv_ranking.setAdapter(adapter);

		lv_ranking.setOnItemClickListener(new AdapterView.OnItemClickListener() {
				@Override
				public void onItemClick(AdapterView<?> parent, View view, int position, long id) {

					HashMap<String, Object> row = rankingList.get(position);

					Intent intent = new Intent(ItemMonthlyRankReportActivity.this, Itemviewactivity.class);
					intent.putExtra("item_id", (Integer) row.get("item_id"));

					startActivity(intent);
				}
			});

		String today = dateFormat.format(new java.util.Date());
		et_custom_from.setText(today);
		et_custom_to.setText(today);

		et_custom_from.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					showDatePicker(et_custom_from);
				}
			});

		et_custom_to.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					showDatePicker(et_custom_to);
				}
			});

		ArrayAdapter<String> metricAdapter = new ArrayAdapter<String>(
			this, android.R.layout.simple_spinner_item, METRIC_LABELS
		);

		metricAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
		spinner_metric.setAdapter(metricAdapter);

		int rememberedMetric = FilterMemory.getInt(this, "ItemMonthlyRankReport", "metric", 0);
		spinner_metric.setSelection(rememberedMetric);
		byProfit = rememberedMetric == 1;

		spinner_metric.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
				@Override
				public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
					selectMetric(position == 1);
					FilterMemory.setInt(
						ItemMonthlyRankReportActivity.this, "ItemMonthlyRankReport", "metric", position
					);
				}

				@Override
				public void onNothingSelected(AdapterView<?> parent) {
				}
			});

		ArrayAdapter<String> rangeAdapter = new ArrayAdapter<String>(
			this, android.R.layout.simple_spinner_item, RANGE_LABELS
		);

		rangeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
		spinner_range.setAdapter(rangeAdapter);

		spinner_range.setSelection(RANGE_MONTH);

		spinner_range.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
				@Override
				public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
					selectRange(position);
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

		int rememberedShoesFilterPosition =
			FilterMemory.getInt(this, "ItemMonthlyRankReport", "shoes_filter", 0);

		spinner_shoes_filter.setSelection(rememberedShoesFilterPosition);
		selectedShoesFilter = SHOES_FILTER_VALUES[rememberedShoesFilterPosition];

		spinner_shoes_filter.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
				@Override
				public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
					selectedShoesFilter = SHOES_FILTER_VALUES[position];
					FilterMemory.setInt(
						ItemMonthlyRankReportActivity.this, "ItemMonthlyRankReport", "shoes_filter", position
					);
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

	private void showDatePicker(final EditText target) {

		Calendar calendar = Calendar.getInstance();

		try {
			calendar.setTime(dateFormat.parse(target.getText().toString()));
		} catch (Exception e) {
		}

		new DatePickerDialog(
			this,
			R.style.AppAlertDialogTheme,
			new DatePickerDialog.OnDateSetListener() {
				@Override
				public void onDateSet(
					android.widget.DatePicker view, int year, int month, int dayOfMonth) {

					target.setText(
						String.format(Locale.getDefault(), "%04d-%02d-%02d", year, month + 1, dayOfMonth)
					);

					if (selectedRange == RANGE_CUSTOM) {
						loadReport();
					}
				}
			},
			calendar.get(Calendar.YEAR),
			calendar.get(Calendar.MONTH),
			calendar.get(Calendar.DAY_OF_MONTH)
		).show();
	}

	private void selectMetric(boolean profit) {

		byProfit = profit;

		loadReport();
	}

	private void selectRange(int range) {

		selectedRange = range;

		container_custom_range.setVisibility(range == RANGE_CUSTOM ? View.VISIBLE : View.GONE);

		loadReport();
	}

	// All non-custom ranges reach back from today to the start of the
	// relevant calendar period (matching the other reports'
	// convention); All Time uses a date far enough back to include
	// everything, since the underlying query always needs two real
	// date bounds to group by month.
	private String[] computeRange(int range) {

		if (range == RANGE_ALL_TIME) {

			return new String[]{
				"0000-01-01",
				dateFormat.format(new java.util.Date())
			};
		}

		if (range == RANGE_CUSTOM) {
			return new String[]{
				et_custom_from.getText().toString().trim(),
				et_custom_to.getText().toString().trim()
			};
		}

		Calendar from = Calendar.getInstance();
		Calendar to = Calendar.getInstance();

		from.set(Calendar.HOUR_OF_DAY, 0);
		from.set(Calendar.MINUTE, 0);
		from.set(Calendar.SECOND, 0);
		from.set(Calendar.MILLISECOND, 0);

		switch (range) {

			case RANGE_QUARTER:
				from.add(Calendar.MONTH, -3);
				break;

			case RANGE_YEAR:
				from.add(Calendar.MONTH, -12);
				break;

			case RANGE_MONTH:
			default:
				from.set(Calendar.DAY_OF_MONTH, 1);
				break;
		}

		return new String[]{
			dateFormat.format(from.getTime()),
			dateFormat.format(to.getTime())
		};
	}

	private void loadReport() {

		final String[] range = computeRange(selectedRange);
		final boolean byProfit_forQuery = byProfit;
		final int shoesFilter_forQuery = selectedShoesFilter;
		final long myGeneration = ++loadGeneration;

		new Thread(new Runnable() {
				@Override
				public void run() {

					final ArrayList<HashMap<String, Object>> result =
						db.getItemMonthlyRankReport(
						range[0], range[1], byProfit_forQuery, shoesFilter_forQuery
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
									chart_monthly_rank.setVisibility(View.GONE);

								} else {

									tv_empty.setVisibility(View.GONE);
									lv_ranking.setVisibility(View.VISIBLE);
									chart_monthly_rank.setVisibility(View.VISIBLE);

									setListViewHeightBasedOnChildren(lv_ranking);

									// Top ~10 items in the list's own best-
									// first order (sorted by rank_sum, the
									// report's whole point), charting
									// whichever metric is currently
									// selected - Sales or Profit - so the
									// chart always matches what the rows
									// themselves are built from.
									ArrayList<SimpleBarChartView.Entry> chartEntries =
										new ArrayList<SimpleBarChartView.Entry>();

									int chartCount = Math.min(10, rankingList.size());

									for (int i = 0; i < chartCount; i++) {

										HashMap<String, Object> row = rankingList.get(i);

										double value = byProfit_forQuery ?
											(Double) row.get("total_profit") :
											(Double) row.get("total_sales");

										chartEntries.add(new SimpleBarChartView.Entry(
											String.valueOf(row.get("item_name")),
											value,
											getResources().getColor(R.color.primary)
										));
									}

									chart_monthly_rank.setEntries(chartEntries);
								}
							}
						});
				}
			}).start();
	}

	// A ListView inside a ScrollView doesn't scroll on its own, so it
	// needs to be sized to wrap all of its rows - same fix used in
	// Itemrankingreportactivity, Partysalesreportactivity, etc.
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
