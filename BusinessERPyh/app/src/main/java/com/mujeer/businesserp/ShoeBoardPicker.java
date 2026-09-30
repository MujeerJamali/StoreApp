package com.mujeer.businesserp;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Spinner;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.HashMap;

// Shared "pick an item, then pick one of its sizes" dialog used by both
// DisplayShoesActivity and SampleShoesActivity's "Add" flow - the two
// boards differ only in what they do with the resulting item+combo
// (grid placement vs a plain list add), not in how that pair gets
// picked.
public class ShoeBoardPicker {

	public interface OnPickedListener {
		void onPicked(int itemId, String itemCode, int comboId, String comboLabel);
	}

	public static void show(final Activity activity, final DatabaseHelper db, final OnPickedListener listener) {

		final ArrayList<HashMap<String, Object>> items = db.getItemsWithVarietyCombos();

		if (items.isEmpty()) {

			Toast.makeText(
				activity,
				"No items have a Size/Variety set up yet - add one on the item's Edit screen first",
				Toast.LENGTH_LONG
			).show();

			return;
		}

		View view = activity.getLayoutInflater().inflate(R.layout.dialog_add_shoe_board_entry, null);

		final AutoCompleteTextView actvItem = view.findViewById(R.id.actv_shoe_item);
		final Spinner spinnerCombo = view.findViewById(R.id.spinner_shoe_combo);

		final ArrayList<String> itemNames = new ArrayList<String>();

		for (HashMap<String, Object> item : items) {
			itemNames.add((String) item.get("name"));
		}

		actvItem.setAdapter(
			new ArrayAdapter<String>(activity, android.R.layout.simple_dropdown_item_1line, itemNames)
		);
		actvItem.setThreshold(1);

		final ArrayList<HashMap<String, Object>> combosForSelectedItem = new ArrayList<HashMap<String, Object>>();

		actvItem.setOnItemClickListener(new android.widget.AdapterView.OnItemClickListener() {
				@Override
				public void onItemClick(
					android.widget.AdapterView<?> parent, View v, int position, long id) {

					String typedName = actvItem.getText().toString().trim();

					Integer itemId = null;

					for (HashMap<String, Object> item : items) {
						if (typedName.equalsIgnoreCase((String) item.get("name"))) {
							itemId = (Integer) item.get("id");
							break;
						}
					}

					combosForSelectedItem.clear();
					ArrayList<String> comboLabels = new ArrayList<String>();

					if (itemId != null) {

						combosForSelectedItem.addAll(db.getVarietyCombos(itemId));

						for (HashMap<String, Object> combo : combosForSelectedItem) {
							comboLabels.add((String) combo.get("label"));
						}
					}

					ArrayAdapter<String> comboAdapter = new ArrayAdapter<String>(
						activity, android.R.layout.simple_spinner_item, comboLabels
					);

					comboAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
					spinnerCombo.setAdapter(comboAdapter);
				}
			});

		new AlertDialog.Builder(activity)
			.setTitle("Add Shoe")
			.setView(view)
			.setPositiveButton("Add", new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int which) {

						String typedName = actvItem.getText().toString().trim();

						HashMap<String, Object> matchedItem = null;

						for (HashMap<String, Object> item : items) {
							if (typedName.equalsIgnoreCase((String) item.get("name"))) {
								matchedItem = item;
								break;
							}
						}

						if (matchedItem == null) {

							Toast.makeText(activity, "Select a valid item", Toast.LENGTH_SHORT).show();
							return;
						}

						int spinnerPosition = spinnerCombo.getSelectedItemPosition();

						if (spinnerPosition < 0 || spinnerPosition >= combosForSelectedItem.size()) {

							Toast.makeText(activity, "This item has no sizes set up", Toast.LENGTH_SHORT).show();
							return;
						}

						HashMap<String, Object> selectedCombo = combosForSelectedItem.get(spinnerPosition);

						listener.onPicked(
							(Integer) matchedItem.get("id"),
							(String) matchedItem.get("code"),
							(Integer) selectedCombo.get("id"),
							(String) selectedCombo.get("label")
						);
					}
				})
			.setNegativeButton("Cancel", null)
			.show();
	}
}
