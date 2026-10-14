package com.custom.rogcontrols;

import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.graphics.SweepGradient;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

/**
 * Custom View rendering an ASUS ROG RGB aesthetic outer neon ring
 * and an inner movable analog thumbstick knob.
 * Implements BlueStacks-style X/Y sensitivity calculation and deadzone thresholds.
 */
public class JoystickView extends View {

    public interface OnJoystickEventListener {
        void onJoystickDown();
        void onJoystickMove(float deltaX, float deltaY, float normalizedX, float normalizedY,
                            float targetOffsetX, float targetOffsetY);
        void onJoystickUp();
    }

    public interface OnViewDragListener {
        void onDrag(View view, float newX, float newY);
    }

    private Paint mOuterRingPaint;
    private Paint mOuterGlowPaint;
    private Paint mKnobPaint;
    private Paint mKnobBorderPaint;
    private Paint mKnobCenterPaint;
    private Paint mDeadzonePaint;
    private Paint mCrosshairPaint;

    private float mCenterX;
    private float mCenterY;
    private float mMaxRadius;
    private float mKnobRadius;
    private float mKnobX;
    private float mKnobY;

    private float mDeltaX;
    private float mDeltaY;

    // Mathematical parameters
    private float mSensitivityX = 1.0f;
    private float mSensitivityY = 1.0f;
    private float mDeadzone = 0.10f; // 10%
    private boolean mIsEditMode = false;

    private float mRgbAngle = 0f;
    private ValueAnimator mRgbAnimator;

    private OnJoystickEventListener mJoystickListener;
    private OnViewDragListener mDragListener;

    private float mTouchDownX;
    private float mTouchDownY;
    private float mViewStartX;
    private float mViewStartY;

    public JoystickView(Context context) {
        super(context);
        init();
    }

    public JoystickView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public JoystickView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        mOuterRingPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mOuterRingPaint.setStyle(Paint.Style.STROKE);
        mOuterRingPaint.setStrokeWidth(5f);

        mOuterGlowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mOuterGlowPaint.setStyle(Paint.Style.STROKE);
        mOuterGlowPaint.setStrokeWidth(12f);
        mOuterGlowPaint.setAlpha(80);

        mKnobPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mKnobPaint.setStyle(Paint.Style.FILL);

        mKnobBorderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mKnobBorderPaint.setStyle(Paint.Style.STROKE);
        mKnobBorderPaint.setStrokeWidth(3.5f);
        mKnobBorderPaint.setColor(Color.parseColor("#FF0040")); // ROG Neon Red

        mKnobCenterPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mKnobCenterPaint.setStyle(Paint.Style.FILL);
        mKnobCenterPaint.setColor(Color.parseColor("#00E5FF")); // ROG Cyan

        mDeadzonePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mDeadzonePaint.setStyle(Paint.Style.STROKE);
        mDeadzonePaint.setStrokeWidth(1.5f);
        mDeadzonePaint.setColor(Color.argb(80, 255, 255, 255));

        mCrosshairPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mCrosshairPaint.setStyle(Paint.Style.STROKE);
        mCrosshairPaint.setStrokeWidth(2f);
        mCrosshairPaint.setColor(Color.argb(120, 0, 229, 255));

