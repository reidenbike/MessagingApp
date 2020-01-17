package com.neilsmiker.textmessenger;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

public class SmsMessageAdapter extends ArrayAdapter<Sms> {

    //private String TAG = "MESSAGE_ADAPTER";
    private int width;
    private int timestampWidth = 0;
    private Context context;
    private SimpleDateFormat hourDateFormat = new SimpleDateFormat("h:mm a", Locale.getDefault());
    //private SimpleDateFormat hour24DateFormat = new SimpleDateFormat("HH:mm", Locale.getDefault());
    private SimpleDateFormat dayDateFormat = new SimpleDateFormat("EEEE, MMMM dd, yyyy", Locale.getDefault());

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

            if (folder.equals("sent")){
                convertView = ((Activity) getContext()).getLayoutInflater().inflate(R.layout.item_message_user, parent, false);
            } else {
                convertView = ((Activity) getContext()).getLayoutInflater().inflate(R.layout.item_message_other, parent, false);
            }

            ImageView photoImageView = convertView.findViewById(R.id.photoImageView);
            final TextView messageTextView = convertView.findViewById(R.id.messageTextView);
            TextView authorTextView = convertView.findViewById(R.id.nameTextView);
            final TextView txtTimestamp = convertView.findViewById(R.id.txtTimestamp);
            LinearLayout textBubble = convertView.findViewById(R.id.textBubble);

            String timestamp = message.getTime();

            if (timestamp != null) {
                Date date = new Date(Long.parseLong(timestamp));
                String messageDate = hourDateFormat.format(date);
                txtTimestamp.setText(messageDate);
            }

            /*boolean isPhoto = false *//*message.getPhotoUrl() != null*//*;
            if (isPhoto) {
                *//*messageTextView.setVisibility(View.GONE);
                photoImageView.setVisibility(View.VISIBLE);
                Glide.with(photoImageView.getContext())
                        .load(message.getPhotoUrl())
                        .into(photoImageView);*//*
            } else {*/
                messageTextView.setVisibility(View.VISIBLE);
                photoImageView.setVisibility(View.GONE);
                messageTextView.setText(message.getMsg());
            //}

            //Set the max width of the text bubbles based on the screen width and the size of the timestamp. There's probably
            // a much more efficient way to do this! Checking the timeStampWidth == 0 helps but a more efficient method is required.
            if (timestampWidth == 0) {
                ViewTreeObserver vto = txtTimestamp.getViewTreeObserver();
                vto.addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
                    @Override
                    public void onGlobalLayout() {
                        timestampWidth = txtTimestamp.getWidth() + txtTimestamp.getPaddingEnd() + txtTimestamp.getPaddingStart();
                        messageTextView.setMaxWidth(width - timestampWidth);
                        txtTimestamp.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                    }
                });
            } else {
                messageTextView.setMaxWidth(width - timestampWidth);
            }

            authorTextView.setText(userName);

            if (position < getCount() - 1) {
                String nextUserName = Objects.requireNonNull(getItem(position + 1)).getDisplayName();
                long nextTimestamp = Long.parseLong(Objects.requireNonNull(getItem(position + 1)).getTime());

                String nextDay = dayDateFormat.format(nextTimestamp);
                String currentDay = dayDateFormat.format(Long.parseLong(Objects.requireNonNull(timestamp)));


                if (folder.equals("inbox")) {
                    if (nextUserName != null && nextUserName.equals(userName)) {
                        authorTextView.setVisibility(View.GONE);
                        if (nextTimestamp - Long.parseLong(Objects.requireNonNull(timestamp)) < 120000
                                && nextDay.equals(currentDay)) {
                            txtTimestamp.setVisibility(View.INVISIBLE);
                        } else if (nextDay.equals(currentDay)) {
                            authorTextView.setVisibility(View.INVISIBLE);
                        }
                        if (nextTimestamp - Long.parseLong(Objects.requireNonNull(timestamp)) < 120000) {
                            txtTimestamp.setVisibility(View.INVISIBLE);
                        } else {
                            authorTextView.setVisibility(View.INVISIBLE);
                        }
                    }
                } else {
                    String nextFolderName = Objects.requireNonNull(getItem(position + 1)).getFolderName();
                    if (nextFolderName != null && nextFolderName.equals(folder)) {
                        authorTextView.setVisibility(View.GONE);
                        if (nextTimestamp - Long.parseLong(Objects.requireNonNull(timestamp)) < 120000
                                && nextDay.equals(currentDay)) {
                            txtTimestamp.setVisibility(View.INVISIBLE);
                        } else if (nextDay.equals(currentDay)) {
                            authorTextView.setVisibility(View.INVISIBLE);
                        }
                    } else {
                        authorTextView.setVisibility(View.INVISIBLE);
                    }
                }

                if (nextDay != null && !nextDay.equals(currentDay)) {
                    TextView dayTimestamp = convertView.findViewById(R.id.dayTimestamp);
                    dayTimestamp.setText(nextDay);
                } else {
                    convertView.findViewById(R.id.dayTimestampLayout).setVisibility(View.GONE);
                }
            } else {
                authorTextView.setVisibility(View.GONE);
                convertView.findViewById(R.id.dayTimestampLayout).setVisibility(View.GONE);
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
