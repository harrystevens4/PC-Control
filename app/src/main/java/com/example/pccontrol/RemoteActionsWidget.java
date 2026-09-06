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
        int[] allAppWidgetIds = AppWidgetManager.getInstance(context).getAppWidgetIds(new ComponentName(context.getApplicationContext(),RemoteActionsWidget.class));
        if (Objects.equals(intent.getAction(), "UNLOCK_ACTION")) {
            thread_pool.execute(new RemoteAction(context,0));
        }
        if (Objects.equals(intent.getAction(), "LOCK_ACTION")) {
            thread_pool.execute(new RemoteAction(context,1));
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
                thread_pool.execute(new RemoteAction(context,2));
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

class RemoteAction implements Runnable {
    private Context caller = null;
    private int action = 0;
    public RemoteAction(Context caller,int action){
        this.caller = caller;
        this.action = action;
    }
    @Override
    public void run() {
        Log.d("RemoteAction", "initiating remote connection");
        //====== get computer hostname and secret key ======
        SharedPreferences remote_action_config = this.caller.getSharedPreferences("remote_action_config", MODE_PRIVATE);
        String computer_hostname = remote_action_config.getString("computer_hostname","");
        if (Objects.equals(computer_hostname,"")) {
            Notifier.notifyError(caller,"computer hostname not set");
            return;
        }
        String secret_key_base64 = remote_action_config.getString("secret_key","");
        if (Objects.equals(secret_key_base64,"")) {
            Notifier.notifyError(caller,"secret key not set");
            return;
        }
        byte[] secret_key = Base64.getDecoder().decode(secret_key_base64);
        //====== open socket ======
        Socket socket;
        OutputStream socket_output;
        InputStream socket_input;
        try {
            InetAddress address;
            try {
                address = QuickDiscover.discover(computer_hostname,"UNLOCKFPHONE");
            } catch (IOException e) {
                SharedPreferences sharedPreferences = caller.getSharedPreferences("remote_action_config",MODE_PRIVATE);
                if (sharedPreferences.getBoolean("useMdnsFallback",true)) {
                    Log.d("RemoteActionsWidget","quick discover failed: falling back to mdns");
                    address = java.net.InetAddress.getByName(computer_hostname + ".local");
                }else {
                    throw new UnknownHostException("no method for resolving hostname available");
                }
            }
            socket = new Socket(address, 19532);
            socket_output = socket.getOutputStream();
            socket_input = socket.getInputStream();
        } catch (UnknownHostException e) {
            Log.e("RemoteAction", "Could not connect to host");
            Notifier.notifyError(caller,"Couldn't connect to host "+computer_hostname);
            return;
        } catch (IOException e){
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
                Notifier.notifyError(caller,"Error performing action: " + text_status);
            }
            //====== close socket ======
        } catch (SocketException e){
            Notifier.notifyError(caller,"Socket transport error: "+e.getMessage());
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
        Notifier.notifyInfoForDuration(caller,"Request Status","remote action successful", Duration.ofSeconds(10));
    }
}