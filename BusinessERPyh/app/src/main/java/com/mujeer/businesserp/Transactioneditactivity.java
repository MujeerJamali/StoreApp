package com.mujeer.businesserp;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.Spinner;

import android.widget.TextView;
import android.widget.AdapterView;
import android.view.View;
import android.app.AlertDialog;
import android.widget.AutoCompleteTextView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;


public class Transactioneditactivity extends Activity {


	private static final int TYPE_PURCHASE = 0;
	private static final int TYPE_SALE = 1;

	// Used when the user types an item name in the add-item dialog that
	// doesn't match anything and chooses to create it: Additemactivity is
	// opened for a result, and onActivityResult() below reopens the
	// add-item dialog pre-filled with whatever item came back.
	private static final int REQUEST_ADD_NEW_ITEM = 5001;

	private int transactionType = TYPE_PURCHASE;
	private boolean isEditMode = false;
	private int transactionId = -1;
	
	AutoCompleteTextView actv_party;
    EditText et_date, et_time, et_invoice_number, et_amount_paid, et_notes;
    EditText et_search_transaction_items;
    CheckBox cb_full_paid;
    View tv_add_note;
    TextView tv_grand_total;
    TextView tv_cash_before, tv_cash_after;
    TextView tv_page_title;
    Button btn_add_item, btn_save_transaction, btn_go_dashboard;
    Button btn_cancel_transaction, btn_save_and_new_transaction;
    ListView lv_transaction_items;
    ScrollView scroll_transaction_edit;
    View items_section_container;

    DatabaseHelper db;
	ArrayList<HashMap<String, Object>> parties;
    ArrayList<HashMap<String,Object>> transactionItemList;
    TransactionItemAdapter transactionItemAdapter;
	HashMap<String, Object> saleMap = new HashMap<>();

    ArrayList<String> partyNames;

    // Tracks whether the user has typed directly into et_amount_paid
    // (distinct from the "Full Paid" checkbox driving it - see
    // cb_full_paid/updateDefaultAmountPaid()).
    boolean amountPaidEditedByUser = false;
    boolean updatingAmountPaidProgrammatically = false;

    // The cash-in-hand balance as it stands with THIS transaction's own
    // cash effect excluded (0 for a brand new transaction; the balance
    // minus what this transaction currently contributes, when editing
    // one that already exists) - see loadCashBaseline()/
    // updateCashPreview() below. "Cash After" is this plus whatever the
    // form's current Amount Paid would contribute.
    double cashBaseline = 0;
    boolean cashBaselineLoaded = false;

