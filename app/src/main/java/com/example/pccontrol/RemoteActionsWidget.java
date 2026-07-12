package com.example.pccontrol;

import static java.util.concurrent.Executors.newSingleThreadExecutor;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.wifi.hotspot2.pps.Credential;
import android.preference.PreferenceManager;
import android.util.Log;
import android.widget.RemoteViews;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ConnectException;
import java.net.Socket;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.Objects;
import java.util.concurrent.ExecutorService;

/**
 * Implementation of App Widget functionality.
 */
public class RemoteActionsWidget extends AppWidgetProvider {
    private final ExecutorService thread_pool = newSingleThreadExecutor();
    static void updateAppWidget(Context context, AppWidgetManager appWidgetManager,
                                int appWidgetId) {

        // Construct the RemoteViews object
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.remote_actions);
        //set button callbacks
        int unlock_button_id = R.id.unlockButton;

        Intent unlock_intent = new Intent(context, RemoteActionsWidget.class);
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
            thread_pool.execute(new RemoteAction(context,0));
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

class RemoteAction implements Runnable {
    private Context caller = null;
    private int action = 0;
    public RemoteAction(Context caller,int action){
        this.caller = caller;
        this.action = action;
    }
    private void notify_error(String message){
        Notification.Builder notification_builder =  new Notification.Builder(this.caller,"ErrorChannel")
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle("Error")
                .setContentText(message)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setAutoCancel(true);
        NotificationManager notification_manager = this.caller.getSystemService(NotificationManager.class);
        notification_manager.notify(0,notification_builder.build());
    }
    @Override
    public void run() {
        Log.d("RemoteAction", "initiating remote unlock");
        //====== get computer hostname and secret key ======
        SharedPreferences remote_action_config = this.caller.getSharedPreferences("remote_action_config", Context.MODE_PRIVATE);
        String computer_hostname = remote_action_config.getString("computer_hostname","");
        if (Objects.equals(computer_hostname,"")) {
            this.notify_error("computer hostname not set");
            return;
        }
        String secret_key_base64 = remote_action_config.getString("secret_key","");
        if (Objects.equals(secret_key_base64,"")) {
            this.notify_error("secret key not set");
            return;
        }
        byte[] secret_key = Base64.getDecoder().decode(secret_key_base64);
        //====== open socket ======
        Socket socket;
        OutputStream socket_output;
        InputStream socket_input;
        try {
            socket = new Socket(computer_hostname, 19532);
            socket_output = socket.getOutputStream();
            socket_input = socket.getInputStream();
        } catch (UnknownHostException | ConnectException e) {
            Log.e("RemoteAction", "Could not connect to host");
            this.notify_error("Couldn't connect to host "+computer_hostname);
            return;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        try {
            ByteBuffer buffer = ByteBuffer.allocate(64);
            //====== auth_request ======
            buffer.putLong(this.action);
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
            if (status != 0) {
                String text_status = "";
                if (status == 1){
                    text_status = "authentication failed";
                }else{
                    text_status = "code "+status;
                }
                notify_error("Error performing action: "+text_status);
            }
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