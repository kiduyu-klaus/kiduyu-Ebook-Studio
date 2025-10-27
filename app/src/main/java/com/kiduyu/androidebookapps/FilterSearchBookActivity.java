package com.kiduyu.androidebookapps;

import android.content.Intent;
import android.graphics.PorterDuff;
import android.os.Bundle;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.FragmentManager;

import com.example.androidebookapps.databinding.ActivitySearchHomeBookBinding;
import com.kiduyu.fragment.BookGridFragment;
import com.kiduyu.fragment.BookListFragment;
import com.kiduyu.util.BannerAds;
import com.kiduyu.util.Method;
import com.kiduyu.util.StatusBar;


public class FilterSearchBookActivity extends AppCompatActivity {

    ActivitySearchHomeBookBinding viewBookListSearch;
    String strValue, strFrom;
    Method method;
    FragmentManager fragmentManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        StatusBar.initWhite(FilterSearchBookActivity.this);
        viewBookListSearch = ActivitySearchHomeBookBinding.inflate(getLayoutInflater());
        setContentView(viewBookListSearch.getRoot());

        method = new Method(FilterSearchBookActivity.this);
        method.forceRTLIfSupported();
        fragmentManager = getSupportFragmentManager();

        Intent intent = getIntent();
        strValue = intent.getStringExtra("searchValue");
        strFrom = intent.getStringExtra("isFrom");

        viewBookListSearch.toolbarMain.tvToolbarTitle.setText(getString(R.string.search_title));
        viewBookListSearch.toolbarMain.ivSearch.setVisibility(View.GONE);
        viewBookListSearch.toolbarMain.imageFilter.setVisibility(View.GONE);
        viewBookListSearch.toolbarMain.imageArrowBack.setOnClickListener(v -> getOnBackPressedDispatcher().onBackPressed());

        viewBookListSearch.ivViewGrid.setOnClickListener(v -> {

            viewBookListSearch.ivViewList.setColorFilter(getResources().getColor(R.color.icon_view_normal), PorterDuff.Mode.SRC_IN);
            viewBookListSearch.ivViewGrid.setColorFilter(getResources().getColor(R.color.icon_view_select), PorterDuff.Mode.SRC_IN);

            if (method.isNetworkAvailable()) {
                goGrid();
            } else {
                method.alertBox(getResources().getString(R.string.internet_connection));
            }
        });

        viewBookListSearch.ivViewList.setOnClickListener(v -> {

            viewBookListSearch.ivViewList.setColorFilter(getResources().getColor(R.color.icon_view_select), PorterDuff.Mode.SRC_IN);
            viewBookListSearch.ivViewGrid.setColorFilter(getResources().getColor(R.color.icon_view_normal), PorterDuff.Mode.SRC_IN);
            if (method.isNetworkAvailable()) {
                goList();
            } else {
                method.alertBox(getResources().getString(R.string.internet_connection));
            }
        });

        if (method.isNetworkAvailable()) {
            goGrid();
        } else {
            method.alertBox(getResources().getString(R.string.internet_connection));
        }

        BannerAds.showBannerAds(FilterSearchBookActivity.this, viewBookListSearch.layoutAds);
    }

    private void goGrid() {
        Bundle bundle = new Bundle();
        bundle.putString("postSubCatId", strValue);
        bundle.putString("postSubCatName", strValue);
        bundle.putString("type", strFrom);
        BookGridFragment bookGridFragment = new BookGridFragment();
        bookGridFragment.setArguments(bundle);
        getSupportFragmentManager().beginTransaction().add(R.id.frameMain, bookGridFragment, "")
                .commitAllowingStateLoss();
    }

    private void goList() {
        Bundle bundle = new Bundle();
        bundle.putString("postSubCatId", strValue);
        bundle.putString("postSubCatName", strValue);
        bundle.putString("type", strFrom);
        BookListFragment bookListFragment = new BookListFragment();
        bookListFragment.setArguments(bundle);
        getSupportFragmentManager().beginTransaction().add(R.id.frameMain, bookListFragment, "")
                .commitAllowingStateLoss();
    }
}
