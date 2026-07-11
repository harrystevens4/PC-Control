package com.example.pccontrol;

import static java.util.concurrent.Executors.newSingleThreadExecutor;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
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

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ConnectException;
import java.net.Socket;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Objects;
import java.util.concurrent.ExecutorService;

/**
 * Implementation of App Widget functionality.
 */
public class PowerControl extends AppWidgetProvider {
    private final ExecutorService thread_pool = newSingleThreadExecutor();
    static void updateAppWidget(Context context, AppWidgetManager appWidgetManager,
                                int appWidgetId) {
        //create thread pool for handling requests

        CharSequence widgetText = context.getString(R.string.appwidget_text);
        // Construct the RemoteViews object
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.power_control);
        //set button callbacks
        int unlock_button_id = R.id.unlockButton;

        Intent unlock_intent = new Intent(context, PowerControl.class);
        unlock_intent.setAction("UNLOCK_ACTION");

        PendingIntent pending_unlock_intent = PendingIntent.getBroadcast(context,0,unlock_intent,PendingIntent.FLAG_IMMUTABLE);
        views.setOnClickPendingIntent(unlock_button_id,pending_unlock_intent);

        // Instruct the widget manager to update the widget
        appWidgetManager.updateAppWidget(appWidgetId, views);

        Log.d("INFO","registered handler for on click");
    }

    @Override
    public void onReceive(Context context, Intent intent){
        if (Objects.equals(intent.getAction(), "UNLOCK_ACTION")) {
            Log.d("PowerControl", "intent received: " + intent);
            thread_pool.execute(new RemoteUnlock(context));
        }
        super.onReceive(context,intent);
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

class RemoteUnlock implements Runnable {
    private Context service = null;
    public RemoteUnlock(Context caller){
        this.service = caller;
    }
    @Override
    public void run() {
        Log.d("RemoteAction", "initiating remote unlock");
        String computer_hostname = "harry-desktop.local";
        byte[] secret_key = "awesome test secret key".getBytes();
        Socket socket;
        OutputStream socket_output;
        InputStream socket_input;
        //====== open socket ======
        try {
            socket = new Socket(computer_hostname, 19532);
            socket_output = socket.getOutputStream();
            socket_input = socket.getInputStream();
        } catch (UnknownHostException | ConnectException e) {
            Log.e("RemoteAction", "Could not connect to host");
            Notification.Builder notification_builder =  new Notification.Builder(this.service,"ErrorChannel")
                    .setSmallIcon(R.mipmap.ic_launcher)
                    .setContentTitle("Unlocking error")
                    .setContentText("Couldn't connect to host "+computer_hostname)
                    .setVisibility(Notification.VISIBILITY_PUBLIC)
                    .setAutoCancel(true);
            NotificationManager notification_manager = this.service.getSystemService(NotificationManager.class);
            notification_manager.notify(0,notification_builder.build());
            return;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        try {
            ByteBuffer buffer = ByteBuffer.allocate(64);
            //====== auth_request ======
            long action = 0; //unlock
            buffer.putLong(action);
            socket_output.write(buffer.array(),0,8);
            //====== auth_challenge ======
            socket_input.read(buffer.array(),0,64);
            //====== auth_response ======
            ByteBuffer bytes_to_digest = ByteBuffer.allocate(64+secret_key.length);
            bytes_to_digest.put(buffer.array(),0,64);
            bytes_to_digest.put(secret_key);
            MessageDigest md = MessageDigest.getInstance("sha256");
            byte[] digest = md.digest(bytes_to_digest.array());
            socket_output.write(digest);
            //====== request_status ======
            byte[] status_buffer = {0};
            socket_input.read(status_buffer,0,1);
            int status = Byte.toUnsignedInt(status_buffer[0]);
            Log.d("RemoteAction","status returned of "+status);
            //====== close socket ======
        } catch (IOException e) {
            throw new RuntimeException(e);
        } catch (NoSuchAlgorithmException ignored){
            Log.e("RemoteAction","sha256 not available");
            return;
        } finally {
            try {
                socket.close();
            } catch (IOException ignored){}
        }
        Log.d("RemoteAction", "remote unlock complete");
    }
}