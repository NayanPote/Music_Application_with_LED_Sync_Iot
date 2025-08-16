package com.nayanpote.Nayora;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.recyclerview.widget.LinearLayoutManager;

import android.Manifest;
import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
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
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.Interpolator;
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
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.Random;
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
    private final AtomicBoolean isDestroyed = new AtomicBoolean(false);

    // Bluetooth
    private BluetoothAdapter bluetoothAdapter;
    private BluetoothSocket btSocket;
    private OutputStream outputStream;
    private boolean isBluetoothConnected = false;
    private final Object bluetoothLock = new Object();

    // Data
    private List<Song> playlist;
    private List<Song> filteredPlaylist;
    private SongAdapter songAdapter;
    private int currentSongIndex = 0;
    private boolean isPlaying = false;
    private boolean isShuffleOn = false;
    private boolean isRepeatOn = false;
    private boolean isPlaylistVisible = false;

    // Visualizer bars
    private List<View> visualizerBarViews = new ArrayList<>();
    private List<Song> originalPlaylist;
    private Handler visualizerHandler;
    private Runnable visualizerRunnable;
    private Random random = new Random();

    // Animation
    private ObjectAnimator albumRotationAnimator;
    private ObjectAnimator gradientAnimator;
    private final AtomicBoolean isAnimationRunning = new AtomicBoolean(false);
    private float currentRotation = 0f;
    private boolean isCardFlipping = false;

    private ItemTouchHelper itemTouchHelper;
    private Vibrator vibrator;
    private SharedPreferences sharedPreferences;
    private static final String PREF_NAME = "playlist_order";
    private static final String KEY_PLAYLIST = "saved_playlist";

    // Handlers for UI updates
    private Handler mainHandler;
    private Runnable progressUpdateRunnable;
    private final AtomicBoolean isSeekBarTracking = new AtomicBoolean(false);

    // Service connection
    private ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            if (isDestroyed.get()) return;

            try {
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

                runOnUiThread(() -> {
                    if (!isDestroyed.get()) {
                        updateUI();
                        startProgressUpdates();

                        // Ensure visualizer bars are created
                        createVisualizerBarsIfNeeded();

                        // Start visualizer and album animation if playing
                        if (isPlaying) {
                            startVisualizerAnimation();
                            startAlbumRotation();
                        }
                    }
                });

                Log.d(TAG, "Service connected successfully");
            } catch (Exception e) {
                Log.e(TAG, "Error in service connection", e);
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            musicService = null;
            isServiceBound = false;
            stopProgressUpdates();
            stopVisualizerAnimation();
            Log.d(TAG, "Service disconnected");
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        try {
            binding = ActivityMainBinding.inflate(getLayoutInflater());
            setContentView(binding.getRoot());

            mainHandler = new Handler(Looper.getMainLooper());
            visualizerHandler = new Handler(Looper.getMainLooper());
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

        } catch (Exception e) {
            Log.e(TAG, "Error in onCreate", e);
        }
    }

    private void initializePlaylist() {
        try {
            // Load saved playlist order first
            loadPlaylistOrder();

            // If no saved order exists, create default playlist
            if (playlist == null || playlist.isEmpty()) {
                playlist = new ArrayList<>(playlistdata.getDefaultPlaylist());
            }

            originalPlaylist = new ArrayList<>(playlist);
            filteredPlaylist = new ArrayList<>(playlist);

            songAdapter = new SongAdapter(filteredPlaylist);
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

            if (binding.playlistRecycler != null) {
                binding.playlistRecycler.setLayoutManager(new LinearLayoutManager(this));
                binding.playlistRecycler.setAdapter(songAdapter);
            }

            // Initialize vibrator and ItemTouchHelper
            vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);

            ItemTouchHelper.Callback callback = new PlaylistItemTouchHelperCallback(songAdapter);
            itemTouchHelper = new ItemTouchHelper(callback);
            if (binding.playlistRecycler != null) {
                itemTouchHelper.attachToRecyclerView(binding.playlistRecycler);
            }

            createVisualizerBarsIfNeeded();

        } catch (Exception e) {
            Log.e(TAG, "Error initializing playlist", e);
        }
    }

    private void setupStatusBar() {
        try {
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
        } catch (Exception e) {
            Log.e(TAG, "Error setting up status bar", e);
        }
    }

    private void setupGradientAnimation() {
        try {
            if (binding != null && binding.gradientOverlay != null) {
                if (gradientAnimator != null) {
                    gradientAnimator.cancel();
                }
                gradientAnimator = ObjectAnimator.ofFloat(binding.gradientOverlay, "alpha", 0.3f, 0.8f, 0.3f);
                gradientAnimator.setDuration(4000);
                gradientAnimator.setRepeatCount(ValueAnimator.INFINITE);
                gradientAnimator.setInterpolator(new AccelerateDecelerateInterpolator());
                gradientAnimator.start();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error setting up gradient animation", e);
        }
    }

    private void savePlaylistOrder() {
        try {
            if (playlist != null && sharedPreferences != null) {
                SharedPreferences.Editor editor = sharedPreferences.edit();
                Gson gson = new Gson();
                String json = gson.toJson(playlist);
                editor.putString(KEY_PLAYLIST, json);
                editor.apply();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error saving playlist order", e);
        }
    }

    private void loadPlaylistOrder() {
        try {
            if (sharedPreferences != null) {
                String json = sharedPreferences.getString(KEY_PLAYLIST, null);
                if (json != null) {
                    Gson gson = new Gson();
                    Type type = new TypeToken<List<Song>>(){}.getType();
                    playlist = gson.fromJson(json, type);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error loading playlist order", e);
            playlist = null; // Reset to null so default playlist is loaded
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
            try {
                int fromPosition = source.getAdapterPosition();
                int toPosition = target.getAdapterPosition();

                if (fromPosition == RecyclerView.NO_POSITION || toPosition == RecyclerView.NO_POSITION) {
                    return false;
                }

                // Update playlist order
                Song movedSong = playlist.remove(fromPosition);
                playlist.add(toPosition, movedSong);

                // Update original playlist too
                Song movedOriginalSong = originalPlaylist.remove(fromPosition);
                originalPlaylist.add(toPosition, movedOriginalSong);

                // Update filtered playlist
                Song movedFilteredSong = filteredPlaylist.remove(fromPosition);
                filteredPlaylist.add(toPosition, movedFilteredSong);

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
            } catch (Exception e) {
                Log.e(TAG, "Error in onMove", e);
                return false;
            }
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

            try {
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
            } catch (Exception e) {
                Log.e(TAG, "Error in onSelectedChanged", e);
            }
        }

        @Override
        public void clearView(RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder) {
            super.clearView(recyclerView, viewHolder);

            try {
                // Reset the item's appearance
                if (viewHolder != null) {
                    viewHolder.itemView.animate()
                            .scaleX(1.0f)
                            .scaleY(1.0f)
                            .translationZ(0f)
                            .setDuration(200)
                            .start();
                }
            } catch (Exception e) {
                Log.e(TAG, "Error in clearView", e);
            }
        }
    }

    private void setupClickListeners() {
        try {
            if (binding == null) return;

            if (binding.connectBtn != null) {
                binding.connectBtn.setOnClickListener(v -> toggleBluetoothConnection());
            }
            if (binding.playPauseBtn != null) {
                binding.playPauseBtn.setOnClickListener(v -> togglePlayPause());
            }
            if (binding.nextBtn != null) {
                binding.nextBtn.setOnClickListener(v -> playNext());
            }
            if (binding.prevBtn != null) {
                binding.prevBtn.setOnClickListener(v -> playPrevious());
            }
            if (binding.shuffleBtn != null) {
                binding.shuffleBtn.setOnClickListener(v -> toggleShuffle());
            }
            if (binding.repeatBtn != null) {
                binding.repeatBtn.setOnClickListener(v -> toggleRepeat());
            }
            if (binding.playlistToggle != null) {
                binding.playlistToggle.setOnClickListener(v -> flipCard());
            }

            if (binding.logoContainer != null) {
                binding.logoContainer.setOnClickListener(v ->
                        startActivity(new Intent(this, developerZone.class)));
            }

            if (binding.progressSeeker != null) {
                binding.progressSeeker.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                    @Override
                    public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                        if (fromUser && isServiceBound && musicService != null) {
                            musicService.seekTo(progress);
                        }
                    }

                    @Override
                    public void onStartTrackingTouch(SeekBar seekBar) {
                        isSeekBarTracking.set(true);
                    }

                    @Override
                    public void onStopTrackingTouch(SeekBar seekBar) {
                        isSeekBarTracking.set(false);
                    }
                });
            }

            if (binding.searchEditText != null) {
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
        } catch (Exception e) {
            Log.e(TAG, "Error setting up click listeners", e);
        }
    }

    private void flipCard() {
        if (isCardFlipping) return;

        isCardFlipping = true;
        animateButton(binding.playlistToggle);

        View frontCard = binding.playerCardFront;
        View backCard = binding.playerCardBack;

        // Ensure both cards are properly prepared for animation
        prepareCardsForFlip(frontCard, backCard);

        if (!isPlaylistVisible) {
            // Flip to back (show playlist)
            flipCardToBack(frontCard, backCard);
        } else {
            // Flip to front (show player)
            flipCardToFront(frontCard, backCard);
        }

        isPlaylistVisible = !isPlaylistVisible;

        // Update toggle icon with smooth transition
        updateToggleIcon();
    }

    private void prepareCardsForFlip(View frontCard, View backCard) {
        // Enable hardware acceleration for smooth animations
        frontCard.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        backCard.setLayerType(View.LAYER_TYPE_HARDWARE, null);

        // Ensure both cards have the same dimensions and position
        ViewGroup.LayoutParams frontParams = frontCard.getLayoutParams();
        ViewGroup.LayoutParams backParams = backCard.getLayoutParams();

        if (frontParams != null && backParams != null) {
            backParams.height = frontParams.height;
            backParams.width = frontParams.width;
        }

        // Set proper pivot points for center rotation
        frontCard.post(() -> {
            frontCard.setPivotX(frontCard.getWidth() / 2f);
            frontCard.setPivotY(frontCard.getHeight() / 2f);
        });

        backCard.post(() -> {
            backCard.setPivotX(backCard.getWidth() / 2f);
            backCard.setPivotY(backCard.getHeight() / 2f);
        });

        // Set camera distance for 3D effect (prevents distortion)
        float cameraDistance = frontCard.getResources().getDisplayMetrics().density * 8000;
        frontCard.setCameraDistance(cameraDistance);
        backCard.setCameraDistance(cameraDistance);

        // Initialize proper states to prevent first-time glitch
        if (!isPlaylistVisible) {
            // Currently showing front card
            frontCard.setVisibility(View.VISIBLE);
            frontCard.setRotationY(0f);
            frontCard.setAlpha(1f);
            frontCard.setScaleX(1f);
            frontCard.setScaleY(1f);

            backCard.setVisibility(View.INVISIBLE);
            backCard.setRotationY(0f);
            backCard.setAlpha(1f);
            backCard.setScaleX(1f);
            backCard.setScaleY(1f);
        } else {
            // Currently showing back card
            frontCard.setVisibility(View.INVISIBLE);
            frontCard.setRotationY(0f);
            frontCard.setAlpha(1f);
            frontCard.setScaleX(1f);
            frontCard.setScaleY(1f);

            backCard.setVisibility(View.VISIBLE);
            backCard.setRotationY(0f);
            backCard.setAlpha(1f);
            backCard.setScaleX(1f);
            backCard.setScaleY(1f);
        }
    }

    private void flipCardToBack(View frontCard, View backCard) {
        // First half: Rotate front card to 90 degrees (edge view)
        ObjectAnimator frontRotation = ObjectAnimator.ofFloat(frontCard, "rotationY", 0f, 90f);
        frontRotation.setDuration(500); // Half of 1 second
        frontRotation.setInterpolator(new AccelerateInterpolator());

        frontRotation.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                // Switch cards at the midpoint when front card is edge-on
                frontCard.setVisibility(View.INVISIBLE);
                backCard.setVisibility(View.VISIBLE);

                // Second half: Rotate back card from -90 to 0 degrees
                ObjectAnimator backRotation = ObjectAnimator.ofFloat(backCard, "rotationY", -90f, 0f);
                backRotation.setDuration(500); // Half of 1 second
                backRotation.setInterpolator(new DecelerateInterpolator());

                backRotation.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        finishFlipAnimation(frontCard, backCard);
                        updateEmptyState();
                    }
                });

                backRotation.start();
            }
        });

        // Set initial state for back card
        backCard.setRotationY(-90f);
        frontRotation.start();
    }

    private void flipCardToFront(View frontCard, View backCard) {
        // First half: Rotate back card to 90 degrees (edge view)
        ObjectAnimator backRotation = ObjectAnimator.ofFloat(backCard, "rotationY", 0f, 90f);
        backRotation.setDuration(500); // Half of 1 second
        backRotation.setInterpolator(new AccelerateInterpolator());

        backRotation.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                // Switch cards at the midpoint when back card is edge-on
                backCard.setVisibility(View.INVISIBLE);
                frontCard.setVisibility(View.VISIBLE);

                // Second half: Rotate front card from -90 to 0 degrees
                ObjectAnimator frontRotation = ObjectAnimator.ofFloat(frontCard, "rotationY", -90f, 0f);
                frontRotation.setDuration(500); // Half of 1 second
                frontRotation.setInterpolator(new DecelerateInterpolator());

                frontRotation.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        finishFlipAnimation(frontCard, backCard);
                        // Clear search text when returning to front
                        if (binding.searchEditText != null) {
                            binding.searchEditText.setText("");
                        }
                    }
                });

                frontRotation.start();
            }
        });

        // Set initial state for front card
        frontCard.setRotationY(-90f);
        backRotation.start();
    }

    private void finishFlipAnimation(View frontCard, View backCard) {
        // Reset layer types to optimize performance
        frontCard.setLayerType(View.LAYER_TYPE_NONE, null);
        backCard.setLayerType(View.LAYER_TYPE_NONE, null);

        // Reset rotation values for clean state
        frontCard.setRotationY(0f);
        backCard.setRotationY(0f);

        isCardFlipping = false;
    }

    private void updateToggleIcon() {
        if (binding.playlistToggle != null) {
            // Create smooth icon transition - matching card animation speed
            ObjectAnimator iconRotation = ObjectAnimator.ofFloat(binding.playlistToggle, "rotation",
                    binding.playlistToggle.getRotation(), binding.playlistToggle.getRotation() + 180f);
            iconRotation.setDuration(500); // Half the duration of card flip
            iconRotation.setInterpolator(new AccelerateDecelerateInterpolator());

            iconRotation.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    binding.playlistToggle.setImageResource(isPlaylistVisible ?
                            R.drawable.ic_playlist_close : R.drawable.ic_playlist_open);
                    // Reset rotation to 0 to prevent accumulation
                    binding.playlistToggle.setRotation(0f);
                }
            });

            iconRotation.start();
        }
    }

    private void updateEmptyState() {
        try {
            if (binding.emptyStateLayout == null || binding.playlistRecycler == null) return;

            boolean isEmpty = filteredPlaylist == null || filteredPlaylist.isEmpty();
            binding.emptyStateLayout.setVisibility(isEmpty ? View.VISIBLE : View.GONE);
            binding.playlistRecycler.setVisibility(isEmpty ? View.GONE : View.VISIBLE);
        } catch (Exception e) {
            Log.e(TAG, "Error updating empty state", e);
        }
    }

    private void filterSongs(String query) {
        try {
            if (songAdapter == null || originalPlaylist == null) return;

            filteredPlaylist.clear();

            if (query.isEmpty()) {
                filteredPlaylist.addAll(originalPlaylist);
            } else {
                for (Song song : originalPlaylist) {
                    if (song.getTitle().toLowerCase().contains(query.toLowerCase()) ||
                            song.getArtist().toLowerCase().contains(query.toLowerCase())) {
                        filteredPlaylist.add(song);
                    }
                }
            }

            songAdapter.updateSongs(filteredPlaylist);
            updateEmptyState();
        } catch (Exception e) {
            Log.e(TAG, "Error filtering songs", e);
        }
    }

    private void setupBluetoothAdapter() {
        try {
            bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        } catch (Exception e) {
            Log.e(TAG, "Error setting up Bluetooth adapter", e);
        }
    }

    private void toggleBluetoothConnection() {
        try {
            if (binding.connectBtn != null) {
                animateButton(binding.connectBtn);
            }

            if (isBluetoothConnected) {
                disconnectBluetooth();
            } else {
                connectBluetooth();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error toggling Bluetooth connection", e);
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
        if (binding.connectBtn != null) {
            binding.connectBtn.setText("Connecting...");
            binding.connectBtn.setEnabled(false);
        }

        // Perform connection in background thread
        BluetoothDevice finalDevice = targetDevice;
        new Thread(() -> {
            try {
                synchronized (bluetoothLock) {
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
                        if (!isDestroyed.get()) {
                            isBluetoothConnected = true;
                            updateStatus("Connected to Musical LED's!", "#4CAF50");
                            if (binding.connectBtn != null) {
                                binding.connectBtn.setText("Disconnect");
                                binding.connectBtn.setEnabled(true);
                            }
                        }
                    });
                }
            } catch (IOException e) {
                Log.e(TAG, "Bluetooth connection failed", e);
                // Try fallback method for some devices
                try {
                    synchronized (bluetoothLock) {
                        btSocket = (BluetoothSocket) finalDevice.getClass()
                                .getMethod("createRfcommSocket", new Class[]{int.class})
                                .invoke(finalDevice, 1);
                        btSocket.connect();
                        outputStream = btSocket.getOutputStream();

                        runOnUiThread(() -> {
                            if (!isDestroyed.get()) {
                                isBluetoothConnected = true;
                                updateStatus("Connected to Musical LED's!", "#4CAF50");
                                if (binding.connectBtn != null) {
                                    binding.connectBtn.setText("Disconnect");
                                    binding.connectBtn.setEnabled(true);
                                }
                            }
                        });
                    }
                } catch (Exception fallbackException) {
                    runOnUiThread(() -> {
                        if (!isDestroyed.get()) {
                            isBluetoothConnected = false;
                            updateStatus("Connection Failed! Make sure device is on and nearby.", "#FF5252");
                            if (binding.connectBtn != null) {
                                binding.connectBtn.setText("Connect");
                                binding.connectBtn.setEnabled(true);
                            }
                        }
                    });
                    Log.e(TAG, "Fallback connection also failed", fallbackException);
                }
            }
        }).start();
    }

    private void disconnectBluetooth() {
        new Thread(() -> {
            try {
                synchronized (bluetoothLock) {
                    if (outputStream != null) {
                        try {
                            outputStream.close();
                        } catch (IOException e) {
                            Log.e(TAG, "Error closing output stream", e);
                        }
                        outputStream = null;
                    }
                    if (btSocket != null) {
                        try {
                            btSocket.close();
                        } catch (IOException e) {
                            Log.e(TAG, "Error closing bluetooth socket", e);
                        }
                        btSocket = null;
                    }
                }

                runOnUiThread(() -> {
                    if (!isDestroyed.get()) {
                        isBluetoothConnected = false;
                        updateStatus("Disconnected from Musical LED's", "#FF9800");
                        if (binding != null && binding.connectBtn != null) {
                            binding.connectBtn.setText("Connect");
                            binding.connectBtn.setEnabled(true);
                        }
                    }
                });

            } catch (Exception e) {
                Log.e(TAG, "Error disconnecting bluetooth", e);
                runOnUiThread(() -> {
                    if (!isDestroyed.get()) {
                        isBluetoothConnected = false;
                        updateStatus("Disconnected", "#FF9800");
                        if (binding != null && binding.connectBtn != null) {
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
        try {
            if (binding.playPauseBtn != null) {
                animateButton(binding.playPauseBtn);
            }
            if (isServiceBound && musicService != null) {
                musicService.togglePlayPause();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error toggling play/pause", e);
        }
    }

    private void playNext() {
        try {
            if (isServiceBound && musicService != null) {
                musicService.playNext();
            }
            if (binding.nextBtn != null) {
                animateButton(binding.nextBtn);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error playing next", e);
        }
    }

    private void playPrevious() {
        try {
            if (isServiceBound && musicService != null) {
                musicService.playPrevious();
            }
            if (binding.prevBtn != null) {
                animateButton(binding.prevBtn);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error playing previous", e);
        }
    }

    private void toggleShuffle() {
        try {
            isShuffleOn = !isShuffleOn;
            if (isServiceBound && musicService != null) {
                musicService.setShuffleMode(isShuffleOn);
            }
            updateUI();
            if (binding.shuffleBtn != null) {
                animateButton(binding.shuffleBtn);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error toggling shuffle", e);
        }
    }

    private void toggleRepeat() {
        try {
            isRepeatOn = !isRepeatOn;
            if (isServiceBound && musicService != null) {
                musicService.setRepeatMode(isRepeatOn);
            }
            updateUI();
            if (binding.repeatBtn != null) {
                animateButton(binding.repeatBtn);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error toggling repeat", e);
        }
    }

    @Override
    public void onSongClick(int position, boolean isCurrentPlaying) {
        try {
            if (songAdapter == null) return;

            int originalPosition = songAdapter.getOriginalPosition(position);
            if (originalPosition == -1) return;

            if (!isCurrentPlaying) {
                if (isServiceBound && musicService != null) {
                    musicService.startPlayback(originalPosition);
                }
            } else {
                togglePlayPause();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error handling song click", e);
        }
    }

    // Service listener callbacks
    @Override
    public void onPlaybackStateChanged(boolean playing) {
        try {
            isPlaying = playing;
            runOnUiThread(() -> {
                if (!isDestroyed.get()) {
                    if (isPlaying) {
                        startAlbumRotation();
                        startProgressUpdates();
                        startVisualizerAnimation();
                    } else {
                        pauseAlbumRotation();
                        stopProgressUpdates();
                        stopVisualizerAnimation();
                    }
                    updateUI();
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "Error in onPlaybackStateChanged", e);
        }
    }

    @Override
    public void onSongChanged(int index) {
        try {
            currentSongIndex = index;
            runOnUiThread(() -> {
                if (!isDestroyed.get()) {
                    if (songAdapter != null) {
                        songAdapter.setCurrentPlaying(currentSongIndex);
                    }
                    updateUI();
                    // Reset album rotation for new song
                    if (isPlaying) {
                        startAlbumRotation();
                    }
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "Error in onSongChanged", e);
        }
    }

    @Override
    public void onProgressUpdate(int currentPosition, int duration) {
        try {
            runOnUiThread(() -> {
                if (!isDestroyed.get() && binding != null && !isSeekBarTracking.get()) {
                    if (binding.progressSeeker != null) {
                        binding.progressSeeker.setMax(duration);
                        binding.progressSeeker.setProgress(currentPosition);
                    }
                    if (binding.currentTime != null) {
                        binding.currentTime.setText(formatTime(currentPosition));
                    }
                    if (binding.totalTime != null) {
                        binding.totalTime.setText(formatTime(duration));
                    }
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "Error in onProgressUpdate", e);
        }
    }

    @Override
    public void onVisualizerData(byte[] data) {
        try {
            // Process real visualizer data if available
            if (data != null && data.length > 0) {
                runOnUiThread(() -> {
                    if (!isDestroyed.get()) {
                        updateVisualizerBarsFFT(data);
                    }
                });

                // Send data to ESP32
                try {
                    float totalMagnitude = 0;
                    for (int i = 2; i < Math.min(data.length, 64); i += 2) {
                        float real = data[i];
                        float imaginary = data[i + 1];
                        totalMagnitude += Math.sqrt(real * real + imaginary * imaginary);
                    }
                    int level = (int) (totalMagnitude / 32);
                    int mappedLevel = Math.min(5, level / 15);
                    sendToESP(mappedLevel);
                } catch (Exception e) {
                    Log.e(TAG, "Error processing visualizer data", e);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error in onVisualizerData", e);
        }
    }

    // Progress update methods
    private void startProgressUpdates() {
        try {
            stopProgressUpdates(); // Stop any existing updates

            if (mainHandler != null && !isDestroyed.get()) {
                progressUpdateRunnable = new Runnable() {
                    @Override
                    public void run() {
                        try {
                            if (!isDestroyed.get() && isServiceBound && musicService != null && isPlaying) {
                                int currentPos = musicService.getCurrentPosition();
                                int duration = musicService.getDuration();

                                onProgressUpdate(currentPos, duration);

                                // Schedule next update
                                if (mainHandler != null && !isDestroyed.get()) {
                                    mainHandler.postDelayed(this, 1000);
                                }
                            }
                        } catch (Exception e) {
                            Log.e(TAG, "Error in progress update runnable", e);
                        }
                    }
                };

                mainHandler.post(progressUpdateRunnable);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error starting progress updates", e);
        }
    }

    private void stopProgressUpdates() {
        try {
            if (mainHandler != null && progressUpdateRunnable != null) {
                mainHandler.removeCallbacks(progressUpdateRunnable);
                progressUpdateRunnable = null;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error stopping progress updates", e);
        }
    }

    // Visualizer animation methods
    private void createVisualizerBarsIfNeeded() {
        try {
            if (binding == null || binding.visualizerBars == null) {
                Log.w(TAG, "Cannot create visualizer bars - binding or visualizer container is null");
                return;
            }

            // Only create if bars don't exist or container is empty
            if (visualizerBarViews.isEmpty() || binding.visualizerBars.getChildCount() == 0) {
                createVisualizerBars();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error in createVisualizerBarsIfNeeded", e);
        }
    }

    private void startVisualizerAnimation() {
        try {
            if (isDestroyed.get() || !isPlaying) return;

            // Ensure bars exist
            createVisualizerBarsIfNeeded();

            stopVisualizerAnimation(); // Stop any existing animation

            if (visualizerHandler != null && visualizerBarViews.size() > 0) {
                visualizerRunnable = new Runnable() {
                    @Override
                    public void run() {
                        try {
                            if (!isDestroyed.get() && isPlaying && visualizerBarViews.size() > 0) {
                                // Generate fake visualizer data for smooth animation
                                generateFakeVisualizerData();

                                // Schedule next update
                                if (visualizerHandler != null && !isDestroyed.get()) {
                                    visualizerHandler.postDelayed(this, 100); // Update every 100ms
                                }
                            }
                        } catch (Exception e) {
                            Log.e(TAG, "Error in visualizer runnable", e);
                        }
                    }
                };

                visualizerHandler.post(visualizerRunnable);
                Log.d(TAG, "Started visualizer animation with " + visualizerBarViews.size() + " bars");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error starting visualizer animation", e);
        }
    }

    private void stopVisualizerAnimation() {
        try {
            if (visualizerHandler != null && visualizerRunnable != null) {
                visualizerHandler.removeCallbacks(visualizerRunnable);
                visualizerRunnable = null;
            }

            // Reset all bars to minimum height
            if (visualizerBarViews != null) {
                for (View bar : visualizerBarViews) {
                    if (bar != null) {
                        animateBarHeight(bar, 20);
                        animateBarGradient(bar, 20);
                    }
                }
            }
            Log.d(TAG, "Stopped visualizer animation");
        } catch (Exception e) {
            Log.e(TAG, "Error stopping visualizer animation", e);
        }
    }

    private void generateFakeVisualizerData() {
        try {
            if (visualizerBarViews == null || visualizerBarViews.size() == 0) return;

            for (int i = 0; i < visualizerBarViews.size(); i++) {
                View bar = visualizerBarViews.get(i);
                if (bar != null) {
                    // Generate random heights with some bass emphasis
                    int baseHeight = 30 + random.nextInt(100);

                    // Add bass boost for first few bars
                    if (i < 8) {
                        baseHeight += random.nextInt(150);
                    }
                    // Mid frequencies
                    else if (i < 32) {
                        baseHeight += random.nextInt(100);
                    }
                    // High frequencies - shorter
                    else {
                        baseHeight += random.nextInt(50);
                    }

                    // Cap the height
                    int height = Math.min(400, baseHeight);

                    animateBarHeight(bar, height);
                    animateBarGradient(bar, height);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error generating fake visualizer data", e);
        }
    }

    // Create visualizer bars
    private void createVisualizerBars() {
        try {
            if (binding == null || binding.visualizerBars == null) {
                Log.w(TAG, "Cannot create visualizer bars - binding or visualizer container is null");
                return;
            }

            // Clear existing bars
            binding.visualizerBars.removeAllViews();
            visualizerBarViews.clear();

            Log.d(TAG, "Creating 64 visualizer bars");

            // Create 64 thin bars for high frequency resolution
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

            Log.d(TAG, "Created " + visualizerBarViews.size() + " visualizer bars");

            // Start animation if music is playing
            if (isPlaying) {
                startVisualizerAnimation();
            }

        } catch (Exception e) {
            Log.e(TAG, "Error creating visualizer bars", e);
        }
    }

    // Update bars using FFT (premium, gradient, dynamic)
    private void updateVisualizerBarsFFT(byte[] fft) {
        try {
            if (visualizerBarViews.size() == 0 || fft == null || fft.length < 4) return;

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

                if (i < visualizerBarViews.size()) {
                    View bar = visualizerBarViews.get(i);
                    animateBarHeight(bar, height);
                    animateBarGradient(bar, height);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error updating visualizer bars", e);
        }
    }

    // Animate bar height smoothly
    private void animateBarHeight(View bar, int targetHeight) {
        try {
            if (bar == null) return;

            android.view.ViewGroup.LayoutParams params = bar.getLayoutParams();
            if (params == null) return;

            ObjectAnimator animator = ObjectAnimator.ofInt(new Object() {
                public void setHeight(int h) {
                    try {
                        params.height = h;
                        bar.setLayoutParams(params);
                    } catch (Exception e) {
                        // Ignore layout errors during animations
                    }
                }
                public int getHeight() {
                    return params.height;
                }
            }, "height", params.height, targetHeight);

            animator.setDuration(100);
            animator.setInterpolator(new LinearInterpolator());
            animator.start();
        } catch (Exception e) {
            // Ignore animation errors
        }
    }

    // Animate gradient color dynamically
    private void animateBarGradient(View bar, int height) {
        try {
            if (bar == null) return;

            // Map height to fraction (0 to 1)
            float fraction = Math.max(0f, Math.min(1f, (float)(height - 20) / 380f));

            // Colors: royal blue -> purple -> white
            int startColor = Color.rgb(255, 215, 0);   // Bright Gold (#FFD700)
            int middleColor = Color.rgb(255, 165, 0); // Light Orange (#FFA500)
            int endColor = Color.rgb(0, 0, 0);     // Black

            // Blend colors based on fraction
            int blendedStart = blendColors(startColor, middleColor, fraction);
            int blendedEnd = blendColors(middleColor, endColor, fraction);

            GradientDrawable gd = new GradientDrawable(
                    GradientDrawable.Orientation.BOTTOM_TOP,
                    new int[]{blendedStart, blendedEnd}
            );
            gd.setCornerRadius(6f);
            bar.setBackground(gd);
        } catch (Exception e) {
            // Ignore gradient errors
        }
    }

    // Helper method to blend two colors
    private int blendColors(int color1, int color2, float ratio) {
        try {
            ratio = Math.max(0f, Math.min(1f, ratio));
            float inverseRatio = 1f - ratio;
            int a = (int) ((Color.alpha(color1) * inverseRatio) + (Color.alpha(color2) * ratio));
            int r = (int) ((Color.red(color1) * inverseRatio) + (Color.red(color2) * ratio));
            int g = (int) ((Color.green(color1) * inverseRatio) + (Color.green(color2) * ratio));
            int b = (int) ((Color.blue(color1) * inverseRatio) + (Color.blue(color2) * ratio));
            return Color.argb(a, r, g, b);
        } catch (Exception e) {
            return color1; // Return original color if blending fails
        }
    }

    // Album rotation methods
    private void startAlbumRotation() {
        try {
            if (isDestroyed.get() || binding == null || binding.albumArt == null) return;

            stopAlbumRotation(); // Stop any existing rotation

            // Get current rotation to continue smoothly
            currentRotation = binding.albumArt.getRotation();

            isAnimationRunning.set(true);
            albumRotationAnimator = ObjectAnimator.ofFloat(binding.albumArt, "rotation",
                    currentRotation, currentRotation + 360f);
            albumRotationAnimator.setDuration(20000); // 20 seconds per rotation
            albumRotationAnimator.setRepeatCount(ValueAnimator.INFINITE);
            albumRotationAnimator.setInterpolator(new LinearInterpolator());

            albumRotationAnimator.addUpdateListener(animation -> {
                currentRotation = (Float) animation.getAnimatedValue();
            });

            albumRotationAnimator.start();
            Log.d(TAG, "Started album rotation from " + currentRotation + " degrees");

        } catch (Exception e) {
            Log.e(TAG, "Error starting album rotation", e);
        }
    }

    private void pauseAlbumRotation() {
        try {
            if (albumRotationAnimator != null && albumRotationAnimator.isRunning()) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                    albumRotationAnimator.pause();
                } else {
                    // For older versions, just stop and save current rotation
                    currentRotation = binding.albumArt != null ? binding.albumArt.getRotation() : 0f;
                    albumRotationAnimator.cancel();
                }
                isAnimationRunning.set(false);
                Log.d(TAG, "Paused album rotation at " + currentRotation + " degrees");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error pausing album rotation", e);
        }
    }

    private void resumeAlbumRotation() {
        try {
            if (isDestroyed.get() || !isPlaying) return;

            if (albumRotationAnimator != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                if (albumRotationAnimator.isPaused()) {
                    albumRotationAnimator.resume();
                    isAnimationRunning.set(true);
                    Log.d(TAG, "Resumed album rotation");
                    return;
                }
            }

            // If no paused animation or older Android version, start new rotation
            if (isPlaying) {
                startAlbumRotation();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error resuming album rotation", e);
        }
    }

    private void stopAlbumRotation() {
        try {
            if (albumRotationAnimator != null) {
                // Save current rotation before stopping
                if (binding != null && binding.albumArt != null) {
                    currentRotation = binding.albumArt.getRotation();
                }
                albumRotationAnimator.cancel();
                albumRotationAnimator = null;
                isAnimationRunning.set(false);
                Log.d(TAG, "Stopped album rotation at " + currentRotation + " degrees");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error stopping album rotation", e);
        }
    }

    private void sendToESP(int level) {
        try {
            synchronized (bluetoothLock) {
                if (outputStream != null && isBluetoothConnected) {
                    try {
                        outputStream.write((level + "\n").getBytes());
                        outputStream.flush();
                    } catch (IOException e) {
                        Log.e(TAG, "BT Send Error", e);
                        // Connection might be lost, update status
                        runOnUiThread(() -> {
                            if (!isDestroyed.get()) {
                                isBluetoothConnected = false;
                                updateStatus("Connection lost", "#FF5252");
                                if (binding != null && binding.connectBtn != null) {
                                    binding.connectBtn.setText("Connect");
                                    binding.connectBtn.setEnabled(true);
                                }
                            }
                        });
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error sending to ESP", e);
        }
    }

    private String formatTime(int milliseconds) {
        try {
            int seconds = milliseconds / 1000;
            int minutes = seconds / 60;
            seconds = seconds % 60;
            return String.format("%02d:%02d", minutes, seconds);
        } catch (Exception e) {
            Log.e(TAG, "Error formatting time", e);
            return "00:00";
        }
    }

    private void updateUI() {
        try {
            if (binding == null || isDestroyed.get()) {
                return;
            }

            // Update current song info if valid
            if (playlist != null && playlist.size() > 0 && currentSongIndex >= 0 && currentSongIndex < playlist.size()) {
                Song currentSong = playlist.get(currentSongIndex);

                if (binding.currentSongTitle != null && currentSong != null) {
                    binding.currentSongTitle.setText(currentSong.getTitle() + " • " + currentSong.getArtist());
                }

                if (binding.albumArt != null && currentSong != null) {
                    try {
                        binding.albumArt.setImageResource(currentSong.getAlbumArt());
                        // Restore rotation if there was any
                        binding.albumArt.setRotation(currentRotation);
                    } catch (Exception e) {
                        Log.e(TAG, "Error setting album art", e);
                        // Set a default image if there's an error
                        try {
                            binding.albumArt.setImageResource(android.R.drawable.ic_media_play);
                        } catch (Exception ex) {
                            Log.e(TAG, "Error setting default album art", ex);
                        }
                    }
                }
            }

            // Update play/pause button
            if (binding.playPauseBtn != null) {
                binding.playPauseBtn.setImageResource(isPlaying ?
                        android.R.drawable.ic_media_pause : android.R.drawable.ic_media_play);
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

        } catch (Exception e) {
            Log.e(TAG, "Error updating UI", e);
        }
    }

    private void updateStatus(String message, String color) {
        try {
            if (binding != null && binding.statusText != null && !isDestroyed.get()) {
                binding.statusText.setText(message);
                binding.statusText.setTextColor(Color.parseColor(color));
            }
        } catch (Exception e) {
            Log.e(TAG, "Error updating status", e);
        }
    }

    private void animateButton(View button) {
        try {
            if (button == null || isDestroyed.get()) return;

            ObjectAnimator scaleX = ObjectAnimator.ofFloat(button, "scaleX", 1f, 0.9f, 1f);
            ObjectAnimator scaleY = ObjectAnimator.ofFloat(button, "scaleY", 1f, 0.9f, 1f);
            scaleX.setDuration(150);
            scaleY.setDuration(150);
            scaleX.setInterpolator(new AccelerateDecelerateInterpolator());
            scaleY.setInterpolator(new AccelerateDecelerateInterpolator());
            scaleX.start();
            scaleY.start();
        } catch (Exception e) {
            Log.e(TAG, "Error animating button", e);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        try {
            isDestroyed.set(true);

            // Stop all updates and animations
            stopProgressUpdates();
            stopVisualizerAnimation();
            stopAlbumRotation();

            // Unbind from service
            if (isServiceBound) {
                try {
                    if (musicService != null) {
                        musicService.setServiceListener(null);
                    }
                    unbindService(serviceConnection);
                    isServiceBound = false;
                } catch (Exception e) {
                    Log.e(TAG, "Error unbinding service", e);
                }
            }

            if (gradientAnimator != null) {
                gradientAnimator.cancel();
                gradientAnimator = null;
            }

            // Disconnect bluetooth properly
            disconnectBluetooth();

            if (playlist != null) {
                savePlaylistOrder();
            }

            // Clean up handlers
            if (mainHandler != null) {
                mainHandler.removeCallbacksAndMessages(null);
                mainHandler = null;
            }

            if (visualizerHandler != null) {
                visualizerHandler.removeCallbacksAndMessages(null);
                visualizerHandler = null;
            }

            // Clean up view binding
            binding = null;

        } catch (Exception e) {
            Log.e(TAG, "Error in onDestroy", e);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        try {
            // Pause animations to save battery but keep music playing
            pauseAlbumRotation();
            stopVisualizerAnimation();
        } catch (Exception e) {
            Log.e(TAG, "Error in onPause", e);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        try {
            if (isDestroyed.get()) return;

            // Sync with service state when resuming
            if (isServiceBound && musicService != null) {
                currentSongIndex = musicService.getCurrentSongIndex();
                isPlaying = musicService.isPlaying();
                isRepeatOn = musicService.isRepeatOn();
                isShuffleOn = musicService.isShuffleOn();

                updateUI();

                // Resume animations if playing
                if (isPlaying) {
                    resumeAlbumRotation();
                    startProgressUpdates();
                    startVisualizerAnimation();
                }
            }

            // Recreate visualizer bars if they don't exist
            createVisualizerBarsIfNeeded();

        } catch (Exception e) {
            Log.e(TAG, "Error in onResume", e);
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        try {
            // Rebind to service if needed
            if (!isServiceBound && !isDestroyed.get()) {
                Intent serviceIntent = new Intent(this, MusicService.class);
                bindService(serviceIntent, serviceConnection, Context.BIND_AUTO_CREATE);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error in onStart", e);
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        try {
            // Stop updates when not visible but keep service connected
            stopProgressUpdates();
            stopVisualizerAnimation();
            pauseAlbumRotation();
        } catch (Exception e) {
            Log.e(TAG, "Error in onStop", e);
        }
    }

    @Override
    public void onConfigurationChanged(android.content.res.Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        try {
            // This prevents activity recreation on theme changes

            // Update UI after configuration change
            if (mainHandler != null) {
                mainHandler.post(() -> {
                    try {
                        if (!isDestroyed.get()) {
                            updateUI();
                            // Recreate visualizer bars if needed
                            if (visualizerBarViews.isEmpty()) {
                                createVisualizerBars();
                            }
                            // Restart animations if playing
                            if (isPlaying) {
                                startVisualizerAnimation();
                                resumeAlbumRotation();
                            }
                            // Restart gradient animation
                            setupGradientAnimation();
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Error updating UI after configuration change", e);
                    }
                });
            }

        } catch (Exception e) {
            Log.e(TAG, "Error in onConfigurationChanged", e);
        }
    }
}