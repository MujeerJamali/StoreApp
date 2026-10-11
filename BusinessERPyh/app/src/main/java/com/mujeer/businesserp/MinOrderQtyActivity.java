package com.mujeer.businesserp;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.HashMap;

// =====================
// Flashcard-style bulk setter for items.min_order_qty (approved feature
// list row #22) - one item at a time, a dialer-type +/- stepper instead
// of a keyboard, so a shop owner can quickly work through every item
// without the friction of opening each one's own Edit screen. A card's
// starting value is whatever's already explicitly set, or otherwise
// DatabaseHelper's own suggested default (6 for a shoe item, else
// derived from that item's last Purchase quantity, else 1) - see
// getItemsForMinOrderQtySetup()/getEffectiveMinOrderQty(), which the
// Reorder List's own suggestion engine (getReorderSuggestions()) then
// honors going forward.
// =====================
public class MinOrderQtyActivity extends Activity {

	// Remembers which item's card was last showing, so reopening this
	// screen resumes where the shop owner left off instead of always
	// restarting at card 1 of a potentially long item list. Keyed by
	// item id (not the raw list position) so an item added/removed/
	// deactivated between visits can't land the resume on the wrong
	// card - lookupResumeIndex() falls back to card 1 if that id is no
	// longer in the freshly-loaded list.
	private static final String PREFS_NAME = "min_order_qty_progress";
	private static final String KEY_LAST_ITEM_ID = "last_item_id";

	private TextView tv_progress;
	private TextView tv_all_done;
	private View container_flashcard;

	private TextView tv_item_name;
	private TextView tv_item_code;
	private TextView tv_qty_value;
	private TextView tv_qty_suggested;
	private TextView tv_qty_minus;
	private TextView tv_qty_plus;

	private Button btn_previous;
	private Button btn_save_next;
	private Button btn_finish;
	private TextView btn_reset_to_first;

	private DatabaseHelper db;

	private final ArrayList<HashMap<String, Object>> items =
		new ArrayList<HashMap<String, Object>>();

	private int currentIndex = 0;
	private double currentQty = 1;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.min_order_qty_activity);

		setTitle("Min Order Quantities");

		InfoBubbleView info_bubble = findViewById(R.id.info_bubble);
		info_bubble.setInfo(
			"Min Order Quantities",
			"Set the minimum quantity to order for each item - the Reorder List will never suggest less than this, even if the formula alone would. A shoe item defaults to 6; every other item defaults to whatever was ordered last time, until you change it here."
		);

		tv_progress = findViewById(R.id.tv_progress);
		tv_all_done = findViewById(R.id.tv_all_done);
		container_flashcard = findViewById(R.id.container_flashcard);

		tv_item_name = findViewById(R.id.tv_item_name);
		tv_item_code = findViewById(R.id.tv_item_code);
		tv_qty_value = findViewById(R.id.tv_qty_value);
		tv_qty_suggested = findViewById(R.id.tv_qty_suggested);
		tv_qty_minus = findViewById(R.id.tv_qty_minus);
		tv_qty_plus = findViewById(R.id.tv_qty_plus);

		btn_previous = findViewById(R.id.btn_previous);
		btn_save_next = findViewById(R.id.btn_save_next);
		btn_finish = findViewById(R.id.btn_finish);
		btn_reset_to_first = findViewById(R.id.btn_reset_to_first);

		db = new DatabaseHelper(this);

		tv_qty_minus.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {

					if (currentQty > 1) {
						currentQty--;
					}

					tv_qty_value.setText(AmountFormat.formatPlain(currentQty));
				}
			}
		);

		tv_qty_plus.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {

					currentQty++;
					tv_qty_value.setText(AmountFormat.formatPlain(currentQty));
				}
			}
		);

		btn_previous.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {

					saveCurrent();

					if (currentIndex > 0) {
						currentIndex--;
					}

					showCurrentCard();
				}
			}
		);

		btn_save_next.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {

					saveCurrent();

					if (currentIndex < items.size() - 1) {

						currentIndex++;
						showCurrentCard();

					} else {

						Toast.makeText(
							MinOrderQtyActivity.this, "All items saved", Toast.LENGTH_SHORT
						).show();

						finish();
					}
				}
			}
		);

		btn_finish.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {

					saveCurrent();
					finish();
				}
			}
		);

		btn_reset_to_first.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {

					saveCurrent();
					currentIndex = 0;
					showCurrentCard();
				}
			}
		);

		loadItems();
	}

	private void loadItems() {

		new Thread(new Runnable() {
				@Override
				public void run() {

					final ArrayList<HashMap<String, Object>> result =
						db.getItemsForMinOrderQtySetup();

					runOnUiThread(new Runnable() {
							@Override
							public void run() {

								if (isFinishing()) {
									return;
								}

								items.clear();
								items.addAll(result);

								currentIndex = lookupResumeIndex();
								showCurrentCard();
							}
						}
					);
				}
			}
		).start();
	}

	private void showCurrentCard() {

		if (items.isEmpty()) {

			container_flashcard.setVisibility(View.GONE);
			tv_all_done.setVisibility(View.VISIBLE);
			tv_progress.setText("");

			btn_previous.setEnabled(false);
			btn_save_next.setEnabled(false);

			return;
		}

		container_flashcard.setVisibility(View.VISIBLE);
		tv_all_done.setVisibility(View.GONE);

		HashMap<String, Object> item = items.get(currentIndex);

		saveResumePosition((Integer) item.get("id"));

		tv_progress.setText("Item " + (currentIndex + 1) + " of " + items.size());

		tv_item_name.setText(String.valueOf(item.get("name")));
		tv_item_code.setText("Code " + item.get("code"));

		double explicitMinOrderQty = (Double) item.get("min_order_qty");
		double suggested = (Double) item.get("suggested_min_order_qty");

		currentQty = explicitMinOrderQty > 0 ? explicitMinOrderQty : suggested;

		tv_qty_value.setText(AmountFormat.formatPlain(currentQty));

		tv_qty_suggested.setText(
			explicitMinOrderQty > 0 ?
			"" :
			"Suggested: " + AmountFormat.formatPlain(suggested)
		);

		btn_previous.setEnabled(currentIndex > 0);
		btn_save_next.setEnabled(true);

		btn_save_next.setText(currentIndex == items.size() - 1 ? "Save" : "Save & Next");
	}

	private void saveCurrent() {

		if (items.isEmpty()) {
			return;
		}

		final int itemId = (Integer) items.get(currentIndex).get("id");
		final double qty = currentQty;

		items.get(currentIndex).put("min_order_qty", qty);

		new Thread(new Runnable() {
				@Override
				public void run() {
					db.updateItemMinOrderQty(itemId, qty);
				}
			}
		).start();
	}

	// Finds the freshly-loaded list position of the item id saved from
	// the last visit, so the flashcard resumes on that same item.
	// Falls back to card 1 (index 0) when there's no saved position yet,
	// or the saved item is no longer in the list (deleted/deactivated
	// since the last visit).
	private int lookupResumeIndex() {

		int lastItemId = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
			.getInt(KEY_LAST_ITEM_ID, -1);

		if (lastItemId == -1) {
			return 0;
		}

		for (int i = 0; i < items.size(); i++) {

			if (lastItemId == (Integer) items.get(i).get("id")) {
				return i;
			}
		}

		return 0;
	}

	private void saveResumePosition(int itemId) {

		getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
			.edit()
			.putInt(KEY_LAST_ITEM_ID, itemId)
			.apply();
	}
}
