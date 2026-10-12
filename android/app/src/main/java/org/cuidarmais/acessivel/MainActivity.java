package org.cuidarmais.acessivel;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.ViewGroup;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import androidx.core.content.ContextCompat;
import androidx.core.view.WindowCompat;

public class MainActivity extends Activity {
    private static final int REQUEST_NOTIFICATIONS = 101;
    private static final int REQUEST_EXACT_ALARM = 102;
    private static final int REQUEST_GLUCOSE_NOTIFICATIONS = 103;
    private static final int REQUEST_BLOOD_PRESSURE_NOTIFICATIONS = 104;

    private WebView webView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), true);

        webView = new WebView(this);
        webView.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                String scheme = request.getUrl().getScheme();
                String path = request.getUrl().getPath();
                return !("file".equals(scheme) && path != null && path.startsWith("/android_asset/"))
                        && !"data".equals(scheme);
            }
        });
        webView.setWebChromeClient(new WebChromeClient());
        webView.addJavascriptInterface(new DailyReminderBridge(this), "AndroidReminders");
        setContentView(webView);
        webView.loadUrl("file:///android_asset/index.html");
    }

    public void scheduleDailyReminder(String id, int hour, int minute) {
        DailyReminderScheduler.save(this, id, hour, minute);
        runOnUiThread(this::requestReminderPermissions);
    }

    public void cancelDailyReminder(String id) {
        DailyReminderScheduler.cancel(this, id);
    }

    public void scheduleAppointmentReminder(String id, long appointmentAtMillis, int reminderMinutes) {
        DailyReminderScheduler.saveAppointmentReminder(this, id, appointmentAtMillis, reminderMinutes);
        runOnUiThread(this::requestReminderPermissions);
    }

    public void cancelAppointmentReminder(String id) {
        DailyReminderScheduler.cancelAppointmentReminder(this, id);
    }

    public void showGlucoseAlert() {
        runOnUiThread(() -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                    && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},
                        REQUEST_GLUCOSE_NOTIFICATIONS);
                return;
            }
            DailyReminderScheduler.showGlucoseAlert(this);
        });
    }

    public void showBloodPressureAlert() {
        runOnUiThread(() -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                    && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},
                        REQUEST_BLOOD_PRESSURE_NOTIFICATIONS);
                return;
            }
            DailyReminderScheduler.showBloodPressureAlert(this);
        });
    }

    private void requestReminderPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQUEST_NOTIFICATIONS);
            return;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                && !DailyReminderScheduler.canScheduleExactAlarms(this)) {
            Intent intent = new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                    Uri.parse("package:" + getPackageName()));
            try {
                startActivityForResult(intent, REQUEST_EXACT_ALARM);
            } catch (Exception exception) {
                reportPermissionDenied();
            }
            return;
        }

        DailyReminderScheduler.scheduleAll(this);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_GLUCOSE_NOTIFICATIONS) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                DailyReminderScheduler.showGlucoseAlert(this);
            } else {
                Toast.makeText(this, "Permita notificações para receber alertas de glicemia.",
                        Toast.LENGTH_LONG).show();
                if (webView != null) {
                    webView.post(() -> webView.evaluateJavascript(
                            "window.androidGlucoseNotificationPermissionDenied && window.androidGlucoseNotificationPermissionDenied()",
                            null));
                }
            }
            return;
        }
        if (requestCode == REQUEST_BLOOD_PRESSURE_NOTIFICATIONS) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                DailyReminderScheduler.showBloodPressureAlert(this);
            } else {
                Toast.makeText(this, "Permita notificações para receber alertas de pressão arterial.",
                        Toast.LENGTH_LONG).show();
                if (webView != null) {
                    webView.post(() -> webView.evaluateJavascript(
                            "window.androidBloodPressureNotificationPermissionDenied && window.androidBloodPressureNotificationPermissionDenied()",
                            null));
                }
            }
            return;
        }
        if (requestCode != REQUEST_NOTIFICATIONS) return;
        if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            requestReminderPermissions();
        } else {
            reportPermissionDenied();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQUEST_EXACT_ALARM) return;
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S
                || DailyReminderScheduler.canScheduleExactAlarms(this)) {
            DailyReminderScheduler.scheduleAll(this);
        } else {
            reportPermissionDenied();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (webView != null
                && (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU
                || ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED)
                && DailyReminderScheduler.canScheduleExactAlarms(this)) {
            DailyReminderScheduler.scheduleAll(this);
        }
    }

    private void reportPermissionDenied() {
        Toast.makeText(this,
                "Permita notificações e alarmes exatos para ativar os lembretes.",
                Toast.LENGTH_LONG).show();
        if (webView != null) {
            webView.post(() -> webView.evaluateJavascript(
                    "window.androidReminderPermissionDenied && window.androidReminderPermissionDenied()",
                    null));
        }
    }
}