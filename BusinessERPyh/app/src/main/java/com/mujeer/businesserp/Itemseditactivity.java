package com.mujeer.businesserp;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.HashMap;

public class Itemseditactivity extends Activity {

    EditText et_item_name;
    EditText et_purchase_price;
    EditText et_sale_price;
    TextView tv_item_stock;
    EditText et_reorder_threshold;
    CheckBox cb_active;

    CheckBox cb_is_shoe;
    TextView tv_item_name_label;
    LinearLayout container_shoe_fields;

    EditText et_shoe_gender;
    EditText et_shoe_type;
    EditText et_shoe_sole;
    EditText et_shoe_upper;
    EditText et_shoe_design;
    EditText et_shoe_color;
    EditText et_shoe_size;

    LinearLayout cardVarieties;
    LinearLayout containerVarieties;
    Button btnAddVarietyGroup;

    Button btn_update_item;

    DatabaseHelper db;

    int itemId = -1;
    String itemCode = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.itemseditactivityv);

        et_item_name = findViewById(R.id.et_item_name);
        et_purchase_price = findViewById(R.id.et_purchase_price);
        et_sale_price = findViewById(R.id.et_sale_price);
        tv_item_stock = findViewById(R.id.tv_item_stock);
        et_reorder_threshold = findViewById(R.id.et_reorder_threshold);
        cb_active = findViewById(R.id.cb_active);

        cb_is_shoe = findViewById(R.id.cb_is_shoe);
        tv_item_name_label = findViewById(R.id.tv_item_name_label);
        container_shoe_fields = findViewById(R.id.container_shoe_fields);

        et_shoe_gender = findViewById(R.id.et_shoe_gender);
        et_shoe_type = findViewById(R.id.et_shoe_type);
        et_shoe_sole = findViewById(R.id.et_shoe_sole);
        et_shoe_upper = findViewById(R.id.et_shoe_upper);
        et_shoe_design = findViewById(R.id.et_shoe_design);
        et_shoe_color = findViewById(R.id.et_shoe_color);
        et_shoe_size = findViewById(R.id.et_shoe_size);

        cb_is_shoe.setOnCheckedChangeListener(
            new android.widget.CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(
                    android.widget.CompoundButton buttonView, boolean isChecked) {
                    applyShoeMode(isChecked);
                }
            }
        );

        btn_update_item = findViewById(R.id.btn_update_item);

        cardVarieties = findViewById(R.id.card_varieties);
        containerVarieties = findViewById(R.id.container_varieties);
        btnAddVarietyGroup = findViewById(R.id.btn_add_variety_group);

        db = new DatabaseHelper(this);

        itemId = getIntent().getIntExtra("item_id", -1);

        if (itemId != -1) {

            HashMap<String, Object> item = db.getItemById(itemId);

            itemCode = (String) item.get("code");

            String existingName = (String) item.get("name");

            et_item_name.setText(existingName);

            ShoeIdentity existingShoe = ShoeIdentity.parse(existingName);

            if (existingShoe != null) {

                cb_is_shoe.setChecked(true);
                applyShoeMode(true);

                et_shoe_gender.setText(existingShoe.gender);
                et_shoe_type.setText(existingShoe.type);
                et_shoe_sole.setText(existingShoe.sole);
                et_shoe_upper.setText(existingShoe.upper);
                et_shoe_design.setText(existingShoe.design);
                et_shoe_color.setText(existingShoe.color);
                et_shoe_size.setText(existingShoe.size);
            }

            et_purchase_price.setText(AmountFormat.formatPlain((Double) item.get("purchase_price")));
            et_sale_price.setText(AmountFormat.formatPlain((Double) item.get("sale_price")));

            double stock = 0;

            if (item.get("balance") != null) {
                stock = (Double) item.get("balance");
            }

            tv_item_stock.setText(AmountFormat.formatPlain(stock));

            if (stock <= 0) {
                tv_item_stock.setTextColor(getResources().getColor(R.color.danger));
            } else {
                tv_item_stock.setTextColor(getResources().getColor(R.color.mod_items));
            }

            double reorderThreshold = 0;

            if (item.get("reorder_threshold") != null) {
                reorderThreshold = (Double) item.get("reorder_threshold");
            }

            et_reorder_threshold.setText(AmountFormat.formatPlain(reorderThreshold));

            cb_active.setChecked(!Boolean.FALSE.equals(item.get("active")));

            // Varieties only make sense once the item has an id to attach
            // groups to - a brand-new item has to be saved once first.
            cardVarieties.setVisibility(View.VISIBLE);
            loadVarieties();

            btnAddVarietyGroup.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    promptAddVarietyGroup();
                }
            });
        }

        btn_update_item.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {

					String name;

					if (cb_is_shoe.isChecked()) {

						name = buildShoeName();

						if (name == null) {
							return;
						}

					} else {

						name = et_item_name.getText().toString().trim();
					}

					String purchase = et_purchase_price.getText().toString().trim();
					String sale = et_sale_price.getText().toString().trim();

					if (name.isEmpty() || purchase.isEmpty() || sale.isEmpty()) {

						Toast.makeText(
                            Itemseditactivity.this,
                            "Fill all fields",
                            Toast.LENGTH_SHORT
						).show();

						return;
					}

					double purchasePrice = Double.parseDouble(purchase);
					double salePrice = Double.parseDouble(sale);

					double reorderThreshold = 0;

					try {

						reorderThreshold = Double.parseDouble(
							et_reorder_threshold.getText().toString().trim()
						);

					} catch (Exception e) {
					}

					if (db.updateItem(
							itemId,
							name,
							purchasePrice,
							salePrice,
							null,
							reorderThreshold
						)) {

						db.setItemActive(itemId, cb_active.isChecked());

						Toast.makeText(
                            Itemseditactivity.this,
                            "Item updated",
                            Toast.LENGTH_SHORT
						).show();

						finish();

					} else {

						Toast.makeText(
                            Itemseditactivity.this,
                            "Update failed",
                            Toast.LENGTH_SHORT
						).show();
					}
				}
			});
    }

    // Toggling "This is a shoe" swaps the plain free-text Name field for
    // the 7 structured fields (Gender/Type/Sole/Upper/Design/Color/Size)
    // that ShoeIdentity.parse() expects joined together - see its own
    // comment for the exact naming convention this has to match.
    private void applyShoeMode(boolean isShoe) {

        tv_item_name_label.setVisibility(isShoe ? View.GONE : View.VISIBLE);
        et_item_name.setVisibility(isShoe ? View.GONE : View.VISIBLE);
        container_shoe_fields.setVisibility(isShoe ? View.VISIBLE : View.GONE);
    }

    // Builds "Shoes {Gender} {Type} {Sole} {Upper} {Design} {Color} {Size}
    // - {Code}" from the 7 shoe fields, matching ShoeIdentity's required
    // shape exactly - see Additemactivity's own copy of this method for
    // the same validation. The code is this item's own existing code
    // (unchanged - this screen has no code field of its own to edit).
    private String buildShoeName() {

        String[] fields = {
            et_shoe_gender.getText().toString().trim(),
            et_shoe_type.getText().toString().trim(),
            et_shoe_sole.getText().toString().trim(),
            et_shoe_upper.getText().toString().trim(),
            et_shoe_design.getText().toString().trim(),
            et_shoe_color.getText().toString().trim(),
            et_shoe_size.getText().toString().trim()
        };

        for (String field : fields) {

            if (field.isEmpty()) {

                Toast.makeText(this, "Fill every shoe field", Toast.LENGTH_SHORT).show();
                return null;
            }

            if (field.contains(" ")) {

                Toast.makeText(
                    this, "Each shoe field must be a single word (no spaces)", Toast.LENGTH_SHORT
                ).show();

                return null;
            }
        }

        return "Shoes " + fields[0] + " " + fields[1] + " " + fields[2] + " " + fields[3] +
            " " + fields[4] + " " + fields[5] + " " + fields[6] + " - " + itemCode;
    }

    // =====================
    // VARIETIES
    // =====================

    private void loadVarieties() {

        containerVarieties.removeAllViews();

        ArrayList<HashMap<String, Object>> groups = db.getVarietyGroups(itemId);

        int chipPad = (int) (10 * getResources().getDisplayMetrics().density);
        int chipMargin = (int) (8 * getResources().getDisplayMetrics().density);

        for (HashMap<String, Object> group : groups) {

            final int groupId = (Integer) group.get("id");
            String groupName = (String) group.get("name");

            TextView groupLabel = new TextView(this);
            groupLabel.setText(groupName);
            groupLabel.setTextColor(getResources().getColor(R.color.text_primary));
            groupLabel.setTextSize(14);
            groupLabel.setTypeface(groupLabel.getTypeface(), android.graphics.Typeface.BOLD);
            groupLabel.setPadding(0, chipMargin, 0, chipMargin / 2);
            containerVarieties.addView(groupLabel);

            android.widget.HorizontalScrollView scroll =
                new android.widget.HorizontalScrollView(this);

            LinearLayout.LayoutParams scrollParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
            scroll.setLayoutParams(scrollParams);

            LinearLayout chipRow = new LinearLayout(this);
            chipRow.setOrientation(LinearLayout.HORIZONTAL);

            ArrayList<HashMap<String, Object>> values = db.getVarietyValues(groupId);

            for (HashMap<String, Object> value : values) {

                TextView chip = new TextView(this);
                chip.setText((String) value.get("label"));
                chip.setBackgroundResource(R.drawable.bg_chip);
                chip.setTextColor(getResources().getColor(R.color.text_primary));
                chip.setTextSize(13);
                chip.setPadding(chipPad * 2, chipPad, chipPad * 2, chipPad);

                LinearLayout.LayoutParams chipParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
                chipParams.setMargins(0, 0, chipMargin, 0);
                chip.setLayoutParams(chipParams);

                chipRow.addView(chip);
            }

            TextView addValueChip = new TextView(this);
            addValueChip.setText("+ Add");
            addValueChip.setTextColor(getResources().getColor(R.color.primary));
            addValueChip.setTextSize(13);
            addValueChip.setPadding(chipPad * 2, chipPad, chipPad * 2, chipPad);

            addValueChip.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    promptAddVarietyValue(groupId);
                }
            });

            chipRow.addView(addValueChip);

            scroll.addView(chipRow);
            containerVarieties.addView(scroll);
        }
    }

    private void promptAddVarietyGroup() {

        final EditText input = new EditText(this);
        input.setHint("e.g. Size");
        input.setInputType(InputType.TYPE_CLASS_TEXT);

        int pad = (int) (16 * getResources().getDisplayMetrics().density);
        input.setPadding(pad, pad, pad, pad);

        new AlertDialog.Builder(this)
            .setTitle("Add Variety Group")
            .setView(input)
            .setPositiveButton("Add", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {

                    String name = input.getText().toString().trim();

                    if (name.isEmpty()) {

                        Toast.makeText(
                            Itemseditactivity.this,
                            "Enter a group name",
                            Toast.LENGTH_SHORT
                        ).show();

                        return;
                    }

                    db.createVarietyGroup(itemId, name);
                    loadVarieties();
                }
            })
            .setNegativeButton("Cancel", null)
            .show();
    }

    private void promptAddVarietyValue(final int groupId) {

        final EditText input = new EditText(this);
        input.setHint("e.g. M");
        input.setInputType(InputType.TYPE_CLASS_TEXT);

        int pad = (int) (16 * getResources().getDisplayMetrics().density);
        input.setPadding(pad, pad, pad, pad);

        new AlertDialog.Builder(this)
            .setTitle("Add Value")
            .setView(input)
            .setPositiveButton("Add", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {

                    String label = input.getText().toString().trim();

                    if (label.isEmpty()) {

                        Toast.makeText(
                            Itemseditactivity.this,
                            "Enter a value",
                            Toast.LENGTH_SHORT
                        ).show();

                        return;
                    }

                    db.addVarietyValue(groupId, label);
                    loadVarieties();
                }
            })
            .setNegativeButton("Cancel", null)
            .show();
    }
}
