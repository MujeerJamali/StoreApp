package com.mujeer.businesserp;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Bulk "generate entries" screen: a persistent toggle at the top switches
 * between Purchase/Sale/Payment In/Payment Out/Expense from anywhere in the
 * flow (instead of a separate picker screen you'd have to back out to in
 * order to change your mind), then you configure one or more lines (an
 * item+price for Purchase/Sale, a party+amount for Payment, a description
 * +amount+party for Expense), and for each line tap dates on a
 * calendar - each tap registers one more unit on that date, a long-press
 * removes one. A review step lists every configured line before a single
 * "Generate" commits everything to the database in one transaction.
 *
 * Same-date lines that share an effective party are merged into one
 * purchase/sale/payment record with the date's tapped quantity, matching
 * how a real invoice or payment would be recorded; Expenses have no such
 * merge (the expenses table has no line-item concept), so every line's
 * per-date tap becomes its own expense row.
 */
public class GenerateEntriesActivity extends Activity {

	private static final int TYPE_PURCHASE = 0;
	private static final int TYPE_SALE = 1;
	private static final int TYPE_PAYMENT_IN = 2;
	private static final int TYPE_PAYMENT_OUT = 3;
	private static final int TYPE_EXPENSE = 4;

	private static final int[] TOGGLE_TYPES =
		{TYPE_PURCHASE, TYPE_SALE, TYPE_PAYMENT_IN, TYPE_PAYMENT_OUT, TYPE_EXPENSE};

	private static final int STEP_SESSION_SETUP = 0;
	private static final int STEP_ITEMS_MULTISELECT = 1;
	private static final int STEP_LINES_LIST = 2;
	private static final int STEP_LINE_CONFIG = 3;
	private static final int STEP_REVIEW = 4;

	private DatabaseHelper db;
	private FrameLayout frameStep;
	private Button[] toggleButtons;
	private int currentStep = -1;

	private int entryType;
	private boolean samePartyForAllItems = true;
	private Integer sessionPartyId;
	private String sessionPartyName;

	private ArrayList<HashMap<String, Object>> allParties;
	private ArrayList<HashMap<String, Object>> allItems;

	private List<String> partyNames;
	private Map<String, Integer> partyIdByName;
	private Map<String, String> partySubtitleByName;
	private Map<Integer, HashMap<String, Object>> itemById;

	private final List<GenerateEntryLine> lines = new ArrayList<GenerateEntryLine>();

	private int configLineIndex;
	private int configReturnStep;

	@Override
	protected void onCreate(Bundle savedInstanceState) {

		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_generate_entries);

		db = new DatabaseHelper(this);
		frameStep = findViewById(R.id.frame_step);

		loadPartiesAndItems();
		wireTypeToggle();

