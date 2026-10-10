package com.mujeer.businesserp;

import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.widget.FrameLayout;

// A per-row swipe-to-reveal container for list rows: swiping the row
// content (child id swipe_content) leftward slides it over to reveal an
// actions panel underneath (child id swipe_actions, e.g. Edit/Delete
// buttons), the way a typical mobile list row reveals actions. This is
// a distinct gesture from the app-wide screen-to-screen swipe navigation
// (Transactioneditactivity etc.) - it only ever acts within a single row.
//
// Deliberately plain View/MotionEvent handling, not RecyclerView's
// ItemTouchHelper - every list in this app is a ListView/BaseAdapter
// (no RecyclerView dependency is wired into build.gradle), so this
// follows that same no-new-dependency constraint.
public class SwipeRevealLayout extends FrameLayout {

	public interface OnSwipeActionListener {
		void onEditAction();
		void onDeleteAction();
	}

	// Only one row across the whole app can be open at a time - opening
	// a new one closes whichever was previously open, same as the
	// standard swipe-list pattern on other apps.
	private static SwipeRevealLayout openRow;

	private View contentView;
	private View actionsView;

	private int actionsWidth = 0;
	private boolean open = false;
	private boolean swiping = false;

	private float downX;
	private float downY;
	private float startTranslation;

	private final int touchSlop;
	private VelocityTracker velocityTracker;

	private OnSwipeActionListener listener;

	public SwipeRevealLayout(Context context) {
		super(context);
		touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
	}

	public SwipeRevealLayout(Context context, AttributeSet attrs) {
		super(context, attrs);
		touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
	}

	public void setOnSwipeActionListener(OnSwipeActionListener listener) {
		this.listener = listener;
	}

	@Override
	protected void onFinishInflate() {
		super.onFinishInflate();
		contentView = findViewById(R.id.swipe_content);
		actionsView = findViewById(R.id.swipe_actions);
	}

	@Override
	protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
		super.onLayout(changed, left, top, right, bottom);

		if (actionsView != null) {
			actionsWidth = actionsView.getWidth();
		}
	}

	// Called by the adapter at the top of getView(), before rebinding
	// row data, so a recycled convertView never shows a stale open
	// state for its new row.
	public void close(boolean animate) {

		open = false;

		if (openRow == this) {
			openRow = null;
		}

		if (contentView == null) {
			return;
		}

		if (animate) {
			contentView.animate().translationX(0).setDuration(150).start();
		} else {
			contentView.setTranslationX(0);
		}
	}

	private boolean isSwipeEnabled() {
		return actionsView != null && actionsView.getVisibility() == View.VISIBLE;
	}

	@Override
	public boolean onInterceptTouchEvent(MotionEvent ev) {

		if (!isSwipeEnabled()) {
			return false;
		}

		float x = ev.getX();
		float y = ev.getY();

		switch (ev.getActionMasked()) {

			case MotionEvent.ACTION_DOWN:

				downX = x;
				downY = y;
				swiping = false;

				// While open, a touch over the revealed actions panel is
				// left alone so its buttons receive the click normally;
				// a touch over the (still-visible) content area is
				// claimed so it can act as "tap to close".
				if (open && x < (getWidth() - actionsWidth)) {
					swiping = true;
					startTranslation = contentView.getTranslationX();
					return true;
				}

				return false;

			case MotionEvent.ACTION_MOVE:

				float dx = x - downX;
				float dy = y - downY;

				if (!swiping && Math.abs(dx) > touchSlop && Math.abs(dx) > Math.abs(dy)) {
					swiping = true;
					startTranslation = contentView.getTranslationX();
				}

				return swiping;

			default:
				return false;
		}
	}

	@Override
	public boolean onTouchEvent(MotionEvent ev) {

		if (!isSwipeEnabled()) {
			return false;
		}

		if (velocityTracker == null) {
			velocityTracker = VelocityTracker.obtain();
		}

		velocityTracker.addMovement(ev);

		float x = ev.getX();

		switch (ev.getActionMasked()) {

			case MotionEvent.ACTION_DOWN:

				downX = x;
				startTranslation = contentView.getTranslationX();
				return true;

			case MotionEvent.ACTION_MOVE: {

				float dx = x - downX;
				float newTranslation = startTranslation + dx;

				if (newTranslation < -actionsWidth) {
					newTranslation = -actionsWidth;
				}
				if (newTranslation > 0) {
					newTranslation = 0;
				}

				contentView.setTranslationX(newTranslation);
				return true;
			}

			case MotionEvent.ACTION_UP:
			case MotionEvent.ACTION_CANCEL: {

				float current = contentView.getTranslationX();
				boolean shouldOpen = actionsWidth > 0 && (-current) > (actionsWidth / 2f);

				if (shouldOpen) {
					openTo(true);
				} else {
					close(true);
				}

				velocityTracker.recycle();
				velocityTracker = null;
				swiping = false;

				return true;
			}

			default:
				return false;
		}
	}

	private void openTo(boolean animate) {

		if (openRow != null && openRow != this) {
			openRow.close(true);
		}

		open = true;
		openRow = this;

		if (animate) {
			contentView.animate().translationX(-actionsWidth).setDuration(150).start();
		} else {
			contentView.setTranslationX(-actionsWidth);
		}
	}

	public void wireActionButtons(View editButton, View deleteButton) {

		if (editButton != null) {
			editButton.setOnClickListener(new View.OnClickListener() {
					@Override
					public void onClick(View v) {
						close(true);
						if (listener != null) {
							listener.onEditAction();
						}
					}
				});
		}

		if (deleteButton != null) {
			deleteButton.setOnClickListener(new View.OnClickListener() {
					@Override
					public void onClick(View v) {
						if (listener != null) {
							listener.onDeleteAction();
						}
					}
				});
		}
	}
}
