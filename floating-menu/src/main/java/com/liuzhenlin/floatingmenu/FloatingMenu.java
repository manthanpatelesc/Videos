package com.liuzhenlin.floatingmenu;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.TypedArray;
import android.content.res.XmlResourceParser;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.util.AttributeSet;
import android.util.Xml;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TextView;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;

public class FloatingMenu extends PopupWindow {

    /** Menu tag name in XML. */
    private static final String XML_TAG_MENU = "menu";

    /** Group tag name in XML. */
    private static final String XML_TAG_GROUP = "group";

    /** Item tag name in XML. */
    private static final String XML_TAG_ITEM = "item";

    private static final int GRAVITY = Gravity.TOP | Gravity.START;

    private final Context mContext;

    private final View mAnchorView;

    private final int mScreenWidth;
    private final int mScreenHeight;

    // Match the width of the contentView of this menu
    private static final int DEFAULT_ITEM_WIDTH = ViewGroup.LayoutParams.MATCH_PARENT;

    private final List<MenuItem> mMenuItems = new ArrayList<>();

    private LinearLayout mMenuLayout;

    private OnItemClickListener mOnItemClickListener;
    private int mDownX;
    private int mDownY;

    public interface OnItemClickListener {
        void onClick(MenuItem menuItem, int position);
    }

    public FloatingMenu(@NonNull View anchor) {
        super(anchor.getContext());
        setFocusable(true);
        setBackgroundDrawable(new BitmapDrawable());

        mContext = anchor.getContext();
        mAnchorView = anchor;
        mAnchorView.setOnTouchListener(new MenuOnTouchListener());
        mScreenWidth = DensityUtils.getScreenWidth(mContext);
        mScreenHeight = DensityUtils.getScreenHeight(mContext);
    }

    public void inflate(int menuRes) {
        inflate(menuRes, DEFAULT_ITEM_WIDTH);
    }

    public void inflate(int menuRes, int itemWidth) {
        XmlResourceParser parser = mContext.getResources().getLayout(menuRes);
        AttributeSet attrs = Xml.asAttributeSet(parser);
        try {
            parseMenu(parser, attrs);
        } catch (XmlPullParserException | IOException e) {
            e.printStackTrace();
        } finally {
            parser.close();
        }
        generateLayout(itemWidth);
    }

    private void parseMenu(XmlPullParser parser, AttributeSet attrs)
            throws XmlPullParserException, IOException {
        int eventType = parser.getEventType();
        String tagName;
        // This loop will skip to the menu start tag
        do {
            if (eventType == XmlPullParser.START_TAG) {
                tagName = parser.getName();
                if (XML_TAG_MENU.equals(tagName)) {
                    // Go to next tag
                    eventType = parser.next();
                    break;
                }

                throw new RuntimeException("Expecting menu, got " + tagName);
            }

            eventType = parser.next();
        } while (eventType != XmlPullParser.END_DOCUMENT);

        boolean reachedEndOfMenu = false;
        boolean lookingForEndOfUnknownTag = false;
        String unknownTagName = null;
        while (!reachedEndOfMenu) {
            switch (eventType) {
                case XmlPullParser.START_TAG:
                    if (lookingForEndOfUnknownTag) {
                        break;
                    }

                    tagName = parser.getName();
                    if (XML_TAG_ITEM.equals(tagName)) {
                        readItem(attrs);
                    } else {
                        lookingForEndOfUnknownTag = true;
                        unknownTagName = tagName;
                    }
                    break;

                case XmlPullParser.END_TAG:
                    tagName = parser.getName();
                    if (lookingForEndOfUnknownTag && tagName.equals(unknownTagName)) {
                        lookingForEndOfUnknownTag = false;
                        unknownTagName = null;
                    } else if (XML_TAG_MENU.equals(tagName)) {
                        reachedEndOfMenu = true;
                    }
                    break;

                case XmlPullParser.END_DOCUMENT:
                    throw new RuntimeException("Unexpected end of document!");
            }

            eventType = parser.next();
        }
    }

    private void readItem(AttributeSet attrs) {
        TypedArray ta = mContext.obtainStyledAttributes(attrs, R.styleable.MenuItem);
        MenuItem item = new MenuItem();
        final int iconResId = ta.getResourceId(R.styleable.MenuItem_icon, View.NO_ID);
        if (iconResId != View.NO_ID) {
            item.setIconResId(iconResId);
        }
        final String text = ta.getText(R.styleable.MenuItem_text).toString();
        item.setText(text);
        mMenuItems.add(item);
        ta.recycle();
    }

    public void items(String... items) {
        items(DEFAULT_ITEM_WIDTH, items);
    }

    public void items(int itemWidth, String... items) {
        mMenuItems.clear();
        for (String item : items) {
            mMenuItems.add(new MenuItem(item));
        }
        generateLayout(itemWidth);
    }

