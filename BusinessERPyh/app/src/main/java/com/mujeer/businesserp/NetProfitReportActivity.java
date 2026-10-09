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
// Net Profit = Sales Total - Item Cost - Expenses, for a selectable
// period (Today/Week/Month/Quarter/Year/All Time/Custom Range). See
// DatabaseHelper.getNetProfitSummary() for the cost-basis
// simplification (current items.purchase_price, not a historical
// snapshot per sale).
// =====================
public class NetProfitReportActivity extends Activity {

	private static final int RANGE_TODAY = 0;
	private static final int RANGE_WEEK = 1;
	private static final int RANGE_MONTH = 2;
	private static final int RANGE_QUARTER = 3;
	private static final int RANGE_YEAR = 4;
	private static final int RANGE_ALL_TIME = 5;
	private static final int RANGE_CUSTOM = 6;
	private static final int RANGE_YESTERDAY = 7;

	private Spinner spinner_range;

	// Positioned in display order (Today, Yesterday, Week, ...), which
	// does not match the RANGE_* numeric order above - so the Spinner's
	// selected position is mapped through RANGE_VALUES, not used as the
	// range value directly.
	private static final int[] RANGE_VALUES = {
		RANGE_TODAY, RANGE_YESTERDAY, RANGE_WEEK, RANGE_MONTH,
		RANGE_QUARTER, RANGE_YEAR, RANGE_ALL_TIME, RANGE_CUSTOM
	};

	private static final String[] RANGE_LABELS = {
		"Today", "Yesterday", "This Week", "This Month",
		"This Quarter", "This Year", "All Time", "Custom Range"
	};

	private View container_custom_range;
	private EditText et_custom_from;
	private EditText et_custom_to;

	private TextView tv_range_label;
	private TextView tv_date_range;
	private TextView tv_sales_total;
	private TextView tv_item_cost;
	private TextView tv_expenses_total;
	private TextView tv_net_profit;
	private SimpleBarChartView chart_net_profit;

	private static final int ITEM_MODE_THIS_PERIOD = 0;
	private static final int ITEM_MODE_STANDING_MARGIN = 1;

	private Spinner spinner_item_mode;
	private Spinner spinner_item_sort;
	private Spinner spinner_item_shoes_filter;
	private TextView tv_item_profit_empty;
	private ListView lv_item_profit;

	private final ArrayList<HashMap<String, Object>> itemProfitList =
		new ArrayList<HashMap<String, Object>>();

	private ItemProfitAdapter itemProfitAdapter;

	private DatabaseHelper db;

	private int selectedRange = RANGE_TODAY;
	private boolean itemSortAscending = false;
	private int selectedShoesFilter = DatabaseHelper.SHOES_FILTER_ALL;
	private int selectedItemMode = ITEM_MODE_THIS_PERIOD;

	// Bumped on every loadReport() call; a background result is only
	// applied if it's still the most recent request by the time it
	// comes back - same convention as Salesreportactivity.
	private long loadGeneration = 0;