		startType(TYPE_PURCHASE);
	}

	// =====================
	// PERSISTENT TYPE TOGGLE (outside frame_step, wired once)
	// =====================

	private void wireTypeToggle() {

		toggleButtons = new Button[5];
		toggleButtons[0] = findViewById(R.id.btn_toggle_purchase);
		toggleButtons[1] = findViewById(R.id.btn_toggle_sale);
		toggleButtons[2] = findViewById(R.id.btn_toggle_payment_in);
		toggleButtons[3] = findViewById(R.id.btn_toggle_payment_out);
		toggleButtons[4] = findViewById(R.id.btn_toggle_expense);

		for (int i = 0; i < toggleButtons.length; i++) {

			final int type = TOGGLE_TYPES[i];

			toggleButtons[i].setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					onTypeToggleClicked(type);
				}
			});
		}
	}

	private void onTypeToggleClicked(final int type) {

		if (type == entryType) {
			return;
		}

		if (lines.isEmpty()) {
			startType(type);
			return;
		}

		new AlertDialog.Builder(this)
			.setTitle("Switch entry type?")
			.setMessage(
				"Switching to " + typeLabel(type) + " clears what you've started for " +
				typeLabel(entryType) + ".")
			.setPositiveButton("Switch", new DialogInterface.OnClickListener() {
				@Override
				public void onClick(DialogInterface dialog, int which) {
					startType(type);
				}
			})
			.setNegativeButton("Cancel", null)
			.show();
	}

	private void updateToggleVisuals() {

		for (int i = 0; i < toggleButtons.length; i++) {
			applyToggleState(toggleButtons[i], TOGGLE_TYPES[i] == entryType);
		}
	}

	private void loadPartiesAndItems() {

		db.getOrCreatePartyId("Cash Expenses");

		allParties = db.getParties();
		allItems = db.getItems();

		// Recently sold items pinned to top of the item picker below -
		// see ItemPickerUtils (same behavior as Transactioneditactivity's
		// Add/Edit Item dialog).
		ItemPickerUtils.pinRecentlySoldItemsFirst(db, allItems);

		partyNames = new ArrayList<String>();
		partyIdByName = new HashMap<String, Integer>();
		partySubtitleByName = new HashMap<String, String>();

		for (HashMap<String, Object> p : allParties) {

			String name = (String) p.get("name");
			int id = (Integer) p.get("id");

			partyNames.add(name);
			partyIdByName.put(name, id);
			partySubtitleByName.put(name, formatPartyBalanceSubtitle(p.get("balance")));
		}

		itemById = new HashMap<Integer, HashMap<String, Object>>();

		for (HashMap<String, Object> it : allItems) {
			itemById.put((Integer) it.get("id"), it);
		}
	}

	// =====================
	// STEP NAVIGATION
	// =====================

	@Override
	public void onBackPressed() {

		switch (currentStep) {

			case STEP_SESSION_SETUP:
				super.onBackPressed();
				break;

			case STEP_ITEMS_MULTISELECT:
				goToStep(STEP_SESSION_SETUP);
				break;

			case STEP_LINES_LIST:
				super.onBackPressed();
				break;

			case STEP_LINE_CONFIG:
				goToStep(configReturnStep);
				break;

			case STEP_REVIEW:
				if (entryType == TYPE_PURCHASE || entryType == TYPE_SALE) {
					goToStep(STEP_ITEMS_MULTISELECT);
				} else {
					goToStep(STEP_LINES_LIST);
				}
				break;

			default:
				super.onBackPressed();
		}
	}

	private void goToStep(int step) {

		currentStep = step;
		frameStep.removeAllViews();

		int layoutRes;

		switch (step) {
			case STEP_SESSION_SETUP: layoutRes = R.layout.step_session_setup; break;
			case STEP_ITEMS_MULTISELECT: layoutRes = R.layout.step_items_multiselect; break;
			case STEP_LINES_LIST: layoutRes = R.layout.step_lines_list; break;
			case STEP_LINE_CONFIG: layoutRes = R.layout.step_line_config; break;
			case STEP_REVIEW: layoutRes = R.layout.step_review; break;
			default: throw new IllegalStateException("Unknown step " + step);
		}

		View view = getLayoutInflater().inflate(layoutRes, frameStep, false);
		frameStep.addView(view);

		switch (step) {
			case STEP_SESSION_SETUP: wireSessionSetup(view); break;
			case STEP_ITEMS_MULTISELECT: wireItemsMultiselect(view); break;
			case STEP_LINES_LIST: wireLinesList(view); break;
			case STEP_LINE_CONFIG: wireLineConfig(view); break;
			case STEP_REVIEW: wireReview(view); break;
		}
	}

	// =====================
	// Resets session state for the newly-selected type and jumps to its
	// first step - Purchase/Sale need the party-mode setup step first,
	// Payment In/Out/Expense go straight to their lines list.
	// =====================
	private void startType(int type) {

		entryType = type;
		lines.clear();
		samePartyForAllItems = true;
		sessionPartyId = null;
		sessionPartyName = null;

		updateToggleVisuals();

		if (type == TYPE_PURCHASE || type == TYPE_SALE) {
			goToStep(STEP_SESSION_SETUP);
		} else {
			goToStep(STEP_LINES_LIST);
		}
	}

	// =====================
	// STEP: SESSION SETUP (Purchase/Sale party mode only)
	// =====================

	private void wireSessionSetup(final View root) {

		TextView title = root.findViewById(R.id.tv_setup_title);
		TextView sessionPartyLabel = root.findViewById(R.id.tv_session_party_label);
		final LinearLayout fieldSessionParty = root.findViewById(R.id.field_session_party);
		final AutoCompleteTextView actvSessionParty = root.findViewById(R.id.actv_session_party);

		final Button btnSame = root.findViewById(R.id.btn_party_mode_same);
		final Button btnDifferent = root.findViewById(R.id.btn_party_mode_different);
		Button btnContinue = root.findViewById(R.id.btn_setup_continue);

		setupPartyAutoComplete(actvSessionParty);

		title.setText(entryType == TYPE_PURCHASE ? "Purchase Setup" : "Sale Setup");
		sessionPartyLabel.setText(entryType == TYPE_PURCHASE ? "Supplier" : "Customer");

		applyToggleState(btnSame, samePartyForAllItems);
		applyToggleState(btnDifferent, !samePartyForAllItems);
		fieldSessionParty.setVisibility(samePartyForAllItems ? View.VISIBLE : View.GONE);

		if (sessionPartyName != null) {
			actvSessionParty.setText(sessionPartyName, false);
		}

		btnSame.setOnClickListener(new View.OnClickListener() {
			@Override public void onClick(View v) {
				samePartyForAllItems = true;
				applyToggleState(btnSame, true);
				applyToggleState(btnDifferent, false);
				fieldSessionParty.setVisibility(View.VISIBLE);
			}
		});

		btnDifferent.setOnClickListener(new View.OnClickListener() {
			@Override public void onClick(View v) {
				samePartyForAllItems = false;
				applyToggleState(btnSame, false);
				applyToggleState(btnDifferent, true);
				fieldSessionParty.setVisibility(View.GONE);
			}
		});

		btnContinue.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {

				if (samePartyForAllItems) {

					String typed = actvSessionParty.getText().toString().trim();
					Integer partyId = partyIdByName.get(typed);

					if (partyId == null) {
						Toast.makeText(GenerateEntriesActivity.this, "Select a valid party", Toast.LENGTH_SHORT).show();
						return;
					}

					sessionPartyId = partyId;
					sessionPartyName = typed;
				}

				goToStep(STEP_ITEMS_MULTISELECT);
			}
		});
	}

	private void applyToggleState(Button b, boolean selected) {
		b.setBackgroundResource(selected ? R.drawable.bg_button_primary : R.drawable.bg_button_outline);
		b.setTextColor(getResources().getColor(selected ? R.color.text_on_primary : R.color.primary));
	}

	// =====================
	// STEP 2a: ITEM MULTI-SELECT (Purchase/Sale)
	// =====================

	private void wireItemsMultiselect(View root) {

		TextView title = root.findViewById(R.id.tv_items_title);
		ListView lv = root.findViewById(R.id.lv_items);
		EditText search = root.findViewById(R.id.et_items_search);
		final TextView tvCount = root.findViewById(R.id.tv_items_selected_count);
		Button btnContinue = root.findViewById(R.id.btn_items_continue);

		title.setText(entryType == TYPE_PURCHASE ? "Select Items to Purchase" : "Select Items to Sell");

		final Set<Integer> selectedIds = new HashSet<Integer>();

		for (GenerateEntryLine line : lines) {
			selectedIds.add(line.refId);
		}

		final SelectableItemAdapter adapter = new SelectableItemAdapter(this, allItems, selectedIds);
		lv.setAdapter(adapter);

		tvCount.setText(selectedIds.size() + " selected");

		lv.setOnItemClickListener(new AdapterView.OnItemClickListener() {
			@Override
			@SuppressWarnings("unchecked")
			public void onItemClick(AdapterView<?> parent, View view, int position, long id) {

				HashMap<String, Object> item = (HashMap<String, Object>) adapter.getItem(position);
				int itemId = (Integer) item.get("id");

				if (selectedIds.contains(itemId)) {
					selectedIds.remove(itemId);
				} else {
					selectedIds.add(itemId);
				}

				adapter.notifyDataSetChanged();
				tvCount.setText(selectedIds.size() + " selected");
			}
		});

		search.addTextChangedListener(new TextWatcher() {
			@Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
			@Override public void onTextChanged(CharSequence s, int start, int before, int count) {
				adapter.getFilter().filter(s);
			}
			@Override public void afterTextChanged(Editable s) {}
		});

		btnContinue.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {

				if (selectedIds.isEmpty()) {
					Toast.makeText(GenerateEntriesActivity.this, "Select at least one item", Toast.LENGTH_SHORT).show();
					return;
				}

				commitSelectedItems(selectedIds);
			}
		});
	}

	private void commitSelectedItems(Set<Integer> selectedIds) {

		// Keep already-configured lines for items still selected (so
		// re-entering this step and tweaking the selection doesn't wipe
		// out dates/prices already set), drop lines for deselected items,
		// and append fresh lines for newly selected ones.
		List<GenerateEntryLine> kept = new ArrayList<GenerateEntryLine>();

		for (GenerateEntryLine line : lines) {
			if (selectedIds.contains(line.refId)) {
				kept.add(line);
			}
		}

		Set<Integer> alreadyKept = new HashSet<Integer>();

		for (GenerateEntryLine line : kept) {
			alreadyKept.add(line.refId);
		}

		for (HashMap<String, Object> item : allItems) {

			int id = (Integer) item.get("id");

			if (!selectedIds.contains(id) || alreadyKept.contains(id)) {
				continue;
			}

			GenerateEntryLine line = new GenerateEntryLine();
			line.refId = id;
			line.label = item.get("code") + " - " + item.get("name");

			Object priceObj = entryType == TYPE_PURCHASE ? item.get("purchase_price") : item.get("sale_price");
			line.unitValue = priceObj == null ? 0 : (Double) priceObj;

			kept.add(line);
		}

		lines.clear();
		lines.addAll(kept);

		configLineIndex = 0;
		configReturnStep = STEP_REVIEW;
		goToStep(STEP_LINE_CONFIG);
	}

	// =====================
	// STEP 2b: LINES LIST (Payment/Expense)
	// =====================

	private void wireLinesList(final View root) {

		TextView title = root.findViewById(R.id.tv_lines_title);
		final TextView tvEmpty = root.findViewById(R.id.tv_lines_empty);
		final ListView lv = root.findViewById(R.id.lv_lines);
		Button btnAdd = root.findViewById(R.id.btn_add_line);
		Button btnContinue = root.findViewById(R.id.btn_lines_continue);

		if (entryType == TYPE_PAYMENT_IN) {
			title.setText("Payment In - Lines");
		} else if (entryType == TYPE_PAYMENT_OUT) {
			title.setText("Payment Out - Lines");
		} else {
			title.setText("Expense Lines");
		}

		GenerateLineListAdapter adapter = new GenerateLineListAdapter(this, lines, true);
		lv.setAdapter(adapter);

		tvEmpty.setVisibility(lines.isEmpty() ? View.VISIBLE : View.GONE);
		lv.setVisibility(lines.isEmpty() ? View.GONE : View.VISIBLE);

		lv.setOnItemClickListener(new AdapterView.OnItemClickListener() {
			@Override
			public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
				editExistingLine(position, STEP_LINES_LIST);
			}
		});

		lv.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
			@Override
			public boolean onItemLongClick(AdapterView<?> parent, View view, final int position, long id) {

				new AlertDialog.Builder(GenerateEntriesActivity.this)
					.setTitle("Remove line?")
					.setMessage("Remove this line and its tapped dates?")
					.setPositiveButton("Remove", new DialogInterface.OnClickListener() {
						@Override
						public void onClick(DialogInterface dialog, int which) {
							lines.remove(position);
							goToStep(STEP_LINES_LIST);
						}
					})
					.setNegativeButton("Cancel", null)
					.show();

				return true;
			}
		});

		btnAdd.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				GenerateEntryLine line = new GenerateEntryLine();
				lines.add(line);
				configLineIndex = lines.size() - 1;
				configReturnStep = STEP_LINES_LIST;
				goToStep(STEP_LINE_CONFIG);
			}
		});

		btnContinue.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {

				if (lines.isEmpty()) {
					Toast.makeText(GenerateEntriesActivity.this, "Add at least one line", Toast.LENGTH_SHORT).show();
					return;
				}

				goToStep(STEP_REVIEW);
			}
		});
	}

	private void editExistingLine(int index, int returnStep) {
		configLineIndex = index;
		configReturnStep = returnStep;
		goToStep(STEP_LINE_CONFIG);
	}

	// =====================
	// STEP 3: LINE CONFIG (shared by all 4 types)
	// =====================

	private void wireLineConfig(final View root) {

		final GenerateEntryLine line = lines.get(configLineIndex);

		TextView title = root.findViewById(R.id.tv_line_config_title);
		LinearLayout groupItemHeader = root.findViewById(R.id.group_line_item_header);
		TextView tvItemName = root.findViewById(R.id.tv_line_item_name);
		TextView tvItemCode = root.findViewById(R.id.tv_line_item_code);
		LinearLayout groupParty = root.findViewById(R.id.group_line_party);
		TextView tvPartyLabel = root.findViewById(R.id.tv_line_party_label);
		final AutoCompleteTextView actvParty = root.findViewById(R.id.actv_line_party);
		LinearLayout groupDescription = root.findViewById(R.id.group_line_description);
		final EditText etDescription = root.findViewById(R.id.et_line_description);
		TextView tvUnitLabel = root.findViewById(R.id.tv_line_unit_value_label);
		final EditText etUnitValue = root.findViewById(R.id.et_line_unit_value);
		final CalendarTapView calendar = root.findViewById(R.id.calendar_line);
		final TextView tvSummary = root.findViewById(R.id.tv_line_summary);
		Button btnSave = root.findViewById(R.id.btn_line_save);

		boolean isItemBased = entryType == TYPE_PURCHASE || entryType == TYPE_SALE;
		boolean isPayment = entryType == TYPE_PAYMENT_IN || entryType == TYPE_PAYMENT_OUT;
		final boolean needsPartyField =
			isPayment || entryType == TYPE_EXPENSE || (isItemBased && !samePartyForAllItems);

		if (isItemBased) {

			title.setText("Configure Item");
			groupItemHeader.setVisibility(View.VISIBLE);
			tvItemName.setText(line.label);

			HashMap<String, Object> item = itemById.get(line.refId);
			double stock = item != null && item.get("balance") != null ? (Double) item.get("balance") : 0;
			tvItemCode.setText("Current stock: " + formatQty(stock));

			tvUnitLabel.setText(entryType == TYPE_PURCHASE ? "Purchase Price" : "Sale Price");

		} else if (isPayment) {

			title.setText("Configure Party");
			tvUnitLabel.setText("Amount");

		} else {

			title.setText("Configure Expense");
			groupDescription.setVisibility(View.VISIBLE);
			tvUnitLabel.setText("Amount");

			if (line.label != null) {
				etDescription.setText(line.label);
			}
		}

		if (needsPartyField) {

			groupParty.setVisibility(View.VISIBLE);

			if (entryType == TYPE_PAYMENT_IN) {
				tvPartyLabel.setText("Received From");
			} else if (entryType == TYPE_PAYMENT_OUT) {
				tvPartyLabel.setText("Paid To");
			} else if (entryType == TYPE_EXPENSE) {
				tvPartyLabel.setText("Party");
			} else {
				tvPartyLabel.setText(entryType == TYPE_PURCHASE ? "Supplier" : "Customer");
			}

			setupPartyAutoComplete(actvParty);

			// Payment stores its party name in label (label doubles as
			// the party's display name for that line, since a payment
			// has no separate description); everything else - including
			// Expense's party - uses linePartyName instead.
			String prefillPartyName = isPayment ? line.label : line.linePartyName;

			if (prefillPartyName == null && entryType == TYPE_EXPENSE) {
				prefillPartyName = "Cash Expenses";
			}

			if (prefillPartyName != null) {
				actvParty.setText(prefillPartyName, false);
			}
		}

		// Purchase/Sale only: one dropdown per variety group the item has,
		// defaulting to (or restoring) the previously chosen value. A
		// group-less item leaves the container empty and hidden, and the
		// line's comboId stays null (no variety tracking for that item).
		LinearLayout containerVarieties = root.findViewById(R.id.container_line_varieties);
		containerVarieties.removeAllViews();

		final Map<Integer, Spinner> varietySpinners = new LinkedHashMap<Integer, Spinner>();
		final Map<Integer, ArrayList<HashMap<String, Object>>> varietyValuesByGroup =
			new LinkedHashMap<Integer, ArrayList<HashMap<String, Object>>>();

		if (isItemBased) {

			ArrayList<HashMap<String, Object>> groups = db.getVarietyGroups(line.refId);

			if (!groups.isEmpty()) {

				containerVarieties.setVisibility(View.VISIBLE);

				for (HashMap<String, Object> group : groups) {

					int groupId = (Integer) group.get("id");
					String groupName = (String) group.get("name");

					Integer previouslySelectedValueId = line.varietySelections.get(groupId);

					ArrayList<HashMap<String, Object>> values;

					if (entryType == TYPE_SALE) {

						// A size that's out of stock can't be sold, so
						// leave it out of the dropdown - except a value
						// this line already had selected, which stays
						// visible even at 0 stock.
						ArrayList<HashMap<String, Object>> valuesWithStock =
							db.getVarietyValuesWithStock(groupId);

						values = new ArrayList<HashMap<String, Object>>();

						for (HashMap<String, Object> value : valuesWithStock) {

							double stock = (Double) value.get("stock");

							boolean isPreselected =
								previouslySelectedValueId != null &&
								previouslySelectedValueId.equals(value.get("id"));

							if (stock > 0.0001 || isPreselected) {
								values.add(value);
							}
						}

						if (values.isEmpty()) {
							values = valuesWithStock;
						}

					} else {

						values = db.getVarietyValues(groupId);
					}

					varietyValuesByGroup.put(groupId, values);

					TextView groupLabel = new TextView(this);
					groupLabel.setText(groupName);
					groupLabel.setTextColor(getResources().getColor(R.color.text_secondary));
					groupLabel.setTextSize(13);
					groupLabel.setPadding(0, 12, 0, 4);
					containerVarieties.addView(groupLabel);

					ArrayList<String> valueLabels = new ArrayList<String>();
					int selectedIndex = 0;

					for (int i = 0; i < values.size(); i++) {

						valueLabels.add((String) values.get(i).get("label"));

						if (previouslySelectedValueId != null &&
							previouslySelectedValueId.equals(values.get(i).get("id"))) {
							selectedIndex = i;
						}
					}

					Spinner spinner = new Spinner(this);

					ArrayAdapter<String> adapter = new ArrayAdapter<String>(
						this, android.R.layout.simple_spinner_item, valueLabels);
					adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
					spinner.setAdapter(adapter);
					spinner.setSelection(selectedIndex);

					containerVarieties.addView(spinner);
					varietySpinners.put(groupId, spinner);
				}

			} else {

				containerVarieties.setVisibility(View.GONE);
			}
		}

		if (line.unitValue > 0) {
			etUnitValue.setText(formatQty(line.unitValue));
		}

		calendar.setDateCounts(line.dateCounts);
		updateLineSummary(tvSummary, line, etUnitValue);

		calendar.setOnDateCountChangeListener(new CalendarTapView.OnDateCountChangeListener() {
			@Override
			public void onDateCountChanged(String isoDate, int newCount) {

				if (newCount <= 0) {
					line.dateCounts.remove(isoDate);
				} else {
					line.dateCounts.put(isoDate, newCount);
				}

				updateLineSummary(tvSummary, line, etUnitValue);
			}
		});

		etUnitValue.addTextChangedListener(new TextWatcher() {
			@Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
			@Override public void onTextChanged(CharSequence s, int start, int before, int count) {
				updateLineSummary(tvSummary, line, etUnitValue);
			}
			@Override public void afterTextChanged(Editable s) {}
		});

		boolean midBatch = isItemBased && configReturnStep == STEP_REVIEW && configLineIndex + 1 < lines.size();

		btnSave.setText(midBatch ? "Save & Next Item" : "Save Line");

		btnSave.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				onLineSaveClicked(
					line, needsPartyField, actvParty, etDescription, etUnitValue,
					varietySpinners, varietyValuesByGroup);
			}
		});
	}

	private void updateLineSummary(TextView tvSummary, GenerateEntryLine line, EditText etUnitValue) {

		double unitValue = parseDoubleOrZero(etUnitValue.getText().toString());

		int totalCount = 0;
		for (int c : line.dateCounts.values()) {
			totalCount += c;
		}

		double total = totalCount * unitValue;

		tvSummary.setText(
			line.dateCounts.size() + " date(s), " + totalCount + " total · Total: " +
			AmountFormat.format(total));
	}

	private void onLineSaveClicked(
		GenerateEntryLine line,
		boolean needsPartyField,
		AutoCompleteTextView actvParty,
		EditText etDescription,
		EditText etUnitValue,
		Map<Integer, Spinner> varietySpinners,
		Map<Integer, ArrayList<HashMap<String, Object>>> varietyValuesByGroup) {

		double unitValue = parseDoubleOrZero(etUnitValue.getText().toString());

		if (unitValue <= 0) {
			Toast.makeText(this, "Enter a price/amount greater than 0", Toast.LENGTH_SHORT).show();
			return;
		}

		if (line.dateCounts.isEmpty()) {
			Toast.makeText(this, "Tap at least one date on the calendar", Toast.LENGTH_SHORT).show();
			return;
		}

		if (needsPartyField) {

			String typed = actvParty.getText().toString().trim();
			boolean isPayment = entryType == TYPE_PAYMENT_IN || entryType == TYPE_PAYMENT_OUT;

			Integer partyId = partyIdByName.get(typed);

			if (partyId == null) {

				Toast.makeText(this, "Select a valid party", Toast.LENGTH_SHORT).show();
				return;
			}

			if (isPayment) {
				line.refId = partyId;
				line.label = typed;
			} else {
				line.linePartyId = partyId;
				line.linePartyName = typed;
			}
		}

		if (entryType == TYPE_EXPENSE) {

			String desc = etDescription.getText().toString().trim();

			if (desc.length() == 0) {
				Toast.makeText(this, "Enter a description", Toast.LENGTH_SHORT).show();
				return;
			}

			line.label = desc;
		}

		line.unitValue = unitValue;

		if (!varietySpinners.isEmpty()) {

			Map<Integer, Integer> selections = new LinkedHashMap<Integer, Integer>();

			for (Map.Entry<Integer, Spinner> entry : varietySpinners.entrySet()) {

				int groupId = entry.getKey();
				int position = entry.getValue().getSelectedItemPosition();

				HashMap<String, Object> selectedValue =
					varietyValuesByGroup.get(groupId).get(position);

				selections.put(groupId, (Integer) selectedValue.get("id"));
			}

			line.varietySelections = selections;
			line.comboId = db.resolveComboId(selections);
		}

		boolean isItemBased = entryType == TYPE_PURCHASE || entryType == TYPE_SALE;

		if (isItemBased && configReturnStep == STEP_REVIEW && configLineIndex + 1 < lines.size()) {
			configLineIndex = configLineIndex + 1;
			goToStep(STEP_LINE_CONFIG);
			return;
		}

		goToStep(configReturnStep);
	}

	// =====================
	// STEP 4: REVIEW
	// =====================

	private void wireReview(View root) {

		TextView title = root.findViewById(R.id.tv_review_title);
		ListView lv = root.findViewById(R.id.lv_review);
		TextView tvGrandTotal = root.findViewById(R.id.tv_review_grand_total);
		Button btnGenerate = root.findViewById(R.id.btn_generate_commit);

		title.setText(typeLabel(entryType) + " Review");

		boolean showUnitValue = entryType == TYPE_PURCHASE || entryType == TYPE_SALE;

		GenerateLineListAdapter adapter = new GenerateLineListAdapter(this, lines, showUnitValue);
		lv.setAdapter(adapter);

		double grandTotal = 0;

		for (GenerateEntryLine line : lines) {
			grandTotal += line.totalValue();
		}

		tvGrandTotal.setText("Grand Total: " + AmountFormat.format(grandTotal));

		lv.setOnItemClickListener(new AdapterView.OnItemClickListener() {
			@Override
			public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
				editExistingLine(position, STEP_REVIEW);
			}
		});

		btnGenerate.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				onGenerateClicked();
			}
		});
	}

	// =====================
	// GENERATE (final commit)
	// =====================

	private void onGenerateClicked() {

		boolean hasAny = false;

		for (GenerateEntryLine line : lines) {
			if (line.totalCount() > 0) {
				hasAny = true;
				break;
			}
		}

		if (!hasAny) {
			Toast.makeText(this, "Tap at least one date before generating", Toast.LENGTH_SHORT).show();
			return;
		}

		if (entryType == TYPE_SALE) {

			String stockError = checkStockForSaleLines();

			if (stockError != null) {
				Toast.makeText(this, stockError, Toast.LENGTH_LONG).show();
				return;
			}
		}

		db.beginTransaction();

		boolean success = false;
		int recordCount = 0;

		try {

			SQLiteDatabase conn = db.getMigrationDatabase();
			String time = new SimpleDateFormat("HH:mm", Locale.US).format(new Date());
			String notes = "Generated via Generate Entries";

			if (entryType == TYPE_PURCHASE || entryType == TYPE_SALE) {
				recordCount = generatePurchasesOrSales(conn, time, notes);
			} else if (entryType == TYPE_PAYMENT_IN || entryType == TYPE_PAYMENT_OUT) {
				recordCount = generatePayments(conn, time, notes);
			} else {
				recordCount = generateExpenses(conn, time, notes);
			}

			success = true;

		} catch (Exception e) {

			success = false;

		} finally {

			db.endTransaction(success);
		}

		if (success) {
			showGenerateSuccessDialog(recordCount);
		} else {
			Toast.makeText(this, "Something went wrong - nothing was saved", Toast.LENGTH_LONG).show();
		}
	}

	// =====================
	// STOCK NEVER GOES NEGATIVE (sales only) - checked once, up front,
	// against every line's full quantity across all its tapped dates,
	// before any date is actually written. Each selected item has
	// exactly one line here (see commitSelectedItems()), so there's no
	// need to sum across lines for the same item/combo the way
	// Transactioneditactivity's checkSaleStockAvailability() does.
	// =====================
	private String checkStockForSaleLines() {

		for (GenerateEntryLine line : lines) {

			int qty = line.totalCount();

			if (qty <= 0 || line.refId == null) {
				continue;
			}

			double available = db.getAvailableStock(line.refId, line.comboId);

			if (qty > available) {

				return "Not enough stock for " + line.label + " - only " +
					formatQty(available) + " available, " + qty + " requested";
			}
		}

		return null;
	}

	private int generatePurchasesOrSales(SQLiteDatabase conn, String time, String notes) {

		// date -> partyId -> lines purchased/sold that day for that party
		TreeMap<String, HashMap<Integer, ArrayList<Object[]>>> groups =
			new TreeMap<String, HashMap<Integer, ArrayList<Object[]>>>();

		for (GenerateEntryLine line : lines) {

			Integer partyId = samePartyForAllItems ? sessionPartyId : line.linePartyId;

			if (partyId == null) {
				continue;
			}

			for (Map.Entry<String, Integer> e : line.dateCounts.entrySet()) {

				int qty = e.getValue();

				if (qty <= 0) {
					continue;
				}

				String date = e.getKey();

				HashMap<Integer, ArrayList<Object[]>> byParty = groups.get(date);

				if (byParty == null) {
					byParty = new HashMap<Integer, ArrayList<Object[]>>();
					groups.put(date, byParty);
				}

				ArrayList<Object[]> entries = byParty.get(partyId);

				if (entries == null) {
					entries = new ArrayList<Object[]>();
					byParty.put(partyId, entries);
				}

				entries.add(new Object[]{line, qty});
			}
		}

		int recordCount = 0;

		for (Map.Entry<String, HashMap<Integer, ArrayList<Object[]>>> dateEntry : groups.entrySet()) {

			String date = dateEntry.getKey();

			for (Map.Entry<Integer, ArrayList<Object[]>> partyEntry : dateEntry.getValue().entrySet()) {

				int partyId = partyEntry.getKey();
				ArrayList<Object[]> entries = partyEntry.getValue();

				double grandTotal = 0;

				for (Object[] pair : entries) {
					GenerateEntryLine line = (GenerateEntryLine) pair[0];
					int qty = (Integer) pair[1];
					grandTotal += qty * line.unitValue;
				}

				if (entryType == TYPE_PURCHASE) {

					// Always a 100% credit purchase (amountPaid is always 0
					// above) - same "date + 3 days" default due date the
					// Sale branch below gets, mirroring Purchase's own Due
					// Date field in Transactioneditactivity.
					long purchaseId = db.insertPurchaseBulk(
						conn, partyId, date, time, "", grandTotal, 0, notes, "Generate Entries",
						addDaysToDateString(date, 3));

					for (Object[] pair : entries) {

						GenerateEntryLine line = (GenerateEntryLine) pair[0];
						int qty = (Integer) pair[1];

						db.insertPurchaseItemBulk(
							conn, purchaseId, line.refId, qty, line.unitValue,
							qty * line.unitValue, line.comboId);
					}

				} else {

					HashMap<String, Object> saleData = new HashMap<String, Object>();
					saleData.put("invoice_no", db.getNextSaleInvoiceNoBulk(conn));
					saleData.put("date", date);
					saleData.put("time", time);
					saleData.put("party_id", partyId);
					saleData.put("subtotal", grandTotal);
					saleData.put("discount", 0.0);
					saleData.put("other_charges", 0.0);
					saleData.put("grand_total", grandTotal);
					saleData.put("paid_amount", 0.0);
					saleData.put("balance", grandTotal);
					saleData.put("notes", notes);

					// Always a 100% credit sale (paid_amount is always 0
					// above) - give it the same "sale date + 3 days"
					// default due date Transactioneditactivity's
					// updateDueDateVisibility() sets for a manually-entered
					// credit sale, or every sale this tool generates would
					// silently have no due date at all.
					saleData.put("due_date", addDaysToDateString(date, 3));

					long saleId = db.insertSaleBulk(conn, saleData, "Generate Entries");

					for (Object[] pair : entries) {

						GenerateEntryLine line = (GenerateEntryLine) pair[0];
						int qty = (Integer) pair[1];

						HashMap<String, Object> itemData = new HashMap<String, Object>();
						itemData.put("sale_id", saleId);
						itemData.put("item_id", line.refId);
						itemData.put("qty", (double) qty);
						itemData.put("rate", line.unitValue);
						itemData.put("amount", qty * line.unitValue);
						itemData.put("combo_id", line.comboId);

						db.insertSaleItemBulk(conn, itemData);
					}
				}

				recordCount++;
			}
		}

		return recordCount;
	}

	// "yyyy-MM-dd" in, "yyyy-MM-dd" + days out - see generatePurchasesOrSales()'s
	// due_date default for the one caller.
	private String addDaysToDateString(String dateStr, int days) {

		java.util.Calendar calendar = java.util.Calendar.getInstance();

		try {

			String[] parts = dateStr.split("-");

			calendar.set(
				Integer.parseInt(parts[0]),
				Integer.parseInt(parts[1]) - 1,
				Integer.parseInt(parts[2])
			);

		} catch (Exception e) {
		}

		calendar.add(java.util.Calendar.DAY_OF_YEAR, days);

		return String.format(
			Locale.US,
			"%04d-%02d-%02d",
			calendar.get(java.util.Calendar.YEAR),
			calendar.get(java.util.Calendar.MONTH) + 1,
			calendar.get(java.util.Calendar.DAY_OF_MONTH)
		);
	}

	private int generatePayments(SQLiteDatabase conn, String time, String notes) {

		int direction = entryType == TYPE_PAYMENT_IN ? DatabaseHelper.PAYMENT_IN : DatabaseHelper.PAYMENT_OUT;

		TreeMap<String, HashMap<Integer, Double>> groups = new TreeMap<String, HashMap<Integer, Double>>();

		for (GenerateEntryLine line : lines) {

			if (line.refId == null) {
				continue;
			}

			for (Map.Entry<String, Integer> e : line.dateCounts.entrySet()) {

				int qty = e.getValue();

				if (qty <= 0) {
					continue;
				}

				String date = e.getKey();
				double amount = qty * line.unitValue;

				HashMap<Integer, Double> byParty = groups.get(date);

				if (byParty == null) {
					byParty = new HashMap<Integer, Double>();
					groups.put(date, byParty);
				}

				Double existing = byParty.get(line.refId);
				byParty.put(line.refId, (existing == null ? 0 : existing) + amount);
			}
		}

		int recordCount = 0;

		for (Map.Entry<String, HashMap<Integer, Double>> dateEntry : groups.entrySet()) {

			String date = dateEntry.getKey();

			for (Map.Entry<Integer, Double> partyEntry : dateEntry.getValue().entrySet()) {

				db.insertPaymentBulk(
					conn, direction, partyEntry.getKey(), date, time, partyEntry.getValue(), notes,
					"Generate Entries");

				recordCount++;
			}
		}

		return recordCount;
	}

	private int generateExpenses(SQLiteDatabase conn, String time, String notes) {

		int recordCount = 0;

		for (GenerateEntryLine line : lines) {

			for (Map.Entry<String, Integer> e : line.dateCounts.entrySet()) {

				int qty = e.getValue();

				if (qty <= 0) {
					continue;
				}

				double amount = qty * line.unitValue;

				db.insertExpenseBulk(
					conn, line.label, e.getKey(), time, amount, notes, line.linePartyId,
					"Generate Entries");

				recordCount++;
			}
		}

		return recordCount;
	}

	private void showGenerateSuccessDialog(int recordCount) {

		String noun = typeLabel(entryType).toLowerCase(Locale.US) + (recordCount == 1 ? " record" : " records");

		new AlertDialog.Builder(this)
			.setTitle("Done")
			.setMessage(recordCount + " " + noun + " created.")
			.setPositiveButton("OK", new DialogInterface.OnClickListener() {
				@Override
				public void onClick(DialogInterface dialog, int which) {
					finish();
				}
			})
			.setCancelable(false)
			.show();
	}

	// =====================
	// SHARED HELPERS
	// =====================

	private void setupPartyAutoComplete(AutoCompleteTextView actv) {

		TwoLineAutoCompleteAdapter adapter =
			new TwoLineAutoCompleteAdapter(this, partyNames, partySubtitleByName);

		actv.setAdapter(adapter);
		actv.setThreshold(1);
	}

	private String formatPartyBalanceSubtitle(Object balanceObj) {

		double balance = balanceObj == null ? 0 : Double.parseDouble(balanceObj.toString());

		if (balance > 0) {
			return "Balance: " + AmountFormat.format(balance) + " (Receivable)";
		} else if (balance < 0) {
			return "Balance: " + AmountFormat.format(Math.abs(balance)) + " (Payable)";
		}

		return "Balance: 0 (Settled)";
	}

	private String typeLabel(int type) {
		switch (type) {
			case TYPE_PURCHASE: return "Purchase";
			case TYPE_SALE: return "Sale";
			case TYPE_PAYMENT_IN: return "Payment In";
			case TYPE_PAYMENT_OUT: return "Payment Out";
			default: return "Expense";
		}
	}

	private double parseDoubleOrZero(String s) {

		if (s == null) {
			return 0;
		}

		s = s.trim();

		if (s.length() == 0) {
			return 0;
		}

		try {
			return Double.parseDouble(s);
		} catch (NumberFormatException e) {
			return 0;
		}
	}

	private String formatQty(double qty) {

		return AmountFormat.formatPlain(qty);
	}
}
