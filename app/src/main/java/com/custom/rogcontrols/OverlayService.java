package com.custom.rogcontrols;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import java.util.HashMap;
import java.util.Map;

/**
 * Core AccessibilityService managing WindowManager overlays, Edit/Play mode states,
 * visual connection lines between buttons and target pointers, and multi-stroke GestureDescription touch synthesis.
 */
public class OverlayService extends AccessibilityService {

    private static final String TAG = "ROGOverlayService";
    private static OverlayService sInstance;

    private WindowManager mWindowManager;
    private FrameLayout mRootOverlay;
    private ConnectingLinesView mLinesView;
    private View mDockView;
    private View mSettingsCard;

    private ConfigManager mConfigManager;
    private boolean mIsEditMode = false;
    private boolean mIsDockMinimized = false;

    private final Map<String, View> mButtonViews = new HashMap<>();
    private final Map<String, View> mTargetPointerViews = new HashMap<>();
    private JoystickView mLeftStickView;
    private JoystickView mRightStickView;
    private View mMoveAnchorPointerView;
    private View mAimAnchorPointerView;

    private final Handler mMainHandler = new Handler(Looper.getMainLooper());
    private boolean mIsAimSwipeBusy = false;
    private long mLastAimSwipeTime = 0;

    public static OverlayService getInstance() {
        return sInstance;
    }