    // Keeps et_time ticking forward to "now" once a minute, for as long
    // as the user hasn't manually picked a time and isn't editing an
    // existing (already-timestamped) transaction.
    boolean timeManuallySet = false;
    android.os.Handler timeTickHandler = new android.os.Handler(android.os.Looper.getMainLooper());
    Runnable timeTickRunnable = new Runnable() {

        @Override
        public void run() {

            if (!isEditMode && !timeManuallySet) {

                setCurrentDateTime();
            }

            long now = System.currentTimeMillis();
            long delayToNextMinute = 60000 - (now % 60000);

            timeTickHandler.postDelayed(this, delayToNextMinute);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.transaction_edit_activity);

		// When this activity is launched as the app's entry point (no
		// "transaction_type" extra set, e.g. tapping the launcher icon),
		// default to a new Sale so the app opens straight into Add Sale.
		transactionType = getIntent().getIntExtra(
			"transaction_type",
			TYPE_SALE
		);

		isEditMode = getIntent().getBooleanExtra(
			"is_edit",
			false
			
		);

		transactionId = getIntent().getIntExtra(
			"transaction_id",
			-1
		);


		actv_party = findViewById(R.id.actv_party);
        et_date = findViewById(R.id.et_date);

		et_date.setOnClickListener(new View.OnClickListener() {

				@Override
				public void onClick(View v) {

					showDatePicker();
				}
			});
        et_time = findViewById(R.id.et_time);
		et_time.setOnTouchListener(new View.OnTouchListener() {

				@Override
				public boolean onTouch(View v, android.view.MotionEvent event) {

					if (event.getAction() == android.view.MotionEvent.ACTION_UP) {
						showTimePicker();
					}

					return true;
				}
			});
        et_invoice_number = findViewById(R.id.et_invoice_number);
        et_amount_paid = findViewById(R.id.et_amount_paid);
		et_amount_paid.addTextChangedListener(
			new android.text.TextWatcher() {

				@Override
				public void beforeTextChanged(
					CharSequence s, int start, int count, int after) {
				}

				@Override
				public void onTextChanged(
					CharSequence s, int start, int before, int count) {
				}

				@Override
				public void afterTextChanged(android.text.Editable s) {

					if (!updatingAmountPaidProgrammatically) {
						amountPaidEditedByUser = true;
					}

					updateCashPreview();
				}
			});
        cb_full_paid = findViewById(R.id.cb_full_paid);
		cb_full_paid.setOnCheckedChangeListener(fullPaidCheckedChangeListener);
        et_notes = findViewById(R.id.et_notes);
        tv_add_note = findViewById(R.id.tv_add_note);
		tv_add_note.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					et_notes.setVisibility(View.VISIBLE);
					tv_add_note.setVisibility(View.GONE);
					et_notes.requestFocus();
				}
			});
        tv_grand_total = findViewById(R.id.tv_grand_total);
        tv_cash_before = findViewById(R.id.tv_cash_before);
        tv_cash_after = findViewById(R.id.tv_cash_after);
        tv_page_title = findViewById(R.id.tv_page_title);
        btn_add_item = findViewById(R.id.btn_add_item);
        btn_save_transaction = findViewById(R.id.btn_update_purchase);
        btn_cancel_transaction = findViewById(R.id.btn_cancel_transaction);
        btn_save_and_new_transaction = findViewById(R.id.btn_save_and_new_transaction);
        btn_go_dashboard = findViewById(R.id.btn_go_dashboard);

		btn_go_dashboard.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {

					Intent intent = new Intent(
						Transactioneditactivity.this,
						MainActivity.class
					);

					// This activity may now be the task root (app
					// launcher), so clear back to a fresh Dashboard
					// instead of stacking on top of it.
					intent.setFlags(
						Intent.FLAG_ACTIVITY_CLEAR_TOP |
						Intent.FLAG_ACTIVITY_NEW_TASK
					);

					startActivity(intent);
					finish();
				}
			});
        lv_transaction_items = findViewById(R.id.lv_purchase_items);
        scroll_transaction_edit = findViewById(R.id.scroll_transaction_edit);
        items_section_container = findViewById(R.id.items_section_container);
        et_search_transaction_items = findViewById(R.id.et_search_transaction_items);

        db = new DatabaseHelper(this);

        transactionItemList = new ArrayList<HashMap<String,Object>>();
        transactionItemAdapter = new TransactionItemAdapter(this, transactionItemList);
        lv_transaction_items.setAdapter(transactionItemAdapter);

		et_search_transaction_items.addTextChangedListener(
			new android.text.TextWatcher() {

				@Override
				public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

				@Override
				public void onTextChanged(CharSequence s, int start, int before, int count) {}

				@Override
				public void afterTextChanged(android.text.Editable s) {

					transactionItemAdapter.filter(s.toString());

					setListViewHeightBasedOnChildren(lv_transaction_items);
				}
			});
		lv_transaction_items.setOnItemClickListener(
			new AdapterView.OnItemClickListener() {

				@Override
				public void onItemClick(
					AdapterView<?> parent,
					View view,
					int position,
					long id) {

					showEditTransactionItem(
						transactionItemAdapter.getRealIndex(position)
					);
				}
			});

        loadParties();
        setCurrentDateTime();

		long delayToNextMinute = 60000 - (System.currentTimeMillis() % 60000);
		timeTickHandler.postDelayed(timeTickRunnable, delayToNextMinute);
		if (!isEditMode && transactionType == TYPE_SALE) {

			et_invoice_number.setText(
				db.getNextSaleInvoiceNo()
			);
		}

		if (!isEditMode && transactionType == TYPE_PURCHASE) {

			et_invoice_number.setText(
				db.getNextPurchaseInvoiceNo()
			);
		}
		
		if (isEditMode) {

			if (transactionType == TYPE_PURCHASE) {

				loadPurchase();

			} else {

				loadSale();
			}

		} else {

			tv_grand_total.setText("0.00");
			updateDefaultAmountPaid(0);
			loadCashBaseline(0);

			if (transactionType == TYPE_PURCHASE) {
				loadPrefilledItemsFromIntent();
			}
		}

		lv_transaction_items.setOnItemLongClickListener(
			new AdapterView.OnItemLongClickListener() {

				@Override
				public boolean onItemLongClick(
					AdapterView<?> parent,
					View view,
					final int position,
					long id) {

					new AlertDialog.Builder(Transactioneditactivity.this)
						.setTitle("Remove Item")
						.setMessage("Remove this item from the purchase?")
						.setPositiveButton("Remove",
						new android.content.DialogInterface.OnClickListener() {

							@Override
							public void onClick(
								android.content.DialogInterface dialog,
								int which) {

								int realIndex =
									transactionItemAdapter.getRealIndex(position);

								if (realIndex != -1) {
									transactionItemList.remove(realIndex);
								}

								transactionItemAdapter.notifyDataSetChanged();

								setListViewHeightBasedOnChildren(
									lv_transaction_items
								);

								updateGrandTotal();
							}
						})
						.setNegativeButton("Cancel", null)
						.show();

					return true;
				}
			});

		btn_add_item.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					if (transactionType == TYPE_PURCHASE) {

						showAddTransactionItemDialog();

					} else {

						showSaleItemDialog();
					}
				}
			});

		btn_save_transaction.setOnClickListener(
			new View.OnClickListener() {

				@Override
				public void onClick(View v) {

					saveTransaction(false);
				}
			});

		btn_save_and_new_transaction.setOnClickListener(
			new View.OnClickListener() {

				@Override
				public void onClick(View v) {

					saveTransaction(true);
				}
			});

		btn_cancel_transaction.setOnClickListener(
			new View.OnClickListener() {

				@Override
				public void onClick(View v) {

					finish();
				}
			});
			
			
		if (transactionType == TYPE_PURCHASE) {

			setTitle(
				isEditMode ?
				"Edit Purchase" :
				"Add Purchase"
			);

			tv_page_title.setText(
				isEditMode ?
				"Edit Purchase" :
				"Add Purchase"
			);

		} else {

			setTitle(
				isEditMode ?
				"Edit Sale" :
				"Add Sale"
			);

			tv_page_title.setText(
				isEditMode ?
				"Edit Sale" :
				"Add Sale"
			);
		}
		
		
		if (transactionType == TYPE_SALE) {

			btn_save_transaction.setText(
				isEditMode ? "Update Sale" : "Save Sale"
			);

		} else {

			btn_save_transaction.setText(
				isEditMode ? "Update Purchase" : "Save Purchase"
			);
		}
		
		
		btn_save_transaction.setText(
			isEditMode ? "Update" : "Save"
		);

		btn_save_and_new_transaction.setText(
			isEditMode ? "Update & New" : "Save & New"
		);

		if (!isEditMode) {

			if (transactionType == TYPE_PURCHASE) {

				showAddTransactionItemDialog();

			} else {

				showSaleItemDialog();
			}
		}

    }

	@Override
	protected void onDestroy() {

		super.onDestroy();

		timeTickHandler.removeCallbacks(timeTickRunnable);
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		super.onActivityResult(requestCode, resultCode, data);

		if (requestCode == REQUEST_ADD_NEW_ITEM
			&& resultCode == RESULT_OK
			&& data != null) {

			int newItemId = data.getIntExtra("item_id", -1);

			if (newItemId != -1) {

				showAddTransactionItemDialog(newItemId);
			}
		}
	}

    private void loadParties() {

		parties = db.getParties();

		partyNames = new ArrayList<String>();

		java.util.Map<String, String> partySubtitles =
			new java.util.HashMap<String, String>();

		for (HashMap<String, Object> map : parties) {

			String name = (String) map.get("name");

			partyNames.add(name);

			partySubtitles.put(
				name,
				formatPartyBalanceSubtitle(map.get("balance"))
			);
		}

		TwoLineAutoCompleteAdapter adapter =
			new TwoLineAutoCompleteAdapter(
            this,
            partyNames,
            partySubtitles
        );


		actv_party.setAdapter(adapter);
		actv_party.setThreshold(1);


	}

	private String formatPartyBalanceSubtitle(Object balanceObj) {

		double balance =
			balanceObj == null ?
			0 :
			Double.parseDouble(balanceObj.toString());

		if (balance > 0) {

			return "Balance: " + AmountFormat.format(balance) + " (Receivable)";

		} else if (balance < 0) {

			return "Balance: " + AmountFormat.format(Math.abs(balance)) + " (Payable)";
		}

		return "Balance: 0 (Settled)";
	}

	private String formatItemSubtitle(
		HashMap<String, Object> item,
		String priceField) {

		Object priceObj = item.get(priceField);

		Object stockObj = item.get("stock");

		double price =
			priceObj == null ?
			0 :
			Double.parseDouble(priceObj.toString());

		double stock =
			stockObj == null ?
			0 :
			Double.parseDouble(stockObj.toString());

		return "Price: " + AmountFormat.format(price) + " | Stock: " + formatStockAmount(stock);
	}

	private String formatStockAmount(double stock) {

		if (stock == Math.rint(stock)) {

			return String.valueOf((long) stock);
		}

		return String.valueOf(stock);
	}

    private void setCurrentDateTime() {

        Date now = new Date();

        SimpleDateFormat dateFormat =
			new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());

        SimpleDateFormat timeFormat =
			new SimpleDateFormat("HH:mm", Locale.getDefault());

        et_date.setText(dateFormat.format(now));
        et_time.setText(timeFormat.format(now));
    }
	private void showAddTransactionItemDialog() {

		showAddTransactionItemDialog(null);
	}

	private void showAddTransactionItemDialog(final Integer presetItemId) {

		View view = getLayoutInflater().inflate(
			R.layout.dialog_add_transaction_item,
			null
		);

		final AutoCompleteTextView actvItem =
			view.findViewById(R.id.actv_item);

		final EditText etQuantity =
			view.findViewById(R.id.et_quantity);

		final EditText etPurchasePrice =
			view.findViewById(R.id.et_purchase_price);

		final EditText etTotal =
			view.findViewById(R.id.et_item_total);

		wireQuantityPriceTotalSync(etQuantity, etPurchasePrice, etTotal);

		final LinearLayout containerVarieties =
			view.findViewById(R.id.container_dialog_varieties);

		final Map<Integer, Spinner> varietySpinners =
			new LinkedHashMap<Integer, Spinner>();

		final Map<Integer, ArrayList<HashMap<String, Object>>> varietyValuesByGroup =
			new LinkedHashMap<Integer, ArrayList<HashMap<String, Object>>>();

		final TextView tvDialogTitle =
			view.findViewById(R.id.tv_dialog_title);

		final Button btnCancel =
			view.findViewById(R.id.btn_dialog_cancel);

		final Button btnConfirm =
			view.findViewById(R.id.btn_dialog_confirm);

		final Button btnAddNew =
			view.findViewById(R.id.btn_dialog_add_new);

		btnAddNew.setVisibility(View.VISIBLE);
		btnAddNew.setText("Add & New");

		tvDialogTitle.setText(
			transactionType == TYPE_PURCHASE ?
			"Add Purchase Item" :
			"Add Sale Item"
		);

		btnConfirm.setText("Add");

		final ArrayList<HashMap<String, Object>> items =
			db.getItemsForSpinner();

		final ArrayList<String> itemNames =
			new ArrayList<String>();

		for (HashMap<String, Object> item : items) {

			itemNames.add(
				(String) item.get("name")
			);
		}

		final String priceFieldForList =
			transactionType == TYPE_PURCHASE ?
			"purchase_price" :
			"sale_price";

		java.util.Map<String, String> itemSubtitles =
			new java.util.HashMap<String, String>();

		for (HashMap<String, Object> item : items) {

			String label = (String) item.get("name");

			itemSubtitles.put(
				label,
				formatItemSubtitle(item, priceFieldForList)
			);
		}

		final TwoLineAutoCompleteAdapter adapter =
			new TwoLineAutoCompleteAdapter(
			this,
			itemNames,
			itemSubtitles
		);

		actvItem.setAdapter(adapter);
		actvItem.setThreshold(1);

		final int[] selectedPosition = {-1};

		actvItem.setOnItemClickListener(
			new AdapterView.OnItemClickListener() {

				@Override
				public void onItemClick(
					AdapterView<?> parent,
					View view,
					int position,
					long id) {

					// 'position' is the row index within the filtered
					// dropdown list, not the original 'items' list - and
					// two items can share the exact same name, so looking
					// the clicked row up by its displayed text (matching
					// against 'itemNames') can silently resolve to a
					// DIFFERENT item than the one actually clicked.
					// getOriginalIndex() tracks the real correspondence
					// directly instead.
					int actualIndex = adapter.getOriginalIndex(position);

					if (actualIndex == -1) {
						return;
					}

					selectedPosition[0] = actualIndex;

					String priceField =
						transactionType == TYPE_PURCHASE ?
						"purchase_price" :
						"sale_price";

					etPurchasePrice.setText(
						items.get(actualIndex)
						.get(priceField)
						.toString()
					);

					populateVarietySpinners(
						containerVarieties,
						(Integer) items.get(actualIndex).get("id"),
						null,
						varietySpinners,
						varietyValuesByGroup
					);
				}
			});

		if (presetItemId != null) {

			for (int i = 0; i < items.size(); i++) {

				if (((Integer) items.get(i).get("id")).intValue() ==
					presetItemId.intValue()) {

					selectedPosition[0] = i;

					actvItem.setText(itemNames.get(i), false);

					Object presetPrice =
						items.get(i).get(priceFieldForList);

					if (presetPrice != null) {

						etPurchasePrice.setText(presetPrice.toString());
					}

					populateVarietySpinners(
						containerVarieties,
						presetItemId,
						null,
						varietySpinners,
						varietyValuesByGroup
					);

					break;
				}
			}
		}

		final AlertDialog dialog =
			new AlertDialog.Builder(this)
			.setView(view)
			.create();

		btnCancel.setOnClickListener(
			new View.OnClickListener() {

				@Override
				public void onClick(View v) {

					dialog.dismiss();
				}
			}
		);

		btnConfirm.setOnClickListener(
			new View.OnClickListener() {

				@Override
				public void onClick(View v) {

					if (items.size() == 0) {
						return;
					}

					if (selectedPosition[0] == -1) {

						promptCreateNewItemFromDialog(
							dialog,
							actvItem.getText().toString().trim()
						);

						return;
					}

					if (tryAddCurrentItemToTransaction(
						items,
						selectedPosition[0],
						etQuantity,
						etPurchasePrice,
						varietySpinners,
						varietyValuesByGroup)) {

						dialog.dismiss();
					}
				}
			}
		);

		btnAddNew.setOnClickListener(
			new View.OnClickListener() {

				@Override
				public void onClick(View v) {

					if (items.size() == 0) {
						return;
					}

					if (selectedPosition[0] == -1) {

						promptCreateNewItemFromDialog(
							dialog,
							actvItem.getText().toString().trim()
						);

						return;
					}

					if (tryAddCurrentItemToTransaction(
						items,
						selectedPosition[0],
						etQuantity,
						etPurchasePrice,
						varietySpinners,
						varietyValuesByGroup)) {

						// Clear all fields and refocus the item name so
						// the next item can be entered right away.
						selectedPosition[0] = -1;

						actvItem.setText("", false);
						etQuantity.setText("");
						etPurchasePrice.setText("");
						etTotal.setText("");

						containerVarieties.removeAllViews();
						containerVarieties.setVisibility(View.GONE);
						varietySpinners.clear();
						varietyValuesByGroup.clear();

						showKeyboardOn(actvItem);
					}
				}
			}
		);

		focusAndShowKeyboard(dialog, actvItem);

		dialog.show();
	}

	// =====================
	// Links the Add/Edit item dialog's Quantity, Price and Total fields
	// two ways: editing Quantity or Price recalculates Total as their
	// product (the normal direction); editing Total instead solves back
	// for Price at the current Quantity (e.g. 4 @ 50 = 200; changing
	// Total to 100 sets Price to 25). 'suppress' stops the two directions
	// from bouncing off each other - each side turns the other's watcher
	// off while it writes its own programmatic setText().
	// =====================
	private void wireQuantityPriceTotalSync(
		final EditText etQuantity,
		final EditText etPurchasePrice,
		final EditText etTotal) {

		final boolean[] suppress = {false};

		android.text.TextWatcher recomputeTotal = new android.text.TextWatcher() {

			@Override
			public void beforeTextChanged(CharSequence s, int start, int count, int after) {
			}

			@Override
			public void onTextChanged(CharSequence s, int start, int before, int count) {
			}

			@Override
			public void afterTextChanged(android.text.Editable s) {

				if (suppress[0]) {
					return;
				}

				double quantity = 0;
				double price = 0;

				try {
					quantity = Double.parseDouble(etQuantity.getText().toString().trim());
				} catch (Exception e) {
				}

				try {
					price = Double.parseDouble(etPurchasePrice.getText().toString().trim());
				} catch (Exception e) {
				}

				suppress[0] = true;
				etTotal.setText(AmountFormat.formatPlain(quantity * price));
				suppress[0] = false;
			}
		};

		etQuantity.addTextChangedListener(recomputeTotal);
		etPurchasePrice.addTextChangedListener(recomputeTotal);

		etTotal.addTextChangedListener(new android.text.TextWatcher() {

			@Override
			public void beforeTextChanged(CharSequence s, int start, int count, int after) {
			}

			@Override
			public void onTextChanged(CharSequence s, int start, int before, int count) {
			}

			@Override
			public void afterTextChanged(android.text.Editable s) {

				if (suppress[0]) {
					return;
				}

				double quantity;

				try {
					quantity = Double.parseDouble(etQuantity.getText().toString().trim());
				} catch (Exception e) {
					return;
				}

				if (quantity <= 0) {
					return;
				}

				double total;

				try {
					total = Double.parseDouble(etTotal.getText().toString().trim());
				} catch (Exception e) {
					return;
				}

				suppress[0] = true;
				etPurchasePrice.setText(AmountFormat.formatPlain(total / quantity));
				suppress[0] = false;
			}
		});

		// Seed Total from whatever Quantity/Price already hold (e.g. an
		// existing row being edited) without bouncing that initial write
		// back into Price.
		double initialQuantity = 0;
		double initialPrice = 0;

		try {
			initialQuantity = Double.parseDouble(etQuantity.getText().toString().trim());
		} catch (Exception e) {
		}

		try {
			initialPrice = Double.parseDouble(etPurchasePrice.getText().toString().trim());
		} catch (Exception e) {
		}

		suppress[0] = true;
		etTotal.setText(AmountFormat.formatPlain(initialQuantity * initialPrice));
		suppress[0] = false;
	}

	// =====================
	// Brings in items handed over by BulkPurchaseImportActivity, which
	// validates an entire Excel file and then opens this screen in
	// purchase add mode with every row already in the item list, so the
	// user can review (and still edit/remove rows, pick the supplier,
	// etc.) before actually saving. Nothing has been written to the
	// database for these rows yet - each one carries "code",
	// "name_without_code" and "sale_price" instead of a resolved
	// "item_id", and savePurchase() resolves/creates the real catalog
	// item for each one (silently, no per-item dialog) right before
	// writing the purchase items, same as the old bulk importer did
	// inside its own transaction.
	// =====================
	@SuppressWarnings("unchecked")
	private void loadPrefilledItemsFromIntent() {

		Object extra = getIntent().getSerializableExtra("prefill_items");

		if (!(extra instanceof ArrayList)) {
			return;
		}

		ArrayList<HashMap<String, Object>> prefillItems =
			(ArrayList<HashMap<String, Object>>) extra;

		if (prefillItems.isEmpty()) {
			return;
		}

		transactionItemList.addAll(prefillItems);

		transactionItemAdapter.notifyDataSetChanged();

		setListViewHeightBasedOnChildren(lv_transaction_items);

		updateGrandTotal();
	}

	// =====================
	// Shared by the Add-item dialog's "Add" and "Add & New" buttons:
	// validates quantity/price for the selected item, appends it to
	// transactionItemList, and refreshes the items list/total. Returns
	// false (with a toast already shown) if validation failed, so the
	// caller knows not to dismiss/clear the dialog.
	// =====================
	private boolean tryAddCurrentItemToTransaction(
		ArrayList<HashMap<String, Object>> items,
		int selectedIndex,
		EditText etQuantity,
		EditText etPurchasePrice,
		Map<Integer, Spinner> varietySpinners,
		Map<Integer, ArrayList<HashMap<String, Object>>> varietyValuesByGroup) {

		HashMap<String, Object> map = new HashMap<String, Object>();

		map.put("item_id", items.get(selectedIndex).get("id"));
		map.put("code", items.get(selectedIndex).get("code"));
		map.put("name", items.get(selectedIndex).get("name"));

		double quantity = 1;

		try {

			quantity = Double.parseDouble(
				etQuantity.getText().toString()
			);

		} catch (Exception e) {
		}

		if (quantity <= 0) {

			android.widget.Toast.makeText(
				this,
				"Quantity must be greater than 0",
				android.widget.Toast.LENGTH_SHORT
			).show();

			return false;
		}

		double purchasePrice = 0;

		try {

			purchasePrice = Double.parseDouble(
				etPurchasePrice.getText().toString()
			);

		} catch (Exception e) {
		}

		if (purchasePrice < 0) {

			android.widget.Toast.makeText(
				this,
				"Purchase price cannot be negative",
				android.widget.Toast.LENGTH_SHORT
			).show();

			return false;
		}

		map.put("quantity", quantity);

		String priceKey =
			transactionType == TYPE_PURCHASE ?
			"purchase_price" :
			"sale_price";

		map.put(priceKey, purchasePrice);
		map.put("total", quantity * purchasePrice);

		Integer comboId = resolveComboIdFromSpinners(varietySpinners, varietyValuesByGroup);
		map.put("combo_id", comboId);

		if (transactionType == TYPE_SALE) {

			int itemId = (Integer) items.get(selectedIndex).get("id");

			String stockError = checkSaleStockAvailability(itemId, comboId, quantity, null);

			if (stockError != null) {

				android.widget.Toast.makeText(
					this, stockError, android.widget.Toast.LENGTH_LONG
				).show();

				return false;
			}
		}

		transactionItemList.add(map);

		transactionItemAdapter.notifyDataSetChanged();

		setListViewHeightBasedOnChildren(lv_transaction_items);

		scrollToNewlyAddedItem();

		updateGrandTotal();

		return true;
	}

	// =====================
	// ITEM VARIETIES (Add/Edit item dialog)
	// =====================
	// Rebuilds container with one label+Spinner pair per variety group of
	// itemId, restoring preselectedValues (group_id -> value_id) where
	// given. Leaves outSpinners/outValuesByGroup empty and the container
	// hidden for an item with no variety groups.
	// =====================
	private void populateVarietySpinners(
		LinearLayout container,
		int itemId,
		Map<Integer, Integer> preselectedValues,
		Map<Integer, Spinner> outSpinners,
		Map<Integer, ArrayList<HashMap<String, Object>>> outValuesByGroup) {

		container.removeAllViews();
		outSpinners.clear();
		outValuesByGroup.clear();

		ArrayList<HashMap<String, Object>> groups = db.getVarietyGroups(itemId);

		if (groups.isEmpty()) {
			container.setVisibility(View.GONE);
			return;
		}

		container.setVisibility(View.VISIBLE);

		for (HashMap<String, Object> group : groups) {

			final int groupId = (Integer) group.get("id");
			String groupName = (String) group.get("name");

			Integer preselectedValueId =
				preselectedValues != null ? preselectedValues.get(groupId) : null;

			ArrayList<HashMap<String, Object>> values;

			if (transactionType == TYPE_SALE) {

				// A size that's out of stock can't be sold, so leave it
				// out of the dropdown entirely - except the value this
				// line already has (editing an existing sale), which
				// stays visible even at 0 stock so editing doesn't
				// silently change what was sold.
				ArrayList<HashMap<String, Object>> valuesWithStock =
					db.getVarietyValuesWithStock(groupId);

				values = new ArrayList<HashMap<String, Object>>();

				for (HashMap<String, Object> value : valuesWithStock) {

					double stock = (Double) value.get("stock");

					boolean isPreselected =
						preselectedValueId != null &&
						preselectedValueId.equals(value.get("id"));

					if (stock > 0.0001 || isPreselected) {
						values.add(value);
					}
				}

				// Never leave the dropdown empty (would crash on
				// selection) - checkSaleStockAvailability() still
				// catches a genuine 0-stock pick when the item is added.
				if (values.isEmpty()) {
					values = valuesWithStock;
				}

			} else {

				values = db.getVarietyValues(groupId);
			}

			outValuesByGroup.put(groupId, values);

			TextView groupLabel = new TextView(this);
			groupLabel.setText(groupName);
			groupLabel.setTextColor(getResources().getColor(R.color.text_secondary));
			groupLabel.setTextSize(13);
			groupLabel.setPadding(0, 12, 0, 4);
			container.addView(groupLabel);

			ArrayList<String> valueLabels = new ArrayList<String>();
			int selectedIndex = 0;

			for (int i = 0; i < values.size(); i++) {

				valueLabels.add((String) values.get(i).get("label"));

				if (preselectedValueId != null &&
					preselectedValueId.equals(values.get(i).get("id"))) {
					selectedIndex = i;
				}
			}

			Spinner spinner = new Spinner(this);

			ArrayAdapter<String> adapter = new ArrayAdapter<String>(
				this, android.R.layout.simple_spinner_item, valueLabels);
			adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
			spinner.setAdapter(adapter);
			spinner.setSelection(selectedIndex);

			container.addView(spinner);
			outSpinners.put(groupId, spinner);
		}
	}

	private Integer resolveComboIdFromSpinners(
		Map<Integer, Spinner> varietySpinners,
		Map<Integer, ArrayList<HashMap<String, Object>>> varietyValuesByGroup) {

		if (varietySpinners.isEmpty()) {
			return null;
		}

		Map<Integer, Integer> selections = new LinkedHashMap<Integer, Integer>();

		for (Map.Entry<Integer, Spinner> entry : varietySpinners.entrySet()) {

			int groupId = entry.getKey();
			int position = entry.getValue().getSelectedItemPosition();

			HashMap<String, Object> selectedValue =
				varietyValuesByGroup.get(groupId).get(position);

			selections.put(groupId, (Integer) selectedValue.get("id"));
		}

		return db.resolveComboId(selections);
	}

	// =====================
	// STOCK NEVER GOES NEGATIVE (sales only - a purchase adds stock, so
	// it never needs this check)
	// =====================
	// Returns null if newQty is safe to sell, or a user-facing message
	// if it would take this item/combo's stock below 0. dbBalance (the
	// real stored balance) already excludes every OTHER sale ever saved,
	// but not the lines sitting in transactionItemList that haven't been
	// written yet this session - those are subtracted here in Java
	// instead. lineBeingEdited is the exact map instance being replaced
	// (skip it when summing "other" lines, and give its own quantity
	// back, since editing it doesn't change what this SAME line already
	// reserved) - pass null when adding a brand new line.
	//
	// Note: when a saved line is edited more than once in one sitting
	// before Save, the give-back uses its latest in-memory quantity, not
	// its original saved-to-DB quantity - fine for the common case
	// (touch a line once, then Save), slightly optimistic/pessimistic
	// only in the rarer case of re-editing the same line repeatedly
	// before saving.
	// =====================
	private String checkSaleStockAvailability(
		int itemId,
		Integer comboId,
		double newQty,
		HashMap<String, Object> lineBeingEdited) {

		double dbBalance = db.getAvailableStock(itemId, comboId);

		double committedByOtherLines = 0;

		for (HashMap<String, Object> line : transactionItemList) {

			if (line == lineBeingEdited) {
				continue;
			}

			Object lineItemIdObj = line.get("item_id");

			if (!(lineItemIdObj instanceof Integer) ||
				((Integer) lineItemIdObj).intValue() != itemId) {
				continue;
			}

			Integer lineComboId = (Integer) line.get("combo_id");

			boolean sameCombo = (comboId == null && lineComboId == null) ||
				(comboId != null && comboId.equals(lineComboId));

			if (!sameCombo) {
				continue;
			}

			Object qtyObj = line.get("quantity");

			if (qtyObj instanceof Number) {
				committedByOtherLines += ((Number) qtyObj).doubleValue();
			}
		}

		double giveBack = 0;

		if (lineBeingEdited != null && lineBeingEdited.get("quantity") instanceof Number) {
			giveBack = ((Number) lineBeingEdited.get("quantity")).doubleValue();
		}

		double available = dbBalance + giveBack - committedByOtherLines;

		if (newQty > available) {

			return "Not enough stock - only " +
				formatStockQty(available) + " available";
		}

		return null;
	}

	private String formatStockQty(double qty) {

		if (qty == Math.floor(qty)) {
			return String.valueOf((long) qty);
		}

		return String.format(java.util.Locale.getDefault(), "%.2f", qty);
	}

	// =====================
	// Shared "item not found" flow used by both the Add-item dialog's
	// "Add" and "Add & New" buttons when the typed text doesn't match
	// an existing item.
	// =====================
	private void promptCreateNewItemFromDialog(
		final AlertDialog dialog,
		final String typedName) {

		if (typedName.isEmpty()) {

			android.widget.Toast.makeText(
				this,
				"Please select an item from the list",
				android.widget.Toast.LENGTH_SHORT
			).show();

			return;
		}

		new AlertDialog.Builder(this)
			.setTitle("Item not found")
			.setMessage(
				"\"" + typedName + "\" doesn't exist. " +
				"Create it as a new item?"
			)
			.setPositiveButton(
				"Create",
				new android.content.DialogInterface.OnClickListener() {

					@Override
					public void onClick(
						android.content.DialogInterface d,
						int which) {

						dialog.dismiss();

						Intent intent = new Intent(
							Transactioneditactivity.this,
							Additemactivity.class
						);

						intent.putExtra("item_name", typedName);

						startActivityForResult(
							intent,
							REQUEST_ADD_NEW_ITEM
						);
					}
				}
			)
			.setNegativeButton("Cancel", null)
			.show();
	}

	// =====================
	// Requests focus on target and pops the keyboard for it. Unlike
	// focusAndShowKeyboard() (which hooks the dialog's onShow event for
	// the initial open), this is for re-focusing a field on an already
	// visible dialog, e.g. after "Add & New" clears the fields.
	// =====================
	private void showKeyboardOn(final View target) {

		target.requestFocus();

		target.post(new Runnable() {

			@Override
			public void run() {

				android.view.inputmethod.InputMethodManager imm =
					(android.view.inputmethod.InputMethodManager)
					getSystemService(INPUT_METHOD_SERVICE);

				if (imm != null) {

					imm.showSoftInput(
						target,
						android.view.inputmethod.InputMethodManager.SHOW_FORCED
					);
				}
			}
		});
	}

	private void focusAndShowKeyboard(
		final AlertDialog dialog,
		final View target) {

		dialog.getWindow().setSoftInputMode(
			android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE
		);

		dialog.setOnShowListener(
			new android.content.DialogInterface.OnShowListener() {

				@Override
				public void onShow(android.content.DialogInterface d) {

					target.requestFocus();

					target.postDelayed(
						new Runnable() {

							@Override
							public void run() {

								android.view.inputmethod.InputMethodManager imm =
									(android.view.inputmethod.InputMethodManager)
									getSystemService(INPUT_METHOD_SERVICE);

								if (imm != null) {

									imm.showSoftInput(
										target,
										android.view.inputmethod.InputMethodManager.SHOW_FORCED
									);
								}
							}
						},
						150
					);
				}
			}
		);
	}

	private void showEditTransactionItem(final int editPosition) {

		final HashMap<String, Object> oldItem =
			transactionItemList.get(editPosition);

		View view = getLayoutInflater().inflate(
			R.layout.dialog_add_transaction_item,
			null
		);

		final AutoCompleteTextView actvItem =
			view.findViewById(R.id.actv_item);

		final EditText etQuantity =
			view.findViewById(R.id.et_quantity);

		final EditText etPurchasePrice =
			view.findViewById(R.id.et_purchase_price);

		final EditText etTotal =
			view.findViewById(R.id.et_item_total);

		final LinearLayout containerVarieties =
			view.findViewById(R.id.container_dialog_varieties);

		final Map<Integer, Spinner> varietySpinners =
			new LinkedHashMap<Integer, Spinner>();

		final Map<Integer, ArrayList<HashMap<String, Object>>> varietyValuesByGroup =
			new LinkedHashMap<Integer, ArrayList<HashMap<String, Object>>>();

		final TextView tvDialogTitle =
			view.findViewById(R.id.tv_dialog_title);

		final Button btnCancel =
			view.findViewById(R.id.btn_dialog_cancel);

		final Button btnConfirm =
			view.findViewById(R.id.btn_dialog_confirm);

		tvDialogTitle.setText("Edit Item");
		btnConfirm.setText("Update");

		final String priceFieldForEdit =
			transactionType == TYPE_PURCHASE ?
			"purchase_price" :
			"sale_price";

		// A row carried over from Bulk Purchase Import has no item_id
		// yet - product resolution/creation is deliberately deferred to
		// savePurchase(). There's no existing catalog item to preselect
		// below, so skip that whole pick-an-existing-item flow (which
		// would otherwise silently overwrite this row's intended
		// code/name/sale_price with whatever the dropdown defaults to)
		// and only let the user fix quantity/price here.
		if (oldItem.get("item_id") == null) {

			tvDialogTitle.setText("Edit Item (new - not yet in catalog)");

			actvItem.setText((String) oldItem.get("name"), false);
			actvItem.setEnabled(false);
			actvItem.setFocusable(false);

			etQuantity.setText(oldItem.get("quantity").toString());
			etPurchasePrice.setText(oldItem.get(priceFieldForEdit).toString());

			wireQuantityPriceTotalSync(etQuantity, etPurchasePrice, etTotal);

			final AlertDialog pendingDialog =
				new AlertDialog.Builder(this)
				.setView(view)
				.create();

			btnCancel.setOnClickListener(
				new View.OnClickListener() {

					@Override
					public void onClick(View v) {

						pendingDialog.dismiss();
					}
				}
			);

			btnConfirm.setOnClickListener(
				new View.OnClickListener() {

					@Override
					public void onClick(View v) {

						double quantity = 1;

						try {

							quantity = Double.parseDouble(
								etQuantity.getText().toString()
							);

						} catch (Exception e) {
						}

						if (quantity <= 0) {

							android.widget.Toast.makeText(
								Transactioneditactivity.this,
								"Quantity must be greater than 0",
								android.widget.Toast.LENGTH_SHORT
							).show();

							return;
						}

						double priceForEdit = 0;

						try {

							priceForEdit = Double.parseDouble(
								etPurchasePrice.getText().toString()
							);

						} catch (Exception e) {
						}

						if (priceForEdit < 0) {

							android.widget.Toast.makeText(
								Transactioneditactivity.this,
								"Price cannot be negative",
								android.widget.Toast.LENGTH_SHORT
							).show();

							return;
						}

						oldItem.put("quantity", quantity);
						oldItem.put(priceFieldForEdit, priceForEdit);
						oldItem.put("total", quantity * priceForEdit);

						transactionItemAdapter.notifyDataSetChanged();

						setListViewHeightBasedOnChildren(
							lv_transaction_items
						);

						updateGrandTotal();

						pendingDialog.dismiss();
					}
				}
			);

			focusAndShowKeyboard(pendingDialog, etQuantity);

			pendingDialog.show();

			return;
		}

		final ArrayList<HashMap<String, Object>> items =
			db.getItemsForSpinner();

		final ArrayList<String> itemNames =
			new ArrayList<String>();

		int selectedPosition = 0;

		for (int i = 0; i < items.size(); i++) {

			HashMap<String, Object> item = items.get(i);

			itemNames.add(
				(String) item.get("name")
			);

			if (((Integer) item.get("id")).intValue() ==
				((Integer) oldItem.get("item_id")).intValue()) {

				selectedPosition = i;
			}
		}

		java.util.Map<String, String> itemSubtitlesEdit =
			new java.util.HashMap<String, String>();

		final String priceField =
			transactionType == TYPE_PURCHASE ?
			"purchase_price" :
			"sale_price";

		for (HashMap<String, Object> item : items) {

			String label = (String) item.get("name");

			itemSubtitlesEdit.put(
				label,
				formatItemSubtitle(item, priceField)
			);
		}

		final TwoLineAutoCompleteAdapter adapter =
			new TwoLineAutoCompleteAdapter(
			this,
			itemNames,
			itemSubtitlesEdit
		);

		actvItem.setAdapter(adapter);
		actvItem.setThreshold(1);
		actvItem.setText(itemNames.get(selectedPosition), false);

		Map<Integer, Integer> preselectedVarietyValues = null;

		if (oldItem.get("combo_id") != null) {
			preselectedVarietyValues =
				db.getComboSelections((Integer) oldItem.get("combo_id"));
		}

		populateVarietySpinners(
			containerVarieties,
			(Integer) items.get(selectedPosition).get("id"),
			preselectedVarietyValues,
			varietySpinners,
			varietyValuesByGroup
		);

		final int[] selectedItemPosition = {selectedPosition};

		actvItem.setOnItemClickListener(
			new AdapterView.OnItemClickListener() {

				@Override
				public void onItemClick(
					AdapterView<?> parent,
					View view,
					int position,
					long id) {

					// Same fix as in showAddTransactionItemDialog(): two
					// items can share the exact same name, so resolve the
					// clicked row via the adapter's own tracked original
					// index rather than by matching displayed text back
					// against 'itemNames' (which can't tell two
					// identically-named items apart).
					int actualIndex = adapter.getOriginalIndex(position);

					if (actualIndex == -1) {
						return;
					}

					selectedItemPosition[0] = actualIndex;

					etPurchasePrice.setText(
						items.get(actualIndex)
						.get(priceField)
						.toString()
					);

					// The item changed, so any previously selected variety
					// no longer applies - repopulate fresh for the newly
					// picked item, defaulting each group to its "?" value.
					populateVarietySpinners(
						containerVarieties,
						(Integer) items.get(actualIndex).get("id"),
						null,
						varietySpinners,
						varietyValuesByGroup
					);
				}
			});

		etQuantity.setText(
			oldItem.get("quantity").toString()
		);

		etPurchasePrice.setText(
			oldItem.get(priceField).toString()
		);

		wireQuantityPriceTotalSync(etQuantity, etPurchasePrice, etTotal);

		final AlertDialog dialog =
			new AlertDialog.Builder(this)
			.setView(view)
			.create();

		btnCancel.setOnClickListener(
			new View.OnClickListener() {

				@Override
				public void onClick(View v) {

					dialog.dismiss();
				}
			}
		);

		btnConfirm.setOnClickListener(
			new View.OnClickListener() {

				@Override
				public void onClick(View v) {

					double quantity = 1;

					try {

						quantity = Double.parseDouble(
							etQuantity.getText().toString()
						);

					} catch (Exception e) {
					}

					if (quantity <= 0) {

						android.widget.Toast.makeText(
							Transactioneditactivity.this,
							"Quantity must be greater than 0",
							android.widget.Toast.LENGTH_SHORT
						).show();

						return;
					}

					double purchasePrice = 0;

					try {

						purchasePrice = Double.parseDouble(
							etPurchasePrice.getText().toString()
						);

					} catch (Exception e) {
					}

					if (purchasePrice < 0) {

						android.widget.Toast.makeText(
							Transactioneditactivity.this,
							"Price cannot be negative",
							android.widget.Toast.LENGTH_SHORT
						).show();

						return;
					}

					Integer newComboId =
						resolveComboIdFromSpinners(varietySpinners, varietyValuesByGroup);

					if (transactionType == TYPE_SALE) {

						int newItemId =
							(Integer) items.get(selectedItemPosition[0]).get("id");

						String stockError = checkSaleStockAvailability(
							newItemId, newComboId, quantity, oldItem);

						if (stockError != null) {

							android.widget.Toast.makeText(
								Transactioneditactivity.this,
								stockError,
								android.widget.Toast.LENGTH_LONG
							).show();

							return;
						}
					}

					oldItem.put(
						"item_id",
						items.get(selectedItemPosition[0]).get("id")
					);

					oldItem.put(
						"code",
						items.get(selectedItemPosition[0]).get("code")
					);

					oldItem.put(
						"name",
						items.get(selectedItemPosition[0]).get("name")
					);

					oldItem.put(
						"quantity",
						quantity
					);

					oldItem.put(
						priceField,
						purchasePrice
					);

					oldItem.put(
						"total",
						quantity * purchasePrice
					);

					oldItem.put(
						"combo_id",
						newComboId
					);

					transactionItemAdapter.notifyDataSetChanged();

					setListViewHeightBasedOnChildren(
						lv_transaction_items
					);

					updateGrandTotal();

					dialog.dismiss();
				}
			}
		);

		focusAndShowKeyboard(dialog, actvItem);

		dialog.show();
	}

	private void updateGrandTotal() {

		if (items_section_container != null) {

			items_section_container.setVisibility(
				transactionItemList.size() > 0 ?
				View.VISIBLE :
				View.GONE
			);
		}

		double total = 0;

		for (HashMap<String, Object> map : transactionItemList) {

			total += (Double) map.get("total");
		}

		tv_grand_total.setText(
			AmountFormat.format(total)
		);

		updateDefaultAmountPaid(total);
	}

	// =====================
	// Cash before/after preview - shows what the shop's cash-in-hand
	// balance is right now, and what it would become once this
	// transaction is saved with its current Amount Paid. Queried once on
	// a background thread (a handful of SUMs against the whole cash
	// ledger), then updated purely with local arithmetic as the user
	// edits Amount Paid, so no further DB hits are needed while typing.
	// =====================
	private void loadCashBaseline(final double originalCashImpact) {

		new Thread(new Runnable() {

				@Override
				public void run() {

					final double balance = db.getCashBalance();

					runOnUiThread(new Runnable() {

							@Override
							public void run() {

								cashBaseline = balance - originalCashImpact;
								cashBaselineLoaded = true;

								updateCashPreview();
							}
						});
				}
			}).start();
	}

	private void updateCashPreview() {

		if (!cashBaselineLoaded || tv_cash_before == null || tv_cash_after == null) {
			return;
		}

		double paid;

		try {

			paid = Double.parseDouble(et_amount_paid.getText().toString().trim());

		} catch (Exception e) {

			paid = 0;
		}

		double impact = transactionType == TYPE_PURCHASE ? -paid : paid;

		tv_cash_before.setText(
			AmountFormat.format(cashBaseline)
		);

		tv_cash_after.setText(
			AmountFormat.format(cashBaseline + impact)
		);
	}

	// =====================
	// Keeps et_amount_paid synced to the grand total for as long as the
	// "Full Paid" checkbox is checked, whether adding a new transaction
	// or editing an existing one - unchecking it hands the field back to
	// the user (see cb_full_paid's listener, which also sets it to 0 the
	// moment it's unchecked).
	// =====================
	private void updateDefaultAmountPaid(double grandTotal) {

		if (cb_full_paid == null || !cb_full_paid.isChecked()) {
			return;
		}

		updatingAmountPaidProgrammatically = true;

		et_amount_paid.setText(
			AmountFormat.formatPlain(grandTotal)
		);

		updatingAmountPaidProgrammatically = false;
	}

	// Shared by both the checkbox's own listener and
	// setFullPaidCheckboxSilently()'s temporary re-wiring below.
	private final android.widget.CompoundButton.OnCheckedChangeListener fullPaidCheckedChangeListener =
		new android.widget.CompoundButton.OnCheckedChangeListener() {

			@Override
			public void onCheckedChanged(
				android.widget.CompoundButton buttonView, boolean isChecked) {

				updatingAmountPaidProgrammatically = true;

				if (isChecked) {

					double total = 0;

					for (HashMap<String, Object> map : transactionItemList) {
						total += (Double) map.get("total");
					}

					et_amount_paid.setText(
						AmountFormat.formatPlain(total)
					);

				} else {

					et_amount_paid.setText("0");
				}

				updatingAmountPaidProgrammatically = false;
			}
		};

	// Sets the checkbox to reflect an already-saved paid/total pair
	// (opening an existing transaction for editing) without triggering
	// its own listener - the listener is for the user's own taps, not
	// for reflecting data that's already what it is.
	private void setFullPaidCheckboxSilently(double paidAmount, double grandTotal) {

		boolean isFull = paidAmount >= grandTotal - 0.01;

		cb_full_paid.setOnCheckedChangeListener(null);
		cb_full_paid.setChecked(isFull);
		cb_full_paid.setOnCheckedChangeListener(fullPaidCheckedChangeListener);
	}
	private void savePurchase(final boolean andNew) {

		if (transactionItemList.size() == 0) {

			android.widget.Toast.makeText(
                this,
                "Please add at least one item",
                android.widget.Toast.LENGTH_SHORT
			).show();

			return;
		}

		int partyPosition = getSelectedPartyPosition();

		if (partyPosition == -1) {

			android.widget.Toast.makeText(
				this,
				"Please select a party",
				android.widget.Toast.LENGTH_SHORT
			).show();

			return;
		}

		int partyId = (Integer) parties.get(partyPosition).get("id");

		double amountPaid = 0;

		try {

			amountPaid = Double.parseDouble(
                et_amount_paid.getText().toString()
			);

		} catch (Exception e) {
		}

		if (amountPaid < 0) {

			android.widget.Toast.makeText(
				Transactioneditactivity.this, // Use Purchaseseditactivity.this in Edit
				"Amount paid cannot be negative",
				android.widget.Toast.LENGTH_SHORT
			).show();

			return;
		}


		// Computed straight from the line items, not read back from
		// tv_grand_total's own text - that TextView shows comma-grouped
		// thousands (AmountFormat.format()), which Double.parseDouble()
		// can't parse and would throw on any total >= 1000, crashing the
		// save for larger purchases while small ones (no comma) worked.
		double grandTotal = 0;

		for (HashMap<String, Object> item : transactionItemList) {
			grandTotal += (Double) item.get("total");
		}

		if (amountPaid > grandTotal) {

			android.widget.Toast.makeText(
				Transactioneditactivity.this, // Use Purchaseseditactivity.this in Edit
				"Amount paid cannot be greater than Grand Total",
				android.widget.Toast.LENGTH_SHORT
			).show();

			return;
		}



		// Any row carried over from Bulk Purchase Import doesn't have a
		// resolved item_id yet (product creation was deferred until
		// now, on purpose - see loadPrefilledItemsFromIntent()).
		// Resolve/create the real catalog item for each one silently
		// here, before writing anything, so a problem here still leaves
		// the database untouched.
		for (HashMap<String, Object> item : transactionItemList) {

			if (item.get("item_id") != null) {
				continue;
			}

			double itemPurchasePrice = 0;
			double itemSalePrice = 0;

			try {
				itemPurchasePrice = ((Double) item.get("purchase_price")).doubleValue();
			} catch (Exception e) {
			}

			try {
				itemSalePrice = ((Double) item.get("sale_price")).doubleValue();
			} catch (Exception e) {
			}

			int resolvedItemId = db.getOrCreateProductForPurchaseImport(
				(String) item.get("code"),
				(String) item.get("name_without_code"),
				itemPurchasePrice,
				itemSalePrice
			);

			if (resolvedItemId <= 0) {

				android.widget.Toast.makeText(
					this,
					"Could not create or find the product for \"" +
					item.get("name") + "\"",
					android.widget.Toast.LENGTH_SHORT
				).show();

				return;
			}

			item.put("item_id", Integer.valueOf(resolvedItemId));
		}

		int purchaseId;

			if (isEditMode) {

				purchaseId = transactionId;

			boolean success = db.updatePurchase(
			purchaseId,
		partyId,
		et_date.getText().toString(),
		et_time.getText().toString(),
			et_invoice_number.getText().toString(),
			grandTotal,
			amountPaid,
				et_notes.getText().toString()
				);

					if (!success) {

					android.widget.Toast.makeText(
					this,
					"Update failed",
					android.widget.Toast.LENGTH_SHORT
					).show();

					return;
				}

			db.deletePurchaseItems(purchaseId);

				} else {

				long newPurchaseId = db.insertPurchase(
			partyId,
			et_date.getText().toString(),
			et_time.getText().toString(),
			et_invoice_number.getText().toString(),
		grandTotal,
			amountPaid,
			et_notes.getText().toString()
				);

				if (newPurchaseId <= 0) {

		android.widget.Toast.makeText(
	this,
	"Save failed",
		android.widget.Toast.LENGTH_SHORT
		).show();

		return;
			}

		purchaseId = (int) newPurchaseId;
		}

		// Save all purchase items
		for (HashMap<String, Object> item : transactionItemList) {

			db.insertPurchaseItem(

			purchaseId,

		((Integer) item.get("item_id")).intValue(),

		((Double) item.get("quantity")).doubleValue(),

		((Double) item.get("purchase_price")).doubleValue(),

			((Double) item.get("total")).doubleValue(),

			(Integer) item.get("combo_id")
			);
			}

		android.widget.Toast.makeText(
		this,
		isEditMode ? "Purchase updated" : "Purchase saved",
	android.widget.Toast.LENGTH_SHORT
	).show();

		if (andNew) {

			resetFormForNewTransaction();

		} else {

			finish();
		}
		}
	private void setListViewHeightBasedOnChildren(ListView listView) {

		android.widget.ListAdapter listAdapter = listView.getAdapter();

		if (listAdapter == null) {
			return;
		}

		// Measuring with an unconstrained width (the old measure(0, 0))
		// lets a wrapping line - like the "Item: <long name>" line here -
		// measure as if it fit on one line, under-reporting that row's
		// real (taller, wrapped) height. That under-count adds up across
		// every row and the list ends up sized shorter than its real
		// content, clipping the last item(s). Measuring at the list's
		// actual width instead makes each row wrap exactly as it will
		// when shown, so the total height comes out right.
		int listViewWidth = listView.getWidth();

		if (listViewWidth <= 0) {

			// Not laid out yet (this can be the very first call, made
			// from loadPurchase()/loadSale() during onCreate before any
			// layout pass has happened) - fall back to the screen width
			// minus this screen's fixed 16dp side padding, which is what
			// the list will actually end up at.
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

	// =====================
	// The items list is a ListView expanded to full height (via
	// setListViewHeightBasedOnChildren) inside an outer ScrollView, so it
	// doesn't scroll on its own - once there are enough items to fill the
	// screen, a newly added one ends up below the fold with nothing
	// bringing it into view. This scrolls the outer ScrollView down to
	// the bottom of the items list right after it's been resized, so the
	// item just added is immediately visible.
	// =====================
	private void scrollToNewlyAddedItem() {

		if (scroll_transaction_edit == null) {
			return;
		}

		lv_transaction_items.post(new Runnable() {
				@Override
				public void run() {

					scroll_transaction_edit.smoothScrollTo(
						0,
						lv_transaction_items.getBottom()
					);
				}
			});
	}

	private void showDatePicker() {

		java.util.Calendar calendar =
			java.util.Calendar.getInstance();

		try {

			String[] parts =
				et_date.getText().toString().split("-");

			calendar.set(
				Integer.parseInt(parts[0]),
				Integer.parseInt(parts[1]) - 1,
				Integer.parseInt(parts[2])
			);

		} catch (Exception e) {
		}

		new android.app.DatePickerDialog(
			this,
			R.style.AppAlertDialogTheme,

			new android.app.DatePickerDialog.OnDateSetListener() {

				@Override
				public void onDateSet(
					android.widget.DatePicker view,
					int year,
					int month,
					int dayOfMonth) {

					et_date.setText(
						String.format(
							Locale.getDefault(),
							"%04d-%02d-%02d",
							year,
							month + 1,
							dayOfMonth
						)
					);
				}
			},

			calendar.get(java.util.Calendar.YEAR),
			calendar.get(java.util.Calendar.MONTH),
			calendar.get(java.util.Calendar.DAY_OF_MONTH)

		).show();
	}
	private void showTimePicker() {

		java.util.Calendar calendar =
			java.util.Calendar.getInstance();

		try {

			String[] parts =
				et_time.getText().toString().split(":");

			calendar.set(
				java.util.Calendar.HOUR_OF_DAY,
				Integer.parseInt(parts[0])
			);

			calendar.set(
				java.util.Calendar.MINUTE,
				Integer.parseInt(parts[1])
			);

		} catch (Exception e) {
		}

		new android.app.TimePickerDialog(
			this,
			R.style.AppAlertDialogTheme,

			new android.app.TimePickerDialog.OnTimeSetListener() {

				@Override
				public void onTimeSet(
					android.widget.TimePicker view,
					int hourOfDay,
					int minute) {

					timeManuallySet = true;

					et_time.setText(
						String.format(
							Locale.getDefault(),
							"%02d:%02d",
							hourOfDay,
							minute
						)
					);
				}
			},

			calendar.get(java.util.Calendar.HOUR_OF_DAY),
			calendar.get(java.util.Calendar.MINUTE),
			true

		).show();
	}


	private int getSelectedPartyPosition() {

		String partyName =
			actv_party.getText().toString().trim();

		for (int i = 0; i < parties.size(); i++) {

			if (parties.get(i)
				.get("name")
				.toString()
				.equalsIgnoreCase(partyName)) {

				return i;
			}
		}

		return -1;
	}
	
	
	private void saveTransaction(boolean andNew) {

		if (transactionType == TYPE_PURCHASE) {

			savePurchase(andNew);

		} else {

			saveSaleMode(andNew);
		}
	}
	
	
	private void saveSaleMode(boolean andNew) {

		if (isEditMode) {

			updateSale(andNew);

		} else {

			saveSale(andNew);
		}
	}

	// =====================
	// Called after a successful save when the user tapped "Save & New"
	// (or "Update & New"): clears the form and puts the activity back
	// into fresh "add" mode so the next transaction can be entered
	// right away, then reopens the add-item dialog like a brand new
	// transaction does.
	// =====================
	private void resetFormForNewTransaction() {

		isEditMode = false;
		transactionId = -1;

		transactionItemList.clear();
		transactionItemAdapter.notifyDataSetChanged();

		setListViewHeightBasedOnChildren(lv_transaction_items);

		actv_party.setText("", false);

		et_notes.setText("");
		et_notes.setVisibility(View.GONE);
		tv_add_note.setVisibility(View.VISIBLE);

		amountPaidEditedByUser = false;
		timeManuallySet = false;

		setCurrentDateTime();

		if (transactionType == TYPE_SALE) {

			et_invoice_number.setText(db.getNextSaleInvoiceNo());

		} else {

			et_invoice_number.setText(db.getNextPurchaseInvoiceNo());
		}

		tv_grand_total.setText("0.00");
		updateDefaultAmountPaid(0);

		String title =
			transactionType == TYPE_PURCHASE ? "Add Purchase" : "Add Sale";

		setTitle(title);
		tv_page_title.setText(title);

		btn_save_transaction.setText("Save");
		btn_save_and_new_transaction.setText("Save & New");

		if (transactionType == TYPE_PURCHASE) {

			showAddTransactionItemDialog();

		} else {

			showSaleItemDialog();
		}
	}
	
	
	private void loadPurchase() {

		if (transactionId == -1) {

			android.widget.Toast.makeText(
				this,
				"transactionId = -1",
				android.widget.Toast.LENGTH_LONG
			).show();

			return;
		}

		int purchaseId = transactionId;

		HashMap<String, Object> purchase =
			db.getPurchaseById(purchaseId);

		if (purchase.size() == 0) {

			android.widget.Toast.makeText(
				this,
				"Purchase not found",
				android.widget.Toast.LENGTH_SHORT
			).show();

			finish();
			return;
		}

		et_date.setText(
			purchase.get("date").toString()
		);

		et_time.setText(
			purchase.get("time").toString()
		);

		et_invoice_number.setText(
			purchase.get("invoice_number").toString()
		);

		et_amount_paid.setText(
			purchase.get("amount_paid").toString()
		);

		setFullPaidCheckboxSilently(
			(Double) purchase.get("amount_paid"),
			(Double) purchase.get("grand_total")
		);

		et_notes.setText(
			purchase.get("notes").toString()
		);

		if (et_notes.getText().toString().trim().length() > 0) {

			et_notes.setVisibility(View.VISIBLE);
			tv_add_note.setVisibility(View.GONE);
		}

		int partyId =
			((Integer) purchase.get("party_id")).intValue();

		for (int i = 0; i < parties.size(); i++) {

			if (((Integer) parties.get(i).get("id")).intValue() == partyId) {

				actv_party.setText(
					parties.get(i).get("name").toString(),
					false
				);

				break;
			}
		}

		transactionItemList.clear();

		transactionItemList.addAll(
			db.getPurchaseItemsForEdit(purchaseId)
		);

		transactionItemAdapter.notifyDataSetChanged();

		setListViewHeightBasedOnChildren(
			lv_transaction_items
		);

		updateGrandTotal();

		loadCashBaseline(-((Double) purchase.get("amount_paid")));
	}

	private void loadSale() {

		if (transactionId == -1) {

			finish();
			return;
		}

		HashMap<String, Object> sale =
			db.getSaleById(String.valueOf(transactionId));

		if (sale.size() == 0) {

			android.widget.Toast.makeText(
				this,
				"Sale not found",
				android.widget.Toast.LENGTH_SHORT
			).show();

			finish();
			return;
		}

		et_invoice_number.setText(
			sale.get("invoice_no").toString()
		);

		et_date.setText(
			sale.get("date").toString()
		);
		
		et_time.setText(
			sale.get("time").toString()
		);

		et_notes.setText(
			sale.get("notes").toString()
		);

		if (et_notes.getText().toString().trim().length() > 0) {

			et_notes.setVisibility(View.VISIBLE);
			tv_add_note.setVisibility(View.GONE);
		}

		et_amount_paid.setText(
			sale.get("paid_amount").toString()
		);

		setFullPaidCheckboxSilently(
			Double.parseDouble(sale.get("paid_amount").toString()),
			Double.parseDouble(sale.get("grand_total").toString())
		);

		int partyId =
			Integer.parseInt(
			sale.get("party_id").toString()
		);

		for (int i = 0; i < parties.size(); i++) {

			if (((Integer) parties.get(i).get("id")).intValue() == partyId) {

				actv_party.setText(
					parties.get(i).get("name").toString(),
					false
				);

				break;
			}
		}

		transactionItemList.clear();

		transactionItemList.addAll(
			db.getSaleItemsForEdit(String.valueOf(transactionId))
		);

		transactionItemAdapter.notifyDataSetChanged();

		setListViewHeightBasedOnChildren(
			lv_transaction_items
		);

		updateGrandTotal();

		loadCashBaseline(Double.parseDouble(sale.get("paid_amount").toString()));
	}


	private void saveSale(boolean andNew) {

		if (transactionItemList.size() == 0) {

			android.widget.Toast.makeText(
				this,
				"Please add at least one item",
				android.widget.Toast.LENGTH_SHORT
			).show();

			return;
		}

		saleMap.clear();

		if (parties.size() == 0) {

			android.widget.Toast.makeText(
				this,
				"No party available",
				android.widget.Toast.LENGTH_SHORT
			).show();

			return;
		}

		int partyPosition = getSelectedPartyPosition();

		if (partyPosition == -1) {

			android.widget.Toast.makeText(
				this,
				"Please select a party",
				android.widget.Toast.LENGTH_SHORT
			).show();

			return;
		}

		saleMap.put(
			"party_id",
			parties.get(partyPosition).get("id")
		);

		double subtotal = 0;

		for (HashMap<String, Object> item : transactionItemList) {

			subtotal += (Double) item.get("total");
		}

		double discount = 0;
		double otherCharges = 0;

		double grandTotal = subtotal - discount + otherCharges;

		double paidAmount = 0;

		try {

			paidAmount = Double.parseDouble(
				et_amount_paid.getText().toString()
			);

		} catch (Exception e) {
		}

		if (paidAmount < 0) {

			android.widget.Toast.makeText(
				this,
				"Amount paid cannot be negative",
				android.widget.Toast.LENGTH_SHORT
			).show();

			return;
		}

		if (paidAmount > grandTotal) {

			android.widget.Toast.makeText(
				this,
				"Amount paid cannot be greater than Grand Total",
				android.widget.Toast.LENGTH_SHORT
			).show();

			return;
		}

		double balance = grandTotal - paidAmount;

		saleMap.put("subtotal", subtotal);
		saleMap.put("discount", discount);
		saleMap.put("other_charges", otherCharges);
		saleMap.put("grand_total", grandTotal);
		saleMap.put("paid_amount", paidAmount);
		saleMap.put("balance", balance);
		saleMap.put("notes", et_notes.getText().toString());

		saleMap.put(
			"invoice_no",
			et_invoice_number.getText().toString()
		);

		saleMap.put(
			"date",
			et_date.getText().toString()
		);
		
		saleMap.put(
			"time",
			et_time.getText().toString()
		);

		long saleId = db.insertSale(saleMap);

		if (saleId == -1) {

			android.widget.Toast.makeText(
				this,
				"Failed to save sale",
				android.widget.Toast.LENGTH_SHORT
			).show();

			return;
		}
		
		for (HashMap<String, Object> item : transactionItemList) {

			HashMap<String, Object> itemData =
				new HashMap<String, Object>();

			itemData.put("sale_id", saleId);
			itemData.put("item_id", item.get("item_id"));
			itemData.put("qty", item.get("quantity"));
			itemData.put("rate", item.get("sale_price"));
			itemData.put("amount", item.get("total"));
			itemData.put("combo_id", item.get("combo_id"));

			db.insertSaleItem(itemData);
		}

		android.widget.Toast.makeText(
			this,
			"Sale saved",
			android.widget.Toast.LENGTH_SHORT
		).show();

		if (andNew) {

			resetFormForNewTransaction();

		} else {

			finish();
		}
		
	}
	
	private void showSaleItemDialog() {

		showAddTransactionItemDialog();

	}
	
	
	private void updateSale(boolean andNew) {

		if (transactionItemList.size() == 0) {

			android.widget.Toast.makeText(
				this,
				"Please add at least one item",
				android.widget.Toast.LENGTH_SHORT
			).show();

			return;
		}

		int partyPosition = getSelectedPartyPosition();

		if (partyPosition == -1) {

			android.widget.Toast.makeText(
				this,
				"Please select a party",
				android.widget.Toast.LENGTH_SHORT
			).show();

			return;
		}

		saleMap.clear();

		saleMap.put(
			"party_id",
			parties.get(partyPosition).get("id")
		);

		double subtotal = 0;

		for (HashMap<String, Object> item : transactionItemList) {

			subtotal += (Double) item.get("total");
		}

		double paidAmount = 0;

		try {

			paidAmount = Double.parseDouble(
				et_amount_paid.getText().toString()
			);

		} catch (Exception e) {
		}

		if (paidAmount < 0) {

			android.widget.Toast.makeText(
				this,
				"Amount paid cannot be negative",
				android.widget.Toast.LENGTH_SHORT
			).show();

			return;
		}

		if (paidAmount > subtotal) {

			android.widget.Toast.makeText(
				this,
				"Amount paid cannot be greater than Grand Total",
				android.widget.Toast.LENGTH_SHORT
			).show();

			return;
		}

		saleMap.put("invoice_no", et_invoice_number.getText().toString());
		saleMap.put("date", et_date.getText().toString());
		saleMap.put("time", et_time.getText().toString());
		saleMap.put("subtotal", subtotal);
		saleMap.put("discount", 0);
		saleMap.put("other_charges", 0);
		saleMap.put("grand_total", subtotal);
		saleMap.put("paid_amount", paidAmount);
		saleMap.put("balance", subtotal - paidAmount);
		saleMap.put("notes", et_notes.getText().toString());

		db.updateSale(
			transactionId,
			saleMap
		);

		db.deleteSaleItems(
			transactionId
		);

		for (HashMap<String, Object> item : transactionItemList) {

			HashMap<String, Object> itemData =
				new HashMap<String, Object>();

			itemData.put("sale_id", transactionId);
			itemData.put("item_id", item.get("item_id"));
			itemData.put("qty", item.get("quantity"));
			itemData.put("rate", item.get("sale_price"));
			itemData.put("amount", item.get("total"));
			itemData.put("combo_id", item.get("combo_id"));

			db.insertSaleItem(itemData);
		}

		android.widget.Toast.makeText(
			this,
			"Sale updated",
			android.widget.Toast.LENGTH_SHORT
		).show();

		if (andNew) {

			resetFormForNewTransaction();

		} else {

			finish();
		}
	}
	
	
	
	
	
}

