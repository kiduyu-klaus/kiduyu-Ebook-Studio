package com.kiduyu.service;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.kiduyu.item.Audiobook;

import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class WebDataSource {
    private static final String TAG = "WebDataSource";
    private static final String BASE_URL = "https://goldenaudiobook.net/";
    private static final String AUDIOBOOKS4SOUL_BASE_URL = "https://audiobooks4soul.com";
    private static final String MAPPLE_BASE_URL = "https://audiobooks4soul.com";
    private static final String MAPPLE_BASE_URL_SEARCH = "https://mapple.mov/audiobooks?search=";
    private static final int TIMEOUT = 30000;
    private final ExecutorService executor;
    private final Handler mainHandler;

    public WebDataSource() {
        this.executor = Executors.newSingleThreadExecutor();
        this.mainHandler = new Handler(Looper.getMainLooper());
    }

    /**
     * Callback interface for async operations
     */
    public interface Callback<T> {
        void onSuccess(T result);

        void onError(Exception e);
    }

    /**
     * Search for audiobooks by query
     * URL format: https://goldenaudiobook.net/?s=search+term
     */
    public void getSearchResultsAudiobooks(String searchQuery, Callback<List<Audiobook>> callback) {
        executor.execute(() -> {
            try {

                List<Audiobook> audiobooks = new ArrayList<>();

                // Construct search URL - replace spaces with +
                String encodedQuery = searchQuery.replace(" ", "+");
                String searchUrl = BASE_URL + "?s=" + encodedQuery;

                Log.d(TAG, "Searching for: " + searchQuery + " at URL: " + searchUrl);

                Document doc = Jsoup.connect(searchUrl)
                        .timeout(TIMEOUT)
                        .userAgent("Mozilla/5.0 (Linux; Android 10; SM-G975F) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.101 Mobile Safari/537.36")
                        .get();

                // Parse posts from search results - use the structure from search results
                Elements posts = doc.select("li.ilovewp-post");
                if (posts.isEmpty()) {
                    posts = doc.select("article[id^=post-]");
                }
                if (posts.isEmpty()) {
                    posts = doc.select("article.post");
                }
                if (posts.isEmpty()) {
                    posts = doc.select(".search-result, .result-item");
                }

                Log.d(TAG, "Search found " + posts.size() + " posts for query: " + searchQuery);
                String[] querySplit = searchQuery.split("by");
                String searchbookTitle = querySplit[0].trim();
                String searchauthor = querySplit[1].trim();

                for (Element post : posts) {
                    Element titleElement = post.selectFirst("h2.title-post a");
                    if (titleElement == null) {
                        // Try alternate selectors for search results
                        titleElement = post.selectFirst(".entry-title a, .post-title a, h2 a");
                    }
                    Log.d(TAG, "titleElement: " + titleElement);
                    if (titleElement != null) {
                        // example title: Lee Child – Blue Moon (Jack Reacher 24) Audiobook
                        String title = titleElement.text().trim().replace("Audiobook", "");
                        if (title != null && !title.isEmpty()) {
                            Log.d(TAG, "Search result title: " + title);
                            //Log.d(TAG, "Search result title: " + title);
                            if (title.contains("–")) {
                                Log.d(TAG, "contains –: ");
                                String[] parts = title.split("–");

                                String bookTitle = parts[1].trim();
                                Log.d(TAG, "getSearchResultsAudiobooks bookTitle: " + bookTitle);
                                String author = parts[0].trim();
                                Log.d(TAG, "getSearchResultsAudiobooks author: " + author);


                                // Remove parentheses and everything inside from bookTitle
                                if (bookTitle.contains("(") && bookTitle.contains(")")) {
                                    bookTitle = bookTitle.replaceAll("\\s*\\([^)]*\\)", "").trim();
                                }

                                // Compare searchauthor with author and searchbookTitle with bookTitle
                                Log.d(TAG, "Comparing: '" + bookTitle + "' with '" + searchbookTitle + "'");
                                Log.d(TAG, "Comparing: '" + author + "' with '" + searchauthor + "'");
                                if (author.equalsIgnoreCase(searchauthor) &&
                                        bookTitle.equalsIgnoreCase(searchbookTitle)) {
                                    Log.d(TAG, "Found matching book: " + title);
                                    Audiobook audiobook = parseSearchPost(post);
                                    if (audiobook != null && audiobook.getTitle() != null && !audiobook.getTitle().isEmpty()) {
                                        audiobooks.add(audiobook);
                                    }
                                    break;
                                }


                            }
                        }
                    }

//                    Audiobook audiobook = parseSearchPost(post);
//                    if (audiobook != null && audiobook.getTitle() != null && !audiobook.getTitle().isEmpty()) {
//                        audiobooks.add(audiobook);
//                    }
                }

                // If no results found, try alternate selectors
                if (audiobooks.isEmpty()) {
                    Log.d(TAG, "No results found with primary selector, trying alternatives");
                    //String searchQueryurl = parseFromAlternateSelectors(searchQuery);
                    //String searchQueryurl = "https://audiobooks4soul.com/the-way-of-kings-audiobook-v1/";
                    Audiobook audiobook = parseFromAlternateSelectors(searchQuery);
                    if (audiobook != null && audiobook.getTitle() != null && !audiobook.getTitle().isEmpty()) {
                        audiobooks.add(audiobook);
                    }
                    //getAudiobooks4soulDetails(searchQueryurl, new Callback<Audiobook>())

                }

                List<Audiobook> finalAudiobooks = audiobooks;
                mainHandler.post(() -> callback.onSuccess(finalAudiobooks));
            } catch (IOException e) {
                Log.e(TAG, "Error searching for audiobooks: " + searchQuery, e);
                mainHandler.post(() -> callback.onError(e));
            }
        });
    }


    private Audiobook parseSearchPost(Element post) {

        Audiobook audiobook = new Audiobook();
        try {
            // Parse title from h2.title-post a (same as category structure)
            Element titleElement = post.selectFirst("h2.title-post a");
            if (titleElement == null) {
                // Try alternate selectors for search results
                titleElement = post.selectFirst(".entry-title a, .post-title a, h2 a");
            }

            if (titleElement != null) {
                String title = titleElement.text().trim().replace("Audiobook", "");
                String url = titleElement.attr("href");
                audiobook.setTitle(title);
                audiobook.setUrl(url);
                Log.d(TAG, "Search result title: " + title);
            }

            // Parse image from .post-cover img or img attachment
            Element imgElement = post.selectFirst("img");
            if (imgElement != null) {
                String imageUrl = imgElement.attr("data-src");
                if (imageUrl.isEmpty()) {

                    imageUrl = imgElement.attr("src");
                }
                if (imageUrl.contains("SL500")) {
                    Log.i(TAG, "contains SL500: ");
                    audiobook.setImageUrl(removeDimensions(imageUrl));
                } else {
                    audiobook.setImageUrl(removeDimensions(imageUrl));
                    Log.d(TAG, "Search result image: " + removeDimensions(imageUrl));
                }
            }

            // Parse categories from .post-meta-category a
            Elements categoryElements = post.select(".post-meta-category a[rel=category tag]");
            if (!categoryElements.isEmpty()) {
                for (Element cat : categoryElements) {
                    audiobook.addCategory(cat.text());
                }
            } else {
                audiobook.addCategory("Search Result");
            }

            // Parse date from time.entry-date
            Element dateElement = post.selectFirst("time.entry-date");
            if (dateElement == null) {
                dateElement = post.selectFirst(".posted-on time, .entry-date");
            }

            if (dateElement != null) {
                String date = dateElement.text().trim();
                audiobook.setPublishedDate(date);
            }

            // Parse author from title if present (format: "Author – Book Title")
            if (audiobook.getTitle() != null && audiobook.getTitle().contains("–")) {
                String[] parts = audiobook.getTitle().split("–");
                if (parts.length > 1) {
                    audiobook.setAuthor(parts[0].trim());
                }
            }

            Log.d(TAG, "Parsed search result: " + audiobook.getTitle());
        } catch (Exception e) {
            Log.e(TAG, "Error parsing search result post", e);
        }
        return audiobook;
    }

    private Audiobook parseFromAlternateSelectors(String searchQuery) {
        Audiobook audiobook = new Audiobook();
        try {
            // Split query to extract book title and author
            Log.d(TAG, "Search query: " + searchQuery);
            if (!searchQuery.contains("by")) {
                Log.d(TAG, "Search query doesn't contain 'by' keyword");
                return new Audiobook();
            }

            String[] querySplit = searchQuery.split("by");
            if (querySplit.length < 2) { // Should have at least 2 parts
                Log.d(TAG, "Invalid query format");
                return new Audiobook();
            }

            String bookTitle = querySplit[0].trim();
            String author = querySplit[1].trim();

            Log.d(TAG, "Searching for book: " + bookTitle + " by author: " + author);

            // Fetch the author directory page
            String authorDirUrl = "https://audiobooks4soul.com/story-author/";
            Document doc = Jsoup.connect(authorDirUrl)
                    .timeout(TIMEOUT)
                    .userAgent("Mozilla/5.0 (Linux; Android 10; SM-G975F) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.101 Mobile Safari/537.36")
                    .get();

            // Get all author list items (data-name from A to Z)
            Elements authorItems = doc.select("li[data-name]");

            if (authorItems.isEmpty()) {
                Log.d(TAG, "No author items found on page");
                return null;
            }

            Log.d(TAG, "Found " + authorItems.size() + " author entries");

            // Search for matching author
            String authorUrl = null;
            for (Element item : authorItems) {
                Element authorLink = item.selectFirst("a");
                if (authorLink != null) {
                    String authorName = authorLink.text().trim();

                    // Case-insensitive comparison
                    if (formatAuthor(authorName).equalsIgnoreCase(formatAuthor(author)) ||
                            authorName.toLowerCase().contains(author.toLowerCase()) ||
                            author.toLowerCase().contains(authorName.toLowerCase())) {

                        authorUrl = authorLink.attr("href");
                        Log.d(TAG, "Found matching author: " + authorName + " at URL: " + authorUrl);
                        break;
                    }
                }
            }

            if (authorUrl == null) {
                Log.d(TAG, "No matching author found for: " + author);
                return null;
            }

            // Now fetch the author's page to find books
            Document authorPage = Jsoup.connect(authorUrl)
                    .timeout(TIMEOUT)
                    .userAgent("Mozilla/5.0 (Linux; Android 10; SM-G975F) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.101 Mobile Safari/537.36")
                    .get();

            // Look for books using the specific structure: div.tdb_module_loop.td_module_wrap
            Elements bookModules = authorPage.select("div.tdb_module_loop.td_module_wrap.td-animation-stack.td-cpt-post");

            if (bookModules.isEmpty()) {
                // Fallback to just the main classes
                bookModules = authorPage.select("div.tdb_module_loop.td_module_wrap");
            }

            if (bookModules.isEmpty()) {
                // Try even simpler selector
                bookModules = authorPage.select("div.td_module_wrap");
            }

            Log.d(TAG, "Found " + bookModules.size() + " book modules on author page");

            // Search for matching book title
            for (Element bookModule : bookModules) {
                // Get the title from h3.entry-title a
                Element titleElement = bookModule.selectFirst("h3.entry-title.td-module-title a");

                if (titleElement != null) {
                    String bookTitleText = titleElement.text().trim();
                    String bookUrl = titleElement.attr("href");

                    // Remove "audiobook" and "Audiobook" from comparison for better matching
                    String cleanBookTitle = bookTitleText.toLowerCase()
                            .replace("audiobook", "")
                            .replace("–", "")
                            .replace("-", "")
                            .trim();

                    String cleanSearchTitle = bookTitle.toLowerCase()
                            .replace("audiobook", "")
                            .replace("–", "")
                            .replace("-", "")
                            .trim();

                    // 3. Extract category
                    Element categoryElement = bookModule.selectFirst(".td-editor-date a.td-post-category");
                    Element imageElement = bookModule.selectFirst(".td-module-thumb a .entry-thumb");


                    Log.d(TAG, "Comparing: '" + cleanSearchTitle + "' with '" + cleanBookTitle + "'");

                    // Case-insensitive comparison with cleaned titles
                    if (cleanBookTitle.contains(cleanSearchTitle) ||
                            cleanSearchTitle.contains(cleanBookTitle)) {
                        audiobook.setTitle(bookTitleText);
                        audiobook.setUrl(bookUrl);
                        if (categoryElement != null) {
                            audiobook.addCategory(categoryElement.text().trim());
                        }
                        if (imageElement != null) {
                            // Try data-bg attribute first (lazy loaded)
                            String imageUrl = imageElement.attr("data-bg");
                            if (imageUrl.isEmpty()) {
                                // Fallback to style background-image
                                String style = imageElement.attr("style");
                                if (style.contains("url(")) {
                                    imageUrl = style.substring(
                                            style.indexOf("url(") + 5,
                                            style.lastIndexOf(")")
                                    ).replace("&quot;", "").replace("\"", "");
                                }
                            }
                            audiobook.setImageUrl(imageUrl);
                        }

                        audiobook.setDetailUrl(titleElement.attr("href"));


                        Log.d(TAG, "Found matching book: " + bookTitleText + " at URL: " + bookUrl);
                        return audiobook;
                    }
                }
            }

            Log.d(TAG, "No matching book found for title: " + bookTitle);
            return new Audiobook(); // Return author URL as fallback

        } catch (IOException e) {
            Log.e(TAG, "Error in parseFromAlternateSelectors", e);
            return null;
        } catch (Exception e) {
            Log.e(TAG, "Unexpected error in parseFromAlternateSelectors", e);
            return null;
        }
    }

    public static String formatAuthor(String author) {
        if (author == null || author.isBlank()) {
            return author;
        }

        // Normalize all whitespace to single spaces
        author = author.trim().replaceAll("\\s+", " ");

        String[] parts = author.split(" ");
        StringBuilder result = new StringBuilder();

        for (String part : parts) {

            // Match blocks like A., B.T., J.R.R.
            if (part.matches("([A-Z]\\.)+")) {
                for (int i = 0; i < part.length(); i += 2) {
                    result.append(part.charAt(i)).append(". ");
                }
            } else {
                result.append(part).append(" ");
            }
        }

        return result.toString().trim();
    }

    public static String removeDimensions(String url) {
        return url.replaceAll("-(\\d+)x(\\d+)(?=\\.[^.]+$)", "");
    }

    /**
     * Get the highest resolution image URL from an element, handling srcset if present
     */
    private String getHighestResolutionImage(Element imgElement) {
        String srcset = imgElement.attr("srcset");
        if (!srcset.isEmpty()) {
            // Parse srcset to find highest resolution
            // Format: "url1 100w, url2 500w, url3 1000w"
            String[] sources = srcset.split(",");
            String highestResUrl = null;
            int highestWidth = 0;

            for (String source : sources) {
                source = source.trim();
                if (source.isEmpty()) continue;

                String[] parts = source.split("\\s+");
                if (parts.length >= 2) {
                    try {
                        int width = Integer.parseInt(parts[parts.length - 1].replace("w", ""));
                        if (width > highestWidth) {
                            highestWidth = width;
                            highestResUrl = parts[0];
                        }
                    } catch (NumberFormatException e) {
                        // Not a valid width, skip
                    }
                }
            }

            if (highestResUrl != null) {
                return removeDimensions(highestResUrl);
            }
        }

        // Fallback to src attribute
        String src = imgElement.attr("src");
        if (!src.isEmpty()) {
            return removeDimensions(src);
        }

        return "";
    }

    /**
     * Fetch audiobook details from detail page
     */
    public void getAudiobookDetails(String url, Callback<Audiobook> callback) {
        executor.execute(() -> {
            try {
                Document doc = Jsoup.connect(url)
                        .timeout(TIMEOUT)
                        .userAgent("Mozilla/5.0 (Linux; Android 10; SM-G975F) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.101 Mobile Safari/537.36")
                        .get();

                Audiobook audiobook = new Audiobook();

                // Parse title
                Element titleElement = doc.selectFirst("h1.title-page, h1.entry-title, .post-single h1");
                if (titleElement != null) {
                    //Chris Wooding – The Ember Blade Audiobook
                    Log.i(TAG, "getAudiobookDetails: " + titleElement.text().trim());
                    if (titleElement.text().trim().contains("–")) {
                        String[] titleauthor = titleElement.text().trim().split("–");
                        String title = titleauthor[1].trim();
                        String author = titleauthor[0].trim();

                        audiobook.setTitle(title.replace("Audiobook", "").trim());
                        audiobook.setAuthor(author.trim());
                    } else {
                        audiobook.setTitle(titleElement.text().trim());

                    }
                }

                // Parse image
                // Parse image - get highest resolution from srcset
                Element imgElement = doc.selectFirst(".post-single img, .entry-content img, .post-cover img");
                if (imgElement != null) {
                    String imageUrl = getHighestResolutionImage(imgElement);
                    audiobook.setImageUrl(imageUrl);
                }

                // Parse description/content
                Element contentElement = doc.selectFirst(".post-single .collapseomatic_content, .entry-content, .post-content");
                if (contentElement != null) {
                    // Get text content, limiting length
                    String text = contentElement.text();
                    if (text.length() > 500) {
                        text = text.substring(0, 497) + "...";
                    }
                    audiobook.setDescription(text);
                }

                // Parse audio URLs from audio elements
                Elements audioElements = doc.select("audio source[src]");
                List<String> audioUrls = new ArrayList<>();
                List<String> trackNames = new ArrayList<>();

                for (int i = 0; i < audioElements.size(); i++) {
                    Element audioSrc = audioElements.get(i);
                    String src = audioSrc.attr("src");
                    if (!src.isEmpty()) {
                        audioUrls.add(src);
                        trackNames.add("Track " + (i + 1));
                    }
                }

                // Also check for direct audio src attributes
                if (audioUrls.isEmpty()) {
                    Elements directAudio = doc.select("audio[src]");
                    for (Element audio : directAudio) {
                        String src = audio.attr("src");
                        if (!src.isEmpty() && !audioUrls.contains(src)) {
                            audioUrls.add(src);
                            trackNames.add("Track " + audioUrls.size());
                        }
                    }
                }

                // Look for iframe embedded players
                if (audioUrls.isEmpty()) {
                    Elements iframes = doc.select("iframe[src*='audio'], iframe[src*='player']");
                    for (Element iframe : iframes) {
                        String src = iframe.attr("src");
                        Log.d(TAG, "Found iframe: " + src);
                        // Extract audio URL from iframe if possible
                    }
                }

                audiobook.setAudioUrls(audioUrls);
                audiobook.setTrackNames(trackNames);

                // Parse categories from meta
                Elements categoryElements = doc.select(".post-meta-category a[rel=category tag]");
                for (Element cat : categoryElements) {
                    audiobook.addCategory(cat.text());
                }

                // Parse author URL from tags
                Elements authorUrlElements = doc.select(".tags-links a");
                if (!authorUrlElements.isEmpty()) {
                    Element lastAuthorElement = authorUrlElements.last();
                    String authorUrl = lastAuthorElement.attr("href");
                    String authorName = lastAuthorElement.text().trim();
                    Log.i(TAG, "authorUrlElement: " + authorName);
                    Log.i(TAG, "authorUrlElement: " + authorUrl);
                    audiobook.setAuthorUrl(authorUrl);
                }

                // Parse date
                Element dateElement = doc.selectFirst(".entry-date, time[datetime]");
                if (dateElement != null) {
                    audiobook.setPublishedDate(dateElement.text().trim());
                }

                audiobook.setUrl(url);

                mainHandler.post(() -> callback.onSuccess(audiobook));
            } catch (IOException e) {
                Log.e(TAG, "Error fetching audiobook details", e);
                mainHandler.post(() -> callback.onError(e));
            }
        });
    }

    /**
     * Fetch audiobook details from audiobooks4soul.com
     * This method handles the custom encryption used by the custom-story-audio plugin.
     * It establishes a session to get cookies, extracts encrypted tokens, and decrypts them
     * using the server-side decryption endpoint.
     */
    public void getAudiobooks4soulDetails(String url, Callback<Audiobook> callback) {
        executor.execute(() -> {
            try {
                // Extract base URL from the full URL
                String baseUrl = extractBaseUrl(url);

                // Use Jsoup's Connection to capture cookies
                Connection.Response response = Jsoup.connect(url)
                        .timeout(TIMEOUT)
                        .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.101 Mobile Safari/537.36")
                        .execute();

                // Get cookies from response
                Map<String, String> cookies = response.cookies();

                // Build session cookie string
                StringBuilder sessionCookie = new StringBuilder();
                for (Map.Entry<String, String> cookie : cookies.entrySet()) {
                    if (sessionCookie.length() > 0) {
                        sessionCookie.append("; ");
                    }
                    sessionCookie.append(cookie.getKey()).append("=").append(cookie.getValue());
                }

                String sessionCookieStr = sessionCookie.toString();
                Log.d(TAG, "Session cookies captured: " + sessionCookieStr);

                // Parse the document
                Document doc = response.parse();
                Audiobook audiobook = new Audiobook();

                // Store the session cookie in the audiobook object
                audiobook.setSessionCookie(sessionCookieStr);
                audiobook.setDetailUrl(url); // Also store the detail URL for Referer header

                // Parse title from h1.tdb-title-text
                Element titleElement = doc.selectFirst("h1.tdb-title-text");
                if (titleElement != null) {
                    String titleText = titleElement.text().trim();
                    Log.d(TAG, "Title found: " + titleText);

                    // Extract title (remove "Audiobook by [Author]" suffix)
                    String title = titleText.replaceAll("(?i)\\s+Audiobook.*$", "").trim();
                    audiobook.setTitle(title);
                }

                // Parse author from metadata span containing author link
                Element authorElement = doc.selectFirst("span:has(a[href*='/story-author/']) a");
                if (authorElement != null) {
                    String author = authorElement.text().trim();
                    audiobook.setAuthor(author);
                    Log.d(TAG, "Author found: " + author);
                }

                // Parse narrator
                Element narratorElement = doc.selectFirst("span:has(a[href*='/narrator/']) a");
                if (narratorElement != null) {
                    String narrator = narratorElement.text().trim();
                    // You can add narrator to audiobook if you have a setter
                    Log.d(TAG, "Narrator found: " + narrator);
                }

                // Parse image from .simp-cover or featured image
                Element imgElement = doc.selectFirst(".simp-cover img, .tdb_single_featured_image img");
                if (imgElement != null) {
                    String imageUrl = imgElement.attr("src");
                    if (imageUrl.isEmpty()) {
                        imageUrl = imgElement.attr("data-src");
                    }
                    if (!imageUrl.isEmpty()) {
                        audiobook.setImageUrl(removeDimensions(imageUrl));
                    }
                }

                // Parse description from .tdb_single_content
                Element contentElement = doc.selectFirst(".tdb_single_content .tdb-block-inner");
                if (contentElement != null) {
                    String text = contentElement.text();
                    if (text.length() > 500) {
                        text = text.substring(0, 497) + "...";
                    }
                    audiobook.setDescription(text);
                }

                // Parse genre only (from Genre metadata)
                Element genreElement = doc.selectFirst("span:has(a[href*='/uncategorized/']) a, span:contains(Genre:) ~ a");
                if (genreElement != null) {
                    audiobook.addCategory(genreElement.text());
                } else {
                    // Fallback: look for the breadcrumb genre
                    Elements breadcrumbLinks = doc.select(".tdb_breadcrumbs a[href*='/uncategorized/']");
                    if (!breadcrumbLinks.isEmpty()) {
                        audiobook.addCategory(breadcrumbLinks.first().text());
                    }
                }

                // Step 2: Extract encrypted audio sources from span.simp-source elements
                Elements audioElements = doc.select("span.simp-source[data-src]");
                Log.d(TAG, "Found " + audioElements.size() + " encrypted audio tracks");

                List<String> audioUrls = new ArrayList<>();
                List<String> trackNames = new ArrayList<>();

                // Step 3: Decrypt each audio URL using the session
                for (int i = 0; i < audioElements.size(); i++) {
                    Element audioElement = audioElements.get(i);
                    String encryptedToken = audioElement.attr("data-src");
                    String trackName = audioElement.text().trim();

                    if (trackName.isEmpty()) {
                        trackName = "Track " + (i + 1);
                    }

                    if (!encryptedToken.isEmpty()) {
                        try {
                            String decryptedUrl = decryptAudioUrl(baseUrl, encryptedToken, cookies);
                            if (decryptedUrl != null && !decryptedUrl.isEmpty()) {

                                //https://storage01.ezaudiobooks.com/Soulful_Exploration.mp3
                                if (!decryptedUrl.contains("Soulful_Exploration")) { //
                                    audioUrls.add(decryptedUrl);
                                    trackNames.add(trackName);
                                    Log.d(TAG, "Decrypted: " + trackName + " -> " + decryptedUrl);
                                }
                            }
                        } catch (Exception e) {
                            Log.e(TAG, "Failed to decrypt track: " + trackName, e);
                        }
                    }
                }

                audiobook.setAudioUrls(audioUrls);
                audiobook.setTrackNames(trackNames);
                audiobook.setUrl(url);

                mainHandler.post(() -> callback.onSuccess(audiobook));

            } catch (IOException e) {
                Log.e(TAG, "Error fetching audiobook details from audiobooks4soul", e);
                mainHandler.post(() -> callback.onError(e));
            }
        });
    }

    /**
     * Extract base URL from full URL for constructing decryption endpoint
     */
    private String extractBaseUrl(String url) {
        try {
            URL parsedUrl = new URL(url);
            return parsedUrl.getProtocol() + "://" + parsedUrl.getHost();
        } catch (Exception e) {
            Log.e(TAG, "Error parsing URL: " + url, e);
            return AUDIOBOOKS4SOUL_BASE_URL;
        }
    }

    /**
     * Decrypt an audio URL using the server-side decryption endpoint.
     * Requires active session cookie for authentication.
     */
    private String decryptAudioUrl(String baseUrl, String encryptedToken, Map<String, String> cookies) {
        try {
            String decryptUrl = baseUrl + "/wp-content/plugins/custom-story-audio/inc/security/decrypt.php?encrypted="
                    + URLEncoder.encode(encryptedToken, StandardCharsets.UTF_8.toString());

            Log.d(TAG, "Decrypting URL: " + decryptUrl);

            URL url = new URL(decryptUrl);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.101 Safari/537.36");
            conn.setRequestProperty("Accept", "text/plain,*/*");
            conn.setRequestProperty("Referer", baseUrl);

            // Add cookies to the request
            if (cookies != null && !cookies.isEmpty()) {
                StringBuilder cookieHeader = new StringBuilder();
                for (Map.Entry<String, String> cookie : cookies.entrySet()) {
                    if (cookieHeader.length() > 0) {
                        cookieHeader.append("; ");
                    }
                    cookieHeader.append(cookie.getKey()).append("=").append(cookie.getValue());
                }
                conn.setRequestProperty("Cookie", cookieHeader.toString());
                Log.d(TAG, "Sending cookies: " + cookieHeader.toString());
            }
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(15000);

            int responseCode = conn.getResponseCode();
            Log.d(TAG, "Decrypt response code: " + responseCode);

            if (responseCode == HttpURLConnection.HTTP_OK) {
                InputStream is = conn.getInputStream();
                Scanner scanner = new Scanner(is, StandardCharsets.UTF_8.name());
                String decryptedUrl = scanner.useDelimiter("\\A").next().trim();
                scanner.close();
                is.close();

                Log.d(TAG, "Decrypted URL: " + decryptedUrl);
                return decryptedUrl;
            } else {
                Log.e(TAG, "Decrypt failed with response code: " + responseCode);
                InputStream errorStream = conn.getErrorStream();
                if (errorStream != null) {
                    Scanner scanner = new Scanner(errorStream, StandardCharsets.UTF_8.name());
                    String errorResponse = scanner.useDelimiter("\\A").next().trim();
                    scanner.close();
                    Log.e(TAG, "Error response: " + errorResponse);
                }
                return null;
            }

        } catch (Exception e) {
            Log.e(TAG, "Error decrypting audio URL", e);
            return null;
        }
    }

    /**
     * Fetch audiobook sample from Audible.com
     *
     * @param searchQuery Format: "book title by author name" (e.g., "61 hours by Lee Child")
     */
    public void getAudiobookAudibleSample(String searchQuery, Callback<Audiobook> callback) {
        executor.execute(() -> {
            try {
                if (!searchQuery.toLowerCase().contains(" by ")) {
                    Log.e(TAG, "Search query must contain 'by' keyword");
                    mainHandler.post(() -> callback.onError(new IllegalArgumentException("Query must be in format: 'title by author'")));
                    return;
                }

                // ✅ Use " by " with limit=2 to avoid splitting on "by" inside words
                String[] parts = searchQuery.split("(?i) by ", 2);
                if (parts.length < 2) {
                    Log.e(TAG, "Invalid query format");
                    mainHandler.post(() -> callback.onError(new IllegalArgumentException("Invalid query format")));
                    return;
                }

                String bookTitle = parts[0].trim();
                String author = parts[1].trim();

                // Construct Audible search URL
                String encodedQuery = URLEncoder.encode(searchQuery, StandardCharsets.UTF_8.toString());
                String searchUrl = "https://www.audible.com/search?keywords=" + encodedQuery;

                Log.d(TAG, "Searching Audible for: " + searchQuery);
                Log.d(TAG, "URL: " + searchUrl);

                Document doc = Jsoup.connect(searchUrl)
                        .timeout(TIMEOUT)
                        .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.101 Safari/537.36")
                        .get();

                // Get all audiobook product items from the shared element
                Elements resultElements = doc.select("li.bc-list-item.productListItem");

                Log.d(TAG, "Found " + resultElements.size() + " search results");

                Audiobook matchedAudiobook = null;

                // Iterate through results to find matching audiobook
                for (Element resultElement : resultElements) {
                    try {
                        // Extract title - it's in an h2.bc-heading structure
                        Element titleElement = resultElement.selectFirst("h2.bc-heading");
                        if (titleElement == null) {
                            titleElement = resultElement.selectFirst("h2");
                        }
                        Log.d(TAG, "getAudiobookAudibleSample title: " + titleElement);
                        if (titleElement == null) continue;

                        String resultTitle = titleElement.text().trim();

                        // Extract series info - "Benjamin Ashwood, Book 6"
                        Element seriesElement = resultElement.selectFirst("li.bc-list-item:contains(Book)");
                        String seriesInfo = "";
                        if (seriesElement != null) {
                            seriesInfo = seriesElement.text().trim();
                        }

                        // Extract author - look for "By:" label
                        Elements authorElements = resultElement.select("li.bc-list-item");
                        String resultAuthor = "";
                        for (Element li : authorElements) {
                            String text = li.text().trim();
                            if (text.startsWith("By:")) {
                                resultAuthor = text.replace("By:", "").trim();
                                break;
                            }
                        }
                        Log.d(TAG, "getAudiobookAudibleSample author: " + resultAuthor);
                        if (resultAuthor.isEmpty()) continue;

                        // Extract narrator - look for "Narrated by:" label
                        String narrator = "";
                        for (Element li : authorElements) {
                            String text = li.text().trim();
                            if (text.startsWith("Narrated by:")) {
                                narrator = text.replace("Narrated by:", "").trim();
                                break;
                            }
                        }

                        // Extract length/runtime - look for "Length:" label
                        String runtime = "";
                        for (Element li : authorElements) {
                            String text = li.text().trim();
                            if (text.startsWith("Length:")) {
                                runtime = text.replace("Length:", "").trim();
                                break;
                            }
                        }

                        // Extract format (Unabridged/Abridged)
                        String format = "";
                        for (Element li : authorElements) {
                            String text = li.text().trim();
                            if (text.equals("Unabridged") || text.equals("Abridged")) {
                                format = text;
                                break;
                            }
                        }

                        // Extract language
                        String languagetxt = "";
                        for (Element li : authorElements) {
                            String text = li.text().trim();
                            if (text.startsWith("Language:")) {
                                languagetxt = text.replace("Language:", "").trim();
                                Log.i(TAG, "getAudiobookAudibleSample language: " + languagetxt);
                                break;
                            }
                        }

                        // Compare title and author (case-insensitive)
                        String cleanResultTitle = resultTitle.toLowerCase().replaceAll("[^a-z0-9\\s]", "");
                        String cleanSearchTitle = bookTitle.toLowerCase().replaceAll("[^a-z0-9\\s]", "");
                        String cleanResultAuthor = formatAuthor(resultAuthor);
                        String cleanSearchAuthor = formatAuthor(author);

                        Log.i(TAG, "getAudiobookAudibleSample cleanResultAuthor: " + cleanResultAuthor);
                        Log.i(TAG, "getAudiobookAudibleSample cleanSearchAuthor: " + cleanSearchAuthor);
                        Log.i(TAG, "getAudiobookAudibleSample cleanSearchTitle: " + cleanSearchTitle);

                        // ✅ FIXED: use authorsMatch() instead of simple contains()
                        if (cleanResultTitle.contains(cleanSearchTitle) && authorsMatch(cleanResultAuthor, cleanSearchAuthor)) {
                            if (languagetxt.toLowerCase().trim().equals("english")) {
                                Log.d(TAG, "Match found: " + resultTitle + " by " + resultAuthor);

                                matchedAudiobook = new Audiobook();
                                matchedAudiobook.setTitle(resultTitle);
                                matchedAudiobook.setAuthor(resultAuthor);

                                Element imgElement = resultElement.selectFirst("img.bc-image-inset-border");
                                if (imgElement == null) {
                                    imgElement = resultElement.selectFirst("img");
                                }
                                if (imgElement != null) {
                                    String imageUrl = null;

                                    // ✅ Prefer 2x image from srcset for better notification art quality
                                    String srcset = imgElement.attr("srcset");
                                    if (!srcset.isEmpty()) {
                                        for (String entry : srcset.split(",")) {
                                            entry = entry.trim();
                                            if (entry.endsWith("2x")) {
                                                imageUrl = entry.replace("2x", "").trim();
                                                break;
                                            }
                                        }
                                    }

                                    // ✅ Fallback to src
                                    if (imageUrl == null || imageUrl.isEmpty()) {
                                        imageUrl = imgElement.attr("src");
                                    }

                                    if (!imageUrl.isEmpty()) {
                                        matchedAudiobook.setImageUrl(removeDimensions(imageUrl));
                                    }
                                }

                                // Build description with narrator and runtime
                                StringBuilder description = new StringBuilder();
                                if (!narrator.isEmpty()) {
                                    description.append("Narrated by: ").append(narrator);
                                }
                                if (!runtime.isEmpty()) {
                                    if (description.length() > 0) description.append("\n");
                                    description.append("Length: ").append(runtime);
                                }
                                if (!format.isEmpty()) {
                                    if (description.length() > 0) description.append("\n");
                                    description.append(format);
                                }
                                matchedAudiobook.setDescription(description.toString());

                                // Extract ratings from the popover content
                                Element overallRatingElement = resultElement.selectFirst("div[aria-label*='out of 5 stars']");
                                if (overallRatingElement != null) {
                                    String ariaLabel = overallRatingElement.attr("aria-label");
                                    if (!ariaLabel.isEmpty()) {
                                        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("([\\d.]+) out of 5 stars");
                                        java.util.regex.Matcher matcher = pattern.matcher(ariaLabel);
                                        if (matcher.find()) {
                                            String rating = matcher.group(1);
                                            //matchedAudiobook.setRating(Float.parseFloat(rating));
                                        }
                                    }
                                }

                                // Extract review count - look for number near the star ratings
                                Element reviewCountElement = resultElement.selectFirst("span.bc-letter-space-mini");
                                if (reviewCountElement != null) {
                                    String reviewCount = reviewCountElement.nextSibling().toString().trim();
                                    if (reviewCount.isEmpty()) {
                                        Element parentEl = reviewCountElement.parent();
                                        if (parentEl != null) {
                                            String fullText = parentEl.text().trim();
                                            java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("([\\d,]+)");
                                            java.util.regex.Matcher matcher = pattern.matcher(fullText);
                                            if (matcher.find()) {
                                                reviewCount = matcher.group(1).replace(",", "");
                                            }
                                        }
                                    } else {
                                        reviewCount = reviewCount.replace(",", "");
                                    }
                                    try {
                                        //matchedAudiobook.setReviewCount(Integer.parseInt(reviewCount));
                                    } catch (NumberFormatException e) {
                                        Log.w(TAG, "Could not parse review count: " + reviewCount);
                                    }
                                }

                                // Extract series info and language as categories
                                List<String> categories = new ArrayList<>();
                                if (!seriesInfo.isEmpty()) {
                                    categories.add(seriesInfo);
                                }

                                // Extract release date if available
                                Element releaseDateElement = resultElement.selectFirst("li.releaseDateLabel span.bc-text");
                                if (releaseDateElement != null) {
                                    String releaseDate = releaseDateElement.text().trim();
                                    if (!releaseDate.isEmpty()) {
                                        categories.add("Release: " + releaseDate);
                                    }
                                }
                                matchedAudiobook.setCategories(categories);

                                // Extract sample MP3 URL
                                Element sampleButton = resultElement.selectFirst("button[data-mp3]");
                                if (sampleButton != null) {
                                    String sampleMp3Url = sampleButton.attr("data-mp3");
                                    if (!sampleMp3Url.isEmpty()) {
                                        List<String> audioUrls = new ArrayList<>();
                                        audioUrls.add(sampleMp3Url);
                                        matchedAudiobook.setAudioUrls(audioUrls);

                                        List<String> trackNames = new ArrayList<>();
                                        trackNames.add("Sample - " + resultTitle);
                                        matchedAudiobook.setTrackNames(trackNames);

                                        Log.d(TAG, "Sample URL found: " + sampleMp3Url);
                                    }
                                }

                                // Extract detail page URL
                                Element detailLink = resultElement.selectFirst("a[href*='/pd/']");
                                if (detailLink == null) {
                                    detailLink = resultElement.selectFirst("div[data-trigger] a");
                                }
                                if (detailLink != null) {
                                    String detailUrl = detailLink.attr("href");
                                    if (!detailUrl.isEmpty()) {
                                        if (!detailUrl.startsWith("http")) {
                                            detailUrl = "https://www.audible.com" + detailUrl;
                                        }
                                        matchedAudiobook.setUrl(detailUrl);
                                    }
                                }

                                break; // Found the match, exit loop
                            }
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Error parsing individual result", e);
                        continue;
                    }
                }

                if (matchedAudiobook == null) {
                    Log.d(TAG, "No matching audiobook found for: " + searchQuery);
                    mainHandler.post(() -> callback.onError(new Exception("No matching audiobook found")));
                } else {
                    Audiobook finalAudiobook = matchedAudiobook;
                    mainHandler.post(() -> callback.onSuccess(finalAudiobook));
                }

            } catch (IOException e) {
                Log.e(TAG, "Error fetching Audible sample", e);
                mainHandler.post(() -> callback.onError(e));
            }
        });
    }

    /**
     * Fuzzy author match that handles middle initials/names gracefully.
     * Every significant word (length > 1) in the search author must appear
     * in the result author. Single-letter initials in the search are also skipped.
     *
     * Examples:
     *   "Andrew Dobell"   vs "Andrew M. Dobell"  → true  ✅
     *   "Lee Child"       vs "Lee Child"          → true  ✅
     *   "J.R.R. Tolkien"  vs "JRR Tolkien"        → true  ✅ (after punctuation strip)
     *   "Andrew Smith"    vs "Andrew M. Dobell"   → false ✅
     */

    /**
     * For multi-author queries, match if ANY single author from the comma-separated
     * list individually matches the result author.
     * e.g. "Barb Hendee, J.C. Hendee" vs "Barb Hendee" → true (first author matches)
     */
    private boolean authorsMatch(String resultAuthor, String searchAuthor) {
        if (resultAuthor == null || searchAuthor == null) return false;

        // Try full string first (handles single-author and exact multi-author matches)
        if (singleAuthorMatch(resultAuthor, searchAuthor)) return true;

        // Split by comma and try each individual author
        if (searchAuthor.contains(",")) {
            for (String singleAuthor : searchAuthor.split(",")) {
                if (singleAuthorMatch(resultAuthor, singleAuthor.trim())) {
                    return true; // any one matching author is enough
                }
            }
        }

        return false;
    }

    /**
     * Token-based match for a single author name.
     * Handles middle initials: "Andrew Dobell" matches "Andrew M. Dobell"
     */
    private boolean singleAuthorMatch(String resultAuthor, String searchAuthor) {
        if (resultAuthor == null || searchAuthor == null || searchAuthor.isEmpty()) return false;

        String normalizedResult = resultAuthor.toLowerCase().replaceAll("[^a-z0-9\\s]", "").trim();
        String normalizedSearch = searchAuthor.toLowerCase().replaceAll("[^a-z0-9\\s]", "").trim();

        // Fast path: direct substring match
        if (normalizedResult.contains(normalizedSearch) || normalizedSearch.contains(normalizedResult)) {
            return true;
        }

        // Token-based: all significant words in search must appear in result
        String[] searchTokens = normalizedSearch.split("\\s+");
        Set<String> resultTokenSet = new HashSet<>(Arrays.asList(normalizedResult.split("\\s+")));

        int matchCount = 0;
        int significantTokens = 0;

        for (String token : searchTokens) {
            if (token.length() <= 1) continue; // skip initials
            significantTokens++;
            if (resultTokenSet.contains(token)) matchCount++;
        }

        return significantTokens > 0 && matchCount == significantTokens;
    }
}
