package com.nayanpote.Nayora;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.nayanpote.musicalledsbynayan.R;
import com.nayanpote.musicalledsbynayan.databinding.ActivityDeveloperZoneBinding;

import java.io.File;

public class developerZone extends AppCompatActivity {

    private ActivityDeveloperZoneBinding binding;
    private Handler animationHandler;
    private boolean isAnimating = false;
    private ObjectAnimator gradientAnimator;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);

        // Initialize view binding
        binding = ActivityDeveloperZoneBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        setupStatusBar();
        setupWindowInsets();
        initializeViews();
        setupAnimations();
        setupClickListeners();
        setupGradientAnimation();
        startContinuousAnimations();
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

    private void setupWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.main, (v, insets) -> {
            // Don't apply any padding to avoid notch issues
            // The layout will extend into system areas
            return WindowInsetsCompat.CONSUMED;
        });
    }

    private void initializeViews() {
        // Initialize handler for animations
        animationHandler = new Handler(Looper.getMainLooper());

        // All views are now accessible through binding
        // binding.backButton, binding.shareButton, etc.
    }

    private void setupAnimations() {
        // Initial entrance animations using binding references
        animateCardEntrance(binding.appInfoCard, 0);
        animateCardEntrance(binding.developerInfoCard, 200);
        animateCardEntrance(binding.featuresCard, 400);
    }

    private void animateCardEntrance(View view, long delay) {
        view.setAlpha(0f);
        view.setTranslationY(100f);

        view.animate()
                .alpha(1f)
                .translationY(0f)
                .setStartDelay(delay)
                .setDuration(800)
                .setInterpolator(new AccelerateDecelerateInterpolator())
                .start();
    }

    private void animateButtonEntrance(View view, long delay) {
        view.setAlpha(0f);
        view.setScaleX(0.8f);
        view.setScaleY(0.8f);

        view.animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .setStartDelay(delay)
                .setDuration(600)
                .setInterpolator(new AccelerateDecelerateInterpolator())
                .start();
    }

    private void setupClickListeners() {
        // Back button using binding
        binding.backButton.setOnClickListener(v -> {
            animateButtonPress(v);
            new Handler(Looper.getMainLooper()).postDelayed(this::handleBackPress, 10);
        });

        // Share button using binding
        binding.shareButton.setOnClickListener(v -> {
            animateButtonPress(v);
            new Handler(Looper.getMainLooper()).postDelayed(this::shareApp, 150);
        });

        // Email card using binding
        binding.emailCard.setOnClickListener(v -> {
            animateCardPress(v);
            new Handler(Looper.getMainLooper()).postDelayed(this::openEmail, 150);
        });

        // Phone card using binding
        binding.phoneCard.setOnClickListener(v -> {
            animateCardPress(v);
            new Handler(Looper.getMainLooper()).postDelayed(this::openPhone, 150);
        });

        // Long press animations
        setupLongPressAnimations();
    }

    private void setupLongPressAnimations() {
        View.OnLongClickListener pulseAnimation = v -> {
            createPulseAnimation(v);
            return true;
        };

        binding.appInfoCard.setOnLongClickListener(pulseAnimation);
        binding.developerInfoCard.setOnLongClickListener(pulseAnimation);
        binding.featuresCard.setOnLongClickListener(pulseAnimation);
    }

    private void animateButtonPress(View view) {
        view.animate()
                .scaleX(0.95f)
                .scaleY(0.95f)
                .setDuration(100)
                .withEndAction(() ->
                        view.animate()
                                .scaleX(1f)
                                .scaleY(1f)
                                .setDuration(100)
                                .start())
                .start();
    }

    private void animateCardPress(View view) {
        view.animate()
                .scaleX(0.98f)
                .scaleY(0.98f)
                .alpha(0.8f)
                .setDuration(100)
                .withEndAction(() ->
                        view.animate()
                                .scaleX(1f)
                                .scaleY(1f)
                                .alpha(1f)
                                .setDuration(100)
                                .start())
                .start();
    }

    private void setupGradientAnimation() {
        if (binding.gradientOverlay != null) {
            gradientAnimator = ObjectAnimator.ofFloat(binding.gradientOverlay, "alpha", 0.3f, 0.8f, 0.3f);
            gradientAnimator.setDuration(4000);
            gradientAnimator.setRepeatCount(ValueAnimator.INFINITE);
            gradientAnimator.setInterpolator(new AccelerateDecelerateInterpolator());
            gradientAnimator.start();
        }

    }

    private void createPulseAnimation(View view) {
        ObjectAnimator scaleX = ObjectAnimator.ofFloat(view, "scaleX", 1f, 1.05f, 1f);
        ObjectAnimator scaleY = ObjectAnimator.ofFloat(view, "scaleY", 1f, 1.05f, 1f);

        scaleX.setDuration(600);
        scaleY.setDuration(600);
        scaleX.setInterpolator(new AccelerateDecelerateInterpolator());
        scaleY.setInterpolator(new AccelerateDecelerateInterpolator());

        scaleX.start();
        scaleY.start();
    }

    private void startContinuousAnimations() {
        // Continuous floating animation for app info card
        startFloatingAnimation(binding.appInfoCard, 2000);

        // Continuous glow animation for share button
        startGlowAnimation(binding.shareButton, 3000);

        // Rotating animation for version/build numbers
        startNumberAnimation();
    }

    private void startFloatingAnimation(View view, long duration) {
        ObjectAnimator animator = ObjectAnimator.ofFloat(view, "translationY", 0f, -10f, 0f);
        animator.setDuration(duration);
        animator.setInterpolator(new AccelerateDecelerateInterpolator());
        animator.setRepeatCount(ValueAnimator.INFINITE);
        animator.start();
    }

    private void startGlowAnimation(View view, long duration) {
        ObjectAnimator animator = ObjectAnimator.ofFloat(view, "alpha", 1f, 0.7f, 1f);
        animator.setDuration(duration);
        animator.setInterpolator(new AccelerateDecelerateInterpolator());
        animator.setRepeatCount(ValueAnimator.INFINITE);
        animator.start();
    }

    private void startNumberAnimation() {
        animationHandler.postDelayed(() -> {
            if (!isFinishing()) {
                // Uncomment if you want version number rotation
                // ObjectAnimator rotateVersion = ObjectAnimator.ofFloat(binding.versionNumber, "rotation", 0f, 360f);
                // rotateVersion.setDuration(1000);
                // rotateVersion.start();

                ObjectAnimator scaleBuild = ObjectAnimator.ofFloat(binding.buildNumber, "scaleX", 1f, 1.2f, 1f);
                ObjectAnimator scaleBuildY = ObjectAnimator.ofFloat(binding.buildNumber, "scaleY", 1f, 1.2f, 1f);
                scaleBuild.setDuration(500);
                scaleBuildY.setDuration(500);
                scaleBuild.start();
                scaleBuildY.start();

                startNumberAnimation(); // Repeat
            }
        }, 5000);
    }

    private void shareApp() {
        try {
            ApplicationInfo app = getApplicationContext().getApplicationInfo();
            String filePath = app.sourceDir;
            File originalApk = new File(filePath);

            if (originalApk.exists()) {
                Uri apkUri = FileProvider.getUriForFile(this,
                        getPackageName() + ".fileprovider", originalApk);

                Intent shareIntent = new Intent(Intent.ACTION_SEND);
                shareIntent.setType("application/vnd.android.package-archive");
                shareIntent.putExtra(Intent.EXTRA_STREAM, apkUri);
                shareIntent.putExtra(Intent.EXTRA_SUBJECT, "Nayora - Musical LED Sync App");
                shareIntent.putExtra(Intent.EXTRA_TEXT,
                        "Check out this amazing music app that syncs LED lights with beats!\n\n" +
                                "🎵 Nayora - Where Music Meets Light! 🎵\n\n" +
                                "Developed by: Nayan Pote\n" +
                                "Experience the future of musical visualization!");

                shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

                startActivity(Intent.createChooser(shareIntent, "Share Nayora App"));

                showCustomToast("Sharing Nayora app...");
            } else {
                // Fallback to sharing app link
                Intent shareIntent = new Intent(Intent.ACTION_SEND);
                shareIntent.setType("text/plain");
                shareIntent.putExtra(Intent.EXTRA_SUBJECT, "Nayora - Musical LED Sync App");
                shareIntent.putExtra(Intent.EXTRA_TEXT,
                        "Check out Nayora - An amazing music app that syncs LED lights with beats!\n\n" +
                                "🎵 Where Music Meets Light! 🎵\n\n" +
                                "Developed by: Nayan Pote");

                startActivity(Intent.createChooser(shareIntent, "Share Nayora"));
                showCustomToast("Sharing app info...");
            }
        } catch (Exception e) {
            e.printStackTrace();
            showCustomToast("Unable to share app file");
        }
    }

    private void openEmail() {
        Intent emailIntent = new Intent(Intent.ACTION_SENDTO);
        emailIntent.setData(Uri.parse("mailto:nayan.pote65@gmail.com"));
        emailIntent.putExtra(Intent.EXTRA_SUBJECT, "Regarding Nayora App");
        emailIntent.putExtra(Intent.EXTRA_TEXT, "Hello Nayan,\n\nI am writing regarding the Nayora app...\n\nBest regards,");

        try {
            startActivity(Intent.createChooser(emailIntent, "Send Email"));
            showCustomToast("Opening email client...");
        } catch (Exception e) {
            showCustomToast("No email client found");
        }
    }

    private void openPhone() {
        Intent phoneIntent = new Intent(Intent.ACTION_DIAL);
        phoneIntent.setData(Uri.parse("tel:+918767378045"));

        try {
            startActivity(phoneIntent);
            showCustomToast("Opening dialer...");
        } catch (Exception e) {
            showCustomToast("Unable to open dialer");
        }
    }

    private void showCustomToast(String message) {
        Toast toast = Toast.makeText(this, message, Toast.LENGTH_SHORT);
        toast.show();
    }

    private void handleBackPress() {
        // Custom back animation using binding
        binding.appInfoCard.animate()
                .alpha(0f)
                .translationY(-50f)
                .setDuration(300)
                .start();

        binding.developerInfoCard.animate()
                .alpha(0f)
                .translationY(-50f)
                .setStartDelay(100)
                .setDuration(300)
                .start();

        binding.featuresCard.animate()
                .alpha(0f)
                .translationY(-50f)
                .setStartDelay(200)
                .setDuration(300)
                .withEndAction(() -> {
                    finish();
                    overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right);
                })
                .start();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Ensure fullscreen is maintained when returning from other apps
        setupStatusBar();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            // Re-apply fullscreen when window gains focus
            setupStatusBar();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (animationHandler != null) {
            animationHandler.removeCallbacksAndMessages(null);
        }
        if (gradientAnimator != null) {
            gradientAnimator.cancel();
            gradientAnimator = null;
        }
        // Clean up binding reference
        binding = null;
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        handleBackPress();
    }
}