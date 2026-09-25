package com.mujeer.businesserp;

import android.app.Activity;
import android.app.DatePickerDialog;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
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

	private Button btn_metric_sales;
	private Button btn_metric_profit;

	private Button btn_range_yesterday;
	private Button btn_range_month;
	private Button btn_range_quarter;
	private Button btn_range_year;
	private Button btn_range_all_time;
	private Button btn_range_custom;

	private Button btn_shoes_all;
	private Button btn_shoes_only;
	private Button btn_shoes_non;

	private View container_custom_range;
	private EditText et_custom_from;
	private EditText et_custom_to;

	private TextView tv_empty;
	private ListView lv_ranking;

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

		btn_metric_sales = findViewById(R.id.btn_metric_sales);
		btn_metric_profit = findViewById(R.id.btn_metric_profit);

		btn_range_yesterday = findViewById(R.id.btn_range_yesterday);
		btn_range_month = findViewById(R.id.btn_range_month);
		btn_range_quarter = findViewById(R.id.btn_range_quarter);
		btn_range_year = findViewById(R.id.btn_range_year);
		btn_range_all_time = findViewById(R.id.btn_range_all_time);
		btn_range_custom = findViewById(R.id.btn_range_custom);

		btn_shoes_all = findViewById(R.id.btn_shoes_all);
		btn_shoes_only = findViewById(R.id.btn_shoes_only);
		btn_shoes_non = findViewById(R.id.btn_shoes_non);

		container_custom_range = findViewById(R.id.container_custom_range);
		et_custom_from = findViewById(R.id.et_custom_from);
		et_custom_to = findViewById(R.id.et_custom_to);

		tv_empty = findViewById(R.id.tv_empty);
		lv_ranking = findViewById(R.id.lv_ranking);

		db = new DatabaseHelper(this);

		adapter = new MonthlyRankAdapter(this, rankingList);
		lv_ranking.setAdapter(adapter);

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

		btn_metric_sales.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					selectMetric(false);
				}
			});

		btn_metric_profit.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					selectMetric(true);
				}
			});

		btn_range_yesterday.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					selectRange(RANGE_YESTERDAY);
				}
			});

		btn_range_month.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					selectRange(RANGE_MONTH);
				}
			});

		btn_range_quarter.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					selectRange(RANGE_QUARTER);
				}
			});

		btn_range_year.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					selectRange(RANGE_YEAR);
				}
			});

		btn_range_all_time.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					selectRange(RANGE_ALL_TIME);
				}
			});

		btn_range_custom.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					selectRange(RANGE_CUSTOM);
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

		selectMetric(false);
		selectShoesFilter(DatabaseHelper.SHOES_FILTER_ALL);
		selectRange(RANGE_MONTH);
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

		if (profit) {

			btn_metric_profit.setBackgroundResource(R.drawable.bg_button_primary);
			btn_metric_profit.setTextColor(getResources().getColor(R.color.text_on_primary));

			btn_metric_sales.setBackgroundResource(R.drawable.bg_button_outline);
			btn_metric_sales.setTextColor(getResources().getColor(R.color.primary));

		} else {

			btn_metric_sales.setBackgroundResource(R.drawable.bg_button_primary);
			btn_metric_sales.setTextColor(getResources().getColor(R.color.text_on_primary));

			btn_metric_profit.setBackgroundResource(R.drawable.bg_button_outline);
			btn_metric_profit.setTextColor(getResources().getColor(R.color.primary));
		}

		loadReport();
	}

	private void selectRange(int range) {

		selectedRange = range;

		Button[] buttons = {
			btn_range_yesterday, btn_range_month, btn_range_quarter,
			btn_range_year, btn_range_all_time, btn_range_custom
		};

		int[] ranges = {
			RANGE_YESTERDAY, RANGE_MONTH, RANGE_QUARTER, RANGE_YEAR, RANGE_ALL_TIME, RANGE_CUSTOM
		};

		for (int i = 0; i < buttons.length; i++) {

			if (ranges[i] == range) {

				buttons[i].setBackgroundResource(R.drawable.bg_button_primary);
				buttons[i].setTextColor(getResources().getColor(R.color.text_on_primary));

			} else {

				buttons[i].setBackgroundResource(R.drawable.bg_button_outline);
				buttons[i].setTextColor(getResources().getColor(R.color.primary));
			}
		}

		container_custom_range.setVisibility(range == RANGE_CUSTOM ? View.VISIBLE : View.GONE);

		loadReport();
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
