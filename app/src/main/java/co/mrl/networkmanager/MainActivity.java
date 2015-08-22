package co.mrl.networkmanager;

import android.app.Activity;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.app.TimePickerDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.TimePicker;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Calendar;

public class MainActivity extends AppCompatActivity implements View.OnClickListener {

    private AlarmManager alarmMgr;
    private PendingIntent alarmIntent;
    Switch timer_switch;
    TextView time_display;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        final SharedPreferences.Editor editor = getPreferences(MODE_PRIVATE).edit();
        SharedPreferences prefs = getPreferences(MODE_PRIVATE);
        Log.i("OnCreate Shared",prefs.getLong("timeInMillis", -1) + "");
        if (prefs.getLong("timeInMillis", -1) == -1){
            Calendar c = Calendar.getInstance();
            c.set(Calendar.HOUR_OF_DAY, 9);
            editor.putLong("timeInMillis", c.getTimeInMillis());
        }


        findViewById(R.id.change_state).setOnClickListener(this);
        findViewById(R.id.select_time).setOnClickListener(this);
        time_display = (TextView) findViewById(R.id.time_display);

        Log.i("OnCreate Shared",prefs.getString("timeInHours", "XXX") + "");
        if (prefs.getString("timeInHours","XXX").equals("XXX")){
            editor.putString("timeInHours","09 : 00");
            time_display.setText("09 : 00");
        }else{
            time_display.setText(prefs.getString("timeInHours","XXX"));
        }


        timer_switch = (Switch) findViewById(R.id.timer_switch);
        Log.i("OnCreate Shared",prefs.getBoolean("timer bool", false) + "");
        if (prefs.getBoolean("timer bool",false)){
            timer_switch.setChecked(true);
        }else{
            timer_switch.setChecked(false);
            editor.putBoolean("timer bool", false);
        }

