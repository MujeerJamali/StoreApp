package com.mujeer.businesserp;

import android.text.Editable;
import android.text.TextWatcher;

// Attach one of these to an AutoCompleteTextView to correctly capture
// what the user actually typed before tapping a "+ Add New ..." row in
// its dropdown. Naively reading actv.getText() from inside
// setOnItemClickListener() doesn't work: AutoCompleteTextView replaces
// the field's text with the clicked row's own display text (e.g.
// "+ Add New Item") *before* onItemClick() runs, so by the time the
// click handler reads the field, the user's typed text is already
// gone - every "+ Add New" flow in this app was passing that sentinel
// label through instead of what was actually typed.
//
// beforeTextChanged() fires with the text as it stood immediately
// before each change - including the change the dropdown selection
// itself causes - so its very last call (triggered by that selection)
// hands back exactly the typed text right before it got overwritten.
public class PreSelectionTextWatcher implements TextWatcher {

	public String textBeforeChange = "";

	@Override
	public void beforeTextChanged(CharSequence s, int start, int count, int after) {
		textBeforeChange = s.toString();
	}

	@Override
	public void onTextChanged(CharSequence s, int start, int before, int count) {
	}

	@Override
	public void afterTextChanged(Editable s) {
	}
}
