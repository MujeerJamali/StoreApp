package com.mujeer.businesserp;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.HashMap;

// =====================
// Applies a price change or a new Reorder Threshold across every active
// item whose name or code contains a typed filter - there's no formal
// "category" field on items, so this substring match is the closest
// thing to one (e.g. typing "Shoes Men" matches every men's shoe, since
// that's how this shop's naming convention already groups them). The
// match count updates live as the filter is typed, and both actions
// confirm with that exact count before touching anything - see
// DatabaseHelper.getItemsMatchingFilter()/bulkAdjustPrice()/
// bulkSetReorderThreshold().
// =====================
public class BulkItemUpdateActivity extends Activity {

	private EditText et_filter_text;
	private TextView tv_match_count;

	private CheckBox cb_apply_purchase_price;
	private CheckBox cb_apply_sale_price;
	private EditText et_percent_change;
	private Button btn_apply_price_change;

	private EditText et_new_threshold;
	private Button btn_apply_threshold;

	private DatabaseHelper db;

	private int lastMatchCount = 0;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.bulk_item_update_activity);

		setTitle("Bulk Item Update");

		InfoBubbleView info_bubble = findViewById(R.id.info_bubble);
		info_bubble.setInfo(
			"Bulk Item Update",
			"Change price or Reorder Threshold across every active item whose name or code contains the text you type - there's no separate category field, so this is the closest thing to one. Always shows how many items will be affected before applying."
		);

		et_filter_text = findViewById(R.id.et_filter_text);
		tv_match_count = findViewById(R.id.tv_match_count);

		cb_apply_purchase_price = findViewById(R.id.cb_apply_purchase_price);
		cb_apply_sale_price = findViewById(R.id.cb_apply_sale_price);
		et_percent_change = findViewById(R.id.et_percent_change);
		btn_apply_price_change = findViewById(R.id.btn_apply_price_change);

		et_new_threshold = findViewById(R.id.et_new_threshold);
		btn_apply_threshold = findViewById(R.id.btn_apply_threshold);

		db = new DatabaseHelper(this);

		et_filter_text.addTextChangedListener(new TextWatcher() {

				@Override
				public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

				@Override
				public void onTextChanged(CharSequence s, int start, int before, int count) {}

				@Override
				public void afterTextChanged(Editable s) {
					updateMatchCount();
				}
			}
		);

		btn_apply_price_change.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					confirmApplyPriceChange();
				}
			}
		);

		btn_apply_threshold.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					confirmApplyThreshold();
				}
			}
		);
	}

	private String currentFilter() {
		return et_filter_text.getText().toString().trim();
	}

	private void updateMatchCount() {

		String filter = currentFilter();

		if (filter.isEmpty()) {

			lastMatchCount = 0;
			tv_match_count.setText("Type above to see how many items match.");
			return;
		}

		ArrayList<HashMap<String, Object>> matches = db.getItemsMatchingFilter(filter);
		lastMatchCount = matches.size();

		tv_match_count.setText(
			lastMatchCount + (lastMatchCount == 1 ? " item matches" : " items match")
		);
	}

	private void confirmApplyPriceChange() {

		String filter = currentFilter();

		if (filter.isEmpty() || lastMatchCount == 0) {
			Toast.makeText(this, "No items match that filter", Toast.LENGTH_SHORT).show();
			return;
		}

		if (!cb_apply_purchase_price.isChecked() && !cb_apply_sale_price.isChecked()) {
			Toast.makeText(this, "Pick Purchase Price and/or Sale Price", Toast.LENGTH_SHORT).show();
			return;
		}

		double percentChange;

		try {

			percentChange = Double.parseDouble(et_percent_change.getText().toString().trim());

		} catch (Exception e) {

			Toast.makeText(this, "Enter a percent change", Toast.LENGTH_SHORT).show();
			return;
		}

		final boolean applyPurchase = cb_apply_purchase_price.isChecked();
		final boolean applySale = cb_apply_sale_price.isChecked();
		final double finalPercentChange = percentChange;

		new AlertDialog.Builder(this)
			.setTitle("Apply Price Change")
			.setMessage(
				"Change " +
				(applyPurchase && applySale ? "Purchase and Sale Price" :
					applyPurchase ? "Purchase Price" : "Sale Price") +
				" by " + (percentChange > 0 ? "+" : "") + AmountFormat.formatPlain(percentChange) +
				"% on " + lastMatchCount + (lastMatchCount == 1 ? " item" : " items") + "?"
			)
			.setPositiveButton("Apply", new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int which) {

						int affected = db.bulkAdjustPrice(
							currentFilter(), applyPurchase, applySale, finalPercentChange
						);

						Toast.makeText(
							BulkItemUpdateActivity.this,
							"Updated " + affected + (affected == 1 ? " item" : " items"),
							Toast.LENGTH_SHORT
						).show();
					}
				}
			)
			.setNegativeButton("Cancel", null)
			.show();
	}

	private void confirmApplyThreshold() {

		String filter = currentFilter();

		if (filter.isEmpty() || lastMatchCount == 0) {
			Toast.makeText(this, "No items match that filter", Toast.LENGTH_SHORT).show();
			return;
		}

		double threshold;

		try {

			threshold = Double.parseDouble(et_new_threshold.getText().toString().trim());

		} catch (Exception e) {

			Toast.makeText(this, "Enter a threshold", Toast.LENGTH_SHORT).show();
			return;
		}

		final double finalThreshold = threshold;

		new AlertDialog.Builder(this)
			.setTitle("Apply Threshold Change")
			.setMessage(
				"Set Reorder Threshold to " + AmountFormat.formatPlain(threshold) +
				" on " + lastMatchCount + (lastMatchCount == 1 ? " item" : " items") + "?"
			)
			.setPositiveButton("Apply", new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int which) {

						int affected = db.bulkSetReorderThreshold(currentFilter(), finalThreshold);

						Toast.makeText(
							BulkItemUpdateActivity.this,
							"Updated " + affected + (affected == 1 ? " item" : " items"),
							Toast.LENGTH_SHORT
						).show();
					}
				}
			)
			.setNegativeButton("Cancel", null)
			.show();
	}
}