	private final SimpleDateFormat dateFormat =
	new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.net_profit_report_activity);

		setTitle("Net Profit");

		InfoBubbleView info_bubble = findViewById(R.id.info_bubble);
		info_bubble.setInfo(
			"Net Profit", "Net Profit = Sales Total - Item Cost - Expenses. The per-item breakdown below can switch between This Period's actual profit and each item's own Standing Margin, period-independent."
		);

		spinner_range = findViewById(R.id.spinner_range);

		container_custom_range = findViewById(R.id.container_custom_range);
		et_custom_from = findViewById(R.id.et_custom_from);
		et_custom_to = findViewById(R.id.et_custom_to);

		tv_range_label = findViewById(R.id.tv_range_label);
		tv_date_range = findViewById(R.id.tv_date_range);
		tv_sales_total = findViewById(R.id.tv_sales_total);
		tv_item_cost = findViewById(R.id.tv_item_cost);
		tv_expenses_total = findViewById(R.id.tv_expenses_total);
		tv_net_profit = findViewById(R.id.tv_net_profit);
		chart_net_profit = findViewById(R.id.chart_net_profit);

		spinner_item_mode = findViewById(R.id.spinner_item_mode);
		spinner_item_sort = findViewById(R.id.spinner_item_sort);
		spinner_item_shoes_filter = findViewById(R.id.spinner_item_shoes_filter);
		tv_item_profit_empty = findViewById(R.id.tv_item_profit_empty);
		lv_item_profit = findViewById(R.id.lv_item_profit);

		itemProfitAdapter = new ItemProfitAdapter(this, itemProfitList);
		lv_item_profit.setAdapter(itemProfitAdapter);

		lv_item_profit.setOnItemClickListener(new AdapterView.OnItemClickListener() {
				@Override
				public void onItemClick(AdapterView<?> parent, View view, int position, long id) {

					HashMap<String, Object> row = itemProfitList.get(position);

					Intent intent = new Intent(NetProfitReportActivity.this, Itemviewactivity.class);
					intent.putExtra("item_id", (Integer) row.get("item_id"));

					startActivity(intent);
				}
			});

		ArrayAdapter<String> itemModeAdapter = new ArrayAdapter<String>(
			this, android.R.layout.simple_spinner_item,
			new String[]{"This Period", "Standing Margin"}
		);

		itemModeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
		spinner_item_mode.setAdapter(itemModeAdapter);

		int rememberedItemMode = FilterMemory.getInt(this, "NetProfitReport", "item_mode", 0);
		spinner_item_mode.setSelection(rememberedItemMode);
		selectedItemMode = rememberedItemMode;
		itemProfitAdapter.setStandingMarginMode(selectedItemMode == ITEM_MODE_STANDING_MARGIN);

		spinner_item_mode.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
				@Override
				public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {

					selectedItemMode = position;
					itemProfitAdapter.setStandingMarginMode(selectedItemMode == ITEM_MODE_STANDING_MARGIN);

					FilterMemory.setInt(
						NetProfitReportActivity.this, "NetProfitReport", "item_mode", position
					);

					loadReport();
				}

				@Override
				public void onNothingSelected(AdapterView<?> parent) {
				}
			});

		ArrayAdapter<String> itemSortAdapter = new ArrayAdapter<String>(
			this, android.R.layout.simple_spinner_item,
			new String[]{"Profit: High to Low", "Profit: Low to High"}
		);

		itemSortAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
		spinner_item_sort.setAdapter(itemSortAdapter);

		int rememberedItemSort = FilterMemory.getInt(this, "NetProfitReport", "item_sort", 0);
		spinner_item_sort.setSelection(rememberedItemSort);
		itemSortAscending = rememberedItemSort == 1;

		spinner_item_sort.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
				@Override
				public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
					itemSortAscending = position == 1;
					FilterMemory.setInt(
						NetProfitReportActivity.this, "NetProfitReport", "item_sort", position
					);
					loadReport();
				}

				@Override
				public void onNothingSelected(AdapterView<?> parent) {
				}
			});

		ArrayAdapter<String> shoesFilterAdapter = new ArrayAdapter<String>(
			this, android.R.layout.simple_spinner_item,
			new String[]{"All Items", "Shoes Only", "Non-Shoes Only"}
		);

		shoesFilterAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
		spinner_item_shoes_filter.setAdapter(shoesFilterAdapter);

		int rememberedShoesFilter = FilterMemory.getInt(this, "NetProfitReport", "shoes_filter", 0);
		spinner_item_shoes_filter.setSelection(rememberedShoesFilter);

		selectedShoesFilter =
			rememberedShoesFilter == 1 ? DatabaseHelper.SHOES_FILTER_SHOES_ONLY :
			rememberedShoesFilter == 2 ? DatabaseHelper.SHOES_FILTER_NON_SHOES_ONLY :
			DatabaseHelper.SHOES_FILTER_ALL;

		spinner_item_shoes_filter.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
				@Override
				public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {

					selectedShoesFilter =
						position == 1 ? DatabaseHelper.SHOES_FILTER_SHOES_ONLY :
						position == 2 ? DatabaseHelper.SHOES_FILTER_NON_SHOES_ONLY :
						DatabaseHelper.SHOES_FILTER_ALL;

					FilterMemory.setInt(
						NetProfitReportActivity.this, "NetProfitReport", "shoes_filter", position
					);

					loadReport();
				}

				@Override
				public void onNothingSelected(AdapterView<?> parent) {
				}
			});

		db = new DatabaseHelper(this);

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

		ArrayAdapter<String> rangeAdapter = new ArrayAdapter<String>(
			this, android.R.layout.simple_spinner_item, RANGE_LABELS
		);

		rangeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
		spinner_range.setAdapter(rangeAdapter);

		spinner_range.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
				@Override
				public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
					selectRange(RANGE_VALUES[position]);
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

	private void selectRange(int range) {

		selectedRange = range;

		container_custom_range.setVisibility(range == RANGE_CUSTOM ? View.VISIBLE : View.GONE);

		loadReport();
	}

	// Every non-custom, non-all-time range ends "today" and starts at
	// the beginning of the relevant calendar period, matching
	// Salesreportactivity's convention. null/null means All Time.
	private String[] computeRange(int range) {

		if (range == RANGE_ALL_TIME) {
			return new String[]{null, null};
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

			case RANGE_YESTERDAY:
				from.add(Calendar.DAY_OF_YEAR, -1);
				to.add(Calendar.DAY_OF_YEAR, -1);
				break;

			case RANGE_WEEK:
				from.setFirstDayOfWeek(Calendar.MONDAY);
				from.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY);
				break;

			case RANGE_MONTH:
				from.set(Calendar.DAY_OF_MONTH, 1);
				break;

			case RANGE_QUARTER:
				int quarterStartMonth = (from.get(Calendar.MONTH) / 3) * 3;
				from.set(Calendar.MONTH, quarterStartMonth);
				from.set(Calendar.DAY_OF_MONTH, 1);
				break;

			case RANGE_YEAR:
				from.set(Calendar.DAY_OF_YEAR, 1);
				break;

			case RANGE_TODAY:
			default:
				break;
		}

		return new String[]{
			dateFormat.format(from.getTime()),
			dateFormat.format(to.getTime())
		};
	}

	private void loadReport() {

		final String[] range = computeRange(selectedRange);
		final int range_forLabel = selectedRange;
		final boolean ascending_forQuery = itemSortAscending;
		final int shoesFilter_forQuery = selectedShoesFilter;
		final int itemMode_forQuery = selectedItemMode;
		final long myGeneration = ++loadGeneration;

		new Thread(new Runnable() {
				@Override
				public void run() {

					final HashMap<String, Object> summary =
						db.getNetProfitSummary(range[0], range[1]);

					final ArrayList<HashMap<String, Object>> byItem =
						itemMode_forQuery == ITEM_MODE_STANDING_MARGIN ?
						db.getStandingMarginByItem(ascending_forQuery, shoesFilter_forQuery) :
						db.getNetProfitByItem(
							range[0], range[1], ascending_forQuery, shoesFilter_forQuery
						);

					runOnUiThread(new Runnable() {
							@Override
							public void run() {

								if (myGeneration != loadGeneration || isFinishing()) {
									return;
								}

								applyReport(summary, range, range_forLabel);
								applyItemProfitList(byItem);
							}
						});
				}
			}).start();
	}

	private void applyItemProfitList(ArrayList<HashMap<String, Object>> byItem) {

		itemProfitList.clear();
		itemProfitList.addAll(byItem);

		itemProfitAdapter.notifyDataSetChanged();

		if (itemProfitList.isEmpty()) {

			tv_item_profit_empty.setText(
				selectedItemMode == ITEM_MODE_STANDING_MARGIN ?
				"No active items." : "No item sales in this period."
			);

			tv_item_profit_empty.setVisibility(View.VISIBLE);
			lv_item_profit.setVisibility(View.GONE);

		} else {

			tv_item_profit_empty.setVisibility(View.GONE);
			lv_item_profit.setVisibility(View.VISIBLE);

			setListViewHeightBasedOnChildren(lv_item_profit);
		}
	}

	// A ListView inside a ScrollView doesn't scroll on its own, so it
	// needs to be sized to wrap all of its rows (measured at the list's
	// real width) instead of clipping - same fix used in
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

		int widthSpec = View.MeasureSpec.makeMeasureSpec(listViewWidth, View.MeasureSpec.EXACTLY);
		int heightSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED);

		int totalHeight = 0;

		for (int i = 0; i < listAdapter.getCount(); i++) {

			View listItem = listAdapter.getView(i, null, listView);
			listItem.measure(widthSpec, heightSpec);
			totalHeight += listItem.getMeasuredHeight();
		}

		android.view.ViewGroup.LayoutParams params = listView.getLayoutParams();

		params.height = totalHeight + (listView.getDividerHeight() * (listAdapter.getCount() - 1));

		listView.setLayoutParams(params);
		listView.requestLayout();
	}

	private void applyReport(
		HashMap<String, Object> summary,
		String[] range,
		int range_forLabel) {

		switch (range_forLabel) {

			case RANGE_YESTERDAY:
				tv_range_label.setText("Yesterday");
				break;

			case RANGE_WEEK:
				tv_range_label.setText("This Week");
				break;

			case RANGE_MONTH:
				tv_range_label.setText("This Month");
				break;

			case RANGE_QUARTER:
				tv_range_label.setText("This Quarter");
				break;

			case RANGE_YEAR:
				tv_range_label.setText("This Year");
				break;

			case RANGE_ALL_TIME:
				tv_range_label.setText("All Time");
				break;

			case RANGE_CUSTOM:
				tv_range_label.setText("Custom Range");
				break;

			case RANGE_TODAY:
			default:
				tv_range_label.setText("Today");
				break;
		}

		tv_date_range.setText(
			range[0] == null ?
			"All Time" :
			(range[0].equals(range[1]) ? range[0] : range[0] + " to " + range[1])
		);

		double salesTotal = (Double) summary.get("sales_total");
		double itemCost = (Double) summary.get("item_cost");
		double expensesTotal = (Double) summary.get("expenses_total");
		double netProfit = (Double) summary.get("net_profit");

		tv_sales_total.setText(AmountFormat.format(salesTotal));
		tv_item_cost.setText(AmountFormat.format(itemCost));
		tv_expenses_total.setText(AmountFormat.format(expensesTotal));

		tv_net_profit.setText(AmountFormat.format(netProfit));

		tv_net_profit.setTextColor(
			getResources().getColor(netProfit >= 0 ? R.color.success : R.color.danger)
		);

		ArrayList<SimpleBarChartView.Entry> chartEntries = new ArrayList<SimpleBarChartView.Entry>();
		chartEntries.add(new SimpleBarChartView.Entry("Sales", salesTotal, getResources().getColor(R.color.mod_sales)));
		chartEntries.add(new SimpleBarChartView.Entry("Item Cost", itemCost, getResources().getColor(R.color.mod_purchase)));
		chartEntries.add(new SimpleBarChartView.Entry("Expenses", expensesTotal, getResources().getColor(R.color.mod_expenses)));
		chartEntries.add(new SimpleBarChartView.Entry("Net Profit", netProfit, getResources().getColor(R.color.primary)));
		chart_net_profit.setEntries(chartEntries);
	}
}
