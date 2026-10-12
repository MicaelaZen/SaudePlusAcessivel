package org.cuidarmais.acessivel;

import android.app.AlarmManager;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Calendar;

final class DailyReminderScheduler {
    static final String ACTION_REMIND = "org.cuidarmais.acessivel.DAILY_REMINDER";
    static final String ACTION_APPOINTMENT_REMIND = "org.cuidarmais.acessivel.APPOINTMENT_REMINDER";
    static final String EXTRA_ID = "reminder_id";
    private static final String PREFS = "cuidar_local_reminders";
    private static final String REMINDERS = "reminders";
    private static final String APPOINTMENT_REMINDERS = "appointment_reminders";
    private static final String CHANNEL_ID = "medication_reminders";
    private static final String APPOINTMENT_CHANNEL_ID = "appointment_reminders_channel";
    private static final String GLUCOSE_CHANNEL_ID = "glucose_alerts";
    private static final String BLOOD_PRESSURE_CHANNEL_ID = "blood_pressure_alerts";

    private DailyReminderScheduler() {}

    static boolean canScheduleExactAlarms(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true;
        AlarmManager manager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        return manager != null && manager.canScheduleExactAlarms();
    }

    static boolean hasNotificationPermission(Context context) {
        return (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU
                || ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED)
                && NotificationManagerCompat.from(context).areNotificationsEnabled();
    }

    static void save(Context context, String id, int hour, int minute) {
        JSONArray current = readReminders(context);
        JSONArray updated = new JSONArray();
        for (int index = 0; index < current.length(); index++) {
            JSONObject item = current.optJSONObject(index);
            if (item != null && !id.equals(item.optString("id"))) updated.put(item);
        }
        JSONObject reminder = new JSONObject();
        try {
            reminder.put("id", id);
            reminder.put("hour", hour);
            reminder.put("minute", minute);
            updated.put(reminder);
            writeReminders(context, updated);
        } catch (JSONException ignored) {
            return;
        }
        schedule(context, id, hour, minute);
    }

    static void cancel(Context context, String id) {
        JSONArray current = readReminders(context);
        JSONArray updated = new JSONArray();
        for (int index = 0; index < current.length(); index++) {
            JSONObject item = current.optJSONObject(index);
            if (item != null && !id.equals(item.optString("id"))) updated.put(item);
        }
        writeReminders(context, updated);

        AlarmManager manager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (manager != null) manager.cancel(pendingIntent(context, id));
    }

    static void saveAppointmentReminder(Context context, String id, long appointmentAtMillis, int reminderMinutes) {
        JSONArray current = readAppointmentReminders(context);
        JSONArray updated = new JSONArray();
        for (int index = 0; index < current.length(); index++) {
            JSONObject item = current.optJSONObject(index);
            if (item != null && !id.equals(item.optString("id"))) updated.put(item);
        }
        if (appointmentAtMillis <= System.currentTimeMillis() || reminderMinutes <= 0 || reminderMinutes > 1440) {
            writeAppointmentReminders(context, updated);
            cancelAppointmentAlarm(context, id);
            return;
        }

        JSONObject reminder = new JSONObject();
        try {
            reminder.put("id", id);
            reminder.put("appointmentAt", appointmentAtMillis);
            reminder.put("reminderMinutes", reminderMinutes);
            updated.put(reminder);
            writeAppointmentReminders(context, updated);
        } catch (JSONException ignored) {
            return;
        }
        scheduleAppointment(context, id, appointmentAtMillis, reminderMinutes);
    }

    static void cancelAppointmentReminder(Context context, String id) {
        JSONArray current = readAppointmentReminders(context);
        JSONArray updated = new JSONArray();
        for (int index = 0; index < current.length(); index++) {
            JSONObject item = current.optJSONObject(index);
            if (item != null && !id.equals(item.optString("id"))) updated.put(item);
        }
        writeAppointmentReminders(context, updated);
        cancelAppointmentAlarm(context, id);
    }

    static void scheduleAll(Context context) {
        if (!canScheduleExactAlarms(context) || !hasNotificationPermission(context)) return;
        JSONArray reminders = readReminders(context);
        for (int index = 0; index < reminders.length(); index++) {
            JSONObject item = reminders.optJSONObject(index);
            if (item == null) continue;
            schedule(context, item.optString("id"), item.optInt("hour"), item.optInt("minute"));
        }
        JSONArray appointments = readAppointmentReminders(context);
        for (int index = 0; index < appointments.length(); index++) {
            JSONObject item = appointments.optJSONObject(index);
            if (item == null) continue;
            long appointmentAt = item.optLong("appointmentAt");
            if (appointmentAt <= System.currentTimeMillis()) {
                cancelAppointmentReminder(context, item.optString("id"));
                continue;
            }
            scheduleAppointment(context, item.optString("id"), appointmentAt,
                    item.optInt("reminderMinutes"));
        }
    }