        timer_switch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean b) {

                if (b){
                    turnAlarmOn();
                    SharedPreferences.Editor editor = getPreferences(MODE_PRIVATE).edit();
                    editor.putBoolean("timer bool", true);
                    editor.apply();
                }else{
                    turnAlarmOff();
                    SharedPreferences.Editor editor = getPreferences(MODE_PRIVATE).edit();
                    editor.putBoolean("timer bool", false);
                    editor.apply();
                }
            }
        });

        editor.apply();
    }


    public void turning_off_everything() {

        WifiManager wifiManager = (WifiManager) getSystemService(Context.WIFI_SERVICE);
        if (wifiManager.isWifiEnabled()) {
            wifiManager.setWifiEnabled(false);
            Log.i("Network State: ", "Wifi Turned Off");
        } else {
            Log.i("Network State: ", "Wifi Already Turned Off");
        }

        try {
            ConnectivityManager dataManager;
            dataManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
            Method dataMtd = ConnectivityManager.class.getDeclaredMethod("setMobileDataEnabled", boolean.class);
            dataMtd.setAccessible(true);

            if (!isDataNetworkEnabled()) {
                // dataMtd.invoke(dataManager, true);
                // setMobileDataEnabled(true);
                setMobileNetworkFromLollipop(MainActivity.this.getApplicationContext());
                Log.i("Network State: ", "Turned on");
            } else {
                // dataMtd.invoke(dataManager, false);
                // setMobileDataEnabled(false);
                setMobileNetworkFromLollipop(MainActivity.this.getApplicationContext());
                Log.i("Network State: ", "Turned off");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    public Boolean isDataNetworkEnabled() {

        ConnectivityManager conMgr = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);

        if (conMgr.getNetworkInfo(0).getState() == NetworkInfo.State.CONNECTED
                || conMgr.getNetworkInfo(1).getState() == NetworkInfo.State.CONNECTING) {
            return true;
        }
        if (conMgr.getNetworkInfo(0).getState() == NetworkInfo.State.DISCONNECTED
                || conMgr.getNetworkInfo(1).getState() == NetworkInfo.State.DISCONNECTED) {
            return false;
        }
        return false;
    }

    public void setMobileNetworkFromLollipop(Context context) {

        String command = null;
        int state = 0;
        try {
            // Get the current state of the mobile network.
            state = isDataNetworkEnabled() ? 0 : 1;
            // Get the value of the "TRANSACTION_setDataEnabled" field.
            String transactionCode = getTransactionCode(context);
            // Android 5.1+ (API 22) and later.
            if (Build.VERSION.SDK_INT > Build.VERSION_CODES.LOLLIPOP) {
                SubscriptionManager mSubscriptionManager = (SubscriptionManager) context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE);
                // Loop through the subscription list i.e. SIM list.
                for (int i = 0; i < mSubscriptionManager.getActiveSubscriptionInfoCountMax(); i++) {
                    if (transactionCode != null && transactionCode.length() > 0) {

                        // Get the active subscription ID for a given SIM card.
                        int subscriptionId = mSubscriptionManager.getActiveSubscriptionInfoList().get(i).getSubscriptionId();

                        // Execute the command via `su` to turn off
                        // mobile network for a subscription service.
                        command = "service call phone " + transactionCode + " i32 " + subscriptionId + " i32 " + state;
                        executeCommandViaSu(context, "-c", command);
                    }
                }
            } else if (Build.VERSION.SDK_INT == Build.VERSION_CODES.LOLLIPOP) {

                // Android 5.0 (API 21) only.
                if (transactionCode != null && transactionCode.length() > 0) {
                    // Execute the command via `su` to turn off mobile network.
                    command = "service call phone " + transactionCode + " i32 " + state;
                    executeCommandViaSu(context, "-c", command);
                }
            }
        } catch (Exception e) {

            e.printStackTrace();
        }
    }

    private static String getTransactionCode(Context context) throws Exception {
        try {
            final TelephonyManager mTelephonyManager = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
            final Class<?> mTelephonyClass = Class.forName(mTelephonyManager.getClass().getName());
            final Method mTelephonyMethod = mTelephonyClass.getDeclaredMethod("getITelephony");
            mTelephonyMethod.setAccessible(true);
            final Object mTelephonyStub = mTelephonyMethod.invoke(mTelephonyManager);
            final Class<?> mTelephonyStubClass = Class.forName(mTelephonyStub.getClass().getName());
            final Class<?> mClass = mTelephonyStubClass.getDeclaringClass();
            final Field field = mClass.getDeclaredField("TRANSACTION_setDataEnabled");
            field.setAccessible(true);
            return String.valueOf(field.getInt(null));
        } catch (Exception e) {
            // The "TRANSACTION_setDataEnabled" field is not available,
            // or named differently in the current API level, so we throw
            // an exception and inform users that the method is not available.
            throw e;
        }
    }

    private static void executeCommandViaSu(Context context, String option, String command) {
        boolean success = false;
        String su = "su";
        for (int i = 0; i < 3; i++) {
            // Default "su" command executed successfully, then quit.
            if (success) {
                break;
            }
            // Else, execute other "su" commands.
            if (i == 1) {
                su = "/system/xbin/su";
            } else if (i == 2) {
                su = "/system/bin/su";
            }
            try {
                // Execute command as "su".
                Runtime.getRuntime().exec(new String[]{su, option, command});
            } catch (IOException e) {
                success = false;
                // Oops! Cannot execute `su` for some reason.
                // Log error here.
            } finally {
                success = true;
            }
        }
    }

    @Override
    public void onClick(View v) {

        if (v.getId() == R.id.select_time) {

            Calendar c = Calendar.getInstance();
            Log.i("State : ", "Time Picker called");

            new TimePickerDialog(this, new TimePickerDialog.OnTimeSetListener() {
                @Override
                public void onTimeSet(TimePicker view, int hourOfDay, int minute) {
                    System.out.println("\nTime:" + hourOfDay + ": " + minute);

                    Calendar temp_cal = Calendar.getInstance();
                    temp_cal.set(Calendar.HOUR_OF_DAY, hourOfDay);
                    temp_cal.set(Calendar.MINUTE, minute);
                    String string_time = "";
                    if (hourOfDay<10){
                        string_time += "0" + hourOfDay;
                    }else{
                        string_time += hourOfDay;
                    }
                    if (minute<10){
                        string_time += ": 0" + minute;
                    }else{
                        string_time += ": " + minute;
                    }

                    SharedPreferences.Editor editor = getPreferences(MODE_PRIVATE).edit();
                    editor.putLong("timeInMillis", temp_cal.getTimeInMillis());
                    editor.putString("timeInHours", string_time);
                    editor.apply();
                    time_display.setText(string_time);
                    timer_switch.setChecked(true);

                }
            }, c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE), true).show();

        } else if (v.getId() == R.id.change_state) {

            turning_off_everything();
            Log.i("State  : ", "Changing");
        }

    }

    public void turnAlarmOn(){

        SharedPreferences prefs = getPreferences(MODE_PRIVATE);
        long timeInMillis = prefs.getLong("timeInMillis", 0);

        Log.i("Alarm Manager:"," Turned On");

        Context context = MainActivity.this;
        Intent intent = new Intent(context, ConfirmSleep.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(context,
                12345, intent, PendingIntent.FLAG_CANCEL_CURRENT);
        AlarmManager am = (AlarmManager) getSystemService(Activity.ALARM_SERVICE);
        am.setRepeating(AlarmManager.RTC_WAKEUP, timeInMillis,
                24 * 60 * 60 * 1000, pendingIntent);

    }

    public void turnAlarmOff(){

        Log.i("Alarm Manager:"," Turned Off");

        // Permanently disable alarm
        Intent intent = new Intent(this, ConfirmSleep.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this,
                12345, intent, PendingIntent.FLAG_CANCEL_CURRENT);
        AlarmManager am = (AlarmManager)getSystemService(Activity.ALARM_SERVICE);
        am.cancel(pendingIntent);

    }

}
