package com.nayanpote.Nayora;

import java.io.Serializable;

public class Song implements Serializable {
    private String title;
    private String artist;
    private int resourceId;
    private int albumArt;

    public Song() {
        // Default constructor for Gson
    }

    public Song(String title, String artist, int resourceId, int albumArt) {
        this.title = title;
        this.artist = artist;
        this.resourceId = resourceId;
        this.albumArt = albumArt;
    }

    // Getters
    public String getTitle() {
        return title != null ? title : "Unknown Title";
    }

    public String getArtist() {
        return artist != null ? artist : "Unknown Artist";
    }

    public int getResourceId() {
        return resourceId;
    }

    public int getAlbumArt() {
        return albumArt;
    }

    // Setters
    public void setTitle(String title) {
        this.title = title;
    }

    public void setArtist(String artist) {
        this.artist = artist;
    }

    public void setResourceId(int resourceId) {
        this.resourceId = resourceId;
    }

    public void setAlbumArt(int albumArt) {
        this.albumArt = albumArt;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        Song song = (Song) obj;
        return resourceId == song.resourceId;
    }

    @Override
    public int hashCode() {
        return resourceId;
    }

    @Override
    public String toString() {
        return "Song{" +
                "title='" + title + '\'' +
                ", artist='" + artist + '\'' +
                ", resourceId=" + resourceId +
                ", albumArt=" + albumArt +
                '}';
    }
}