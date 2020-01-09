package com.neilsmiker.textmessenger;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Objects;

public class SmsMessageAdapter extends ArrayAdapter<Sms> {

    private String TAG = "MESSAGE_ADAPTER";
    private int width;
    private Context context;

    SmsMessageAdapter(Context context, int resource, List<Sms> objects, int width) {
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

            //if (convertView == null) {
                if (folder.equals("sent")){
                    convertView = ((Activity) getContext()).getLayoutInflater().inflate(R.layout.item_message_user, parent, false);
                } else {
                    convertView = ((Activity) getContext()).getLayoutInflater().inflate(R.layout.item_message_other, parent, false);
                }
            //}

            ImageView photoImageView = convertView.findViewById(R.id.photoImageView);
            TextView messageTextView = convertView.findViewById(R.id.messageTextView);
            TextView authorTextView = convertView.findViewById(R.id.nameTextView);
            TextView txtTimestamp = convertView.findViewById(R.id.txtTimestamp);
            LinearLayout textBubble = convertView.findViewById(R.id.textBubble);

            String timestamp = message.getTime();

            if (timestamp != null) {
                long dv = Long.valueOf(timestamp);
                Date df = new Date(dv);
                String vv = new SimpleDateFormat("MM/dd/yyyy hh:mma").format(df);
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
                messageTextView.setVisibility(View.VISIBLE);
                photoImageView.setVisibility(View.GONE);
                messageTextView.setText(message.getMsg());
                messageTextView.setMaxWidth(width);
            }

            authorTextView.setText(userName);

            if (folder.equals("inbox")) {
                if (position < getCount() - 1) {
                    String nextUserName = Objects.requireNonNull(getItem(position + 1)).getDisplayName();

                    if (nextUserName != null && nextUserName.equals(userName)){
                        authorTextView.setVisibility(View.GONE);
                    }
                }
            } else {
                if (position < getCount() - 1) {
                    String nextFolderName = Objects.requireNonNull(getItem(position + 1)).getFolderName();

                    if (nextFolderName != null && nextFolderName.equals(folder)){
                        authorTextView.setVisibility(View.GONE);
                    } else {
                        authorTextView.setVisibility(View.INVISIBLE);
                    }
                } else {
                    authorTextView.setVisibility(View.GONE);
                }
            }


            selectListItem(message,messageTextView,textBubble);
        }

        return convertView;
    }

    private void selectListItem(Sms message, TextView tv, LinearLayout textBubble){
        boolean isUser = message.getFolderName().equals("sent");
        if (!message.isSelected()){
            if (isUser) {
                textBubble.setBackground(context.getDrawable(R.drawable.text_bubble_user));
            } else {
                textBubble.setBackground(context.getDrawable(R.drawable.text_bubble_other));
            }
            tv.setTextColor(Color.parseColor("#000000"));
        } else {
            if (isUser) {
                textBubble.setBackground(context.getDrawable(R.drawable.text_bubble_user_selected));
            } else {
                textBubble.setBackground(context.getDrawable(R.drawable.text_bubble_other_selected));
            }
            tv.setTextColor(context.getResources().getColor(R.color.colorTitle));
        }
    }
}
