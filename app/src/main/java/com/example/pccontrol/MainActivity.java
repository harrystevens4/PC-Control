package com.example.pccontrol;

import static android.appwidget.AppWidgetManager.ACTION_APPWIDGET_UPDATE;
import static android.appwidget.AppWidgetManager.EXTRA_APPWIDGET_ID;
import static android.appwidget.AppWidgetManager.EXTRA_APPWIDGET_IDS;
import static android.view.View.GONE;
import static android.view.View.VISIBLE;

import android.app.Activity;
import android.app.Application;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.Switch;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.google.android.material.color.DynamicColors;
import com.google.android.material.materialswitch.MaterialSwitch;

public class MainActivity extends Activity {

    @Override
    public void onCreate(Bundle saved_instance_state){
        super.onCreate(saved_instance_state);
        //setup notifications
        Notifier.init(this);
        //retrieve config values
        SharedPreferences remoteActionConfig = this.getSharedPreferences("remote_action_config", Context.MODE_PRIVATE);
        SharedPreferences.Editor remoteActionConfigEditor = remoteActionConfig.edit();
        String computer_hostname = remoteActionConfig.getString("computer_hostname","");
        String secret_key = remoteActionConfig.getString("secret_key","");
        boolean showWidgetLabels = remoteActionConfig.getBoolean("showWidgetLabels",true);
        boolean useMdnsFallback = remoteActionConfig.getBoolean("useMdnsFallback",true);
        //build ui
        DynamicColors.applyToActivitiesIfAvailable(this.getApplication());
        setContentView(R.layout.main_activity);
        EditText computer_hostname_text_entry = (EditText)findViewById(R.id.computer_hostname_text_entry);
        computer_hostname_text_entry.setText(computer_hostname);
        EditText secret_key_text_entry = (EditText)findViewById(R.id.secret_key_text_entry);
        secret_key_text_entry.setText(secret_key);
        MaterialSwitch showWidgetLabelsSwitch = findViewById(R.id.showLabelsSwitch);
        showWidgetLabelsSwitch.setChecked(showWidgetLabels);
        MaterialSwitch useMdnsFallbackSwitch = findViewById(R.id.useMdnsFallbackSwitch);
        useMdnsFallbackSwitch.setChecked(useMdnsFallback);

        //interactions
        Button computer_hostname_save_button = (Button)findViewById(R.id.computer_hostname_save_button);
        Button secret_key_save_button = (Button)findViewById(R.id.secret_key_save_button);
        computer_hostname_save_button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                remoteActionConfigEditor.putString("computer_hostname",computer_hostname_text_entry.getText().toString());
                remoteActionConfigEditor.apply();
            }
        });
        secret_key_save_button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                remoteActionConfigEditor.putString("secret_key",secret_key_text_entry.getText().toString());
                remoteActionConfigEditor.apply();
            }
        });
        showWidgetLabelsSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(@NonNull CompoundButton buttonView, boolean isChecked) {
                remoteActionConfigEditor.putBoolean("showWidgetLabels",isChecked).apply();
                //redraw all widgets
                redrawAllWidgets();
            }
        });
        useMdnsFallbackSwitch.setOnCheckedChangeListener((buttonView,isChecked) -> {
            remoteActionConfigEditor.putBoolean("useMdnsFallback",isChecked).apply();
        });
    }
    public void redrawAllWidgets(){
        Intent updateRemoteActionsWidgetIntent = new Intent(getApplicationContext(), RemoteActionsWidget.class);
        int[] appWidgetIds = AppWidgetManager.getInstance(this).getAppWidgetIds(new ComponentName(this,RemoteActionsWidget.class));
        updateRemoteActionsWidgetIntent.setAction(ACTION_APPWIDGET_UPDATE);
        updateRemoteActionsWidgetIntent.putExtra(EXTRA_APPWIDGET_IDS,appWidgetIds);
        sendBroadcast(updateRemoteActionsWidgetIntent);
    }
}
