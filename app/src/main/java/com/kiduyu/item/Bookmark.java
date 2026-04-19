package com.kiduyu.item;

import java.io.Serializable;

/**
 * Bookmark model class for audiobook playback positions
 */
public class Bookmark implements Serializable {
    private String id;
    private String name;
    private int trackIndex;
    private int trackPosition;
    private long createdAt;

    public Bookmark() {
        this.createdAt = System.currentTimeMillis();
    }

    public Bookmark(String name, int trackIndex, int trackPosition) {
        this();
        this.name = name;
        this.trackIndex = trackIndex;
        this.trackPosition = trackPosition;
        this.id = java.util.UUID.randomUUID().toString();
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getTrackIndex() {
        return trackIndex;
    }

    public void setTrackIndex(int trackIndex) {
        this.trackIndex = trackIndex;
    }

    public int getTrackPosition() {
        return trackPosition;
    }

    public void setTrackPosition(int trackPosition) {
        this.trackPosition = trackPosition;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt;
    }

    /**
     * Format position as readable time (MM:SS)
     */
    public String getFormattedPosition() {
        int seconds = trackPosition / 1000;
        int minutes = seconds / 60;
        seconds = seconds % 60;
        return String.format("%d:%02d", minutes, seconds);
    }

    /**
     * Format track info as "Track X"
     */
    public String getFormattedTrack() {
        return "Track " + (trackIndex + 1);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Bookmark bookmark = (Bookmark) o;
        return id != null && id.equals(bookmark.id);
    }

    @Override
    public int hashCode() {
        return id != null ? id.hashCode() : 0;
    }

    @Override
    public String toString() {
        return "Bookmark{" +
                "name='" + name + '\'' +
                ", trackIndex=" + trackIndex +
                ", trackPosition=" + trackPosition +
                '}';
    }
}
