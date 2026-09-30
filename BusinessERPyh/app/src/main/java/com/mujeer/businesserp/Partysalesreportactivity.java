package com.mujeer.businesserp;

import android.app.Activity;
import android.app.DatePickerDialog;
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

public class Partysalesreportactivity extends Activity {

	private static final int RANGE_TODAY = 0;
	private static final int RANGE_YESTERDAY = 1;
	private static final int RANGE_WEEK = 2;
	private static final int RANGE_MONTH = 3;
	private static final int RANGE_QUARTER = 4;
	private static final int RANGE_YEAR = 5;
	private static final int RANGE_CUSTOM = 6;

	private Spinner spinner_range;

	private static final String[] RANGE_LABELS = {
		"Today", "Yesterday", "This Week", "This Month",
		"This Quarter", "This Year", "Custom Range"
	};

	private View container_custom_range;
	private EditText et_custom_from;
	private EditText et_custom_to;

	private Spinner spinner_party_sales_sort;

	private static final int[] SORT_VALUES = {
		DatabaseHelper.SORT_AMOUNT_DESC, DatabaseHelper.SORT_AMOUNT_ASC,
		DatabaseHelper.SORT_NAME_ASC, DatabaseHelper.SORT_COUNT_DESC
	};

	private static final String[] SORT_LABELS = {
		"Amount: High to Low", "Amount: Low to High", "Party Name", "Number of Sales"
	};

	private TextView tv_empty;
	private ListView lv_party_sales;

	private DatabaseHelper db;

	private int selectedRange = RANGE_TODAY;
	private int selectedSort = DatabaseHelper.SORT_AMOUNT_DESC;

	// Bumped on every loadReport() call; a background result is only
	// applied if it's still the most recent request by the time it
	// comes back.
	private long loadGeneration = 0;

	private ArrayList<HashMap<String, Object>> partySalesList =
	new ArrayList<HashMap<String, Object>>();

	private PartySalesAdapter adapter;

	private final SimpleDateFormat dateFormat =
	new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.partysalesreportactivity);

		setTitle("Sales by Party");

		spinner_range = findViewById(R.id.spinner_range);

		container_custom_range = findViewById(R.id.container_custom_range);
		et_custom_from = findViewById(R.id.et_custom_from);
		et_custom_to = findViewById(R.id.et_custom_to);

		spinner_party_sales_sort = findViewById(R.id.spinner_party_sales_sort);

		tv_empty = findViewById(R.id.tv_empty);
		lv_party_sales = findViewById(R.id.lv_party_sales);

		db = new DatabaseHelper(this);

		adapter = new PartySalesAdapter(this, partySalesList);
		lv_party_sales.setAdapter(adapter);

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
					selectRange(position);
				}

				@Override
				public void onNothingSelected(AdapterView<?> parent) {
				}
			});

		ArrayAdapter<String> sortAdapter = new ArrayAdapter<String>(
			this, android.R.layout.simple_spinner_item, SORT_LABELS
		);

		sortAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
		spinner_party_sales_sort.setAdapter(sortAdapter);

		spinner_party_sales_sort.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
				@Override
				public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
					selectedSort = SORT_VALUES[position];
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

	private void selectRange(int range) {

		selectedRange = range;

		container_custom_range.setVisibility(range == RANGE_CUSTOM ? View.VISIBLE : View.GONE);

		loadReport();
	}

	// Same range logic as Salesreportactivity: every range runs from the
	// start of that period through today.
	private String[] computeRange(int range) {

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

				int quarterStartMonth =
					(from.get(Calendar.MONTH) / 3) * 3;

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
		final int sort_forQuery = selectedSort;
		final long myGeneration = ++loadGeneration;

		new Thread(new Runnable() {
				@Override
				public void run() {

					final ArrayList<HashMap<String, Object>> result =
						db.getSalesByParty(range[0], range[1], sort_forQuery);

					runOnUiThread(new Runnable() {
							@Override
							public void run() {

								if (myGeneration != loadGeneration || isFinishing()) {
									return;
								}

								partySalesList.clear();
								partySalesList.addAll(result);

								adapter.notifyDataSetChanged();

								if (partySalesList.isEmpty()) {

									tv_empty.setVisibility(View.VISIBLE);
									lv_party_sales.setVisibility(View.GONE);

								} else {

									tv_empty.setVisibility(View.GONE);
									lv_party_sales.setVisibility(View.VISIBLE);

									setListViewHeightBasedOnChildren(lv_party_sales);
								}
							}
						});
				}
			}).start();
	}

	// A ListView inside a ScrollView doesn't scroll on its own, so it
	// needs to be sized to wrap all of its rows (measured at the list's
	// real width) instead of clipping - same fix used elsewhere in the
	// app (Transactionviewactivity, Itemviewactivity).
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
