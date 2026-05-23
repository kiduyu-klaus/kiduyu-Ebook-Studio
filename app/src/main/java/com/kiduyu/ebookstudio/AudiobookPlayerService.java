package com.kiduyu.ebookstudio;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.OptIn;
import androidx.core.app.NotificationCompat;
import androidx.lifecycle.LifecycleService;
import androidx.media3.common.AudioAttributes;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MediaMetadata;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.datasource.DefaultDataSource;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
import androidx.media3.exoplayer.source.MediaSource;
import androidx.media3.session.MediaSession;
import androidx.media3.ui.PlayerNotificationManager;


import com.bumptech.glide.Glide;
import com.bumptech.glide.request.target.CustomTarget;
import com.bumptech.glide.request.transition.Transition;
import com.kiduyu.item.Audiobook;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

public class AudiobookPlayerService extends LifecycleService {

    private static final String TAG = "AudiobookPlayerService";
    private static final String CHANNEL_ID = "audiobook_player_channel";
    private static final int NOTIFICATION_ID = 1001;
    private static final int PLAYLIST_REQUEST_CODE = 2001;

    // Playback states
    public static final int STATE_IDLE = 0;
    public static final int STATE_BUFFERING = 1;
    public static final int STATE_PLAYING = 2;
    public static final int STATE_PAUSED = 3;
    public static final int STATE_STOPPED = 4;

    // Define unique request codes as constants at the top of the class
    private static final int RC_PLAY_PAUSE = 100;
    private static final int RC_PREVIOUS   = 101;
    private static final int RC_NEXT       = 102;
    private static final int RC_PLAYLIST   = 103;

    // Playback modes
    public static final int MODE_NORMAL = 0;
    public static final int MODE_SHUFFLE = 1;
    public static final int MODE_REPEAT_ONE = 2;
    public static final int MODE_REPEAT_ALL = 3;

    private ExoPlayer exoPlayer;
    private MediaSession mediaSession;
    private final IBinder binder = new LocalBinder();
    private Audiobook currentAudiobook;
    private int currentTrackIndex = 0;
    private int playbackState = STATE_IDLE;
    private int playMode = MODE_NORMAL;
    private boolean isFromSearch = false;

    private Handler progressHandler;
    private Runnable progressRunnable;
    private boolean isPrepared = false;

    private OnPlayerStateListener playerStateListener;
    private OnPlaylistListener playlistListener;

    // Custom headers for Cloudflare bypass
    private static final String CUSTOM_USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";
    private static final String CUSTOM_ACCEPT = "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8";
    private static final String CUSTOM_ACCEPT_LANGUAGE = "en-US,en;q=0.9";
    private static final String CUSTOM_ACCEPT_ENCODING = "gzip, deflate, br";
    private static final String CUSTOM_ORIGIN = "https://audiobooks4soul.com";

    public class LocalBinder extends Binder {
        public AudiobookPlayerService getService() {
            return AudiobookPlayerService.this;
        }
    }

    public interface OnPlayerStateListener {
        void onPlaybackStateChanged(int state);
        void onProgressChanged(int currentPosition, int duration);
        void onTrackChanged(int index, String trackName);
        void onError(String error);
    }