    static JSONObject find(Context context, String id) {
        JSONArray reminders = readReminders(context);
        for (int index = 0; index < reminders.length(); index++) {
            JSONObject item = reminders.optJSONObject(index);
            if (item != null && id.equals(item.optString("id"))) return item;
        }
        return null;
    }

    static JSONObject findAppointmentReminder(Context context, String id) {
        JSONArray reminders = readAppointmentReminders(context);
        for (int index = 0; index < reminders.length(); index++) {
            JSONObject item = reminders.optJSONObject(index);
            if (item != null && id.equals(item.optString("id"))) return item;
        }
        return null;
    }

    static void showNotification(Context context, String id) {
        if (!hasNotificationPermission(context)) return;
        createNotificationChannel(context);

        Intent openApp = new Intent(context, MainActivity.class);
        openApp.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent contentIntent = PendingIntent.getActivity(context, id.hashCode(), openApp,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        NotificationCompat.Builder notification = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentTitle("Lembrete de medicamento")
                .setContentText("Está na hora de conferir sua rotina de cuidado.")
                .setContentIntent(contentIntent)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                .setAutoCancel(true);
        NotificationManagerCompat.from(context).notify(id.hashCode(), notification.build());
    }

    static void showAppointmentNotification(Context context, String id) {
        if (!hasNotificationPermission(context)) return;
        createAppointmentNotificationChannel(context);

        Intent openApp = new Intent(context, MainActivity.class);
        openApp.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent contentIntent = PendingIntent.getActivity(context, id.hashCode() ^ 0x415050, openApp,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        NotificationCompat.Builder notification = new NotificationCompat.Builder(context, APPOINTMENT_CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentTitle("Lembrete de consulta")
                .setContentText("Sua consulta está agendada em breve. Toque para abrir o app.")
                .setContentIntent(contentIntent)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                .setAutoCancel(true);
        NotificationManagerCompat.from(context).notify(id.hashCode() ^ 0x415050, notification.build());
    }

    static void showGlucoseAlert(Context context) {
        if (!hasNotificationPermission(context)) return;
        createGlucoseNotificationChannel(context);

        Intent openApp = new Intent(context, MainActivity.class);
        openApp.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent contentIntent = PendingIntent.getActivity(context, 7301, openApp,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        NotificationCompat.Builder notification = new NotificationCompat.Builder(context, GLUCOSE_CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentTitle("Alerta de glicemia")
                .setContentText("Uma leitura ficou fora dos limites pessoais configurados. Toque para abrir o app.")
                .setContentIntent(contentIntent)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                .setAutoCancel(true);
        NotificationManagerCompat.from(context).notify(7301, notification.build());
    }

    static void showBloodPressureAlert(Context context) {
        if (!hasNotificationPermission(context)) return;
        createBloodPressureNotificationChannel(context);

        Intent openApp = new Intent(context, MainActivity.class);
        openApp.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent contentIntent = PendingIntent.getActivity(context, 7401, openApp,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        NotificationCompat.Builder notification = new NotificationCompat.Builder(context, BLOOD_PRESSURE_CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentTitle("Alerta de pressão arterial")
                .setContentText("Uma medição ficou fora dos seus limites pessoais. Toque para abrir o app.")
                .setContentIntent(contentIntent)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                .setAutoCancel(true);
        NotificationManagerCompat.from(context).notify(7401, notification.build());
    }

    private static void schedule(Context context, String id, int hour, int minute) {
        AlarmManager manager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (manager == null || !canScheduleExactAlarms(context)) return;

        Calendar next = Calendar.getInstance();
        next.set(Calendar.HOUR_OF_DAY, hour);
        next.set(Calendar.MINUTE, minute);
        next.set(Calendar.SECOND, 0);
        next.set(Calendar.MILLISECOND, 0);
        if (next.getTimeInMillis() <= System.currentTimeMillis()) next.add(Calendar.DAY_OF_YEAR, 1);

        try {
            manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next.getTimeInMillis(),
                    pendingIntent(context, id));
        } catch (SecurityException ignored) {
            // Exact-alarm access may have been revoked in system settings.
        }
    }

    private static void scheduleAppointment(Context context, String id, long appointmentAtMillis, int reminderMinutes) {
        AlarmManager manager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (manager == null || !canScheduleExactAlarms(context)) return;
        if (appointmentAtMillis <= System.currentTimeMillis()) {
            cancelAppointmentReminder(context, id);
            return;
        }

        long triggerAtMillis = appointmentAtMillis - reminderMinutes * 60_000L;
        triggerAtMillis = Math.max(triggerAtMillis, System.currentTimeMillis() + 1_000L);
        try {
            manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis,
                    appointmentPendingIntent(context, id));
        } catch (SecurityException ignored) {
            // Exact-alarm access may have been revoked in system settings.
        }
    }

    private static PendingIntent pendingIntent(Context context, String id) {
        Intent intent = new Intent(context, DailyReminderReceiver.class);
        intent.setAction(ACTION_REMIND);
        intent.putExtra(EXTRA_ID, id);
        intent.setData(Uri.parse("cuidarplus://daily-reminder/" + Uri.encode(id)));
        return PendingIntent.getBroadcast(context, id.hashCode(), intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private static PendingIntent appointmentPendingIntent(Context context, String id) {
        Intent intent = new Intent(context, DailyReminderReceiver.class);
        intent.setAction(ACTION_APPOINTMENT_REMIND);
        intent.putExtra(EXTRA_ID, id);
        intent.setData(Uri.parse("cuidarplus://appointment-reminder/" + Uri.encode(id)));
        return PendingIntent.getBroadcast(context, id.hashCode() ^ 0x415050, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private static void cancelAppointmentAlarm(Context context, String id) {
        AlarmManager manager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (manager != null) manager.cancel(appointmentPendingIntent(context, id));
    }

    private static JSONArray readReminders(Context context) {
        String saved = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(REMINDERS, "[]");
        try {
            return new JSONArray(saved);
        } catch (JSONException ignored) {
            return new JSONArray();
        }
    }

    private static void writeReminders(Context context, JSONArray reminders) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putString(REMINDERS, reminders.toString())
                .commit();
    }

    private static JSONArray readAppointmentReminders(Context context) {
        String saved = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(APPOINTMENT_REMINDERS, "[]");
        try {
            return new JSONArray(saved);
        } catch (JSONException ignored) {
            return new JSONArray();
        }
    }

    private static void writeAppointmentReminders(Context context, JSONArray reminders) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putString(APPOINTMENT_REMINDERS, reminders.toString())
                .commit();
    }

    private static void createNotificationChannel(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        NotificationChannel channel = new NotificationChannel(CHANNEL_ID,
                "Lembretes de medicamentos", NotificationManager.IMPORTANCE_DEFAULT);
        channel.setDescription("Alertas diários de medicamentos cadastrados no Cuidar+.");
        channel.setLockscreenVisibility(NotificationCompat.VISIBILITY_PRIVATE);
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        if (manager != null) manager.createNotificationChannel(channel);
    }

    private static void createAppointmentNotificationChannel(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        NotificationChannel channel = new NotificationChannel(APPOINTMENT_CHANNEL_ID,
                "Lembretes de consultas", NotificationManager.IMPORTANCE_DEFAULT);
        channel.setDescription("Avisos antes dos horários de consulta cadastrados.");
        channel.setLockscreenVisibility(NotificationCompat.VISIBILITY_PRIVATE);
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        if (manager != null) manager.createNotificationChannel(channel);
    }

    private static void createGlucoseNotificationChannel(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        NotificationChannel channel = new NotificationChannel(GLUCOSE_CHANNEL_ID,
                "Alertas de glicemia", NotificationManager.IMPORTANCE_HIGH);
        channel.setDescription("Avisos quando uma medição sair dos limites pessoais configurados.");
        channel.setLockscreenVisibility(NotificationCompat.VISIBILITY_PRIVATE);
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        if (manager != null) manager.createNotificationChannel(channel);
    }

    private static void createBloodPressureNotificationChannel(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        NotificationChannel channel = new NotificationChannel(BLOOD_PRESSURE_CHANNEL_ID,
                "Alertas de pressão arterial", NotificationManager.IMPORTANCE_HIGH);
        channel.setDescription("Avisos quando uma medição sair dos limites pessoais configurados.");
        channel.setLockscreenVisibility(NotificationCompat.VISIBILITY_PRIVATE);
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        if (manager != null) manager.createNotificationChannel(channel);
    }
}