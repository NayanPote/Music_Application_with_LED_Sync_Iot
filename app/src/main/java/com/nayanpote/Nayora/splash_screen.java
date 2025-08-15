package com.nayanpote.Nayora;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Intent;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.view.animation.AccelerateDecelerateInterpolator;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.airbnb.lottie.LottieAnimationView;
import com.nayanpote.musicalledsbynayan.R;

public class splash_screen extends AppCompatActivity {

    private static final int SPLASH_TIME_OUT = 2700; // 2.7 sec

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_splash_screen);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.splash_main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

//        setupGradientAnimation();
        setupStatusBar();

        // You can also animate your logo with a scale-up effect
        View logoImage = findViewById(R.id.logoImage);
        ObjectAnimator logoAnim = ObjectAnimator.ofFloat(logoImage, "scaleX", 0.8f, 1.2f, 1.0f);
        logoAnim.setDuration(1200);
        logoAnim.setInterpolator(new AccelerateDecelerateInterpolator());
        logoAnim.start();

        ObjectAnimator logoAnimY = ObjectAnimator.ofFloat(logoImage, "scaleY", 0.8f, 1.2f, 1.0f);
        logoAnimY.setDuration(1200);
        logoAnimY.setInterpolator(new AccelerateDecelerateInterpolator());
        logoAnimY.start();

        // Optional: play Lottie animation programmatically if you want control
        LottieAnimationView lottieView = findViewById(R.id.lottie);
        lottieView.playAnimation();

        // Splash Delay
        new Handler().postDelayed(() -> {
            startActivity(new Intent(this, MainActivity.class));
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
            finish();
        }, SPLASH_TIME_OUT);
    }

    private void setupStatusBar() {
        // Handle different Android versions for fullscreen and notch
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Android 11 (API 30) and above
            getWindow().setDecorFitsSystemWindows(false);
            WindowInsetsController controller = getWindow().getInsetsController();
            if (controller != null) {
                controller.hide(WindowInsetsCompat.Type.statusBars() | WindowInsetsCompat.Type.navigationBars());
                controller.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            // Android 9 (API 28) and above - Handle notch
            getWindow().setStatusBarColor(Color.TRANSPARENT);
            getWindow().setNavigationBarColor(Color.TRANSPARENT);

            // Handle notch area
            WindowManager.LayoutParams layoutParams = getWindow().getAttributes();
            layoutParams.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;

            getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE |
                            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN |
                            View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION |
                            View.SYSTEM_UI_FLAG_FULLSCREEN |
                            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
                            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            );
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            // Android 5 (API 21) to Android 8.1 (API 27)
            getWindow().setStatusBarColor(Color.TRANSPARENT);
            getWindow().setNavigationBarColor(Color.TRANSPARENT);
            getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE |
                            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN |
                            View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION |
                            View.SYSTEM_UI_FLAG_FULLSCREEN |
                            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
                            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            );
        } else {
            // Below Android 5
            getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_FULLSCREEN |
                            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
                            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            );
        }
    }

//    private void setupGradientAnimation() {
//        View gradientOverlay = findViewById(R.id.gradientOverlay);
//        if (gradientOverlay != null) {
//            ObjectAnimator gradientAnimator = ObjectAnimator.ofFloat(gradientOverlay, "alpha", 0.3f, 0.7f, 0.3f);
//            gradientAnimator.setDuration(1200);
//            gradientAnimator.setRepeatCount(ValueAnimator.INFINITE);
//            gradientAnimator.setRepeatMode(ValueAnimator.REVERSE);
//            gradientAnimator.setInterpolator(new AccelerateDecelerateInterpolator());
//            gradientAnimator.start();
//        }
//    }
}
