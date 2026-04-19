package com.kiduyu.ebookstudio;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.webkit.WebSettings;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.AppCompatButton;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.bumptech.glide.Glide;
import com.kiduyu.ebookstudio.R;
import com.kiduyu.ebookstudio.databinding.ActivityBookDetailBinding;
import com.kiduyu.adapter.RelatedAdapter;
import com.kiduyu.fragment.ReportBookFragment;
import com.kiduyu.fragment.WriteRateReviewFragment;
import com.kiduyu.item.Audiobook;
import com.kiduyu.item.BookDetailList;
import com.kiduyu.response.BookDetailRP;
import com.kiduyu.rest.ApiClient;
import com.kiduyu.rest.ApiInterface;
import com.kiduyu.service.AudiobookRepository;
import com.kiduyu.util.API;
import com.kiduyu.util.BannerAds;
import com.kiduyu.util.Constant;
import com.kiduyu.util.Events;
import com.kiduyu.util.FavouriteIF;
import com.kiduyu.util.GlobalBus;
import com.kiduyu.util.Method;
import com.kiduyu.util.StatusBar;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.karumi.dexter.Dexter;
import com.karumi.dexter.PermissionToken;
import com.karumi.dexter.listener.PermissionDeniedResponse;
import com.karumi.dexter.listener.PermissionGrantedResponse;
import com.karumi.dexter.listener.PermissionRequest;
import com.karumi.dexter.listener.single.PermissionListener;

import org.greenrobot.eventbus.Subscribe;
import org.jetbrains.annotations.NotNull;

import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;


public class BookDetailsActivity extends AppCompatActivity {

    ActivityBookDetailBinding viewBookDetail;
    Method method;
    String postBookId;


    AudiobookRepository repository;
    RelatedAdapter relatedAdapter;
    BookDetailList bookDetailListPos;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        StatusBar.initWhite(BookDetailsActivity.this);
        viewBookDetail = ActivityBookDetailBinding.inflate(getLayoutInflater());
        setContentView(viewBookDetail.getRoot());
        GlobalBus.getBus().register(this);
        method = new Method(BookDetailsActivity.this);
        method.forceRTLIfSupported();
        repository= new AudiobookRepository();

        Intent intent = getIntent();
        postBookId = intent.getStringExtra("BOOK_ID");

        viewBookDetail.toolbarMain.tvToolbarTitle.setText(getString(R.string.book_detail_title));
        viewBookDetail.toolbarMain.imageFilter.setVisibility(View.VISIBLE);
        viewBookDetail.toolbarMain.imageFilter.setImageResource(R.drawable.img_share);
        viewBookDetail.toolbarMain.imageFilter.setOnClickListener(v -> {
            if (bookDetailListPos != null && bookDetailListPos.getShare_url() != null) {
                Intent intent1 = new Intent(Intent.ACTION_SEND);
                intent1.setType("text/plain");
                intent1.putExtra(Intent.EXTRA_TEXT, bookDetailListPos.getShare_url());
                startActivity(Intent.createChooser(intent1, "Choose one"));
            } else {
                method.alertBox(getResources().getString(R.string.wrong));
            }
        });

        viewBookDetail.toolbarMain.imageArrowBack.setOnClickListener(v -> getOnBackPressedDispatcher().onBackPressed());

        viewBookDetail.ivRateList.setOnClickListener(v -> {
            Intent intentRateList = new Intent(BookDetailsActivity.this, RateReviewActivity.class);
            startActivity(intentRateList);
        });

        viewBookDetail.progressHome.setVisibility(View.GONE);
        viewBookDetail.llNoData.clNoDataFound.setVisibility(View.GONE);
        viewBookDetail.nsView.setVisibility(View.GONE);
        viewBookDetail.btnBuyBook.setVisibility(View.GONE);

        viewBookDetail.rvRelatedBook.setHasFixedSize(true);
        viewBookDetail.rvRelatedBook.setLayoutManager(new LinearLayoutManager(BookDetailsActivity.this, LinearLayoutManager.HORIZONTAL, false));
        viewBookDetail.rvRelatedBook.setFocusable(false);
        viewBookDetail.rvRelatedBook.setNestedScrollingEnabled(false);
        viewBookDetail.llCheckBookAudiobook.setOnClickListener(v -> {
            if (method.isNetworkAvailable()) {
                StringBuilder strAuthor = new StringBuilder();
                if (!bookDetailListPos.getListBookDetailAuthor().isEmpty()) {
                    String prefix = "";
                    for (int k = 0; k < bookDetailListPos.getListBookDetailAuthor().size(); k++) {
                        strAuthor.append(prefix);
                        prefix = ", ";
                        strAuthor.append(bookDetailListPos.getListBookDetailAuthor().get(k).getAuthor_name());
                    }
                }
                CheckBookAudiobook(bookDetailListPos.getPost_title(), strAuthor.toString());
            }


        });


