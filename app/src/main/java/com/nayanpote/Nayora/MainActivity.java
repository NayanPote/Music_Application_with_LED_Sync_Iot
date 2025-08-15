package com.nayanpote.Nayora;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.recyclerview.widget.LinearLayoutManager;

import android.Manifest;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.LinearInterpolator;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.RecyclerView;

import android.os.Vibrator;
import android.content.Context;
import android.content.SharedPreferences;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Type;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import android.os.Build;

import com.nayanpote.musicalledsbynayan.R;
import com.nayanpote.musicalledsbynayan.databinding.ActivityMainBinding;

public class MainActivity extends AppCompatActivity implements SongAdapter.OnSongClickListener, MusicService.ServiceListener {
    private static final String TAG = "Nayora";
    private static final UUID MY_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
    private static final String DEVICE_NAME = "Nayora Device";
    private static final int REQUEST_BLUETOOTH_PERMISSIONS = 1;

    // View Binding
    private ActivityMainBinding binding;

    // Service connection
    private MusicService musicService;
    private boolean isServiceBound = false;

    // Bluetooth
    private BluetoothAdapter bluetoothAdapter;
    private BluetoothSocket btSocket;
    private OutputStream outputStream;
    private boolean isBluetoothConnected = false;

    // Data
    private List<Song> playlist;
    private SongAdapter songAdapter;
    private int currentSongIndex = 0;
    private boolean isPlaying = false;
    private boolean isShuffleOn = false;
    private boolean isRepeatOn = false;
    private boolean isPlaylistVisible = false;

    // Visualizer bars
    private List<View> visualizerBarViews = new ArrayList<>();
    private List<Song> originalPlaylist;

    // Animation
    private ObjectAnimator albumRotationAnimator;
    private ObjectAnimator gradientAnimator;

    private ItemTouchHelper itemTouchHelper;
    private Vibrator vibrator;
    private SharedPreferences sharedPreferences;
    private static final String PREF_NAME = "playlist_order";
    private static final String KEY_PLAYLIST = "saved_playlist";

    // Service connection
    private ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            MusicService.MusicBinder binder = (MusicService.MusicBinder) service;
            musicService = binder.getService();
            musicService.setServiceListener(MainActivity.this);
            musicService.setPlaylist(playlist);
            isServiceBound = true;

            // Update UI with current service state
            currentSongIndex = musicService.getCurrentSongIndex();
            isPlaying = musicService.isPlaying();
            isRepeatOn = musicService.isRepeatOn();
            isShuffleOn = musicService.isShuffleOn();

            updateUI();

            Log.d(TAG, "Service connected");
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            musicService = null;
            isServiceBound = false;
            Log.d(TAG, "Service disconnected");
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        sharedPreferences = getSharedPreferences("playlist_prefs", MODE_PRIVATE);
        setupStatusBar();
        initializePlaylist();
        setupClickListeners();
        setupBluetoothAdapter();
        setupGradientAnimation();

        // Start and bind to music service
        Intent serviceIntent = new Intent(this, MusicService.class);
        startService(serviceIntent);
        bindService(serviceIntent, serviceConnection, Context.BIND_AUTO_CREATE);

