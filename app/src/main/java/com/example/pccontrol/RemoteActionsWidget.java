package com.example.pccontrol;

import static android.appwidget.AppWidgetManager.EXTRA_APPWIDGET_ID;
import static java.util.concurrent.Executors.newSingleThreadExecutor;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.util.Log;
import android.widget.RemoteViews;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ConnectException;
import java.net.Socket;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
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
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.remote_actions);

        //set button callbacks
        Intent unlock_intent = new Intent(context, RemoteActionsWidget.class);
        unlock_intent.setAction("UNLOCK_ACTION");
        Intent lock_intent = new Intent(context, RemoteActionsWidget.class);
        lock_intent.setAction("LOCK_ACTION");
        Intent shutdown_intent = new Intent(context, RemoteActionsWidget.class);
        shutdown_intent.setAction("SHUTDOWN_ACTION");
        shutdown_intent.putExtra(EXTRA_APPWIDGET_ID,appWidgetId);

        views.setOnClickPendingIntent(R.id.unlockButton,PendingIntent.getBroadcast(context,0,unlock_intent,PendingIntent.FLAG_IMMUTABLE));
        views.setOnClickPendingIntent(R.id.lockButton,PendingIntent.getBroadcast(context,0,lock_intent,PendingIntent.FLAG_IMMUTABLE));
        views.setOnClickPendingIntent(R.id.powerOffButton,PendingIntent.getBroadcast(context,0,shutdown_intent,PendingIntent.FLAG_IMMUTABLE));

        // Instruct the widget manager to update the widget
        appWidgetManager.updateAppWidget(appWidgetId, views);

        Log.d("INFO","registered handler for on click");
    }

    @Override
    public void onReceive(Context context, Intent intent){
        int app_widget_id = intent.getIntExtra(EXTRA_APPWIDGET_ID,0);
        if (Objects.equals(intent.getAction(), "UNLOCK_ACTION")) {
            thread_pool.execute(new RemoteAction(context,0));
        }
        if (Objects.equals(intent.getAction(), "LOCK_ACTION")) {
            thread_pool.execute(new RemoteAction(context,1));
        }
        if (Objects.equals(intent.getAction(), "SHUTDOWN_ACTION")) {
            shutdown_button_confirmation_stage++;
            RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.remote_actions);
            AppWidgetManager app_widget_manager = AppWidgetManager.getInstance(context);
            String[] confirmation_texts = {"confirm?","confirm!"};
            //schedule confirmation stage reset
            if (shutdown_button_confirmation_stage == 1){
                TimerTask reset_shutdown_confirmation_timer_task = new TimerTask() {
                    @Override public void run(){
                        shutdown_button_confirmation_stage = 0;
                        views.setTextViewText(R.id.powerOffButton, "shutdown");
                        app_widget_manager.partiallyUpdateAppWidget(app_widget_id,views);
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
                app_widget_manager.partiallyUpdateAppWidget(app_widget_id,views);
            }else {
                Log.d("RemoteActionsWidget","power off requested");
                //execute shutdown and reset label
                thread_pool.execute(new RemoteAction(context,2));
                //reset text
                shutdown_button_confirmation_stage = 0;
                views.setTextViewText(R.id.powerOffButton, "shutdown");
                app_widget_manager.partiallyUpdateAppWidget(app_widget_id,views);
            }
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
        Log.d("RemoteAction", "initiating remote connection");
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
            socket_output.write(buffer.array(), 0, 8);
            //====== auth_challenge ======
            socket_input.read(buffer.array(), 0, 64);
            //====== auth_response ======
            ByteBuffer bytes_to_digest = ByteBuffer.allocate(64 + secret_key.length);
            bytes_to_digest.put(buffer.array(), 0, 64);
            bytes_to_digest.put(secret_key);
            MessageDigest md = MessageDigest.getInstance("sha256");
            byte[] digest = md.digest(bytes_to_digest.array());
            socket_output.write(digest);
            //====== request_status ======
            byte[] status_buffer = {0};
            socket_input.read(status_buffer, 0, 1);
            int status = Byte.toUnsignedInt(status_buffer[0]);
            Log.d("RemoteAction", "status returned of " + status);
            if (status != 0) {
                String text_status = "";
                if (status == 1) {
                    text_status = "authentication failed";
                } else if (status == 2){
                    text_status = "command not implemented";
                } else {
                    text_status = "code " + status;
                }
                notify_error("Error performing action: " + text_status);
            }
            //====== close socket ======
        } catch (SocketException e){
            this.notify_error("Socket transport error: "+e.getMessage());
            return;
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