        // ROG RGB Ring rotation animation
        mRgbAnimator = ValueAnimator.ofFloat(0f, 360f);
        mRgbAnimator.setDuration(4000);
        mRgbAnimator.setRepeatCount(ValueAnimator.INFINITE);
        mRgbAnimator.addUpdateListener(anim -> {
            mRgbAngle = (float) anim.getAnimatedValue();
            invalidate();
        });
        mRgbAnimator.start();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (mRgbAnimator != null) {
            mRgbAnimator.cancel();
        }
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        mCenterX = w / 2f;
        mCenterY = h / 2f;
        mMaxRadius = Math.min(w, h) / 2f - 16f;
        mKnobRadius = mMaxRadius * 0.38f;
        resetKnobPosition();
    }

    private void resetKnobPosition() {
        mKnobX = mCenterX;
        mKnobY = mCenterY;
        mDeltaX = 0f;
        mDeltaY = 0f;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        if (mMaxRadius <= 0) return;

        // 1. Draw ASUS ROG Cyberpunk RGB Outer Ring
        int[] rgbColors = new int[]{
                Color.parseColor("#FF0040"), // ROG Red
                Color.parseColor("#9D00FF"), // Purple
                Color.parseColor("#00E5FF"), // Cyber Cyan
                Color.parseColor("#00FF66"), // Neon Green
                Color.parseColor("#FF0040")  // ROG Red
        };
        float[] positions = new float[]{0f, 0.25f, 0.5f, 0.75f, 1f};

        canvas.save();
        canvas.rotate(mRgbAngle, mCenterX, mCenterY);
        SweepGradient sweepGradient = new SweepGradient(mCenterX, mCenterY, rgbColors, positions);
        mOuterRingPaint.setShader(sweepGradient);
        mOuterGlowPaint.setShader(sweepGradient);

        canvas.drawCircle(mCenterX, mCenterY, mMaxRadius, mOuterGlowPaint);
        canvas.drawCircle(mCenterX, mCenterY, mMaxRadius, mOuterRingPaint);
        canvas.restore();

        // 2. Draw Crosshair markings
        float tickLen = mMaxRadius * 0.15f;
        canvas.drawLine(mCenterX - mMaxRadius, mCenterY, mCenterX - mMaxRadius + tickLen, mCenterY, mCrosshairPaint);
        canvas.drawLine(mCenterX + mMaxRadius - tickLen, mCenterY, mCenterX + mMaxRadius, mCenterY, mCrosshairPaint);
        canvas.drawLine(mCenterX, mCenterY - mMaxRadius, mCenterX, mCenterY - mMaxRadius + tickLen, mCrosshairPaint);
        canvas.drawLine(mCenterX, mCenterY + mMaxRadius - tickLen, mCenterX, mCenterY + mMaxRadius, mCrosshairPaint);

        // 3. Draw Deadzone Circle indicator
        if (mDeadzone > 0f) {
            float deadzoneRadius = mMaxRadius * mDeadzone;
            canvas.drawCircle(mCenterX, mCenterY, deadzoneRadius, mDeadzonePaint);
        }

        // 4. Draw Inner Movable Thumbstick Knob with metallic gradient
        RadialGradient knobGrad = new RadialGradient(
                mKnobX - mKnobRadius * 0.25f,
                mKnobY - mKnobRadius * 0.25f,
                mKnobRadius,
                Color.parseColor("#2A2A3E"),
                Color.parseColor("#12121E"),
                Shader.TileMode.CLAMP
        );
        mKnobPaint.setShader(knobGrad);
        canvas.drawCircle(mKnobX, mKnobY, mKnobRadius, mKnobPaint);
        canvas.drawCircle(mKnobX, mKnobY, mKnobRadius, mKnobBorderPaint);

        // Knob center neon dot
        canvas.drawCircle(mKnobX, mKnobY, mKnobRadius * 0.25f, mKnobCenterPaint);
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (mIsEditMode) {
            // Drag-to-reposition in Edit Mode
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    mTouchDownX = event.getRawX();
                    mTouchDownY = event.getRawY();
                    mViewStartX = getX();
                    mViewStartY = getY();
                    return true;
                case MotionEvent.ACTION_MOVE:
                    float dx = event.getRawX() - mTouchDownX;
                    float dy = event.getRawY() - mTouchDownY;
                    float newX = mViewStartX + dx;
                    float newY = mViewStartY + dy;
                    setX(newX);
                    setY(newY);
                    if (mDragListener != null) {
                        mDragListener.onDrag(this, newX, newY);
                    }
                    return true;
            }
            return super.onTouchEvent(event);
        }

        // In Play Mode: Compute Analog displacement and BlueStacks-Style Multipliers
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                if (mJoystickListener != null) {
                    mJoystickListener.onJoystickDown();
                }
                processAnalogInput(event.getX(), event.getY());
                return true;

            case MotionEvent.ACTION_MOVE:
                processAnalogInput(event.getX(), event.getY());
                return true;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                resetKnobPosition();
                invalidate();
                if (mJoystickListener != null) {
                    mJoystickListener.onJoystickUp();
                }
                return true;
        }
        return super.onTouchEvent(event);
    }

    /**
     * Executes the mathematical specifications:
     * 1. Radial displacement: Δx = Touch_x - C_x, Δy = Touch_y - C_y
     * 2. Polar clamping: D = sqrt(Δx² + Δy²)
     * 3. Deadzone filter: mag = D / R_max
     * 4. Multipliers: Target_x = Anchor_x + (Δx * S_x), Target_y = Anchor_y + (Δy * S_y)
     */
    private void processAnalogInput(float touchX, float touchY) {
        float rawDx = touchX - mCenterX;
        float rawDy = touchY - mCenterY;
        float distance = (float) Math.sqrt(rawDx * rawDx + rawDy * rawDy);

        if (distance > mMaxRadius) {
            float ratio = mMaxRadius / distance;
            mDeltaX = rawDx * ratio;
            mDeltaY = rawDy * ratio;
        } else {
            mDeltaX = rawDx;
            mDeltaY = rawDy;
        }

        mKnobX = mCenterX + mDeltaX;
        mKnobY = mCenterY + mDeltaY;
        invalidate();

        // Normalization
        float normalizedMag = (float) Math.sqrt(mDeltaX * mDeltaX + mDeltaY * mDeltaY) / mMaxRadius;
        float normalizedX = mDeltaX / mMaxRadius;
        float normalizedY = mDeltaY / mMaxRadius;

        if (normalizedMag < mDeadzone) {
            // Inside deadzone
            if (mJoystickListener != null) {
                mJoystickListener.onJoystickMove(0f, 0f, 0f, 0f, 0f, 0f);
            }
        } else {
            // Apply X/Y Sensitivity Multipliers
            float targetOffsetX = mDeltaX * mSensitivityX;
            float targetOffsetY = mDeltaY * mSensitivityY;

            if (mJoystickListener != null) {
                mJoystickListener.onJoystickMove(mDeltaX, mDeltaY, normalizedX, normalizedY,
                        targetOffsetX, targetOffsetY);
            }
        }
    }

    public void setSensitivity(float sx, float sy) {
        mSensitivityX = sx;
        mSensitivityY = sy;
    }

    public void setDeadzone(float deadzone) {
        mDeadzone = deadzone;
        invalidate();
    }

    public void setEditMode(boolean editMode) {
        mIsEditMode = editMode;
        if (editMode) {
            resetKnobPosition();
            invalidate();
        }
    }

    public boolean isEditMode() {
        return mIsEditMode;
    }

    public void setOnJoystickEventListener(OnJoystickEventListener listener) {
        mJoystickListener = listener;
    }

    public void setOnViewDragListener(OnViewDragListener listener) {
        mDragListener = listener;
    }
}