    public static boolean isRunning() {
        return sInstance != null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        sInstance = this;
        mConfigManager = ConfigManager.getInstance(this);
        mWindowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
    }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        sInstance = this;
        Log.d(TAG, "ROG Overlay AccessibilityService connected.");
        initOverlayViews();
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        // No-op: service is focused on gesture synthesis and HUD overlay
    }

    @Override
    public void onInterrupt() {
        Log.w(TAG, "ROG Overlay AccessibilityService interrupted.");
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        removeOverlayViews();
        sInstance = null;
        Log.d(TAG, "ROG Overlay AccessibilityService destroyed.");
    }

    // =========================================================================
    // Overlay View Setup & WindowManager Management
    // =========================================================================

    private void initOverlayViews() {
        if (mRootOverlay != null) return;

        int layoutType = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;

        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                layoutType,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT
        );
        params.gravity = Gravity.TOP | Gravity.START;

        mRootOverlay = new FrameLayout(this);
        mRootOverlay.setAlpha(mConfigManager.getOpacity());

        // 1. Full-screen connecting lines canvas (for Edit Mode)
        mLinesView = new ConnectingLinesView(this);
        mRootOverlay.addView(mLinesView, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        // 2. Initialize buttons and joysticks from ConfigManager
        buildGamepadControls();

        // 3. Floating HUD Control Dock (Top Pill Bar)
        buildDockView();

        // 4. Sensitivity Dialog / Card (initially GONE)
        buildSettingsCard();

        mWindowManager.addView(mRootOverlay, params);
        updateEditModeUI();
    }

    private void removeOverlayViews() {
        if (mWindowManager != null && mRootOverlay != null) {
            try {
                mWindowManager.removeView(mRootOverlay);
            } catch (Exception ignored) {}
            mRootOverlay = null;
        }
    }

    // =========================================================================
    // Gamepad Controls Builder (Buttons, Pointers, Joysticks)
    // =========================================================================

    private void buildGamepadControls() {
        Map<String, ConfigManager.ButtonNode> buttons = mConfigManager.getButtonNodes();

        for (Map.Entry<String, ConfigManager.ButtonNode> entry : buttons.entrySet()) {
            final ConfigManager.ButtonNode node = entry.getValue();

            // A. Virtual Console Button
            final View btnView = createButtonView(node);
            mButtonViews.put(node.id, btnView);
            FrameLayout.LayoutParams btnParams = new FrameLayout.LayoutParams((int) node.sizeDp, (int) node.sizeDp);
            btnView.setX(node.buttonX);
            btnView.setY(node.buttonY);
            mRootOverlay.addView(btnView, btnParams);

            // B. Paired Target Pointer Node (🎯)
            final View pointerView = createTargetPointerView(node);
            mTargetPointerViews.put(node.id, pointerView);
            int pointerSize = (int) (38 * getResources().getDisplayMetrics().density);
            FrameLayout.LayoutParams ptrParams = new FrameLayout.LayoutParams(pointerSize, pointerSize);
            pointerView.setX(node.targetX - (pointerSize / 2f));
            pointerView.setY(node.targetY - (pointerSize / 2f));
            mRootOverlay.addView(pointerView, ptrParams);
        }

        // C. Left Analog Stick (Movement)
        ConfigManager.JoystickNode leftNode = mConfigManager.getJoystickNode("LEFT_STICK");
        if (leftNode != null) {
            int size = (int) (leftNode.radiusDp * 2);
            mLeftStickView = new JoystickView(this);
            mLeftStickView.setSensitivity(leftNode.sensitivityX, leftNode.sensitivityY);
            mLeftStickView.setDeadzone(leftNode.deadzone);
            mLeftStickView.setX(leftNode.centerX - leftNode.radiusDp);
            mLeftStickView.setY(leftNode.centerY - leftNode.radiusDp);
            mRootOverlay.addView(mLeftStickView, new FrameLayout.LayoutParams(size, size));

            // Movement Anchor Pointer (🎯Move)
            mMoveAnchorPointerView = createAnchorPointerView("🎯Move", Color.parseColor("#00E5FF"));
            int ptrSize = (int) (44 * getResources().getDisplayMetrics().density);
            mMoveAnchorPointerView.setX(leftNode.anchorX - (ptrSize / 2f));
            mMoveAnchorPointerView.setY(leftNode.anchorY - (ptrSize / 2f));
            mRootOverlay.addView(mMoveAnchorPointerView, new FrameLayout.LayoutParams(ptrSize, ptrSize));

            setupLeftStickListener(leftNode);
        }

        // D. Right Analog Stick (Aim / BlueStacks-style Sensitivity Engine)
        ConfigManager.JoystickNode rightNode = mConfigManager.getJoystickNode("RIGHT_STICK");
        if (rightNode != null) {
            int size = (int) (rightNode.radiusDp * 2);
            mRightStickView = new JoystickView(this);
            mRightStickView.setSensitivity(mConfigManager.getRightSensX(), mConfigManager.getRightSensY());
            mRightStickView.setDeadzone(mConfigManager.getDeadzone());
            mRightStickView.setX(rightNode.centerX - rightNode.radiusDp);
            mRightStickView.setY(rightNode.centerY - rightNode.radiusDp);
            mRootOverlay.addView(mRightStickView, new FrameLayout.LayoutParams(size, size));

            // Aim Anchor Pointer (🎯Aim)
            mAimAnchorPointerView = createAnchorPointerView("🎯Aim", Color.parseColor("#FF0040"));
            int ptrSize = (int) (44 * getResources().getDisplayMetrics().density);
            mAimAnchorPointerView.setX(rightNode.anchorX - (ptrSize / 2f));
            mAimAnchorPointerView.setY(rightNode.anchorY - (ptrSize / 2f));
            mRootOverlay.addView(mAimAnchorPointerView, new FrameLayout.LayoutParams(ptrSize, ptrSize));

            setupRightStickListener(rightNode);
        }
    }

    // =========================================================================
    // Touch Synthesis Listeners (Buttons & Analog Sticks)
    // =========================================================================

    private void setupLeftStickListener(ConfigManager.JoystickNode node) {
        mLeftStickView.setOnJoystickEventListener(new JoystickView.OnJoystickEventListener() {
            @Override
            public void onJoystickDown() {}

            @Override
            public void onJoystickMove(float deltaX, float deltaY, float normalizedX, float normalizedY,
                                       float targetOffsetX, float targetOffsetY) {
                if (mIsEditMode) return;
                // Movement touch synthesis: continuous deflection from Movement Anchor
                dispatchMovementStroke(node.anchorX, node.anchorY, deltaX, deltaY);
            }

            @Override
            public void onJoystickUp() {
                if (mIsEditMode) return;
                // Release movement stroke
                dispatchTap(node.anchorX, node.anchorY);
            }
        });

        mLeftStickView.setOnViewDragListener((v, newX, newY) -> {
            node.centerX = newX + node.radiusDp;
            node.centerY = newY + node.radiusDp;
            mLinesView.invalidate();
        });
    }

    private void setupRightStickListener(ConfigManager.JoystickNode node) {
        mRightStickView.setOnJoystickEventListener(new JoystickView.OnJoystickEventListener() {
            @Override
            public void onJoystickDown() {}

            @Override
            public void onJoystickMove(float deltaX, float deltaY, float normalizedX, float normalizedY,
                                       float targetOffsetX, float targetOffsetY) {
                if (mIsEditMode) return;
                if (targetOffsetX == 0f && targetOffsetY == 0f) return;

                // Aim Camera touch synthesis: rapid continuous swipe with X/Y multipliers
                dispatchAimSwipe(node.anchorX, node.anchorY, targetOffsetX, targetOffsetY);
            }

            @Override
            public void onJoystickUp() {
                mIsAimSwipeBusy = false;
            }
        });

        mRightStickView.setOnViewDragListener((v, newX, newY) -> {
            node.centerX = newX + node.radiusDp;
            node.centerY = newY + node.radiusDp;
            mLinesView.invalidate();
        });
    }

    /**
     * Synthesizes camera look swipe gestures centered around the Aim Anchor Point.
     * Uses rate-limiting to maintain high frequency without flooding the system input queue.
     */
    private void dispatchAimSwipe(float anchorX, float anchorY, float offsetX, float offsetY) {
        long now = System.currentTimeMillis();
        if (mIsAimSwipeBusy || (now - mLastAimSwipeTime < 32)) {
            return;
        }

        mIsAimSwipeBusy = true;
        mLastAimSwipeTime = now;

        Path swipePath = new Path();
        swipePath.moveTo(anchorX, anchorY);
        swipePath.lineTo(anchorX + offsetX, anchorY + offsetY);

        GestureDescription.StrokeDescription stroke =
                new GestureDescription.StrokeDescription(swipePath, 0, 30);

        GestureDescription.Builder builder = new GestureDescription.Builder();
        builder.addStroke(stroke);

        dispatchGesture(builder.build(), new GestureResultCallback() {
            @Override
            public void onCompleted(GestureDescription gestureDescription) {
                super.onCompleted(gestureDescription);
                mIsAimSwipeBusy = false;
            }

            @Override
            public void onCancelled(GestureDescription gestureDescription) {
                super.onCancelled(gestureDescription);
                mIsAimSwipeBusy = false;
            }
        }, mMainHandler);
    }

    /**
     * Synthesizes movement dragging from Movement Anchor Zone.
     */
    private void dispatchMovementStroke(float anchorX, float anchorY, float dx, float dy) {
        Path movePath = new Path();
        movePath.moveTo(anchorX, anchorY);
        movePath.lineTo(anchorX + dx, anchorY + dy);

        GestureDescription.StrokeDescription stroke =
                new GestureDescription.StrokeDescription(movePath, 0, 45);

        GestureDescription.Builder builder = new GestureDescription.Builder();
        builder.addStroke(stroke);
        dispatchGesture(builder.build(), null, mMainHandler);
    }

    /**
     * Synthesizes a fast tap at exact target coordinates.
     */
    public void dispatchTap(float x, float y) {
        Path tapPath = new Path();
        tapPath.moveTo(x, y);

        GestureDescription.StrokeDescription stroke =
                new GestureDescription.StrokeDescription(tapPath, 0, 40);

        GestureDescription.Builder builder = new GestureDescription.Builder();
        builder.addStroke(stroke);
        dispatchGesture(builder.build(), null, mMainHandler);
    }

    // =========================================================================
    // View Factories (Buttons & Target Pointers)
    // =========================================================================

    @SuppressLint("ClickableViewAccessibility")
    private View createButtonView(ConfigManager.ButtonNode node) {
        TextView tv = new TextView(this);
        tv.setText(node.label);
        tv.setTextColor(Color.WHITE);
        tv.setTextSize(16);
        tv.setTypeface(Typeface.DEFAULT_BOLD);
        tv.setGravity(Gravity.CENTER);

        // Styling based on button type
        GradientDrawable gd = new GradientDrawable();
        gd.setShape(GradientDrawable.OVAL);
        int btnColor = Color.parseColor("#801E1E2E");
        if (node.id.equals("A")) btnColor = Color.parseColor("#B32ECC71");
        else if (node.id.equals("B")) btnColor = Color.parseColor("#B3E74C3C");
        else if (node.id.equals("X")) btnColor = Color.parseColor("#B33498DB");
        else if (node.id.equals("Y")) btnColor = Color.parseColor("#B3F1C40F");
        else if (node.id.startsWith("L") || node.id.startsWith("R")) {
            gd.setShape(GradientDrawable.RECTANGLE);
            gd.setCornerRadius(16f);
            btnColor = Color.parseColor("#B39B59B6");
        }
        gd.setColor(btnColor);
        gd.setStroke(3, Color.parseColor("#00E5FF"));
        tv.setBackground(gd);

        tv.setOnTouchListener(new View.OnTouchListener() {
            private float downX, downY, startX, startY;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (mIsEditMode) {
                    switch (event.getActionMasked()) {
                        case MotionEvent.ACTION_DOWN:
                            downX = event.getRawX();
                            downY = event.getRawY();
                            startX = v.getX();
                            startY = v.getY();
                            return true;
                        case MotionEvent.ACTION_MOVE:
                            float nx = startX + (event.getRawX() - downX);
                            float ny = startY + (event.getRawY() - downY);
                            v.setX(nx);
                            v.setY(ny);
                            node.buttonX = nx;
                            node.buttonY = ny;
                            mLinesView.invalidate();
                            return true;
                    }
                    return false;
                }

                // In Play Mode: dispatch fast tap at paired target node coordinates
                switch (event.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        tv.setScaleX(0.9f);
                        tv.setScaleY(0.9f);
                        dispatchTap(node.targetX, node.targetY);
                        return true;
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        tv.setScaleX(1.0f);
                        tv.setScaleY(1.0f);
                        return true;
                }
                return false;
            }
        });

        return tv;
    }

    @SuppressLint("ClickableViewAccessibility")
    private View createTargetPointerView(ConfigManager.ButtonNode node) {
        TextView tv = new TextView(this);
        tv.setText("🎯" + node.label);
        tv.setTextColor(Color.WHITE);
        tv.setTextSize(11);
        tv.setTypeface(Typeface.DEFAULT_BOLD);
        tv.setGravity(Gravity.CENTER);

        GradientDrawable gd = new GradientDrawable();
        gd.setShape(GradientDrawable.OVAL);
        gd.setColor(Color.parseColor("#CCFF0040")); // Glowing Red
        gd.setStroke(2, Color.WHITE);
        tv.setBackground(gd);

        tv.setOnTouchListener(new View.OnTouchListener() {
            private float downX, downY, startX, startY;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (!mIsEditMode) return false;

                switch (event.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        downX = event.getRawX();
                        downY = event.getRawY();
                        startX = v.getX();
                        startY = v.getY();
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        float nx = startX + (event.getRawX() - downX);
                        float ny = startY + (event.getRawY() - downY);
                        v.setX(nx);
                        v.setY(ny);
                        node.targetX = nx + (v.getWidth() / 2f);
                        node.targetY = ny + (v.getHeight() / 2f);
                        mLinesView.invalidate();
                        return true;
                }
                return false;
            }
        });

        return tv;
    }

    @SuppressLint("ClickableViewAccessibility")
    private View createAnchorPointerView(String label, int color) {
        TextView tv = new TextView(this);
        tv.setText(label);
        tv.setTextColor(Color.WHITE);
        tv.setTextSize(11);
        tv.setTypeface(Typeface.DEFAULT_BOLD);
        tv.setGravity(Gravity.CENTER);

        GradientDrawable gd = new GradientDrawable();
        gd.setShape(GradientDrawable.OVAL);
        gd.setColor(color);
        gd.setStroke(2, Color.WHITE);
        tv.setBackground(gd);

        tv.setOnTouchListener(new View.OnTouchListener() {
            private float downX, downY, startX, startY;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (!mIsEditMode) return false;

                switch (event.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        downX = event.getRawX();
                        downY = event.getRawY();
                        startX = v.getX();
                        startY = v.getY();
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        float nx = startX + (event.getRawX() - downX);
                        float ny = startY + (event.getRawY() - downY);
                        v.setX(nx);
                        v.setY(ny);
                        float cx = nx + (v.getWidth() / 2f);
                        float cy = ny + (v.getHeight() / 2f);

                        if (label.contains("Move")) {
                            ConfigManager.JoystickNode jn = mConfigManager.getJoystickNode("LEFT_STICK");
                            if (jn != null) { jn.anchorX = cx; jn.anchorY = cy; }
                        } else {
                            ConfigManager.JoystickNode jn = mConfigManager.getJoystickNode("RIGHT_STICK");
                            if (jn != null) { jn.anchorX = cx; jn.anchorY = cy; }
                        }
                        mLinesView.invalidate();
                        return true;
                }
                return false;
            }
        });

        return tv;
    }

    // =========================================================================
    // Floating HUD Control Dock & Settings Panel
    // =========================================================================

    private void buildDockView() {
        float density = getResources().getDisplayMetrics().density;

        LinearLayout dock = new LinearLayout(this);
        dock.setOrientation(LinearLayout.HORIZONTAL);
        dock.setGravity(Gravity.CENTER_VERTICAL);
        dock.setPadding((int) (14 * density), (int) (8 * density), (int) (14 * density), (int) (8 * density));

        GradientDrawable dockBg = new GradientDrawable();
        dockBg.setColor(Color.parseColor("#E60F0F1A"));
        dockBg.setCornerRadius(24 * density);
        dockBg.setStroke(2, Color.parseColor("#FF0040"));
        dock.setBackground(dockBg);

        // Mode Toggle Button
        final TextView btnMode = createDockButton("🔒 Play Mode", Color.parseColor("#00E5FF"));
        btnMode.setOnClickListener(v -> {
            mIsEditMode = !mIsEditMode;
            btnMode.setText(mIsEditMode ? "⚙️ Edit Mode" : "🔒 Play Mode");
            btnMode.setTextColor(mIsEditMode ? Color.parseColor("#FF0040") : Color.parseColor("#00E5FF"));
            updateEditModeUI();
        });
        dock.addView(btnMode);

        // Sensitivity Dialog Button
        TextView btnSens = createDockButton("⚡ Sensitivity", Color.WHITE);
        btnSens.setOnClickListener(v -> {
            if (mSettingsCard != null) {
                mSettingsCard.setVisibility(mSettingsCard.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE);
            }
        });
        dock.addView(btnSens);

        // Save Profile Button
        TextView btnSave = createDockButton("💾 Save", Color.WHITE);
        btnSave.setOnClickListener(v -> {
            mConfigManager.saveAll();
            Toast.makeText(OverlayService.this, "ROG Profile Saved!", Toast.LENGTH_SHORT).show();
        });
        dock.addView(btnSave);

        // Reset Profile Button
        TextView btnReset = createDockButton("🔄 Reset", Color.WHITE);
        btnReset.setOnClickListener(v -> {
            mConfigManager.resetToDefaults(OverlayService.this);
            removeOverlayViews();
            initOverlayViews();
            Toast.makeText(OverlayService.this, "Layout Reset to Defaults", Toast.LENGTH_SHORT).show();
        });
        dock.addView(btnReset);

        mDockView = dock;
        FrameLayout.LayoutParams dockParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT);
        dockParams.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        dockParams.topMargin = (int) (20 * density);
        mRootOverlay.addView(mDockView, dockParams);
    }

    private TextView createDockButton(String text, int textColor) {
        float density = getResources().getDisplayMetrics().density;
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextColor(textColor);
        tv.setTextSize(13);
        tv.setTypeface(Typeface.DEFAULT_BOLD);
        tv.setPadding((int) (10 * density), (int) (6 * density), (int) (10 * density), (int) (6 * density));
        tv.setGravity(Gravity.CENTER);

        GradientDrawable itemBg = new GradientDrawable();
        itemBg.setColor(Color.parseColor("#33FFFFFF"));
        itemBg.setCornerRadius(16 * density);
        tv.setBackground(itemBg);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMarginEnd((int) (8 * density));
        tv.setLayoutParams(lp);
        return tv;
    }

    private void buildSettingsCard() {
        float density = getResources().getDisplayMetrics().density;

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding((int) (20 * density), (int) (16 * density), (int) (20 * density), (int) (16 * density));
        card.setVisibility(View.GONE);

        GradientDrawable cardBg = new GradientDrawable();
        cardBg.setColor(Color.parseColor("#F212121E"));
        cardBg.setCornerRadius(16 * density);
        cardBg.setStroke(2, Color.parseColor("#00E5FF"));
        card.setBackground(cardBg);

        TextView title = new TextView(this);
        title.setText("ROG Sensitivity & Calibration");
        title.setTextColor(Color.parseColor("#00E5FF"));
        title.setTextSize(16);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        card.addView(title);

        // 1. Right Stick X Sensitivity (0.5x to 8.0x)
        final TextView tvSensX = new TextView(this);
        tvSensX.setTextColor(Color.WHITE);
        tvSensX.setText("Right Stick X Sensitivity: " + String.format("%.1fx", mConfigManager.getRightSensX()));
        tvSensX.setPadding(0, (int) (8 * density), 0, 0);
        card.addView(tvSensX);

        SeekBar sbSensX = new SeekBar(this);
        sbSensX.setMax(75); // 0.5 to 8.0 (step 0.1)
        sbSensX.setProgress((int) ((mConfigManager.getRightSensX() - 0.5f) * 10));
        sbSensX.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                float val = 0.5f + (progress / 10f);
                mConfigManager.setRightSensX(val);
                tvSensX.setText("Right Stick X Sensitivity: " + String.format("%.1fx", val));
                if (mRightStickView != null) {
                    mRightStickView.setSensitivity(val, mConfigManager.getRightSensY());
                }
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });
        card.addView(sbSensX);

        // 2. Right Stick Y Sensitivity (Vertical Drag Boost) (0.5x to 8.0x)
        final TextView tvSensY = new TextView(this);
        tvSensY.setTextColor(Color.WHITE);
        tvSensY.setText("Vertical Drag Boost (Y): " + String.format("%.1fx", mConfigManager.getRightSensY()));
        tvSensY.setPadding(0, (int) (8 * density), 0, 0);
        card.addView(tvSensY);

        SeekBar sbSensY = new SeekBar(this);
        sbSensY.setMax(75);
        sbSensY.setProgress((int) ((mConfigManager.getRightSensY() - 0.5f) * 10));
        sbSensY.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                float val = 0.5f + (progress / 10f);
                mConfigManager.setRightSensY(val);
                tvSensY.setText("Vertical Drag Boost (Y): " + String.format("%.1fx", val));
                if (mRightStickView != null) {
                    mRightStickView.setSensitivity(mConfigManager.getRightSensX(), val);
                }
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });
        card.addView(sbSensY);

        // 3. Joystick Deadzone (0% to 30%)
        final TextView tvDeadzone = new TextView(this);
        tvDeadzone.setTextColor(Color.WHITE);
        tvDeadzone.setText("Joystick Deadzone: " + (int) (mConfigManager.getDeadzone() * 100) + "%");
        tvDeadzone.setPadding(0, (int) (8 * density), 0, 0);
        card.addView(tvDeadzone);

        SeekBar sbDeadzone = new SeekBar(this);
        sbDeadzone.setMax(30);
        sbDeadzone.setProgress((int) (mConfigManager.getDeadzone() * 100));
        sbDeadzone.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                float val = progress / 100f;
                mConfigManager.setDeadzone(val);
                tvDeadzone.setText("Joystick Deadzone: " + progress + "%");
                if (mLeftStickView != null) mLeftStickView.setDeadzone(val);
                if (mRightStickView != null) mRightStickView.setDeadzone(val);
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });
        card.addView(sbDeadzone);

        // 4. Overlay Opacity (10% to 100%)
        final TextView tvOpacity = new TextView(this);
        tvOpacity.setTextColor(Color.WHITE);
        tvOpacity.setText("Overlay Opacity: " + (int) (mConfigManager.getOpacity() * 100) + "%");
        tvOpacity.setPadding(0, (int) (8 * density), 0, 0);
        card.addView(tvOpacity);

        SeekBar sbOpacity = new SeekBar(this);
        sbOpacity.setMax(90);
        sbOpacity.setProgress((int) ((mConfigManager.getOpacity() - 0.1f) * 100));
        sbOpacity.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                float val = 0.1f + (progress / 100f);
                mConfigManager.setOpacity(val);
                tvOpacity.setText("Overlay Opacity: " + (int) (val * 100) + "%");
                if (mRootOverlay != null) {
                    mRootOverlay.setAlpha(val);
                }
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });
        card.addView(sbOpacity);

        // Close Button
        TextView btnClose = createDockButton("Done", Color.parseColor("#00E5FF"));
        btnClose.setOnClickListener(v -> card.setVisibility(View.GONE));
        LinearLayout.LayoutParams btnLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        btnLp.gravity = Gravity.END;
        btnLp.topMargin = (int) (12 * density);
        card.addView(btnClose, btnLp);

        mSettingsCard = card;
        FrameLayout.LayoutParams cardParams = new FrameLayout.LayoutParams(
                (int) (320 * density), FrameLayout.LayoutParams.WRAP_CONTENT);
        cardParams.gravity = Gravity.CENTER;
        mRootOverlay.addView(mSettingsCard, cardParams);
    }

    private void updateEditModeUI() {
        int visibility = mIsEditMode ? View.VISIBLE : View.GONE;

        // Toggle target pointers
        for (View ptr : mTargetPointerViews.values()) {
            ptr.setVisibility(visibility);
        }
        if (mMoveAnchorPointerView != null) mMoveAnchorPointerView.setVisibility(visibility);
        if (mAimAnchorPointerView != null) mAimAnchorPointerView.setVisibility(visibility);

        // Toggle connecting lines canvas
        if (mLinesView != null) {
            mLinesView.setVisibility(visibility);
            mLinesView.invalidate();
        }

        // Toggle edit mode in joysticks
        if (mLeftStickView != null) mLeftStickView.setEditMode(mIsEditMode);
        if (mRightStickView != null) mRightStickView.setEditMode(mIsEditMode);
    }

    // =========================================================================
    // Full-Screen Canvas for Visual Connecting Lines in Edit Mode
    // =========================================================================

    private class ConnectingLinesView extends View {
        private final Paint linePaint;
        private final Paint dotPaint;

        public ConnectingLinesView(Context context) {
            super(context);
            linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            linePaint.setStyle(Paint.Style.STROKE);
            linePaint.setStrokeWidth(3f);
            linePaint.setColor(Color.parseColor("#00E5FF")); // Glowing cyan laser
            linePaint.setPathEffect(new DashPathEffect(new float[]{12, 10}, 0));

            dotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            dotPaint.setStyle(Paint.Style.FILL);
            dotPaint.setColor(Color.parseColor("#FF0040"));
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            if (!mIsEditMode) return;

            // Draw lines from buttons to their target pointer nodes
            for (ConfigManager.ButtonNode node : mConfigManager.getButtonNodes().values()) {
                float startX = node.buttonX + (node.sizeDp / 2f);
                float startY = node.buttonY + (node.sizeDp / 2f);
                canvas.drawLine(startX, startY, node.targetX, node.targetY, linePaint);
                canvas.drawCircle(node.targetX, node.targetY, 6f, dotPaint);
            }

            // Draw line from Left Stick to Movement Anchor
            ConfigManager.JoystickNode leftNode = mConfigManager.getJoystickNode("LEFT_STICK");
            if (leftNode != null) {
                canvas.drawLine(leftNode.centerX, leftNode.centerY, leftNode.anchorX, leftNode.anchorY, linePaint);
                canvas.drawCircle(leftNode.anchorX, leftNode.anchorY, 8f, dotPaint);
            }

            // Draw line from Right Stick to Aim Anchor
            ConfigManager.JoystickNode rightNode = mConfigManager.getJoystickNode("RIGHT_STICK");
            if (rightNode != null) {
                canvas.drawLine(rightNode.centerX, rightNode.centerY, rightNode.anchorX, rightNode.anchorY, linePaint);
                canvas.drawCircle(rightNode.anchorX, rightNode.anchorY, 8f, dotPaint);
            }
        }
    }
}
