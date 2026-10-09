package com.mujeer.businesserp;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

public class Additemactivity extends Activity {

    EditText et_item_code;
    EditText et_item_name;
    EditText et_purchase_price;
    EditText et_sale_price;

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

    Button btn_save_item;

    DatabaseHelper db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.additemactivityv);

        et_item_code = findViewById(R.id.et_item_code);
        et_item_name = findViewById(R.id.et_item_name);
        et_purchase_price = findViewById(R.id.et_purchase_price);
        et_sale_price = findViewById(R.id.et_sale_price);

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

        btn_save_item = findViewById(R.id.btn_save_item);

        db = new DatabaseHelper(this);

        // Pre-filled with what would be auto-assigned if left alone -
        // the user can still type a different one before saving.
        et_item_code.setText(db.peekNextItemCode());

        // Lets a caller (e.g. the item picker in Transactioneditactivity)
        // pre-fill the name when the user typed something that didn't
        // match any existing item, so they don't have to retype it here.
        String prefillName = getIntent().getStringExtra("item_name");

        if (prefillName != null && !prefillName.trim().isEmpty()) {

            et_item_name.setText(prefillName.trim());
            et_item_name.setSelection(et_item_name.getText().length());
        }

        cb_is_shoe.setOnCheckedChangeListener(
            new android.widget.CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(
                    android.widget.CompoundButton buttonView, boolean isChecked) {
                    applyShoeMode(isChecked);
                }
            }
        );

        btn_save_item.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {

					String code = et_item_code.getText().toString().trim();
					String purchase = et_purchase_price.getText().toString().trim();
					String sale = et_sale_price.getText().toString().trim();

					String name;

					if (cb_is_shoe.isChecked()) {

						name = buildShoeName();

						if (name == null) {
							return;
						}

					} else {

						name = et_item_name.getText().toString().trim();
					}

					if (code.isEmpty() || name.isEmpty() || purchase.isEmpty() || sale.isEmpty()) {

						Toast.makeText(
                            Additemactivity.this,
                            "Fill all fields",
                            Toast.LENGTH_SHORT
						).show();

						return;
					}

					if (db.isItemCodeTaken(code)) {

						Toast.makeText(
                            Additemactivity.this,
                            "That item code is already in use",
                            Toast.LENGTH_SHORT
						).show();

						return;
					}

					double purchasePrice = Double.parseDouble(purchase);
					double salePrice = Double.parseDouble(sale);

					long result = db.insertItem(
                        code,
                        name,
                        purchasePrice,
                        salePrice,
                        0
					);

					if (result != -1) {

						Toast.makeText(
                            Additemactivity.this,
                            "Item saved",
                            Toast.LENGTH_SHORT
						).show();

						Intent data = new Intent();
						data.putExtra("item_id", (int) result);
						data.putExtra("item_name", name);
						setResult(RESULT_OK, data);

						finish();

					} else {

						Toast.makeText(
                            Additemactivity.this,
                            "Item already exists",
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
    // shape exactly. Returns null (after telling the user why) if any
    // field is empty or contains whitespace, since ShoeIdentity.parse()
    // splits on whitespace and expects exactly 7 single-word tokens
    // between "Shoes" and the trailing " - {Code}".
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

        String code = et_item_code.getText().toString().trim();

        return "Shoes " + fields[0] + " " + fields[1] + " " + fields[2] + " " + fields[3] +
            " " + fields[4] + " " + fields[5] + " " + fields[6] + " - " + code;
    }
}
