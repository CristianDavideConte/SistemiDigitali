package com.example.sistemidigitali.views;

import static com.example.sistemidigitali.debugUtility.Debug.println;

import android.content.Intent;
import android.graphics.Rect;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.ConstraintLayout;

import com.example.sistemidigitali.R;
import com.example.sistemidigitali.customEvents.OverlayVisibilityChangeEvent;

import org.greenrobot.eventbus.EventBus;

public class PopUpActivity extends AppCompatActivity {

    public static String POP_UP_TEXT_1 = "com.example.sistemidigitali.POP_UP_TEXT_1";
    public static String POP_UP_TEXT_2 = "com.example.sistemidigitali.POP_UP_TEXT_2";
    public static String POP_UP_TEXT_3 = "com.example.sistemidigitali.POP_UP_TEXT_3";
    public static String POP_UP_BACKGROUND_COLOR = "com.example.sistemidigitali.POP_UP_BACKGROUND_COLOR";
    public static String POP_UP_TEXT_COLOR = "com.example.sistemidigitali.POP_UP_TEXT_COLOR";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_pop_up);

        TextView text1 = this.findViewById(R.id.popUpText1);
        TextView text2 = this.findViewById(R.id.popUpText2);
        TextView text3 = this.findViewById(R.id.popUpText3);
        ConstraintLayout popUpLayout = this.findViewById(R.id.popUpLayout);

        Rect windowBounds = this.getWindowManager().getCurrentWindowMetrics().getBounds();
        final int width = (int) Math.min(windowBounds.width() * 0.8, 960);   //1920 * 0.5 = 960
        final int height = (int) Math.max(windowBounds.height() * 0.2, 384); //1920 * 0.2 = 384
        println(windowBounds.height() * 0.2);
        this.getWindow().setLayout(width, height);

        WindowManager.LayoutParams params = this.getWindow().getAttributes();
        params.gravity = Gravity.CENTER;
        params.x = 0;
        params.y = 0;

        this.getWindow().setAttributes(params);
        Intent intent = this.getIntent();
        text1.setText(intent.getStringExtra(PopUpActivity.POP_UP_TEXT_1));
        text1.setTextColor(Integer.parseInt(intent.getStringExtra(PopUpActivity.POP_UP_TEXT_COLOR)));
        text2.setText(intent.getStringExtra(PopUpActivity.POP_UP_TEXT_2));
        text2.setTextColor(Integer.parseInt(intent.getStringExtra(PopUpActivity.POP_UP_TEXT_COLOR)));
        text3.setText(intent.getStringExtra(PopUpActivity.POP_UP_TEXT_3));
        text3.setTextColor(Integer.parseInt(intent.getStringExtra(PopUpActivity.POP_UP_TEXT_COLOR)));
        GradientDrawable gradientDrawable = (GradientDrawable) popUpLayout.getBackground();
        gradientDrawable.setColor(Integer.parseInt(intent.getStringExtra(PopUpActivity.POP_UP_BACKGROUND_COLOR)));

        EventBus.getDefault().postSticky(new OverlayVisibilityChangeEvent(View.VISIBLE));
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        EventBus.getDefault().removeStickyEvent(OverlayVisibilityChangeEvent.class);
        EventBus.getDefault().post(new OverlayVisibilityChangeEvent(View.GONE));
    }
}