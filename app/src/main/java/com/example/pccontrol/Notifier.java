package com.example.pccontrol;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;

import androidx.compose.runtime.AtomicInt;
import androidx.work.WorkManager;
import androidx.work.WorkRequest;

import java.time.Duration;

public class Notifier {
    static AtomicInt currentNotificationId = new AtomicInt(0);
    static final String ERROR_CHANNEL_ID = "ErrorChannel";
    static final String INFO_CHANNEL_ID = "InfoChannel";
    public static void init(Context context){
        //get notification manager
        NotificationManager notification_manager = context.getSystemService(NotificationManager.class);
        //error channel
        NotificationChannel errorNotificationChannel = new NotificationChannel(ERROR_CHANNEL_ID,"Errors", NotificationManager.IMPORTANCE_HIGH);
        errorNotificationChannel.enableVibration(true);
        errorNotificationChannel.enableLights(true);
        notification_manager.createNotificationChannel(errorNotificationChannel);
        //info channel
        NotificationChannel infoNotificationChannel = new NotificationChannel(INFO_CHANNEL_ID,"Info", NotificationManager.IMPORTANCE_HIGH);
        infoNotificationChannel.enableVibration(true);
        infoNotificationChannel.enableLights(true);
        notification_manager.createNotificationChannel(infoNotificationChannel);
    }
    public static int notifyError(Context context, String message){
        Notification.Builder notification_builder =  new Notification.Builder(context,ERROR_CHANNEL_ID)
                .setSmallIcon(R.mipmap.pccontrol_icon)
                .setContentTitle("Error")
                .setContentText(message)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setAutoCancel(true);
        NotificationManager notification_manager = context.getSystemService(NotificationManager.class);
        int id = currentNotificationId.incrementAndGet();
        notification_manager.notify(id,notification_builder.build());
        return id;
    }
    public static int notifyInfo(Context context, String title, String message){
        Notification.Builder notification_builder =  new Notification.Builder(context,INFO_CHANNEL_ID)
                .setSmallIcon(R.mipmap.pccontrol_icon)
                .setContentTitle(title)
                .setContentText(message)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setAutoCancel(true);
        NotificationManager notification_manager = context.getSystemService(NotificationManager.class);
        int id = currentNotificationId.incrementAndGet();
        notification_manager.notify(id,notification_builder.build());
        return id;
    }
    public static int notifyInfoForDuration(Context context, String title, String message, Duration duration){
        int notificationId = notifyInfo(context,title,message);
        WorkManager workManager = WorkManager.getInstance(context);
        WorkRequest cancelRequest = CancelNotificationWorker
                .OneTimeWorkRequestBuilder(notificationId)
                .setInitialDelay(duration)
                .build();
        workManager.enqueue(cancelRequest);
        return notificationId;
    }
}
