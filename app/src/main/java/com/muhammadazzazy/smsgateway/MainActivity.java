package com.muhammadazzazy.smsgateway;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.lang.ref.WeakReference;
import java.net.HttpURLConnection;
import java.net.URL;
import android.telephony.SmsManager;
import android.widget.Toast;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";
    private int sentMessagesCount = 0;
    private Button startButton, stopButton;
    private TextView textView;
    final private Handler handler = new Handler();
    private Runnable runnable;
    private boolean isRunning = false;
    private static final int PERMISSION_REQUEST_CODE = 1;

    private final BroadcastReceiver sentReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            switch (getResultCode()) {
                case AppCompatActivity.RESULT_OK:
                    Log.i(TAG,"SMS sent successfully");
                    break;
                case SmsManager.RESULT_ERROR_GENERIC_FAILURE:
                    Log.e(TAG,"SMS sending failed: Generic failure");
                    break;
                case SmsManager.RESULT_ERROR_RADIO_OFF:
                    Log.e(TAG,"SMS sending failed: Radio off");
                    break;
                case SmsManager.RESULT_ERROR_NULL_PDU:
                    Log.e(TAG, "SMS sending failed: Null PDU");
                    break;
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        textView = findViewById(R.id.text_view);

        startButton = findViewById(R.id.start_button);
        stopButton = findViewById(R.id.stop_button);

        startButton.setEnabled(true);
        stopButton.setEnabled(false);

        startButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startRetrieving();
            }
        });

        stopButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                stopRetrieving();
            }
        });

        registerReceiver(sentReceiver, new IntentFilter("SMS_SENT"));

        updateTextView();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unregisterReceiver(sentReceiver);
    }

    private void startRetrieving() {
        int result = ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.SEND_SMS);
        if (result == PackageManager.PERMISSION_GRANTED) {
            startSendingMessages();
        } else {
            ActivityCompat.requestPermissions(MainActivity.this, new String[]{Manifest.permission.SEND_SMS}, PERMISSION_REQUEST_CODE);
        }
    }

    private void startSendingMessages() {
        isRunning = true;
        startButton.setEnabled(false);
        stopButton.setEnabled(true);
        runnable = new Runnable() {
            @Override
            public void run() {
                if (isRunning) {
                    new RetrieveAndSendTask(MainActivity.this).execute();
                    handler.postDelayed(this, 5000);
                }
            }
        };
        handler.post(runnable);
    }

    private void stopRetrieving() {
        isRunning = false;
        startButton.setEnabled(true);
        stopButton.setEnabled(false);
        handler.removeCallbacks(runnable);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(MainActivity.this, "Permission Granted", Toast.LENGTH_SHORT).show();
                startSendingMessages();
            } else {
                Toast.makeText(MainActivity.this, "Permission Denied", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void updateTextView() {
        String message = getString(R.string.sms_count, sentMessagesCount);
        textView.setText(message);
    }

    private static class RetrieveAndSendTask extends AsyncTask<Void, Void, Boolean> {
        private final WeakReference<MainActivity> weakReference;

        RetrieveAndSendTask(MainActivity activity) {
            weakReference = new WeakReference<>(activity);
        }

        @Override
        protected Boolean doInBackground(Void... voids) {
            HttpURLConnection connection = null;
            try {
                final String PROTOCOL = "http://";
                final String IP = "10.0.2.2";
                final String PORT = ":3000";
                String path = "/getSMS";
                String webAddress = PROTOCOL + IP + PORT + path;
                URL url = new URL(webAddress);
                connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.connect();

                BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }
                reader.close();

                JSONObject message = new JSONObject(response.toString());
                if (message.has("id")) {
                    String phone = message.getString("phone");
                    String body = message.getString("message_body");

                    Log.i(TAG, "Sending SMS to " + phone + ": " + body);

                    try {
                        SmsManager smsManager = SmsManager.getDefault();
                        smsManager.sendTextMessage(phone, null, body, null, null);
                        Log.i(TAG, "SMS Sent Successfully");
                        return true;
                    } catch (Exception e) {
                        Log.e(TAG, "Failed to send SMS: " + e.getMessage());
                        e.printStackTrace();
                    }
                } else {
                    Log.i(TAG, "No unsent messages available.");
                }
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
            return false;
        }

        @Override
        protected void onPostExecute(Boolean success) {
            super.onPostExecute(success);
            MainActivity activity = weakReference.get();
            if (activity == null || activity.isFinishing()) {
                return;
            }
            if (success) {
                activity.sentMessagesCount++;
                activity.updateTextView();
            }
        }
    }
}
