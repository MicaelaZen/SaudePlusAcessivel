package org.cuidarmais.acessivel;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import org.json.JSONObject;

public class DailyReminderReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null) return;
        String id = intent.getStringExtra(DailyReminderScheduler.EXTRA_ID);
        if (id == null) return;

        if (DailyReminderScheduler.ACTION_APPOINTMENT_REMIND.equals(intent.getAction())) {
            JSONObject appointment = DailyReminderScheduler.findAppointmentReminder(context, id);
            if (appointment == null) return;
            if (appointment.optLong("appointmentAt") > System.currentTimeMillis()) {
                DailyReminderScheduler.showAppointmentNotification(context, id);
            }
            DailyReminderScheduler.cancelAppointmentReminder(context, id);
            return;
        }

        if (!DailyReminderScheduler.ACTION_REMIND.equals(intent.getAction())) return;
        JSONObject reminder = DailyReminderScheduler.find(context, id);
        if (reminder == null) return;

        DailyReminderScheduler.showNotification(context, id);
        DailyReminderScheduler.schedule(context, id,
                reminder.optInt("hour"), reminder.optInt("minute"));
    }
}