package com.mujeer.businesserp;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;

// =====================
// Quick Sale mode (approved feature list row #84) - one screen, the
// biggest buttons, the least navigation, for a fast walk-up cash sale
// of ordinary (size-less) items. Deliberately skips everything the full
// Sale screen asks for that a quick sale never needs: no party picker
// (always the existing "Cash Sale" placeholder party), no partial
// payment or due date (always paid in full), no discount/other-charges
// fields. Tapping a Quick Pick button (the items sold most recently -
// see DatabaseHelper.getRecentlySoldItemIds()) adds one unit straight
// to the cart; tapping a cart row offers +1/-1/Remove. Completing the
// sale saves it exactly like the full Sale screen would
// (insertSale()/insertSaleItem()) and then clears the cart, ready for
// the next customer without leaving this screen.
// =====================
public class QuickSaleActivity extends Activity {

	TextView tv_quick_sale_total;
	LinearLayout container_quick_picks;
	Button btn_quick_sale_add_any_item;
	TextView tv_quick_sale_cart_empty;
	LinearLayout container_quick_sale_cart;
	Button btn_complete_quick_sale;

	DatabaseHelper db;

	ArrayList<HashMap<String, Object>> cartLines = new ArrayList<>();

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.quick_sale_activity);

		tv_quick_sale_total = findViewById(R.id.tv_quick_sale_total);
		container_quick_picks = findViewById(R.id.container_quick_picks);
		btn_quick_sale_add_any_item = findViewById(R.id.btn_quick_sale_add_any_item);
		tv_quick_sale_cart_empty = findViewById(R.id.tv_quick_sale_cart_empty);
		container_quick_sale_cart = findViewById(R.id.container_quick_sale_cart);
		btn_complete_quick_sale = findViewById(R.id.btn_complete_quick_sale);

		db = new DatabaseHelper(this);

		loadQuickPicks();
		refreshCart();

		btn_quick_sale_add_any_item.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					showAddAnyItemDialog();
				}
			}
		);

		btn_complete_quick_sale.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					completeSale();
				}
			}
		);
	}

	private void loadQuickPicks() {

		ArrayList<HashMap<String, Object>> simpleItems = db.getSimpleItemsForQuickSale();
		ArrayList<Integer> recentIds = db.getRecentlySoldItemIds(8);

		ArrayList<HashMap<String, Object>> quickPicks = new ArrayList<>();

		for (Integer recentId : recentIds) {

			for (HashMap<String, Object> item : simpleItems) {

				if (recentId.equals(item.get("id"))) {
					quickPicks.add(item);
					break;
				}
			}
		}

		if (quickPicks.isEmpty()) {

			int limit = Math.min(8, simpleItems.size());

			for (int i = 0; i < limit; i++) {
				quickPicks.add(simpleItems.get(i));
			}
		}

		container_quick_picks.removeAllViews();

		LinearLayout currentRow = null;

		for (int i = 0; i < quickPicks.size(); i++) {

			final HashMap<String, Object> item = quickPicks.get(i);

			if (i % 2 == 0) {

				currentRow = new LinearLayout(this);
				currentRow.setOrientation(LinearLayout.HORIZONTAL);
				currentRow.setLayoutParams(new LinearLayout.LayoutParams(
					LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
				));

				int topMargin = (int) (8 * getResources().getDisplayMetrics().density);
				((LinearLayout.LayoutParams) currentRow.getLayoutParams()).topMargin = topMargin;

				container_quick_picks.addView(currentRow);
			}

			Button button = new Button(this, null, 0, R.style.AppButton_Outline);
			button.setText((String) item.get("name"));
			button.setAllCaps(false);

			LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
				0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f
			);

			int sideMargin = (int) (4 * getResources().getDisplayMetrics().density);

			params.setMargins(sideMargin, 0, sideMargin, 0);
			button.setLayoutParams(params);
			button.setMinHeight((int) (56 * getResources().getDisplayMetrics().density));

			button.setOnClickListener(new View.OnClickListener() {
					@Override
					public void onClick(View v) {
						addToCart(item);
					}
				}
			);

			currentRow.addView(button);
		}
	}

	private void showAddAnyItemDialog() {

		View dialogView = LayoutInflater.from(this).inflate(
			R.layout.quick_sale_add_item_dialog, null
		);

		EditText search = dialogView.findViewById(R.id.et_quick_sale_item_search);
		ListView listView = dialogView.findViewById(R.id.lv_quick_sale_item_search);

		final ArrayList<HashMap<String, Object>> simpleItems = db.getSimpleItemsForQuickSale();
		final ArrayList<String> names = new ArrayList<>();

		for (HashMap<String, Object> item : simpleItems) {
			names.add((String) item.get("name"));
		}

		final ArrayAdapter<String> adapter = new ArrayAdapter<String>(
			this, android.R.layout.simple_list_item_1, names
		);

		listView.setAdapter(adapter);

		final AlertDialog dialog = new AlertDialog.Builder(this)
			.setTitle("Add Any Item")
			.setView(dialogView)
			.setNegativeButton("Cancel", null)
			.create();

		listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
				@Override
				public void onItemClick(
					AdapterView<?> parent, View view, int position, long id) {

					String name = (String) parent.getItemAtPosition(position);

					for (HashMap<String, Object> item : simpleItems) {

						if (name.equals(item.get("name"))) {

							addToCart(item);
							break;
						}
					}

					dialog.dismiss();
				}
			}
		);

		search.addTextChangedListener(new TextWatcher() {
				@Override
				public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

				@Override
				public void onTextChanged(CharSequence s, int start, int before, int count) {
					adapter.getFilter().filter(s);
				}

				@Override
				public void afterTextChanged(Editable s) {}
			}
		);

		dialog.show();
	}

	private void addToCart(HashMap<String, Object> item) {

		int itemId = (Integer) item.get("id");

		for (HashMap<String, Object> line : cartLines) {

			if (itemId == (Integer) line.get("item_id")) {

				double qty = (Double) line.get("qty") + 1;
				double rate = (Double) line.get("rate");

				line.put("qty", qty);
				line.put("amount", qty * rate);

				refreshCart();
				return;
			}
		}

		double rate = 0;

		if (item.get("sale_price") != null) {
			rate = (Double) item.get("sale_price");
		}

		HashMap<String, Object> line = new HashMap<>();
		line.put("item_id", itemId);
		line.put("code", (String) item.get("code"));
		line.put("name", (String) item.get("name"));
		line.put("rate", rate);
		line.put("qty", 1.0);
		line.put("amount", rate);

		cartLines.add(line);

		refreshCart();
	}

	private void refreshCart() {

		container_quick_sale_cart.removeAllViews();

		double total = 0;

		for (HashMap<String, Object> line : cartLines) {
			total += (Double) line.get("amount");
		}

		tv_quick_sale_total.setText("Total: " + AmountFormat.format(total));

		if (cartLines.isEmpty()) {

			tv_quick_sale_cart_empty.setVisibility(View.VISIBLE);
			return;
		}

		tv_quick_sale_cart_empty.setVisibility(View.GONE);

		for (final HashMap<String, Object> line : cartLines) {

			View view = LayoutInflater.from(this).inflate(
				R.layout.discount_stop_restock_row, container_quick_sale_cart, false
			);

			TextView tv_name = view.findViewById(R.id.tv_row_name);
			TextView tv_detail = view.findViewById(R.id.tv_row_detail);
			TextView tv_badge = view.findViewById(R.id.tv_row_badge);

			double qty = (Double) line.get("qty");
			double rate = (Double) line.get("rate");
			double amount = (Double) line.get("amount");

			tv_name.setText((String) line.get("name"));
			tv_detail.setText(
				AmountFormat.formatPlain(qty) + " x " + AmountFormat.format(rate) +
				" = " + AmountFormat.format(amount)
			);

			tv_badge.setText(AmountFormat.formatPlain(qty));
			tv_badge.setTextColor(getResources().getColor(R.color.mod_sales));

			view.setOnClickListener(new View.OnClickListener() {
					@Override
					public void onClick(View v) {
						showCartLineOptions(line);
					}
				}
			);

			container_quick_sale_cart.addView(view);
		}
	}

	private void showCartLineOptions(final HashMap<String, Object> line) {

		new AlertDialog.Builder(this)
			.setTitle((String) line.get("name"))
			.setItems(
				new String[]{"+1", "-1", "Remove"},
				new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int which) {

						double qty = (Double) line.get("qty");
						double rate = (Double) line.get("rate");

						if (which == 0) {

							qty += 1;
							line.put("qty", qty);
							line.put("amount", qty * rate);

						} else if (which == 1) {

							qty -= 1;

							if (qty <= 0) {
								cartLines.remove(line);
							} else {
								line.put("qty", qty);
								line.put("amount", qty * rate);
							}

						} else {

							cartLines.remove(line);
						}

						refreshCart();
					}
				}
			)
			.show();
	}

	private void completeSale() {

		if (cartLines.isEmpty()) {

			Toast.makeText(this, "Cart is empty", Toast.LENGTH_SHORT).show();
			return;
		}

		double grandTotal = 0;

		for (HashMap<String, Object> line : cartLines) {
			grandTotal += (Double) line.get("amount");
		}

		int partyId = db.getOrCreatePartyId("Cash Sale");

		String date = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
		String time = new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date());

		HashMap<String, Object> saleData = new HashMap<>();
		saleData.put("invoice_no", db.getNextSaleInvoiceNo());
		saleData.put("date", date);
		saleData.put("time", time);
		saleData.put("party_id", partyId);
		saleData.put("subtotal", grandTotal);
		saleData.put("discount", 0.0);
		saleData.put("other_charges", 0.0);
		saleData.put("grand_total", grandTotal);
		saleData.put("paid_amount", grandTotal);
		saleData.put("balance", 0.0);
		saleData.put("notes", "Quick Sale");
		saleData.put("due_date", null);

		long saleId = db.insertSale(saleData);

		for (HashMap<String, Object> line : cartLines) {

			HashMap<String, Object> itemData = new HashMap<>();
			itemData.put("sale_id", saleId);
			itemData.put("item_id", line.get("item_id"));
			itemData.put("qty", line.get("qty"));
			itemData.put("rate", line.get("rate"));
			itemData.put("amount", line.get("amount"));
			itemData.put("combo_id", null);

			db.insertSaleItem(itemData);
		}

		Toast.makeText(
			this,
			"Sale completed - " + AmountFormat.format(grandTotal),
			Toast.LENGTH_LONG
		).show();

		cartLines.clear();
		refreshCart();
		loadQuickPicks();
	}
}
