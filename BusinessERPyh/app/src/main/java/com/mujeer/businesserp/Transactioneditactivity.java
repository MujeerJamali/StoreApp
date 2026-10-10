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


	// Package-private (not private) so Expenseeditactivity's swipe
	// navigation can pass these as the "transaction_type" extra without
	// a magic number.
	static final int TYPE_PURCHASE = 0;
	static final int TYPE_SALE = 1;

	// Used when the user types an item name in the add-item dialog that
	// doesn't match anything and chooses to create it: Additemactivity is
	// opened for a result, and onActivityResult() below reopens the
	// add-item dialog pre-filled with whatever item came back.
	private static final int REQUEST_ADD_NEW_ITEM = 5001;

	// "+ Select Expenses" - see openSelectExpenses()/onActivityResult().
	private static final int REQUEST_SELECT_EXPENSES = 5002;

	// "+ Add New Party" - see loadParties()/onActivityResult().
	private static final int REQUEST_ADD_NEW_PARTY = 5003;

	private int transactionType = TYPE_PURCHASE;
	private boolean isEditMode = false;
	private int transactionId = -1;

	private SwipeNavigationHelper swipeNavigationHelper;

	// Set when this screen was opened from the Drafts list to finish a
	// parked Sale/Purchase - once the real save succeeds, this draft row
	// is deleted so it doesn't linger alongside the now-real transaction.
	private int draftId = -1;

	// Set (to the line being edited) when the "+ Add New Item" row is
	// tapped from the EDIT Item dialog rather than the Add Item dialog -
	// -1 means "not applicable" (either nothing pending, or the pending
	// create came from the Add dialog instead). onActivityResult() checks
	// this to decide whether to reopen showEditTransactionItem() on that
	// same line (with the new item preselected) or showAddTransactionItemDialog().
	private int pendingEditPositionForNewItem = -1;
	
	AutoCompleteTextView actv_party;
    EditText et_date, et_time, et_invoice_number, et_amount_paid, et_notes, et_due_date;
    View container_due_date;
    EditText et_search_transaction_items;
    CheckBox cb_full_paid;
    View container_purchase_costs;
    LinearLayout container_pending_purchase_costs_list;
    Button btn_select_expenses;
    View tv_add_note;
    TextView tv_grand_total;
    View container_sale_profit;
    TextView tv_sale_profit;
    View container_sale_discount;
    Button btn_discount_5, btn_discount_10, btn_discount_custom;
    EditText et_sale_discount_percent;
    TextView tv_discount_amount;
    View container_loyalty_preview;
    TextView tv_loyalty_preview;
    TextView tv_cash_before, tv_cash_after;
    TextView tv_page_title;
    Button btn_add_item, btn_save_transaction, btn_go_dashboard;
    Button btn_cancel_transaction, btn_save_and_new_transaction;
    Button btn_save_draft;
    ListView lv_transaction_items;
    ScrollView scroll_transaction_edit;
    View items_section_container;

    DatabaseHelper db;
	ArrayList<HashMap<String, Object>> parties;
    ArrayList<HashMap<String,Object>> transactionItemList;
    TransactionItemAdapter transactionItemAdapter;
	HashMap<String, Object> saleMap = new HashMap<>();

	// Existing Expenses (Petrol/Shipping/Packaging/...) linked via
	// "+ Select Expenses", staged here regardless of new-purchase or
	// edit-mode - see openSelectExpenses()/onActivityResult(). Each map
	// holds expense_id/amount/item/date, plus "selections" (an
	// ArrayList<HashMap<String,Object>> of {"purchase_id": Integer},
	// matching db.applyExpensePurchaseLinks()'s own shape) when this was
	// picked in edit mode's multi-purchase step - a brand-new purchase's
	// entry has no "selections" key at all, since singlePurchaseMode
	// skips that step and implicitly means "100% to this purchase".
	// Nothing here is written to the database until savePurchase()
	// actually succeeds, same "nothing touches the database until Save"
	// pattern transactionItemList already follows - "+ Select Expenses"
	// used to write an edit-mode pick straight to the database instead,
	// bypassing Update Transaction and only showing up once the screen
	// was reloaded from scratch; staging it here like everything else
	// fixed both problems at once.
	ArrayList<HashMap<String, Object>> pendingLinkedExpenses = new ArrayList<>();

	// A purchase created before the Purchase Costs feature may still
	// carry its old single "Other Charges" amount in these two DB
	// columns (see DatabaseHelper's other_charges/other_charges_to_party)
	// - loadPurchase() reads them in so savePurchase() can pass them
	// through to updatePurchase() unchanged, preserving that purchase's
	// existing grand_total/party balance instead of silently dropping
	// this now-retired field's contribution. A brand new purchase never
	// sets these (they stay 0/false, per the field's own retirement
	// comment: "left in place, just unused by new entries").
	double legacyOtherCharges = 0;
	boolean legacyOtherChargesToParty = false;

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

		draftId = getIntent().getIntExtra(
			"draft_id",
			-1
		);

		// Swipe-up/down cycles Sale -> Purchase -> Expense -> Sale (and
		// reverse on swipe down) - only on the plain Add flow, never
		// while editing an existing transaction, so a swipe can't be
		// mistaken for navigating away from in-progress edits.
		if (!isEditMode) {

			if (transactionType == TYPE_SALE) {

				swipeNavigationHelper = new SwipeNavigationHelper(
					this,
					new Runnable() {
						@Override
						public void run() {
							navigateToAddPurchase();
						}
					},
					new Runnable() {
						@Override
						public void run() {
							navigateToAddExpense();
						}
					}
				);

			} else {

				swipeNavigationHelper = new SwipeNavigationHelper(
					this,
					new Runnable() {
						@Override
						public void run() {
							navigateToAddExpense();
						}
					},
					new Runnable() {
						@Override
						public void run() {
							navigateToAddSale();
						}
					}
				);
			}
		}

		actv_party = findViewById(R.id.actv_party);
        et_date = findViewById(R.id.et_date);

		et_date.setOnClickListener(new View.OnClickListener() {

				@Override
				public void onClick(View v) {

					showDatePicker();
				}
			});

		container_due_date = findViewById(R.id.container_due_date);
		et_due_date = findViewById(R.id.et_due_date);

		et_due_date.setOnClickListener(new View.OnClickListener() {

				@Override
				public void onClick(View v) {

					showDueDatePicker();
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

        container_purchase_costs = findViewById(R.id.container_purchase_costs);
        container_pending_purchase_costs_list =
            findViewById(R.id.container_pending_purchase_costs_list);
        btn_select_expenses = findViewById(R.id.btn_select_expenses);

        // Only a Purchase has a supplier bill to add costs to - a Sale
        // has no equivalent concept.
        container_purchase_costs.setVisibility(
            transactionType == TYPE_PURCHASE ? View.VISIBLE : View.GONE
        );

        btn_select_expenses.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    openSelectExpenses();
                }
            });

		tv_add_note.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					et_notes.setVisibility(View.VISIBLE);
					tv_add_note.setVisibility(View.GONE);
					et_notes.requestFocus();
				}
			});
        tv_grand_total = findViewById(R.id.tv_grand_total);
        container_sale_profit = findViewById(R.id.container_sale_profit);
        tv_sale_profit = findViewById(R.id.tv_sale_profit);
        container_sale_discount = findViewById(R.id.container_sale_discount);
        btn_discount_5 = findViewById(R.id.btn_discount_5);
        btn_discount_10 = findViewById(R.id.btn_discount_10);
        btn_discount_custom = findViewById(R.id.btn_discount_custom);
        et_sale_discount_percent = findViewById(R.id.et_sale_discount_percent);
        tv_discount_amount = findViewById(R.id.tv_discount_amount);
        container_loyalty_preview = findViewById(R.id.container_loyalty_preview);
        tv_loyalty_preview = findViewById(R.id.tv_loyalty_preview);
        tv_cash_before = findViewById(R.id.tv_cash_before);
        tv_cash_after = findViewById(R.id.tv_cash_after);
        tv_page_title = findViewById(R.id.tv_page_title);
        btn_add_item = findViewById(R.id.btn_add_item);
        btn_save_transaction = findViewById(R.id.btn_update_purchase);
        btn_cancel_transaction = findViewById(R.id.btn_cancel_transaction);
        btn_save_and_new_transaction = findViewById(R.id.btn_save_and_new_transaction);
        btn_go_dashboard = findViewById(R.id.btn_go_dashboard);
        btn_save_draft = findViewById(R.id.btn_save_draft);

        // Only makes sense for a not-yet-real transaction - editing one
        // that already exists in the database has nothing to "park".
        btn_save_draft.setVisibility(isEditMode ? View.GONE : View.VISIBLE);

        btn_save_draft.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					saveDraft();
				}
			});

		btn_go_dashboard.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					goToDashboard();
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

        // Created (once) before loadParties() so it's already in the
        // in-memory list below - a new Sale/Purchase defaults its party
        // to this, since most walk-in sales/cash purchases have no real
        // named party, and every transaction now requires a party
        // selection (no more blank "no party" shortcut).
        if (!isEditMode && transactionType == TYPE_SALE) {
            db.getOrCreatePartyId("Cash Sale");
        }

        if (!isEditMode && transactionType == TYPE_PURCHASE) {
            db.getOrCreatePartyId("Cash Purchase");
        }

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

			if (transactionType == TYPE_SALE) {
				actv_party.setText("Cash Sale", false);
			}

			if (transactionType == TYPE_PURCHASE) {
				actv_party.setText("Cash Purchase", false);
			}

			if (draftId != -1) {
				loadDraft(draftId);
			} else {
				maybePromptResumeAutosave();
			}

			autosaveHandler.postDelayed(autosaveRunnable, 20_000);
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
					finishOrGoToDashboard();
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

			container_sale_discount.setVisibility(View.VISIBLE);

		} else {

			btn_save_transaction.setText(
				isEditMode ? "Update Purchase" : "Save Purchase"
			);

			container_sale_discount.setVisibility(View.GONE);
		}

		btn_discount_5.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					et_sale_discount_percent.setText("5");
				}
			}
		);

		btn_discount_10.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					et_sale_discount_percent.setText("10");
				}
			}
		);

		btn_discount_custom.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {

					et_sale_discount_percent.requestFocus();
					et_sale_discount_percent.selectAll();

					et_sale_discount_percent.post(new Runnable() {
							@Override
							public void run() {

								android.view.inputmethod.InputMethodManager imm =
									(android.view.inputmethod.InputMethodManager)
									getSystemService(INPUT_METHOD_SERVICE);

								if (imm != null) {

									imm.showSoftInput(
										et_sale_discount_percent,
										android.view.inputmethod.InputMethodManager.SHOW_FORCED
									);
								}
							}
						}
					);
				}
			}
		);

		et_sale_discount_percent.addTextChangedListener(new android.text.TextWatcher() {

				@Override
				public void beforeTextChanged(
					CharSequence s, int start, int count, int after) {}

				@Override
				public void onTextChanged(CharSequence s, int start, int before, int count) {}

				@Override
				public void afterTextChanged(android.text.Editable s) {
					updateGrandTotal();
				}
			}
		);
		
		
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
		autosaveHandler.removeCallbacks(autosaveRunnable);
	}

	@Override
	public boolean dispatchTouchEvent(android.view.MotionEvent ev) {

		if (swipeNavigationHelper != null) {
			swipeNavigationHelper.onTouchEvent(ev);
		}

		return super.dispatchTouchEvent(ev);
	}

	// Starts a fresh instance rather than finishing this one, so nothing
	// typed here is ever lost to a swipe - the previous Add screen just
	// sits in the back stack, reachable with a normal Back press.
	private void navigateToAddSale() {

		Intent intent = new Intent(this, Transactioneditactivity.class);
		intent.putExtra("transaction_type", TYPE_SALE);
		startActivity(intent);
	}

	private void navigateToAddPurchase() {

		Intent intent = new Intent(this, Transactioneditactivity.class);
		intent.putExtra("transaction_type", TYPE_PURCHASE);
		startActivity(intent);
	}

	private void navigateToAddExpense() {

		startActivity(new Intent(this, Expenseeditactivity.class));
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		super.onActivityResult(requestCode, resultCode, data);

		if (requestCode == REQUEST_ADD_NEW_ITEM) {

			int newItemId = (resultCode == RESULT_OK && data != null)
				? data.getIntExtra("item_id", -1)
				: -1;

			if (newItemId != -1 && pendingEditPositionForNewItem != -1) {

				int editPosition = pendingEditPositionForNewItem;
				pendingEditPositionForNewItem = -1;

				showEditTransactionItem(editPosition, newItemId);

			} else if (newItemId != -1) {

				showAddTransactionItemDialog(newItemId);

			} else {

				// Cancelled (back button, etc.) - don't let a stale
				// pending edit position redirect some later, unrelated
				// "+ Add New Item" tap from the Add dialog into edit mode.
				pendingEditPositionForNewItem = -1;
			}
		}

		if (requestCode == REQUEST_ADD_NEW_PARTY
			&& resultCode == RESULT_OK
			&& data != null) {

			String newPartyName = data.getStringExtra("party_name");

			if (newPartyName != null) {

				// Reload first so the new party's balance subtitle (and
				// the party itself) is actually in the dropdown's list
				// before selecting it by text.
				loadParties();

				actv_party.setText(newPartyName, false);
			}
		}

		if (requestCode == REQUEST_SELECT_EXPENSES
			&& resultCode == RESULT_OK
			&& data != null) {

			if (data.hasExtra("expense_id")) {

				// LinkExpenseActivity never writes to the database itself
				// (see openSelectExpenses()) - this is always a pending
				// pick, applied for real only once Save/Update Transaction
				// succeeds (see savePurchase()), whether this is a
				// brand-new purchase or editing an already-saved one.
				HashMap<String, Object> pending = new HashMap<String, Object>();
				pending.put("expense_id", data.getIntExtra("expense_id", -1));
				pending.put("amount", data.getDoubleExtra("amount", 0));
				pending.put("item", data.getStringExtra("item"));
				pending.put("date", data.getStringExtra("date"));

				if (data.hasExtra("purchase_ids")) {

					int[] purchaseIds = data.getIntArrayExtra("purchase_ids");
					ArrayList<HashMap<String, Object>> selections = new ArrayList<>();

					for (int purchaseId : purchaseIds) {

						HashMap<String, Object> selection = new HashMap<>();
						selection.put("purchase_id", purchaseId);
						selections.add(selection);
					}

					pending.put("selections", selections);
				}

				pendingLinkedExpenses.add(pending);

				refreshLinkedExpensesDisplay();
			}
		}
	}

	// =====================
	// Opens LinkExpenseActivity to link an existing Expense as this
	// purchase's landed cost. An already-saved purchase (editing an
	// existing one) has a real id to preselect in the multi-select-
	// purchases step there, which can also split the expense across
	// OTHER purchases; a purchase still being entered has no id yet, so
	// it's opened in single-purchase mode instead, which skips that step
	// entirely (nothing else to split across before this purchase even
	// exists). Either way LinkExpenseActivity only ever hands the pick
	// back as a pending entry (see onActivityResult()) - applied for
	// real only once Save/Update Transaction succeeds (see
	// savePurchase()), never written to the database just from picking.
	// =====================
	private void openSelectExpenses() {

		Intent intent = new Intent(
			Transactioneditactivity.this, LinkExpenseActivity.class
		);

		if (isEditMode) {
			intent.putExtra("preselect_purchase_id", transactionId);
		} else {
			intent.putExtra("single_purchase_mode", true);
		}

		startActivityForResult(intent, REQUEST_SELECT_EXPENSES);
	}

	// =====================
	// Rebuilds container_pending_purchase_costs_list from BOTH sources at
	// once: an already-saved purchase's links from a PREVIOUS save
	// (straight from the database, via getLinkedExpensesForPurchase) and
	// anything picked THIS session via "+ Select Expenses" (from the
	// in-memory pendingLinkedExpenses - nothing written to the database
	// until Save/Update Transaction succeeds, for a brand-new purchase
	// and an edit alike). The two need different "Remove" behavior, so
	// a pending row is labeled to tell them apart: removing an
	// already-applied row deletes its link right away (same "not
	// retroactively corrected" simplification as the rest of this
	// feature - the extra_cost_per_unit blend it already applied stays),
	// while removing a pending row is a plain in-memory removal, since
	// nothing was written for it yet.
	// =====================
	private void refreshLinkedExpensesDisplay() {

		container_pending_purchase_costs_list.removeAllViews();

		if (isEditMode && transactionId != -1) {

			ArrayList<HashMap<String, Object>> links =
				db.getLinkedExpensesForPurchase(transactionId);

			for (HashMap<String, Object> link : links) {

				final int linkId = (Integer) link.get("link_id");

				View row = getLayoutInflater().inflate(
					R.layout.transaction_purchase_cost_row,
					container_pending_purchase_costs_list,
					false
				);

				TextView tvLabel = row.findViewById(R.id.tv_tpc_label);
				Button btnRemove = row.findViewById(R.id.btn_tpc_remove);

				tvLabel.setText(
					link.get("item") + " - " +
					AmountFormat.format((Double) link.get("allocated_amount")) +
					" - " + link.get("date")
				);

				btnRemove.setVisibility(View.VISIBLE);

				btnRemove.setOnClickListener(new View.OnClickListener() {
						@Override
						public void onClick(View v) {

							new AlertDialog.Builder(Transactioneditactivity.this)
								.setTitle("Remove Linked Expense")
								.setMessage("Remove this expense link from the purchase?")
								.setPositiveButton("Remove",
								new android.content.DialogInterface.OnClickListener() {

									@Override
									public void onClick(
										android.content.DialogInterface dialog, int which) {

										db.deletePurchaseExpenseLink(linkId);
										refreshLinkedExpensesDisplay();
									}
								})
								.setNegativeButton("Cancel", null)
								.show();
						}
					});

				container_pending_purchase_costs_list.addView(row);
			}
		}

		for (int i = 0; i < pendingLinkedExpenses.size(); i++) {

			final int index = i;
			HashMap<String, Object> pending = pendingLinkedExpenses.get(i);

			View row = getLayoutInflater().inflate(
				R.layout.transaction_purchase_cost_row,
				container_pending_purchase_costs_list,
				false
			);

			TextView tvLabel = row.findViewById(R.id.tv_tpc_label);
			Button btnRemove = row.findViewById(R.id.btn_tpc_remove);

			tvLabel.setText(
				pending.get("item") + " - " +
				AmountFormat.format((Double) pending.get("amount")) +
				" - " + pending.get("date") +
				(isEditMode ? " (pending - applies on Update)" : "")
			);

			btnRemove.setVisibility(View.VISIBLE);

			btnRemove.setOnClickListener(new View.OnClickListener() {
					@Override
					public void onClick(View v) {

						pendingLinkedExpenses.remove(index);
						refreshLinkedExpensesDisplay();
					}
				});

			container_pending_purchase_costs_list.addView(row);
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

		final TwoLineAutoCompleteAdapter adapter =
			new TwoLineAutoCompleteAdapter(
            this,
            partyNames,
            partySubtitles,
            "+ Add New Party"
        );


		actv_party.setAdapter(adapter);
		actv_party.setThreshold(1);

		final PreSelectionTextWatcher partyTextTracker = new PreSelectionTextWatcher();
		actv_party.addTextChangedListener(partyTextTracker);

		// Refreshes the loyalty points preview (see updateLoyaltyPreview())
		// the moment the party changes, not just when items/discount do.
		actv_party.addTextChangedListener(new android.text.TextWatcher() {

				@Override
				public void beforeTextChanged(
					CharSequence s, int start, int count, int after) {}

				@Override
				public void onTextChanged(CharSequence s, int start, int before, int count) {}

				@Override
				public void afterTextChanged(android.text.Editable s) {
					updateGrandTotal();
				}
			}
		);

		actv_party.setOnItemClickListener(
			new AdapterView.OnItemClickListener() {

				@Override
				public void onItemClick(
					AdapterView<?> parent, View view, int position, long id) {

					// Always the dropdown's last row - jumps straight to
					// Addpartyactivity with whatever was typed, same
					// pattern as the Item field's own "+ Add New Item".
					if (adapter.isAddNewPosition(position)) {

						String typedName = partyTextTracker.textBeforeChange.trim();

						actv_party.setText("", false);

						Intent intent = new Intent(
							Transactioneditactivity.this,
							Addpartyactivity.class
						);

						intent.putExtra("party_name", typedName);

						startActivityForResult(intent, REQUEST_ADD_NEW_PARTY);
					}
				}
			}
		);
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

		return AmountFormat.formatPlain(stock);
	}

	// Moves whatever items were sold most recently to the front of the
	// Add Item dialog's picker - on both the Sale and Purchase screens,
	// since a fast-selling item is exactly the one worth restocking
	// too. See ItemPickerUtils for the shared implementation (also used
	// by GenerateEntriesActivity) and why reordering the source list is
	// enough to pin them at the top of an AutoCompleteTextView's
	// dropdown too.
	private void pinRecentlySoldItemsFirst(ArrayList<HashMap<String, Object>> items) {
		ItemPickerUtils.pinRecentlySoldItemsFirst(db, items);
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

		// Created here (rather than at the end, as it used to be) so
		// actvItem's own item-click listener below can dismiss it before
		// jumping to Additemactivity for the "+ Add New Item" row - see
		// that listener.
		final AlertDialog dialog =
			new AlertDialog.Builder(this)
			.setView(view)
			.create();

		final AutoCompleteTextView actvItem =
			view.findViewById(R.id.actv_item);

		final EditText etQuantity =
			view.findViewById(R.id.et_quantity);

		final EditText etPurchasePrice =
			view.findViewById(R.id.et_purchase_price);

		final EditText etTotal =
			view.findViewById(R.id.et_item_total);

		final Button btnQuantityMinus =
			view.findViewById(R.id.btn_quantity_minus);

		final Button btnQuantityPlus =
			view.findViewById(R.id.btn_quantity_plus);

		// Defaults Quantity to 1 rather than leaving it blank, so Total
		// already shows a real quantity*price figure as soon as an item
		// (and its price) is picked, instead of reading as 0 until the
		// user manually types a quantity.
		etQuantity.setText("1");

		final Double[] exactPriceOverride =
			wireQuantityPriceTotalSync(etQuantity, etPurchasePrice, etTotal);

		wireQuantityStepper(etQuantity, btnQuantityMinus, btnQuantityPlus);

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

		pinRecentlySoldItemsFirst(items);

		final ArrayList<String> itemNames =
			new ArrayList<String>();

		// Parallel to itemNames (same order/size) - lets the dropdown's
		// filter also match a typed item code, even though the code
		// itself is never the text shown/selected (see
		// TwoLineAutoCompleteAdapter's extraSearchText).
		final ArrayList<String> itemCodesForSearch =
			new ArrayList<String>();

		for (HashMap<String, Object> item : items) {

			itemNames.add(
				(String) item.get("name")
			);

			itemCodesForSearch.add(
				(String) item.get("code")
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
			itemSubtitles,
			"+ Add New Item",
			itemCodesForSearch
		);

		actvItem.setAdapter(adapter);
		actvItem.setThreshold(1);

		final int[] selectedPosition = {-1};

		final PreSelectionTextWatcher itemTextTracker = new PreSelectionTextWatcher();
		actvItem.addTextChangedListener(itemTextTracker);

		actvItem.setOnItemClickListener(
			new AdapterView.OnItemClickListener() {

				@Override
				public void onItemClick(
					AdapterView<?> parent,
					View view,
					int position,
					long id) {

					// Always the dropdown's last row - jumps straight to
					// Additemactivity rather than requiring the typed text
					// to mismatch first (see promptCreateNewItemFromDialog()
					// for that older, still-available fallback via the
					// Add/Add & New buttons).
					if (adapter.isAddNewPosition(position)) {

						String typedName = itemTextTracker.textBeforeChange.trim();

						actvItem.setText("", false);
						dialog.dismiss();

						Intent intent = new Intent(
							Transactioneditactivity.this,
							Additemactivity.class
						);

						intent.putExtra("item_name", typedName);

						startActivityForResult(intent, REQUEST_ADD_NEW_ITEM);
						return;
					}

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

					showKeyboardOn(etQuantity);
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
						exactPriceOverride,
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
						exactPriceOverride,
						varietySpinners,
						varietyValuesByGroup)) {

						// Clear all fields and refocus the item name so
						// the next item can be entered right away.
						selectedPosition[0] = -1;

						actvItem.setText("", false);
						etQuantity.setText("1");
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
	//
	// Every number shown in this app is capped to 2 decimals, Price
	// included - but a Total-driven Price can genuinely need more than 2
	// (210 / 36 = 5.8333...) to make the typed Total exact. The returned
	// array holds that exact value, invisibly, whenever the displayed
	// (rounded) Price wouldn't multiply back out to the right Total; the
	// caller must prefer this over re-parsing the Price field when it
	// actually saves the item. A genuine (non-programmatic) edit to
	// Price clears it, since the user's typed number then takes over.
	// =====================
	// +/- steppers beside the Quantity field - step of 1, floor of 1 (no
	// point stepping down into 0 or negative), field stays manually
	// editable either way. Reuses etQuantity's own TextWatcher (see
	// wireQuantityPriceTotalSync above) to recompute Total - a plain
	// setText() here is enough, no separate recompute call needed.
	private void wireQuantityStepper(
		final EditText etQuantity, Button btnMinus, Button btnPlus) {

		btnMinus.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {

					double quantity = 0;

					try {
						quantity = Double.parseDouble(etQuantity.getText().toString().trim());
					} catch (Exception e) {
					}

					if (quantity > 1) {
						etQuantity.setText(AmountFormat.formatPlain(quantity - 1));
					}
				}
			});

		btnPlus.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {

					double quantity = 0;

					try {
						quantity = Double.parseDouble(etQuantity.getText().toString().trim());
					} catch (Exception e) {
					}

					etQuantity.setText(AmountFormat.formatPlain(quantity + 1));
				}
			});
	}

	private Double[] wireQuantityPriceTotalSync(
		final EditText etQuantity,
		final EditText etPurchasePrice,
		final EditText etTotal) {

		final boolean[] suppress = {false};
		final Double[] exactPriceOverride = {null};

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

				try {
					quantity = Double.parseDouble(etQuantity.getText().toString().trim());
				} catch (Exception e) {
				}

				double price;

				if (exactPriceOverride[0] != null) {

					price = exactPriceOverride[0];

				} else {

					price = 0;

					try {
						price = Double.parseDouble(etPurchasePrice.getText().toString().trim());
					} catch (Exception e) {
					}
				}

				suppress[0] = true;
				etTotal.setText(AmountFormat.formatPlain(quantity * price));
				suppress[0] = false;
			}
		};

		etQuantity.addTextChangedListener(recomputeTotal);
		etPurchasePrice.addTextChangedListener(recomputeTotal);

		etPurchasePrice.addTextChangedListener(new android.text.TextWatcher() {

			@Override
			public void beforeTextChanged(CharSequence s, int start, int count, int after) {
			}

			@Override
			public void onTextChanged(CharSequence s, int start, int before, int count) {
			}

			@Override
			public void afterTextChanged(android.text.Editable s) {

				if (!suppress[0]) {
					exactPriceOverride[0] = null;
				}
			}
		});

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

				double exactPrice = total / quantity;

				exactPriceOverride[0] = exactPrice;

				suppress[0] = true;
				etPurchasePrice.setText(AmountFormat.formatPlain(exactPrice));
				suppress[0] = false;
			}
		});

		// Seed Total from whatever Quantity/Price already hold (e.g. an
		// existing row being edited) without bouncing that initial write
		// back into Price. If that existing price already carries more
		// precision than 2 decimals (e.g. it was itself saved from an
		// exact-Total edit in an earlier session), preserve it the same
		// way - as an override behind a cleaned-up display - rather than
		// silently truncating it the moment this dialog opens.
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

		String roundedPriceText = AmountFormat.formatPlain(initialPrice);

		double roundedPrice = 0;

		try {
			roundedPrice = Double.parseDouble(roundedPriceText);
		} catch (Exception e) {
		}

		if (Math.abs(roundedPrice - initialPrice) > 0.0001) {

			exactPriceOverride[0] = initialPrice;

			suppress[0] = true;
			etPurchasePrice.setText(roundedPriceText);
			suppress[0] = false;
		}

		suppress[0] = true;
		etTotal.setText(AmountFormat.formatPlain(initialQuantity * initialPrice));
		suppress[0] = false;

		return exactPriceOverride;
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

	private void goToDashboard() {

		Intent intent = new Intent(
			Transactioneditactivity.this,
			MainActivity.class
		);

		// This activity may now be the task root (app launcher), so
		// clear back to a fresh Dashboard instead of stacking on top
		// of it.
		intent.setFlags(
			Intent.FLAG_ACTIVITY_CLEAR_TOP |
			Intent.FLAG_ACTIVITY_NEW_TASK
		);

		startActivity(intent);
		finish();
	}

	// =====================
	// The finish() every "I'm done with this screen" path (a successful
	// Save, Save & New's plain-Save case, Save as Draft, Cancel, an
	// edit-mode row that turned out missing) should actually call - a
	// bare finish() exits the whole app instead when this screen is the
	// launcher (task root, nothing behind it in the back stack), same
	// reasoning as onBackPressed().
	// =====================
	private void finishOrGoToDashboard() {

		if (isTaskRoot()) {

			goToDashboard();

		} else {

			finish();
		}
	}

	// =====================
	// This screen is the app's launcher (opens straight to Add Sale),
	// so when there's nothing else in the back stack, plain Back would
	// otherwise exit the app entirely - go to the Dashboard instead,
	// same as tapping btn_go_dashboard. isTaskRoot() is what tells the
	// two cases apart: when this screen was instead opened from
	// somewhere else in the app (Add Sale from a party's page, editing
	// an existing sale, a Drafts entry, etc.), it isn't the task root,
	// so Back keeps its normal behavior of returning to that screen.
	// =====================
	@Override
	public void onBackPressed() {

		if (isTaskRoot()) {

			goToDashboard();

		} else {

			super.onBackPressed();
		}
	}

	// =====================
	// Restores every field this screen can hold from a draft saved
	// earlier via saveDraft() - whatever was there when it was parked,
	// complete or not, since a draft is explicitly allowed to be
	// incomplete until the user actually hits Save/Update.
	// =====================
	@SuppressWarnings("unchecked")
	private void loadDraft(int id) {

		HashMap<String, Object> draftRow = db.getDraftById(id);

		if (draftRow == null) {
			return;
		}

		HashMap<String, Object> data = DraftCodec.decode((String) draftRow.get("data"));

		applyDraftData(data);
	}

	// Shared by loadDraft() (a real, user-chosen draft from the Drafts
	// list) and the crash-safety autosave resume prompt (see
	// maybePromptResumeAutosave()) - both restore the exact same field
	// set, built by buildDraftData() below.
	@SuppressWarnings("unchecked")
	private void applyDraftData(HashMap<String, Object> data) {

		Integer partyId = (Integer) data.get("party_id");

		if (partyId != null) {

			for (int i = 0; i < parties.size(); i++) {

				if (((Integer) parties.get(i).get("id")).intValue() == partyId.intValue()) {

					actv_party.setText((String) parties.get(i).get("name"), false);
					break;
				}
			}
		}

		if (data.get("date") != null) {
			et_date.setText((String) data.get("date"));
		}

		if (data.get("time") != null) {
			timeManuallySet = true;
			et_time.setText((String) data.get("time"));
		}

		if (data.get("invoice_number") != null) {
			et_invoice_number.setText((String) data.get("invoice_number"));
		}

		if (data.get("notes") != null && ((String) data.get("notes")).length() > 0) {

			et_notes.setText((String) data.get("notes"));
			et_notes.setVisibility(View.VISIBLE);
			tv_add_note.setVisibility(View.GONE);
		}

		if (data.get("amount_paid") != null) {

			amountPaidEditedByUser = true;
			et_amount_paid.setText((String) data.get("amount_paid"));
		}

		if (transactionType == TYPE_SALE && data.get("discount_percent") != null) {
			et_sale_discount_percent.setText((String) data.get("discount_percent"));
		}

		// Sale or Purchase - see saveDraft()/updateDueDateVisibility().
		// Read back before updateGrandTotal() runs below (triggered once
		// the items list is restored a few lines down) so the default-
		// due-date logic sees it already filled in.
		if (data.get("due_date") != null) {
			et_due_date.setText((String) data.get("due_date"));
		}

		ArrayList<HashMap<String, Object>> pendingFromDraft =
			(ArrayList<HashMap<String, Object>>) data.get("pending_linked_expenses");

		if (pendingFromDraft != null) {

			pendingLinkedExpenses.clear();
			pendingLinkedExpenses.addAll(pendingFromDraft);

			refreshLinkedExpensesDisplay();
		}

		ArrayList<HashMap<String, Object>> items =
			(ArrayList<HashMap<String, Object>>) data.get("items");

		if (items != null && !items.isEmpty()) {

			transactionItemList.clear();
			transactionItemList.addAll(items);

			transactionItemAdapter.notifyDataSetChanged();

			setListViewHeightBasedOnChildren(lv_transaction_items);

			updateGrandTotal();
		}
	}

	// =====================
	// Parks whatever is currently on screen as a draft, without any of
	// the validation a real save requires (missing party, no items, an
	// amount that doesn't parse - all fine here, since finishing the
	// entry later is exactly what a draft is for). Deliberately mirrors
	// only the fields this screen has, generically enough that both
	// Purchase and Sale use the same method.
	// =====================
	// Builds whatever is currently on screen into a draft-shaped map,
	// deliberately with none of the validation a real save requires
	// (missing party, no items, an amount that doesn't parse - all fine
	// here, since finishing the entry later is exactly what a draft is
	// for). Shared by saveDraft() (the user's own explicit "Save as
	// Draft") and the periodic crash-safety autosave tick (see
	// autosaveTick()) - mirrors only the fields this screen has,
	// generically enough that both Purchase and Sale use it as-is.
	private HashMap<String, Object> buildDraftData() {

		HashMap<String, Object> data = new HashMap<String, Object>();

		int partyPosition = getSelectedPartyPosition();

		if (partyPosition != -1) {
			data.put("party_id", parties.get(partyPosition).get("id"));
		}

		data.put("date", et_date.getText().toString());
		data.put("time", et_time.getText().toString());
		data.put("invoice_number", et_invoice_number.getText().toString());
		data.put("notes", et_notes.getText().toString());
		data.put("amount_paid", et_amount_paid.getText().toString());
		data.put("items", new ArrayList<HashMap<String, Object>>(transactionItemList));

		if (transactionType == TYPE_SALE) {
			data.put("discount_percent", et_sale_discount_percent.getText().toString());
		}

		if (
			(transactionType == TYPE_SALE || transactionType == TYPE_PURCHASE) &&
			container_due_date != null &&
			container_due_date.getVisibility() == View.VISIBLE
		) {
			data.put("due_date", et_due_date.getText().toString());
		}

		if (transactionType == TYPE_PURCHASE) {
			data.put(
				"pending_linked_expenses",
				new ArrayList<HashMap<String, Object>>(pendingLinkedExpenses)
			);
		}

		return data;
	}

	private void saveDraft() {

		HashMap<String, Object> data = buildDraftData();

		// Recomputed rather than reused from buildDraftData() - only
		// needed here for the label shown in the Drafts list.
		int partyPosition = getSelectedPartyPosition();

		String encoded = DraftCodec.encode(data);

		if (encoded == null) {

			android.widget.Toast.makeText(
				this,
				"Could not save draft",
				android.widget.Toast.LENGTH_SHORT
			).show();

			return;
		}

		String partyLabel = "No party";

		if (partyPosition != -1) {
			partyLabel = (String) parties.get(partyPosition).get("name");
		}

		String label =
			(transactionType == TYPE_PURCHASE ? "Purchase" : "Sale") +
			" - " + partyLabel + " - " + transactionItemList.size() +
			(transactionItemList.size() == 1 ? " item" : " items");

		String type = transactionType == TYPE_PURCHASE ?
			DatabaseHelper.DRAFT_TYPE_PURCHASE : DatabaseHelper.DRAFT_TYPE_SALE;

		db.insertDraft(
			type,
			label,
			encoded,
			et_date.getText().toString(),
			et_time.getText().toString()
		);

		// This explicit draft now holds the entry - the silent autosave
		// safety net for this type is no longer needed.
		DraftAutosave.clear(db, type);

		// Spells out what tapping this actually does - row #48's own
		// feedback ("Hold/resume a sale - I don't know how it works")
		// was this exact button under a different name than the user
		// expected: there's no separate "Hold Sale" concept, this one
		// button both holds it now and is how to resume it later (open
		// Drafts, tap the row, finish the sale from where it left off).
		android.widget.Toast.makeText(
			this,
			"Held - resume it anytime from Drafts",
			android.widget.Toast.LENGTH_LONG
		).show();

		finishOrGoToDashboard();
	}

	// =====================
	// CRASH-SAFE AUTOSAVE - a silent, periodic safety net for a brand-
	// new (not-yet-committed) Sale/Purchase, completely separate from
	// the user's own explicit "Save as Draft" above. Ticks every 20s
	// while this screen is open (started/stopped alongside
	// timeTickHandler - see onCreate()/onDestroy()), and writes nothing
	// when there's nothing worth protecting yet (an untouched, still-
	// blank screen) so a real autosave from a previous crashed session
	// is never silently overwritten with an empty one on the way back
	// in. Never shown in DraftsActivity, never part of the Vyapar
	// round-trip - see DatabaseHelper.DATABASE_VERSION's comment.
	// =====================
	android.os.Handler autosaveHandler = new android.os.Handler(android.os.Looper.getMainLooper());
	Runnable autosaveRunnable = new Runnable() {
			@Override
			public void run() {
				autosaveTick();
				autosaveHandler.postDelayed(this, 20_000);
			}
		};

	private void autosaveTick() {

		if (isEditMode) {
			return;
		}

		if (parties == null || transactionItemList == null) {
			return;
		}

		boolean hasParty = getSelectedPartyPosition() != -1;
		boolean hasItems = !transactionItemList.isEmpty();

		if (!hasParty && !hasItems) {
			return;
		}

		String type = transactionType == TYPE_PURCHASE ?
			DatabaseHelper.DRAFT_TYPE_PURCHASE : DatabaseHelper.DRAFT_TYPE_SALE;

		DraftAutosave.save(
			db, type, buildDraftData(),
			et_date.getText().toString(), et_time.getText().toString());
	}

	// Offers to resume a silent autosave left behind by a session that
	// never reached a real Save/"Save as Draft" - e.g. a crash, or the
	// app being killed while this screen was open. Only ever checked
	// for a brand-new entry (never editing an existing transaction),
	// and never when a specific draft was already explicitly requested
	// via the "draft_id" intent extra - that explicit choice wins.
	private void maybePromptResumeAutosave() {

		String type = transactionType == TYPE_PURCHASE ?
			DatabaseHelper.DRAFT_TYPE_PURCHASE : DatabaseHelper.DRAFT_TYPE_SALE;

		final DraftAutosave.Pending pending = DraftAutosave.getPending(db, type);

		if (pending == null) {
			return;
		}

		new AlertDialog.Builder(this)
			.setTitle("Resume unsaved entry?")
			.setMessage(
				"This app closed before you finished a " +
				(transactionType == TYPE_PURCHASE ? "Purchase" : "Sale") +
				" you were entering on " + pending.date + " at " + pending.time +
				". Resume it, or discard it and start fresh.")
			.setCancelable(false)
			.setPositiveButton("Resume", new android.content.DialogInterface.OnClickListener() {
					@Override
					public void onClick(android.content.DialogInterface dialog, int which) {
						applyDraftData(pending.data);
					}
				})
			.setNegativeButton("Discard", new android.content.DialogInterface.OnClickListener() {
					@Override
					public void onClick(android.content.DialogInterface dialog, int which) {

						String type = transactionType == TYPE_PURCHASE ?
							DatabaseHelper.DRAFT_TYPE_PURCHASE : DatabaseHelper.DRAFT_TYPE_SALE;

						DraftAutosave.clear(db, type);
					}
				})
			.show();
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
		Double[] exactPriceOverride,
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

		if (exactPriceOverride != null && exactPriceOverride[0] != null) {

			purchasePrice = exactPriceOverride[0];

		} else {

			try {

				purchasePrice = Double.parseDouble(
					etPurchasePrice.getText().toString()
				);

			} catch (Exception e) {
			}
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
		showEditTransactionItem(editPosition, null);
	}

	// presetItemId is only non-null when reopening this dialog after
	// creating a brand-new item via the "+ Add New Item" dropdown row
	// (see onActivityResult()) - it overrides oldItem's own item_id for
	// which row starts selected, and (since it's a different item to the
	// one this line used to be) its own price and a fresh, unselected set
	// of variety dropdowns are used instead of oldItem's.
	private void showEditTransactionItem(final int editPosition, final Integer presetItemId) {

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

		final Button btnQuantityMinus =
			view.findViewById(R.id.btn_quantity_minus);

		final Button btnQuantityPlus =
			view.findViewById(R.id.btn_quantity_plus);

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

			final Double[] exactPriceOverride =
				wireQuantityPriceTotalSync(etQuantity, etPurchasePrice, etTotal);

			wireQuantityStepper(etQuantity, btnQuantityMinus, btnQuantityPlus);

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

						if (exactPriceOverride[0] != null) {

							priceForEdit = exactPriceOverride[0];

						} else {

							try {

								priceForEdit = Double.parseDouble(
									etPurchasePrice.getText().toString()
								);

							} catch (Exception e) {
							}
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

		// Parallel to itemNames (same order/size) - see
		// TwoLineAutoCompleteAdapter's extraSearchText.
		final ArrayList<String> itemCodesForSearch =
			new ArrayList<String>();

		int selectedPosition = 0;

		int matchAgainstItemId = presetItemId != null ?
			presetItemId.intValue() :
			((Integer) oldItem.get("item_id")).intValue();

		for (int i = 0; i < items.size(); i++) {

			HashMap<String, Object> item = items.get(i);

			itemNames.add(
				(String) item.get("name")
			);

			itemCodesForSearch.add(
				(String) item.get("code")
			);

			if (((Integer) item.get("id")).intValue() == matchAgainstItemId) {

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
			itemSubtitlesEdit,
			"+ Add New Item",
			itemCodesForSearch
		);

		actvItem.setAdapter(adapter);
		actvItem.setThreshold(1);
		actvItem.setText(itemNames.get(selectedPosition), false);

		final PreSelectionTextWatcher editItemTextTracker = new PreSelectionTextWatcher();
		actvItem.addTextChangedListener(editItemTextTracker);

		Map<Integer, Integer> preselectedVarietyValues = null;

		// A presetItemId means this is a different item than the line
		// used to be (just created via "+ Add New Item"), so its old
		// combo selection doesn't apply.
		if (presetItemId == null && oldItem.get("combo_id") != null) {
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

		// Created here (rather than at the end, as it used to be) so
		// actvItem's own item-click listener below can dismiss it before
		// jumping to Additemactivity for the "+ Add New Item" row - see
		// that listener.
		final AlertDialog dialog =
			new AlertDialog.Builder(this)
			.setView(view)
			.create();

		actvItem.setOnItemClickListener(
			new AdapterView.OnItemClickListener() {

				@Override
				public void onItemClick(
					AdapterView<?> parent,
					View view,
					int position,
					long id) {

					// Always the dropdown's last row - jumps straight to
					// Additemactivity, then reopens THIS SAME line's Edit
					// dialog (not the Add dialog) with the new item
					// preselected - see pendingEditPositionForNewItem/
					// onActivityResult().
					if (adapter.isAddNewPosition(position)) {

						String typedName = editItemTextTracker.textBeforeChange.trim();

						actvItem.setText("", false);
						dialog.dismiss();

						pendingEditPositionForNewItem = editPosition;

						Intent intent = new Intent(
							Transactioneditactivity.this,
							Additemactivity.class
						);

						intent.putExtra("item_name", typedName);

						startActivityForResult(intent, REQUEST_ADD_NEW_ITEM);
						return;
					}

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

					showKeyboardOn(etQuantity);
				}
			});

		etQuantity.setText(
			oldItem.get("quantity").toString()
		);

		// A presetItemId means a different item than the line used to be -
		// its own price, not the old item's.
		etPurchasePrice.setText(
			presetItemId != null ?
			items.get(selectedPosition).get(priceField).toString() :
			oldItem.get(priceField).toString()
		);

		final Double[] exactPriceOverride =
			wireQuantityPriceTotalSync(etQuantity, etPurchasePrice, etTotal);

		wireQuantityStepper(etQuantity, btnQuantityMinus, btnQuantityPlus);

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

					if (exactPriceOverride[0] != null) {

						purchasePrice = exactPriceOverride[0];

					} else {

						try {

							purchasePrice = Double.parseDouble(
								etPurchasePrice.getText().toString()
							);

						} catch (Exception e) {
						}
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

		// A linked Expense never contributes to grand_total - its cash/
		// party-balance effect is already fully owned by the expense
		// record itself (see TABLE_PURCHASE_EXPENSE_LINKS), only its
		// landed-cost share changes as a result of linking it here.
		if (transactionType == TYPE_PURCHASE) {

			total += (legacyOtherChargesToParty ? legacyOtherCharges : 0);
		}

		// Sale only - see container_sale_discount's own comment. Clamped
		// to 0-100% so a stray typed value (negative, or over 100) can
		// never push the Grand Total the wrong way.
		double discountAmount = 0;

		if (transactionType == TYPE_SALE && container_sale_discount != null) {

			double discountPercent = 0;

			try {

				discountPercent = Double.parseDouble(
					et_sale_discount_percent.getText().toString().trim()
				);

			} catch (Exception e) {
			}

			if (discountPercent < 0) {
				discountPercent = 0;
			} else if (discountPercent > 100) {
				discountPercent = 100;
			}

			discountAmount = total * discountPercent / 100.0;

			tv_discount_amount.setText("- " + AmountFormat.format(discountAmount));
		}

		total -= discountAmount;

		tv_grand_total.setText(
			AmountFormat.format(total)
		);

		updateDefaultAmountPaid(total);
		updateDueDateVisibility(total);

		// Sale only - a live estimate as items are added, using each
		// item's purchase_price + extra_cost_per_unit as cost, same
		// basis Net Profit/Item Monthly Rank/Profit Split already use.
		if (container_sale_profit != null) {

			if (transactionType == TYPE_SALE && transactionItemList.size() > 0) {

				double profit = 0;

				for (HashMap<String, Object> map : transactionItemList) {

					Object itemIdObj = map.get("item_id");

					if (itemIdObj == null) {
						continue;
					}

					double qty = (Double) map.get("quantity");
					double lineTotal = (Double) map.get("total");
					double costBasis = db.getItemCostBasis((Integer) itemIdObj);

					profit += lineTotal - (qty * costBasis);
				}

				profit -= discountAmount;

				tv_sale_profit.setText(AmountFormat.format(profit));
				container_sale_profit.setVisibility(View.VISIBLE);

			} else {

				container_sale_profit.setVisibility(View.GONE);
			}
		}

		updateLoyaltyPreview(total);
	}

	// Sale only, and only once a real (non-"Cash Sale") party is
	// selected - a live preview of this party's current loyalty points
	// balance plus how many this sale itself would add, computed with
	// the exact same formula earnLoyaltyPointsForSale() uses on save
	// (see DatabaseHelper.getLoyaltyPointsPreview()) so the two numbers
	// never disagree. Not the only place a balance shows - the toast
	// after saving is a separate, one-time milestone celebration.
	private void updateLoyaltyPreview(double grandTotal) {

		if (container_loyalty_preview == null) {
			return;
		}

		if (transactionType != TYPE_SALE) {

			container_loyalty_preview.setVisibility(View.GONE);
			return;
		}

		int partyPosition = getSelectedPartyPosition();

		if (partyPosition == -1) {

			container_loyalty_preview.setVisibility(View.GONE);
			return;
		}

		String partyName = (String) parties.get(partyPosition).get("name");

		if (isCashPlaceholderParty(partyName)) {

			container_loyalty_preview.setVisibility(View.GONE);
			return;
		}

		int partyId = (Integer) parties.get(partyPosition).get("id");
		int currentBalance = db.getLoyaltyPointsBalance(partyId);
		int pointsToEarn = db.getLoyaltyPointsPreview(grandTotal);

		tv_loyalty_preview.setText(
			currentBalance + (pointsToEarn > 0 ? " (+" + pointsToEarn + ")" : "")
		);

		container_loyalty_preview.setVisibility(View.VISIBLE);
	}

	// =====================
	// A credit transaction (anything not fully paid, Sale or Purchase)
	// gets a Due Date field; a fully-paid one has nothing to be due, so
	// the field is hidden (not cleared - switching back to partial/
	// unpaid later brings back whatever was there). The field defaults
	// to the transaction date + 3 days only the moment it first becomes
	// relevant and is still empty - editing an existing credit Sale/
	// Purchase's already-saved due date, or one the user already
	// picked, is never overwritten.
	// =====================
	private void updateDueDateVisibility(double grandTotal) {

		if ((transactionType != TYPE_SALE && transactionType != TYPE_PURCHASE)
			|| container_due_date == null) {
			return;
		}

		double paid;

		try {

			paid = Double.parseDouble(et_amount_paid.getText().toString().trim());

		} catch (Exception e) {

			paid = 0;
		}

		boolean isCredit = paid < grandTotal - 0.01;

		container_due_date.setVisibility(isCredit ? View.VISIBLE : View.GONE);

		if (isCredit && et_due_date.getText().toString().trim().isEmpty()) {

			java.util.Calendar calendar = java.util.Calendar.getInstance();

			try {

				String[] parts = et_date.getText().toString().trim().split("-");

				calendar.set(
					Integer.parseInt(parts[0]),
					Integer.parseInt(parts[1]) - 1,
					Integer.parseInt(parts[2])
				);

			} catch (Exception e) {
			}

			calendar.add(java.util.Calendar.DAY_OF_YEAR, 3);

			et_due_date.setText(
				String.format(
					Locale.getDefault(),
					"%04d-%02d-%02d",
					calendar.get(java.util.Calendar.YEAR),
					calendar.get(java.util.Calendar.MONTH) + 1,
					calendar.get(java.util.Calendar.DAY_OF_MONTH)
				)
			);
		}
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

		double total = 0;

		for (HashMap<String, Object> map : transactionItemList) {
			total += (Double) map.get("total");
		}

		updateDueDateVisibility(total);
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
		double itemsSubtotal = 0;

		for (HashMap<String, Object> item : transactionItemList) {
			itemsSubtotal += (Double) item.get("total");
		}

		// pendingLinkedExpenses never touches grand_total, whether it's
		// applied here on save or was already applied on a previous save
		// - a linked expense's cash/party-balance effect is already fully
		// owned by the expense record itself.
		// legacyOtherCharges/legacyOtherChargesToParty carry forward a
		// pre-Purchase-Costs purchase's old Other Charges contribution
		// unchanged (see loadPurchase()) so editing such a purchase here
		// doesn't silently drop it from grand_total/party balance.
		double grandTotal = itemsSubtotal + (legacyOtherChargesToParty ? legacyOtherCharges : 0);

		if (amountPaid > grandTotal) {

			android.widget.Toast.makeText(
				Transactioneditactivity.this, // Use Purchaseseditactivity.this in Edit
				"Amount paid cannot be greater than Grand Total",
				android.widget.Toast.LENGTH_SHORT
			).show();

			return;
		}

		if (isCashPlaceholderParty((String) parties.get(partyPosition).get("name")) && amountPaid < grandTotal) {

			android.widget.Toast.makeText(
				this,
				"Cash Purchase must be paid in full - no partial or credit",
				android.widget.Toast.LENGTH_LONG
			).show();

			return;
		}

		if (!cashBaselineLoaded) {

			android.widget.Toast.makeText(
				this,
				"Still checking cash balance - try again in a moment",
				android.widget.Toast.LENGTH_SHORT
			).show();

			return;
		}

		if (amountPaid > 0 && cashBaseline - amountPaid < 0) {

			android.widget.Toast.makeText(
				this,
				"This would take cash balance below 0 - reduce Amount Paid " +
				"or add cash first",
				android.widget.Toast.LENGTH_LONG
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

		// Same "only a credit transaction gets a due date" rule as Sale -
		// see updateDueDateVisibility().
		String dueDate =
			amountPaid < grandTotal - 0.01 ? et_due_date.getText().toString().trim() : null;

			if (isEditMode) {

				purchaseId = transactionId;

			// Snapshot the pre-edit row/items so this update can be
			// undone from Recently Deleted (see
			// DatabaseHelper.snapshotPurchaseBeforeEdit()/undoPurchaseEdit()) -
			// must happen before updatePurchase()/deletePurchaseItems()
			// below change anything.
			db.snapshotPurchaseBeforeEdit(purchaseId);

			boolean success = db.updatePurchase(
			purchaseId,
		partyId,
		et_date.getText().toString(),
		et_time.getText().toString(),
			et_invoice_number.getText().toString(),
			grandTotal,
			amountPaid,
				et_notes.getText().toString(),
				legacyOtherCharges,
				legacyOtherChargesToParty,
				dueDate
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
			et_notes.getText().toString(),
			dueDate
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

			// Blend this line's share of every pending linked Expense that
			// goes 100% to THIS purchase (no "selections" - see
			// pendingLinkedExpenses' own field comment) into the item's
			// running extra_cost_per_unit BEFORE this purchase's stock is
			// added in (see applyExtraCostToItem()'s own ordering note) -
			// combined into one call per item rather than one call per
			// pending expense, since calling it more than once here would
			// blend this same incoming quantity into the weighted average
			// multiple times over. A pending entry WITH "selections" (an
			// edit-mode pick, possibly split across other purchases too)
			// blends itself instead inside applyExpensePurchaseLinks()
			// below, once purchase_items has this purchase's newly
			// (re)inserted rows to query.
			if (itemsSubtotal > 0) {

				double lineShare =
					((Double) item.get("total")).doubleValue() / itemsSubtotal;

				double combinedPendingCost = 0;

				for (HashMap<String, Object> pending : pendingLinkedExpenses) {

					if (pending.get("selections") != null) {
						continue;
					}

					combinedPendingCost += ((Double) pending.get("amount")) * lineShare;
				}

				if (combinedPendingCost != 0) {

					db.applyExtraCostToItem(
						((Integer) item.get("item_id")).intValue(),
						((Double) item.get("quantity")).doubleValue(),
						combinedPendingCost
					);
				}
			}

			db.insertPurchaseItem(

			purchaseId,

		((Integer) item.get("item_id")).intValue(),

		((Double) item.get("quantity")).doubleValue(),

		((Double) item.get("purchase_price")).doubleValue(),

			((Double) item.get("total")).doubleValue(),

			(Integer) item.get("combo_id")
			);
			}

		// Apply every pending linked Expense now that purchaseId (and this
		// purchase's own purchase_items rows, just inserted/reinserted
		// above) exist for real - for both a brand-new purchase and an
		// edit alike (see pendingLinkedExpenses' own field comment). An
		// entry with no "selections" goes 100% to this purchase and
		// already had its landed-cost blend done per-line above (see the
		// ordering note there), so this only needs to record the link
		// itself. An entry WITH "selections" (an edit-mode pick, possibly
		// split across other purchases too) is applied via
		// applyExpensePurchaseLinks(), which blends each selected
		// purchase's own share into ITS OWN items - including this one's,
		// now that they're freshly (re)inserted.
		for (HashMap<String, Object> pending : pendingLinkedExpenses) {

			int expenseId = (Integer) pending.get("expense_id");
			double pendingAmount = (Double) pending.get("amount");

			@SuppressWarnings("unchecked")
			ArrayList<HashMap<String, Object>> selections =
				(ArrayList<HashMap<String, Object>>) pending.get("selections");

			if (selections != null) {

				db.applyExpensePurchaseLinks(expenseId, pendingAmount, selections);

			} else {

				db.applySingleExpensePurchaseLink(expenseId, purchaseId, pendingAmount);
			}
		}

		pendingLinkedExpenses.clear();

		if (draftId != -1) {
			db.deleteDraft(draftId);
			draftId = -1;
		}

		// A real Purchase just got committed - the autosave safety net
		// (if any) is no longer needed.
		DraftAutosave.clear(db, DatabaseHelper.DRAFT_TYPE_PURCHASE);

		android.widget.Toast.makeText(
		this,
		isEditMode ? "Purchase updated" : "Purchase saved",
	android.widget.Toast.LENGTH_SHORT
	).show();

		if (andNew) {

			resetFormForNewTransaction();

		} else {

			finishOrGoToDashboard();
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

	// Same tappable-date-picker pattern as showDatePicker() above, for
	// a credit Sale or Purchase's due date (see updateDueDateVisibility()).
	private void showDueDatePicker() {

		java.util.Calendar calendar = java.util.Calendar.getInstance();

		try {

			String[] parts = et_due_date.getText().toString().split("-");

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

					et_due_date.setText(
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


	// The reserved cash-placeholder parties ("Cash Sale" for a Sale,
	// "Cash Purchase" for a Purchase) stand in for "no real party" - since
	// there's no real party to ever collect a balance from later, these
	// specifically must be paid in full on save, unlike a named party
	// which can carry a partial/credit balance.
	private boolean isCashPlaceholderParty(String partyName) {
		return "Cash Sale".equalsIgnoreCase(partyName)
			|| "Cash Purchase".equalsIgnoreCase(partyName);
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

		pendingLinkedExpenses.clear();
		legacyOtherCharges = 0;
		legacyOtherChargesToParty = false;

		if (transactionType == TYPE_PURCHASE) {
			refreshLinkedExpensesDisplay();
		}

		transactionItemList.clear();
		transactionItemAdapter.notifyDataSetChanged();

		setListViewHeightBasedOnChildren(lv_transaction_items);

		actv_party.setText(
			transactionType == TYPE_SALE ? "Cash Sale" : "Cash Purchase",
			false
		);

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

		if (transactionType == TYPE_SALE) {
			et_sale_discount_percent.setText("");
		}

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

			finishOrGoToDashboard();
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

		legacyOtherCharges = (Double) purchase.get("other_charges");
		legacyOtherChargesToParty = Boolean.TRUE.equals(purchase.get("other_charges_to_party"));

		refreshLinkedExpensesDisplay();

		setFullPaidCheckboxSilently(
			(Double) purchase.get("amount_paid"),
			(Double) purchase.get("grand_total")
		);

		// Read back before updateGrandTotal() runs below, so
		// updateDueDateVisibility() sees it already filled in and
		// doesn't overwrite it with a freshly-computed default - same
		// as loadSale() does.
		if (purchase.get("due_date") != null) {
			et_due_date.setText(purchase.get("due_date").toString());
		}

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

			finishOrGoToDashboard();
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

			finishOrGoToDashboard();
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

		// Read back before updateGrandTotal() runs below, so
		// updateDueDateVisibility() sees it already filled in and
		// doesn't overwrite it with a freshly-computed default.
		if (sale.get("due_date") != null) {
			et_due_date.setText(sale.get("due_date").toString());
		}

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

		// Back-computed from the saved absolute discount amount, since
		// that's all the sales table stores - read before
		// updateGrandTotal() runs below, same reasoning as due_date above.
		try {

			double savedSubtotal = Double.parseDouble(sale.get("subtotal").toString());
			double savedDiscount = Double.parseDouble(sale.get("discount").toString());

			if (savedSubtotal > 0 && savedDiscount > 0) {

				et_sale_discount_percent.setText(
					AmountFormat.formatPlain(savedDiscount / savedSubtotal * 100.0)
				);
			}

		} catch (Exception e) {
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

		if (transactionType == TYPE_SALE) {

			double discountPercent = 0;

			try {

				discountPercent = Double.parseDouble(
					et_sale_discount_percent.getText().toString().trim()
				);

			} catch (Exception e) {
			}

			if (discountPercent < 0) {
				discountPercent = 0;
			} else if (discountPercent > 100) {
				discountPercent = 100;
			}

			discount = subtotal * discountPercent / 100.0;
		}

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

		if (isCashPlaceholderParty((String) parties.get(partyPosition).get("name")) && paidAmount < grandTotal) {

			android.widget.Toast.makeText(
				this,
				"Cash Sale must be paid in full - no partial or credit",
				android.widget.Toast.LENGTH_LONG
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
			"due_date",
			balance > 0.01 ? et_due_date.getText().toString().trim() : null
		);

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

		if (draftId != -1) {
			db.deleteDraft(draftId);
			draftId = -1;
		}

		// A real Sale just got committed - the autosave safety net (if
		// any) is no longer needed.
		DraftAutosave.clear(db, DatabaseHelper.DRAFT_TYPE_SALE);

		// Loyalty points - only for a brand-new Sale (see this method's
		// own "only ever runs for a brand-new Sale" note below), never
		// on an edit. A milestone crossing gets a one-time celebratory
		// toast; most saves cross nothing and this is a no-op.
		int loyaltyPartyId = (Integer) parties.get(partyPosition).get("id");
		Integer milestoneCrossed = db.earnLoyaltyPointsForSale(loyaltyPartyId, grandTotal, saleId);

		if (milestoneCrossed != null) {

			String partyName = (String) parties.get(partyPosition).get("name");

			android.widget.Toast.makeText(
				this,
				partyName + " just reached " + milestoneCrossed + " loyalty points!",
				android.widget.Toast.LENGTH_LONG
			).show();
		}

		runDisplaySampleHookThenFinish(andNew);
	}

	// =====================
	// DISPLAY/SAMPLE SHOES - after a Sale saves, for every sold line
	// that has a combo (a Size), check whether that combo just ran out
	// (auto-remove any matching Display/Sample board entry silently -
	// there's no stock left for it to reference) or still has stock
	// left with a board entry present (ask which physical unit was
	// sold, since the app has no way to know on its own). Only ever
	// runs for a brand-new Sale (not an edit) - see saveSale()'s only
	// caller.
	// =====================
	private void runDisplaySampleHookThenFinish(final boolean andNew) {

		final ArrayList<HashMap<String, Object>> prompts = new ArrayList<HashMap<String, Object>>();

		for (HashMap<String, Object> item : transactionItemList) {

			Object comboObj = item.get("combo_id");

			if (comboObj == null) {
				continue;
			}

			int itemId = (Integer) item.get("item_id");
			int comboId = (Integer) comboObj;

			boolean hasDisplay = db.hasDisplayShoeForCombo(itemId, comboId);
			boolean hasSample = db.hasSampleShoeForCombo(itemId, comboId);

			if (!hasDisplay && !hasSample) {
				continue;
			}

			double remaining = db.getComboBalance(comboId);

			if (remaining <= 0) {

				if (hasDisplay) {
					db.removeOneDisplayShoeForCombo(itemId, comboId);
				}

				if (hasSample) {
					db.removeOneSampleShoeForCombo(itemId, comboId);
				}

				continue;
			}

			String itemName = String.valueOf(item.get("name"));
			String comboLabel = db.getComboLabel(comboId);

			if (hasDisplay) {

				HashMap<String, Object> prompt = new HashMap<String, Object>();
				prompt.put("board", "display");
				prompt.put("item_id", itemId);
				prompt.put("combo_id", comboId);
				prompt.put("item_name", itemName);
				prompt.put("combo_label", comboLabel);
				prompts.add(prompt);
			}

			if (hasSample) {

				HashMap<String, Object> prompt = new HashMap<String, Object>();
				prompt.put("board", "sample");
				prompt.put("item_id", itemId);
				prompt.put("combo_id", comboId);
				prompt.put("item_name", itemName);
				prompt.put("combo_label", comboLabel);
				prompts.add(prompt);
			}
		}

		showNextDisplaySamplePrompt(prompts, 0, andNew);
	}

	private void showNextDisplaySamplePrompt(
		final ArrayList<HashMap<String, Object>> prompts, final int index, final boolean andNew) {

		if (index >= prompts.size()) {
			finishSaleSave(andNew);
			return;
		}

		final HashMap<String, Object> prompt = prompts.get(index);
		final boolean isDisplay = "display".equals(prompt.get("board"));

		String boardLabel = isDisplay ? "Display (right shoe)" : "Sample (left shoe)";

		new AlertDialog.Builder(this)
			.setTitle("Was this the " + boardLabel + "?")
			.setMessage(
				prompt.get("item_name") + " - " + prompt.get("combo_label") +
				" - just sold one and stock remains. Was the unit sold the one kept as " +
				boardLabel + "?"
			)
			.setCancelable(false)
			.setPositiveButton("Yes", new android.content.DialogInterface.OnClickListener() {
					@Override
					public void onClick(android.content.DialogInterface dialog, int which) {

						int itemId = (Integer) prompt.get("item_id");
						int comboId = (Integer) prompt.get("combo_id");

						if (isDisplay) {
							db.removeOneDisplayShoeForCombo(itemId, comboId);
						} else {
							db.removeOneSampleShoeForCombo(itemId, comboId);
						}

						showNextDisplaySamplePrompt(prompts, index + 1, andNew);
					}
				})
			.setNegativeButton("No", new android.content.DialogInterface.OnClickListener() {
					@Override
					public void onClick(android.content.DialogInterface dialog, int which) {
						showNextDisplaySamplePrompt(prompts, index + 1, andNew);
					}
				})
			.show();
	}

	private void finishSaleSave(boolean andNew) {

		android.widget.Toast.makeText(
			this,
			"Sale saved",
			android.widget.Toast.LENGTH_SHORT
		).show();

		if (andNew) {

			resetFormForNewTransaction();

		} else {

			finishOrGoToDashboard();
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

		double discount = 0;

		try {

			double discountPercent = Double.parseDouble(
				et_sale_discount_percent.getText().toString().trim()
			);

			if (discountPercent < 0) {
				discountPercent = 0;
			} else if (discountPercent > 100) {
				discountPercent = 100;
			}

			discount = subtotal * discountPercent / 100.0;

		} catch (Exception e) {
		}

		double grandTotal = subtotal - discount;

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

		if (isCashPlaceholderParty((String) parties.get(partyPosition).get("name")) && paidAmount < grandTotal) {

			android.widget.Toast.makeText(
				this,
				"Cash Sale must be paid in full - no partial or credit",
				android.widget.Toast.LENGTH_LONG
			).show();

			return;
		}

		saleMap.put("invoice_no", et_invoice_number.getText().toString());
		saleMap.put("date", et_date.getText().toString());
		saleMap.put("time", et_time.getText().toString());
		saleMap.put("subtotal", subtotal);
		saleMap.put("discount", discount);
		saleMap.put("other_charges", 0);
		saleMap.put("grand_total", grandTotal);
		saleMap.put("paid_amount", paidAmount);
		saleMap.put("balance", grandTotal - paidAmount);
		saleMap.put("notes", et_notes.getText().toString());

		saleMap.put(
			"due_date",
			(grandTotal - paidAmount) > 0.01 ? et_due_date.getText().toString().trim() : null
		);

		// Snapshot the pre-edit row/items so this update can be undone
		// from Recently Deleted (see
		// DatabaseHelper.snapshotSaleBeforeEdit()/undoSaleEdit()) - must
		// happen before updateSale()/deleteSaleItems() below change
		// anything.
		db.snapshotSaleBeforeEdit(transactionId);

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

			finishOrGoToDashboard();
		}
	}
	
	
	
	
	
}

