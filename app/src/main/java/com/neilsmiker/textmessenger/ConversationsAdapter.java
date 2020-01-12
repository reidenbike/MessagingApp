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
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.constraintlayout.widget.ConstraintLayout;

import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Objects;

public class ConversationsAdapter extends ArrayAdapter<Sms> {

    private String TAG = "CONVERSATION_ADAPTER";
    private int width;
    private Context context;

    ConversationsAdapter(Context context, int resource, List<Sms> objects, int width) {
        super(context, resource, objects);
        this.width = width;
        this.context = context;
    }

    @Override
    @NonNull
    public View getView(int position, View convertView, @NonNull ViewGroup parent) {
        Sms message = getItem(position);
        String userName;
        String folder;
        if (message != null) {
            userName = message.getDisplayName();
            folder = message.getFolderName();


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
                long dv = Long.valueOf(timestamp);
                Date df = new Date(dv);
                String vv = new SimpleDateFormat("h:mma").format(df);
                txtTimestamp.setText(vv);
            }

            boolean isPhoto = false /*message.getPhotoUrl() != null*/;
            if (isPhoto) {
                /*messageTextView.setVisibility(View.GONE);
                photoImageView.setVisibility(View.VISIBLE);
                Glide.with(photoImageView.getContext())
                        .load(message.getPhotoUrl())
                        .into(photoImageView);*/
            } else {
                txtLastMessage.setVisibility(View.VISIBLE);
                txtLastMessage.setText(message.getMsg());
                txtLastMessage.setMaxWidth(width);
            }

            txtProfileName.setText(userName);

            if (message.getReadState().equals("1")){
                txtUnreadBadge.setVisibility(View.GONE);
            } else {
                txtUnreadBadge.setVisibility(View.VISIBLE);
                txtUnreadBadge.setText("1");
            }

            selectListItem(message,txtProfileName,txtLastMessage,conversationLayout);
        }

        return convertView;
    }

    private void selectListItem(Sms message, TextView name, TextView lastMessage, ConstraintLayout conversationBubble){
        boolean isUser = message.getFolderName().equals("sent");
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
