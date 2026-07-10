package com.example.pccontrol;

import android.app.Activity;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;

import androidx.activity.ComponentActivity;
import androidx.appcompat.app.AppCompatActivity;

public class RemoteAction extends Service {
    @Override
    public IBinder onBind(Intent intent){
        return null;
    }
    @Override
    public int onStartCommand(Intent intent, int flags, int startId){
        Log.d("INFO","RemoteAction for "+" requested");
        return START_NOT_STICKY;
    }
}
