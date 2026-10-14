package com.custom.rogcontrols;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.DisplayMetrics;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;

/**
 * Handles persistence and retrieval of virtual gamepad configurations,
 * button coordinates, paired target pointer nodes, and X/Y sensitivity multipliers.
 */
public class ConfigManager {

    private static final String PREF_NAME = "rog_controls_config";
    private static final String KEY_RIGHT_SENSITIVITY_X = "right_sens_x";
    private static final String KEY_RIGHT_SENSITIVITY_Y = "right_sens_y";
    private static final String KEY_DEADZONE = "deadzone";
    private static final String KEY_OPACITY = "overlay_opacity";
    private static final String KEY_BUTTON_SCALE = "button_scale";
    private static final String KEY_BUTTON_MAPPINGS_JSON = "button_mappings_json";
    private static final String KEY_JOYSTICK_MAPPINGS_JSON = "joystick_mappings_json";

    public static class ButtonNode {
        public String id;
        public String label;
        public float buttonX;
        public float buttonY;
        public float targetX;
        public float targetY;
        public float sizeDp;

        public ButtonNode() {}

        public ButtonNode(String id, String label, float buttonX, float buttonY, float targetX, float targetY, float sizeDp) {
            this.id = id;
            this.label = label;
            this.buttonX = buttonX;
            this.buttonY = buttonY;
            this.targetX = targetX;
            this.targetY = targetY;
            this.sizeDp = sizeDp;
        }
    }

    public static class JoystickNode {
        public String id;
        public float centerX;
        public float centerY;
        public float anchorX;
        public float anchorY;
        public float radiusDp;
        public float sensitivityX;
        public float sensitivityY;
        public float deadzone;

        public JoystickNode() {}

        public JoystickNode(String id, float centerX, float centerY, float anchorX, float anchorY,
                            float radiusDp, float sensitivityX, float sensitivityY, float deadzone) {
            this.id = id;
            this.centerX = centerX;
            this.centerY = centerY;
            this.anchorX = anchorX;
            this.anchorY = anchorY;
            this.radiusDp = radiusDp;
            this.sensitivityX = sensitivityX;
            this.sensitivityY = sensitivityY;
            this.deadzone = deadzone;
        }
    }

    private static ConfigManager sInstance;
    private final SharedPreferences mPrefs;
    private final Gson mGson;

    private float mRightSensX;
    private float mRightSensY;
    private float mDeadzone;
    private float mOpacity;
    private float mButtonScale;

    private Map<String, ButtonNode> mButtonNodes = new HashMap<>();
    private Map<String, JoystickNode> mJoystickNodes = new HashMap<>();

    public static synchronized ConfigManager getInstance(Context context) {
        if (sInstance == null) {
            sInstance = new ConfigManager(context.getApplicationContext());
        }
        return sInstance;
    }

    private ConfigManager(Context context) {
        mPrefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        mGson = new Gson();
        loadAll(context);
    }

    public void loadAll(Context context) {
        mRightSensX = mPrefs.getFloat(KEY_RIGHT_SENSITIVITY_X, 1.8f);
        mRightSensY = mPrefs.getFloat(KEY_RIGHT_SENSITIVITY_Y, 2.4f); // Vertical boost for headshot drag
        mDeadzone = mPrefs.getFloat(KEY_DEADZONE, 0.10f); // 10%
        mOpacity = mPrefs.getFloat(KEY_OPACITY, 0.85f);
        mButtonScale = mPrefs.getFloat(KEY_BUTTON_SCALE, 1.0f);

        String buttonJson = mPrefs.getString(KEY_BUTTON_MAPPINGS_JSON, null);
        String joystickJson = mPrefs.getString(KEY_JOYSTICK_MAPPINGS_JSON, null);

        if (buttonJson != null && joystickJson != null) {
            try {
                Type buttonType = new TypeToken<Map<String, ButtonNode>>() {}.getType();
                Type joystickType = new TypeToken<Map<String, JoystickNode>>() {}.getType();
                mButtonNodes = mGson.fromJson(buttonJson, buttonType);
                mJoystickNodes = mGson.fromJson(joystickJson, joystickType);
            } catch (Exception e) {
                initDefaultLayout(context);
            }
        } else {
            initDefaultLayout(context);
        }
    }

    public void saveAll() {
        SharedPreferences.Editor editor = mPrefs.edit();
        editor.putFloat(KEY_RIGHT_SENSITIVITY_X, mRightSensX);
        editor.putFloat(KEY_RIGHT_SENSITIVITY_Y, mRightSensY);
        editor.putFloat(KEY_DEADZONE, mDeadzone);
        editor.putFloat(KEY_OPACITY, mOpacity);
        editor.putFloat(KEY_BUTTON_SCALE, mButtonScale);
        editor.putString(KEY_BUTTON_MAPPINGS_JSON, mGson.toJson(mButtonNodes));
        editor.putString(KEY_JOYSTICK_MAPPINGS_JSON, mGson.toJson(mJoystickNodes));
        editor.apply();
    }

