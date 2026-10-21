package com.example.pccontrol;

import static android.appwidget.AppWidgetManager.EXTRA_APPWIDGET_ID;
import static android.content.Context.MODE_PRIVATE;
import static android.view.View.GONE;
import static android.view.View.VISIBLE;
import static java.util.concurrent.Executors.newSingleThreadExecutor;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.location.Address;
import android.util.Log;
import android.widget.RemoteViews;
import android.widget.TextView;

import androidx.work.Data;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ConnectException;
import java.net.InetAddress;
import java.net.Socket;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.Base64;
import java.util.Objects;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ExecutorService;

/**
 * Implementation of App Widget functionality.
 */
public class RemoteActionsWidget extends AppWidgetProvider {
    private final ExecutorService thread_pool = newSingleThreadExecutor();
    private static int shutdown_button_confirmation_stage = 0;
    static void updateAppWidget(Context context, AppWidgetManager appWidgetManager,
                                int appWidgetId) {
        Log.d("RemoteActionsWidget","updateAppWidget id "+appWidgetId);
        // Construct the RemoteViews object
        RemoteViews views = RemoteActionsWidget.buildRemoteViews(context);

        // Instruct the widget manager to update the widget
        appWidgetManager.updateAppWidget(appWidgetId, views);

        Log.d("INFO","registered handler for on click");
    }
    private static OneTimeWorkRequest remoteActionWorkerFactory(int action) {
        Data workRequestInputData = new Data.Builder()
                .putInt("action",action)
                .build();
        OneTimeWorkRequest workRequest = new OneTimeWorkRequest.Builder(RemoteActionWorker.class)
                .setInputData(workRequestInputData)
                .build();
        return workRequest;
    }
    @Override
    public void onReceive(Context context, Intent intent){
        WorkManager workManager = WorkManager.getInstance(context);
        int[] allAppWidgetIds = AppWidgetManager.getInstance(context).getAppWidgetIds(new ComponentName(context.getApplicationContext(),RemoteActionsWidget.class));
        if (Objects.equals(intent.getAction(), "UNLOCK_ACTION")) {
            workManager.enqueue(remoteActionWorkerFactory(0));
        }
        if (Objects.equals(intent.getAction(), "LOCK_ACTION")) {
            workManager.enqueue(remoteActionWorkerFactory(1));
        }
        if (Objects.equals(intent.getAction(), "SHUTDOWN_ACTION")) {
            shutdown_button_confirmation_stage++;
            RemoteViews views = buildRemoteViews(context);
            AppWidgetManager app_widget_manager = AppWidgetManager.getInstance(context);
            String[] confirmation_texts = {"confirm?","confirm!"};
            //schedule confirmation stage reset
            if (shutdown_button_confirmation_stage == 1){
                TimerTask reset_shutdown_confirmation_timer_task = new TimerTask() {
                    @Override public void run(){
                        shutdown_button_confirmation_stage = 0;
                        views.setTextViewText(R.id.powerOffButton, "shutdown");
                        app_widget_manager.updateAppWidget(allAppWidgetIds,views);
                    }
                };
                Timer timer = new Timer(true);
                timer.schedule(reset_shutdown_confirmation_timer_task,3000);
            }
            //handle confirmation stages
            if (shutdown_button_confirmation_stage < confirmation_texts.length+1) {
                Log.d("RemoteActionsWidget","power off confirmation stage "+shutdown_button_confirmation_stage);
                //change text to next stage of confirmation
                views.setTextViewText(R.id.powerOffButton, confirmation_texts[shutdown_button_confirmation_stage-1]);
                app_widget_manager.updateAppWidget(allAppWidgetIds,views);
            }else {
                Log.d("RemoteActionsWidget","power off requested");
                //execute shutdown and reset label
                workManager.enqueue(remoteActionWorkerFactory(2));
                //reset text
                shutdown_button_confirmation_stage = 0;
                views.setTextViewText(R.id.powerOffButton, "shutdown");
                app_widget_manager.updateAppWidget(allAppWidgetIds,views);
            }
        }
        super.onReceive(context,intent);
    }

    public static RemoteViews buildRemoteViews(Context context){
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.remote_actions);

        //configure ui
        SharedPreferences remoteActionConfig = context.getSharedPreferences("remote_action_config",MODE_PRIVATE);
        boolean showWidgetLabels = remoteActionConfig.getBoolean("showWidgetLabels",true);
        views.setViewVisibility(R.id.lockingLabel,showWidgetLabels ? VISIBLE : GONE);
        views.setViewVisibility(R.id.powerLabel,showWidgetLabels ? VISIBLE : GONE);


        //set button callbacks
        Intent unlock_intent = new Intent(context, RemoteActionsWidget.class);
        unlock_intent.setAction("UNLOCK_ACTION");
        Intent lock_intent = new Intent(context, RemoteActionsWidget.class);
        lock_intent.setAction("LOCK_ACTION");
        Intent shutdown_intent = new Intent(context, RemoteActionsWidget.class);
        shutdown_intent.setAction("SHUTDOWN_ACTION");

        views.setOnClickPendingIntent(R.id.unlockButton,PendingIntent.getBroadcast(context,0,unlock_intent,PendingIntent.FLAG_IMMUTABLE));
        views.setOnClickPendingIntent(R.id.lockButton,PendingIntent.getBroadcast(context,0,lock_intent,PendingIntent.FLAG_IMMUTABLE));
        views.setOnClickPendingIntent(R.id.powerOffButton,PendingIntent.getBroadcast(context,0,shutdown_intent,PendingIntent.FLAG_IMMUTABLE));

        return views;
    }

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        // There may be multiple widgets active, so update all of them
        Log.d("RemoteActionsWidget","updating app widgets "+appWidgetIds);
        for (int appWidgetId : appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId);
        }
    }

    @Override
    public void onEnabled(Context context) {
        // Enter relevant functionality for when the first widget is created
    }

    @Override
    public void onDisabled(Context context) {
        // Enter relevant functionality for when the last widget is disabled
    }
}