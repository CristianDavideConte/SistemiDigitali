package com.example.sistemidigitali.views;

import android.content.Intent;
import android.graphics.Rect;
import android.graphics.drawable.GradientDrawable;
import android.opengl.Visibility;
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

    private TextView text1;
    private TextView text2;
    private TextView text3;
    private ConstraintLayout popUpLayout;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_pop_up);

        this.text1 = this.findViewById(R.id.popUpText1);
        this.text2 = this.findViewById(R.id.popUpText2);
        this.text3 = this.findViewById(R.id.popUpText3);
        this.popUpLayout = this.findViewById(R.id.popUpLayout);

        Rect windowBounds = this.getWindowManager().getCurrentWindowMetrics().getBounds();
        this.getWindow().setLayout((int)(windowBounds.width() * 0.8), (int)(windowBounds.height() * 0.2));

        WindowManager.LayoutParams params = this.getWindow().getAttributes();
        params.gravity = Gravity.CENTER;
        params.x = 0;
        params.y = 0;

        this.getWindow().setAttributes(params);
        Intent intent = this.getIntent();
        this.text1.setText(intent.getStringExtra(PopUpActivity.POP_UP_TEXT_1));
        this.text1.setTextColor(Integer.parseInt(intent.getStringExtra(PopUpActivity.POP_UP_TEXT_COLOR)));
        this.text2.setText(intent.getStringExtra(PopUpActivity.POP_UP_TEXT_2));
        this.text2.setTextColor(Integer.parseInt(intent.getStringExtra(PopUpActivity.POP_UP_TEXT_COLOR)));
        this.text3.setText(intent.getStringExtra(PopUpActivity.POP_UP_TEXT_3));
        this.text3.setTextColor(Integer.parseInt(intent.getStringExtra(PopUpActivity.POP_UP_TEXT_COLOR)));
        GradientDrawable gradientDrawable = (GradientDrawable) this.popUpLayout.getBackground();
        gradientDrawable.setColor(Integer.parseInt(intent.getStringExtra(PopUpActivity.POP_UP_BACKGROUND_COLOR)));

        EventBus.getDefault().post(new OverlayVisibilityChangeEvent(View.VISIBLE));
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        EventBus.getDefault().post(new OverlayVisibilityChangeEvent(View.GONE));
    }
}