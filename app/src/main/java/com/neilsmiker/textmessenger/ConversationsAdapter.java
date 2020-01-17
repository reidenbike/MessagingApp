package com.neilsmiker.textmessenger;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.net.Uri;
import android.provider.ContactsContract;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.constraintlayout.widget.ConstraintLayout;
import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class ConversationsAdapter extends ArrayAdapter<Sms> {

    //private String TAG = "CONVERSATION_ADAPTER";
    private Context context;
    private SimpleDateFormat hourDateFormat = new SimpleDateFormat("h:mm a", Locale.getDefault());
    //private SimpleDateFormat hour24DateFormat = new SimpleDateFormat("HH:mm", Locale.getDefault());
    private SimpleDateFormat dayDateFormat = new SimpleDateFormat("M/d/yy", Locale.getDefault());
    private String currentDate;

    ConversationsAdapter(Context context, int resource, List<Sms> objects) {
        super(context, resource, objects);
        this.context = context;
        currentDate = dayDateFormat.format(new Date(System.currentTimeMillis()));
    }

    @Override
    @NonNull
    public View getView(int position, View convertView, @NonNull ViewGroup parent) {
        Sms message = getItem(position);
        String userName;
        if (message != null) {
            userName = message.getDisplayName();

            convertView = ((Activity) getContext()).getLayoutInflater().inflate(R.layout.item_conversation, parent, false);

            ImageView profilePic = convertView.findViewById(R.id.profilePic);
            profilePic.setImageBitmap(getProfilePic(context,message.getAddress()));
            profilePic.setClipToOutline(true);

            TextView txtLastMessage = convertView.findViewById(R.id.txtLastMessage);
            TextView txtProfileName = convertView.findViewById(R.id.profileName);
            TextView txtTimestamp = convertView.findViewById(R.id.txtTimestamp);
            TextView txtUnreadBadge = convertView.findViewById(R.id.txtUnreadBadge);
            ConstraintLayout conversationLayout = convertView.findViewById(R.id.conversationLayout);

            String timestamp = message.getTime();

            if (timestamp != null) {
                Date date = new Date(Long.valueOf(timestamp));
                String messageDate = dayDateFormat.format(date);
                if (messageDate.equals(currentDate)){
                    messageDate = hourDateFormat.format(date);
                }
                txtTimestamp.setText(messageDate);
            }

            txtLastMessage.setVisibility(View.VISIBLE);
            txtLastMessage.setText(message.getMsg());

            txtProfileName.setText(userName);

            if (message.getNumberUnread().equals("0")){
                txtUnreadBadge.setVisibility(View.GONE);
            } else {
                txtUnreadBadge.setVisibility(View.VISIBLE);
                txtUnreadBadge.setText(message.getNumberUnread());
            }

            selectListItem(message,txtProfileName,txtLastMessage,conversationLayout);
        }

        return convertView;
    }

    private void selectListItem(Sms message, TextView name, TextView lastMessage, ConstraintLayout conversationBubble){
        if (!message.isSelected()){
            conversationBubble.setBackground(context.getDrawable(R.drawable.conversation_bubble));
            name.setTextColor(Color.parseColor("#000000"));
            lastMessage.setTextColor(Color.parseColor("#000000"));
        } else {
            conversationBubble.setBackground(context.getDrawable(R.drawable.text_bubble_user_selected));
            name.setTextColor(context.getResources().getColor(R.color.colorTitle));
            lastMessage.setTextColor(context.getResources().getColor(R.color.colorTitle));
        }
    }

    private Bitmap getProfilePic(Context context, String number) {
        ContentResolver contentResolver = context.getContentResolver();
        String contactId = null;
        Uri uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number));

        String[] projection = new String[]{ContactsContract.PhoneLookup.DISPLAY_NAME, ContactsContract.PhoneLookup._ID};

        Cursor cursor =
                contentResolver.query(
                        uri,
                        projection,
                        null,
                        null,
                        null);

        if (cursor != null) {
            while (cursor.moveToNext()) {
                contactId = cursor.getString(cursor.getColumnIndexOrThrow(ContactsContract.PhoneLookup._ID));
            }
            cursor.close();
        }

        Bitmap photo = BitmapFactory.decodeResource(context.getResources(),
                R.drawable.image_circle);

        try {
            InputStream inputStream = null;
            if (contactId != null) {
                inputStream = ContactsContract.Contacts.openContactPhotoInputStream(context.getContentResolver(),
                        ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI, Long.valueOf(contactId)));
            }

            if (inputStream != null) {
                photo = BitmapFactory.decodeStream(inputStream);
            }

            if (inputStream != null) {
                inputStream.close();
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
        return photo;
    }
}
