package com.mujeer.businesserp;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.view.View;
import android.widget.TextView;

// =====================
// The app-wide minimalist "i" badge - a small circle with "i" that, on
// tap, shows an AlertDialog describing whatever feature it sits next
// to. Meant to go right beside a screen's PageTitle (see any report
// layout for the pattern: <PageTitle/><InfoBubbleView/> in a
// horizontal row), one per screen for this first pass - covering
// every remaining screen is an ongoing incremental effort afterward,
// the same way charts and bulk select were rolled out one screen at a
// time rather than everywhere at once.
//
// Deliberately text ("i"), not an image/icon drawable - this app
// removed icons everywhere else (see README's Modules section); this
// is a functional tap affordance, not decoration, so it's exempt the
// same way a Spinner's dropdown arrow would be.
//
// Usage: declare <com.mujeer.businesserp.InfoBubbleView
// android:layout_width="@dimen/info_bubble_size"
// android:layout_height="@dimen/info_bubble_size" .../> in the
// layout, then call infoBubble.setInfo(title, description) once in
// onCreate() - a plain two-line call, no dialog-building boilerplate
// repeated per screen.
// =====================
public class InfoBubbleView extends TextView {

	public InfoBubbleView(Context context) {
		super(context);
		init();
	}

	public InfoBubbleView(Context context, AttributeSet attrs) {
		super(context, attrs);
		init();
	}

	private void init() {

		setText("i");
		setGravity(android.view.Gravity.CENTER);
		setTypeface(getTypeface(), Typeface.BOLD_ITALIC);
		setTextColor(getResources().getColor(R.color.primary));
		setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 11);
		setBackgroundResource(R.drawable.bg_info_bubble);
		setIncludeFontPadding(false);
	}

	public void setInfo(final String title, final String description) {

		setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {

					new AlertDialog.Builder(getContext())
						.setTitle(title)
						.setMessage(description)
						.setPositiveButton("Got it", null)
						.show();
				}
			});
	}
}
