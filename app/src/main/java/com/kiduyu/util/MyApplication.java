package com.kiduyu.util;

import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.net.Uri;
import android.os.StrictMode;

import androidx.annotation.NonNull;
import androidx.multidex.MultiDex;

import com.kiduyu.ebookstudio.SplashActivity;
import com.facebook.ads.AudienceNetworkAds;
import com.google.android.gms.ads.MobileAds;
import com.onesignal.OneSignal;
import com.onesignal.notifications.INotificationClickEvent;
import com.onesignal.notifications.INotificationClickListener;


import org.json.JSONException;
import org.json.JSONObject;


public class MyApplication extends Application {

    private static MyApplication mInstance;
    public SharedPreferences preferences;
    public String prefName = "app";

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);
        MultiDex.install(this);
    }

    public MyApplication() {
        mInstance = this;
    }

    public static synchronized MyApplication getInstance() {
        return mInstance;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        AudienceNetworkAds.initialize(this);
        MobileAds.initialize(this, initializationStatus -> {
        });


        StrictMode.VmPolicy.Builder builder = new StrictMode.VmPolicy.Builder();
        StrictMode.setVmPolicy(builder.build());
        builder.detectFileUriExposure();

        OneSignal.initWithContext(this, "ddb0b1a7-b1e1-4f82-86c3-5b30fffb5e50");
        OneSignal.getNotifications().addClickListener(new NotificationExtenderExample());

    }

    public void saveIsNotification(boolean flag) {
        preferences = this.getSharedPreferences(prefName, 0);
        Editor editor = preferences.edit();
        editor.putBoolean("IsNotification", flag);
        editor.apply();
    }

    public boolean getNotification() {
        preferences = this.getSharedPreferences(prefName, 0);
        return preferences.getBoolean("IsNotification", true);
    }

    class NotificationExtenderExample implements INotificationClickListener {

        @Override
        public void onClick(@NonNull INotificationClickEvent result) {
            JSONObject jsonObject = result.getNotification().getAdditionalData();
            if (jsonObject != null) {
                try {

                    String id = jsonObject.getString("post_id");
                    String type = jsonObject.getString("type");
                    String titleName = jsonObject.getString("post_title");
                    String url = jsonObject.getString("external_link");

                    Intent intent;
                    if (id.equals("") && !url.equals("false") && !url.trim().isEmpty()) {
                        intent = new Intent(Intent.ACTION_VIEW);
                        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        intent.setData(Uri.parse(url));
                    } else {
                        intent = new Intent(MyApplication.this, SplashActivity.class);
                        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        intent.putExtra("id", id);
                        intent.putExtra("type", type);
                        intent.putExtra("title", titleName);
                    }
                    startActivity(intent);

                } catch (JSONException e) {
                    e.printStackTrace();
                    Intent intent = new Intent(MyApplication.this, SplashActivity.class);
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(intent);
                }
            } else {
                Intent intent = new Intent(MyApplication.this, SplashActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent);
            }
        }
    }
}
