package com.kiduyu.ebookstudio;

import android.Manifest;
import android.app.Dialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.kiduyu.ebookstudio.databinding.ActivityAudiobookPlayerBinding;
import com.kiduyu.item.Audiobook;
import com.kiduyu.item.Bookmark;
import com.kiduyu.service.AudiobookRepository;
import com.kiduyu.util.StatusBar;
import com.karumi.dexter.Dexter;
import com.karumi.dexter.PermissionToken;
import com.karumi.dexter.listener.PermissionDeniedResponse;
import com.karumi.dexter.listener.PermissionGrantedResponse;
import com.karumi.dexter.listener.PermissionRequest;
import com.karumi.dexter.listener.single.PermissionListener;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class AudiobookPlayerActivity extends AppCompatActivity implements
        AudiobookPlayerService.OnPlayerStateListener,
        AudiobookPlayerService.OnPlaylistListener {

    private static final String TAG = "AudiobookPlayerActivity";
    private static final int PERMISSION_REQUEST_CODE = 101;

    ActivityAudiobookPlayerBinding binding;
    private AudiobookPlayerService playerService;
    private boolean serviceBound = false;
    private boolean isFromSearch = false;
    private Audiobook currentAudiobook;

    private Handler seekHandler;
    private Runnable seekRunnable;

    private AudiobookRepository repository;
    private String audiobookUrl;

    private ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            AudiobookPlayerService.LocalBinder binder = (AudiobookPlayerService.LocalBinder) service;
            playerService = binder.getService();
            playerService.setOnPlayerStateListener(AudiobookPlayerActivity.this);
            playerService.setOnPlaylistListener(AudiobookPlayerActivity.this);

            serviceBound = true;

            // Check if service already has an audiobook playing
            Audiobook serviceAudiobook = playerService.getCurrentAudiobook();
            if (serviceAudiobook != null) {
                // Service already has an audiobook, use it
                currentAudiobook = serviceAudiobook;
                displayAudiobook(currentAudiobook);
                updateUI();
                Log.d(TAG, "Reconnected to existing playback");
            } else if (currentAudiobook != null) {
                // We have a new audiobook to play
                playerService.setAudiobook(currentAudiobook, isFromSearch);
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            serviceBound = false;
            playerService = null;
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        //StatusBar.init(AudiobookPlayerActivity.this);

        binding = ActivityAudiobookPlayerBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        repository = new AudiobookRepository();
        seekHandler = new Handler();
        //binding.progressBar.setVisibility(View.VISIBLE);


        // Get intent data
        audiobookUrl = getIntent().getStringExtra("AUDIOBOOK_URL");
        isFromSearch = getIntent().getBooleanExtra("FROM_SEARCH", false);

        if (audiobookUrl == null || audiobookUrl.isEmpty()) {
            Toast.makeText(this, "No audiobook URL provided", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        setupUI();
        setupClickListeners();
        setupSeekBar();

        // Don't load here - let onStart handle it to avoid duplicate loading
    }

    private void setupUI() {
        // Set dynamic background color based on artwork
        int bgColor = getColor(R.color.player_background);
        binding.playerRoot.setBackgroundColor(bgColor);

        // Set status bar
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            getWindow().setStatusBarColor(bgColor);
            getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        }
    }

    private void setupClickListeners() {
        // Back button
        binding.btnBack.setOnClickListener(v -> onBackPressed());

        // Play/Pause button
        binding.btnPlayPause.setOnClickListener(v -> {
            if (playerService != null) {
                if (playerService.isPlaying()) {
                    Log.i(TAG, "setupClickListeners: playing");
                    playerService.pause();
                } else {
                    Log.i(TAG, "setupClickListeners: not playing");
                    playerService.play();
                }
            }
        });

        // Previous button
        binding.btnPrevious.setOnClickListener(v -> {
            if (playerService != null) {
                playerService.previousTrack();
            }
        });

        // Next button
        binding.btnNext.setOnClickListener(v -> {
            if (playerService != null) {
                playerService.nextTrack();
            }
        });

        // Shuffle button
        binding.btnShuffle.setOnClickListener(v -> {
            if (playerService != null) {
                int currentMode = playerService.getPlayMode();
                if (currentMode == AudiobookPlayerService.MODE_SHUFFLE) {
                    playerService.setPlayMode(AudiobookPlayerService.MODE_NORMAL);
                    binding.btnShuffle.setAlpha(0.5f);
                } else {
                    playerService.setPlayMode(AudiobookPlayerService.MODE_SHUFFLE);
                    binding.btnShuffle.setAlpha(1.0f);
                }
            }
        });

        // Repeat button
        binding.btnRepeat.setOnClickListener(v -> {
            if (playerService != null) {
                int currentMode = playerService.getPlayMode();
                int newMode;
                float alpha;

                if (currentMode == AudiobookPlayerService.MODE_NORMAL) {
                    newMode = AudiobookPlayerService.MODE_REPEAT_ALL;
                    alpha = 1.0f;
                } else if (currentMode == AudiobookPlayerService.MODE_REPEAT_ALL) {
                    newMode = AudiobookPlayerService.MODE_REPEAT_ONE;
                    alpha = 1.0f;
                } else {
                    newMode = AudiobookPlayerService.MODE_NORMAL;
                    alpha = 0.5f;
                }

                playerService.setPlayMode(newMode);
                binding.btnRepeat.setAlpha(alpha);
            }
        });

        // Like button
        binding.btnLike.setOnClickListener(v -> {
            showBookmarksDialog();
        });

        // Playlist button
        binding.btnPlaylist.setOnClickListener(v -> {
            if (playerService != null) {
                playerService.showPlaylist();
            }
        });

        // Menu button
        binding.btnMenu.setOnClickListener(v -> {
            // Show options menu
            Toast.makeText(this, "Menu options", Toast.LENGTH_SHORT).show();
        });
    }

    private void setupSeekBar() {
        binding.seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser && playerService != null) {
                    playerService.seekTo(progress);
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                seekHandler.removeCallbacks(seekRunnable);
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                if (playerService != null) {
                    startProgressUpdates();
                }
            }
        });
    }

    private void loadAudiobookFromUrl(String url) {
        binding.progressBar.setVisibility(View.VISIBLE);

        repository.Audiobooks4soulDetails(url, new AudiobookRepository.DataCallback<Audiobook>() {
            @Override
            public void onSuccess(Audiobook result) {
                binding.progressBar.setVisibility(View.GONE);
                if (result != null) {
                    currentAudiobook = result;
                    displayAudiobook(result);

                    if (serviceBound && playerService != null) {
                        playerService.setAudiobook(result, true); // true because this is from audiobooks4soul
                        playerService.loadTrack(0);
                        playerService.play();
                    }
                } else {
                    Log.i(TAG, "onSuccess: Failed to load audiobook");
                    Toast.makeText(AudiobookPlayerActivity.this, "Failed to load audiobook", Toast.LENGTH_SHORT).show();
                    finish();
                }
            }

            @Override
            public void onError(Exception e) {
                binding.progressBar.setVisibility(View.GONE);
                Log.e(TAG, "Error loading audiobook", e);
                Toast.makeText(AudiobookPlayerActivity.this, "Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
                finish();
            }
        });
    }

    private void loadAudiobookDetails(String url) {
        binding.progressBar.setVisibility(View.VISIBLE);

        //https://samples.audible.com/bk/rhuk/008599/bk_rhuk_008599_sample.mp3

        if(url.contains("samples.audible.com")){
            String audiobook1 = getIntent().getStringExtra("AUDIBLE_AUDIOBOOK");
            getAudibleAudiobook(audiobook1);



        }else {

            repository.getAudiobookDetails(url, new AudiobookRepository.DataCallback<Audiobook>() {
                @Override
                public void onSuccess(Audiobook result) {
                    binding.progressBar.setVisibility(View.GONE);
                    if (result != null) {
                        currentAudiobook = result;
                        displayAudiobook(result);

                        if (serviceBound && playerService != null) {
                            playerService.setAudiobook(result, false); // false because this is a regular audiobook
                            playerService.loadTrack(0);
                            playerService.play();
                        }
                    } else {
                        Toast.makeText(AudiobookPlayerActivity.this, "Failed to load audiobook details", Toast.LENGTH_SHORT).show();
                        finish();
                    }
                }

                @Override
                public void onError(Exception e) {
                    binding.progressBar.setVisibility(View.GONE);
                    Log.e(TAG, "Error loading audiobook details", e);
                    Toast.makeText(AudiobookPlayerActivity.this, "Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    finish();
                }
            });
        }
    }

    private void getAudibleAudiobook(String audiobook1) {
        repository.getAudiobookAudibleSample(audiobook1, new AudiobookRepository.DataCallback<Audiobook>() {
            @Override
            public void onSuccess(Audiobook data) {
                if (data != null) {
                    binding.progressBar.setVisibility(View.GONE);
                    currentAudiobook = data;
                    displayAudiobook(data);
                    if (serviceBound && playerService != null) {
                        playerService.setAudiobook(data, false); // false because this is a regular audiobook
                        playerService.loadTrack(0);
                        playerService.play();
                    }}

            }

            @Override
            public void onError(Exception e) {

            }
        });

    }


    private void displayAudiobook(Audiobook audiobook) {
        // Title
        binding.tvTitle.setText(audiobook.getTitle() != null ? audiobook.getTitle() : "Unknown Title");

        // Author
        binding.tvAuthor.setText(audiobook.getAuthor() != null ? audiobook.getAuthor() : "Unknown Author");

        // Track info
        if (audiobook.getTrackNames() != null && !audiobook.getTrackNames().isEmpty()) {
            binding.tvTrackInfo.setText(audiobook.getTrackNames().get(0));
        }

        // Album art
        if (audiobook.getImageUrl() != null && !audiobook.getImageUrl().isEmpty()) {
            Log.d(TAG,"Album art: "+audiobook.getImageUrl());
            Glide.with(this)
                    .load(audiobook.getImageUrl())
                    .placeholder(R.drawable.placeholder_portable)
                    .into(binding.ivAlbumArt);
        }

        // Description
        if (audiobook.getDescription() != null && !audiobook.getDescription().isEmpty()) {
            binding.tvDescription.setText(audiobook.getDescription());
            binding.tvDescription.setVisibility(View.VISIBLE);
        } else {
            binding.tvDescription.setVisibility(View.GONE);
        }

        // Track count
        int trackCount = audiobook.getAudioUrls() != null ? audiobook.getAudioUrls().size() : 0;
        binding.tvTrackCount.setText(trackCount + " tracks");
    }

    private void updateUI() {
        if (playerService == null) return;

        // Update play/pause button
        updatePlayPauseButton();

        // Update seek bar
        binding.seekBar.setMax(playerService.getDuration());
        binding.seekBar.setProgress(playerService.getCurrentPosition());

        // Update time labels
        binding.tvCurrentTime.setText(formatTime(playerService.getCurrentPosition()));
        binding.tvTotalTime.setText(formatTime(playerService.getDuration()));

        // Update track info
        binding.tvTrackInfo.setText(playerService.getCurrentTrackName());

        // Update shuffle/repeat states
        int mode = playerService.getPlayMode();
        binding.btnShuffle.setAlpha(mode == AudiobookPlayerService.MODE_SHUFFLE ? 1.0f : 0.5f);
        binding.btnRepeat.setAlpha(mode == AudiobookPlayerService.MODE_NORMAL ? 0.5f : 1.0f);
    }

    private void updatePlayPauseButton() {
        if (playerService == null) return;

        if (playerService.isPlaying()) {
            binding.btnPlayPause.setImageResource(android.R.drawable.ic_media_pause);
        } else {
            binding.btnPlayPause.setImageResource(android.R.drawable.ic_media_play);
        }
    }

    private void startProgressUpdates() {
        seekRunnable = new Runnable() {
            @Override
            public void run() {
                if (playerService != null && playerService.isPlaying()) {
                    binding.seekBar.setProgress(playerService.getCurrentPosition());
                    binding.tvCurrentTime.setText(formatTime(playerService.getCurrentPosition()));
                    seekHandler.postDelayed(this, 1000);
                }
            }
        };
        seekHandler.post(seekRunnable);
    }

    private String formatTime(int milliseconds) {
        long seconds = TimeUnit.MILLISECONDS.toSeconds(milliseconds);
        long minutes = seconds / 60;
        seconds = seconds % 60;
        return String.format("%d:%02d", minutes, seconds);
    }

    private void showPlaylistDialog(List<String> trackNames, int currentIndex) {
        if (trackNames == null || trackNames.isEmpty()) return;

        Dialog dialog = new Dialog(this, R.style.Theme_AppCompat_Translucent);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_playlist);
        dialog.setCancelable(true);

        if (getWindow() != null) {
            getWindow().setLayout(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.MATCH_PARENT
            );
            getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        RecyclerView recyclerView = dialog.findViewById(R.id.rvPlaylist);
        TextView tvTitle = dialog.findViewById(R.id.tvPlaylistTitle);
        ImageView ivClose = dialog.findViewById(R.id.ivClose);

        tvTitle.setText("Up Next - " + trackNames.size() + " tracks");

        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        PlaylistAdapter adapter = new PlaylistAdapter(trackNames, currentIndex, (position, trackName) -> {
            if (playerService != null) {
                playerService.loadTrack(position);
                if (!playerService.isPlaying()) {
                    playerService.play();
                }
            }
            dialog.dismiss();
        });

        recyclerView.setAdapter(adapter);

        ivClose.setOnClickListener(v -> dialog.dismiss());
        dialog.show();
    }

    private void showBookmarksDialog() {
        if (currentAudiobook == null) {
            Toast.makeText(this, "No audiobook loaded", Toast.LENGTH_SHORT).show();
            return;
        }

        Dialog dialog = new Dialog(this, R.style.Theme_AppCompat_Translucent);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_bookmarks);
        dialog.setCancelable(true);

        if (getWindow() != null) {
            getWindow().setLayout(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.MATCH_PARENT
            );
            getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        RecyclerView recyclerView = dialog.findViewById(R.id.rvBookmarks);
        ImageView ivClose = dialog.findViewById(R.id.ivClose);
        ImageView ivAddBookmark = dialog.findViewById(R.id.btnAddBookmark);

        List<Bookmark> bookmarks = currentAudiobook.getBookmarks();
        if (bookmarks == null) {
            bookmarks = new ArrayList<>();
        }

        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        BookmarkAdapter adapter = new BookmarkAdapter(bookmarks, (bookmark) -> {
            if (playerService != null) {
                // Seek to the bookmark's track and position
                playerService.loadTrack(bookmark.getTrackIndex());
                playerService.seekTo(bookmark.getTrackPosition());
                if (!playerService.isPlaying()) {
                    playerService.play();
                }
            }
            dialog.dismiss();
        });

        recyclerView.setAdapter(adapter);

        ivClose.setOnClickListener(v -> dialog.dismiss());

        ivAddBookmark.setOnClickListener(v -> {
            dialog.dismiss();
            showAddBookmarkDialog();
        });

        dialog.show();
    }

    private void showAddBookmarkDialog() {
        if (currentAudiobook == null || playerService == null) {
            Toast.makeText(this, "No audiobook loaded", Toast.LENGTH_SHORT).show();
            return;
        }

        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_add_bookmark);
        dialog.setCancelable(true);

        // Set dialog window properties
        Window dialogWindow = dialog.getWindow();
        if (dialogWindow != null) {
            dialogWindow.setLayout(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            );
            dialogWindow.setBackgroundDrawableResource(android.R.color.transparent);
            // Optionally set the background drawable if you have a custom one:
            // dialogWindow.setBackgroundDrawableResource(R.drawable.bg_dialog_rounded);
        }

        EditText etBookmarkName = dialog.findViewById(R.id.etBookmarkName);
        TextView tvCurrentTrack = dialog.findViewById(R.id.tvCurrentTrack);
        Button btnCancel = dialog.findViewById(R.id.btnCancel);
        Button btnSave = dialog.findViewById(R.id.btnSave);

        int currentTrackIndex = playerService.getCurrentTrackIndex();
        String currentTrackName = playerService.getCurrentTrackName();
        int currentPosition = playerService.getCurrentPosition();

        tvCurrentTrack.setText("Track: " + (currentTrackIndex + 1) + " - " + currentTrackName);
        etBookmarkName.setHint("Bookmark name");

        btnCancel.setOnClickListener(v -> dialog.dismiss());

        btnSave.setOnClickListener(v -> {
            String name = etBookmarkName.getText().toString().trim();
            if (name.isEmpty()) {
                Toast.makeText(this, "Please enter a bookmark name", Toast.LENGTH_SHORT).show();
                return;
            }

            // Create new bookmark
            Bookmark bookmark = new Bookmark();
            bookmark.setName(name);
            bookmark.setTrackIndex(currentTrackIndex);
            bookmark.setTrackPosition(currentPosition);

            // Add to audiobook's bookmarks list
            if (currentAudiobook.getBookmarks() == null) {
                currentAudiobook.setBookmarks(new ArrayList<>());
            }
            currentAudiobook.getBookmarks().add(bookmark);

            Toast.makeText(this, "Bookmark added: " + name, Toast.LENGTH_SHORT).show();
            dialog.dismiss();
        });

        dialog.show();
    }

    @Override
    public void onPlaybackStateChanged(int state) {
        runOnUiThread(() -> {
            updatePlayPauseButton();
            if (state == AudiobookPlayerService.STATE_PLAYING) {
                startProgressUpdates();
            } else {
                seekHandler.removeCallbacks(seekRunnable);
            }
        });
    }

    @Override
    public void onProgressChanged(int currentPosition, int duration) {
        runOnUiThread(() -> {
            binding.seekBar.setMax(duration);
            binding.seekBar.setProgress(currentPosition);
            binding.tvCurrentTime.setText(formatTime(currentPosition));
            binding.tvTotalTime.setText(formatTime(duration));
        });
    }

    @Override
    public void onTrackChanged(int index, String trackName) {
        runOnUiThread(() -> {
            binding.tvTrackInfo.setText(trackName);
        });
    }

    @Override
    public void onError(String error) {
        runOnUiThread(() -> {
            Toast.makeText(this, error, Toast.LENGTH_LONG).show();
        });
    }

    @Override
    public void onPlaylistRequested(List<String> trackNames, int currentIndex) {
        runOnUiThread(() -> {
            showPlaylistDialog(trackNames, currentIndex);
        });
    }

    @Override
    protected void onStart() {
        super.onStart();
        bindAudiobookService();

        // Only load if we don't have a current audiobook
        // This prevents duplicate loading when the activity reconnects to an existing service
        if (audiobookUrl != null && currentAudiobook == null) {
            if (isFromSearch) {
                loadAudiobookFromUrl(audiobookUrl);
            } else {
                loadAudiobookDetails(audiobookUrl);
            }
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        unbindAudiobookService();
        // Don't reload here - the service should continue playing in background
        // with the notification remaining visible
    }

    private void bindAudiobookService() {
        Intent intent = new Intent(this, AudiobookPlayerService.class);
        bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE);
    }

    private void unbindAudiobookService() {
        if (serviceBound) {
            unbindService(serviceConnection);
            serviceBound = false;
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        seekHandler.removeCallbacksAndMessages(null);
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
    }

    /**
     * Playlist Adapter
     */
    private static class PlaylistAdapter extends RecyclerView.Adapter<PlaylistAdapter.ViewHolder> {
        private List<String> trackNames;
        private int currentIndex;
        private OnTrackClickListener listener;

        interface OnTrackClickListener {
            void onTrackClick(int position, String trackName);
        }

        PlaylistAdapter(List<String> trackNames, int currentIndex, OnTrackClickListener listener) {
            this.trackNames = trackNames != null ? trackNames : new ArrayList<>();
            this.currentIndex = currentIndex;
            this.listener = listener;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull android.view.ViewGroup parent, int viewType) {
            View view = android.view.LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_playlist_track, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            String trackName = trackNames.get(position);
            holder.tvTrackName.setText(trackName);
            holder.tvTrackNumber.setText(String.valueOf(position + 1));

            boolean isPlaying = position == currentIndex;
            holder.itemView.setBackgroundColor(isPlaying ?
                    ContextCompat.getColor(holder.itemView.getContext(), R.color.playlist_highlight) :
                    android.graphics.Color.TRANSPARENT);
            holder.tvTrackName.setTextColor(isPlaying ?
                    ContextCompat.getColor(holder.itemView.getContext(), R.color.cyan_accent) :
                    ContextCompat.getColor(holder.itemView.getContext(), R.color.white));

            holder.itemView.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onTrackClick(position, trackName);
                }
            });
        }

        @Override
        public int getItemCount() {
            return trackNames.size();
        }

        static class ViewHolder extends RecyclerView.ViewHolder {
            TextView tvTrackNumber;
            TextView tvTrackName;

            ViewHolder(@NonNull View itemView) {
                super(itemView);
                tvTrackNumber = itemView.findViewById(R.id.tvTrackNumber);
                tvTrackName = itemView.findViewById(R.id.tvTrackName);
            }
        }
    }

    /**
     * Bookmark Adapter
     */
    private static class BookmarkAdapter extends RecyclerView.Adapter<BookmarkAdapter.ViewHolder> {
        private List<Bookmark> bookmarks;
        private OnBookmarkClickListener listener;

        interface OnBookmarkClickListener {
            void onBookmarkClick(Bookmark bookmark);
        }

        BookmarkAdapter(List<Bookmark> bookmarks, OnBookmarkClickListener listener) {
            this.bookmarks = bookmarks != null ? bookmarks : new ArrayList<>();
            this.listener = listener;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull android.view.ViewGroup parent, int viewType) {
            View view = android.view.LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_bookmark, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            Bookmark bookmark = bookmarks.get(position);
            holder.tvBookmarkName.setText(bookmark.getName());

            // Format track index and position
            String trackInfo = "Track " + (bookmark.getTrackIndex() + 1) + " - " +
                    formatTime(bookmark.getTrackPosition());
            holder.tvBookmarkTime.setText(trackInfo);

            holder.itemView.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onBookmarkClick(bookmark);
                }
            });
        }

        @Override
        public int getItemCount() {
            return bookmarks.size();
        }

        private static String formatTime(int milliseconds) {
            long seconds = TimeUnit.MILLISECONDS.toSeconds(milliseconds);
            long minutes = seconds / 60;
            seconds = seconds % 60;
            return String.format("%d:%02d", minutes, seconds);
        }

        static class ViewHolder extends RecyclerView.ViewHolder {
            TextView tvBookmarkName;
            TextView tvBookmarkTime;

            ViewHolder(@NonNull View itemView) {
                super(itemView);
                tvBookmarkName = itemView.findViewById(R.id.tvBookmarkName);
                tvBookmarkTime = itemView.findViewById(R.id.tvBookmarkInfo);
            }
        }
    }
}
