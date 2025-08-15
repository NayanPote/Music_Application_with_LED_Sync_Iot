package com.nayanpote.Nayora;

import android.animation.ObjectAnimator;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateDecelerateInterpolator;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.nayanpote.musicalledsbynayan.R;
import com.nayanpote.musicalledsbynayan.databinding.ItemSongBinding;
import java.util.List;

/**
 * RecyclerView Adapter for Songs
 * Handles the display of songs in a playlist
 */
public class SongAdapter extends RecyclerView.Adapter<SongAdapter.SongViewHolder> {
    private List<Song> songs;
    private int currentPlayingIndex = -1;
    private OnSongClickListener onSongClickListener;
    private List<Song> originalPlaylist;
    // Interface for song click events
    public interface OnSongClickListener {
        void onSongClick(int position, boolean isCurrentPlaying);
    }

    public interface OnDragStartListener {
        void onDragStart(RecyclerView.ViewHolder viewHolder);
    }

    private OnDragStartListener dragStartListener;

    public void setOnDragStartListener(OnDragStartListener listener) {
        this.dragStartListener = listener;
    }

    public SongAdapter(List<Song> songs) {
        this.songs = songs;
    }

    public void setOriginalPlaylist(List<Song> originalPlaylist) {
        this.originalPlaylist = originalPlaylist;
    }

    public void setOnSongClickListener(OnSongClickListener listener) {
        this.onSongClickListener = listener;
    }

    public void setCurrentPlaying(int index) {
        int oldIndex = currentPlayingIndex;
        currentPlayingIndex = index;
        if (oldIndex != -1) notifyItemChanged(oldIndex);
        if (currentPlayingIndex != -1) notifyItemChanged(currentPlayingIndex);
    }

    public int getCurrentPlayingIndex() {
        return currentPlayingIndex;
    }

    @NonNull
    @Override
    public SongViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemSongBinding binding = ItemSongBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new SongViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull SongViewHolder holder, int position) {
        Song song = songs.get(position);
        boolean isCurrentPlaying = (position == currentPlayingIndex);
        holder.bind(song, isCurrentPlaying);

        // Regular click listener
        holder.itemView.setOnClickListener(v -> {
            animateButton(holder.itemView);
            if (onSongClickListener != null) {
                onSongClickListener.onSongClick(position, isCurrentPlaying);
            }
        });

        // Long press listener for drag handle
        holder.binding.adjustposition.setOnLongClickListener(v -> {
            if (dragStartListener != null) {
                dragStartListener.onDragStart(holder);
            }
            return true;
        });
    }

    @Override
    public int getItemCount() {
        return songs != null ? songs.size() : 0;
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

    /**
     * ViewHolder class for Song items
     */
    public static class SongViewHolder extends RecyclerView.ViewHolder {
        private final ItemSongBinding binding;

        public SongViewHolder(@NonNull ItemSongBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        public void bind(Song song, boolean isPlaying) {
            binding.songTitle.setText(song.getTitle());
            binding.songArtist.setText(song.getArtist());
            binding.songAlbumArt.setImageResource(song.getAlbumArt());

            binding.playIndicator.setVisibility(isPlaying ? View.VISIBLE : View.GONE);

            binding.songContainer.setBackgroundResource(isPlaying ?
                    R.drawable.song_item_selected : R.drawable.song_item_background);
        }
    }

    // Method to update the songs list
    public void updateSongs(List<Song> newSongs) {
        this.songs = newSongs;
        notifyDataSetChanged();
    }

    // Method to get song at specific position
    public Song getSong(int position) {
        if (songs != null && position >= 0 && position < songs.size()) {
            return songs.get(position);
        }
        return null;
    }

    public int getOriginalPosition(int filteredPosition) {
        if (songs == null || filteredPosition >= songs.size()) return -1;
        Song clickedSong = songs.get(filteredPosition);

        // Find the position in original playlist
        for (int i = 0; i < originalPlaylist.size(); i++) {
            if (originalPlaylist.get(i).getTitle().equals(clickedSong.getTitle()) &&
                    originalPlaylist.get(i).getArtist().equals(clickedSong.getArtist())) {
                return i;
            }
        }
        return -1;
    }
}