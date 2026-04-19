package com.kiduyu.service;

import android.util.Log;

import com.kiduyu.item.Audiobook;

import java.util.ArrayList;
import java.util.List;

public class AudiobookRepository {
    private static final String TAG = "AudiobookRepository";

    private final WebDataSource webDataSource;

    public AudiobookRepository() {
        this.webDataSource = new WebDataSource();
    }

    /**
     * Callback interface for async operations
     */
    public interface DataCallback<T> {
        void onSuccess(T data);
        void onError(Exception e);
    }

    public void searchAudiobooks(String trimmedQuery, AudiobookRepository.DataCallback<List<Audiobook>> dataCallback) {
        webDataSource.getSearchResultsAudiobooks(trimmedQuery, new WebDataSource.Callback<List<Audiobook>>() {
            @Override
            public void onSuccess(List<Audiobook> result) {
                if (result != null) {
                    dataCallback.onSuccess(result);
                } else {
                    dataCallback.onSuccess(new ArrayList<>());
                }
            }

            @Override
            public void onError(Exception e) {
                Log.e(TAG, "Error searching audiobooks", e);
                dataCallback.onError(e);
            }
        });
    }

    /**
     * Get audiobook details
     */
    public void getAudiobookDetails(String url, DataCallback<Audiobook> callback) {
        webDataSource.getAudiobookDetails(url, new WebDataSource.Callback<Audiobook>() {
            @Override
            public void onSuccess(Audiobook result) {
                callback.onSuccess(result);
            }

            @Override
            public void onError(Exception e) {
                Log.e(TAG, "Error getting audiobook details", e);
                callback.onError(e);
            }
        });
    }

    /**
     * Get audiobook details from audiobooks4soul.com
     * This method handles the custom encryption used by the custom-story-audio plugin
     * and returns the audiobook with decrypted playlist URLs.
     */
    public void Audiobooks4soulDetails(String url, DataCallback<Audiobook> callback) {
        webDataSource.getAudiobooks4soulDetails(url, new WebDataSource.Callback<Audiobook>() {
            @Override
            public void onSuccess(Audiobook result) {
                callback.onSuccess(result);
            }

            @Override
            public void onError(Exception e) {
                Log.e(TAG, "Error getting audiobooks4soul details", e);
                callback.onError(e);
            }
        });
    }

    /**
     * Get audiobook sample from Audible
     */
    public void getAudiobookAudibleSample(String searchQuery, DataCallback<Audiobook> callback) {
        webDataSource.getAudiobookAudibleSample(searchQuery, new WebDataSource.Callback<Audiobook>() {
            @Override
            public void onSuccess(Audiobook result) {
                callback.onSuccess(result);
            }

            @Override
            public void onError(Exception e) {
                Log.e(TAG, "Error getting audiobook Audible sample", e);
                callback.onError(e);
            }
        });
    }
}
