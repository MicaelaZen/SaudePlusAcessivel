package org.cuidarmais.acessivel;

import android.webkit.JavascriptInterface;

final class DailyReminderBridge {
    private final MainActivity activity;

    DailyReminderBridge(MainActivity activity) {
        this.activity = activity;
    }

    @JavascriptInterface
    public void scheduleDailyReminder(String id, String time) {
        String[] parts = time.split(":");
        if (parts.length != 2) return;
        try {
            int hour = Integer.parseInt(parts[0]);
            int minute = Integer.parseInt(parts[1]);
            if (hour < 0 || hour > 23 || minute < 0 || minute > 59) return;
            activity.scheduleDailyReminder(id, hour, minute);
        } catch (NumberFormatException ignored) {
            // Ignore invalid values from the WebView.
        }
    }

    @JavascriptInterface
    public void cancelDailyReminder(String id) {
        activity.cancelDailyReminder(id);
    }

    @JavascriptInterface
    public void scheduleAppointmentReminder(String id, long appointmentAtMillis, int reminderMinutes) {
        if (reminderMinutes <= 0 || reminderMinutes > 1440) return;
        activity.scheduleAppointmentReminder(id, appointmentAtMillis, reminderMinutes);
    }

    @JavascriptInterface
    public void cancelAppointmentReminder(String id) {
        activity.cancelAppointmentReminder(id);
    }

    @JavascriptInterface
    public void showGlucoseAlert() {
        activity.showGlucoseAlert();
    }

    @JavascriptInterface
    public void showBloodPressureAlert() {
        activity.showBloodPressureAlert();
    }
}