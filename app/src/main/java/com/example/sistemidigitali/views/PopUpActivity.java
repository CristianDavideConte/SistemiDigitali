package com.example.sistemidigitali.views;

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

    public final static String POP_UP_TEXT_1 = "com.example.sistemidigitali.POP_UP_TEXT_1";
    public final static String POP_UP_TEXT_2 = "com.example.sistemidigitali.POP_UP_TEXT_2";
    public final static String POP_UP_TEXT_3 = "com.example.sistemidigitali.POP_UP_TEXT_3";
    public final static String POP_UP_TEXT_COLOR = "com.example.sistemidigitali.POP_UP_TEXT_COLOR";
    public final static String POP_UP_BACKGROUND_COLOR = "com.example.sistemidigitali.POP_UP_BACKGROUND_COLOR";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_pop_up);

        Intent intent = this.getIntent();
        TextView text1 = this.findViewById(R.id.popUpText1);
        TextView text2 = this.findViewById(R.id.popUpText2);
        TextView text3 = this.findViewById(R.id.popUpText3);
        ConstraintLayout popUpLayout = this.findViewById(R.id.popUpLayout);

        //Force the pop-up to always have the correct sizes
        Rect windowBounds = this.getWindowManager().getCurrentWindowMetrics().getBounds();
        final int width  = (int) Math.min(windowBounds.width()  * 0.8, 960); //1920 * 0.5 = 960
        final int height = (int) Math.max(windowBounds.height() * 0.2, 384); //1920 * 0.2 = 384
        this.getWindow().setLayout(width, height);

        WindowManager.LayoutParams params = this.getWindow().getAttributes();
        params.gravity = Gravity.CENTER;
        params.x = 0;
        params.y = 0;
        this.getWindow().setAttributes(params);

        //Sets the text fields to display the correct texts
        text1.setText(intent.getStringExtra(PopUpActivity.POP_UP_TEXT_1)); //Wearing mode
        text2.setText(intent.getStringExtra(PopUpActivity.POP_UP_TEXT_2)); //Mask type (optional)
        text3.setText(intent.getStringExtra(PopUpActivity.POP_UP_TEXT_3)); //Accuracy

        //Sets the text fields to have the correct texts colors
        text1.setTextColor(Integer.parseInt(intent.getStringExtra(PopUpActivity.POP_UP_TEXT_COLOR))); //Wearing mode
        text2.setTextColor(Integer.parseInt(intent.getStringExtra(PopUpActivity.POP_UP_TEXT_COLOR))); //Mask type (optional)
        text3.setTextColor(Integer.parseInt(intent.getStringExtra(PopUpActivity.POP_UP_TEXT_COLOR))); //Accuracy

        //Sets the pop-up background to the correct color
        GradientDrawable gradientDrawable = (GradientDrawable) popUpLayout.getBackground();
        gradientDrawable.setColor(Integer.parseInt(intent.getStringExtra(PopUpActivity.POP_UP_BACKGROUND_COLOR)));

        EventBus.getDefault().postSticky(new OverlayVisibilityChangeEvent(View.VISIBLE)); //Sticky for orientation related coherence
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        EventBus.getDefault().removeStickyEvent(OverlayVisibilityChangeEvent.class);
        EventBus.getDefault().post(new OverlayVisibilityChangeEvent(View.GONE));
    }
}