    public void resetToDefaults(Context context) {
        initDefaultLayout(context);
        mRightSensX = 1.8f;
        mRightSensY = 2.4f;
        mDeadzone = 0.10f;
        mOpacity = 0.85f;
        mButtonScale = 1.0f;
        saveAll();
    }

    public void initDefaultLayout(Context context) {
        DisplayMetrics dm = context.getResources().getDisplayMetrics();
        int width = dm.widthPixels;
        int height = dm.heightPixels;

        mButtonNodes.clear();
        mJoystickNodes.clear();

        // Standard Landscape ROG Layout defaults
        float density = dm.density;
        float btnSize = 56 * density;

        // Action Buttons (ABXY diamond layout on the right)
        float abxyCenterX = width - (140 * density);
        float abxyCenterY = height - (140 * density);
        float spacing = 48 * density;

        mButtonNodes.put("A", new ButtonNode("A", "A", abxyCenterX, abxyCenterY + spacing,
                width * 0.88f, height * 0.82f, btnSize)); // Bottom
        mButtonNodes.put("B", new ButtonNode("B", "B", abxyCenterX + spacing, abxyCenterY,
                width * 0.94f, height * 0.70f, btnSize)); // Right
        mButtonNodes.put("X", new ButtonNode("X", "X", abxyCenterX - spacing, abxyCenterY,
                width * 0.82f, height * 0.70f, btnSize)); // Left
        mButtonNodes.put("Y", new ButtonNode("Y", "Y", abxyCenterX, abxyCenterY - spacing,
                width * 0.88f, height * 0.58f, btnSize)); // Top

        // Shoulder Triggers
        mButtonNodes.put("L1", new ButtonNode("L1", "L1", 40 * density, 80 * density,
                width * 0.15f, height * 0.25f, 64 * density));
        mButtonNodes.put("R1", new ButtonNode("R1", "R1", width - (104 * density), 80 * density,
                width * 0.85f, height * 0.25f, 64 * density));

        // D-Pad buttons
        float dpadCenterX = 130 * density;
        float dpadCenterY = height - (240 * density);
        float dpadSpacing = 42 * density;

        mButtonNodes.put("DPAD_UP", new ButtonNode("DPAD_UP", "↑", dpadCenterX, dpadCenterY - dpadSpacing,
                width * 0.20f, height * 0.60f, 44 * density));
        mButtonNodes.put("DPAD_DOWN", new ButtonNode("DPAD_DOWN", "↓", dpadCenterX, dpadCenterY + dpadSpacing,
                width * 0.20f, height * 0.80f, 44 * density));
        mButtonNodes.put("DPAD_LEFT", new ButtonNode("DPAD_LEFT", "←", dpadCenterX - dpadSpacing, dpadCenterY,
                width * 0.10f, height * 0.70f, 44 * density));
        mButtonNodes.put("DPAD_RIGHT", new ButtonNode("DPAD_RIGHT", "→", dpadCenterX + dpadSpacing, dpadCenterY,
                width * 0.30f, height * 0.70f, 44 * density));

        // Left Joystick (Movement)
        mJoystickNodes.put("LEFT_STICK", new JoystickNode(
                "LEFT_STICK",
                140 * density,
                height - (120 * density),
                width * 0.20f, // Movement anchor zone in HUD
                height * 0.75f,
                70 * density,
                1.0f,
                1.0f,
                mDeadzone
        ));

        // Right Joystick (Aim Camera with BlueStacks-style X/Y sensitivity)
        mJoystickNodes.put("RIGHT_STICK", new JoystickNode(
                "RIGHT_STICK",
                width - (220 * density),
                height - (180 * density),
                width * 0.70f, // Aim camera look anchor on the right
                height * 0.50f,
                70 * density,
                mRightSensX,
                mRightSensY,
                mDeadzone
        ));
    }

    public float getRightSensX() { return mRightSensX; }
    public void setRightSensX(float val) { mRightSensX = Math.max(0.1f, Math.min(10.0f, val)); }

    public float getRightSensY() { return mRightSensY; }
    public void setRightSensY(float val) { mRightSensY = Math.max(0.1f, Math.min(10.0f, val)); }

    public float getDeadzone() { return mDeadzone; }
    public void setDeadzone(float val) { mDeadzone = Math.max(0.0f, Math.min(0.35f, val)); }

    public float getOpacity() { return mOpacity; }
    public void setOpacity(float val) { mOpacity = Math.max(0.1f, Math.min(1.0f, val)); }

    public float getButtonScale() { return mButtonScale; }
    public void setButtonScale(float val) { mButtonScale = Math.max(0.5f, Math.min(2.0f, val)); }

    public Map<String, ButtonNode> getButtonNodes() { return mButtonNodes; }
    public Map<String, JoystickNode> getJoystickNodes() { return mJoystickNodes; }

    public ButtonNode getButtonNode(String id) { return mButtonNodes.get(id); }
    public JoystickNode getJoystickNode(String id) { return mJoystickNodes.get(id); }
}