        updateUI();
    }

    private void initializePlaylist() {
        // Load saved playlist order first
        loadPlaylistOrder();

        // If no saved order exists, create default playlist
        if (playlist == null || playlist.isEmpty()) {
            playlist = new ArrayList<>(playlistdata.getDefaultPlaylist());
        }

        originalPlaylist = new ArrayList<>(playlist);

        songAdapter = new SongAdapter(playlist);
        songAdapter.setOnSongClickListener(this);
        songAdapter.setOriginalPlaylist(originalPlaylist);

        // Set up drag listener
        songAdapter.setOnDragStartListener(new SongAdapter.OnDragStartListener() {
            @Override
            public void onDragStart(RecyclerView.ViewHolder viewHolder) {
                if (itemTouchHelper != null) {
                    itemTouchHelper.startDrag(viewHolder);
                }
            }
        });

        binding.playlistRecycler.setLayoutManager(new LinearLayoutManager(this));
        binding.playlistRecycler.setAdapter(songAdapter);

        // Initialize vibrator and ItemTouchHelper
        vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);

        ItemTouchHelper.Callback callback = new PlaylistItemTouchHelperCallback(songAdapter);
        itemTouchHelper = new ItemTouchHelper(callback);
        itemTouchHelper.attachToRecyclerView(binding.playlistRecycler);

        createVisualizerBars();
    }

    private void setupStatusBar() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            getWindow().setStatusBarColor(Color.TRANSPARENT);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            getWindow().setDecorFitsSystemWindows(false);
        } else {
            getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            );
        }
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

    private void savePlaylistOrder() {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        Gson gson = new Gson();
        String json = gson.toJson(playlist);
        editor.putString(KEY_PLAYLIST, json);
        editor.apply();
    }

    private void loadPlaylistOrder() {
        String json = sharedPreferences.getString(KEY_PLAYLIST, null);
        if (json != null) {
            Gson gson = new Gson();
            Type type = new TypeToken<List<Song>>(){}.getType();
            playlist = gson.fromJson(json, type);
        }
    }

    private class PlaylistItemTouchHelperCallback extends ItemTouchHelper.Callback {
        private final SongAdapter adapter;

        public PlaylistItemTouchHelperCallback(SongAdapter adapter) {
            this.adapter = adapter;
        }

        @Override
        public int getMovementFlags(RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder) {
            int dragFlags = ItemTouchHelper.UP | ItemTouchHelper.DOWN;
            return makeMovementFlags(dragFlags, 0);
        }

        @Override
        public boolean onMove(RecyclerView recyclerView, RecyclerView.ViewHolder source, RecyclerView.ViewHolder target) {
            int fromPosition = source.getAdapterPosition();
            int toPosition = target.getAdapterPosition();

            // Update playlist order
            Song movedSong = playlist.remove(fromPosition);
            playlist.add(toPosition, movedSong);

            // Update original playlist too
            Song movedOriginalSong = originalPlaylist.remove(fromPosition);
            originalPlaylist.add(toPosition, movedOriginalSong);

            // Update current song index if needed
            if (currentSongIndex == fromPosition) {
                currentSongIndex = toPosition;
            } else if (fromPosition < currentSongIndex && toPosition >= currentSongIndex) {
                currentSongIndex--;
            } else if (fromPosition > currentSongIndex && toPosition <= currentSongIndex) {
                currentSongIndex++;
            }

            adapter.notifyItemMoved(fromPosition, toPosition);
            adapter.setCurrentPlaying(currentSongIndex);

            // Update service playlist
            if (isServiceBound && musicService != null) {
                musicService.setPlaylist(playlist);
            }

            // Save the new order
            savePlaylistOrder();

            return true;
        }

        @Override
        public void onSwiped(RecyclerView.ViewHolder viewHolder, int direction) {
            // Not used
        }

        @Override
        public boolean isLongPressDragEnabled() {
            return false; // We'll handle this manually
        }

        @Override
        public void onSelectedChanged(RecyclerView.ViewHolder viewHolder, int actionState) {
            super.onSelectedChanged(viewHolder, actionState);

            if (actionState == ItemTouchHelper.ACTION_STATE_DRAG) {
                // Vibrate when drag starts
                if (vibrator != null) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        vibrator.vibrate(android.os.VibrationEffect.createOneShot(50, android.os.VibrationEffect.DEFAULT_AMPLITUDE));
                    } else {
                        vibrator.vibrate(50);
                    }
                }

                // Animate the dragged item
                if (viewHolder != null) {
                    viewHolder.itemView.animate()
                            .scaleX(1.05f)
                            .scaleY(1.05f)
                            .translationZ(16f)
                            .setDuration(200)
                            .start();
                }
            }
        }

        @Override
        public void clearView(RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder) {
            super.clearView(recyclerView, viewHolder);

            // Reset the item's appearance
            if (viewHolder != null) {
                viewHolder.itemView.animate()
                        .scaleX(1.0f)
                        .scaleY(1.0f)
                        .translationZ(0f)
                        .setDuration(200)
                        .start();
            }
        }
    }

    private void setupClickListeners() {
        binding.connectBtn.setOnClickListener(v -> toggleBluetoothConnection());
        binding.playPauseBtn.setOnClickListener(v -> togglePlayPause());
        binding.nextBtn.setOnClickListener(v -> playNext());
        binding.prevBtn.setOnClickListener(v -> playPrevious());
        binding.shuffleBtn.setOnClickListener(v -> toggleShuffle());
        binding.repeatBtn.setOnClickListener(v -> toggleRepeat());
        binding.playlistToggle.setOnClickListener(v -> togglePlaylist());

        binding.logoContainer.setOnClickListener(v ->
                startActivity(new Intent(this, developerZone.class)));

        binding.progressSeeker.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser && isServiceBound && musicService != null) {
                    musicService.seekTo(progress);
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        binding.searchEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                filterSongs(s.toString());
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });
    }

    private void filterSongs(String query) {
        if (query.isEmpty()) {
            songAdapter.updateSongs(originalPlaylist);
        } else {
            List<Song> filteredSongs = new ArrayList<>();
            for (Song song : originalPlaylist) {
                if (song.getTitle().toLowerCase().contains(query.toLowerCase()) ||
                        song.getArtist().toLowerCase().contains(query.toLowerCase())) {
                    filteredSongs.add(song);
                }
            }
            songAdapter.updateSongs(filteredSongs);
        }
    }

    private void setupBluetoothAdapter() {
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
    }

    private void toggleBluetoothConnection() {
        animateButton(binding.connectBtn);

        if (isBluetoothConnected) {
            disconnectBluetooth();
        } else {
            connectBluetooth();
        }
    }

    private void connectBluetooth() {
        // Check if Bluetooth adapter exists
        if (bluetoothAdapter == null) {
            updateStatus("Bluetooth not supported on this device!", "#FF5252");
            return;
        }

        // Check if Bluetooth is enabled
        if (!bluetoothAdapter.isEnabled()) {
            updateStatus("Bluetooth is OFF! Please enable Bluetooth.", "#FF5252");
            return;
        }

        // Check permissions for Android 12+ (API 31+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.BLUETOOTH_CONNECT},
                        REQUEST_BLUETOOTH_PERMISSIONS);
                return;
            }
        }

        // Find the target device
        BluetoothDevice targetDevice = null;
        try {
            if (bluetoothAdapter.getBondedDevices() != null) {
                for (BluetoothDevice device : bluetoothAdapter.getBondedDevices()) {
                    if (device.getName() != null && device.getName().equals(DEVICE_NAME)) {
                        targetDevice = device;
                        break;
                    }
                }
            }
        } catch (SecurityException e) {
            updateStatus("Bluetooth permission denied!", "#FF5252");
            Log.e(TAG, "Permission error: ", e);
            return;
        }

        if (targetDevice == null) {
            updateStatus("'" + DEVICE_NAME + "' not found in paired devices!", "#FF9800");
            return;
        }

        // Update status to show connection attempt
        updateStatus("Connecting to " + DEVICE_NAME + "...", "#2196F3");
        binding.connectBtn.setText("Connecting...");
        binding.connectBtn.setEnabled(false);

        // Perform connection in background thread
        BluetoothDevice finalDevice = targetDevice;
        new Thread(() -> {
            try {
                // Close existing connection if any
                if (btSocket != null) {
                    try {
                        btSocket.close();
                    } catch (IOException e) {
                        Log.e(TAG, "Error closing existing socket", e);
                    }
                }

                // Create new socket and connect
                btSocket = finalDevice.createRfcommSocketToServiceRecord(MY_UUID);

                // Cancel discovery to improve connection success rate
                if (bluetoothAdapter.isDiscovering()) {
                    bluetoothAdapter.cancelDiscovery();
                }

                btSocket.connect();
                outputStream = btSocket.getOutputStream();

                // Update UI on successful connection
                runOnUiThread(() -> {
                    isBluetoothConnected = true;
                    updateStatus("Connected to Musical LED's!", "#4CAF50");
                    binding.connectBtn.setText("Disconnect");
                    binding.connectBtn.setEnabled(true);
                });

            } catch (IOException e) {
                Log.e(TAG, "Bluetooth connection failed", e);
                // Try fallback method for some devices
                try {
                    btSocket = (BluetoothSocket) finalDevice.getClass()
                            .getMethod("createRfcommSocket", new Class[]{int.class})
                            .invoke(finalDevice, 1);
                    btSocket.connect();
                    outputStream = btSocket.getOutputStream();

                    runOnUiThread(() -> {
                        isBluetoothConnected = true;
                        updateStatus("Connected to Musical LED's!", "#4CAF50");
                        binding.connectBtn.setText("Disconnect");
                        binding.connectBtn.setEnabled(true);
                    });
                } catch (Exception fallbackException) {
                    runOnUiThread(() -> {
                        isBluetoothConnected = false;
                        updateStatus("Connection Failed! Make sure device is on and nearby.", "#FF5252");
                        binding.connectBtn.setText("Connect");
                        binding.connectBtn.setEnabled(true);
                    });
                    Log.e(TAG, "Fallback connection also failed", fallbackException);
                }
            }
        }).start();
    }

    private void disconnectBluetooth() {
        new Thread(() -> {
            try {
                if (outputStream != null) {
                    outputStream.close();
                    outputStream = null;
                }
                if (btSocket != null) {
                    btSocket.close();
                    btSocket = null;
                }

                runOnUiThread(() -> {
                    isBluetoothConnected = false;

                    // Safety checks for binding
                    if (binding != null) {
                        updateStatus("Disconnected from Musical LED's", "#FF9800");

                        if (binding.connectBtn != null) {
                            binding.connectBtn.setText("Connect");
                            binding.connectBtn.setEnabled(true);
                        }
                    }
                });

            } catch (IOException e) {
                Log.e(TAG, "Error disconnecting bluetooth", e);
                runOnUiThread(() -> {
                    isBluetoothConnected = false;

                    // Safety checks for binding
                    if (binding != null) {
                        updateStatus("Disconnected", "#FF9800");

                        if (binding.connectBtn != null) {
                            binding.connectBtn.setText("Connect");
                            binding.connectBtn.setEnabled(true);
                        }
                    }
                });
            }
        }).start();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_BLUETOOTH_PERMISSIONS) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Permission granted, try connecting again
                connectBluetooth();
            } else {
                updateStatus("Bluetooth permission is required to connect!", "#FF5252");
            }
        }
    }

    private void togglePlayPause() {
        animateButton(binding.playPauseBtn);
        if (isServiceBound && musicService != null) {
            musicService.togglePlayPause();
        }
    }

    private void playNext() {
        if (isServiceBound && musicService != null) {
            musicService.playNext();
        }
        animateButton(binding.nextBtn);
    }

    private void playPrevious() {
        if (isServiceBound && musicService != null) {
            musicService.playPrevious();
        }
        animateButton(binding.prevBtn);
    }

    private void toggleShuffle() {
        isShuffleOn = !isShuffleOn;
        if (isServiceBound && musicService != null) {
            musicService.setShuffleMode(isShuffleOn);
        }
        updateUI();
        animateButton(binding.shuffleBtn);
    }

    private void toggleRepeat() {
        isRepeatOn = !isRepeatOn;
        if (isServiceBound && musicService != null) {
            musicService.setRepeatMode(isRepeatOn);
        }
        updateUI();
        animateButton(binding.repeatBtn);
    }

    private void togglePlaylist() {
        isPlaylistVisible = !isPlaylistVisible;

        if (isPlaylistVisible) {
            binding.playlistRecycler.setVisibility(View.VISIBLE);
            binding.searchLayout.setVisibility(View.VISIBLE);

            binding.playlistRecycler.setAlpha(0f);
            binding.searchLayout.setAlpha(0f);

            ObjectAnimator.ofFloat(binding.playlistRecycler, "alpha", 0f, 1f)
                    .setDuration(300).start();
            ObjectAnimator.ofFloat(binding.searchLayout, "alpha", 0f, 1f)
                    .setDuration(300).start();
        } else {
            ObjectAnimator fadeOutRecycler = ObjectAnimator.ofFloat(binding.playlistRecycler, "alpha", 1f, 0f);
            ObjectAnimator fadeOutSearch = ObjectAnimator.ofFloat(binding.searchLayout, "alpha", 1f, 0f);

            fadeOutRecycler.setDuration(300);
            fadeOutSearch.setDuration(300);

            fadeOutRecycler.addListener(new android.animation.AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(android.animation.Animator animation) {
                    binding.playlistRecycler.setVisibility(View.GONE);
                    binding.searchLayout.setVisibility(View.GONE);
                }
            });
            fadeOutRecycler.start();
            fadeOutSearch.start();
        }

        binding.playlistToggle.setImageResource(isPlaylistVisible ?
                R.drawable.ic_playlist_close : R.drawable.ic_playlist_open);
        animateButton(binding.playlistToggle);
    }

    @Override
    public void onSongClick(int position, boolean isCurrentPlaying) {
        int originalPosition = songAdapter.getOriginalPosition(position);
        if (originalPosition == -1) return;

        if (!isCurrentPlaying) {
            if (isServiceBound && musicService != null) {
                musicService.startPlayback(originalPosition);
            }
        } else {
            togglePlayPause();
        }
    }

    // Service listener callbacks
    @Override
    public void onPlaybackStateChanged(boolean playing) {
        isPlaying = playing;
        runOnUiThread(() -> {
            if (isPlaying) {
                animateAlbumArt();
            } else {
                pauseAlbumAnimation();
            }
            updateUI();
        });
    }

    @Override
    public void onSongChanged(int index) {
        currentSongIndex = index;
        runOnUiThread(() -> {
            songAdapter.setCurrentPlaying(currentSongIndex);
            updateUI();
        });
    }

    @Override
    public void onProgressUpdate(int currentPosition, int duration) {
        runOnUiThread(() -> {
            if (binding != null) {
                binding.progressSeeker.setProgress(currentPosition);
                binding.progressSeeker.setMax(duration);
                binding.currentTime.setText(formatTime(currentPosition));
                binding.totalTime.setText(formatTime(duration));
            }
        });
    }

    @Override
    public void onVisualizerData(byte[] data) {
        runOnUiThread(() -> updateVisualizerBarsFFT(data));

        // Send data to ESP32
        if (data != null && data.length > 0) {
            float totalMagnitude = 0;
            for (int i = 2; i < Math.min(data.length, 64); i += 2) {
                float real = data[i];
                float imaginary = data[i + 1];
                totalMagnitude += Math.sqrt(real * real + imaginary * imaginary);
            }
            int level = (int) (totalMagnitude / 32);
            int mappedLevel = Math.min(5, level / 15);
            sendToESP(mappedLevel);
        }
    }

    // Create visualizer bars
    private void createVisualizerBars() {
        if (binding.visualizerBars == null) return;

        binding.visualizerBars.removeAllViews();
        visualizerBarViews.clear();

        // 64 thin bars for high frequency resolution
        for (int i = 0; i < 64; i++) {
            View bar = new View(this);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(12, 20);
            params.setMargins(2, 0, 2, 0);
            params.gravity = Gravity.BOTTOM;
            bar.setLayoutParams(params);

            // Initial gradient
            GradientDrawable gd = new GradientDrawable(
                    GradientDrawable.Orientation.BOTTOM_TOP,
                    new int[]{Color.TRANSPARENT, Color.TRANSPARENT}
            );
            gd.setCornerRadius(6f);
            bar.setBackground(gd);

            binding.visualizerBars.addView(bar);
            visualizerBarViews.add(bar);
        }
    }

    // Update bars using FFT (premium, gradient, dynamic)
    private void updateVisualizerBarsFFT(byte[] fft) {
        if (visualizerBarViews.size() == 0 || fft.length < 4) return;

        int numBars = visualizerBarViews.size();
        int fftBinsToUse = Math.min(fft.length / 2, numBars * 2);

        for (int i = 0; i < numBars && i * 2 < fftBinsToUse; i++) {
            int fftIndex = (i + 1) * 2;
            if (fftIndex + 1 >= fft.length) break;

            float real = fft[fftIndex];
            float imaginary = fft[fftIndex + 1];
            float magnitude = (float) Math.sqrt(real * real + imaginary * imaginary);
            float dbValue = 20 * (float) Math.log10(magnitude + 1);

            int height = Math.max(20, Math.min(400, (int)(dbValue * 6)));

            // Frequency weighting
            float frequencyWeight = 1.2f;
            if (i < numBars * 0.3) {
                frequencyWeight = 0.8f + (i / (float)(numBars * 0.3)) * 0.4f;
            } else if (i > numBars * 0.7) {
                frequencyWeight = 1.2f - ((i - numBars * 0.7f) / (numBars * 0.3f)) * 0.6f;
            }
            height = (int)(height * frequencyWeight);
            height = Math.max(20, Math.min(400, height));

            View bar = visualizerBarViews.get(i);
            animateBarHeight(bar, height);
            animateBarGradient(bar, height);
        }
    }

    // Animate bar height smoothly
    private void animateBarHeight(View bar, int targetHeight) {
        if (bar == null) return;

        android.view.ViewGroup.LayoutParams params = bar.getLayoutParams();
        if (params == null) return;

        ObjectAnimator animator = ObjectAnimator.ofInt(new Object() {
            public void setHeight(int h) {
                params.height = h;
                bar.setLayoutParams(params);
            }
            public int getHeight() { return params.height; }
        }, "height", params.height, targetHeight);

        animator.setDuration(50);
        animator.setInterpolator(new LinearInterpolator());
        animator.start();
    }

    // Animate gradient color dynamically
    private void animateBarGradient(View bar, int height) {
        if (bar == null) return;

        // Map height to fraction (0 to 1)
        float fraction = (float)(height - 20) / 380f; // 20-400 px

        // Colors: royal blue -> purple -> white
        int startColor = Color.rgb(65, 105, 225); // Royal Blue
        int middleColor = Color.rgb(128, 0, 128); // Purple
        int endColor = Color.rgb(255, 255, 255);  // White

        // Blend colors based on fraction
        int blendedStart = blendColors(startColor, middleColor, fraction);
        int blendedEnd = blendColors(middleColor, endColor, fraction);

        GradientDrawable gd = new GradientDrawable(
                GradientDrawable.Orientation.BOTTOM_TOP,
                new int[]{blendedStart, blendedEnd}
        );
        gd.setCornerRadius(6f);
        bar.setBackground(gd);
    }

    // Helper method to blend two colors
    private int blendColors(int color1, int color2, float ratio) {
        float inverseRatio = 1f - ratio;
        int a = (int) ((Color.alpha(color1) * inverseRatio) + (Color.alpha(color2) * ratio));
        int r = (int) ((Color.red(color1) * inverseRatio) + (Color.red(color2) * ratio));
        int g = (int) ((Color.green(color1) * inverseRatio) + (Color.green(color2) * ratio));
        int b = (int) ((Color.blue(color1) * inverseRatio) + (Color.blue(color2) * ratio));
        return Color.argb(a, r, g, b);
    }

    private void sendToESP(int level) {
        if (outputStream != null && isBluetoothConnected) {
            try {
                outputStream.write((level + "\n").getBytes());
                outputStream.flush();
            } catch (IOException e) {
                Log.e(TAG, "BT Send Error", e);
                // Connection might be lost, update status
                runOnUiThread(() -> {
                    isBluetoothConnected = false;
                    updateStatus("Connection lost", "#FF5252");
                    if (binding != null && binding.connectBtn != null) {
                        binding.connectBtn.setText("Connect");
                        binding.connectBtn.setEnabled(true);
                    }
                });
            }
        }
    }

    private String formatTime(int milliseconds) {
        int seconds = milliseconds / 1000;
        int minutes = seconds / 60;
        seconds = seconds % 60;
        return String.format("%02d:%02d", minutes, seconds);
    }

    private void updateUI() {
        if (binding == null) {
            Log.w(TAG, "updateUI called but binding is null");
            return; // Exit early if binding is not initialized
        }

        // Update current song info if valid
        if (playlist != null && playlist.size() > 0 && currentSongIndex >= 0 && currentSongIndex < playlist.size()) {
            Song currentSong = playlist.get(currentSongIndex);

            if (binding.currentSongTitle != null) {
                binding.currentSongTitle.setText(currentSong.getTitle() + " • " + currentSong.getArtist());
            }

            if (binding.albumArt != null) {
                binding.albumArt.setImageResource(currentSong.getAlbumArt());
            }
        }

        // Update play/pause button
        if (binding.playPauseBtn != null) {
            binding.playPauseBtn.setImageResource(isPlaying ? R.drawable.ic_pause : R.drawable.ic_play);
        }

        // Update shuffle/repeat buttons
        int activeColor = Color.parseColor("#FF6B35");
        int inactiveColor = Color.parseColor("#FFFFFF");

        if (binding.shuffleBtn != null) {
            binding.shuffleBtn.setColorFilter(isShuffleOn ? activeColor : inactiveColor);
        }

        if (binding.repeatBtn != null) {
            binding.repeatBtn.setColorFilter(isRepeatOn ? activeColor : inactiveColor);
        }

        // Update status safely
        String statusMessage = isPlaying ? "Playing" :
                isBluetoothConnected ? "Connected - Ready to play" : "Ready to connect";
        String statusColor = isPlaying ? "#4CAF50" :
                isBluetoothConnected ? "#4CAF50" : "#B0BEC5";
        updateStatus(statusMessage, statusColor);
    }

    private void updateStatus(String message, String color) {
        if (binding != null && binding.statusText != null) {
            binding.statusText.setText(message);
            binding.statusText.setTextColor(Color.parseColor(color));
        }
    }

    private void animateButton(View button) {
        if (button == null) return;

        ObjectAnimator scaleX = ObjectAnimator.ofFloat(button, "scaleX", 1f, 0.9f, 1f);
        ObjectAnimator scaleY = ObjectAnimator.ofFloat(button, "scaleY", 1f, 0.9f, 1f);
        scaleX.setDuration(150);
        scaleY.setDuration(150);
        scaleX.setInterpolator(new AccelerateDecelerateInterpolator());
        scaleY.setInterpolator(new AccelerateDecelerateInterpolator());
        scaleX.start();
        scaleY.start();
    }

    private void animateAlbumArt() {
        stopAlbumAnimation();
        if (binding != null && binding.albumArt != null) {
            albumRotationAnimator = ObjectAnimator.ofFloat(binding.albumArt, "rotation", 0f, 360f);
            albumRotationAnimator.setDuration(20000);
            albumRotationAnimator.setRepeatCount(ValueAnimator.INFINITE);
            albumRotationAnimator.setInterpolator(new android.view.animation.LinearInterpolator());
            albumRotationAnimator.start();
        }
    }

    private void pauseAlbumAnimation() {
        if (albumRotationAnimator != null && albumRotationAnimator.isRunning()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                albumRotationAnimator.pause();
            }
        }
    }

    private void resumeAlbumAnimation() {
        if (albumRotationAnimator != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            if (albumRotationAnimator.isPaused()) {
                albumRotationAnimator.resume();
            } else if (!albumRotationAnimator.isRunning() && isPlaying) {
                animateAlbumArt();
            }
        } else if (isPlaying) {
            animateAlbumArt();
        }
    }

    private void stopAlbumAnimation() {
        if (albumRotationAnimator != null) {
            albumRotationAnimator.cancel();
            albumRotationAnimator = null;
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        // Unbind from service
        if (isServiceBound) {
            try {
                unbindService(serviceConnection);
                isServiceBound = false;
            } catch (Exception e) {
                Log.e(TAG, "Error unbinding service", e);
            }
        }

        stopAlbumAnimation();

        if (gradientAnimator != null) {
            gradientAnimator.cancel();
        }

        // Disconnect bluetooth properly
        disconnectBluetooth();

        if (playlist != null) {
            savePlaylistOrder();
        }

        // Clean up view binding
        binding = null;
    }

    @Override
    protected void onPause() {
        super.onPause();
        // Don't pause music when app goes to background - let it continue playing
        // The service will handle background playback
    }

    @Override
    protected void onResume() {
        super.onResume();

        // Sync with service state when resuming
        if (isServiceBound && musicService != null) {
            currentSongIndex = musicService.getCurrentSongIndex();
            isPlaying = musicService.isPlaying();
            isRepeatOn = musicService.isRepeatOn();
            isShuffleOn = musicService.isShuffleOn();

            updateUI();

            // Resume animations if playing
            if (isPlaying) {
                resumeAlbumAnimation();
            }
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        // Rebind to service if needed
        if (!isServiceBound) {
            Intent serviceIntent = new Intent(this, MusicService.class);
            bindService(serviceIntent, serviceConnection, Context.BIND_AUTO_CREATE);
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        // Don't unbind here as we want to maintain connection for quick resume
    }

    @Override
    public void onConfigurationChanged(android.content.res.Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        // This prevents activity recreation on theme changes
        // The service will continue running uninterrupted
    }
}