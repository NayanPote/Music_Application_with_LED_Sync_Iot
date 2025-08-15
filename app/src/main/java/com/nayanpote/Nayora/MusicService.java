package com.nayanpote.Nayora;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.media.MediaPlayer;
import android.media.audiofx.Visualizer;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.media.session.MediaButtonReceiver;

import com.nayanpote.musicalledsbynayan.R;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class MusicService extends Service {
    private static final String TAG = "MusicService";
    private static final String CHANNEL_ID = "MusicPlayerChannel";
    private static final int NOTIFICATION_ID = 1;

    // Actions for notification buttons
    public static final String ACTION_PLAY_PAUSE = "com.nayanpote.Nayora.PLAY_PAUSE";
    public static final String ACTION_NEXT = "com.nayanpote.Nayora.NEXT";
    public static final String ACTION_PREVIOUS = "com.nayanpote.Nayora.PREVIOUS";
    public static final String ACTION_STOP = "com.nayanpote.Nayora.STOP";

    // Binder for activity communication
    private final IBinder binder = new MusicBinder();

    // Media components
    private MediaPlayer mediaPlayer;
    private MediaSessionCompat mediaSession;
    private NotificationManager notificationManager;
    private Visualizer visualizer;

    // Music data
    private List<Song> playlist = new ArrayList<>();
    private int currentSongIndex = 0;
    private boolean isPlaying = false;
    private boolean isRepeatOn = false;
    private boolean isShuffleOn = false;

    // Service listener for UI updates
    private ServiceListener serviceListener;

    // Visualizer data for notification
    private byte[] currentVisualizerData;
    private Handler visualizerHandler = new Handler();
    private Runnable visualizerUpdateRunnable;

    // Progress tracking
    private Handler progressHandler = new Handler();
    private Runnable progressRunnable;

    // Broadcast receiver for notification actions
    private BroadcastReceiver musicReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            Log.d(TAG, "Received action: " + action);

            if (action != null) {
                switch (action) {
                    case ACTION_PLAY_PAUSE:
                        togglePlayPause();
                        break;
                    case ACTION_NEXT:
                        playNext();
                        break;
                    case ACTION_PREVIOUS:
                        playPrevious();
                        break;
                    case ACTION_STOP:
                        stopPlayback();
                        stopSelf();
                        break;
                }
            }
        }
    };

    public class MusicBinder extends Binder {
        public MusicService getService() {
            return MusicService.this;
        }
    }

    public interface ServiceListener {
        void onPlaybackStateChanged(boolean isPlaying);
        void onSongChanged(int index);
        void onProgressUpdate(int currentPosition, int duration);
        void onVisualizerData(byte[] data);
    }

    @Override
    public void onCreate() {
        super.onCreate();

        notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        createNotificationChannel();
        setupMediaSession();
        registerMusicReceiver();

        Log.d(TAG, "MusicService created");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // Handle media button intents
        if (intent != null) {
            MediaButtonReceiver.handleIntent(mediaSession, intent);

            // Handle custom actions
            String action = intent.getAction();
            if (action != null) {
                switch (action) {
                    case ACTION_PLAY_PAUSE:
                        togglePlayPause();
                        break;
                    case ACTION_NEXT:
                        playNext();
                        break;
                    case ACTION_PREVIOUS:
                        playPrevious();
                        break;
                    case ACTION_STOP:
                        stopPlayback();
                        stopSelf();
                        break;
                }
            }
        }

        return START_STICKY; // Service will be restarted if killed
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        stopPlayback();
        stopVisualizer();
        stopProgressTracking();

        if (mediaSession != null) {
            mediaSession.release();
        }

        try {
            unregisterReceiver(musicReceiver);
        } catch (Exception e) {
            Log.e(TAG, "Error unregistering receiver", e);
        }

        Log.d(TAG, "MusicService destroyed");
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Nayora Music Player",
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("Music player controls with visualizer");
            channel.setShowBadge(true);
            channel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
            channel.enableLights(true);
            channel.setLightColor(Color.BLUE);
            notificationManager.createNotificationChannel(channel);
        }
    }

    private void setupMediaSession() {
        mediaSession = new MediaSessionCompat(this, "NayoraMusicService");
        mediaSession.setFlags(MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS |
                MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS);

        mediaSession.setCallback(new MediaSessionCompat.Callback() {
            @Override
            public void onPlay() {
                resumePlayback();
            }

            @Override
            public void onPause() {
                pausePlayback();
            }

            @Override
            public void onSkipToNext() {
                playNext();
            }

            @Override
            public void onSkipToPrevious() {
                playPrevious();
            }

            @Override
            public void onStop() {
                stopPlayback();
                stopSelf();
            }

            @Override
            public void onSeekTo(long pos) {
                seekTo((int) pos);
            }
        });

        mediaSession.setActive(true);
    }

    private void registerMusicReceiver() {
        IntentFilter filter = new IntentFilter();
        filter.addAction(ACTION_PLAY_PAUSE);
        filter.addAction(ACTION_NEXT);
        filter.addAction(ACTION_PREVIOUS);
        filter.addAction(ACTION_STOP);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(musicReceiver, filter, Context.RECEIVER_EXPORTED);
        } else {
            registerReceiver(musicReceiver, filter);
        }
    }

    public void setPlaylist(List<Song> playlist) {
        if (playlist != null) {
            this.playlist = new ArrayList<>(playlist);
        }
    }

    public void setServiceListener(ServiceListener listener) {
        this.serviceListener = listener;
    }

    public void startPlayback(int index) {
        if (playlist == null || playlist.isEmpty() || index < 0 || index >= playlist.size()) {
            Log.w(TAG, "Invalid playlist or index");
            return;
        }

        stopPlayback();
        currentSongIndex = index;
        Song song = playlist.get(index);

        try {
            mediaPlayer = MediaPlayer.create(this, song.getResourceId());
            if (mediaPlayer == null) {
                Log.e(TAG, "Failed to create MediaPlayer for resource: " + song.getResourceId());
                return;
            }

            mediaPlayer.setOnCompletionListener(mp -> {
                if (isRepeatOn) {
                    startPlayback(currentSongIndex);
                } else {
                    playNext();
                }
            });

            mediaPlayer.setOnErrorListener((mp, what, extra) -> {
                Log.e(TAG, "MediaPlayer error: " + what + ", " + extra);
                // Try to recover by playing next song
                playNext();
                return true;
            });

            mediaPlayer.start();
            isPlaying = true;

            updateMediaSession();
            showNotification();
            startProgressTracking();
            startVisualizer();

            if (serviceListener != null) {
                serviceListener.onPlaybackStateChanged(isPlaying);
                serviceListener.onSongChanged(currentSongIndex);
            }

            Log.d(TAG, "Started playback: " + song.getTitle());

        } catch (Exception e) {
            Log.e(TAG, "Error starting playback", e);
        }
    }

    public void togglePlayPause() {
        if (mediaPlayer == null) {
            if (playlist != null && !playlist.isEmpty()) {
                startPlayback(currentSongIndex);
            }
        } else if (isPlaying) {
            pausePlayback();
        } else {
            resumePlayback();
        }
    }

    public void pausePlayback() {
        if (mediaPlayer != null && mediaPlayer.isPlaying()) {
            try {
                mediaPlayer.pause();
                isPlaying = false;
                stopVisualizer();
                updateMediaSession();
                showNotification();

                if (serviceListener != null) {
                    serviceListener.onPlaybackStateChanged(isPlaying);
                }

                Log.d(TAG, "Playback paused");
            } catch (Exception e) {
                Log.e(TAG, "Error pausing playback", e);
            }
        }
    }

    public void resumePlayback() {
        if (mediaPlayer != null && !mediaPlayer.isPlaying()) {
            try {
                mediaPlayer.start();
                isPlaying = true;
                startVisualizer();
                updateMediaSession();
                showNotification();
                startProgressTracking();

                if (serviceListener != null) {
                    serviceListener.onPlaybackStateChanged(isPlaying);
                }

                Log.d(TAG, "Playback resumed");
            } catch (Exception e) {
                Log.e(TAG, "Error resuming playback", e);
            }
        }
    }

    public void stopPlayback() {
        if (mediaPlayer != null) {
            try {
                if (mediaPlayer.isPlaying()) {
                    mediaPlayer.stop();
                }
                mediaPlayer.release();
            } catch (Exception e) {
                Log.e(TAG, "Error stopping media player", e);
            }
            mediaPlayer = null;
        }

        isPlaying = false;
        stopVisualizer();
        stopProgressTracking();

        try {
            notificationManager.cancel(NOTIFICATION_ID);
            stopForeground(true);
        } catch (Exception e) {
            Log.e(TAG, "Error stopping foreground", e);
        }

        if (serviceListener != null) {
            serviceListener.onPlaybackStateChanged(isPlaying);
        }

        Log.d(TAG, "Playback stopped");
    }

    public void playNext() {
        if (playlist == null || playlist.isEmpty()) return;

        if (isShuffleOn) {
            currentSongIndex = (int) (Math.random() * playlist.size());
        } else {
            currentSongIndex = (currentSongIndex + 1) % playlist.size();
        }
        startPlayback(currentSongIndex);
        Log.d(TAG, "Playing next song");
    }

    public void playPrevious() {
        if (playlist == null || playlist.isEmpty()) return;

        currentSongIndex = (currentSongIndex - 1 + playlist.size()) % playlist.size();
        startPlayback(currentSongIndex);
        Log.d(TAG, "Playing previous song");
    }

    public void seekTo(int position) {
        if (mediaPlayer != null) {
            try {
                mediaPlayer.seekTo(position);
            } catch (Exception e) {
                Log.e(TAG, "Error seeking", e);
            }
        }
    }

    private void startVisualizer() {
        if (mediaPlayer == null) return;
        stopVisualizer();

        try {
            visualizer = new Visualizer(mediaPlayer.getAudioSessionId());
            visualizer.setCaptureSize(Visualizer.getCaptureSizeRange()[1]);

            visualizer.setDataCaptureListener(new Visualizer.OnDataCaptureListener() {
                @Override
                public void onWaveFormDataCapture(Visualizer vis, byte[] waveform, int samplingRate) {
                    // Not used
                }

                @Override
                public void onFftDataCapture(Visualizer vis, byte[] fft, int samplingRate) {
                    currentVisualizerData = fft.clone();
                    if (serviceListener != null) {
                        serviceListener.onVisualizerData(fft);
                    }
                    // Update notification with visualizer data periodically
                    updateNotificationVisualizer();
                }
            }, Visualizer.getMaxCaptureRate() / 2, false, true);

            visualizer.setEnabled(true);
            Log.d(TAG, "Visualizer started");

        } catch (Exception e) {
            Log.e(TAG, "Visualizer Error", e);
        }
    }

    private void stopVisualizer() {
        if (visualizer != null) {
            try {
                visualizer.setEnabled(false);
                visualizer.release();
            } catch (Exception e) {
                Log.e(TAG, "Error stopping visualizer", e);
            }
            visualizer = null;
            currentVisualizerData = null;
        }
    }

    private void updateNotificationVisualizer() {
        // Update notification every 500ms with new visualizer data
        if (visualizerHandler != null) {
            visualizerHandler.removeCallbacks(visualizerUpdateRunnable);
            visualizerUpdateRunnable = new Runnable() {
                @Override
                public void run() {
                    if (isPlaying) {
                        showNotification();
                    }
                }
            };
            visualizerHandler.postDelayed(visualizerUpdateRunnable, 500);
        }
    }

    private void updateMediaSession() {
        if (playlist == null || currentSongIndex >= playlist.size()) return;

        Song currentSong = playlist.get(currentSongIndex);

        // Update metadata
        MediaMetadataCompat.Builder metadataBuilder = new MediaMetadataCompat.Builder()
                .putString(MediaMetadataCompat.METADATA_KEY_TITLE, currentSong.getTitle())
                .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, currentSong.getArtist())
                .putString(MediaMetadataCompat.METADATA_KEY_ALBUM, "Nayora Playlist");

        // Add album art with visualizer overlay
        Bitmap albumArt = createAlbumArtWithVisualizer(currentSong);
        if (albumArt != null) {
            metadataBuilder.putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, albumArt);
        }

        if (mediaPlayer != null) {
            try {
                metadataBuilder.putLong(MediaMetadataCompat.METADATA_KEY_DURATION, mediaPlayer.getDuration());
            } catch (Exception e) {
                Log.e(TAG, "Error getting duration", e);
            }
        }

        mediaSession.setMetadata(metadataBuilder.build());

        // Update playback state
        PlaybackStateCompat.Builder stateBuilder = new PlaybackStateCompat.Builder()
                .setActions(PlaybackStateCompat.ACTION_PLAY_PAUSE |
                        PlaybackStateCompat.ACTION_SKIP_TO_NEXT |
                        PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS |
                        PlaybackStateCompat.ACTION_STOP |
                        PlaybackStateCompat.ACTION_SEEK_TO)
                .setState(isPlaying ? PlaybackStateCompat.STATE_PLAYING : PlaybackStateCompat.STATE_PAUSED,
                        mediaPlayer != null ? getCurrentPosition() : 0,
                        1.0f);

        mediaSession.setPlaybackState(stateBuilder.build());
    }

    private Bitmap createAlbumArtWithVisualizer(Song song) {
        try {
            // Get original album art
            Bitmap originalBitmap = BitmapFactory.decodeResource(getResources(), song.getAlbumArt());
            if (originalBitmap == null) {
                // Create default bitmap if album art not found
                originalBitmap = Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888);
                Canvas canvas = new Canvas(originalBitmap);
                canvas.drawColor(Color.parseColor("#1E88E5"));
            }

            // Create a mutable copy
            Bitmap resultBitmap = originalBitmap.copy(Bitmap.Config.ARGB_8888, true);
            Canvas canvas = new Canvas(resultBitmap);

            // Draw visualizer bars if data is available
            if (currentVisualizerData != null && isPlaying) {
                drawVisualizerOnBitmap(canvas, resultBitmap.getWidth(), resultBitmap.getHeight());
            }

            return resultBitmap;

        } catch (Exception e) {
            Log.e(TAG, "Error creating album art with visualizer", e);
            try {
                return BitmapFactory.decodeResource(getResources(), song.getAlbumArt());
            } catch (Exception e2) {
                Log.e(TAG, "Error loading default album art", e2);
                // Return a simple colored bitmap as fallback
                Bitmap fallback = Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888);
                Canvas canvas = new Canvas(fallback);
                canvas.drawColor(Color.parseColor("#1E88E5"));
                return fallback;
            }
        }
    }

    private void drawVisualizerOnBitmap(Canvas canvas, int width, int height) {
        if (currentVisualizerData == null) return;

        Paint paint = new Paint();
        paint.setAntiAlias(true);
        paint.setColor(Color.WHITE);
        paint.setAlpha(180);

        int barCount = 32;
        int barWidth = width / (barCount * 2);
        int maxBarHeight = height / 4;

        for (int i = 0; i < barCount && i * 2 < currentVisualizerData.length; i++) {
            int fftIndex = (i + 1) * 2;
            if (fftIndex + 1 >= currentVisualizerData.length) break;

            float real = currentVisualizerData[fftIndex];
            float imaginary = currentVisualizerData[fftIndex + 1];
            float magnitude = (float) Math.sqrt(real * real + imaginary * imaginary);
            float dbValue = 20 * (float) Math.log10(magnitude + 1);

            int barHeight = Math.max(5, Math.min(maxBarHeight, (int)(dbValue * 3)));
            int x = i * barWidth * 2 + barWidth / 2;
            int y = height - 50;

            // Draw bar
            canvas.drawRect(x, y - barHeight, x + barWidth, y, paint);
        }
    }

    private void showNotification() {
        if (playlist == null || currentSongIndex >= playlist.size()) return;

        Song currentSong = playlist.get(currentSongIndex);

        try {
            // Create pending intents for notification actions with unique request codes
            Intent playPauseIntent = new Intent(ACTION_PLAY_PAUSE);
            playPauseIntent.setPackage(getPackageName());
            PendingIntent playPausePendingIntent = PendingIntent.getBroadcast(this, 100, playPauseIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

            Intent nextIntent = new Intent(ACTION_NEXT);
            nextIntent.setPackage(getPackageName());
            PendingIntent nextPendingIntent = PendingIntent.getBroadcast(this, 101, nextIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

            Intent prevIntent = new Intent(ACTION_PREVIOUS);
            prevIntent.setPackage(getPackageName());
            PendingIntent prevPendingIntent = PendingIntent.getBroadcast(this, 102, prevIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

            Intent stopIntent = new Intent(ACTION_STOP);
            stopIntent.setPackage(getPackageName());
            PendingIntent stopPendingIntent = PendingIntent.getBroadcast(this, 103, stopIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

            // Intent to open the app when notification is tapped
            Intent openAppIntent = new Intent(this, MainActivity.class);
            openAppIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            PendingIntent openAppPendingIntent = PendingIntent.getActivity(this, 104, openAppIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

            // Get album art with visualizer
            Bitmap albumArt = createAlbumArtWithVisualizer(currentSong);

            // Build notification with larger action buttons
            NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                    .setContentTitle(currentSong.getTitle())
                    .setContentText(currentSong.getArtist())
                    .setSubText("🎵 Nayora Music Player")
                    .setLargeIcon(albumArt)
                    .setSmallIcon(R.drawable.ic_music_note)
                    .setContentIntent(openAppPendingIntent)
                    .setDeleteIntent(stopPendingIntent)
                    .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                    .setOngoing(isPlaying)
                    .setShowWhen(false)
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setStyle(new androidx.media.app.NotificationCompat.MediaStyle()
                            .setMediaSession(mediaSession.getSessionToken())
                            .setShowActionsInCompactView(0, 1, 2)
                            .setShowCancelButton(true)
                            .setCancelButtonIntent(stopPendingIntent))

                    // Add larger action buttons
                    .addAction(new NotificationCompat.Action.Builder(
                            R.drawable.ic_previous,
                            "Previous",
                            prevPendingIntent)
                            .build())
                    .addAction(new NotificationCompat.Action.Builder(
                            isPlaying ? R.drawable.ic_pause : R.drawable.ic_play,
                            isPlaying ? "Pause" : "Play",
                            playPausePendingIntent)
                            .build())
                    .addAction(new NotificationCompat.Action.Builder(
                            R.drawable.ic_next,
                            "Next",
                            nextPendingIntent)
                            .build());

            Notification notification = builder.build();

            if (isPlaying) {
                startForeground(NOTIFICATION_ID, notification);
            } else {
                stopForeground(false);
                notificationManager.notify(NOTIFICATION_ID, notification);
            }

        } catch (Exception e) {
            Log.e(TAG, "Error showing notification", e);
        }
    }

    private void startProgressTracking() {
        stopProgressTracking();
        if (progressHandler != null) {
            progressRunnable = new Runnable() {
                @Override
                public void run() {
                    if (mediaPlayer != null && isPlaying) {
                        try {
                            int currentPos = getCurrentPosition();
                            int duration = getDuration();

                            if (serviceListener != null) {
                                serviceListener.onProgressUpdate(currentPos, duration);
                            }

                            // Update media session playback state
                            updateMediaSession();

                            progressHandler.postDelayed(this, 1000);
                        } catch (Exception e) {
                            Log.e(TAG, "Progress tracking error", e);
                        }
                    }
                }
            };
            progressHandler.post(progressRunnable);
        }
    }

    private void stopProgressTracking() {
        if (progressHandler != null && progressRunnable != null) {
            progressHandler.removeCallbacks(progressRunnable);
            progressRunnable = null;
        }
    }

    // Getters for current state
    public boolean isPlaying() { return isPlaying; }
    public int getCurrentSongIndex() { return currentSongIndex; }
    public Song getCurrentSong() {
        return (playlist != null && currentSongIndex >= 0 && currentSongIndex < playlist.size())
                ? playlist.get(currentSongIndex) : null;
    }

    public int getCurrentPosition() {
        try {
            return (mediaPlayer != null) ? mediaPlayer.getCurrentPosition() : 0;
        } catch (Exception e) {
            Log.e(TAG, "Error getting current position", e);
            return 0;
        }
    }

    public int getDuration() {
        try {
            return (mediaPlayer != null) ? mediaPlayer.getDuration() : 0;
        } catch (Exception e) {
            Log.e(TAG, "Error getting duration", e);
            return 0;
        }
    }

    public void setRepeatMode(boolean repeat) {
        this.isRepeatOn = repeat;
        Log.d(TAG, "Repeat mode: " + repeat);
    }

    public void setShuffleMode(boolean shuffle) {
        this.isShuffleOn = shuffle;
        Log.d(TAG, "Shuffle mode: " + shuffle);
    }

    public boolean isRepeatOn() { return isRepeatOn; }
    public boolean isShuffleOn() { return isShuffleOn; }
}