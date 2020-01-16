package com.neilsmiker.textmessenger;

import android.Manifest;
import android.annotation.TargetApi;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.TaskStackBuilder;
import android.content.BroadcastReceiver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.provider.Telephony;
import android.telephony.SmsMessage;
import android.util.Log;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

public class SMSreceiver extends BroadcastReceiver {
    //private static final String TAG = "SMSreceiver";
    public static final String pdu_type = "pdus";
    private final String CHANNEL_ID = "textosaurus";

    @TargetApi(Build.VERSION_CODES.M)
    @Override
    public void onReceive(Context context, Intent intent) {
        // Get the SMS message.
        Bundle bundle = intent.getExtras();
        if (bundle == null){
            return;
        }
        SmsMessage[] msgs;
        String format = bundle.getString("format");
        // Retrieve the SMS message received.
        Object[] pdus = (Object[]) bundle.get(pdu_type);
        if (pdus != null && Telephony.Sms.getDefaultSmsPackage(context).equals(context.getPackageName())) {
            // Check the Android version.
            boolean isVersionM =
                    (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M);
            // Fill the msgs array.
            msgs = new SmsMessage[pdus.length];

            createNotificationChannel(context);

            for (int i = 0; i < msgs.length; i++) {
                // Check Android version and use appropriate createFromPdu.
                if (isVersionM) {
                    // If Android version M or newer:
                    msgs[i] = SmsMessage.createFromPdu((byte[]) pdus[i], format);
                } else {
                    // If Android version L or older:
                    msgs[i] = SmsMessage.createFromPdu((byte[]) pdus[i]);
                }
                // Build the message to show.
                long timestamp = msgs[i].getTimestampMillis();
                String address = msgs[i].getOriginatingAddress();
                String displayName = getContactName(address, context);
                String message = msgs[i].getMessageBody();

                //Push Notification
                // Create an Intent for the activity you want to start
                Intent smsThreadIntent = new Intent(context, MainActivitySMS.class);
                // Create the TaskStackBuilder and add the intent, which inflates the back stack
                smsThreadIntent.putExtra("selectedAddress", address);
                smsThreadIntent.putExtra("selectedName", displayName);
                TaskStackBuilder stackBuilder = TaskStackBuilder.create(context);
                stackBuilder.addNextIntentWithParentStack(smsThreadIntent);
                // Get the PendingIntent containing the entire back stack
                PendingIntent smsThreadPendingIntent = stackBuilder.getPendingIntent(0, PendingIntent.FLAG_UPDATE_CURRENT);

                NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                        .setSmallIcon(R.drawable.icons8_dinosaur_96)
                        .setContentTitle(displayName)
                        .setContentText(message)
                        .setStyle(new NotificationCompat.BigTextStyle()
                                .bigText(message))
                        .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                        .setContentIntent(smsThreadPendingIntent);

                NotificationManagerCompat notificationManager = NotificationManagerCompat.from(context);

                // notificationId is a unique int for each notification that you must define
                notificationManager.notify((int) System.currentTimeMillis(), builder.build());

                //Write to SMS Provider
                ContentValues values = new ContentValues();
                values.put(Telephony.Sms.ADDRESS, address);
                values.put(Telephony.Sms.BODY, message);
                context.getContentResolver().insert(Telephony.Sms.Inbox.CONTENT_URI, values);
            }
        }
    }

    private void createNotificationChannel(Context context) {
        // Create the NotificationChannel, but only on API 26+ because
        // the NotificationChannel class is new and not in the support library
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            CharSequence name = context.getString(R.string.channel_name);
            String description = context.getString(R.string.channel_description);
            int importance = NotificationManager.IMPORTANCE_DEFAULT;
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, name, importance);
            channel.setDescription(description);
            // Register the channel with the system; you can't change the importance
            // or other notification behaviors after this
            NotificationManager notificationManager = context.getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(channel);
        }
    }

    public String getContactName(final String phoneNumber, Context context)
    {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS)
                + ContextCompat.checkSelfPermission(
                context, Manifest.permission.SEND_SMS)
                + ContextCompat.checkSelfPermission(
                context, Manifest.permission.RECEIVE_SMS)
                + ContextCompat.checkSelfPermission(
                context, Manifest.permission.READ_CONTACTS)
                + ContextCompat.checkSelfPermission(
                context, Manifest.permission.WRITE_CONTACTS)
                == PackageManager.PERMISSION_GRANTED) {
            Uri uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(phoneNumber));

            String[] projection = new String[]{ContactsContract.PhoneLookup.DISPLAY_NAME};

            String contactName = phoneNumber;
            Cursor cursor = context.getContentResolver().query(uri, projection, null, null, null);

            if (cursor != null) {
                if (cursor.moveToFirst()) {
                    contactName = cursor.getString(0);
                    if (contactName.equals("")){
                        contactName = phoneNumber;
                    }
                }
                cursor.close();
            }

            return contactName;
        }
        return phoneNumber;
    }

    //TODO Implement AsyncTask?
}
