package com.example.pccontrol;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.text.BoringLayout;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.RemoteViews;

import androidx.core.content.ContextCompat;

/**
 * Implementation of App Widget functionality.
 */
public class PowerControl extends AppWidgetProvider {
    public void unlockButtonHandler(View view){
        Log.d("INFO","unlock requested");
    }

    static void updateAppWidget(Context context, AppWidgetManager appWidgetManager,
                                int appWidgetId) {

        CharSequence widgetText = context.getString(R.string.appwidget_text);
        // Construct the RemoteViews object
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.power_control);
        //set button callbacks
        int unlock_button_id = R.id.unlockButton;//views.getLayoutId();
        Intent unlock_intent = new Intent(context, RemoteAction.class);
        PendingIntent pending_unlock_intent = PendingIntent.getService(context,0,unlock_intent,PendingIntent.FLAG_ONE_SHOT | PendingIntent.FLAG_IMMUTABLE);
        views.setOnClickPendingIntent(unlock_button_id,pending_unlock_intent);

        // Instruct the widget manager to update the widget
        appWidgetManager.updateAppWidget(appWidgetId, views);

        Log.d("INFO","registered handler for on click");
        IntentFilter intent_filter = new IntentFilter("lockButtonPressed");
        BroadcastReceiver receiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                Log.d("INFO","on click triggered");
            }
        };
        ContextCompat.registerReceiver(context, receiver, intent_filter, ContextCompat.RECEIVER_NOT_EXPORTED);
    }

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        // There may be multiple widgets active, so update all of them
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