    public interface OnPlaylistListener {
        void onPlaylistRequested(List<String> trackNames, int currentIndex);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        progressHandler = new Handler(getMainLooper());
        createNotificationChannel();
        setupExoPlayer();
        setupMediaSession();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Audiobook Player",
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("Audiobook playback controls");
            channel.setShowBadge(false);
            channel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
            channel.setSound(null, null);

            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            if (notificationManager != null) {
                notificationManager.createNotificationChannel(channel);
            }
        }
    }

    @OptIn(markerClass = UnstableApi.class)
    private void setupExoPlayer() {
        // Build headers map for Cloudflare bypass
        Map<String, String> headers = new HashMap<>();
        headers.put("User-Agent", CUSTOM_USER_AGENT);
        headers.put("Accept", CUSTOM_ACCEPT);
        headers.put("Accept-Language", CUSTOM_ACCEPT_LANGUAGE);
        headers.put("Accept-Encoding", CUSTOM_ACCEPT_ENCODING);
        headers.put("Origin", CUSTOM_ORIGIN);
        // Add session cookie if available

        // Add referer from the audiobook detail URL


        // Create custom DataSource.Factory with headers
        DefaultHttpDataSource.Factory httpDataSourceFactory = new DefaultHttpDataSource.Factory()
                .setDefaultRequestProperties(headers)
                .setUserAgent(CUSTOM_USER_AGENT);

        DefaultDataSource.Factory dataSourceFactory = new DefaultDataSource.Factory(
                this,
                httpDataSourceFactory
        );

        DefaultMediaSourceFactory mediaSourceFactory = new DefaultMediaSourceFactory(dataSourceFactory);

        // Build ExoPlayer with custom data source factory
        AudioAttributes audioAttributes = new AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                .build();

        exoPlayer = new ExoPlayer.Builder(this)
                .setMediaSourceFactory(mediaSourceFactory)
                .setAudioAttributes(audioAttributes, true)
                .setHandleAudioBecomingNoisy(true)
                .build();

        // Add listener for player events
        exoPlayer.addListener(new Player.Listener() {
            @Override
            public void onPlaybackStateChanged(int state) {
                Log.d(TAG, "Playback state changed: " + state);
                if (state == Player.STATE_READY) {
                    isPrepared = true;
                    if (exoPlayer.getPlayWhenReady()) {
                        playbackState = STATE_PLAYING;
                    } else {
                        playbackState = STATE_PAUSED;
                    }
                    notifyPlaybackStateChanged();
                    startProgressUpdates();
                } else if (state == Player.STATE_BUFFERING) {
                    playbackState = STATE_BUFFERING;
                    notifyPlaybackStateChanged();
                } else if (state == Player.STATE_ENDED) {
                    onTrackCompletion();
                } else if (state == Player.STATE_IDLE) {
                    playbackState = STATE_IDLE;
                    notifyPlaybackStateChanged();
                }
            }

            @Override
            public void onIsPlayingChanged(boolean isPlaying) {
                Log.d(TAG, "Is playing changed: " + isPlaying);
                if (isPlaying) {
                    playbackState = STATE_PLAYING;
                } else if (exoPlayer.getPlaybackState() == Player.STATE_READY) {
                    playbackState = STATE_PAUSED;
                }
                notifyPlaybackStateChanged();

                if (isPlaying) {
                    startProgressUpdates();
                } else {
                    stopProgressUpdates();
                }

                updateNotification();
            }

            @Override
            public void onPlayerError(PlaybackException error) {
                Log.e(TAG, "Player error: " + error.getMessage());
                playbackState = STATE_IDLE;
                isPrepared = false;
                notifyPlaybackStateChanged();
                stopProgressUpdates();

                if (playerStateListener != null) {
                    playerStateListener.onError("Playback error: " + error.getMessage());
                }
            }
        });
    }

    private void setupMediaSession() {
        mediaSession = new MediaSession.Builder(this, exoPlayer)
                .setCallback(new MediaSession.Callback() {
                    // Using default implementations - override if needed
                })
                .build();
    }

    public void setAudiobook(Audiobook audiobook, boolean fromSearch) {
        if (audiobook == null) return;

        this.currentAudiobook = audiobook;
        this.isFromSearch = fromSearch;
        this.currentTrackIndex = 0;
        this.isPrepared = false;

        if (playerStateListener != null && audiobook.getTrackNames() != null && !audiobook.getTrackNames().isEmpty()) {
            playerStateListener.onTrackChanged(0, audiobook.getTrackNames().get(0));
        }
    }

    @OptIn(markerClass = UnstableApi.class)
    public void loadTrack(int index) {
        if (currentAudiobook == null || currentAudiobook.getAudioUrls() == null) return;

        if (index >= 0 && index < currentAudiobook.getAudioUrls().size()) {
            currentTrackIndex = index;
            String audioUrl = currentAudiobook.getAudioUrls().get(index);
            Log.i(TAG, "Loading track: " + audioUrl + " | isFromSearch: " + isFromSearch);

            try {
                MediaItem mediaItem = new MediaItem.Builder()
                        .setUri(Uri.parse(audioUrl))
                        .setMediaMetadata(new MediaMetadata.Builder()
                                .setTitle(getCurrentTrackName())
                                .setArtist(currentAudiobook != null ? currentAudiobook.getAuthor() : "Unknown Author")
                                .build())
                        .build();

                // Only apply Cloudflare bypass headers for audiobooks from audiobooks4soul.com
                if (isFromSearch) {
                    Map<String, String> headers = new HashMap<>();
                    headers.put("User-Agent", CUSTOM_USER_AGENT);
                    headers.put("Accept", CUSTOM_ACCEPT);
                    headers.put("Accept-Language", CUSTOM_ACCEPT_LANGUAGE);
                    headers.put("Accept-Encoding", CUSTOM_ACCEPT_ENCODING);
                    headers.put("Origin", CUSTOM_ORIGIN);

                    if (currentAudiobook.getSessionCookie() != null && !currentAudiobook.getSessionCookie().isEmpty()) {
                        headers.put("Cookie", currentAudiobook.getSessionCookie());
                        Log.d(TAG, "Using session cookie for audiobooks4soul: " + currentAudiobook.getSessionCookie());
                    }

                    if (currentAudiobook.getUrl() != null && !currentAudiobook.getUrl().isEmpty()) {
                        headers.put("Referer", currentAudiobook.getUrl());
                        Log.d(TAG, "Using referer: " + currentAudiobook.getUrl());
                    }

                    DefaultHttpDataSource.Factory httpDataSourceFactory = new DefaultHttpDataSource.Factory()
                            .setDefaultRequestProperties(headers)
                            .setUserAgent(CUSTOM_USER_AGENT);

                    DefaultDataSource.Factory dataSourceFactory = new DefaultDataSource.Factory(
                            this,
                            httpDataSourceFactory
                    );

                    DefaultMediaSourceFactory mediaSourceFactory = new DefaultMediaSourceFactory(dataSourceFactory);

                    // Use the factory to create the source manually
                    MediaSource mediaSource = mediaSourceFactory.createMediaSource(mediaItem);

                    // Pass the source to the player
                    exoPlayer.setMediaSource(mediaSource);
                    Log.d(TAG, "Applied Cloudflare bypass headers for audiobooks4soul");
                } else {
                    // Use standard playback for regular audiobooks
                    exoPlayer.setMediaItem(mediaItem);
                    Log.d(TAG, "Using standard playback for regular audiobooks");
                }

                exoPlayer.prepare();
                exoPlayer.setPlayWhenReady(true);

                playbackState = STATE_BUFFERING;
                notifyPlaybackStateChanged();

                if (playerStateListener != null) {
                    String trackName = currentTrackIndex < currentAudiobook.getTrackNames().size()
                            ? currentAudiobook.getTrackNames().get(currentTrackIndex)
                            : "Track " + (currentTrackIndex + 1);
                    playerStateListener.onTrackChanged(currentTrackIndex, trackName);
                }

            } catch (Exception e) {
                Log.e(TAG, "Error loading track", e);
                if (playerStateListener != null) {
                    playerStateListener.onError("Error loading track: " + e.getMessage());
                }
            }
        }
    }

    public void play() {
        if (currentAudiobook == null) return;

        if (!isPrepared) {
            loadTrack(currentTrackIndex);
            return;
        }

        exoPlayer.play();
        playbackState = STATE_PLAYING;
        notifyPlaybackStateChanged();
        startProgressUpdates();
        startForeground(NOTIFICATION_ID, buildNotification());
    }

    public void pause() {
        if (exoPlayer.isPlaying()) {
            exoPlayer.pause();
            playbackState = STATE_PAUSED;
            notifyPlaybackStateChanged();
            stopProgressUpdates();
            startForeground(NOTIFICATION_ID, buildNotification());
        }
    }

    public void stop() {
        exoPlayer.stop();
        isPrepared = false;
        playbackState = STATE_STOPPED;
        notifyPlaybackStateChanged();
        stopProgressUpdates();
        stopForeground(true);
    }

    public void nextTrack() {
        if (currentAudiobook == null || currentAudiobook.getAudioUrls() == null) return;

        int nextIndex;
        if (playMode == MODE_SHUFFLE) {
            nextIndex = new Random().nextInt(currentAudiobook.getAudioUrls().size());
        } else {
            nextIndex = currentTrackIndex + 1;
            if (nextIndex >= currentAudiobook.getAudioUrls().size()) {
                if (playMode == MODE_REPEAT_ALL) nextIndex = 0;
                else return;
            }
        }

        loadTrack(nextIndex); // ✅ playWhenReady handles auto-play
    }

    public void previousTrack() {
        if (currentAudiobook == null || currentAudiobook.getAudioUrls() == null) return;
        int prevIndex = Math.max(0, currentTrackIndex - 1);
        loadTrack(prevIndex); // ✅ same
    }

    public void seekTo(int position) {
        if (isPrepared) {
            exoPlayer.seekTo(position);
        }
    }

    public void setPlayMode(int mode) {
        this.playMode = mode;
        switch (mode) {
            case MODE_REPEAT_ONE:
                exoPlayer.setRepeatMode(Player.REPEAT_MODE_ONE);
                break;
            case MODE_REPEAT_ALL:
                exoPlayer.setRepeatMode(Player.REPEAT_MODE_ALL);
                break;
            default:
                exoPlayer.setRepeatMode(Player.REPEAT_MODE_OFF);
                break;
        }
    }

    public int getPlayMode() {
        return playMode;
    }

    public int getCurrentTrackIndex() {
        return currentTrackIndex;
    }

    public String getCurrentTrackName() {
        if (currentAudiobook != null && currentAudiobook.getTrackNames() != null
                && currentTrackIndex < currentAudiobook.getTrackNames().size()) {
            return currentAudiobook.getTrackNames().get(currentTrackIndex);
        }
        return "Track " + (currentTrackIndex + 1);
    }

    public int getPlaybackState() {
        return playbackState;
    }

    public int getCurrentPosition() {
        if (isPrepared && exoPlayer != null) {
            return (int) exoPlayer.getCurrentPosition();
        }
        return 0;
    }

    public int getDuration() {
        if (isPrepared && exoPlayer != null) {
            return (int) exoPlayer.getDuration();
        }
        return 0;
    }

    public Audiobook getCurrentAudiobook() {
        return currentAudiobook;
    }

    public boolean isPlaying() {
        return exoPlayer != null && exoPlayer.isPlaying();
    }

    public void showPlaylist() {
        if (playlistListener != null && currentAudiobook != null) {
            playlistListener.onPlaylistRequested(
                    currentAudiobook.getTrackNames(),
                    currentTrackIndex
            );
        }
    }

    public void setOnPlayerStateListener(OnPlayerStateListener listener) {
        this.playerStateListener = listener;
    }

    public void setOnPlaylistListener(OnPlaylistListener listener) {
        this.playlistListener = listener;
    }

    private void onTrackCompletion() {
        if (playMode == MODE_REPEAT_ONE) {
            loadTrack(currentTrackIndex); // playWhenReady=true handles it
        } else if (currentTrackIndex < currentAudiobook.getAudioUrls().size() - 1) {
            nextTrack();
        } else {
            playbackState = STATE_STOPPED;
            notifyPlaybackStateChanged();
            stopProgressUpdates();
            stopForeground(false); // ✅ keep notification but stop foreground
        }
    }

    private void startProgressUpdates() {
        stopProgressUpdates();
        progressRunnable = new Runnable() {
            @Override
            public void run() {
                if (playbackState == STATE_PLAYING && playerStateListener != null) {
                    playerStateListener.onProgressChanged(getCurrentPosition(), getDuration());
                }
                progressHandler.postDelayed(this, 1000);
            }
        };
        progressHandler.post(progressRunnable);
    }

    private void stopProgressUpdates() {
        if (progressRunnable != null) {
            progressHandler.removeCallbacks(progressRunnable);
            progressRunnable = null;
        }
    }

    private void notifyPlaybackStateChanged() {
        if (playerStateListener != null) {
            playerStateListener.onPlaybackStateChanged(playbackState);
        }
    }

    private void updateNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (notificationManager != null) {
                notificationManager.notify(NOTIFICATION_ID, buildNotification());
            }
        }
    }


    @OptIn(markerClass = UnstableApi.class)
    private Notification buildNotification() {
        Intent contentIntent = new Intent(this, AudiobookPlayerActivity.class);
        contentIntent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent contentPendingIntent = PendingIntent.getActivity(
                this, 0, contentIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        Intent playPauseIntent = new Intent(this, AudiobookPlayerService.class);
        playPauseIntent.setAction(playbackState == STATE_PLAYING ? "ACTION_PAUSE" : "ACTION_PLAY");
        PendingIntent playPausePendingIntent = PendingIntent.getService(
                this, RC_PLAY_PAUSE, playPauseIntent,  // ✅ unique code
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        Intent prevIntent = new Intent(this, AudiobookPlayerService.class);
        prevIntent.setAction("ACTION_PREVIOUS");
        PendingIntent prevPendingIntent = PendingIntent.getService(
                this, RC_PREVIOUS, prevIntent,          // ✅ unique code
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        Intent nextIntent = new Intent(this, AudiobookPlayerService.class);
        nextIntent.setAction("ACTION_NEXT");
        PendingIntent nextPendingIntent = PendingIntent.getService(
                this, RC_NEXT, nextIntent,              // ✅ unique code
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        Intent playlistIntent = new Intent(this, AudiobookPlayerService.class);
        playlistIntent.setAction("ACTION_PLAYLIST");
        PendingIntent playlistPendingIntent = PendingIntent.getService(
                this, RC_PLAYLIST, playlistIntent,      // ✅ unique code
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        String title = currentAudiobook != null ? currentAudiobook.getTitle() : "Audiobook";
        String artist = currentAudiobook != null ? currentAudiobook.getAuthor() : "Unknown Author";
        int playPauseIcon = playbackState == STATE_PLAYING
                ? android.R.drawable.ic_media_pause
                : android.R.drawable.ic_media_play;
        String playPauseText = playbackState == STATE_PLAYING ? "Pause" : "Play";

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_play)
                .setContentTitle(title)
                .setContentText(artist)
                .setContentIntent(contentPendingIntent)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
                .addAction(android.R.drawable.ic_media_previous, "Previous", prevPendingIntent)  // index 0
                .addAction(playPauseIcon, playPauseText, playPausePendingIntent)                  // index 1
                .addAction(android.R.drawable.ic_media_next, "Next", nextPendingIntent)          // index 2
                .addAction(R.drawable.ic_playlist, "Playlist", playlistPendingIntent)            // index 3
                .setStyle(new androidx.media.app.NotificationCompat.MediaStyle()
                        .setMediaSession(mediaSession.getSessionCompatToken())
                        .setShowActionsInCompactView(0, 1, 2)); // ✅ max 3 indices: prev, play/pause, next

        if (currentAudiobook != null && currentAudiobook.getImageUrl() != null
                && !currentAudiobook.getImageUrl().isEmpty()) {
            loadAndSetAlbumArt(builder, currentAudiobook.getImageUrl());
        }

        return builder.build();
    }

    private void loadAndSetAlbumArt(NotificationCompat.Builder builder, String imageUrl) {
        // ✅ Reject null, empty, or data: URIs before Glide ever sees them
        if (imageUrl == null || imageUrl.isEmpty() || imageUrl.startsWith("data:")) {
            Log.w(TAG, "Skipping invalid album art URL: " + imageUrl);
            return;
        }
        try {
            Glide.with(getApplicationContext())
                    .asBitmap()
                    .load(imageUrl)
                    .placeholder(R.drawable.placeholder_portable)
                    .into(new CustomTarget<Bitmap>() {
                        @Override
                        public void onResourceReady(@NonNull Bitmap resource, @Nullable Transition<? super Bitmap> transition) {
                            builder.setLargeIcon(resource);
                            NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
                            if (notificationManager != null) {
                                notificationManager.notify(NOTIFICATION_ID, builder.build());
                            }
                        }

                        @Override
                        public void onLoadCleared(@Nullable Drawable placeholder) {
                        }
                    });
        } catch (Exception e) {
            Log.e(TAG, "Error loading album art", e);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        super.onStartCommand(intent, flags, startId);

        if (currentAudiobook != null && isPlaying() && playbackState == STATE_PLAYING) {
            Log.d(TAG, "Service restarted with existing playback - showing notification");
            startForeground(NOTIFICATION_ID, buildNotification());
        }
        startForeground(NOTIFICATION_ID, buildNotification());

        if (intent != null && intent.getAction() != null) {
            switch (intent.getAction()) {
                case "ACTION_PLAY":
                    play();
                    break;
                case "ACTION_PAUSE":
                    pause();
                    break;
                case "ACTION_NEXT":
                    nextTrack();
                    break;
                case "ACTION_PREVIOUS":
                    previousTrack();
                    break;
                case "ACTION_PLAYLIST":
                    showPlaylist();
                    break;
                case "ACTION_STOP":
                    stop();
                    break;
            }
        }

        return START_STICKY;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        super.onBind(intent);
        return binder;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        if (isPlaying()) {
            Log.d(TAG, "Activity unbound while playing - keeping notification visible");
            startForeground(NOTIFICATION_ID, buildNotification());
        }
        return super.onUnbind(intent);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        stopProgressUpdates();
        if (exoPlayer != null) {
            exoPlayer.release();
        }
        if (mediaSession != null) {
            mediaSession.release();
        }
    }
}