package com.example.pccontrol;

import android.app.NotificationManager;
import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.Data;
import androidx.work.OneTimeWorkRequest;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

public class CancelNotificationWorker extends Worker {
    public static String ID_PARAMETER = "notificationId";
    private final int notificationId;
    public CancelNotificationWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
        notificationId = workerParams.getInputData().getInt(ID_PARAMETER,0);
    }
    @NonNull
    public static OneTimeWorkRequest.Builder OneTimeWorkRequestBuilder(int notificationId){
        Data workData = new Data.Builder()
                .putInt(ID_PARAMETER,notificationId)
                .build();
        return new OneTimeWorkRequest.Builder(CancelNotificationWorker.class)
                .setInputData(workData);
    }
    @NonNull
    @Override
    public Result doWork() {
        NotificationManager notificationManager = getApplicationContext().getSystemService(NotificationManager.class);
        notificationManager.cancel(notificationId);
        return Result.success();
    }
}
