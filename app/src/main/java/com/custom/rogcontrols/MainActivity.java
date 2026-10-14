package com.custom.rogcontrols;

import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.accessibility.AccessibilityManager;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.custom.rogcontrols.databinding.ActivityMainBinding;

import java.util.List;

/**
 * Dark ROG-themed onboarding dashboard providing live status indicators
 * for WindowManager overlay permission and AccessibilityService status.
 */
public class MainActivity extends AppCompatActivity {

    private ActivityMainBinding mBinding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mBinding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(mBinding.getRoot());

        setupListeners();
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateStatus();
    }

    private void setupListeners() {
        // Overlay Permission Button
        mBinding.btnToggleOverlay.setOnClickListener(v -> {
            if (!hasOverlayPermission()) {
                requestOverlayPermission();
            } else if (!isAccessibilityServiceEnabled()) {
                requestAccessibilityPermission();
            } else {
                Toast.makeText(this, "ROG Overlay is running! Switch to your game.", Toast.LENGTH_LONG).show();
            }
        });

        // Accessibility Settings Button
        mBinding.btnAccessibility.setOnClickListener(v -> requestAccessibilityPermission());

        // Reset Settings Button
        mBinding.btnSettings.setOnClickListener(v -> {
            ConfigManager.getInstance(this).resetToDefaults(this);
            Toast.makeText(this, "Layout and sensitivities reset to ROG defaults", Toast.LENGTH_SHORT).show();
        });
    }

    private void updateStatus() {
        boolean overlayOk = hasOverlayPermission();
        boolean a11yOk = isAccessibilityServiceEnabled();

        // 1. Overlay status
        if (overlayOk) {
            mBinding.tvOverlayStatus.setText("• Overlay Permission: GRANTED ✓");
            mBinding.tvOverlayStatus.setTextColor(Color.parseColor("#00E5FF")); // ROG Cyan
        } else {
            mBinding.tvOverlayStatus.setText("• Overlay Permission: NOT GRANTED ✗");
            mBinding.tvOverlayStatus.setTextColor(Color.parseColor("#FF0040")); // ROG Red
        }

        // 2. Accessibility status
        if (a11yOk) {
            mBinding.tvAccessibilityStatus.setText("• Accessibility Service: ACTIVE ✓");
            mBinding.tvAccessibilityStatus.setTextColor(Color.parseColor("#00E5FF"));
        } else {
            mBinding.tvAccessibilityStatus.setText("• Accessibility Service: NOT ENABLED ✗");
            mBinding.tvAccessibilityStatus.setTextColor(Color.parseColor("#FF0040"));
        }

        // 3. Primary action button state
        if (!overlayOk) {
            mBinding.btnToggleOverlay.setText("GRANT OVERLAY PERMISSION");
            mBinding.btnToggleOverlay.setBackgroundColor(Color.parseColor("#FF0040"));
        } else if (!a11yOk) {
            mBinding.btnToggleOverlay.setText("ENABLE ACCESSIBILITY SERVICE");
            mBinding.btnToggleOverlay.setBackgroundColor(Color.parseColor("#FF6600"));
        } else {
            mBinding.btnToggleOverlay.setText("OVERLAY READY & ACTIVE");
            mBinding.btnToggleOverlay.setBackgroundColor(Color.parseColor("#00C853"));
        }
    }

    private boolean hasOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return Settings.canDrawOverlays(this);
        }
        return true;
    }

    private void requestOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName()));
            try {
                startActivity(intent);
            } catch (Exception e) {
                Intent fallback = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION);
                startActivity(fallback);
            }
        }
    }

    private boolean isAccessibilityServiceEnabled() {
        if (OverlayService.isRunning()) {
            return true;
        }

        AccessibilityManager am = (AccessibilityManager) getSystemService(Context.ACCESSIBILITY_SERVICE);
        if (am == null) return false;

        List<AccessibilityServiceInfo> enabledServices =
                am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK);

        String myServiceId = getPackageName() + "/" + OverlayService.class.getName();

        for (AccessibilityServiceInfo info : enabledServices) {
            if (info.getId() != null && info.getId().contains(getPackageName())) {
                return true;
            }
        }

        // Secondary check via Settings.Secure
        String enabledServicesSetting = Settings.Secure.getString(
                getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);

        if (!TextUtils.isEmpty(enabledServicesSetting)) {
            TextUtils.SimpleStringSplitter colonSplitter = new TextUtils.SimpleStringSplitter(':');
            colonSplitter.setString(enabledServicesSetting);
            while (colonSplitter.hasNext()) {
                String componentName = colonSplitter.next();
                if (componentName.equalsIgnoreCase(myServiceId) || componentName.contains(getPackageName())) {
                    return true;
                }
            }
        }

        return false;
    }

    private void requestAccessibilityPermission() {
        Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
        startActivity(intent);
        Toast.makeText(this, "Find 'ROG Virtual Console' and turn it ON", Toast.LENGTH_LONG).show();
    }
}