    public <T extends MenuItem> void items(List<T> items) {
        mMenuItems.clear();
        mMenuItems.addAll(items);
        generateLayout(DEFAULT_ITEM_WIDTH);
    }

    public <T extends MenuItem> void items(List<T> items, int itemWidth) {
        mMenuItems.clear();
        mMenuItems.addAll(items);
        generateLayout(itemWidth);
    }

    private void generateLayout(int itemWidth) {
        mMenuLayout = new LinearLayout(mContext);
        mMenuLayout.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        mMenuLayout.setBackgroundDrawable(ContextCompat.getDrawable(mContext, R.drawable.bg_shadow));
        mMenuLayout.setOrientation(LinearLayout.VERTICAL);

        final int padding = DensityUtils.dp2px(mContext, 12);
        for (int i = 0, itemCount = mMenuItems.size(); i < itemCount; i++) {
            MenuItem menuItem = mMenuItems.get(i);

            TextView textView = new TextView(mContext);
            textView.setLayoutParams(new LinearLayout.LayoutParams(
                    itemWidth, ViewGroup.LayoutParams.WRAP_CONTENT));
            ViewCompat.setPaddingRelative(textView, padding, padding, padding * 2, padding);
            textView.setClickable(true);
            textView.setBackgroundDrawable(ContextCompat.getDrawable(mContext, R.drawable.selector_item));
            textView.setText(menuItem.getText());
            textView.setTextSize(15);
            textView.setTextColor(Color.BLACK);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
                textView.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_START);
            }
            textView.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
            if (menuItem.getIconResId() != View.NO_ID) {
                Drawable icon = ContextCompat.getDrawable(mContext, menuItem.getIconResId());
                if (icon != null) {
                    textView.setCompoundDrawablePadding(padding);
                    if (ViewCompat.getLayoutDirection(mAnchorView) == ViewCompat.LAYOUT_DIRECTION_LTR) {
                        textView.setCompoundDrawablesWithIntrinsicBounds(
                                icon, null, null, null);
                    } else {
                        textView.setCompoundDrawablesWithIntrinsicBounds(
                                null, null, icon, null);
                    }
                }
            }
            if (mOnItemClickListener != null) {
                textView.setOnClickListener(new ItemOnClickListener(i));
            }

            mMenuLayout.addView(textView);
        }

        mMenuLayout.measure(
                View.MeasureSpec.makeMeasureSpec(mScreenWidth, View.MeasureSpec.AT_MOST),
                View.MeasureSpec.makeMeasureSpec(mScreenHeight, View.MeasureSpec.AT_MOST));
        setWidth(mMenuLayout.getMeasuredWidth());
        setHeight(mMenuLayout.getMeasuredHeight());
        setContentView(mMenuLayout);
    }

    public void show(int x, int y) {
        mDownX = x;
        mDownY = y;
        show();
    }

    public void show() {
        if (isShowing()) {
            return;
        }
//        // It is must, otherwise 'setFocusable' will not work below Android 6.0
//        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
//            setBackgroundDrawable(new BitmapDrawable());
//        }

        final int height = getHeight();
        if (mDownX <= mScreenWidth / 2) {
            if (mDownY + height < mScreenHeight) {
                setAnimationStyle(R.style.Animation_top_left);
                showAtLocation(mAnchorView, GRAVITY, mDownX, mDownY);
            } else {
                setAnimationStyle(R.style.Animation_bottom_left);
                showAtLocation(mAnchorView, GRAVITY, mDownX, mDownY - height);
            }
        } else {
            if (mDownY + height < mScreenHeight) {
                setAnimationStyle(R.style.Animation_top_right);
                showAtLocation(mAnchorView, GRAVITY, mDownX - getWidth(), mDownY);
            } else {
                setAnimationStyle(R.style.Animation_bottom_right);
                showAtLocation(mAnchorView, GRAVITY, mDownX - getWidth(), mDownY - height);
            }
        }
    }

    public void setOnItemClickListener(OnItemClickListener listener) {
        mOnItemClickListener = listener;
        if (listener != null) {
            for (int i = mMenuLayout.getChildCount() - 1; i >= 0; i--) {
                View view = mMenuLayout.getChildAt(i);
                view.setOnClickListener(new ItemOnClickListener(i));
            }
        }
    }

    private class ItemOnClickListener implements View.OnClickListener {
        int position;

        ItemOnClickListener(int position) {
            this.position = position;
        }

        @Override
        public void onClick(View v) {
            dismiss();
            if (mOnItemClickListener != null) {
                mOnItemClickListener.onClick(mMenuItems.get(position), position);
            }
        }
    }

    private class MenuOnTouchListener implements View.OnTouchListener {

        @SuppressLint("ClickableViewAccessibility")
        @Override
        public boolean onTouch(View v, MotionEvent event) {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                mDownX = (int) event.getRawX();
                mDownY = (int) event.getRawY();
            }
            return false;
        }
    }
}