        //linearButtonPart
        if(viewBookDetail.llDownload.getVisibility()==View.GONE){
            viewBookDetail.linearButtonPart.setWeightSum(4);
        }
        if (method.isNetworkAvailable()) {
            bookDetailData();
        } else {
            method.alertBox(getResources().getString(R.string.internet_connection));
        }

        BannerAds.showBannerAds(BookDetailsActivity.this, viewBookDetail.layoutAds);
    }

    private void CheckBookAudiobook(String postTitle, String string) {
        String bookTitle = postTitle;
        String bookAuthor = string;
        String query = bookTitle + " by " + bookAuthor;    
        Toast.makeText(BookDetailsActivity.this, "Book Title: "+bookTitle + " Book Author: " + bookAuthor, Toast.LENGTH_SHORT).show();
        Audiobooksearch(query);
    }

    private void Audiobooksearch(String query) {
        viewBookDetail.progressHome.setVisibility(View.VISIBLE);
        if (query == null || query.trim().isEmpty()) {
            //error.setValue("Please enter a search term");
            viewBookDetail.progressHome.setVisibility(View.GONE);
            Toast.makeText(BookDetailsActivity.this, "Please enter a search term", Toast.LENGTH_SHORT).show();
            return;
        }

        String trimmedQuery = query.trim();

        repository.searchAudiobooks(trimmedQuery, new AudiobookRepository.DataCallback<List<Audiobook>>() {
            @Override
            public void onSuccess(List<Audiobook> result) {

                if (result.isEmpty()) {
                    viewBookDetail.progressHome.setVisibility(View.GONE);
                    Toast.makeText(BookDetailsActivity.this, "No results found", Toast.LENGTH_LONG).show();
                    Toast.makeText(BookDetailsActivity.this, "Checking Sample From Audible", Toast.LENGTH_LONG).show();
                    LoadAudibleSample(trimmedQuery);

                } else {
                    viewBookDetail.progressHome.setVisibility(View.GONE);
                    // Get the first audiobook result and load its details
                    if (result.size() > 0) {
                        Audiobook firstAudiobook = result.get(0);
                        String audiobookUrl = firstAudiobook.getUrl();


                        if (audiobookUrl != null && !audiobookUrl.isEmpty()) {
                            if(audiobookUrl.contains("audiobooks4soul.com")){
                                Toast.makeText(BookDetailsActivity.this, "Loading: " + firstAudiobook.getTitle(), Toast.LENGTH_SHORT).show();
                                loadSampleBook(audiobookUrl);

                            } else {
                                Toast.makeText(BookDetailsActivity.this, "Loading: " + firstAudiobook.getTitle(), Toast.LENGTH_SHORT).show();
                                loadAndPlayAudiobook(audiobookUrl);
                            }
                        } else {
                            Toast.makeText(BookDetailsActivity.this, "Invalid audiobook URL", Toast.LENGTH_SHORT).show();
                        }
                    }
                }
                // After initial search, check for next page
                //checkForNextPage(trimmedQuery, 1);
            }

            @Override
            public void onError(Exception e) {
                viewBookDetail.progressHome.setVisibility(View.GONE);
                Toast.makeText(BookDetailsActivity.this, "Search failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
            }
        });
    }

    private void LoadAudibleSample(String trimmedQuery) {
        viewBookDetail.progressHome.setVisibility(View.VISIBLE);
        repository.getAudiobookAudibleSample(trimmedQuery, new AudiobookRepository.DataCallback<Audiobook>() {


            @Override
            public void onSuccess(Audiobook data) {
                if (data != null && data.getAudioUrls().size()>0) {
                    loadAudibleSampleBook(data.getUrl(),data,trimmedQuery);
                    viewBookDetail.progressHome.setVisibility(View.GONE);
                }

            }

            @Override
            public void onError(Exception e) {
                Toast.makeText(BookDetailsActivity.this, "Search failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
                Log.d("exception_error", e.toString());
                viewBookDetail.progressHome.setVisibility(View.GONE);

            }
        });
    }


    private void loadAudibleSampleBook(String url, Audiobook data,String query) {
        Log.i("loadSampleBook","audiobookUrl"+ url);
        Toast.makeText(BookDetailsActivity.this, "Loading: " + url, Toast.LENGTH_SHORT).show();
        Intent intent = new Intent(BookDetailsActivity.this, AudiobookPlayerActivity.class);
        intent.putExtra("AUDIOBOOK_URL", data.getAudioUrls().get(0));
        intent.putExtra("FROM_SEARCH", false);
        intent.putExtra("AUDIBLE_AUDIOBOOK", query);
        startActivity(intent);
    }

    private void loadSampleBook(String searchQueryurl) {
        viewBookDetail.progressHome.setVisibility(View.GONE);
        Log.i("loadSampleBook","audiobookUrl"+ searchQueryurl);
        Toast.makeText(BookDetailsActivity.this, "Loading: " + searchQueryurl, Toast.LENGTH_SHORT).show();
        Intent intent = new Intent(BookDetailsActivity.this, AudiobookPlayerActivity.class);
        intent.putExtra("AUDIOBOOK_URL", searchQueryurl);
        intent.putExtra("FROM_SEARCH", true);
        startActivity(intent);
        //String searchQueryurl = "https://audiobooks4soul.com/fantastic-beasts-and-where-to-find-them-audiobook-3/";
        /**repository.Audiobooks4soulDetails(searchQueryurl, new AudiobookRepository.DataCallback<Audiobook>() {

            @Override
            public void onSuccess(Audiobook result) {
                String audiobookUrl =result.getUrl();
                if (result != null && !audiobookUrl.isEmpty()) {
                    Log.i("loadSampleBook","audiobookUrl"+ result.getAudioUrls().size());
                    Log.i("loadSampleBook","audiobookUrl"+ result);
                    Toast.makeText(BookDetailsActivity.this, "Loading: " + result.getTitle(), Toast.LENGTH_SHORT).show();
                    Intent intent = new Intent(BookDetailsActivity.this, AudiobookPlayerActivity.class);
                    intent.putExtra("AUDIOBOOK_URL", audiobookUrl);
                    intent.putExtra("FROM_SEARCH", true);
                    startActivity(intent);
                    viewBookDetail.progressHome.setVisibility(View.GONE);
                } else {
                    Toast.makeText(BookDetailsActivity.this, "Invalid audiobook URL", Toast.LENGTH_SHORT).show();
                    viewBookDetail.progressHome.setVisibility(View.GONE);
                }

            }

            @Override
            public void onError(Exception e) {
                Toast.makeText(BookDetailsActivity.this, "Search failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
                Log.d("exception_error", e.toString());
                viewBookDetail.progressHome.setVisibility(View.GONE);

            }
        });**/
    }

    private void loadAndPlayAudiobook(String url) {
        repository.getAudiobookDetails(url, new AudiobookRepository.DataCallback<Audiobook>() {
            @Override
            public void onSuccess(Audiobook result) {
                if (result != null && result.getAudioUrls() != null && !result.getAudioUrls().isEmpty()) {
                    // Launch the player activity with the audiobook
                    Intent intent = new Intent(BookDetailsActivity.this, AudiobookPlayerActivity.class);
                    intent.putExtra("AUDIOBOOK_URL", url);
                    intent.putExtra("FROM_SEARCH", false);
                    startActivity(intent);
                } else {
                    Toast.makeText(BookDetailsActivity.this, "No audio tracks available", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onError(Exception e) {
                Toast.makeText(BookDetailsActivity.this, "Failed to load audiobook: " + e.getMessage(), Toast.LENGTH_LONG).show();
            }
        });
    }

    private void bookDetailData() {

        viewBookDetail.progressHome.setVisibility(View.VISIBLE);

        JsonObject jsObj = (JsonObject) new Gson().toJsonTree(new API(BookDetailsActivity.this));
        jsObj.addProperty("book_id", postBookId);
        jsObj.addProperty("user_id", method.getIsLogin() ? method.getUserId() : "");
        ApiInterface apiService = ApiClient.getClient().create(ApiInterface.class);
        Call<BookDetailRP> call = apiService.getBookDetailData(API.toBase64(jsObj.toString()));
        call.enqueue(new Callback<BookDetailRP>() {
            @Override
            public void onResponse(@NotNull Call<BookDetailRP> call, @NotNull Response<BookDetailRP> response) {
                try {

                    BookDetailRP bookDetailRP = response.body();

                    if (bookDetailRP != null && bookDetailRP.getSuccess().equals("1")) {

                        if (bookDetailRP.getBookDetailLists().size() != 0) {
                            viewBookDetail.nsView.setVisibility(View.VISIBLE);
                            viewBookDetail.btnBuyBook.setVisibility(View.VISIBLE);
                            bookDetailListPos = bookDetailRP.getBookDetailLists().get(0);
                            bookViewData(postBookId);
                            viewBookDetail.tvBookName.setText(bookDetailListPos.getPost_title());
                            viewBookDetail.tvByAuthor.setSelected(true);
                            StringBuilder strAuthor = new StringBuilder();
                            if (!bookDetailListPos.getListBookDetailAuthor().isEmpty()) {
                                String prefix = "";
                                for (int k = 0; k < bookDetailListPos.getListBookDetailAuthor().size(); k++) {
                                    strAuthor.append(prefix);
                                    prefix = ", ";
                                    strAuthor.append(bookDetailListPos.getListBookDetailAuthor().get(k).getAuthor_name());
                                }
                            }
                            viewBookDetail.tvByAuthor.setText(getString(R.string.by_author, strAuthor.toString()));
                            viewBookDetail.tvBookView.setText(Method.Format(Integer.parseInt(bookDetailListPos.getTotal_views())));

                            if (bookDetailListPos.getBook_on_rent().equals("1")) {
                                viewBookDetail.llPremium.setVisibility(View.VISIBLE);
                                viewBookDetail.tvBookPrice.setText(getString(R.string.currency_code, Constant.constantCurrency, bookDetailListPos.getBook_rent_price()));
                                viewBookDetail.tvBookPrice.setBackgroundResource(R.drawable.paid_detail_box);
                                viewBookDetail.tvBookPrice.setTextColor(ContextCompat.getColor(BookDetailsActivity.this, R.color.white));
                            } else if (bookDetailListPos.getPost_access().equals("Paid")) {
                                viewBookDetail.llPremium.setVisibility(View.VISIBLE);
                                viewBookDetail.tvBookPrice.setText(getString(R.string.lbl_paid));
                                viewBookDetail.tvBookPrice.setBackgroundResource(R.drawable.paid_detail_box);
                                viewBookDetail.tvBookPrice.setTextColor(ContextCompat.getColor(BookDetailsActivity.this, R.color.white));
                            } else {
                                viewBookDetail.llPremium.setVisibility(View.GONE);
                                viewBookDetail.tvBookPrice.setText(getString(R.string.lbl_free));
                                viewBookDetail.tvBookPrice.setBackgroundResource(R.drawable.free_box);
                                viewBookDetail.tvBookPrice.setTextColor(ContextCompat.getColor(BookDetailsActivity.this, R.color.free_box_text));
                            }

                            if (!bookDetailListPos.getPost_image().equals("")) {
                                Glide.with(BookDetailsActivity.this).load(bookDetailListPos.getPost_image())
                                        .placeholder(R.drawable.placeholder_portable)
                                        .into(viewBookDetail.ivBook);
                            }

                            WebSettings webSettings = viewBookDetail.wvBookDesc.getSettings();
                            webSettings.setJavaScriptEnabled(true);
                            webSettings.setPluginState(WebSettings.PluginState.ON);
                            viewBookDetail.wvBookDesc.setBackgroundColor(Color.TRANSPARENT);
                            viewBookDetail.wvBookDesc.setFocusableInTouchMode(false);
                            viewBookDetail.wvBookDesc.setFocusable(false);

                            viewBookDetail.wvBookDesc.getSettings().setDefaultTextEncodingName("UTF-8");
                            String mimeType = "text/html";
                            String encoding = "utf-8";

                            String text = "<html dir=" + method.isWebViewTextRtl() + "><head>"
                                    + "<style type=\"text/css\">@font-face {font-family: MyFont;src: url(\"file:///android_asset/fonts/opensansromanregular.ttf\")}body{font-family: MyFont;color: " + method.webViewText() + "font-size: 14px;line-height:1.7;margin-left: 0px;margin-right: 0px;margin-top: 0px;margin-bottom: 0px;padding: 0px;}"
                                    + "a {color:" + method.webViewLink() + "text-decoration:none}"
                                    + "</style></head>"
                                    + "<body>"
                                    + bookDetailListPos.getPost_description()
                                    + "</body></html>";

                            viewBookDetail.wvBookDesc.loadDataWithBaseURL(null, text, mimeType, encoding, null);

                            viewBookDetail.ivRateList.setOnClickListener(v -> {
                                Intent intentDetail = new Intent(BookDetailsActivity.this, RateReviewActivity.class);
                                intentDetail.putExtra("RATE_BOOK_ID", bookDetailListPos.getPost_id());
                                startActivity(intentDetail);
                            });
                            viewBookDetail.tvRateAvg.setText(bookDetailListPos.getTotal_rate());
                            viewBookDetail.ratingView.setRating(Float.parseFloat(bookDetailListPos.getTotal_rate()));
                            viewBookDetail.tvRateTotalReviews.setText(getString(R.string.lbl_review_detail, Method.Format(Integer.parseInt(bookDetailListPos.getTotal_reviews()))));
                            viewBookDetail.btnWriteAReview.setOnClickListener(v -> {
                                if (method.getIsLogin()) {
                                    Bundle bundle = new Bundle();
                                    bundle.putString("postId", bookDetailListPos.getPost_id());
                                    bundle.putString("userId", method.getUserId());
                                    WriteRateReviewFragment writeRateReviewFragment = new WriteRateReviewFragment();
                                    writeRateReviewFragment.setArguments(bundle);
                                    writeRateReviewFragment.show(getSupportFragmentManager(), writeRateReviewFragment.getTag());

                                } else {
                                    Toast.makeText(BookDetailsActivity.this, getString(R.string.login_require), Toast.LENGTH_SHORT).show();
                                    Intent intentLogin = new Intent(BookDetailsActivity.this, LoginActivity.class);
                                    intentLogin.putExtra("isFromDetail", true);
                                    startActivity(intentLogin);
                                }
                            });

                            if (bookDetailListPos.getListRelatedBook().size() != 0) {
                                relatedAdapter = new RelatedAdapter(BookDetailsActivity.this, bookDetailListPos.getListRelatedBook());
                                viewBookDetail.rvRelatedBook.setAdapter(relatedAdapter);
                                relatedAdapter.setOnItemClickListener(position -> {
                                    Intent intentDetail = new Intent(BookDetailsActivity.this, BookDetailsActivity.class);
                                    intentDetail.putExtra("BOOK_ID", bookDetailListPos.getListRelatedBook().get(position).getPost_id());
                                    startActivity(intentDetail);
                                    finish();
                                });
                            } else {
                                viewBookDetail.rlRelatedSection.setVisibility(View.GONE);
                                viewBookDetail.rvRelatedBook.setVisibility(View.GONE);
                            }


                            viewBookDetail.llReportBook.setOnClickListener(v -> {
                                if (method.getIsLogin()) {
                                    Bundle bundle = new Bundle();
                                    bundle.putString("postId", bookDetailListPos.getPost_id());
                                    bundle.putString("userId", method.getUserId());
                                    ReportBookFragment reportBookFragment = new ReportBookFragment();
                                    reportBookFragment.setArguments(bundle);
                                    reportBookFragment.show(getSupportFragmentManager(), reportBookFragment.getTag());

                                } else {
                                    Toast.makeText(BookDetailsActivity.this, getString(R.string.login_require), Toast.LENGTH_SHORT).show();
                                    Intent intentLogin = new Intent(BookDetailsActivity.this, LoginActivity.class);
                                    intentLogin.putExtra("isFromDetail", true);
                                    startActivity(intentLogin);
                                }

                            });
                             
                            if (checkRentSubsPlanStatus()) {
                                viewBookDetail.btnBuyBook.setVisibility(View.GONE);
                            } else {
                                viewBookDetail.btnBuyBook.setVisibility(View.VISIBLE);
                            }
                            if (bookDetailListPos.getBook_on_rent().equals("1")) {
                                viewBookDetail.btnBuyBook.setText(getString(R.string.lbl_buy_book));
                            } else if (bookDetailListPos.getPost_access().equals("Paid")) {
                                viewBookDetail.btnBuyBook.setText(getString(R.string.lbl_buy_plan));
                            }
                            viewBookDetail.btnBuyBook.setOnClickListener(v -> {
                                if (method.getIsLogin()) {
                                    if (bookDetailListPos.getBook_on_rent().equals("1")) {
                                        Intent intentRent = new Intent(BookDetailsActivity.this, PaymentMethodActivity.class);
                                        intentRent.putExtra("planId", bookDetailListPos.getPost_id());
                                        intentRent.putExtra("planName", bookDetailListPos.getPost_title());
                                        intentRent.putExtra("planPrice", bookDetailListPos.getBook_rent_price());
                                        intentRent.putExtra("planPriceCurrency", Constant.constantCurrency);
                                        intentRent.putExtra("planUserId", method.getUserId());
                                        intentRent.putExtra("planDuration", bookDetailListPos.getBook_rent_time());
                                        intentRent.putExtra("isRent", true);
                                        startActivity(intentRent);
                                    } else {
                                        Intent intentPlan = new Intent(BookDetailsActivity.this, PlanListActivity.class);
                                        startActivity(intentPlan);
                                    }
                                } else {
                                    Toast.makeText(BookDetailsActivity.this, getString(R.string.login_require), Toast.LENGTH_SHORT).show();
                                    Intent intentLogin = new Intent(BookDetailsActivity.this, LoginActivity.class);
                                    intentLogin.putExtra("isFromDetail", true);
                                    startActivity(intentLogin);
                                }
                            });

                            viewBookDetail.llReadBook.setOnClickListener(v -> {
                                if (checkRentSubsPlanStatus()) {
                                    openBook();
                                } else {
                                    showPaidMsgDialog();
                                }
                            });

                            viewBookDetail.llDownload.setOnClickListener(v -> {
                                if (bookDetailListPos.getDownload_enable().equals("1")) {
                                    //downloadPermission();
                                    Toast.makeText(BookDetailsActivity.this, getString(R.string.download_disable_msg), Toast.LENGTH_SHORT).show();
                                } else {
                                    Toast.makeText(BookDetailsActivity.this, getString(R.string.download_disable_msg), Toast.LENGTH_SHORT).show();
                                }

                            });

                            if (bookDetailListPos.isFavourite()) {
                                viewBookDetail.ivFav.setImageResource(R.drawable.img_fav_hover);
                            } else {
                                viewBookDetail.ivFav.setImageResource(R.drawable.img_fav);
                            }

                            viewBookDetail.llFavourite.setOnClickListener(v -> {
                                if (method.getIsLogin()) {
                                    FavouriteIF favouriteIF = (isFavourite, message) -> {
                                        if (isFavourite.equals("true")) {
                                            viewBookDetail.ivFav.setImageResource(R.drawable.img_fav_hover);
                                        } else {
                                            viewBookDetail.ivFav.setImageResource(R.drawable.img_fav);
                                        }
                                    };
                                    method.addToFav(bookDetailListPos.getPost_id(), method.getUserId(), "Book", favouriteIF);
                                } else {
                                    Toast.makeText(BookDetailsActivity.this, getString(R.string.login_require), Toast.LENGTH_SHORT).show();
                                    Intent intentLogin = new Intent(BookDetailsActivity.this, LoginActivity.class);
                                    intentLogin.putExtra("isFromDetail", true);
                                    startActivity(intentLogin);
                                }
                            });

                        } else {
                            viewBookDetail.llNoData.clNoDataFound.setVisibility(View.VISIBLE);
                            viewBookDetail.nsView.setVisibility(View.GONE);
                            viewBookDetail.progressHome.setVisibility(View.GONE);
                        }
                    } else {
                        viewBookDetail.llNoData.clNoDataFound.setVisibility(View.VISIBLE);
                        viewBookDetail.nsView.setVisibility(View.GONE);
                        viewBookDetail.progressHome.setVisibility(View.GONE);
                        method.alertBox(getResources().getString(R.string.failed_try_again));
                    }

                } catch (Exception e) {
                    Log.d("exception_error", e.toString());
                    method.alertBox(getResources().getString(R.string.failed_try_again));
                }
                viewBookDetail.progressHome.setVisibility(View.GONE);
            }

            @Override
            public void onFailure(@NotNull Call<BookDetailRP> call, @NotNull Throwable t) {
                // Log error here since request failed
                Log.e("fail", t.toString());
                viewBookDetail.llNoData.clNoDataFound.setVisibility(View.VISIBLE);
                viewBookDetail.progressHome.setVisibility(View.GONE);
                method.alertBox(getResources().getString(R.string.failed_try_again));
            }
        });
    }

    private void downloadPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Dexter.withContext(BookDetailsActivity.this)
                    .withPermission(Manifest.permission.POST_NOTIFICATIONS)
                    .withListener(new PermissionListener() {
                        @Override
                        public void onPermissionGranted(PermissionGrantedResponse response) {
                            if (method.getIsLogin()) {
                                if (checkRentSubsPlanStatus()) {
                                    if (method.isNetworkAvailable()) {
                                        if (bookDetailListPos.getPost_file_url().contains(".epub")) {
                                            method.download(bookDetailListPos.getPost_id(),
                                                    bookDetailListPos.getPost_title(),
                                                    bookDetailListPos.getPost_image(),
                                                    bookDetailListPos.getListBookDetailAuthor().isEmpty() ? "" : bookDetailListPos.getListBookDetailAuthor().get(0).getAuthor_name(),
                                                    bookDetailListPos.getPost_file_url(), "epub");
                                        } else {
                                            method.download(bookDetailListPos.getPost_id(),
                                                    bookDetailListPos.getPost_title(),
                                                    bookDetailListPos.getPost_image(),
                                                    bookDetailListPos.getListBookDetailAuthor().isEmpty() ? "" : bookDetailListPos.getListBookDetailAuthor().get(0).getAuthor_name(),
                                                    bookDetailListPos.getPost_file_url(), "pdf");
                                        }
                                    }
                                } else {
                                    showDownloadPaidMsgDialog();
                                }
                            } else {
                                Toast.makeText(BookDetailsActivity.this, getString(R.string.login_require), Toast.LENGTH_SHORT).show();
                                Intent intentLogin = new Intent(BookDetailsActivity.this, LoginActivity.class);
                                intentLogin.putExtra("isFromDetail", true);
                                startActivity(intentLogin);
                            }
                        }

                        @Override
                        public void onPermissionDenied(PermissionDeniedResponse response) {
                            // check for permanent denial of permission
                            showSettingsDialog(BookDetailsActivity.this);
                        }

                        @Override
                        public void onPermissionRationaleShouldBeShown(PermissionRequest permission, PermissionToken token) {
                            token.continuePermissionRequest();
                        }
                    }).check();

        } else {
            if (method.getIsLogin()) {
                if (checkRentSubsPlanStatus()) {
                    if (method.isNetworkAvailable()) {
                        if (bookDetailListPos.getPost_file_url().contains(".epub")) {
                            method.download(bookDetailListPos.getPost_id(),
                                    bookDetailListPos.getPost_title(),
                                    bookDetailListPos.getPost_image(),
                                    bookDetailListPos.getListBookDetailAuthor().isEmpty() ? "" : bookDetailListPos.getListBookDetailAuthor().get(0).getAuthor_name(),
                                    bookDetailListPos.getPost_file_url(), "epub");
                        } else {
                            method.download(bookDetailListPos.getPost_id(),
                                    bookDetailListPos.getPost_title(),
                                    bookDetailListPos.getPost_image(),
                                    bookDetailListPos.getListBookDetailAuthor().isEmpty() ? "" : bookDetailListPos.getListBookDetailAuthor().get(0).getAuthor_name(),
                                    bookDetailListPos.getPost_file_url(), "pdf");
                        }
                    }
                } else {
                    showDownloadPaidMsgDialog();
                }
            } else {
                Toast.makeText(BookDetailsActivity.this, getString(R.string.login_require), Toast.LENGTH_SHORT).show();
                Intent intentLogin = new Intent(BookDetailsActivity.this, LoginActivity.class);
                intentLogin.putExtra("isFromDetail", true);
                startActivity(intentLogin);
            }
        }
    }

    private static void showSettingsDialog(Activity activity) {
        AlertDialog.Builder builder = new AlertDialog.Builder(activity);
        builder.setTitle("Get Notified");
        builder.setMessage("Enable Notifications to show download notification");
        builder.setPositiveButton("GOTO SETTINGS", (dialog, which) -> {
            dialog.cancel();
            Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            Uri uri = Uri.fromParts("package", activity.getPackageName(), null);
            intent.setData(uri);
            activity.startActivity(intent);
        });
        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.cancel());
        builder.show();
    }

    private boolean checkRentSubsPlanStatus() {
        boolean isStatus;
        if (bookDetailListPos.getBook_on_rent().equals("1")) {
            isStatus = bookDetailListPos.isBook_purchased();
        } else {
            if (bookDetailListPos.getPost_access().equals("Paid")) {
                isStatus = bookDetailListPos.isUser_plan_status();
            } else {//free
                isStatus = true;
            }
        }


        return isStatus;
    }

    private void bookViewData(String bookId) {
        JsonObject jsObj = (JsonObject) new Gson().toJsonTree(new API(BookDetailsActivity.this));
        jsObj.addProperty("post_id", bookId);
        jsObj.addProperty("post_type", "Book");
        ApiInterface apiService = ApiClient.getClient().create(ApiInterface.class);
        Call<JsonObject> call = apiService.getPostViewData(API.toBase64(jsObj.toString()));
        call.enqueue(new Callback<JsonObject>() {
            @Override
            public void onResponse(@NotNull Call<JsonObject> call, @NotNull Response<JsonObject> response) {
            }

            @Override
            public void onFailure(@NotNull Call<JsonObject> call, @NotNull Throwable t) {
                // Log error here since request failed
                Log.e("fail", t.toString());
            }
        });
    }

     private void openBook() {
        if (bookDetailListPos.getContinue_page_num().isEmpty()) {
            if (bookDetailListPos.getPost_file_url().contains(".epub")) {
                DownloadEpub downloadEpub = new DownloadEpub(BookDetailsActivity.this);
                downloadEpub.pathEpub(bookDetailListPos.getPost_file_url(), bookDetailListPos.getPost_id(), "");

            } else {
                startActivity(new Intent(BookDetailsActivity.this, PDFShow.class)
                        .putExtra("id", bookDetailListPos.getPost_id())
                        .putExtra("link", bookDetailListPos.getPost_file_url())
                        .putExtra("toolbarTitle", bookDetailListPos.getPost_title())
                        .putExtra("type", "link")
                        .putExtra("PAGE_NUM", ""));
            }
        } else {
            if (bookDetailListPos.getPost_file_url().contains(".epub")) {
                DownloadEpub downloadEpub = new DownloadEpub(BookDetailsActivity.this);
                downloadEpub.pathEpub(bookDetailListPos.getPost_file_url(), bookDetailListPos.getPost_id(), bookDetailListPos.getContinue_page_num());

            } else {
                startActivity(new Intent(BookDetailsActivity.this, PDFShow.class)
                        .putExtra("id", bookDetailListPos.getPost_id())
                        .putExtra("link", bookDetailListPos.getPost_file_url())
                        .putExtra("toolbarTitle", bookDetailListPos.getPost_title())
                        .putExtra("type", "link")
                        .putExtra("PAGE_NUM", bookDetailListPos.getContinue_page_num()));
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        GlobalBus.getBus().unregister(this);
    }

    @Subscribe
    public void onEvent(Events.UpdatePages updatePages) {
        if (method.getIsLogin()) {
            bookDetailListPos.setContinue_page_num(updatePages.getNumPages());
        }
    }

    public void showPaidMsgDialog() {
        Dialog dialog = new Dialog(BookDetailsActivity.this,R.style.Theme_AppCompat_Translucent);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_rent_msg);
        dialog.setCancelable(false);
        if (method.isRtl()) {
            dialog.getWindow().getDecorView().setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        }
        dialog.getWindow().setLayout(ConstraintLayout.LayoutParams.MATCH_PARENT, ConstraintLayout.LayoutParams.MATCH_PARENT);
        TextView tvMsg=dialog.findViewById(R.id.txtPaidMsg);
        AppCompatButton btnBuy=dialog.findViewById(R.id.btnPaid);
        ImageView ivClose = dialog.findViewById(R.id.ivCloseReport);

        ivClose.setOnClickListener(v -> dialog.dismiss());

        if (bookDetailListPos.getBook_on_rent().equals("1")) {
            tvMsg.setText(getString(R.string.rent_book_msg));
            btnBuy.setText(getString(R.string.lbl_buy_book));
        } else {
            tvMsg.setText(getString(R.string.subs_book_msg));
            btnBuy.setText(getString(R.string.lbl_buy_plan));
       }

        btnBuy.setOnClickListener(v -> {
            dialog.dismiss();
            if (method.getIsLogin()) {
                if (bookDetailListPos.getBook_on_rent().equals("1")) {
                    Intent intentRent = new Intent(BookDetailsActivity.this, PaymentMethodActivity.class);
                    intentRent.putExtra("planId", bookDetailListPos.getPost_id());
                    intentRent.putExtra("planName", bookDetailListPos.getPost_title());
                    intentRent.putExtra("planPrice", bookDetailListPos.getBook_rent_price());
                    intentRent.putExtra("planPriceCurrency", Constant.constantCurrency);
                    intentRent.putExtra("planUserId", method.getUserId());
                    intentRent.putExtra("planDuration", bookDetailListPos.getBook_rent_time());
                    intentRent.putExtra("isRent", true);
                    startActivity(intentRent);
                } else {
                    Intent intentPlan = new Intent(BookDetailsActivity.this, PlanListActivity.class);
                    startActivity(intentPlan);
                }
            } else {
                Toast.makeText(BookDetailsActivity.this, getString(R.string.login_require), Toast.LENGTH_SHORT).show();
                Intent intentLogin = new Intent(BookDetailsActivity.this, LoginActivity.class);
                intentLogin.putExtra("isFromDetail", true);
                startActivity(intentLogin);
            }
        });

        dialog.show();
    }

    public void showDownloadPaidMsgDialog() {
        Dialog dialog = new Dialog(BookDetailsActivity.this,R.style.Theme_AppCompat_Translucent);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_rent_msg);
        dialog.setCancelable(false);
        if (method.isRtl()) {
            dialog.getWindow().getDecorView().setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        }
        dialog.getWindow().setLayout(ConstraintLayout.LayoutParams.MATCH_PARENT, ConstraintLayout.LayoutParams.MATCH_PARENT);
        TextView tvMsg=dialog.findViewById(R.id.txtPaidMsg);
        AppCompatButton btnBuy=dialog.findViewById(R.id.btnPaid);
        ImageView ivClose = dialog.findViewById(R.id.ivCloseReport);

        ivClose.setOnClickListener(v -> dialog.dismiss());

        if (bookDetailListPos.getBook_on_rent().equals("1")) {
            tvMsg.setText(getString(R.string.rent_download_book_msg));
            btnBuy.setText(getString(R.string.lbl_buy_book));
        } else {
            tvMsg.setText(getString(R.string.subs_download_book_msg));
            btnBuy.setText(getString(R.string.lbl_buy_plan));
        }

        btnBuy.setOnClickListener(v -> {
            dialog.dismiss();
            if (method.getIsLogin()) {
                if (bookDetailListPos.getBook_on_rent().equals("1")) {
                    Intent intentRent = new Intent(BookDetailsActivity.this, PaymentMethodActivity.class);
                    intentRent.putExtra("planId", bookDetailListPos.getPost_id());
                    intentRent.putExtra("planName", bookDetailListPos.getPost_title());
                    intentRent.putExtra("planPrice", bookDetailListPos.getBook_rent_price());
                    intentRent.putExtra("planPriceCurrency", Constant.constantCurrency);
                    intentRent.putExtra("planUserId", method.getUserId());
                    intentRent.putExtra("planDuration", bookDetailListPos.getBook_rent_time());
                    intentRent.putExtra("isRent", true);
                    startActivity(intentRent);
                } else {
                    Intent intentPlan = new Intent(BookDetailsActivity.this, PlanListActivity.class);
                    startActivity(intentPlan);
                }
            } else {
                Toast.makeText(BookDetailsActivity.this, getString(R.string.login_require), Toast.LENGTH_SHORT).show();
                Intent intentLogin = new Intent(BookDetailsActivity.this, LoginActivity.class);
                intentLogin.putExtra("isFromDetail", true);
                startActivity(intentLogin);
            }
        });

        dialog.show();
    }
}
