package app.topjournal.client;

import android.Manifest;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DownloadManager;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.pdf.PdfRenderer;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.provider.Settings;
import android.text.InputFilter;
import android.text.InputType;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.Window;
import android.view.WindowManager;
import android.webkit.CookieManager;
import android.webkit.DownloadListener;
import android.webkit.SslErrorHandler;
import android.webkit.URLUtil;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebResourceError;
import android.webkit.RenderProcessGoneDetail;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.window.OnBackInvokedDispatcher;
import android.widget.CompoundButton;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RatingBar;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.webkit.WebViewCompat;
import androidx.webkit.WebViewFeature;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URI;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;

public final class MainActivity extends Activity {
    static final String CHANNEL_REMINDERS = "journal_reminders";
    private static final String CHANNEL_EVENTS = "journal_events";
    private static final String BASE_URL = "https://journal.top-academy.ru/";
    private static final String PREFS = "top_journal_preferences";
    private static final int FILE_CHOOSER_REQUEST = 301;
    private static final int NOTIFICATION_PERMISSION_REQUEST = 302;
    private static final int HEADER_HEIGHT_DP = 64;
    private static final int MAX_AVATAR_BYTES = 5 * 1024 * 1024;
    private static final long BACKGROUND_LOGIN_WINDOW_MS = 7000;

    // Vercel palette from 21st.dev, with restrained status/chart accents.
    private static final int COLOR_BLACK = Color.rgb(0, 0, 0);
    private static final int COLOR_MUTED = Color.rgb(102, 102, 102);
    private static final int COLOR_BACKGROUND = Color.rgb(252, 252, 252);
    private static final int COLOR_CARD = Color.rgb(255, 255, 255);
    private static final int COLOR_SURFACE = Color.rgb(247, 247, 247);
    private static final int COLOR_ACCENT = Color.rgb(240, 240, 240);
    private static final int COLOR_BORDER = Color.rgb(229, 229, 229);
    private static final int COLOR_DANGER = Color.rgb(229, 72, 77);
    private static final int COLOR_SUCCESS = Color.rgb(22, 163, 74);
    private static final int COLOR_CHART_ORANGE = Color.rgb(245, 166, 35);
    private static final int COLOR_CHART_BLUE = Color.rgb(79, 70, 229);

    private String apiCaptureJs;
    private String snapshotJs;
    private SharedPreferences screenPreferences;
    private static final List<String> NATIVE_ROUTES = Arrays.asList("home", "schedule", "assistant", "grades", "tasks", "materials", "announcements", "rewards", "reviews");
    private int taskStatus = 3, taskPage = 1, materialType = 2, materialRecommended = 0;
    private int selectedScheduleDay;
    private long expandedNews = -1;
    private int rewardFilter = 0;
    private boolean automaticLoginUsed;
    private long unverifiedSince = android.os.SystemClock.elapsedRealtime();
    private boolean shellVisible = true;
    private boolean foreground;
    private boolean connectionUnavailable;
    private boolean hybridLoading;
    private boolean quietRender;
    private boolean aiLoading;
    private boolean homeworkAiLoading;
    private String aiError = "";
    private int sessionGeneration;
    private int requestSequence;
    private final Map<String, String> requestVariants = new LinkedHashMap<>();
    private final Map<String, Integer> nativeRequests = new LinkedHashMap<>();
    private final Map<String, String> refreshMessages = new LinkedHashMap<>();
    private final Map<String, String> renderedSignatures = new LinkedHashMap<>();
    private final Map<TextView, SkeletonView> headerSkeletons = new LinkedHashMap<>();
    private TextView refreshStatus;
    private final Runnable sessionObserver = new Runnable() {
        @Override public void run() {
            if (!foreground || webView == null) return;
            updateChromeForCurrentPage();
            if (signedIn) {
                syncNativeHeader();
                syncNativeRoute();
            }
            handler.postDelayed(this, 1500);
        }
    };

    private WebView webView;
    private FrameLayout rootView;
    private ScrollView nativeHomeView;
    private LinearLayout nativeHomeContent;
    private ProgressBar progressBar;
    private TextView pageTitle;
    private TextView scoreValue;
    private TextView topCoinsValue;
    private TextView gemsValue;
    private TextView avatarFallback;
    private ImageView avatarImage;
    private View toolbarView;
    private View bottomNavigationView;
    private FrameLayout.LayoutParams webViewLayoutParams;
    private ValueCallback<Uri[]> fileChooserCallback;
    private SharedPreferences preferences;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Map<String, NavItem> navItems = new LinkedHashMap<>();
    private final Map<String, JSONObject> nativeDataCache = new LinkedHashMap<>();
    private String activeSection = "home";
    private String requestedRoute = "home";
    private String loadedAvatarUrl = "";
    private String avatarRequestUrl = "";
    private long avatarRetryAt;
    private String nativeScreenRoute = "";
    private String confirmedRoute = "home";
    private String navigationRoute = "";
    private boolean signedIn;
    private String currentSessionState = "pending";
    private boolean navigationInFlight;
    private boolean recoveringFromHttpError;
    private boolean initialNativeDataRequested;
    private int navigationAttempt;
    private int navigationGeneration;
    private int statusBarInset;
    private int navigationBarInset;
    private long backgroundLoginSince;
    private boolean backgroundLoginSubmitted;
    private final long activityStartedAt = System.currentTimeMillis();
    private AlertDialog lessonEvaluationDialog;
    private boolean evaluationSubmitting;
    private boolean evaluationsDeferredForSession;
    private int evaluationRequestSequence;

    private static final class NavItem {
        final LinearLayout view;
        final ImageView icon;
        final TextView label;

        NavItem(LinearLayout view, ImageView icon, TextView label) {
            this.view = view;
            this.icon = icon;
            this.label = label;
        }
    }

    private static final class LessonEvaluationDraft {
        final JSONObject lesson;
        final boolean schoolBranch;
        int teacherRating;
        int lessonRating;
        String teacherComment = "";
        String lessonComment = "";

        LessonEvaluationDraft(JSONObject lesson, boolean schoolBranch) {
            this.lesson = lesson;
            this.schoolBranch = schoolBranch;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        preferences = getSharedPreferences(PREFS, MODE_PRIVATE);
        // v0.18 used a headless WebView JobService. Cancel its persisted job before
        // creating the UI so an OEM WebView crash cannot relaunch in a loop.
        BackgroundSyncScheduler.cancel(this);
        if (preferences.getInt("design_revision", 0) < 13) {
            preferences.edit()
                    .putInt("design_revision", 13)
                    .putBoolean("original_mode", false)
                    .apply();
        }
        SharedPreferences.Editor defaults = preferences.edit();
        if (!preferences.contains("daily_reminder")) defaults.putBoolean("daily_reminder", true);
        defaults.putBoolean("background_sync", false);
        if (!preferences.contains("new_grade_notifications")) defaults.putBoolean("new_grade_notifications", true);
        defaults.apply();
        String launchRoute = getIntent().getStringExtra("open_route");
        if (launchRoute != null && NATIVE_ROUTES.contains(launchRoute)) {
            requestedRoute = launchRoute;
            activeSection = navigationTab(launchRoute);
        }
        screenPreferences = getSharedPreferences("native_screens_v12", MODE_PRIVATE);
        apiCaptureJs = readAsset("journal-bridge.js");
        snapshotJs = readAsset("journal-snapshot.js");
        restoreNativeCache();
        shellVisible = true;
        configureSystemBars();
        configureBackNavigation();
        createNotificationChannels();
        requestNotificationPermissionIfNeeded();
        if (preferences.getBoolean("daily_reminder", true)) ReminderScheduler.schedule(this);
        buildInterface();
        configureWebView();
        toolbarView.setVisibility(shellVisible ? View.VISIBLE : View.GONE);
        bottomNavigationView.setVisibility(shellVisible ? View.VISIBLE : View.GONE);
        if (shellVisible) showCachedNativeData(requestedRoute);
        updateNavigation(navigationTab(requestedRoute));
        updateNativeHomeVisibility();
        webView.loadUrl(BASE_URL);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        String route = intent.getStringExtra("open_route");
        if (route != null && NATIVE_ROUTES.contains(route)) navigateByKey(route);
    }

    private void configureSystemBars() {
        Window window = getWindow();
        window.setStatusBarColor(COLOR_BACKGROUND);
        window.setNavigationBarColor(COLOR_BACKGROUND);
        int flags = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
        flags |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
        window.getDecorView().setSystemUiVisibility(flags);
    }

    private void configureBackNavigation() {
        if (Build.VERSION.SDK_INT >= 33) {
            getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                    OnBackInvokedDispatcher.PRIORITY_DEFAULT,
                    this::handleBack
            );
        }
    }

    private void buildInterface() {
        rootView = new FrameLayout(this);
        rootView.setBackgroundColor(COLOR_BACKGROUND);

        webView = new WebView(this);
        webView.setBackgroundColor(COLOR_BACKGROUND);
        webView.setOverScrollMode(View.OVER_SCROLL_NEVER);
        webView.setPadding(0, 0, 0, dp(84));
        webView.setClipToPadding(false);
        webView.setSaveEnabled(true);
        webView.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_YES);
        webViewLayoutParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        );
        webViewLayoutParams.topMargin = dp(HEADER_HEIGHT_DP);
        rootView.addView(webView, webViewLayoutParams);

        nativeHomeView = new ScrollView(this);
        nativeHomeView.setFillViewport(true);
        nativeHomeView.setBackgroundColor(COLOR_BACKGROUND);
        nativeHomeView.setOverScrollMode(View.OVER_SCROLL_NEVER);
        nativeHomeView.setVisibility(View.GONE);
        nativeHomeContent = new LinearLayout(this);
        nativeHomeContent.setOrientation(LinearLayout.VERTICAL);
        nativeHomeContent.setPadding(dp(16), dp(22), dp(16), dp(104));
        nativeHomeView.addView(nativeHomeContent, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        FrameLayout.LayoutParams nativeHomeParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        );
        nativeHomeParams.topMargin = dp(HEADER_HEIGHT_DP);
        rootView.addView(nativeHomeView, nativeHomeParams);

        toolbarView = createToolbar();
        bottomNavigationView = createBottomNavigation();
        toolbarView.setVisibility(View.INVISIBLE);
        bottomNavigationView.setVisibility(View.INVISIBLE);
        rootView.addView(toolbarView);
        rootView.addView(bottomNavigationView);
        rootView.setOnApplyWindowInsetsListener((view, insets) -> {
            applySystemInsets(insets);
            return insets;
        });
        setContentView(rootView);
        rootView.requestApplyInsets();
    }

    @SuppressWarnings("deprecation")
    private void applySystemInsets(WindowInsets insets) {
        if (Build.VERSION.SDK_INT >= 30) {
            statusBarInset = insets.getInsets(WindowInsets.Type.statusBars()).top;
            navigationBarInset = insets.getInsets(WindowInsets.Type.navigationBars()).bottom;
        } else {
            statusBarInset = insets.getSystemWindowInsetTop();
            navigationBarInset = insets.getSystemWindowInsetBottom();
        }

        if (toolbarView != null) {
            FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) toolbarView.getLayoutParams();
            params.height = dp(HEADER_HEIGHT_DP) + statusBarInset;
            toolbarView.setLayoutParams(params);
            toolbarView.setPadding(0, statusBarInset, 0, 0);
        }
        int top = shellVisible ? dp(HEADER_HEIGHT_DP) + statusBarInset : 0;
        if (webViewLayoutParams != null) {
            webViewLayoutParams.topMargin = top;
            webView.setLayoutParams(webViewLayoutParams);
        }
        if (nativeHomeView != null) {
            FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) nativeHomeView.getLayoutParams();
            params.topMargin = dp(HEADER_HEIGHT_DP) + statusBarInset;
            nativeHomeView.setLayoutParams(params);
            nativeHomeView.setPadding(0, 0, 0, navigationBarInset);
        }
        if (bottomNavigationView != null) {
            FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) bottomNavigationView.getLayoutParams();
            params.bottomMargin = navigationBarInset;
            bottomNavigationView.setLayoutParams(params);
        }
        if (webView != null) {
            webView.setPadding(0, 0, 0, shellVisible ? dp(76) + navigationBarInset : 0);
        }
    }

    private View createToolbar() {
        FrameLayout toolbar = new FrameLayout(this);
        toolbar.setBackgroundColor(COLOR_BACKGROUND);
        toolbar.setElevation(0);
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(HEADER_HEIGHT_DP),
                Gravity.TOP
        );
        toolbar.setLayoutParams(params);

        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(12), 0, dp(10), 0);
        toolbar.addView(row, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(HEADER_HEIGHT_DP), Gravity.TOP));

        FrameLayout avatar = new FrameLayout(this);
        avatar.setBackgroundResource(R.drawable.bg_avatar);
        avatar.setClipToOutline(true);
        avatar.setOnClickListener(v -> navigateByKey("profile"));
        avatar.setContentDescription("Профиль");

        avatarImage = new ImageView(this);
        avatarImage.setScaleType(ImageView.ScaleType.CENTER_CROP);
        avatarImage.setVisibility(View.INVISIBLE);
        avatar.addView(avatarImage, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        avatarFallback = new TextView(this);
        avatarFallback.setText("Д");
        avatarFallback.setTextColor(Color.WHITE);
        avatarFallback.setTextSize(13);
        avatarFallback.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        avatarFallback.setGravity(Gravity.CENTER);
        avatar.addView(avatarFallback, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        LinearLayout.LayoutParams avatarParams = new LinearLayout.LayoutParams(dp(36), dp(36));
        avatarParams.rightMargin = dp(9);
        row.addView(avatar, avatarParams);

        ImageView logo = new ImageView(this);
        logo.setImageResource(R.drawable.logo_top_official);
        logo.setColorFilter(COLOR_BLACK);
        logo.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        logo.setContentDescription("Официальный логотип дневника");
        LinearLayout.LayoutParams logoParams = new LinearLayout.LayoutParams(dp(70), dp(34));
        logoParams.rightMargin = dp(3);
        row.addView(logo, logoParams);

        pageTitle = text("", 1, Color.TRANSPARENT, Typeface.NORMAL);
        pageTitle.setVisibility(View.GONE);
        View brandSpacer = new View(this);
        row.addView(brandSpacer, new LinearLayout.LayoutParams(0, dp(1), 1f));

        scoreValue = addHeaderGameMetric(row, R.drawable.ic_points, cachedHeader("score"), "Баллы");
        topCoinsValue = addHeaderGameMetric(row, R.drawable.ic_coin, cachedHeader("coins"), "Топкоины");
        gemsValue = addHeaderGameMetric(row, R.drawable.ic_gem, cachedHeader("gems"), "Гемы");

        ImageView bell = new ImageView(this);
        bell.setImageResource(R.drawable.ic_bell);
        bell.setColorFilter(COLOR_BLACK);
        bell.setPadding(dp(9), dp(9), dp(9), dp(9));
        bell.setBackgroundColor(Color.TRANSPARENT);
        bell.setContentDescription("Уведомления");
        bell.setOnClickListener(v -> navigateByKey("announcements"));
        row.addView(bell, new LinearLayout.LayoutParams(dp(36), dp(36)));

        progressBar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progressBar.setMax(100);
        progressBar.setProgressTintList(ColorStateList.valueOf(COLOR_BLACK));
        progressBar.setProgressBackgroundTintList(ColorStateList.valueOf(Color.TRANSPARENT));
        FrameLayout.LayoutParams progressParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(2),
                Gravity.BOTTOM
        );
        toolbar.addView(progressBar, progressParams);
        return toolbar;
    }

    private TextView addHeaderGameMetric(LinearLayout parent, int iconRes, String initialValue, String label) {
        LinearLayout metric = new LinearLayout(this);
        metric.setOrientation(LinearLayout.HORIZONTAL);
        metric.setGravity(Gravity.CENTER);
        metric.setPadding(dp(3), dp(5), dp(3), dp(5));
        metric.setContentDescription(label + ": " + initialValue);

        ImageView icon = new ImageView(this);
        icon.setImageResource(iconRes);
        metric.addView(icon, new LinearLayout.LayoutParams(dp(17), dp(17)));

        TextView value = monoText(initialValue, 10, COLOR_BLACK, Typeface.BOLD);
        value.setGravity(Gravity.CENTER);
        value.setIncludeFontPadding(false);
        value.setMaxLines(1);
        value.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams valueParams = new LinearLayout.LayoutParams(dp(25), dp(20));
        valueParams.leftMargin = dp(1);
        metric.addView(value, valueParams);
        parent.addView(metric, new LinearLayout.LayoutParams(dp(45), dp(38)));
        return value;
    }

    private String cachedHeader(String key) {
        long age = System.currentTimeMillis() - screenPreferences.getLong("header_time", 0);
        return age >= 0 && age <= NativeSnapshot.MAX_AGE_MS ? screenPreferences.getString("header_" + key, "—") : "—";
    }

    private void addHeaderSeparator(LinearLayout parent) {
        View separator = new View(this);
        separator.setBackgroundColor(Color.rgb(192, 192, 192));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(1), dp(24));
        parent.addView(separator, params);
    }

    private View createBottomNavigation() {
        FrameLayout shell = new FrameLayout(this);
        shell.setBackgroundResource(R.drawable.bg_nav);
        shell.setElevation(dp(8));
        FrameLayout.LayoutParams shellParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(72),
                Gravity.BOTTOM
        );
        shellParams.leftMargin = 0;
        shellParams.rightMargin = 0;
        shellParams.bottomMargin = 0;
        shell.setLayoutParams(shellParams);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER);
        shell.addView(row, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));

        addNavItem(row, "home", "Главная", R.drawable.ic_home);
        addNavItem(row, "schedule", "Расписание", R.drawable.ic_calendar);
        addNavItem(row, "assistant", "AI", R.drawable.ic_ai);
        addNavItem(row, "grades", "Оценки", R.drawable.ic_grades);
        addNavItem(row, "tasks", "Задания", R.drawable.ic_tasks);
        addNavItem(row, "more", "Ещё", R.drawable.ic_more);
        updateNavigation("home");
        return shell;
    }

    private void addNavItem(LinearLayout parent, String key, String labelText, int iconRes) {
        LinearLayout item = new LinearLayout(this);
        item.setOrientation(LinearLayout.VERTICAL);
        item.setGravity(Gravity.CENTER);
        item.setPadding(dp(4), dp(8), dp(4), dp(6));
        item.setBackgroundResource(R.drawable.bg_nav_inactive);
        item.setContentDescription(labelText);

        ImageView icon = new ImageView(this);
        icon.setImageResource(iconRes);
        icon.setColorFilter(COLOR_MUTED);
        item.addView(icon, new LinearLayout.LayoutParams(dp(20), dp(20)));

        TextView label = new TextView(this);
        label.setText(labelText);
        label.setTextColor(COLOR_MUTED);
        label.setTextSize(9.5f);
        label.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        label.setGravity(Gravity.CENTER);
        label.setMaxLines(1);
        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        labelParams.topMargin = dp(3);
        item.addView(label, labelParams);

        LinearLayout.LayoutParams itemParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f);
        itemParams.setMargins(dp(2), dp(2), dp(2), dp(2));
        parent.addView(item, itemParams);
        navItems.put(key, new NavItem(item, icon, label));

        item.setOnClickListener(v -> {
            if ("more".equals(key)) {
                updateNavigation("more");
                showMoreDialog();
            } else {
                navigateByKey(key);
            }
        });
    }

    private void updateNavigation(String section) {
        activeSection = section;
        for (Map.Entry<String, NavItem> entry : navItems.entrySet()) {
            boolean active = entry.getKey().equals(section);
            NavItem item = entry.getValue();
            item.view.setBackgroundResource(active ? R.drawable.bg_nav_active : R.drawable.bg_nav_inactive);
            item.icon.setColorFilter(active ? Color.WHITE : COLOR_MUTED);
            item.label.setTextColor(active ? Color.WHITE : COLOR_MUTED);
            item.label.setTypeface(Typeface.DEFAULT, active ? Typeface.BOLD : Typeface.NORMAL);
            item.view.animate().cancel();
            item.view.animate()
                    .scaleX(active ? 1.03f : 1f)
                    .scaleY(active ? 1.03f : 1f)
                    .setDuration(180)
                    .start();
        }
        setPageTitleAnimated(titleForSection(section));
        updateNativeHomeVisibility();
    }

    private void setPageTitleAnimated(String title) {
        if (pageTitle == null) return;
        pageTitle.setText("TOP\nJOURNAL");
        pageTitle.setContentDescription("Top Journal · " + title);
        pageTitle.setAlpha(1f);
        pageTitle.setTranslationY(0f);
    }

    private String titleForSection(String key) {
        switch (key) {
            case "schedule": return "Расписание";
            case "assistant": return "AI-аналитик";
            case "grades": return "Оценки";
            case "tasks": return "Задания";
            case "more": return "Ещё";
            default: return "Главная";
        }
    }

    @SuppressWarnings("SetJavaScriptEnabled")
    private void configureWebView() {
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setSaveFormData(true);
        settings.setAllowFileAccess(false);
        settings.setAllowFileAccessFromFileURLs(false);
        settings.setAllowUniversalAccessFromFileURLs(false);
        settings.setAllowContentAccess(true);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        settings.setBuiltInZoomControls(false);
        settings.setDisplayZoomControls(false);
        settings.setSupportZoom(false);
        settings.setUseWideViewPort(true);
        settings.setLoadWithOverviewMode(false);
        settings.setTextZoom(100);
        settings.setMediaPlaybackRequiresUserGesture(true);
        settings.setSupportMultipleWindows(false);
        settings.setSafeBrowsingEnabled(true);

        CookieManager cookieManager = CookieManager.getInstance();
        cookieManager.setAcceptCookie(true);
        cookieManager.setAcceptThirdPartyCookies(webView, false);

        boolean debuggable = (getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0;
        WebView.setWebContentsDebuggingEnabled(debuggable);

        if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
            WebViewCompat.addDocumentStartJavaScript(
                    webView,
                    apiCaptureJs,
                    Collections.singleton("https://journal.top-academy.ru")
            );
        }

        webView.setWebChromeClient(new JournalChromeClient());
        webView.setWebViewClient(new JournalWebViewClient());
        webView.setDownloadListener(new JournalDownloadListener());
    }

    private final class JournalChromeClient extends WebChromeClient {
        @Override
        public void onProgressChanged(WebView view, int newProgress) {
            progressBar.setProgress(newProgress);
            progressBar.setVisibility(newProgress >= 100 ? View.INVISIBLE : View.VISIBLE);
            if (nativeHomeView.getVisibility() == View.VISIBLE) {
                view.setAlpha(0f);
            } else if (newProgress < 65) {
                view.setAlpha(1f);
            } else if (view.getAlpha() < 1f) {
                view.animate().alpha(1f).setDuration(160).start();
            }
        }

        @Override
        public boolean onShowFileChooser(WebView webView, ValueCallback<Uri[]> callback, FileChooserParams params) {
            if (fileChooserCallback != null) {
                fileChooserCallback.onReceiveValue(null);
            }
            fileChooserCallback = callback;
            Intent intent;
            try {
                intent = params.createIntent();
            } catch (ActivityNotFoundException error) {
                fileChooserCallback = null;
                Toast.makeText(MainActivity.this, "На устройстве нет приложения для выбора файла", Toast.LENGTH_LONG).show();
                return false;
            }
            try {
                startActivityForResult(intent, FILE_CHOOSER_REQUEST);
                return true;
            } catch (ActivityNotFoundException error) {
                fileChooserCallback = null;
                Toast.makeText(MainActivity.this, "Не удалось открыть выбор файла", Toast.LENGTH_LONG).show();
                return false;
            }
        }
    }

    private final class JournalWebViewClient extends WebViewClient {
        @Override
        public void onPageStarted(WebView view, String url, Bitmap favicon) {
            super.onPageStarted(view, url, favicon);
            dismissLessonEvaluation();
            evaluationSubmitting = false;
            sessionGeneration++;
            nativeRequests.clear();
            requestVariants.clear();
            avatarRequestUrl = "";
            initialNativeDataRequested = false;
            signedIn = false;
            connectionUnavailable = false;
            currentSessionState = "pending";
            unverifiedSince = android.os.SystemClock.elapsedRealtime();
            shellVisible = true;
            if (isNativeRoute(requestedRoute)) {
                refreshMessages.put(requestedRoute, "Подключаемся к дневнику…");
                showCachedNativeData(requestedRoute);
            } else showHybridPlaceholder(false);
            updateSessionChrome();
            int generation = sessionGeneration;
            handler.postDelayed(() -> {
                if (generation == sessionGeneration && !signedIn && shellVisible) {
                    shellVisible = true;
                    String message = "pending".equals(currentSessionState) ? "Подключение занимает больше времени…"
                            : "login".equals(currentSessionState) ? "Страница входа не загрузилась · Повторить" : sessionMessage(currentSessionState);
                    for (String route : NATIVE_ROUTES) refreshMessages.put(route, message);
                    if (isNativeRoute(requestedRoute)) showCachedNativeData(requestedRoute);
                    updateSessionChrome();
                    finishHeaderLoading();
                }
            }, 20000);
            installApiCapture();
        }

        @Override
        public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
            if (!request.isForMainFrame()) return false;
            return handleNavigation(request.getUrl());
        }

        @Override
        @SuppressWarnings("deprecation")
        public boolean shouldOverrideUrlLoading(WebView view, String url) {
            return handleNavigation(Uri.parse(url));
        }

        @Override
        public void onPageFinished(WebView view, String url) {
            super.onPageFinished(view, url);
            installApiCapture();
            updateChromeForCurrentPage();
            applyMobileStyle();
            handler.postDelayed(MainActivity.this::updateChromeForCurrentPage, 450);
            handler.postDelayed(MainActivity.this::applyMobileStyle, 700);
            handler.postDelayed(MainActivity.this::applyMobileStyle, 1800);
            handler.postDelayed(MainActivity.this::detectCurrentSection, 900);
            handler.postDelayed(MainActivity.this::syncNativeHeader, 650);
            handler.postDelayed(MainActivity.this::syncNativeHeader, 1900);
            handler.postDelayed(MainActivity.this::syncNativeHome, 750);
            handler.postDelayed(MainActivity.this::syncNativeHome, 2050);
            handler.postDelayed(MainActivity.this::syncNativeHome, 4500);
            handler.postDelayed(MainActivity.this::syncNativeRoute, 850);
            handler.postDelayed(MainActivity.this::syncNativeRoute, 2200);
            handler.postDelayed(MainActivity.this::syncNativeRoute, 5000);
            handler.postDelayed(MainActivity.this::scanVisibleCounters, 2100);
            CookieManager.getInstance().flush();
        }

        @Override
        public void doUpdateVisitedHistory(WebView view, String url, boolean isReload) {
            super.doUpdateVisitedHistory(view, url, isReload);
            handler.postDelayed(MainActivity.this::detectCurrentSection, 120);
            handler.postDelayed(MainActivity.this::applyMobileStyle, 180);
            handler.postDelayed(MainActivity.this::applyMobileStyle, 700);
            handler.postDelayed(MainActivity.this::syncNativeHeader, 420);
            handler.postDelayed(MainActivity.this::syncNativeHome, 760);
            handler.postDelayed(MainActivity.this::syncNativeHome, 1750);
            handler.postDelayed(MainActivity.this::syncNativeRoute, 520);
            handler.postDelayed(MainActivity.this::syncNativeRoute, 1450);
            handler.postDelayed(MainActivity.this::syncNativeRoute, 4200);
        }

        @Override
        public void onReceivedSslError(WebView view, SslErrorHandler handler, android.net.http.SslError error) {
            handler.cancel();
            showConnectionFailure();
            Toast.makeText(MainActivity.this, "Защищённое соединение не прошло проверку", Toast.LENGTH_LONG).show();
        }

        @Override
        public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
            super.onReceivedError(view, request, error);
            if (request.isForMainFrame()) showConnectionFailure();
        }

        @Override
        public void onReceivedHttpError(WebView view, WebResourceRequest request, WebResourceResponse errorResponse) {
            super.onReceivedHttpError(view, request, errorResponse);
            if (request.isForMainFrame() && errorResponse.getStatusCode() >= 400) {
                if (errorResponse.getStatusCode() == 403 && signedIn) {
                    ++navigationGeneration;
                    navigationInFlight = false;
                    navigationRoute = "";
                    requestedRoute = confirmedRoute;
                if (isNativeRoute(requestedRoute)) { hybridLoading = false; showCachedNativeData(requestedRoute); }
                    if (isNativeRoute(confirmedRoute)) updateNavigation(navigationTab(confirmedRoute));
                    else updateNavigation("more");
                    Toast.makeText(MainActivity.this,
                            "Раздел временно не разрешён сервером. Повторного перехода не будет.",
                            Toast.LENGTH_LONG).show();
                    if (!recoveringFromHttpError && view.canGoBack()) {
                        recoveringFromHttpError = true;
                        view.goBack();
                        handler.postDelayed(() -> recoveringFromHttpError = false, 900);
                    }
                } else {
                    showConnectionFailure();
                    Toast.makeText(MainActivity.this, "Сайт временно недоступен: " + errorResponse.getStatusCode(), Toast.LENGTH_LONG).show();
                }
            }
        }

        @Override
        public boolean onRenderProcessGone(WebView view, RenderProcessGoneDetail detail) {
            handler.removeCallbacksAndMessages(null);
            navigationInFlight = false;
            navigationRoute = "";
            if (rootView != null) rootView.removeView(view);
            view.setWebChromeClient(null);
            view.setWebViewClient(null);
            view.destroy();
            webView = null;
            long now = System.currentTimeMillis();
            int crashes = now - preferences.getLong("renderer_crash_time", 0) < 120000
                    ? preferences.getInt("renderer_crashes", 0) + 1 : 1;
            preferences.edit().putLong("renderer_crash_time", now).putInt("renderer_crashes", crashes).apply();
            if (crashes > 2) {
                signedIn = false;
                shellVisible = true;
                requestedRoute = "home";
                refreshMessages.put("home", "Модуль дневника остановлен · Повторить");
                showCachedNativeData("home");
                nativeHomeView.setVisibility(View.VISIBLE);
                nativeHomeView.setAlpha(1f);
                new android.app.AlertDialog.Builder(MainActivity.this)
                        .setTitle("Не удалось запустить дневник")
                        .setMessage("Веб-модуль несколько раз завершился. Сохранённые данные остались в приложении.")
                        .setPositiveButton("Перезапустить", (dialog, which) -> {
                            preferences.edit().remove("renderer_crashes").apply();
                            recreate();
                        }).setNegativeButton("Закрыть", (dialog, which) -> finish()).setCancelable(false).show();
                return true;
            }
            Toast.makeText(MainActivity.this,
                    "Модуль дневника перезапускается после сбоя", Toast.LENGTH_LONG).show();
            handler.postDelayed(MainActivity.this::recreate, 350);
            return true;
        }
    }

    private void updateChromeForCurrentPage() {
        if (webView == null || toolbarView == null) return;
        int generation = sessionGeneration;
        String script = "(function(){if(window.__tjSessionState)return window.__tjSessionState();" +
                "const login=/\\/auth\\//.test(location.pathname)||!!document.querySelector('input[type=password]');" +
                "return JSON.stringify({state:login?'login':'pending',verified:false});})()";
        webView.evaluateJavascript(script, value -> {
            if (generation != sessionGeneration || isDestroyed()) return;
            JSONObject session;
            try { session = new JSONObject(decodeJavascriptString(value)); }
            catch (JSONException ignored) { return; }
            String state = session.optString("state", "pending");
            boolean changed = !state.equals(currentSessionState);
            boolean wasSignedIn = signedIn;
            currentSessionState = state;
            if ("login".equals(state)) {
                if (changed) {
                    webView.evaluateJavascript("window.__tjEnterLogin&&window.__tjEnterLogin()", null);
                    sessionGeneration++;
                    requestedRoute = "home";
                    activeSection = "home";
                    backgroundLoginSince = android.os.SystemClock.elapsedRealtime();
                    backgroundLoginSubmitted = false;
                }
                signedIn = false;
                boolean loginReady = session.optBoolean("loginReady");
                long loginElapsed = backgroundLoginSince == 0 ? Long.MAX_VALUE
                        : android.os.SystemClock.elapsedRealtime() - backgroundLoginSince;
                boolean backgroundWindow = loginElapsed < BACKGROUND_LOGIN_WINDOW_MS;
                // Keep cached native data visible while the journal/password manager restores the form.
                shellVisible = !loginReady || backgroundWindow;
                if (loginReady && backgroundWindow && !backgroundLoginSubmitted) tryBackgroundAutoLogin();
                if (shellVisible && isNativeRoute(requestedRoute)) showCachedNativeData(requestedRoute);
                initialNativeDataRequested = false;
            } else if (session.optBoolean("verified")) {
                signedIn = true;
                shellVisible = true;
                backgroundLoginSince = 0;
                backgroundLoginSubmitted = false;
                connectionUnavailable = false;
                unverifiedSince = android.os.SystemClock.elapsedRealtime();
                if (!wasSignedIn) {
                    refreshMessages.clear();
                    if ("more".equals(requestedRoute)) requestedRoute = "home";
                    showCachedNativeData(requestedRoute);
                    applyMobileStyle();
                    if (!isNativeRoute(requestedRoute) && !navigationInFlight) {
                        hybridLoading = false;
                    }
                }
            } else {
                signedIn = false;
                shellVisible = true;
                initialNativeDataRequested = false;
                if (wasSignedIn) {
                    sessionGeneration++;
                    nativeRequests.clear();
                    requestVariants.clear();
                    avatarRequestUrl = "";
                }
                if (changed || wasSignedIn) {
                    String message = sessionMessage(state);
                    for (String route : NATIVE_ROUTES) refreshMessages.put(route, message);
                    if (isNativeRoute(requestedRoute) && shellVisible) showCachedNativeData(requestedRoute);
                }
                // Bounded retries for transport errors; 401/403 do not retry automatically.
                webView.evaluateJavascript("window.__tjVerifySession&&window.__tjVerifySession()", null);
            }
            updateSessionChrome();
            long elapsed = android.os.SystemClock.elapsedRealtime() - unverifiedSince;
            if (SessionRecovery.shouldOpenLogin(state, signedIn, session.optBoolean("authObserved"),
                    connectionUnavailable, automaticLoginUsed, elapsed)) {
                automaticLoginUsed = true;
                openJournalLogin();
                return;
            }
            if (signedIn && !initialNativeDataRequested) {
                initialNativeDataRequested = true;
                syncNativeHeader();
                handler.postDelayed(() -> {
                    if (generation != sessionGeneration || !signedIn) return;
                    requestNativeData(isNativeRoute(requestedRoute) ? requestedRoute : "home", false);
                }, 300);
            }
        });
    }

    private void tryBackgroundAutoLogin() {
        if (webView == null || backgroundLoginSubmitted) return;
        webView.evaluateJavascript("window.__tjTryAutoLogin?window.__tjTryAutoLogin():''", raw -> {
            if (isDestroyed() || !"login".equals(currentSessionState)) return;
            try {
                JSONObject result = new JSONObject(decodeJavascriptString(raw));
                if (result.optBoolean("submitted")) {
                    backgroundLoginSubmitted = true;
                    refreshMessages.put("home", "Выполняем автоматический вход…");
                    showCachedNativeData("home");
                }
            } catch (JSONException ignored) { }
        });
    }

    private String sessionMessage(String state) {
        if ("expired".equals(state)) return "Сессия истекла (401). Войдите в дневник";
        if ("forbidden".equals(state)) return "Сервер запретил доступ (403). Попробуйте позже · Повторить";
        if ("pending".equals(state)) return "Проверяем вход в дневник…";
        return "Не удалось проверить вход · Повторить";
    }

    private void updateSessionChrome() {
        toolbarView.setVisibility(shellVisible ? View.VISIBLE : View.GONE);
        bottomNavigationView.setVisibility(shellVisible ? View.VISIBLE : View.GONE);
        webViewLayoutParams.topMargin = shellVisible ? dp(HEADER_HEIGHT_DP) + statusBarInset : 0;
        webView.setLayoutParams(webViewLayoutParams);
        webView.setPadding(0, 0, 0, shellVisible ? dp(84) + navigationBarInset : 0);
        updateNativeHomeVisibility();
    }

    private void openJournalLogin() {
        if (webView == null) return;
        automaticLoginUsed = true;
        signedIn = false;
        shellVisible = true;
        initialNativeDataRequested = false;
        sessionGeneration++;
        nativeRequests.clear();
        requestVariants.clear();
        avatarRequestUrl = "";
        currentSessionState = "pending";
        requestedRoute = "home";
        refreshMessages.put("home", "Открываем страницу входа…");
        showCachedNativeData("home");
        removeMobileStyle();
        updateSessionChrome();
        webView.loadUrl(BASE_URL + "ru/auth/login/index");
    }

    private boolean handleNavigation(Uri uri) {
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
        if ("https".equals(scheme) && isTrustedHost(host)) {
            return false;
        }
        if (!("https".equals(scheme) || "http".equals(scheme)
                || "mailto".equals(scheme) || "tel".equals(scheme) || "geo".equals(scheme))) {
            Toast.makeText(this, "Ссылка заблокирована из соображений безопасности", Toast.LENGTH_SHORT).show();
            return true;
        }
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, uri));
        } catch (ActivityNotFoundException error) {
            Toast.makeText(this, "Не удалось открыть ссылку", Toast.LENGTH_SHORT).show();
        }
        return true;
    }

    private boolean isTrustedHost(String host) {
        return host.equals("journal.top-academy.ru");
    }

    private final class JournalDownloadListener implements DownloadListener {
        @Override
        public void onDownloadStart(String url, String userAgent, String contentDisposition, String mimeType, long length) {
            try {
                DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url));
                request.setMimeType(mimeType);
                request.addRequestHeader("User-Agent", userAgent);
                String cookies = CookieManager.getInstance().getCookie(url);
                if (cookies != null) request.addRequestHeader("Cookie", cookies);
                request.setTitle(URLUtil.guessFileName(url, contentDisposition, mimeType));
                request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
                request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS,
                        URLUtil.guessFileName(url, contentDisposition, mimeType));
                DownloadManager manager = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
                manager.enqueue(request);
                Toast.makeText(MainActivity.this, "Файл загружается", Toast.LENGTH_SHORT).show();
            } catch (Exception error) {
                Toast.makeText(MainActivity.this, "Не удалось начать загрузку", Toast.LENGTH_LONG).show();
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == FILE_CHOOSER_REQUEST && fileChooserCallback != null) {
            Uri[] result = WebChromeClient.FileChooserParams.parseResult(resultCode, data);
            fileChooserCallback.onReceiveValue(result);
            fileChooserCallback = null;
        }
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onBackPressed() {
        handleBack();
    }

    private void handleBack() {
        if ("material_detail".equals(requestedRoute)) { navigateByKey("materials"); return; }
        if (shellVisible && isNativeRoute(requestedRoute)) {
            if ("home".equals(requestedRoute)) finish();
            else navigateByKey("home");
        } else if (webView != null && webView.canGoBack()) {
            webView.goBack();
        } else {
            finish();
        }
    }

    @Override
    protected void onPause() {
        foreground = false;
        handler.removeCallbacks(sessionObserver);
        CookieManager.getInstance().flush();
        if (webView != null) webView.onPause();
        super.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        foreground = true;
        handler.removeCallbacks(sessionObserver);
        handler.post(sessionObserver);
        if (webView != null) {
            webView.onResume();
            handler.postDelayed(this::syncNativeHeader, 350);
            JSONObject home = nativeDataCache.get("home");
            if (home != null) handler.postDelayed(() -> maybeShowLessonEvaluation(home), 500);
            if (connectionUnavailable && shellVisible) {
                refreshMessages.clear();
                webView.reload();
            } else if (signedIn && isNativeRoute(requestedRoute)
                    && !refreshMessages.getOrDefault(requestedRoute, "").contains("ограничил")) {
                JSONObject data = nativeDataCache.get(requestedRoute);
                JSONObject sources = data == null ? null : data.optJSONObject("_sources");
                long newest = 0;
                if (sources != null) {
                    java.util.Iterator<String> keys = sources.keys();
                    while (keys.hasNext()) newest = Math.max(newest, sources.optLong(keys.next()));
                }
                if (System.currentTimeMillis() - newest > 60000) requestNativeData(requestedRoute, false);
            }
        }
    }

    @Override
    protected void onDestroy() {
        foreground = false;
        dismissLessonEvaluation();
        sessionGeneration++;
        handler.removeCallbacksAndMessages(null);
        if (webView != null) {
            webView.stopLoading();
            webView.setWebChromeClient(null);
            webView.setWebViewClient(null);
            webView.destroy();
        }
        webView = null;
        super.onDestroy();
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private TextView text(String value, float size, int color, int style) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setTypeface(Typeface.DEFAULT, style);
        view.setGravity(Gravity.CENTER_VERTICAL);
        return view;
    }

    private TextView serifText(String value, float size, int color, int style) {
        TextView view = text(value, size, color, style);
        view.setTypeface(Typeface.create("sans-serif", style));
        return view;
    }

    private TextView monoText(String value, float size, int color, int style) {
        TextView view = text(value, size, color, style);
        view.setTypeface(Typeface.create("monospace", style));
        view.setLetterSpacing(.025f);
        return view;
    }

    private void syncNativeHeader() {
        if (webView == null || scoreValue == null || topCoinsValue == null || gemsValue == null) return;
        String script = "window.__tjHeader?window.__tjHeader():''";
        int generation = sessionGeneration;
        webView.evaluateJavascript(script, rawValue -> {
            if (generation != sessionGeneration || !signedIn || isDestroyed()) return;
            try {
                String decoded = decodeJavascriptString(rawValue);
                if (decoded.isEmpty()) return;
                JSONObject data = new JSONObject(decoded);
                String score = cleanHeaderValue(data.optString("score", ""));
                String topCoins = cleanHeaderValue(data.optString("topCoins", ""));
                String gems = cleanHeaderValue(data.optString("gems", ""));
                String profile = data.optString("profile", "").trim();
                String avatar = data.optString("avatar", "").trim();

                String previousProfile = screenPreferences.getString("profile", "");
                if (!profile.isEmpty() && !previousProfile.isEmpty() && !profile.equals(previousProfile)) {
                    clearNativeCache();
                    sessionGeneration++;
                    initialNativeDataRequested = false;
                    showCachedNativeData(requestedRoute);
                    webView.evaluateJavascript("window.__tjReset&&window.__tjReset()", null);
                    webView.reload();
                    return;
                }
                if (!profile.isEmpty()) {
                    screenPreferences.edit().putString("profile", profile).apply();
                    webView.evaluateJavascript("window.__tjAccount&&window.__tjAccount(" + JSONObject.quote(profile) + ")", null);
                }
                updateHeaderMetric(scoreValue, "score", score);
                updateHeaderMetric(topCoinsValue, "coins", topCoins);
                updateHeaderMetric(gemsValue, "gems", gems);
                if (!profile.isEmpty() && avatarFallback != null) {
                    avatarFallback.setText(String.valueOf(profile.charAt(0)).toUpperCase(Locale.ROOT));
                }
                if (!avatar.isEmpty()) loadAvatar(avatar);
            } catch (JSONException ignored) {
                // The site may still be replacing its Angular tree; the delayed pass will retry.
            }
        });
    }

    private String cleanHeaderValue(String value) {
        String clean = value == null ? "" : value.replaceAll("\\s+", "").trim();
        if (clean.length() > 12) return clean.substring(0, 12);
        return clean;
    }

    private void loadAvatar(String avatarUrl) {
        if (avatarUrl.equals(loadedAvatarUrl) || avatarImage == null || avatarUrl.equals(avatarRequestUrl)
                || android.os.SystemClock.elapsedRealtime() < avatarRetryAt) return;
        Uri uri = Uri.parse(avatarUrl);
        if (!"https".equalsIgnoreCase(uri.getScheme())) return;
        avatarRequestUrl = avatarUrl;
        int generation = sessionGeneration;
        String cookies = CookieManager.getInstance().getCookie(avatarUrl);
        String userAgent = webView.getSettings().getUserAgentString();

        Thread loader = new Thread(() -> {
            HttpURLConnection connection = null;
            try {
                connection = (HttpURLConnection) new URL(avatarUrl).openConnection();
                connection.setConnectTimeout(8000);
                connection.setReadTimeout(8000);
                connection.setInstanceFollowRedirects(true);
                connection.setRequestProperty("User-Agent", userAgent);
                if (cookies != null && !cookies.isEmpty()) connection.setRequestProperty("Cookie", cookies);
                int contentLength = connection.getContentLength();
                if (contentLength > MAX_AVATAR_BYTES) return;
                int responseCode = connection.getResponseCode();
                if (responseCode < 200 || responseCode >= 300) return;

                byte[] bytes;
                try (InputStream input = connection.getInputStream();
                     ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                    byte[] buffer = new byte[8192];
                    int read;
                    int total = 0;
                    while ((read = input.read(buffer)) != -1) {
                        total += read;
                        if (total > MAX_AVATAR_BYTES) return;
                        output.write(buffer, 0, read);
                    }
                    bytes = output.toByteArray();
                }
                Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
                if (bitmap == null) return;
                runOnUiThread(() -> {
                    if (generation != sessionGeneration || !signedIn || isFinishing() || isDestroyed()) return;
                    loadedAvatarUrl = avatarUrl;
                    avatarImage.setImageBitmap(bitmap);
                    avatarImage.setVisibility(View.VISIBLE);
                    if (avatarFallback != null) avatarFallback.setVisibility(View.GONE);
                });
            } catch (Exception ignored) {
                // Keep the initial in the avatar when a protected or external image cannot be loaded.
            } finally {
                if (connection != null) connection.disconnect();
                runOnUiThread(() -> {
                    if (generation == sessionGeneration && avatarUrl.equals(avatarRequestUrl)) {
                        avatarRequestUrl = "";
                        avatarRetryAt = android.os.SystemClock.elapsedRealtime() + 30000;
                    }
                });
            }
        }, "top-journal-avatar");
        loader.start();
    }

    private void installApiCapture() {
        if (webView == null) return;
        webView.evaluateJavascript(apiCaptureJs + ";" + snapshotJs, null);
    }

    private void syncNativeHome() {
        if ("home".equals(requestedRoute)) syncSnapshot("home");
    }

    private void syncSnapshot(String route) {
        syncSnapshot(route, null);
    }

    private void syncSnapshot(String route, Runnable afterSync) {
        if (webView == null || !signedIn || !isNativeRoute(route)) return;
        int generation = sessionGeneration;
        webView.evaluateJavascript("window.__tjSnapshot?window.__tjSnapshot(" + JSONObject.quote(route) + "):''", raw -> {
            if (generation != sessionGeneration || isDestroyed()) return;
            try {
                JSONObject incoming = new JSONObject(decodeJavascriptString(raw));
                JSONObject previous = nativeDataCache.get(route);
                JSONObject merged = NativeSnapshot.merge(previous, incoming);
                JSONObject sources = merged.optJSONObject("_sources");
                if (sources == null || sources.length() == 0) return;
                if (previous != null && previous.toString().equals(merged.toString())) {
                    if ("home".equals(route)) maybeShowLessonEvaluation(merged);
                    return;
                }
                nativeDataCache.put(route, merged);
                if (merged.toString().length() < 512000) screenPreferences.edit().putString(route, merged.toString()).apply();
                if ("grades".equals(route)) JournalNotifications.updateGrades(this, merged,
                        !"grades".equals(requestedRoute));
                if ("home".equals(route)) maybeShowLessonEvaluation(merged);
                if (route.equals(requestedRoute)) showCachedNativeData(route);
            } catch (JSONException ignored) { /* Not ready; leave the visible snapshot intact. */ }
            finally { if (afterSync != null) afterSync.run(); }
        });
    }

    private void maybeShowLessonEvaluation(JSONObject home) {
        if (!foreground || !signedIn || isFinishing() || isDestroyed() || evaluationSubmitting
                || evaluationsDeferredForSession || lessonEvaluationDialog != null) return;
        JSONObject sources = home.optJSONObject("_sources");
        // Never open an old cached survey. The pending list must have been checked during this app run.
        if (sources == null || sources.optLong("evaluations") < activityStartedAt - 10000) return;
        JSONArray evaluations = home.optJSONArray("evaluations");
        if (evaluations == null || evaluations.length() == 0) return;
        JSONObject lesson = evaluations.optJSONObject(0);
        if (lesson == null || lesson.optString("key").trim().isEmpty()) return;
        showLessonEvaluationStep(new LessonEvaluationDraft(lesson, home.optBoolean("schoolBranch")),
                home.optBoolean("schoolBranch") ? 2 : 1);
    }

    private void showLessonEvaluationStep(LessonEvaluationDraft draft, int step) {
        if (!foreground || isFinishing() || isDestroyed()) return;
        boolean teacherStep = step == 1;
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(22), dp(18), dp(22), dp(18));
        content.setBackgroundColor(COLOR_BACKGROUND);

        String subject = draft.lesson.optString("subject", "Занятие");
        String teacher = draft.lesson.optString("teacher");
        String date = draft.lesson.optString("date");
        TextView lessonTitle = serifText(subject, 22, COLOR_BLACK, Typeface.BOLD);
        content.addView(lessonTitle);
        String meta = joinNonEmpty(" · ", teacher, date);
        if (!meta.isEmpty()) {
            TextView details = text(meta, 13, COLOR_MUTED, Typeface.NORMAL);
            LinearLayout.LayoutParams detailsParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            detailsParams.topMargin = dp(5);
            content.addView(details, detailsParams);
        }

        TextView question = serifText(teacherStep ? "Как вы оцениваете преподавателя?" : "Как вы оцениваете занятие?",
                18, COLOR_BLACK, Typeface.BOLD);
        LinearLayout.LayoutParams questionParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        questionParams.topMargin = dp(22);
        content.addView(question, questionParams);

        RatingBar rating = new RatingBar(this);
        rating.setNumStars(5);
        rating.setStepSize(1f);
        rating.setRating(teacherStep ? draft.teacherRating : draft.lessonRating);
        rating.setIsIndicator(false);
        rating.setProgressTintList(ColorStateList.valueOf(Color.rgb(245, 173, 35)));
        rating.setSecondaryProgressTintList(ColorStateList.valueOf(Color.rgb(225, 225, 228)));
        rating.setProgressBackgroundTintList(ColorStateList.valueOf(Color.rgb(225, 225, 228)));
        LinearLayout.LayoutParams ratingParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        ratingParams.topMargin = dp(10);
        content.addView(rating, ratingParams);

        TextView scale = monoText("1 — ПЛОХО     5 — ОТЛИЧНО", 10, COLOR_MUTED, Typeface.NORMAL);
        content.addView(scale);

        EditText comment = new EditText(this);
        comment.setHint("Комментарий (для оценки 1–3 обязателен, минимум 20 символов)");
        comment.setText(teacherStep ? draft.teacherComment : draft.lessonComment);
        comment.setTextSize(14);
        comment.setTextColor(COLOR_BLACK);
        comment.setHintTextColor(COLOR_MUTED);
        comment.setGravity(Gravity.TOP | Gravity.START);
        comment.setMinLines(3);
        comment.setMaxLines(6);
        comment.setFilters(new InputFilter[]{new InputFilter.LengthFilter(500)});
        comment.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE
                | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        comment.setPadding(dp(14), dp(12), dp(14), dp(12));
        comment.setBackground(borderedDrawable(COLOR_CARD, COLOR_BORDER, 10));
        LinearLayout.LayoutParams commentParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        commentParams.topMargin = dp(16);
        content.addView(comment, commentParams);

        TextView reward = text("Ответ отправится в дневник и будет учтён при начислении баллов.",
                12, COLOR_MUTED, Typeface.NORMAL);
        LinearLayout.LayoutParams rewardParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rewardParams.topMargin = dp(12);
        content.addView(reward, rewardParams);

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(COLOR_BACKGROUND);
        scroll.addView(content);
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(teacherStep ? "Оценка преподавателя · 1 из 2" :
                        (draft.schoolBranch ? "Оценка занятия" : "Оценка занятия · 2 из 2"))
                .setView(scroll)
                .setNegativeButton("Позже", (ignored, which) -> evaluationsDeferredForSession = true)
                .setPositiveButton(teacherStep ? "Далее" : "Отправить", null)
                .create();
        dialog.setOnCancelListener(ignored -> evaluationsDeferredForSession = true);
        dialog.setOnDismissListener(ignored -> {
            if (lessonEvaluationDialog == dialog) lessonEvaluationDialog = null;
        });
        dialog.setOnShowListener(ignored -> {
            Button positive = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            positive.setTextColor(Color.WHITE);
            positive.setBackground(roundedDrawable(COLOR_BLACK, 8));
            Button negativeButton = dialog.getButton(AlertDialog.BUTTON_NEGATIVE);
            if (negativeButton != null) negativeButton.setTextColor(COLOR_MUTED);
            positive.setOnClickListener(view -> {
                int value = Math.round(rating.getRating());
                String message = comment.getText().toString().trim();
                if (!validLessonEvaluation(value, message)) return;
                if (teacherStep) {
                    draft.teacherRating = value;
                    draft.teacherComment = message;
                    dialog.dismiss();
                    handler.post(() -> showLessonEvaluationStep(draft, 2));
                } else {
                    draft.lessonRating = value;
                    draft.lessonComment = message;
                    submitLessonEvaluation(draft, dialog);
                }
            });
        });
        lessonEvaluationDialog = dialog;
        dialog.getWindow();
        dialog.show();
        Window window = dialog.getWindow();
        if (window != null) window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
    }

    private boolean validLessonEvaluation(int rating, String comment) {
        if (rating < 1 || rating > 5) {
            Toast.makeText(this, "Выберите оценку от 1 до 5", Toast.LENGTH_SHORT).show();
            return false;
        }
        if (!comment.isEmpty() && comment.length() < 20) {
            Toast.makeText(this, "Комментарий должен содержать не менее 20 символов", Toast.LENGTH_LONG).show();
            return false;
        }
        if (rating <= 3 && comment.length() < 20) {
            Toast.makeText(this, "Для оценки 1–3 напишите комментарий не короче 20 символов",
                    Toast.LENGTH_LONG).show();
            return false;
        }
        return true;
    }

    private void submitLessonEvaluation(LessonEvaluationDraft draft, AlertDialog dialog) {
        if (evaluationSubmitting || webView == null || !signedIn) return;
        evaluationSubmitting = true;
        dialog.setCancelable(false);
        Button positive = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
        Button negative = dialog.getButton(AlertDialog.BUTTON_NEGATIVE);
        positive.setEnabled(false);
        positive.setText("Отправляем…");
        if (negative != null) negative.setEnabled(false);
        JSONObject payload = new JSONObject();
        try {
            payload.put("key", draft.lesson.optString("key"));
            payload.put("mark_lesson", draft.lessonRating);
            payload.put("mark_teach", draft.schoolBranch ? 0 : draft.teacherRating);
            payload.put("comment_lesson", draft.lessonComment);
            payload.put("comment_teach", draft.schoolBranch ? "" : draft.teacherComment);
        } catch (JSONException impossible) {
            finishEvaluationFailure(dialog, positive, negative, "Не удалось подготовить оценку");
            return;
        }
        String id = "evaluation-" + (++evaluationRequestSequence);
        int generation = sessionGeneration;
        String script = "window.__tjStartEvaluationJob?window.__tjStartEvaluationJob(" +
                JSONObject.quote(id) + "," + payload + "):false";
        webView.evaluateJavascript(script, ignored -> pollEvaluationSubmission(id, generation, dialog,
                positive, negative, android.os.SystemClock.elapsedRealtime() + 20000));
    }

    private void pollEvaluationSubmission(String id, int generation, AlertDialog dialog, Button positive,
                                          Button negative, long deadline) {
        if (generation != sessionGeneration || webView == null || isDestroyed()) {
            evaluationSubmitting = false;
            return;
        }
        if (android.os.SystemClock.elapsedRealtime() >= deadline) {
            webView.evaluateJavascript("window.__tjDropEvaluationJob&&window.__tjDropEvaluationJob(" +
                    JSONObject.quote(id) + ")", null);
            finishEvaluationFailure(dialog, positive, negative, "Сервер не ответил вовремя");
            return;
        }
        webView.evaluateJavascript("window.__tjReadEvaluationJob?window.__tjReadEvaluationJob(" +
                JSONObject.quote(id) + "):JSON.stringify({done:true,reason:'bridge_pending'})", raw -> {
            if (generation != sessionGeneration || isDestroyed()) return;
            try {
                JSONObject job = new JSONObject(decodeJavascriptString(raw));
                if (!job.optBoolean("done")) {
                    handler.postDelayed(() -> pollEvaluationSubmission(id, generation, dialog, positive,
                            negative, deadline), 300);
                    return;
                }
                webView.evaluateJavascript("window.__tjDropEvaluationJob&&window.__tjDropEvaluationJob(" +
                        JSONObject.quote(id) + ")", null);
                if (job.optBoolean("ok")) {
                    evaluationSubmitting = false;
                    dialog.dismiss();
                    Toast.makeText(this, "Спасибо! Оценка отправлена", Toast.LENGTH_LONG).show();
                    syncSnapshot("home");
                    handler.postDelayed(() -> requestNativeData("home", false), 900);
                    return;
                }
                String reason = job.optString("reason");
                int status = job.optInt("status");
                String message = status == 401 ? "Сессия истекла. Войдите в дневник снова"
                        : status == 403 ? "Сервер не разрешил отправить эту оценку"
                        : "invalid_evaluation".equals(reason) ? "Проверьте оценки и комментарии"
                        : "Не удалось отправить оценку. Проверьте соединение";
                finishEvaluationFailure(dialog, positive, negative, message);
            } catch (JSONException error) {
                finishEvaluationFailure(dialog, positive, negative, "Не удалось получить ответ сервера");
            }
        });
    }

    private void finishEvaluationFailure(AlertDialog dialog, Button positive, Button negative, String message) {
        evaluationSubmitting = false;
        if (dialog.isShowing()) {
            dialog.setCancelable(true);
            positive.setEnabled(true);
            positive.setText("Отправить");
            if (negative != null) negative.setEnabled(true);
        }
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    private void dismissLessonEvaluation() {
        AlertDialog dialog = lessonEvaluationDialog;
        lessonEvaluationDialog = null;
        if (dialog != null && dialog.isShowing()) dialog.dismiss();
    }

    private void renderNativeHome(JSONObject data) {
        if (nativeHomeContent == null) return;
        nativeHomeContent.removeAllViews();

        TextView eyebrow = monoText(todayLabel(), 10, COLOR_MUTED, Typeface.NORMAL);
        addNativeBlock(eyebrow, 7);
        TextView dashboardTitle = text("Обзор", 30, COLOR_BLACK, Typeface.BOLD);
        dashboardTitle.setIncludeFontPadding(false);
        addNativeBlock(dashboardTitle, 18);

        JSONObject tasks = data.optJSONObject("tasks");
        addNativeBlock(createCommandCenter(tasks), 12);
        addNativeBlock(createBalanceStrip(), 26);

        addNativeSectionTitle("Показатели");
        LinearLayout metricsOne = new LinearLayout(this);
        metricsOne.setOrientation(LinearLayout.HORIZONTAL);
        metricsOne.addView(createDashboardMetric("Средний балл", data.has("score") ? data.optString("score") : null,
                "за текущий период", COLOR_CHART_BLUE), weightedParams(0, 5));
        metricsOne.addView(createDashboardMetric("Посещаемость", data.has("attendance") ? data.optString("attendance") : null,
                "последние занятия", COLOR_CHART_ORANGE), weightedParams(5, 0));
        addNativeBlock(metricsOne, 10);
        LinearLayout metricsTwo = new LinearLayout(this);
        metricsTwo.setOrientation(LinearLayout.HORIZONTAL);
        metricsTwo.addView(createDashboardMetric("В группе", data.has("group") ? data.optString("group") : null,
                "место в рейтинге", COLOR_BLACK), weightedParams(0, 5));
        metricsTwo.addView(createDashboardMetric("В потоке", data.has("stream") ? data.optString("stream") : null,
                "среди всех групп", Color.rgb(145, 145, 145)), weightedParams(5, 0));
        addNativeBlock(metricsTwo, 26);

        addNativeSectionTitle("Ближайшие экзамены");
        JSONArray exams = data.optJSONArray("exams");
        LinearLayout examList = createNativeListContainer();
        if (exams == null) {
            examList.addView(missingData(100));
        } else if (exams.length() == 0) {
            examList.addView(createEmptyNativeState("Назначенных экзаменов пока нет"));
        } else {
            for (int i = 0; i < exams.length(); i++) {
                JSONObject exam = exams.optJSONObject(i);
                if (exam != null) examList.addView(createNativeListRow("E", exam.optString("title"),
                        "Экзамен", exam.optString("date")));
            }
        }
        addNativeBlock(examList, 26);

        JSONArray activity = data.optJSONArray("activity");
        if (activity == null) {
            addNativeSectionTitle("Последняя активность");
            addNativeBlock(loadingCard("События дневника", 108), 24);
        } else if (activity.length() > 0) {
            addNativeSectionTitle("Последняя активность");
            LinearLayout list = createNativeListContainer();
            for (int i = 0; i < Math.min(activity.length(), 5); i++) {
                JSONObject item = activity.optJSONObject(i);
                if (item != null) list.addView(createNativeListRow(String.valueOf(i + 1),
                        item.optString("title"), item.optString("date"), item.optString("amount")));
            }
            addNativeBlock(list, 24);
        }

        addNativeSectionTitle("Разделы");
        LinearLayout quickOne = new LinearLayout(this);
        quickOne.setOrientation(LinearLayout.HORIZONTAL);
        quickOne.addView(createShortcut("AI-аналитик", "План и риски", "assistant"), weightedParams(0, 5));
        quickOne.addView(createShortcut("Материалы", "Уроки и тесты", "materials"), weightedParams(5, 0));
        addNativeBlock(quickOne, 10);
        LinearLayout quickTwo = new LinearLayout(this);
        quickTwo.setOrientation(LinearLayout.HORIZONTAL);
        quickTwo.addView(createShortcut("Награды", "Достижения", "rewards"), weightedParams(0, 5));
        quickTwo.addView(createShortcut("Оплата", "История платежей", "payment"), weightedParams(5, 0));
        addNativeBlock(quickTwo, 18);

        animateNativeHomeContent();
    }

    private void renderNativeAssistant() {
        if (nativeHomeContent == null) return;
        nativeHomeContent.removeAllViews();

        addNativeBlock(monoText("HOMEWORK AI · GLM 5.3", 10, COLOR_CHART_BLUE, Typeface.BOLD), 7);
        TextView title = text("Помощник по домашке", 30, COLOR_BLACK, Typeface.BOLD);
        title.setIncludeFontPadding(false);
        addNativeBlock(title, 18);

        LinearLayout hero = new LinearLayout(this);
        hero.setOrientation(LinearLayout.VERTICAL);
        hero.setPadding(dp(20), dp(18), dp(20), dp(18));
        hero.setBackground(roundedDrawable(COLOR_BLACK, 10));
        LinearLayout heroTop = new LinearLayout(this);
        heroTop.setGravity(Gravity.CENTER_VERTICAL);
        ImageView sparkle = new ImageView(this);
        sparkle.setImageResource(R.drawable.ic_ai);
        heroTop.addView(sparkle, new LinearLayout.LayoutParams(dp(28), dp(28)));
        TextView heroTitle = text("С чего начать?", 19, Color.WHITE, Typeface.BOLD);
        LinearLayout.LayoutParams heroTitleParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        heroTitleParams.leftMargin = dp(10);
        heroTop.addView(heroTitle, heroTitleParams);
        hero.addView(heroTop);
        TextView intro = text("Расставлю задания по приоритету и дам короткий план без лишнего текста.",
                13, Color.rgb(190, 190, 190), Typeface.NORMAL);
        intro.setPadding(0, dp(13), 0, 0);
        intro.setLineSpacing(dp(2), 1f);
        hero.addView(intro);
        TextView primary = text(homeworkAiLoading ? "Анализирую…" : "Расставить приоритеты  →", 15, COLOR_BLACK, Typeface.BOLD);
        primary.setGravity(Gravity.CENTER);
        primary.setMinHeight(dp(48));
        primary.setEnabled(!homeworkAiLoading);
        primary.setAlpha(homeworkAiLoading ? .55f : 1f);
        primary.setBackground(roundedDrawable(Color.WHITE, 8));
        primary.setOnClickListener(v -> requestHomeworkOverview("priorities"));
        hero.addView(primary, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48)));
        addNativeBlock(hero, 22);

        addNativeSectionTitle("Быстрые действия");
        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.addView(createHomeworkAction("Риски сроков", "Найти просрочки", () -> requestHomeworkOverview("requirements")), weightedParams(0, 5));
        actions.addView(createHomeworkAction("План на день", "По шагам", () -> requestHomeworkOverview("plan")), weightedParams(5, 0));
        addNativeBlock(actions, 22);

        JSONArray homework = currentHomeworkItems();
        addNativeSectionTitle("Текущие задания");
        LinearLayout list = createNativeListContainer();
        if (homework == null) list.addView(createEmptyNativeState("Сначала обновите раздел «Задания»"));
        else if (homework.length() == 0) list.addView(createEmptyNativeState("Текущих заданий нет"));
        else for (int i = 0; i < Math.min(homework.length(), 8); i++) {
            JSONObject item = homework.optJSONObject(i);
            if (item != null) list.addView(createHomeworkAiRow(item));
        }
        addNativeBlock(list, 22);

        String saved = preferences.getString("homework_ai_last_result", "");
        if (!saved.isEmpty()) try {
            addNativeSectionTitle("Последний разбор");
            addNativeBlock(createHomeworkResultCard(new JSONObject(saved)), 18);
        } catch (JSONException ignored) { }
        if (!aiError.isEmpty()) {
            TextView error = text(aiError, 13, COLOR_DANGER, Typeface.NORMAL);
            error.setPadding(dp(14), dp(12), dp(14), dp(12));
            error.setBackground(borderedDrawable(Color.rgb(255, 248, 248), Color.rgb(245, 184, 187), 8));
            addNativeBlock(error, 14);
        }

        TextView privacy = text(BuildConfig.AI_SERVER_URL.isEmpty()
                        ? "Локальный AI-сервер не настроен. Запустите server/setup.ps1 и пересоберите приложение."
                        : "Запрос идёт через ваш компьютер. Ключ RuRouter не хранится в приложении; сервер не сохраняет снимки дневника.",
                11, COLOR_MUTED, Typeface.NORMAL);
        privacy.setLineSpacing(dp(2), 1f);
        addNativeBlock(privacy, 20);
        animateNativeHomeContent();
    }

    private LinearLayout createHomeworkAction(String title, String subtitle, Runnable action) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(15), dp(13), dp(15), dp(13));
        card.setMinimumHeight(dp(88));
        card.setBackground(borderedDrawable(COLOR_CARD, COLOR_BORDER, 8));
        card.addView(text(title, 14, COLOR_BLACK, Typeface.BOLD));
        TextView note = text(subtitle, 11, COLOR_MUTED, Typeface.NORMAL);
        note.setPadding(0, dp(4), 0, 0);
        card.addView(note);
        card.setOnClickListener(v -> action.run());
        enablePressAnimation(card);
        return card;
    }

    private JSONArray currentHomeworkItems() {
        JSONObject data = nativeDataCache.get("tasks");
        if (data == null) return null;
        JSONArray items = data.optJSONArray(snapshotKey("tasks"));
        return items != null ? items : data.optJSONArray("items");
    }

    private View createHomeworkAiRow(JSONObject item) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(5), dp(11), dp(5), dp(11));
        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        TextView title = text(item.optString("title", "Задание"), 14, COLOR_BLACK, Typeface.BOLD);
        title.setMaxLines(2);
        copy.addView(title);
        copy.addView(text(joinNonEmpty(" · ", item.optString("deadline"), taskAttachments(item).length() + " файл."),
                11, COLOR_MUTED, Typeface.NORMAL));
        row.addView(copy, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView action = text("Разобрать", 11, COLOR_BLACK, Typeface.BOLD);
        action.setGravity(Gravity.CENTER);
        action.setBackground(roundedDrawable(COLOR_ACCENT, 6));
        action.setOnClickListener(v -> showHomeworkAiActions(item));
        row.addView(action, new LinearLayout.LayoutParams(dp(82), dp(36)));
        return row;
    }

    private void showHomeworkAiActions(JSONObject item) {
        String[] labels = taskAttachments(item).length() > 0
                ? new String[]{"Кратко: что нужно сделать", "Составить план", "Объяснить тему", "Проверить требования", "Разобрать все вложения"}
                : new String[]{"Кратко: что нужно сделать", "Составить план", "Объяснить тему", "Проверить требования"};
        String[] actions = {"summary", "plan", "explain", "requirements", "files"};
        new AlertDialog.Builder(this).setTitle(item.optString("title", "Домашнее задание"))
                .setItems(labels, (dialog, which) -> requestHomeworkAi(item, actions[which], "files".equals(actions[which])))
                .setNegativeButton("Отмена", null).show();
    }

    private void requestHomeworkOverview(String action) {
        JSONArray items = currentHomeworkItems();
        if (items == null || items.length() == 0) {
            Toast.makeText(this, "Сначала загрузите текущие задания", Toast.LENGTH_SHORT).show();
            navigateByKey("tasks");
            return;
        }
        JSONObject homework = new JSONObject();
        try { homework.put("items", sanitizeAiValue(items)); } catch (JSONException ignored) { }
        startHomeworkAiRequest(homework, action, new JSONArray(), null);
    }

    private void requestHomeworkAi(JSONObject item, String action, boolean includeFiles) {
        if (!includeFiles) {
            startHomeworkAiRequest(item, action, new JSONArray(), null);
            return;
        }
        JSONArray attachments = taskAttachments(item);
        if (attachments.length() == 0) {
            Toast.makeText(this, "У задания нет вложений", Toast.LENGTH_SHORT).show();
            return;
        }
        AlertDialog loading = new AlertDialog.Builder(this).setTitle("Анализ вложений")
                .setMessage("Загружаю файлы 0 из " + attachments.length())
                .setNegativeButton("Отмена", null).create();
        loading.show();
        downloadHomeworkAiFiles(item, attachments, 0, new ArrayList<>(), loading);
    }

    private JSONArray taskAttachments(JSONObject item) {
        JSONArray result = new JSONArray();
        Map<String, Boolean> urls = new LinkedHashMap<>();
        JSONArray source = item.optJSONArray("attachments");
        if (source != null) for (int i = 0; i < source.length(); i++) {
            JSONObject file = source.optJSONObject(i);
            if (file != null && !file.optString("url").isEmpty() && !urls.containsKey(file.optString("url"))) {
                result.put(file); urls.put(file.optString("url"), true);
            }
        }
        String url = item.optString("url");
        if (!url.isEmpty() && !urls.containsKey(url)) {
            try { result.put(new JSONObject().put("url", url).put("filename", item.optString("filename")).put("mime", item.optString("mime"))); }
            catch (JSONException ignored) { }
        }
        return result;
    }

    private LinearLayout createHomeworkResultCard(JSONObject result) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(18), dp(16), dp(18), dp(18));
        card.setBackground(borderedDrawable(COLOR_CARD, COLOR_BORDER, 8));
        card.addView(text(result.optString("headline", "Разбор готов"), 19, COLOR_BLACK, Typeface.BOLD));
        String summary = result.optString("summary").trim();
        if (!summary.isEmpty()) {
            TextView summaryView = text(summary, 14, COLOR_BLACK, Typeface.NORMAL);
            summaryView.setLineSpacing(dp(3), 1f);
            summaryView.setPadding(0, dp(10), 0, 0);
            card.addView(summaryView);
        }
        addHomeworkResultList(card, "ЧТО ДЕЛАТЬ", result.optJSONArray("actions"), COLOR_BLACK);
        addHomeworkResultList(card, "ОБРАТИТЬ ВНИМАНИЕ", result.optJSONArray("warnings"), COLOR_DANGER);
        JSONArray files = result.optJSONArray("files");
        if (files != null && files.length() > 0) {
            TextView heading = monoText("ПО ФАЙЛАМ", 9, COLOR_MUTED, Typeface.BOLD);
            heading.setPadding(0, dp(14), 0, dp(5));
            card.addView(heading);
            for (int i = 0; i < files.length(); i++) {
                JSONObject file = files.optJSONObject(i);
                if (file == null) continue;
                TextView row = text(file.optString("name", "Файл") + "\n" + file.optString("note"), 13, COLOR_BLACK, Typeface.NORMAL);
                row.setPadding(0, dp(5), 0, dp(5));
                row.setLineSpacing(dp(2), 1f);
                card.addView(row);
            }
        }
        return card;
    }

    private void addHomeworkResultList(LinearLayout card, String label, JSONArray items, int color) {
        if (items == null || items.length() == 0) return;
        TextView heading = monoText(label, 9, COLOR_MUTED, Typeface.BOLD);
        heading.setPadding(0, dp(14), 0, dp(4));
        card.addView(heading);
        for (int i = 0; i < items.length(); i++) {
            TextView row = text((i + 1) + ".  " + items.optString(i), 13, color, Typeface.NORMAL);
            row.setPadding(0, dp(4), 0, dp(4));
            row.setLineSpacing(dp(2), 1f);
            card.addView(row);
        }
    }

    private interface HomeworkFileCallback {
        void complete(DownloadedFile file, String error);
    }

    private void downloadHomeworkAiFiles(JSONObject homework, JSONArray attachments, int index,
                                         List<DownloadedFile> files, AlertDialog loading) {
        int total = Math.min(attachments.length(), 8);
        if (!loading.isShowing()) return;
        if (index >= total) {
            loading.setMessage("Подготавливаю содержимое файлов…");
            new Thread(() -> {
                JSONArray prepared = prepareHomeworkFiles(files);
                handler.post(() -> {
                    if (loading.isShowing()) startHomeworkAiRequest(homework, "files", prepared, loading);
                });
            }, "homework-file-preparation").start();
            return;
        }
        loading.setMessage("Загружаю файлы " + (index + 1) + " из " + total);
        JSONObject attachment = attachments.optJSONObject(index);
        if (attachment == null) {
            downloadHomeworkAiFiles(homework, attachments, index + 1, files, loading);
            return;
        }
        requestHomeworkAiFile(attachment, (file, error) -> {
            if (file != null) files.add(file);
            downloadHomeworkAiFiles(homework, attachments, index + 1, files, loading);
        });
    }

    private void requestHomeworkAiFile(JSONObject attachment, HomeworkFileCallback callback) {
        Uri uri = resolveHomeworkFileUri(attachment.optString("url"));
        if (uri == null) { callback.complete(null, "Некорректная ссылка"); return; }
        String name = attachment.optString("filename");
        if (name.isEmpty()) name = URLUtil.guessFileName(uri.toString(), null, attachment.optString("mime"));
        final String fileName = name;
        final String mime = attachment.optString("mime");
        if (webView == null) { downloadHomeworkAiFileDirect(uri, fileName, mime, callback); return; }
        String id = "ai_file_" + (++requestSequence) + "_" + System.currentTimeMillis();
        String script = "window.__tjStartFileJob&&window.__tjStartFileJob(" + JSONObject.quote(id) + ","
                + JSONObject.quote(uri.toString()) + ")";
        webView.evaluateJavascript(script, ignored -> pollHomeworkAiFile(id, uri, fileName, mime, callback, 0));
    }

    private void pollHomeworkAiFile(String id, Uri uri, String name, String mime,
                                    HomeworkFileCallback callback, int attempt) {
        if (webView == null || isDestroyed()) { callback.complete(null, "Дневник закрыт"); return; }
        if (attempt >= 150) {
            webView.evaluateJavascript("window.__tjDropFileJob&&window.__tjDropFileJob(" + JSONObject.quote(id) + ")", null);
            downloadHomeworkAiFileDirect(uri, name, mime, callback);
            return;
        }
        webView.evaluateJavascript("window.__tjReadFileJob?window.__tjReadFileJob(" + JSONObject.quote(id) + "):''", raw -> {
            try {
                JSONObject payload = new JSONObject(decodeJavascriptString(raw));
                if (!payload.optBoolean("done")) {
                    handler.postDelayed(() -> pollHomeworkAiFile(id, uri, name, mime, callback, attempt + 1), 200);
                    return;
                }
                webView.evaluateJavascript("window.__tjDropFileJob&&window.__tjDropFileJob(" + JSONObject.quote(id) + ")", null);
                String encoded = payload.optString("data");
                if (encoded.isEmpty()) throw new java.io.IOException(payload.optString("error", "Файл не загрузился"));
                callback.complete(new DownloadedFile(android.util.Base64.decode(encoded, android.util.Base64.DEFAULT),
                        name, payload.optString("mime", mime)), null);
            } catch (Exception error) {
                downloadHomeworkAiFileDirect(uri, name, mime, callback);
            }
        });
    }

    private void downloadHomeworkAiFileDirect(Uri uri, String name, String mime, HomeworkFileCallback callback) {
        final String userAgent = webView == null ? "TopJournal/0.26.0" : webView.getSettings().getUserAgentString();
        new Thread(() -> {
            try {
                DownloadedFile file = downloadTaskFile(uri, name, mime, userAgent);
                handler.post(() -> callback.complete(file, null));
            } catch (Exception error) {
                handler.post(() -> callback.complete(null, error.getMessage()));
            }
        }, "homework-ai-download").start();
    }

    private Uri resolveHomeworkFileUri(String raw) {
        if (raw == null || raw.trim().isEmpty()) return null;
        try {
            URI link = new URI(BASE_URL).resolve(raw);
            if (!"https".equalsIgnoreCase(link.getScheme()) || link.getHost() == null || link.getUserInfo() != null) return null;
            return Uri.parse(link.toString());
        } catch (Exception ignored) { return null; }
    }

    private JSONArray prepareHomeworkFiles(List<DownloadedFile> files) {
        JSONArray result = new JSONArray();
        int totalImages = 0;
        for (DownloadedFile file : files) {
            try {
                JSONObject item = new JSONObject().put("name", file.name).put("mime", file.mime);
                JSONArray images = new JSONArray();
                String lower = (file.name + " " + file.mime).toLowerCase(Locale.ROOT);
                String extracted = "";
                if (lower.contains(".pptx") || lower.contains("presentationml")) {
                    StringBuilder text = new StringBuilder();
                    for (PptxSlide slide : readPptxSlides(file.bytes)) {
                        if (!slide.text.isEmpty()) text.append("Слайд: ").append(slide.text).append('\n');
                        for (byte[] image : slide.images) if (images.length() < 4) addAiImage(images, image);
                    }
                    extracted = text.toString();
                } else if (lower.contains(".pdf") || isPdfBytes(file.bytes)) {
                    renderPdfForAi(file.bytes, images);
                } else if (isImageFile(lower, file.bytes)) {
                    addAiImage(images, file.bytes);
                } else if (isZipBytes(file.bytes)) {
                    extracted = extractOfficeArchiveText(file.bytes);
                } else if (isPlainTextFile(lower)) {
                    extracted = new String(file.bytes, 0, Math.min(file.bytes.length, 120_000), StandardCharsets.UTF_8);
                }
                String cleanText = plainText(extracted);
                item.put("text", cleanText.substring(0, Math.min(cleanText.length(), 45_000)));
                JSONArray limitedImages = new JSONArray();
                for (int i = 0; i < images.length() && totalImages < 8; i++, totalImages++)
                    limitedImages.put(images.optString(i));
                item.put("images", limitedImages);
                result.put(item);
            } catch (Exception ignored) { }
        }
        return result;
    }

    private boolean isPlainTextFile(String lower) {
        return lower.contains("text/") || lower.matches(".*\\.(txt|md|csv|tsv|json|xml|html|css|js|java|kt|py|sql)(\\s.*)?$");
    }

    private boolean isImageFile(String lower, byte[] bytes) {
        return lower.contains("image/") || lower.matches(".*\\.(jpg|jpeg|png|webp|bmp|gif)(\\s.*)?$")
                || BitmapFactory.decodeByteArray(bytes, 0, Math.min(bytes.length, 128)) != null;
    }

    private boolean isPdfBytes(byte[] bytes) {
        return bytes != null && bytes.length >= 4 && bytes[0] == '%' && bytes[1] == 'P' && bytes[2] == 'D' && bytes[3] == 'F';
    }

    private void addAiImage(JSONArray images, byte[] bytes) {
        try {
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            BitmapFactory.decodeByteArray(bytes, 0, bytes.length, bounds);
            int sample = 1;
            while (bounds.outWidth / sample > 1400 || bounds.outHeight / sample > 1400) sample *= 2;
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inSampleSize = sample;
            Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length, options);
            if (bitmap == null) return;
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            bitmap.compress(Bitmap.CompressFormat.JPEG, 76, output);
            bitmap.recycle();
            images.put(android.util.Base64.encodeToString(output.toByteArray(), android.util.Base64.NO_WRAP));
        } catch (Exception ignored) { }
    }

    private void renderPdfForAi(byte[] bytes, JSONArray images) {
        File temporary = null;
        try {
            temporary = File.createTempFile("topjournal_ai_", ".pdf", getCacheDir());
            try (FileOutputStream output = new FileOutputStream(temporary)) { output.write(bytes); }
            try (ParcelFileDescriptor descriptor = ParcelFileDescriptor.open(temporary, ParcelFileDescriptor.MODE_READ_ONLY);
                 PdfRenderer renderer = new PdfRenderer(descriptor)) {
                for (int i = 0; i < Math.min(renderer.getPageCount(), 6); i++) {
                    try (PdfRenderer.Page page = renderer.openPage(i)) {
                        int width = 1100;
                        int height = Math.max(1, Math.round(width * (page.getHeight() / (float) page.getWidth())));
                        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
                        bitmap.eraseColor(Color.WHITE);
                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
                        ByteArrayOutputStream output = new ByteArrayOutputStream();
                        bitmap.compress(Bitmap.CompressFormat.JPEG, 72, output);
                        bitmap.recycle();
                        images.put(android.util.Base64.encodeToString(output.toByteArray(), android.util.Base64.NO_WRAP));
                    }
                }
            }
        } catch (Exception ignored) { }
        finally { if (temporary != null) temporary.delete(); }
    }

    private String extractOfficeArchiveText(byte[] bytes) {
        StringBuilder result = new StringBuilder();
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(bytes))) {
            ZipEntry entry;
            Pattern textTag = Pattern.compile("<(?:w:t|a:t|t|text:p)[^>]*>(.*?)</(?:w:t|a:t|t|text:p)>", Pattern.DOTALL);
            while ((entry = zip.getNextEntry()) != null && result.length() < 45_000) {
                String name = entry.getName();
                if (entry.isDirectory() || !name.endsWith(".xml") || !(name.startsWith("word/")
                        || name.startsWith("ppt/slides/") || name.startsWith("xl/") || "content.xml".equals(name))) continue;
                String xml = new String(readAllBytes(zip, 6 * 1024 * 1024), StandardCharsets.UTF_8);
                Matcher matcher = textTag.matcher(xml);
                while (matcher.find() && result.length() < 45_000) {
                    String text = android.text.Html.fromHtml(matcher.group(1), android.text.Html.FROM_HTML_MODE_COMPACT).toString().trim();
                    if (!text.isEmpty()) result.append(text).append(' ');
                }
            }
        } catch (Exception ignored) { }
        return result.toString();
    }

    private void startHomeworkAiRequest(Object homework, String action, JSONArray files, AlertDialog loadingDialog) {
        if (homeworkAiLoading) return;
        if (!signedIn) { openJournalLogin(); return; }
        if (BuildConfig.AI_SERVER_URL.isEmpty() || BuildConfig.AI_SERVER_KEY.isEmpty()) {
            Toast.makeText(this, "AI-сервер не настроен", Toast.LENGTH_SHORT).show(); return;
        }
        homeworkAiLoading = true;
        aiError = "";
        AlertDialog loading = loadingDialog != null ? loadingDialog : new AlertDialog.Builder(this)
                .setTitle("AI разбирает домашку").setMessage("Формирую короткий ответ…")
                .setNegativeButton("Скрыть", null).create();
        if (!loading.isShowing()) loading.show(); else loading.setMessage("Формирую короткий ответ…");
        renderedSignatures.remove("assistant");
        if ("assistant".equals(requestedRoute)) showCachedNativeData("assistant");
        new Thread(() -> {
            JSONObject result = null;
            String failure = null;
            try {
                JSONObject payload = new JSONObject().put("action", action).put("homework", homework).put("files", files);
                HttpsURLConnection connection = (HttpsURLConnection) new URL(BuildConfig.AI_SERVER_URL + "/api/homework").openConnection();
                connection.setSSLSocketFactory(createAiSslContext().getSocketFactory());
                connection.setRequestMethod("POST");
                connection.setConnectTimeout(10_000);
                connection.setReadTimeout(165_000);
                connection.setDoOutput(true);
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                connection.setRequestProperty("X-TopJournal-Key", BuildConfig.AI_SERVER_KEY);
                byte[] body = payload.toString().getBytes(StandardCharsets.UTF_8);
                connection.setFixedLengthStreamingMode(body.length);
                try (OutputStream output = connection.getOutputStream()) { output.write(body); }
                int status = connection.getResponseCode();
                JSONObject response = new JSONObject(readLimited(status < 300 ? connection.getInputStream() : connection.getErrorStream(), 1024 * 1024));
                if (status < 200 || status >= 300 || !response.optBoolean("ok"))
                    throw new IllegalStateException("provider:" + response.optString("message", response.optString("error", "HTTP " + status)));
                result = response.optJSONObject("result");
                if (result == null) throw new IllegalStateException("Некорректный ответ AI");
            } catch (Exception error) {
                String reason = error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
                failure = reason.startsWith("provider:") ? reason.substring(9) : "Не удалось связаться с локальным AI-сервером: " + reason;
            }
            JSONObject finalResult = result;
            String finalFailure = failure;
            handler.post(() -> {
                homeworkAiLoading = false;
                if (loading.isShowing()) loading.dismiss();
                if (finalResult != null) {
                    preferences.edit().putString("homework_ai_last_result", finalResult.toString())
                            .putLong("homework_ai_last_generated", System.currentTimeMillis()).apply();
                    ScrollView scroll = new ScrollView(this);
                    LinearLayout shell = new LinearLayout(this);
                    shell.setPadding(dp(8), dp(6), dp(8), dp(6));
                    shell.addView(createHomeworkResultCard(finalResult));
                    scroll.addView(shell);
                    new AlertDialog.Builder(this).setTitle("AI-разбор").setView(scroll).setPositiveButton("Готово", null).show();
                } else {
                    aiError = finalFailure;
                    new AlertDialog.Builder(this).setTitle("Не удалось выполнить анализ")
                            .setMessage(finalFailure).setPositiveButton("Закрыть", null).show();
                }
                renderedSignatures.remove("assistant");
                if ("assistant".equals(requestedRoute)) showCachedNativeData("assistant");
            });
        }, "homework-ai-request").start();
    }

    private void requestAiAnalysis() {
        if (aiLoading) return;
        if (!signedIn) {
            Toast.makeText(this, "Сначала войдите в дневник", Toast.LENGTH_SHORT).show();
            openJournalLogin();
            return;
        }
        if (BuildConfig.AI_SERVER_URL.isEmpty() || BuildConfig.AI_SERVER_KEY.isEmpty()) {
            aiError = "Локальный AI-сервер не настроен в этой сборке.";
            renderedSignatures.remove("assistant");
            showCachedNativeData("assistant");
            return;
        }

        JSONObject snapshot = buildAiSnapshot();
        aiLoading = true;
        aiError = "";
        renderedSignatures.remove("assistant");
        showCachedNativeData("assistant");
        new Thread(() -> {
            String analysis = null;
            String failure = null;
            try {
                URL url = new URL(BuildConfig.AI_SERVER_URL + "/api/insights");
                HttpsURLConnection connection = (HttpsURLConnection) url.openConnection();
                connection.setSSLSocketFactory(createAiSslContext().getSocketFactory());
                connection.setRequestMethod("POST");
                connection.setConnectTimeout(10_000);
                connection.setReadTimeout(140_000);
                connection.setDoOutput(true);
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                connection.setRequestProperty("X-TopJournal-Key", BuildConfig.AI_SERVER_KEY);
                byte[] body = new JSONObject().put("snapshot", snapshot).toString().getBytes(StandardCharsets.UTF_8);
                connection.setFixedLengthStreamingMode(body.length);
                try (OutputStream output = connection.getOutputStream()) { output.write(body); }
                int status = connection.getResponseCode();
                InputStream stream = status >= 200 && status < 300 ? connection.getInputStream() : connection.getErrorStream();
                JSONObject response = new JSONObject(readLimited(stream, 512 * 1024));
                if (status < 200 || status >= 300 || !response.optBoolean("ok")) {
                    throw new IllegalStateException("provider:" + response.optString("message",
                            response.optString("error", "HTTP " + status)));
                }
                analysis = response.optString("analysis", "").trim();
                if (analysis.isEmpty()) throw new IllegalStateException("empty_response");
            } catch (Exception error) {
                String reason = error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
                failure = reason.startsWith("provider:") ? reason.substring("provider:".length())
                        : "Локальный AI-сервер недоступен. Проверьте, что компьютер и телефон находятся в одной сети, а server/start.ps1 запущен.\n\nТехническая причина: " + reason;
            }
            String finalAnalysis = analysis;
            String finalFailure = failure;
            handler.post(() -> {
                if (isDestroyed()) return;
                aiLoading = false;
                if (finalAnalysis != null) {
                    aiError = "";
                    preferences.edit().putString("ai_last_analysis", finalAnalysis)
                            .putLong("ai_last_generated", System.currentTimeMillis()).apply();
                } else aiError = finalFailure;
                renderedSignatures.remove("assistant");
                if ("assistant".equals(requestedRoute)) showCachedNativeData("assistant");
            });
        }, "topjournal-ai").start();
    }

    private View createAiCoverageRow(int iconRes, String title, boolean ready) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(5), dp(8), dp(5), dp(8));
        row.setMinimumHeight(dp(58));

        FrameLayout iconShell = new FrameLayout(this);
        iconShell.setBackground(roundedDrawable(COLOR_SURFACE, 7));
        ImageView icon = new ImageView(this);
        icon.setImageResource(iconRes);
        icon.setColorFilter(ready ? COLOR_BLACK : Color.rgb(155, 155, 155));
        icon.setPadding(dp(8), dp(8), dp(8), dp(8));
        iconShell.addView(icon, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        row.addView(iconShell, new LinearLayout.LayoutParams(dp(38), dp(38)));

        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        copy.setPadding(dp(12), 0, dp(8), 0);
        copy.addView(text(title, 14, COLOR_BLACK, Typeface.BOLD));
        copy.addView(text(ready ? "Данные загружены" : "Ожидает синхронизации", 11, COLOR_MUTED, Typeface.NORMAL));
        row.addView(copy, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        View dot = new View(this);
        dot.setBackground(roundedDrawable(ready ? COLOR_SUCCESS : Color.rgb(185, 185, 185), 99));
        row.addView(dot, new LinearLayout.LayoutParams(dp(7), dp(7)));
        return row;
    }

    private JSONObject buildAiSnapshot() {
        JSONObject snapshot = new JSONObject();
        try {
            snapshot.put("capturedAt", new java.util.Date().toString());
            snapshot.put("balances", new JSONObject()
                    .put("score", cachedHeader("score"))
                    .put("coins", cachedHeader("coins"))
                    .put("gems", cachedHeader("gems")));
            for (String route : Arrays.asList("home", "schedule", "grades", "tasks", "rewards")) {
                JSONObject source = nativeDataCache.get(route);
                if (source != null) snapshot.put(route, sanitizeAiValue(source));
            }
        } catch (JSONException ignored) { }
        return snapshot;
    }

    private CharSequence formatAiAnalysis(String source) {
        StringBuilder html = new StringBuilder();
        for (String rawLine : source.split("\\r?\\n")) {
            String line = rawLine.trim();
            if (line.isEmpty()) {
                html.append("<br>");
                continue;
            }
            boolean heading = line.startsWith("#");
            while (line.startsWith("#")) line = line.substring(1).trim();
            boolean bullet = line.startsWith("- ") || line.startsWith("* ") || line.startsWith("• ");
            if (bullet) line = line.substring(2).trim();
            String escaped = TextUtils.htmlEncode(line).replaceAll("\\*\\*([^*]+)\\*\\*", "<b>$1</b>");
            if (heading) html.append("<b>").append(escaped).append("</b>");
            else if (bullet) html.append("• ").append(escaped);
            else html.append(escaped);
            html.append("<br>");
        }
        return android.text.Html.fromHtml(html.toString(), android.text.Html.FROM_HTML_MODE_LEGACY);
    }

    private Object sanitizeAiValue(Object value) throws JSONException {
        if (value instanceof JSONObject) {
            JSONObject source = (JSONObject) value;
            JSONObject clean = new JSONObject();
            java.util.Iterator<String> keys = source.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                String lower = key.toLowerCase(Locale.ROOT);
                if (key.startsWith("_") || lower.contains("token") || lower.contains("authorization")
                        || lower.contains("password") || lower.contains("avatar") || lower.contains("email")
                        || lower.contains("phone")) continue;
                clean.put(key, sanitizeAiValue(source.opt(key)));
            }
            return clean;
        }
        if (value instanceof JSONArray) {
            JSONArray source = (JSONArray) value;
            JSONArray clean = new JSONArray();
            for (int i = 0; i < source.length(); i++) clean.put(sanitizeAiValue(source.opt(i)));
            return clean;
        }
        return value == null ? JSONObject.NULL : value;
    }

    private SSLContext createAiSslContext() throws Exception {
        CertificateFactory factory = CertificateFactory.getInstance("X.509");
        Certificate certificate;
        try (InputStream input = getResources().openRawResource(R.raw.topjournal_ai_ca)) {
            certificate = factory.generateCertificate(input);
        }
        KeyStore store = KeyStore.getInstance(KeyStore.getDefaultType());
        store.load(null, null);
        store.setCertificateEntry("topjournal-ai", certificate);
        TrustManagerFactory managers = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        managers.init(store);
        SSLContext context = SSLContext.getInstance("TLS");
        context.init(null, managers.getTrustManagers(), null);
        return context;
    }

    private String readLimited(InputStream input, int maximum) throws Exception {
        if (input == null) return "{}";
        try (InputStream stream = input; ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            int total = 0;
            int count;
            while ((count = stream.read(buffer)) != -1) {
                total += count;
                if (total > maximum) throw new IllegalStateException("response_too_large");
                output.write(buffer, 0, count);
            }
            return output.toString(StandardCharsets.UTF_8.name());
        }
    }

    private LinearLayout createCommandCenter(JSONObject tasks) {
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(20), dp(20), dp(20), dp(18));
        panel.setBackground(roundedDrawable(COLOR_BLACK, 10));

        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        copy.addView(monoText("TASK CONTROL", 10, Color.rgb(160, 160, 160), Typeface.NORMAL));
        TextView title = text("Что нужно сделать", 21, Color.WHITE, Typeface.BOLD);
        title.setPadding(0, dp(5), 0, 0);
        copy.addView(title);
        top.addView(copy, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView action = text("Открыть  →", 12, COLOR_BLACK, Typeface.BOLD);
        action.setGravity(Gravity.CENTER);
        action.setPadding(dp(12), dp(9), dp(12), dp(9));
        action.setBackground(roundedDrawable(Color.WHITE, 7));
        action.setOnClickListener(v -> navigateByKey("tasks"));
        enablePressAnimation(action);
        top.addView(action);
        panel.addView(top);

        TextView total = text(tasks == null ? "—" : tasks.optString("open", "0"), 54, Color.WHITE, Typeface.BOLD);
        total.setIncludeFontPadding(false);
        total.setPadding(0, dp(18), 0, dp(4));
        panel.addView(total);
        panel.addView(text("активных заданий", 13, Color.rgb(170, 170, 170), Typeface.NORMAL));

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, dp(18), 0, 0);
        row.addView(createControlMetric("На проверке", tasks == null ? "—" : tasks.optString("inspection", "0"), false), weightedParams(0, 4));
        row.addView(createControlMetric("Просрочено", tasks == null ? "—" : tasks.optString("expired", "0"), true), weightedParams(4, 0));
        panel.addView(row);
        return panel;
    }

    private LinearLayout createControlMetric(String label, String value, boolean danger) {
        LinearLayout metric = new LinearLayout(this);
        metric.setGravity(Gravity.CENTER_VERTICAL);
        metric.setPadding(dp(12), dp(10), dp(12), dp(10));
        metric.setBackground(roundedDrawable(Color.rgb(28, 28, 28), 7));
        TextView labelView = text(label, 11, Color.rgb(180, 180, 180), Typeface.NORMAL);
        metric.addView(labelView, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        metric.addView(text(value, 16, danger ? Color.rgb(255, 125, 125) : Color.WHITE, Typeface.BOLD));
        return metric;
    }

    private LinearLayout createBalanceStrip() {
        LinearLayout strip = new LinearLayout(this);
        strip.setOrientation(LinearLayout.HORIZONTAL);
        strip.setPadding(dp(4), dp(4), dp(4), dp(4));
        strip.setBackground(borderedDrawable(COLOR_CARD, COLOR_BORDER, 8));
        strip.addView(createBalanceItem("Балл", cachedHeader("score")), new LinearLayout.LayoutParams(0, dp(54), 1f));
        strip.addView(createBalanceItem("Топкоины", cachedHeader("coins")), new LinearLayout.LayoutParams(0, dp(54), 1f));
        strip.addView(createBalanceItem("Гемы", cachedHeader("gems")), new LinearLayout.LayoutParams(0, dp(54), 1f));
        return strip;
    }

    private LinearLayout createBalanceItem(String label, String value) {
        LinearLayout item = new LinearLayout(this);
        item.setOrientation(LinearLayout.VERTICAL);
        item.setGravity(Gravity.CENTER);
        item.addView(monoText(value, 14, COLOR_BLACK, Typeface.BOLD));
        item.addView(text(label, 10, COLOR_MUTED, Typeface.NORMAL));
        return item;
    }

    private LinearLayout createDashboardMetric(String label, String value, String note, int accent) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setMinimumHeight(dp(132));
        card.setBackground(borderedDrawable(COLOR_CARD, COLOR_BORDER, 8));
        View bar = new View(this);
        bar.setBackgroundColor(accent);
        card.addView(bar, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(3)));
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(15), dp(13), dp(15), dp(14));
        content.addView(text(label, 12, COLOR_MUTED, Typeface.NORMAL));
        TextView valueView = text(value == null || value.isEmpty() ? "—" : value, 30, COLOR_BLACK, Typeface.BOLD);
        valueView.setPadding(0, dp(10), 0, dp(6));
        content.addView(valueView);
        content.addView(text(note, 10, COLOR_MUTED, Typeface.NORMAL));
        card.addView(content);
        return card;
    }

    private LinearLayout createHeroMetric(String label, String value) {
        LinearLayout metric = new LinearLayout(this);
        metric.setGravity(Gravity.CENTER_VERTICAL);
        metric.setPadding(dp(13), dp(11), dp(13), dp(11));
        metric.setBackground(borderedDrawable(COLOR_CARD, Color.rgb(192, 192, 192), 10));
        TextView labelView = monoText(label.toUpperCase(new Locale("ru")), 9, COLOR_MUTED, Typeface.NORMAL);
        labelView.setMaxLines(1);
        metric.addView(labelView, new LinearLayout.LayoutParams(0, dp(28), 1f));
        int valueColor = "Просрочено".equals(label) ? COLOR_DANGER : COLOR_BLACK;
        TextView valueView = serifText(value, 18, valueColor, Typeface.BOLD);
        valueView.setGravity(Gravity.CENTER);
        metric.addView(value == null ? missingData(28) : valueView, new LinearLayout.LayoutParams(dp(28), dp(28)));
        return metric;
    }

    private LinearLayout createNativeStatCard(String label, String value, String note, boolean dark) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(16), dp(16), dp(15));
        card.setMinimumHeight(dp(146));
        card.setBackground(borderedDrawable(dark ? COLOR_BLACK : COLOR_CARD, COLOR_BORDER, 8));
        TextView labelView = monoText(label.toUpperCase(new Locale("ru")), 10,
                dark ? Color.rgb(160, 160, 160) : COLOR_MUTED, Typeface.BOLD);
        labelView.setMaxLines(2);
        card.addView(labelView);
        TextView valueView = serifText(value, 36, dark ? Color.WHITE : COLOR_BLACK, Typeface.BOLD);
        valueView.setIncludeFontPadding(false);
        LinearLayout.LayoutParams valueParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        valueParams.topMargin = dp(18);
        if (value == null) { valueParams.height = dp(42); card.addView(missingData(42), valueParams); }
        else card.addView(valueView, valueParams);
        TextView noteView = text(note, 12, dark ? Color.rgb(160, 160, 160) : COLOR_MUTED, Typeface.NORMAL);
        noteView.setMaxLines(2);
        LinearLayout.LayoutParams noteParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        noteParams.topMargin = dp(8);
        card.addView(noteView, noteParams);
        return card;
    }

    private LinearLayout createNativeChartCard(String label, String value, String note,
                                                JSONArray values, boolean dark) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(18), dp(18), dp(18), dp(16));
        card.setBackground(borderedDrawable(dark ? COLOR_BLACK : COLOR_CARD, COLOR_BORDER, 8));

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        TextView labelView = monoText(label.toUpperCase(new Locale("ru")), 10,
                dark ? Color.rgb(160, 160, 160) : COLOR_MUTED, Typeface.BOLD);
        header.addView(labelView, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView valueView = text(value == null || value.trim().isEmpty() ? "—" : value,
                29, dark ? Color.WHITE : COLOR_BLACK, Typeface.BOLD);
        valueView.setTypeface(Typeface.create("serif", Typeface.BOLD));
        valueView.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        if (value == null) header.addView(missingData(30), new LinearLayout.LayoutParams(dp(55), dp(30)));
        else header.addView(valueView);
        card.addView(header);

        float[] series = jsonFloatArray(values);
        MiniChartView chart = new MiniChartView(this, series, dark);
        LinearLayout.LayoutParams chartParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(116));
        chartParams.topMargin = dp(12);
        card.addView(values == null ? missingData(116) : chart, chartParams);

        TextView noteView = text(series.length > 1 ? note : "График появится после обновления данных",
                11, dark ? Color.rgb(160, 160, 160) : COLOR_MUTED, Typeface.NORMAL);
        noteView.setGravity(Gravity.CENTER_VERTICAL);
        card.addView(noteView);
        return card;
    }

    private float[] jsonFloatArray(JSONArray values) {
        if (values == null) return new float[0];
        List<Float> output = new ArrayList<>();
        for (int i = 0; i < values.length(); i++) {
            double value = values.optDouble(i, Double.NaN);
            if (!Double.isNaN(value) && !Double.isInfinite(value)) output.add((float) value);
        }
        float[] result = new float[output.size()];
        for (int i = 0; i < output.size(); i++) result[i] = output.get(i);
        return result;
    }

    private final class MiniChartView extends View {
        private final float[] values;
        private final boolean dark;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path linePath = new Path();
        private final Path fillPath = new Path();

        MiniChartView(Context context, float[] values, boolean dark) {
            super(context);
            this.values = values == null ? new float[0] : values;
            this.dark = dark;
            setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float left = dp(2), right = getWidth() - dp(2);
            float top = dp(8), bottom = getHeight() - dp(12);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dp(1));
            paint.setColor(Color.rgb(210, 207, 198));
            for (int i = 0; i < 3; i++) {
                float y = top + (bottom - top) * i / 2f;
                canvas.drawLine(left, y, right, y, paint);
            }
            if (values.length < 2) {
                paint.setColor(Color.rgb(160, 160, 160));
                paint.setStrokeWidth(dp(2));
                paint.setStrokeCap(Paint.Cap.ROUND);
                canvas.drawLine(left, (top + bottom) / 2f, right * .33f, (top + bottom) / 2f, paint);
                return;
            }
            float min = values[0], max = values[0];
            for (float value : values) { min = Math.min(min, value); max = Math.max(max, value); }
            if (Math.abs(max - min) < .001f) { min -= 1f; max += 1f; }
            linePath.reset();
            fillPath.reset();
            for (int i = 0; i < values.length; i++) {
                float x = left + (right - left) * i / (values.length - 1f);
                float y = bottom - (values[i] - min) / (max - min) * (bottom - top);
                if (i == 0) { linePath.moveTo(x, y); fillPath.moveTo(x, bottom); fillPath.lineTo(x, y); }
                else { linePath.lineTo(x, y); fillPath.lineTo(x, y); }
            }
            fillPath.lineTo(right, bottom); fillPath.close();
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.argb(24, 51, 51, 51));
            canvas.drawPath(fillPath, paint);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dp(3));
            paint.setStrokeJoin(Paint.Join.ROUND);
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setColor(COLOR_BLACK);
            canvas.drawPath(linePath, paint);
            float lastX = right;
            float lastY = bottom - (values[values.length - 1] - min) / (max - min) * (bottom - top);
            paint.setStyle(Paint.Style.FILL);
            canvas.drawCircle(lastX, lastY, dp(4), paint);
        }
    }

    private LinearLayout createNativeListContainer() {
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(dp(14), dp(6), dp(14), dp(6));
        list.setBackground(borderedDrawable(COLOR_CARD, COLOR_BORDER, 8));
        return list;
    }

    private View createNativeListRow(String rank, String title, String subtitle, String pill) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(10), 0, dp(10));
        row.setMinimumHeight(dp(66));

        TextView rankView = monoText(rank.isEmpty() ? "•" : rank, 12, COLOR_BLACK, Typeface.BOLD);
        rankView.setGravity(Gravity.CENTER);
        rankView.setMaxLines(1);
        rankView.setBackground(roundedDrawable(COLOR_BLACK, 6));
        rankView.setTextColor(Color.WHITE);
        row.addView(rankView, new LinearLayout.LayoutParams(dp(38), dp(38)));

        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        copy.setPadding(dp(12), 0, dp(8), 0);
        TextView titleView = text(title, 14, COLOR_BLACK, Typeface.BOLD);
        titleView.setMaxLines(2);
        titleView.setEllipsize(TextUtils.TruncateAt.END);
        copy.addView(titleView);
        TextView subtitleView = text(subtitle, 11, COLOR_MUTED, Typeface.NORMAL);
        subtitleView.setMaxLines(1);
        subtitleView.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams subtitleParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        subtitleParams.topMargin = dp(3);
        copy.addView(subtitleView, subtitleParams);
        row.addView(copy, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        if (pill != null && !pill.trim().isEmpty()) {
            TextView pillView = text(pill.trim(), 11, COLOR_BLACK, Typeface.BOLD);
            pillView.setGravity(Gravity.CENTER);
            pillView.setMaxLines(1);
            pillView.setPadding(dp(9), dp(6), dp(9), dp(6));
            pillView.setBackground(borderedDrawable(COLOR_BACKGROUND, Color.rgb(192, 192, 192), 99));
            row.addView(pillView);
        }
        return row;
    }

    private TextView createEmptyNativeState(String message) {
        TextView empty = text(message, 14, COLOR_MUTED, Typeface.NORMAL);
        empty.setGravity(Gravity.CENTER);
        empty.setPadding(dp(18), dp(24), dp(18), dp(24));
        return empty;
    }

    private LinearLayout createShortcut(String title, String subtitle, String route) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(17), dp(15), dp(17), dp(15));
        card.setMinimumHeight(dp(102));
        card.setBackground(borderedDrawable(COLOR_CARD, COLOR_BORDER, 8));
        TextView titleView = serifText(title, 17, COLOR_BLACK, Typeface.BOLD);
        card.addView(titleView);
        TextView subtitleView = text(subtitle, 12, COLOR_MUTED, Typeface.NORMAL);
        LinearLayout.LayoutParams subtitleParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        subtitleParams.topMargin = dp(5);
        card.addView(subtitleView, subtitleParams);
        card.setOnClickListener(v -> navigateByKey(route));
        enablePressAnimation(card);
        return card;
    }

    private void addNativeSectionTitle(String title) {
        TextView heading = serifText(title, 22, COLOR_BLACK, Typeface.BOLD);
        heading.setIncludeFontPadding(false);
        addNativeBlock(heading, 13);
    }

    private void addNativeBlock(View view, int bottomMarginDp) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.bottomMargin = dp(bottomMarginDp);
        nativeHomeContent.addView(view, params);
    }

    private LinearLayout.LayoutParams weightedParams(int leftMarginDp, int rightMarginDp) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        params.leftMargin = dp(leftMarginDp);
        params.rightMargin = dp(rightMarginDp);
        return params;
    }

    private GradientDrawable roundedDrawable(int color, float radiusDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radiusDp));
        return drawable;
    }

    private GradientDrawable borderedDrawable(int color, int strokeColor, float radiusDp) {
        GradientDrawable drawable = roundedDrawable(color, radiusDp);
        drawable.setStroke(dp(1), strokeColor);
        return drawable;
    }

    private void enablePressAnimation(View view) {
        view.setOnTouchListener((target, event) -> {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                target.animate().scaleX(0.97f).scaleY(0.97f).setDuration(90).start();
            } else if (event.getAction() == MotionEvent.ACTION_UP) {
                target.animate().scaleX(1f).scaleY(1f).setDuration(150).start();
                target.performClick();
            } else if (event.getAction() == MotionEvent.ACTION_CANCEL) {
                target.animate().scaleX(1f).scaleY(1f).setDuration(150).start();
            }
            return true;
        });
    }

    private void animateNativeHomeContent() {
        if (quietRender) return;
        for (int i = 0; i < nativeHomeContent.getChildCount(); i++) {
            View child = nativeHomeContent.getChildAt(i);
            child.animate().cancel();
            child.setAlpha(0f);
            child.setTranslationY(dp(14));
            child.animate().alpha(1f).translationY(0).setStartDelay(Math.min(i * 42L, 460L))
                    .setDuration(280).start();
        }
    }

    private String navigationTab(String route) {
        return Arrays.asList("home", "schedule", "assistant", "grades", "tasks").contains(route) ? route : "more";
    }

    private boolean isNativeRoute(String route) {
        return NATIVE_ROUTES.contains(route);
    }

    private void showNativeLoading(String route) {
        showCachedNativeData(route);
    }

    private boolean showCachedNativeData(String route) {
        if (!isNativeRoute(route) || nativeHomeContent == null) return false;
        JSONObject data = nativeDataCache.get(route);
        if (data == null) data = new JSONObject();
        String message = refreshMessages.getOrDefault(route, signedIn ? "Обновляем данные…" : "Подключаемся к дневнику…");
        if (nativeDataCache.containsKey(route) && !message.startsWith("Обновлено")) {
            JSONObject sources = data.optJSONObject("_sources");
            long oldest = Long.MAX_VALUE;
            if (sources != null) {
                java.util.Iterator<String> keys = sources.keys();
                while (keys.hasNext()) oldest = Math.min(oldest, sources.optLong(keys.next(), Long.MAX_VALUE));
            }
            if (oldest != Long.MAX_VALUE) message += "\nСохранено: " + android.text.format.DateFormat.format("dd.MM HH:mm", oldest);
        }
        String signature = data.toString() + message + ":" + taskStatus + ":" + taskPage + ":"
                + selectedScheduleDay + ":" + materialType + ":" + materialRecommended + ":"
                + expandedNews + ":" + rewardFilter + ":" + aiLoading + ":" + aiError + ":"
                + preferences.getLong("ai_last_generated", 0);
        if (route.equals(nativeScreenRoute) && signature.equals(renderedSignatures.get(route))) {
            updateNativeHomeVisibility();
            return nativeDataCache.containsKey(route);
        }
        boolean sameRoute = route.equals(nativeScreenRoute);
        int scroll = sameRoute ? nativeHomeView.getScrollY() : 0;
        quietRender = sameRoute;
        nativeScreenRoute = route;
        renderedSignatures.put(route, signature);
        if ("home".equals(route)) renderNativeHome(data);
        else if ("schedule".equals(route)) renderNativeSchedule(data);
        else if ("assistant".equals(route)) renderNativeAssistant();
        else if ("grades".equals(route)) renderNativeGrades(data);
        else if ("tasks".equals(route)) renderNativeTasks(data);
        else renderNativeExtra(route, data);
        if (!"assistant".equals(route)) addRefreshStatus(route, message);
        quietRender = false;
        nativeHomeView.post(() -> { if (route.equals(nativeScreenRoute)) nativeHomeView.scrollTo(0, scroll); });
        updateNativeHomeVisibility();
        return nativeDataCache.containsKey(route);
    }

    private void syncNativeRoute() { syncSnapshot(requestedRoute); }

    private void showHybridPlaceholder(boolean failed) {
        hybridLoading = true;
        if (!failed) refreshMessages.put(requestedRoute, "Открываем раздел…");
        nativeScreenRoute = requestedRoute;
        addNativeScreenHeading("ДНЕВНИК", "material_detail".equals(requestedRoute) ? "Учебный материал" : labelForRoute(requestedRoute),
                failed ? "Не удалось соединиться с дневником" : "Открываем раздел…");
        if (failed) {
            LinearLayout card = editorialCard("СОЕДИНЕНИЕ", "Страница пока недоступна", "Повторите загрузку, когда соединение восстановится.");
            cardAction(card, "Повторить", () -> {
                connectionUnavailable = false;
                showHybridPlaceholder(false);
                webView.reload();
            });
            addNativeBlock(card, 18);
        } else addListLoading("Загрузка");
        updateNativeHomeVisibility();
    }

    private void showConnectionFailure() {
        connectionUnavailable = true;
        if (!shellVisible || isDestroyed()) return;
        for (String route : NATIVE_ROUTES) {
            refreshMessages.put(route, "Нет соединения с дневником · Повторить");
        }
        if (isNativeRoute(requestedRoute)) showCachedNativeData(requestedRoute);
        else {
            navigationInFlight = false;
            showHybridPlaceholder(true);
        }
        finishHeaderLoading();
    }

    private void finishHeaderLoading() {
        for (Map.Entry<TextView, SkeletonView> entry : headerSkeletons.entrySet()) {
            if (entry.getValue().getVisibility() == View.VISIBLE) {
                entry.getValue().setVisibility(View.GONE);
                entry.getKey().setVisibility(View.VISIBLE);
            }
        }
    }

    private String readAsset(String name) {
        try (InputStream input = getAssets().open(name); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            int count;
            while ((count = input.read(buffer)) != -1) out.write(buffer, 0, count);
            return out.toString("UTF-8");
        } catch (java.io.IOException error) { throw new IllegalStateException("Missing bundled asset: " + name, error); }
    }

    private void restoreNativeCache() {
        for (String route : NATIVE_ROUTES) {
            try {
                JSONObject data = NativeSnapshot.restore(screenPreferences.getString(route, "{}"), System.currentTimeMillis());
                if (data != null) nativeDataCache.put(route, data);
            } catch (JSONException ignored) { screenPreferences.edit().remove(route).apply(); }
        }
    }

    private void clearNativeCache() {
        dismissLessonEvaluation();
        evaluationSubmitting = false;
        evaluationsDeferredForSession = false;
        nativeDataCache.clear();
        renderedSignatures.clear();
        refreshMessages.clear();
        nativeRequests.clear();
        requestVariants.clear();
        avatarRequestUrl = "";
        nativeScreenRoute = "";
        screenPreferences.edit().clear().apply();
        preferences.edit().remove("last_score").apply();
        loadedAvatarUrl = "";
        avatarRequestUrl = "";
        avatarRetryAt = 0;
        if (avatarImage != null) { avatarImage.setImageDrawable(null); avatarImage.setVisibility(View.GONE); }
        if (avatarFallback != null) { avatarFallback.setText("Я"); avatarFallback.setVisibility(View.VISIBLE); }
        for (TextView value : headerSkeletons.keySet()) { value.setText("—"); value.setVisibility(View.INVISIBLE); headerSkeletons.get(value).setVisibility(View.VISIBLE); }
    }

    private void updateHeaderMetric(TextView view, String key, String value) {
        if (value.isEmpty()) return;
        view.setText(value);
        view.setVisibility(View.VISIBLE);
        SkeletonView skeleton = headerSkeletons.get(view);
        if (skeleton != null) skeleton.setVisibility(View.GONE);
        screenPreferences.edit().putString("header_" + key, value).putLong("header_time", System.currentTimeMillis()).apply();
    }

    private void addRefreshStatus(String route, String message) {
        refreshStatus = text(message, 12, COLOR_MUTED, Typeface.NORMAL);
        refreshStatus.setPadding(dp(4), dp(5), dp(4), dp(12));
        refreshStatus.setMinHeight(dp(44));
        refreshStatus.setOnClickListener(v -> {
            if (signedIn) requestNativeData(route, true);
            else if ("expired".equals(currentSessionState)) openJournalLogin();
            else if (webView != null) {
                connectionUnavailable = false;
                refreshMessages.remove(route);
                showCachedNativeData(route);
                webView.reload();
            }
        });
        nativeHomeContent.addView(refreshStatus, Math.min(3, nativeHomeContent.getChildCount()));
        if (!signedIn) {
            TextView login = text("Войти в дневник", 15, Color.WHITE, Typeface.BOLD);
            login.setGravity(Gravity.CENTER);
            login.setBackground(roundedDrawable(COLOR_BLACK, 16));
            login.setMinHeight(dp(48));
            login.setOnClickListener(v -> openJournalLogin());
            nativeHomeContent.addView(login, 2);
        }
        if (message.contains("Повторить") || !signedIn) {
            TextView diagnostics = text("Диагностика загрузки", 13, COLOR_BLACK, Typeface.BOLD);
            diagnostics.setMinHeight(dp(44));
            diagnostics.setGravity(Gravity.CENTER_VERTICAL);
            diagnostics.setOnClickListener(v -> showLoadDiagnostics());
            nativeHomeContent.addView(diagnostics, 2);
        }
    }

    private void showLoadDiagnostics() {
        if (webView == null) return;
        webView.evaluateJavascript("window.__tjDiagnostic?window.__tjDiagnostic():JSON.stringify({bridge:0})", raw -> {
            if (isDestroyed()) return;
            String report = "Top Journal 0.26.0" + "\nAndroid " + Build.VERSION.RELEASE
                    + "\nРаздел: " + requestedRoute + "\n";
            android.content.pm.PackageInfo provider = WebView.getCurrentWebViewPackage();
            if (provider != null) report += "WebView " + provider.versionName + "\n";
            try {
                JSONObject info = new JSONObject(decodeJavascriptString(raw));
                report += "Модуль: " + info.optInt("bridge") + "\nЗаголовок авторизации обнаружен: " + info.optBoolean("authObserved")
                        + "\nПроверка сессии: " + info.optString("sessionState", "неизвестно")
                        + "\nРежим cookies: " + info.optString("credentialMode", "неизвестно")
                        + "\nПолучено ответов: " + info.optInt("sourceCount") + "\n";
                JSONArray recent = info.optJSONArray("recent");
                if (recent != null) for (int i = 0; i < recent.length(); i++) {
                    JSONObject item = recent.optJSONObject(i);
                    if (item != null) report += item.optString("path") + " → " + item.optInt("status") + " " + item.optString("reason") + "\n";
                }
            } catch (JSONException ignored) { report += "Модуль не вернул диагностический отчёт"; }
            final String sanitizedReport = report;
            new android.app.AlertDialog.Builder(this).setTitle("Диагностика загрузки")
                    .setMessage(report).setPositiveButton("Скопировать", (dialog, which) -> {
                        ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                        clipboard.setPrimaryClip(ClipData.newPlainText("Диагностика дневника", sanitizedReport));
                        Toast.makeText(this, "Отчёт скопирован. Паролей и токенов в нём нет", Toast.LENGTH_LONG).show();
                    }).setNegativeButton("Закрыть", null).show();
        });
    }

    private View missingData(int height) {
        String status = refreshMessages.getOrDefault(nativeScreenRoute, "");
        if (status.contains("Повторить") || "expired".equals(currentSessionState)) {
            TextView error = text("—", 20, COLOR_MUTED, Typeface.NORMAL);
            error.setMaxLines(height <= 54 ? 1 : 2);
            error.setGravity(Gravity.CENTER);
            error.setMinHeight(dp(height));
            return error;
        }
        SkeletonView view = new SkeletonView(12, 0);
        view.setMinimumHeight(dp(height));
        return view;
    }

    private View loadingCard(String label, int height) {
        LinearLayout card = createNativeListContainer();
        TextView title = text(label, 14, COLOR_MUTED, Typeface.BOLD);
        card.addView(title);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(height));
        params.topMargin = dp(14);
        card.addView(missingData(height), params);
        return card;
    }

    private void addNativeScreenHeading(String kicker, String title, String subtitle) {
        nativeHomeContent.removeAllViews();
        TextView eyebrow = monoText(kicker, 10, COLOR_MUTED, Typeface.BOLD);
        addNativeBlock(eyebrow, 8);
        TextView heading = serifText(title, 31, COLOR_BLACK, Typeface.BOLD);
        heading.setIncludeFontPadding(false);
        addNativeBlock(heading, subtitle == null || subtitle.isEmpty() ? 22 : 5);
        if (subtitle != null && !subtitle.isEmpty()) {
            TextView subheading = text(subtitle, 14, COLOR_MUTED, Typeface.NORMAL);
            addNativeBlock(subheading, 22);
        }
    }

    private void renderNativeSchedule(JSONObject data) {
        addNativeScreenHeading("НЕДЕЛЯ", "Расписание", "Выберите день — ниже останутся только его занятия");
        JSONArray items = data.optJSONArray("items");
        if (items == null) {
            addNativeBlock(loadingCard("Расписание", 250), 22);
            animateNativeHomeContent();
            return;
        }
        if (items.length() == 0) {
            addNativeBlock(editorialCard("РАСПИСАНИЕ", "Занятий пока нет", "На выбранный период расписание не опубликовано."), 22);
        } else {
            addNativeScheduleWeek(items);
        }

        if (!quietRender) nativeHomeView.scrollTo(0, 0);
        animateNativeHomeContent();
    }

    private void addNativeScheduleWeek(JSONArray items) {
        Map<Integer, List<JSONObject>> byDay = new LinkedHashMap<>();
        for (int day = 1; day <= 7; day++) byDay.put(day, new ArrayList<>());
        for (int i = 0; i < items.length(); i++) {
            JSONObject item = items.optJSONObject(i);
            if (item == null) continue;
            int day = scheduleDayOfWeek(item.optString("date"));
            if (day >= 1 && day <= 7) byDay.get(day).add(item);
        }
        for (List<JSONObject> dayItems : byDay.values()) {
            Collections.sort(dayItems, Comparator.comparing(item -> joinTime(item.optString("start"), item.optString("end"))));
        }

        int today = java.util.Calendar.getInstance().get(java.util.Calendar.DAY_OF_WEEK)
                - java.util.Calendar.MONDAY + 1;
        if (today < 1) today = 7;
        if (selectedScheduleDay < 1 || selectedScheduleDay > 7) selectedScheduleDay = today;

        LinearLayout dayStrip = new LinearLayout(this);
        dayStrip.setGravity(Gravity.CENTER_VERTICAL);
        dayStrip.setPadding(dp(4), dp(4), dp(4), dp(4));
        dayStrip.setBackground(borderedDrawable(COLOR_CARD, COLOR_BORDER, 8));
        for (int day = 1; day <= 7; day++) {
            final int selectedDay = day;
            boolean active = day == selectedScheduleDay;
            String count = byDay.get(day).isEmpty() ? "—" : String.valueOf(byDay.get(day).size());
            TextView button = monoText(scheduleDayShort(day) + "\n" + count, 11,
                    active ? Color.WHITE : COLOR_MUTED, Typeface.BOLD);
            button.setGravity(Gravity.CENTER);
            button.setPadding(dp(2), dp(8), dp(2), dp(8));
            button.setMinHeight(dp(54));
            button.setBackground(roundedDrawable(active ? COLOR_BLACK : Color.TRANSPARENT, 6));
            button.setContentDescription(scheduleDayName(day) + ", занятий: " + count
                    + (active ? ", выбрано" : ""));
            button.setOnClickListener(v -> {
                if (selectedScheduleDay == selectedDay) return;
                selectedScheduleDay = selectedDay;
                renderedSignatures.remove("schedule");
                showCachedNativeData("schedule");
            });
            enablePressAnimation(button);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(54), 1f);
            if (day < 7) params.rightMargin = dp(2);
            dayStrip.addView(button, params);
        }
        addNativeBlock(dayStrip, 16);

        List<JSONObject> dayItems = byDay.get(selectedScheduleDay);
        LinearLayout card = createNativeListContainer();
        card.setPadding(dp(14), dp(16), dp(14), dp(8));
        LinearLayout heading = new LinearLayout(this);
        heading.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = text(scheduleDayName(selectedScheduleDay), 20, COLOR_BLACK, Typeface.BOLD);
        heading.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView count = monoText(dayItems.size() + " " + lessonsWord(dayItems.size()), 10, COLOR_MUTED, Typeface.NORMAL);
        count.setPadding(dp(9), dp(6), dp(9), dp(6));
        count.setBackground(roundedDrawable(COLOR_SURFACE, 99));
        heading.addView(count);
        card.addView(heading);
        String dateLabel = scheduleDateLabel(dayItems);
        if (!dateLabel.isEmpty()) {
            TextView date = text(dateLabel, 12, COLOR_MUTED, Typeface.NORMAL);
            date.setPadding(0, dp(4), 0, dp(14));
            card.addView(date);
        } else title.setPadding(0, 0, 0, dp(14));
        addScheduleHeaderRow(card);
        if (dayItems.isEmpty()) {
            TextView empty = text("Свободный день", 14, COLOR_MUTED, Typeface.NORMAL);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(0, dp(34), 0, dp(34));
            card.addView(empty);
        } else for (JSONObject item : dayItems) addScheduleTableRow(card, item);
        addNativeBlock(card, 18);
    }

    private void addScheduleHeaderRow(LinearLayout parent) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(9), 0, dp(9));
        row.setBackground(roundedDrawable(COLOR_SURFACE, 6));
        addScheduleCell(row, "ВРЕМЯ", 0.22f, COLOR_MUTED, Typeface.BOLD);
        addScheduleCell(row, "ЗАНЯТИЕ", 0.53f, COLOR_MUTED, Typeface.BOLD);
        addScheduleCell(row, "АУД.", 0.25f, COLOR_MUTED, Typeface.BOLD);
        parent.addView(row);
    }

    private void addScheduleTableRow(LinearLayout parent, JSONObject item) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(dp(62));
        row.setPadding(0, dp(7), 0, dp(7));
        String time = joinTime(item.optString("start"), item.optString("end"));
        addScheduleCell(row, time.isEmpty() ? "—" : time, 0.22f, COLOR_BLACK, Typeface.BOLD);

        LinearLayout subject = new LinearLayout(this);
        subject.setOrientation(LinearLayout.VERTICAL);
        subject.setPadding(dp(8), 0, dp(4), 0);
        TextView name = text(item.optString("title", "Занятие"), 13, COLOR_BLACK, Typeface.BOLD);
        name.setMaxLines(2);
        name.setEllipsize(TextUtils.TruncateAt.END);
        subject.addView(name);
        String teacher = item.optString("teacher");
        if (!teacher.isEmpty()) {
            TextView teacherView = text(teacher, 11, COLOR_MUTED, Typeface.NORMAL);
            teacherView.setMaxLines(1);
            teacherView.setEllipsize(TextUtils.TruncateAt.END);
            subject.addView(teacherView);
        }
        row.addView(subject, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, .53f));
        String room = item.optString("room", "—");
        TextView roomView = monoText(room.isEmpty() ? "—" : room, 10, COLOR_BLACK, Typeface.BOLD);
        roomView.setGravity(Gravity.CENTER);
        roomView.setMaxLines(2);
        roomView.setPadding(dp(7), dp(6), dp(7), dp(6));
        roomView.setBackground(borderedDrawable(COLOR_CARD, COLOR_BORDER, 6));
        LinearLayout roomSlot = new LinearLayout(this);
        roomSlot.setGravity(Gravity.CENTER);
        roomSlot.setPadding(dp(5), 0, dp(2), 0);
        roomSlot.addView(roomView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        row.addView(roomSlot, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, .25f));
        parent.addView(row);
        View divider = new View(this);
        divider.setBackgroundColor(COLOR_BORDER);
        parent.addView(divider, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1)));
    }

    private void addScheduleCell(LinearLayout row, String value, float weight, int color, int style) {
        TextView cell = monoText(value, 10, color, style);
        cell.setGravity(Gravity.CENTER_VERTICAL);
        cell.setPadding(dp(7), 0, dp(5), 0);
        cell.setMaxLines(2);
        cell.setEllipsize(TextUtils.TruncateAt.END);
        row.addView(cell, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, weight));
    }

    private String scheduleDayName(int day) {
        return new String[]{"Понедельник", "Вторник", "Среда", "Четверг", "Пятница", "Суббота", "Воскресенье"}[day - 1];
    }

    private String scheduleDayShort(int day) {
        return new String[]{"ПН", "ВТ", "СР", "ЧТ", "ПТ", "СБ", "ВС"}[day - 1];
    }

    private String lessonsWord(int count) {
        int mod10 = count % 10, mod100 = count % 100;
        if (mod10 == 1 && mod100 != 11) return "занятие";
        if (mod10 >= 2 && mod10 <= 4 && (mod100 < 12 || mod100 > 14)) return "занятия";
        return "занятий";
    }

    private int scheduleDayOfWeek(String raw) {
        if (raw == null) return -1;
        String value = raw.trim();
        String[] patterns = {"dd.MM.yyyy", "yyyy-MM-dd", "dd-MM-yyyy", "yyyy/MM/dd"};
        for (String pattern : patterns) {
            try {
                java.text.SimpleDateFormat format = new java.text.SimpleDateFormat(pattern, Locale.ROOT);
                format.setLenient(false);
                java.util.Date date = format.parse(value);
                if (date != null) {
                    java.util.Calendar calendar = java.util.Calendar.getInstance(Locale.ROOT);
                    calendar.setTime(date);
                    int result = calendar.get(java.util.Calendar.DAY_OF_WEEK) - java.util.Calendar.MONDAY + 1;
                    return result < 1 ? 7 : result;
                }
            } catch (java.text.ParseException ignored) { }
        }
        return -1;
    }

    private String scheduleDateLabel(List<JSONObject> items) {
        if (items == null || items.isEmpty()) return "";
        String first = items.get(0).optString("date");
        if (first.isEmpty()) return "";
        for (JSONObject item : items) if (!first.equals(item.optString("date"))) return "Несколько дат";
        return first;
    }

    private void renderNativeGrades(JSONObject data) {
        addNativeScreenHeading("АНАЛИТИКА", "Оценки", "Сводка по предметам и посещаемости");
        JSONArray items = data.optJSONArray("items");
        JSONArray series = data.optJSONArray("series");
        String average = items == null ? null : calculateAverageLabel(series);

        int attended = 0, absent = 0, late = 0;
        Map<String, double[]> subjectStats = new LinkedHashMap<>();
        if (items != null) {
            for (int i = 0; i < items.length(); i++) {
                JSONObject item = items.optJSONObject(i);
                if (item == null) continue;
                int status = item.optInt("status", -1);
                if (status == 0) absent++; else if (status == 1) attended++; else if (status == 2) late++;
                String subject = item.optString("title", "Без предмета").trim();
                if (subject.isEmpty()) subject = "Без предмета";
                double[] stat = subjectStats.computeIfAbsent(subject, ignored -> new double[2]);
                JSONArray marks = item.optJSONArray("marks");
                if (marks != null) for (int j = 0; j < marks.length(); j++) {
                    double mark = marks.optDouble(j, Double.NaN);
                    if (!Double.isNaN(mark)) { stat[0] += mark; stat[1]++; }
                }
            }
        }
        addNativeBlock(createGradeSummary(average, attended, absent, late, items == null), 26);

        addNativeSectionTitle("По предметам");
        LinearLayout subjects = createNativeListContainer();
        if (items == null) subjects.addView(missingData(160));
        else if (subjectStats.isEmpty()) subjects.addView(createEmptyNativeState("Предметная статистика появится после первых оценок"));
        else for (Map.Entry<String, double[]> entry : subjectStats.entrySet()) {
            double[] stat = entry.getValue();
            String value = stat[1] == 0 ? "—" : String.format(Locale.getDefault(), "%.1f", stat[0] / stat[1]);
            subjects.addView(createSubjectSummaryRow(entry.getKey(), value, (int) stat[1]));
        }
        addNativeBlock(subjects, 26);

        addNativeSectionTitle("Последние результаты");
        LinearLayout list = createNativeListContainer();
        if (items == null) {
            list.addView(missingData(210));
        } else if (items.length() == 0) {
            list.addView(createEmptyNativeState("Оценок пока нет. Обновите раздел после проверки преподавателем."));
        } else {
            for (int i = 0; i < Math.min(items.length(), 12); i++) {
                JSONObject item = items.optJSONObject(i);
                if (item == null) continue;
                JSONArray marks = item.optJSONArray("marks");
                String mark = marksLabel(marks);
                String subtitle = joinNonEmpty(" · ", item.optString("date"), item.optString("theme"));
                list.addView(createNativeListRow(String.valueOf(i + 1),
                        item.optString("title", "Занятие"), subtitle, mark));
            }
        }
        addNativeBlock(list, 18);
        if (!quietRender) nativeHomeView.scrollTo(0, 0);
        animateNativeHomeContent();
    }

    private LinearLayout createGradeSummary(String average, int attended, int absent, int late, boolean loading) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(20), dp(20), dp(20), dp(18));
        card.setBackground(roundedDrawable(COLOR_BLACK, 10));
        card.addView(monoText("CURRENT PERIOD", 10, Color.rgb(160, 160, 160), Typeface.NORMAL));
        LinearLayout score = new LinearLayout(this);
        score.setGravity(Gravity.BOTTOM);
        score.setPadding(0, dp(10), 0, dp(18));
        TextView value = text(loading || average == null ? "—" : average, 52, Color.WHITE, Typeface.BOLD);
        value.setIncludeFontPadding(false);
        score.addView(value);
        TextView caption = text("средняя оценка", 12, Color.rgb(170, 170, 170), Typeface.NORMAL);
        LinearLayout.LayoutParams captionParams = new LinearLayout.LayoutParams(-2, dp(38));
        captionParams.leftMargin = dp(9);
        score.addView(caption, captionParams);
        card.addView(score);
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.addView(createGradeStatus("Посещено", loading ? "—" : String.valueOf(attended), Color.WHITE), new LinearLayout.LayoutParams(0, dp(62), 1f));
        row.addView(createGradeStatus("Пропуски", loading ? "—" : String.valueOf(absent), Color.rgb(255, 125, 125)), new LinearLayout.LayoutParams(0, dp(62), 1f));
        row.addView(createGradeStatus("Опоздания", loading ? "—" : String.valueOf(late), Color.rgb(245, 166, 35)), new LinearLayout.LayoutParams(0, dp(62), 1f));
        card.addView(row);
        return card;
    }

    private LinearLayout createGradeStatus(String label, String value, int color) {
        LinearLayout item = new LinearLayout(this);
        item.setOrientation(LinearLayout.VERTICAL);
        item.setGravity(Gravity.CENTER_VERTICAL);
        item.addView(text(value, 18, color, Typeface.BOLD));
        item.addView(text(label, 10, Color.rgb(155, 155, 155), Typeface.NORMAL));
        return item;
    }

    private LinearLayout createSubjectSummaryRow(String subject, String average, int markCount) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(2), dp(12), dp(2), dp(12));
        View marker = new View(this);
        marker.setBackgroundColor(markCount == 0 ? COLOR_BORDER : COLOR_CHART_BLUE);
        row.addView(marker, new LinearLayout.LayoutParams(dp(3), dp(38)));
        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        copy.setPadding(dp(12), 0, dp(8), 0);
        TextView title = text(subject, 14, COLOR_BLACK, Typeface.BOLD);
        title.setMaxLines(2);
        copy.addView(title);
        copy.addView(text(markCount + " оценок", 11, COLOR_MUTED, Typeface.NORMAL));
        row.addView(copy, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView value = monoText(average, 18, COLOR_BLACK, Typeface.BOLD);
        value.setGravity(Gravity.CENTER);
        value.setBackground(roundedDrawable(COLOR_SURFACE, 6));
        row.addView(value, new LinearLayout.LayoutParams(dp(52), dp(40)));
        return row;
    }

    private String snapshotKey(String route) {
        if ("tasks".equals(route)) return "tasks_" + taskStatus + "_" + taskPage;
        if ("materials".equals(route)) return "materials_" + materialType + "_" + materialRecommended;
        if ("announcements".equals(route) && expandedNews >= 0) return "news_" + expandedNews;
        return "items";
    }

    private void selectNativeFilter(String route, Runnable change) {
        change.run();
        renderedSignatures.remove(route);
        refreshMessages.put(route, signedIn ? "Обновляем данные…" : "Подключаемся к дневнику…");
        showCachedNativeData(route);
        requestNativeData(route, true);
    }

    private void addNativeFilters(String[] labels, int[] values, int selected, java.util.function.IntConsumer onSelect) {
        android.widget.HorizontalScrollView scroll = new android.widget.HorizontalScrollView(this);
        scroll.setHorizontalScrollBarEnabled(false);
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        for (int i = 0; i < labels.length; i++) {
            final int value = values[i];
            TextView chip = text(labels[i], 12,
                    value == selected ? Color.WHITE : COLOR_MUTED, Typeface.BOLD);
            chip.setGravity(Gravity.CENTER);
            chip.setPadding(dp(16), dp(10), dp(16), dp(10));
            chip.setMinHeight(dp(44));
            chip.setBackground(borderedDrawable(value == selected ? COLOR_BLACK : COLOR_CARD,
                    value == selected ? COLOR_BLACK : COLOR_BORDER, 7));
            chip.setContentDescription(labels[i] + (value == selected ? ", выбрано" : ""));
            chip.setOnClickListener(v -> { if (value != selected) onSelect.accept(value); });
            enablePressAnimation(chip);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
            lp.rightMargin = dp(8);
            row.addView(chip, lp);
            if (value == selected) chip.post(() -> scroll.smoothScrollTo(Math.max(0, chip.getLeft() - dp(18)), 0));
        }
        scroll.addView(row);
        addNativeBlock(scroll, 12);
    }

    private LinearLayout editorialCard(String label, String title, String subtitle) {
        LinearLayout card = createNativeListContainer();
        card.setPadding(dp(18), dp(17), dp(18), dp(17));
        if (!label.isEmpty()) {
            int labelColor = label.contains("ПРОСРОЧ") ? COLOR_DANGER
                    : label.contains("ПРОВЕРЕНО") || label.contains("ПОЛУЧЕНО") ? COLOR_SUCCESS : COLOR_MUTED;
            TextView eyebrow = monoText(label, 10, labelColor, Typeface.BOLD);
            eyebrow.setPadding(dp(9), dp(5), dp(9), dp(5));
            eyebrow.setBackground(borderedDrawable(label.contains("ПРОСРОЧ") ? Color.rgb(255, 239, 240)
                    : label.contains("ПРОВЕРЕНО") || label.contains("ПОЛУЧЕНО") ? Color.rgb(235, 244, 237)
                    : COLOR_SURFACE, COLOR_BORDER, 6));
            LinearLayout.LayoutParams eyebrowParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            eyebrowParams.bottomMargin = dp(12);
            card.addView(eyebrow, eyebrowParams);
        }
        TextView heading = text(plainText(title), 18, COLOR_BLACK, Typeface.BOLD);
        heading.setLineSpacing(dp(2), 1f);
        card.addView(heading);
        if (!subtitle.isEmpty()) {
            TextView sub = text(plainText(subtitle), 13, COLOR_MUTED, Typeface.NORMAL);
            sub.setPadding(0, dp(10), 0, 0);
            sub.setLineSpacing(dp(3), 1f);
            card.addView(sub);
        }
        return card;
    }

    private String plainText(String html) {
        return android.text.Html.fromHtml(html, android.text.Html.FROM_HTML_MODE_COMPACT).toString().trim();
    }

    private TextView documentText(String html) {
        TextView body = text("", 15, COLOR_BLACK, Typeface.NORMAL);
        android.text.SpannableString content = new android.text.SpannableString(
                android.text.Html.fromHtml(html.replace("\n", "<br>"), android.text.Html.FROM_HTML_MODE_COMPACT));
        for (android.text.style.URLSpan url : content.getSpans(0, content.length(), android.text.style.URLSpan.class)) {
            int a = content.getSpanStart(url), b = content.getSpanEnd(url);
            content.removeSpan(url);
            content.setSpan(new android.text.style.ClickableSpan() {
                @Override public void onClick(View widget) { openReadOnlyLink(url.getURL()); }
            }, a, b, 0);
        }
        body.setText(content);
        body.setTextIsSelectable(true);
        body.setMovementMethod(android.text.method.LinkMovementMethod.getInstance());
        body.setLineSpacing(dp(5), 1f);
        body.setPadding(0, dp(16), 0, dp(8));
        body.setLinkTextColor(COLOR_BLACK);
        return body;
    }

    private void cardAction(LinearLayout card, String label, Runnable action) {
        TextView button = monoText(label.toUpperCase(new Locale("ru")), 10, COLOR_BLACK, Typeface.BOLD);
        button.setMinHeight(dp(48));
        button.setGravity(Gravity.CENTER);
        button.setPadding(dp(12), dp(10), dp(12), dp(10));
        button.setBackground(roundedDrawable(COLOR_BLACK, 7));
        button.setTextColor(Color.WHITE);
        LinearLayout.LayoutParams actionParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        actionParams.topMargin = dp(14);
        button.setOnClickListener(v -> action.run());
        enablePressAnimation(button);
        card.addView(button, actionParams);
    }

    private void openReadOnlyLink(String raw) {
        try {
            java.net.URI link = new java.net.URI(BASE_URL).resolve(raw);
            if (!"https".equalsIgnoreCase(link.getScheme()) || link.getHost() == null || link.getUserInfo() != null) {
                Toast.makeText(this, "Ссылка недоступна для открытия", Toast.LENGTH_SHORT).show();
                return;
            }
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(link.toString())));
        } catch (Exception ignored) {
            Toast.makeText(this, "Не удалось открыть ссылку", Toast.LENGTH_SHORT).show();
        }
    }

    private void showNativeDocument(JSONObject item) {
        ScrollView scroll = new ScrollView(this);
        LinearLayout content = editorialCard("", item.optString("title"),
                joinNonEmpty(" · ", item.optString("teacher"), item.optString("deadline")));
        content.addView(documentText(joinNonEmpty("<br><br>", item.optString("theme"), item.optString("body"))));
        TextView aiHeading = monoText("AI-ДЕЙСТВИЯ", 9, COLOR_MUTED, Typeface.BOLD);
        aiHeading.setPadding(0, dp(16), 0, dp(5));
        content.addView(aiHeading);
        cardAction(content, "Кратко: что требуется  ›", () -> requestHomeworkAi(item, "summary", false));
        cardAction(content, "Составить план выполнения  ›", () -> requestHomeworkAi(item, "plan", false));
        cardAction(content, "Объяснить тему  ›", () -> requestHomeworkAi(item, "explain", false));
        cardAction(content, "Проверить требования  ›", () -> requestHomeworkAi(item, "requirements", false));
        JSONArray attachments = taskAttachments(item);
        if (attachments.length() > 0) {
            cardAction(content, "Разобрать все вложения (" + attachments.length() + ")  ›",
                    () -> requestHomeworkAi(item, "files", true));
            cardAction(content, "Просмотреть первое вложение  ›", () -> openTaskFile(attachments.optJSONObject(0)));
        }
        scroll.addView(content);
        new android.app.AlertDialog.Builder(this).setView(scroll).setPositiveButton("Готово", null).show();
    }

    private static final class DownloadedFile {
        final byte[] bytes;
        final String name;
        final String mime;

        DownloadedFile(byte[] bytes, String name, String mime) {
            this.bytes = bytes;
            this.name = name;
            this.mime = mime == null ? "" : mime;
        }
    }

    private static final class PptxSlide {
        final String text;
        final List<byte[]> images = new ArrayList<>();

        PptxSlide(String text) { this.text = text; }
    }

    private void openTaskFile(JSONObject item) {
        String raw = item.optString("url");
        if (raw.isEmpty()) {
            Toast.makeText(this, "У задания нет прикреплённого файла", Toast.LENGTH_SHORT).show();
            return;
        }
        Uri uri;
        try {
            URI link = new URI(BASE_URL).resolve(raw);
            if (!"https".equalsIgnoreCase(link.getScheme()) || link.getHost() == null || link.getUserInfo() != null) {
                throw new IllegalArgumentException("unsafe file URL");
            }
            uri = Uri.parse(link.toString());
        } catch (Exception error) {
            Toast.makeText(this, "Ссылка на файл недоступна", Toast.LENGTH_SHORT).show();
            return;
        }

        String guessedName = item.optString("filename");
        if (guessedName.isEmpty()) guessedName = URLUtil.guessFileName(uri.toString(), null, item.optString("mime"));
        final String fileName = guessedName;
        final AlertDialog loading = new android.app.AlertDialog.Builder(this)
                .setTitle("Загрузка файла")
                .setMessage(fileName + "\n\nПодготавливаем просмотр…")
                .setNegativeButton("Отмена", null)
                .create();
        loading.setOnShowListener(dialog -> {
            TextView message = loading.findViewById(android.R.id.message);
            if (message != null) message.setLineSpacing(dp(4), 1f);
        });
        loading.show();
        final Uri fileUri = uri;
        requestTaskFile(fileUri, fileName, item.optString("mime"), loading);
    }

    private void requestTaskFile(Uri fileUri, String fileName, String expectedMime, AlertDialog loading) {
        if (webView == null) {
            downloadTaskFileInBackground(fileUri, fileName, expectedMime, loading);
            return;
        }
        String id = "file_" + (++requestSequence) + "_" + System.currentTimeMillis();
        String script = "window.__tjStartFileJob&&window.__tjStartFileJob(" + JSONObject.quote(id) + ","
                + JSONObject.quote(fileUri.toString()) + ")";
        webView.evaluateJavascript(script, ignored -> pollTaskFile(id, fileUri, fileName, expectedMime, loading, 0));
    }

    private void pollTaskFile(String id, Uri fileUri, String fileName, String expectedMime,
                              AlertDialog loading, int attempt) {
        if (!loading.isShowing() || webView == null || isDestroyed()) {
            if (webView != null) webView.evaluateJavascript("window.__tjDropFileJob&&window.__tjDropFileJob("
                    + JSONObject.quote(id) + ")", null);
            return;
        }
        if (attempt >= 150) {
            webView.evaluateJavascript("window.__tjDropFileJob&&window.__tjDropFileJob(" + JSONObject.quote(id) + ")", null);
            downloadTaskFileInBackground(fileUri, fileName, expectedMime, loading);
            return;
        }
        webView.evaluateJavascript("window.__tjReadFileJob?window.__tjReadFileJob(" + JSONObject.quote(id) + "):''", raw -> {
            if (!loading.isShowing()) return;
            try {
                JSONObject payload = new JSONObject(decodeJavascriptString(raw));
                if (!payload.optBoolean("done")) {
                    handler.postDelayed(() -> pollTaskFile(id, fileUri, fileName, expectedMime, loading, attempt + 1), 200);
                    return;
                }
                webView.evaluateJavascript("window.__tjDropFileJob&&window.__tjDropFileJob(" + JSONObject.quote(id) + ")", null);
                String encoded = payload.optString("data");
                if (encoded.isEmpty()) throw new java.io.IOException(payload.optString("error", "empty_file_response"));
                byte[] bytes = android.util.Base64.decode(encoded, android.util.Base64.DEFAULT);
                loading.dismiss();
                showFilePreview(new DownloadedFile(bytes, fileName, payload.optString("mime", expectedMime)));
            } catch (Exception error) {
                downloadTaskFileInBackground(fileUri, fileName, expectedMime, loading);
            }
        });
    }

    private void downloadTaskFileInBackground(Uri fileUri, String fileName, String expectedMime, AlertDialog loading) {
        final String userAgent = webView == null ? "TopJournal/0.26.0" : webView.getSettings().getUserAgentString();
        new Thread(() -> {
            try {
                DownloadedFile file = downloadTaskFile(fileUri, fileName, expectedMime, userAgent);
                runOnUiThread(() -> {
                    if (!loading.isShowing()) return;
                    loading.dismiss();
                    showFilePreview(file);
                });
            } catch (Exception error) {
                runOnUiThread(() -> {
                    if (!loading.isShowing()) return;
                    loading.dismiss();
                    new android.app.AlertDialog.Builder(this)
                            .setTitle("Не удалось открыть файл")
                            .setMessage("Проверьте соединение и повторите попытку.\n\n" + error.getMessage())
                            .setPositiveButton("Закрыть", null)
                            .show();
                });
            }
        }, "task-file-reader").start();
    }

    private DownloadedFile downloadTaskFile(Uri uri, String fileName, String expectedMime, String userAgent) throws Exception {
        URLConnection connection = new URL(uri.toString()).openConnection();
        connection.setConnectTimeout(20000);
        connection.setReadTimeout(30000);
        connection.setRequestProperty("User-Agent", userAgent);
        String cookies = CookieManager.getInstance().getCookie(uri.toString());
        if (cookies != null) connection.setRequestProperty("Cookie", cookies);
        if (connection instanceof HttpURLConnection) {
            HttpURLConnection http = (HttpURLConnection) connection;
            http.setInstanceFollowRedirects(true);
            int status = http.getResponseCode();
            if (status < 200 || status >= 300) throw new java.io.IOException("Сервер вернул " + status);
        }
        int length = connection.getContentLength();
        if (length > 30 * 1024 * 1024) throw new java.io.IOException("Файл слишком большой для просмотра");
        try (InputStream input = connection.getInputStream(); ByteArrayOutputStream output = new ByteArrayOutputStream(Math.max(4096, length))) {
            byte[] buffer = new byte[8192];
            int count;
            int total = 0;
            while ((count = input.read(buffer)) != -1) {
                total += count;
                if (total > 30 * 1024 * 1024) throw new java.io.IOException("Файл слишком большой для просмотра");
                output.write(buffer, 0, count);
            }
            String mime = connection.getContentType();
            return new DownloadedFile(output.toByteArray(), fileName,
                    mime == null || mime.isEmpty() ? expectedMime : mime);
        }
    }

    private void showFilePreview(DownloadedFile file) {
        String value = (file.name + " " + file.mime).toLowerCase(Locale.ROOT);
        boolean jpeg = value.contains(".jpg") || value.contains(".jpeg") || value.contains("image/jpeg")
                || isJpegBytes(file.bytes);
        boolean pptxHint = value.contains(".pptx") || value.contains("presentationml.presentation")
                || isZipBytes(file.bytes);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(8), dp(4), dp(8), dp(4));
        TextView caption = text(file.name, 13, COLOR_MUTED, Typeface.NORMAL);
        caption.setPadding(dp(8), dp(4), dp(8), dp(12));
        content.addView(caption);
        if (jpeg) {
            ImageView image = new ImageView(this);
            image.setAdjustViewBounds(true);
            image.setScaleType(ImageView.ScaleType.FIT_CENTER);
            image.setBackground(roundedDrawable(COLOR_SURFACE, 18));
            image.setPadding(dp(6), dp(6), dp(6), dp(6));
            Bitmap bitmap = decodePreviewBitmap(file.bytes);
            if (bitmap != null) image.setImageBitmap(bitmap);
            else image.setContentDescription("Изображение не удалось декодировать");
            content.addView(image, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(360)));
        } else if (pptxHint) {
            List<PptxSlide> slides = readPptxSlides(file.bytes);
            if (slides.isEmpty()) {
                content.addView(createEmptyNativeState("В презентации не удалось найти слайды."));
            } else {
                for (int i = 0; i < slides.size(); i++) addPptxSlide(content, slides.get(i), i + 1);
            }
        } else {
            content.addView(createEmptyNativeState("Этот формат нельзя просмотреть внутри приложения."));
        }
        ScrollView scroll = new ScrollView(this);
        scroll.addView(content);
        new android.app.AlertDialog.Builder(this).setTitle("Просмотр файла").setView(scroll)
                .setPositiveButton("Готово", null).show();
    }

    private boolean isJpegBytes(byte[] bytes) {
        return bytes != null && bytes.length >= 3 && (bytes[0] & 0xff) == 0xff
                && (bytes[1] & 0xff) == 0xd8 && (bytes[2] & 0xff) == 0xff;
    }

    private boolean isZipBytes(byte[] bytes) {
        return bytes != null && bytes.length >= 4 && bytes[0] == 'P' && bytes[1] == 'K'
                && ((bytes[2] == 3 && bytes[3] == 4) || (bytes[2] == 5 && bytes[3] == 6));
    }

    private Bitmap decodePreviewBitmap(byte[] bytes) {
        try {
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            BitmapFactory.decodeByteArray(bytes, 0, bytes.length, bounds);
            int sample = 1;
            int target = Math.max(dp(360), 720);
            while (bounds.outWidth / sample > target || bounds.outHeight / sample > target) sample *= 2;
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inSampleSize = sample;
            options.inPreferredConfig = Bitmap.Config.RGB_565;
            return BitmapFactory.decodeByteArray(bytes, 0, bytes.length, options);
        } catch (OutOfMemoryError error) {
            return null;
        }
    }

    private List<PptxSlide> readPptxSlides(byte[] bytes) {
        Map<String, String> slideXml = new LinkedHashMap<>();
        Map<String, String> slideRels = new LinkedHashMap<>();
        Map<String, byte[]> media = new LinkedHashMap<>();
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(bytes))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (entry.isDirectory()) continue;
                byte[] value = readAllBytes(zip, 30 * 1024 * 1024);
                String name = entry.getName();
                if (name.matches("ppt/slides/slide\\d+\\.xml")) slideXml.put(name, new String(value, StandardCharsets.UTF_8));
                else if (name.matches("ppt/slides/_rels/slide\\d+\\.xml\\.rels")) slideRels.put(name, new String(value, StandardCharsets.UTF_8));
                else if (name.startsWith("ppt/media/")) media.put(name, value);
            }
        } catch (Exception ignored) {
            return Collections.emptyList();
        }
        List<String> names = new ArrayList<>(slideXml.keySet());
        Collections.sort(names, Comparator.comparingInt(this::slideNumber));
        List<PptxSlide> result = new ArrayList<>();
        for (String name : names) {
            PptxSlide slide = new PptxSlide(extractPptxText(slideXml.get(name)));
            String relsName = "ppt/slides/_rels/" + name.substring("ppt/slides/".length()) + ".rels";
            Map<String, String> rels = extractPptxRelationships(slideRels.get(relsName));
            Matcher embeds = Pattern.compile("r:embed=\\\"([^\\\"]+)\\\"").matcher(slideXml.get(name));
            while (embeds.find() && slide.images.size() < 3) {
                String target = rels.get(embeds.group(1));
                byte[] image = media.get(target);
                if (image != null) slide.images.add(image);
            }
            result.add(slide);
        }
        return result;
    }

    private byte[] readAllBytes(InputStream input, int limit) throws java.io.IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int count;
        int total = 0;
        while ((count = input.read(buffer)) != -1) {
            total += count;
            if (total > limit) throw new java.io.IOException("entry too large");
            output.write(buffer, 0, count);
        }
        return output.toByteArray();
    }

    private int slideNumber(String name) {
        Matcher matcher = Pattern.compile("slide(\\d+)\\.xml").matcher(name);
        return matcher.find() ? Integer.parseInt(matcher.group(1)) : Integer.MAX_VALUE;
    }

    private String extractPptxText(String xml) {
        if (xml == null) return "";
        Matcher matcher = Pattern.compile("<a:t[^>]*>(.*?)</a:t>", Pattern.DOTALL).matcher(xml);
        StringBuilder text = new StringBuilder();
        while (matcher.find()) {
            String part = android.text.Html.fromHtml(matcher.group(1), android.text.Html.FROM_HTML_MODE_COMPACT).toString().trim();
            if (!part.isEmpty()) {
                if (text.length() > 0) text.append(' ');
                text.append(part);
            }
        }
        return text.toString();
    }

    private Map<String, String> extractPptxRelationships(String xml) {
        Map<String, String> result = new LinkedHashMap<>();
        if (xml == null) return result;
        Matcher matcher = Pattern.compile("<Relationship[^>]*Id=\\\"([^\\\"]+)\\\"[^>]*Target=\\\"([^\\\"]+)\\\"").matcher(xml);
        while (matcher.find()) {
            String target = matcher.group(2).replace('\\', '/');
            if (target.startsWith("../")) target = "ppt/" + target.substring(3);
            else if (!target.startsWith("ppt/")) target = "ppt/slides/" + target;
            result.put(matcher.group(1), target);
        }
        return result;
    }

    private void addPptxSlide(LinearLayout parent, PptxSlide slide, int number) {
        LinearLayout card = createNativeListContainer();
        card.setPadding(dp(14), dp(14), dp(14), dp(14));
        TextView title = text("Слайд " + number, 14, COLOR_MUTED, Typeface.BOLD);
        card.addView(title);
        TextView body = text(slide.text.isEmpty() ? "Без текста" : slide.text, 15, COLOR_BLACK, Typeface.NORMAL);
        body.setLineSpacing(dp(4), 1f);
        body.setPadding(0, dp(8), 0, slide.images.isEmpty() ? 0 : dp(12));
        card.addView(body);
        for (byte[] imageBytes : slide.images) {
            Bitmap image = decodePreviewBitmap(imageBytes);
            if (image == null) continue;
            ImageView imageView = new ImageView(this);
            imageView.setAdjustViewBounds(true);
            imageView.setScaleType(ImageView.ScaleType.FIT_CENTER);
            imageView.setImageBitmap(image);
            imageView.setBackground(roundedDrawable(COLOR_SURFACE, 14));
            imageView.setPadding(dp(4), dp(4), dp(4), dp(4));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(220));
            params.topMargin = dp(8);
            card.addView(imageView, params);
        }
        parent.addView(card, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        View gap = new View(this);
        parent.addView(gap, new LinearLayout.LayoutParams(1, dp(10)));
    }

    private void addListLoading(String title) {
        for (int i = 0; i < 3; i++) {
            LinearLayout card = editorialCard(i == 0 ? title : "", "", "");
            card.addView(missingData(20));
            View gap = new View(this); card.addView(gap, new LinearLayout.LayoutParams(1, dp(12)));
            card.addView(missingData(58));
            addNativeBlock(card, 12);
        }
    }

    private void renderNativeTasks(JSONObject data) {
        addNativeScreenHeading("TASK INBOX", "Задания", "Сроки, статус проверки и вложенные материалы");
        String[] labels = {"Текущие", "На проверке", "Проверено", "Просрочено"};
        addNativeFilters(labels, new int[]{3, 2, 1, 0}, taskStatus,
                value -> selectNativeFilter("tasks", () -> { taskStatus = value; taskPage = 1; }));
        JSONArray items = data.optJSONArray(snapshotKey("tasks"));
        String status = taskStatus == 3 ? "В работе" : taskStatus == 2 ? "На проверке"
                : taskStatus == 1 ? "Проверено" : "Просрочено";
        LinearLayout inbox = createNativeListContainer();
        inbox.setPadding(dp(12), dp(10), dp(12), dp(10));
        LinearLayout summary = new LinearLayout(this);
        summary.setGravity(Gravity.CENTER_VERTICAL);
        summary.setPadding(dp(4), dp(4), dp(4), dp(10));
        summary.addView(text(status, 14, COLOR_BLACK, Typeface.BOLD),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView count = monoText(items == null ? "—" : String.valueOf(items.length()), 12, COLOR_MUTED, Typeface.BOLD);
        count.setGravity(Gravity.CENTER);
        count.setBackground(roundedDrawable(COLOR_SURFACE, 6));
        summary.addView(count, new LinearLayout.LayoutParams(dp(38), dp(30)));
        inbox.addView(summary);
        if (items == null) inbox.addView(missingData(210));
        else if (items.length() == 0) inbox.addView(createEmptyNativeState("В этой категории пока ничего нет"));
        else for (int i = 0; i < items.length(); i++) {
            JSONObject item = items.optJSONObject(i);
            if (item != null) inbox.addView(createTaskInboxRow(item, taskStatus));
        }
        addNativeBlock(inbox, 14);

        LinearLayout pages = new LinearLayout(this);
        pages.setGravity(Gravity.CENTER_VERTICAL);
        if (taskPage > 1) pages.addView(createPagerButton("← Назад", () -> selectNativeFilter("tasks", () -> taskPage--)), weightedParams(0, 5));
        else pages.addView(new View(this), weightedParams(0, 5));
        TextView page = monoText("PAGE " + taskPage, 10, COLOR_MUTED, Typeface.NORMAL);
        page.setGravity(Gravity.CENTER);
        pages.addView(page, new LinearLayout.LayoutParams(dp(72), dp(42)));
        if (items != null && items.length() > 0 && taskPage < 30)
            pages.addView(createPagerButton("Далее →", () -> selectNativeFilter("tasks", () -> taskPage++)), weightedParams(5, 0));
        else pages.addView(new View(this), weightedParams(5, 0));
        addNativeBlock(pages, 18);
        animateNativeHomeContent();
    }

    private View createTaskInboxRow(JSONObject item, int status) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.TOP);
        row.setPadding(dp(4), dp(14), dp(4), dp(14));
        TextView state = text(status == 1 ? "✓" : status == 0 ? "!" : "○", 18,
                status == 1 ? COLOR_SUCCESS : status == 0 ? COLOR_DANGER : COLOR_BLACK, Typeface.BOLD);
        state.setGravity(Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        row.addView(state, new LinearLayout.LayoutParams(dp(34), dp(42)));

        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        TextView title = text(item.optString("title", "Задание"), 14, COLOR_BLACK, Typeface.BOLD);
        title.setMaxLines(2);
        copy.addView(title);
        String theme = plainText(item.optString("theme"));
        if (!theme.isEmpty()) {
            TextView themeView = text(theme, 12, COLOR_MUTED, Typeface.NORMAL);
            themeView.setMaxLines(2);
            themeView.setEllipsize(TextUtils.TruncateAt.END);
            themeView.setPadding(0, dp(4), 0, 0);
            copy.addView(themeView);
        }
        LinearLayout meta = new LinearLayout(this);
        meta.setGravity(Gravity.CENTER_VERTICAL);
        meta.setPadding(0, dp(9), 0, 0);
        String deadline = item.optString("deadline");
        TextView due = monoText(deadline.isEmpty() ? "БЕЗ СРОКА" : deadline, 9,
                status == 0 ? COLOR_DANGER : COLOR_MUTED, Typeface.BOLD);
        due.setPadding(dp(8), dp(5), dp(8), dp(5));
        due.setBackground(roundedDrawable(status == 0 ? Color.rgb(255, 239, 240) : COLOR_SURFACE, 5));
        meta.addView(due);
        int attachmentCount = taskAttachments(item).length();
        if (attachmentCount > 0) {
            TextView attachment = monoText("  " + attachmentCount + " FILE", 9, COLOR_CHART_BLUE, Typeface.BOLD);
            meta.addView(attachment);
        }
        copy.addView(meta);
        row.addView(copy, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView arrow = text("›", 24, COLOR_MUTED, Typeface.NORMAL);
        arrow.setGravity(Gravity.CENTER);
        row.addView(arrow, new LinearLayout.LayoutParams(dp(28), dp(44)));
        row.setOnClickListener(v -> showNativeDocument(item));
        enablePressAnimation(row);
        return row;
    }

    private TextView createPagerButton(String label, Runnable action) {
        TextView button = text(label, 12, COLOR_BLACK, Typeface.BOLD);
        button.setGravity(Gravity.CENTER);
        button.setMinHeight(dp(42));
        button.setBackground(borderedDrawable(COLOR_CARD, COLOR_BORDER, 7));
        button.setOnClickListener(v -> action.run());
        enablePressAnimation(button);
        return button;
    }

    private void renderNativeExtra(String route, JSONObject data) {
        if ("materials".equals(route)) {
            addNativeScreenHeading("LIBRARY", "Материалы", "Уроки, книги, практика и презентации");
            addNativeFilters(new String[]{"Уроки", "Книги", "Видео", "Статьи", "Практика", "Тесты", "Презентации"},
                    new int[]{2, 4, 5, 8, 3, 7, 6}, materialType,
                    value -> selectNativeFilter(route, () -> materialType = value));
            addNativeFilters(new String[]{"Методические", "От преподавателей"}, new int[]{0, 1}, materialRecommended,
                    value -> selectNativeFilter(route, () -> materialRecommended = value));
            JSONArray items = data.optJSONArray(snapshotKey(route));
            if (items == null) { addListLoading("Материалы"); return; }
            LinearLayout library = createNativeListContainer();
            if (items.length() == 0) library.addView(createEmptyNativeState("В выбранной категории пока нет материалов"));
            for (int i = 0; i < items.length(); i++) {
                JSONObject item = items.optJSONObject(i); if (item == null) continue;
                library.addView(createLibraryRow(item));
            }
            addNativeBlock(library, 18);
        } else if ("announcements".equals(route)) {
            addNativeScreenHeading("INBOX", "Объявления", "Сообщения и новости учебного центра");
            JSONArray items = data.optJSONArray("items");
            if (items == null) { addListLoading("Объявления"); return; }
            LinearLayout feed = createNativeListContainer();
            if (items.length() == 0) feed.addView(createEmptyNativeState("Новых объявлений нет"));
            for (int i = 0; i < items.length(); i++) {
                JSONObject item = items.optJSONObject(i); if (item == null) continue;
                long id = item.optLong("id", -1);
                feed.addView(createAnnouncementRow(item, id, data, route));
            }
            addNativeBlock(feed, 18);
        } else if ("rewards".equals(route)) {
            addNativeScreenHeading("PROGRESS", "Награды", "Полученные достижения и следующие цели");
            addNativeFilters(new String[]{"Все", "Получены", "Впереди"}, new int[]{0, 1, 2}, rewardFilter,
                    value -> { rewardFilter = value; showCachedNativeData(route); });
            JSONArray items = data.optJSONArray("items");
            if (items == null) { addListLoading("Награды"); return; }
            int count = 0;
            LinearLayout grid = new LinearLayout(this);
            grid.setOrientation(LinearLayout.VERTICAL);
            LinearLayout row = null;
            for (int i = 0; i < items.length(); i++) {
                JSONObject item = items.optJSONObject(i); if (item == null) continue;
                boolean earned = item.optBoolean("earned");
                if ((rewardFilter == 1 && !earned) || (rewardFilter == 2 && earned)) continue;
                if (count % 2 == 0) {
                    row = new LinearLayout(this);
                    row.setOrientation(LinearLayout.HORIZONTAL);
                    LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(-1, -2);
                    if (count > 0) rowParams.topMargin = dp(10);
                    grid.addView(row, rowParams);
                }
                count++;
                row.addView(createRewardTile(item, earned), weightedParams(count % 2 == 1 ? 0 : 5, count % 2 == 1 ? 5 : 0));
            }
            if (count == 0) addNativeBlock(createEmptyNativeState("Награды появятся по мере выполнения условий"), 18);
            else {
                if (count % 2 == 1 && row != null) row.addView(new View(this), weightedParams(5, 0));
                addNativeBlock(grid, 18);
            }
        } else {
            addNativeScreenHeading("FEEDBACK", "Отзывы", "Комментарии преподавателей о вашем прогрессе");
            JSONArray items = data.optJSONArray("items");
            if (items == null) { addListLoading("Отзывы"); return; }
            LinearLayout timeline = createNativeListContainer();
            if (items.length() == 0) timeline.addView(createEmptyNativeState("Преподаватели пока не оставили комментариев"));
            for (int i = items.length() - 1; i >= 0; i--) {
                JSONObject item = items.optJSONObject(i); if (item == null) continue;
                timeline.addView(createReviewRow(item));
            }
            addNativeBlock(timeline, 18);
        }
        animateNativeHomeContent();
    }

    private View createLibraryRow(JSONObject item) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(4), dp(13), dp(4), dp(13));
        TextView type = monoText(item.optString("subject", "DOC"), 9, COLOR_MUTED, Typeface.BOLD);
        type.setGravity(Gravity.CENTER);
        type.setMaxLines(2);
        type.setBackground(roundedDrawable(COLOR_SURFACE, 6));
        row.addView(type, new LinearLayout.LayoutParams(dp(54), dp(42)));
        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        copy.setPadding(dp(12), 0, dp(8), 0);
        TextView title = text(item.optString("title", "Материал"), 14, COLOR_BLACK, Typeface.BOLD);
        title.setMaxLines(2);
        copy.addView(title);
        copy.addView(text(item.optString("date"), 11, COLOR_MUTED, Typeface.NORMAL));
        row.addView(copy, new LinearLayout.LayoutParams(0, -2, 1f));
        row.addView(text("↗", 18, COLOR_BLACK, Typeface.BOLD), new LinearLayout.LayoutParams(dp(26), dp(40)));
        row.setOnClickListener(v -> {
            if (!item.optString("url").isEmpty()) openReadOnlyLink(item.optString("url"));
            else openMaterialInJournal(item);
        });
        enablePressAnimation(row);
        return row;
    }

    private View createAnnouncementRow(JSONObject item, long id, JSONObject data, String route) {
        LinearLayout block = new LinearLayout(this);
        block.setOrientation(LinearLayout.VERTICAL);
        block.setPadding(dp(4), dp(13), dp(4), dp(13));
        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        copy.addView(text(item.optString("title", "Объявление"), 14, COLOR_BLACK, Typeface.BOLD));
        copy.addView(text(item.optString("date"), 11, COLOR_MUTED, Typeface.NORMAL));
        header.addView(copy, new LinearLayout.LayoutParams(0, -2, 1f));
        TextView arrow = text(id == expandedNews ? "↑" : "↓", 16, COLOR_MUTED, Typeface.BOLD);
        arrow.setGravity(Gravity.CENTER);
        header.addView(arrow, new LinearLayout.LayoutParams(dp(36), dp(36)));
        block.addView(header);
        if (id == expandedNews) {
            JSONObject detail = data.optJSONObject("news_" + id);
            if (detail == null) block.addView(missingData(100));
            else block.addView(documentText(detail.optString("body")));
        }
        if (id >= 0) block.setOnClickListener(v -> {
            expandedNews = expandedNews == id ? -1 : id;
            refreshMessages.remove(route);
            renderedSignatures.remove(route);
            showCachedNativeData(route);
            if (expandedNews >= 0) requestNativeData(route, true);
        });
        enablePressAnimation(block);
        return block;
    }

    private View createRewardTile(JSONObject item, boolean earned) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(15), dp(14), dp(15), dp(14));
        card.setMinimumHeight(dp(138));
        card.setBackground(borderedDrawable(earned ? COLOR_BLACK : COLOR_CARD,
                earned ? COLOR_BLACK : COLOR_BORDER, 8));
        card.addView(monoText(earned ? "UNLOCKED" : "LOCKED", 9,
                earned ? Color.rgb(160, 160, 160) : COLOR_MUTED, Typeface.BOLD));
        TextView title = text(item.optString("title", "Награда"), 14,
                earned ? Color.WHITE : COLOR_BLACK, Typeface.BOLD);
        title.setMaxLines(3);
        title.setPadding(0, dp(10), 0, dp(8));
        card.addView(title, new LinearLayout.LayoutParams(-1, 0, 1f));
        JSONArray points = item.optJSONArray("points");
        StringBuilder amount = new StringBuilder();
        if (points != null) for (int j = 0; j < points.length(); j++) {
            JSONObject point = points.optJSONObject(j); if (point == null) continue;
            if (amount.length() > 0) amount.append(" · ");
            amount.append('+').append(point.optString("amount"));
        }
        card.addView(monoText(amount.length() == 0 ? "—" : amount.toString(), 11,
                earned ? Color.WHITE : COLOR_MUTED, Typeface.BOLD));
        return card;
    }

    private View createReviewRow(JSONObject item) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.TOP);
        row.setPadding(dp(4), dp(14), dp(4), dp(14));
        View marker = new View(this);
        marker.setBackgroundColor(COLOR_CHART_BLUE);
        row.addView(marker, new LinearLayout.LayoutParams(dp(3), dp(54)));
        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        copy.setPadding(dp(12), 0, 0, 0);
        copy.addView(text(item.optString("title"), 14, COLOR_BLACK, Typeface.BOLD));
        copy.addView(text(joinNonEmpty(" · ", item.optString("teacher"), item.optString("date")),
                11, COLOR_MUTED, Typeface.NORMAL));
        TextView body = documentText(item.optString("body"));
        body.setPadding(0, dp(8), 0, 0);
        copy.addView(body);
        row.addView(copy, new LinearLayout.LayoutParams(0, -2, 1f));
        return row;
    }

    private void openMaterialInJournal(JSONObject item) {
        if (!signedIn || webView == null) return;
        requestedRoute = "material_detail";
        updateNavigation("more");
        setPageTitleAnimated("Учебный материал");
        updateNativeHomeVisibility();
        int type = item.optInt("type", 2);
        String path = type == 7 && item.optLong("id", -1) >= 0
                ? "quiz/" + item.optLong("id") : "index/" + type;
        webView.loadUrl(BASE_URL + "ru/main/library/page/" + path);
    }

    private View createCompactMetricRow(String label, String value) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(4), dp(13), dp(4), dp(13));
        TextView labelView = text(label, 14, COLOR_MUTED, Typeface.NORMAL);
        row.addView(labelView, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView valueView = text(value, 17, COLOR_BLACK, Typeface.BOLD);
        valueView.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        row.addView(valueView);
        return row;
    }

    private String joinTime(String start, String end) {
        if (start == null) start = "";
        if (end == null) end = "";
        if (!start.isEmpty() && !end.isEmpty()) return start + "–" + end;
        return !start.isEmpty() ? start : end;
    }

    private String todayLabel() {
        java.text.SimpleDateFormat format = new java.text.SimpleDateFormat("EEEE · d MMMM", new Locale("ru"));
        return format.format(new java.util.Date()).toUpperCase(new Locale("ru"));
    }

    private String joinNonEmpty(String separator, String... values) {
        List<String> parts = new ArrayList<>();
        for (String value : values) if (value != null && !value.trim().isEmpty()) parts.add(value.trim());
        return TextUtils.join(separator, parts);
    }

    private String calculateAverageLabel(JSONArray series) {
        if (series == null || series.length() == 0) return "—";
        double total = 0; int count = 0;
        for (int i = 0; i < series.length(); i++) {
            double value = series.optDouble(i, Double.NaN);
            if (!Double.isNaN(value)) { total += value; count++; }
        }
        if (count == 0) return "—";
        double average = total / count;
        return Math.abs(average - Math.round(average)) < .01
                ? String.valueOf(Math.round(average))
                : String.format(Locale.getDefault(), "%.1f", average);
    }

    private String marksLabel(JSONArray marks) {
        if (marks == null || marks.length() == 0) return "";
        List<String> labels = new ArrayList<>();
        for (int i = 0; i < marks.length(); i++) {
            double value = marks.optDouble(i, Double.NaN);
            if (Double.isNaN(value)) continue;
            labels.add(Math.abs(value - Math.round(value)) < .01
                    ? String.valueOf(Math.round(value))
                    : String.format(Locale.getDefault(), "%.1f", value));
        }
        return TextUtils.join(" · ", labels);
    }

    private void updateNativeHomeVisibility() {
        if (webView == null || nativeHomeView == null) return;
        boolean show = shellVisible && (isNativeRoute(requestedRoute) || hybridLoading) && requestedRoute.equals(nativeScreenRoute);
        if (show) {
            webView.setVisibility(View.VISIBLE);
            webView.setAlpha(0f);
            webView.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
            if (nativeHomeView.getVisibility() != View.VISIBLE) {
                nativeHomeView.setVisibility(View.VISIBLE);
                nativeHomeView.setAlpha(0f);
                nativeHomeView.setTranslationY(dp(8));
                nativeHomeView.animate().alpha(1f).translationY(0).setDuration(220).start();
            }
        } else {
            nativeHomeView.animate().cancel();
            nativeHomeView.setVisibility(View.GONE);
            webView.setVisibility(View.VISIBLE);
            webView.setAlpha(1f);
            webView.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_AUTO);
        }
    }

    private void showMoreDialog() {
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundResource(R.drawable.bg_dialog);

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(18), dp(10), dp(18), dp(30));
        scroll.addView(content, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        View indicator = new View(this);
        indicator.setBackgroundResource(R.drawable.bg_nav_active);
        LinearLayout.LayoutParams indicatorParams = new LinearLayout.LayoutParams(dp(42), dp(4));
        indicatorParams.gravity = Gravity.CENTER_HORIZONTAL;
        indicatorParams.bottomMargin = dp(16);
        content.addView(indicator, indicatorParams);

        TextView heading = text("Ещё", 28, COLOR_BLACK, Typeface.BOLD);
        LinearLayout.LayoutParams headingParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(54));
        content.addView(heading, headingParams);

        addMenuGroup(content, "Учёба", new String[][]{
                {"AI-аналитик", "assistant"},
                {"Материалы", "materials"},
                {"Объявления", "announcements"},
                {"Награды", "rewards"},
                {"Отзывы", "reviews"}
        }, dialog);
        addMenuGroup(content, "Финансы", new String[][]{
                {"Оплата", "payment"},
                {"Магазин", "shop"}
        }, dialog);
        addMenuGroup(content, "Профиль", new String[][]{
                {"Личный кабинет", "profile"}
        }, dialog);
        addMenuGroup(content, "Поддержка", new String[][]{
                {"Частые вопросы", "faq"},
                {"Контакты", "contacts"},
                {"Обращения", "tickets"},
                {"Жалобы", "complaints"}
        }, dialog);

        TextView settingsHeading = text("Настройки приложения", 15, COLOR_MUTED, Typeface.BOLD);
        LinearLayout.LayoutParams settingsHeadingParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(42));
        settingsHeadingParams.topMargin = dp(10);
        content.addView(settingsHeading, settingsHeadingParams);

        addSettingsSwitch(content, "Оригинальный вид веб-страниц",
                "Оформление дополнительных разделов внутри сайта",
                preferences.getBoolean("original_mode", false),
                (button, checked) -> {
                    preferences.edit().putBoolean("original_mode", checked).apply();
                    if (checked) removeMobileStyle(); else applyMobileStyle();
                    updateNativeHomeVisibility();
                });

        addSettingsSwitch(content, "Вечернее напоминание",
                "В 19:00 — только если осталось невыполненное ДЗ",
                preferences.getBoolean("daily_reminder", true),
                (button, checked) -> {
                    preferences.edit().putBoolean("daily_reminder", checked).apply();
                    if (checked) ReminderScheduler.schedule(MainActivity.this);
                    else ReminderScheduler.cancel(MainActivity.this);
                });

        addSettingsSwitch(content, "Новые оценки",
                "Уведомлять после появления новой оценки",
                preferences.getBoolean("new_grade_notifications", true),
                (button, checked) -> preferences.edit().putBoolean("new_grade_notifications", checked).apply());

        TextView diagnosticsRow = createMenuRow("Скопировать техническую карту страницы");
        diagnosticsRow.setOnClickListener(v -> {
            dialog.dismiss();
            copySanitizedPageMap();
        });
        LinearLayout.LayoutParams diagnosticsParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(62));
        diagnosticsParams.topMargin = dp(8);
        content.addView(diagnosticsRow, diagnosticsParams);

        TextView browserRow = createMenuRow("Открыть текущую страницу в браузере");
        browserRow.setOnClickListener(v -> {
            dialog.dismiss();
            try {
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(webView.getUrl())));
            } catch (Exception error) {
                Toast.makeText(this, "Не удалось открыть браузер", Toast.LENGTH_SHORT).show();
            }
        });
        LinearLayout.LayoutParams browserParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(62));
        browserParams.topMargin = dp(8);
        content.addView(browserRow, browserParams);

        dialog.setContentView(scroll);
        dialog.setOnDismissListener(value -> {
            updateNavigation(navigationTab(requestedRoute));
            setPageTitleAnimated(labelForRoute(requestedRoute));
        });
        dialog.show();
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.setDimAmount(0.28f);
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            window.setGravity(Gravity.BOTTOM);
            WindowManager.LayoutParams attributes = window.getAttributes();
            attributes.width = ViewGroup.LayoutParams.MATCH_PARENT;
            attributes.height = Math.min(dp(720), getResources().getDisplayMetrics().heightPixels - dp(56));
            window.setAttributes(attributes);
        }
    }

    private void addMenuGroup(LinearLayout content, String title, String[][] rows, Dialog dialog) {
        TextView groupTitle = text(title, 15, COLOR_MUTED, Typeface.BOLD);
        LinearLayout.LayoutParams groupTitleParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(42));
        groupTitleParams.topMargin = dp(5);
        content.addView(groupTitle, groupTitleParams);
        for (String[] row : rows) {
            TextView menuRow = createMenuRow(row[0]);
            menuRow.setOnClickListener(v -> {
                dialog.dismiss();
                navigateByKey(row[1]);
            });
            LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(58));
            rowParams.bottomMargin = dp(8);
            content.addView(menuRow, rowParams);
        }
    }

    private TextView createMenuRow(String title) {
        TextView row = text(title + "   ›", 16, COLOR_BLACK, Typeface.BOLD);
        row.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
        row.setPadding(dp(18), 0, dp(18), 0);
        row.setBackgroundResource(R.drawable.bg_menu_row);
        return row;
    }

    private void addSettingsSwitch(LinearLayout content, String title, String subtitle,
                                   boolean checked, CompoundButton.OnCheckedChangeListener listener) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(16), dp(9), dp(8), dp(9));
        row.setBackgroundResource(R.drawable.bg_menu_row);

        LinearLayout labels = new LinearLayout(this);
        labels.setOrientation(LinearLayout.VERTICAL);
        TextView titleView = text(title, 15, COLOR_BLACK, Typeface.BOLD);
        TextView subtitleView = text(subtitle, 12, COLOR_MUTED, Typeface.NORMAL);
        subtitleView.setPadding(0, dp(2), dp(6), 0);
        labels.addView(titleView);
        labels.addView(subtitleView);
        row.addView(labels, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        Switch toggle = new Switch(this);
        toggle.setChecked(checked);
        toggle.setShowText(false);
        toggle.setThumbTintList(new ColorStateList(
                new int[][]{new int[]{android.R.attr.state_checked}, new int[]{}},
                new int[]{COLOR_BLACK, Color.WHITE}
        ));
        toggle.setTrackTintList(new ColorStateList(
                new int[][]{new int[]{android.R.attr.state_checked}, new int[]{}},
                new int[]{Color.rgb(130, 130, 134), Color.rgb(210, 210, 214)}
        ));
        toggle.setOnCheckedChangeListener(listener);
        row.addView(toggle, new LinearLayout.LayoutParams(dp(56), dp(48)));
        row.setOnClickListener(v -> toggle.setChecked(!toggle.isChecked()));

        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(76));
        rowParams.bottomMargin = dp(8);
        content.addView(row, rowParams);
    }

    private void navigateByKey(String key) {
        if (!signedIn && !isNativeRoute(key)) {
            Toast.makeText(this, "Этот раздел доступен после подключения к дневнику", Toast.LENGTH_SHORT).show();
            return;
        }
        if (navigationInFlight && !isNativeRoute(key)) {
            if (!key.equals(navigationRoute)) {
                Toast.makeText(this, "Предыдущий раздел ещё открывается", Toast.LENGTH_SHORT).show();
            }
            return;
        }
        requestedRoute = key;
        navigationRoute = key;
        navigationInFlight = true;
        navigationAttempt = 0;
        int generation = ++navigationGeneration;
        if (isNativeRoute(key)) {
            hybridLoading = false;
            activeSection = navigationTab(key);
            boolean alreadyVisible = key.equals(nativeScreenRoute) && nativeHomeView.getVisibility() == View.VISIBLE;
            if (!alreadyVisible) showCachedNativeData(key);
            updateNavigation(navigationTab(key));
            setPageTitleAnimated(labelForRoute(key));
            confirmedRoute = key;
            navigationInFlight = false;
            navigationRoute = "";
            if (!"assistant".equals(key)) requestNativeData(key, true);
            return;
        } else {
            updateNavigation("more");
            setPageTitleAnimated(labelForRoute(key));
            showHybridPlaceholder(false);
        }
        attemptInternalNavigation(key, generation);
    }

    private void requestNativeData(String route, boolean userInitiated) {
        requestNativeData(route, userInitiated, 0);
    }

    private void requestNativeData(String route, boolean userInitiated, int attempt) {
        if ("assistant".equals(route) || webView == null || !signedIn || !isNativeRoute(route) || nativeRequests.containsKey(route)) return;
        final String requestedVariant = snapshotKey(route);
        int id = ++requestSequence;
        int generation = sessionGeneration;
        nativeRequests.put(route, id);
        requestVariants.put(route, requestedVariant);
        refreshMessages.put(route, "Обновляем данные…");
        if (route.equals(requestedRoute)) showCachedNativeData(route);
        installApiCapture();
        String paths;
        switch (route) {
            case "materials":
                paths = "['/library/operations/list?material_type=" + materialType + "&recommended_type=" + materialRecommended + "']";
                break;
            case "announcements":
                paths = expandedNews >= 0 ? "['/news/operations/detail-news?news_id=" + expandedNews + "']" : "['/news/operations/latest-news']";
                break;
            case "rewards":
                paths = "['/profile/statistic/student-achievements','/public/translations?language=ru']";
                break;
            case "reviews":
                paths = "['/reviews/index/list']";
                break;
            case "schedule":
                paths = "[('/schedule/operations/get-by-date-range?date_start='+date(0)+'&date_end='+date(35))]";
                break;
            case "grades":
                paths = "['/progress/operations/student-visits','/progress/operations/student-exams'," +
                        "'/dashboard/chart/progress','/dashboard/chart/average-progress'," +
                        "'/dashboard/chart/attendance']";
                break;
            case "tasks":
                paths = "['/homework/operations/list?status=" + taskStatus + "&type=0&page=" + taskPage + "','/count/homework?type=0']";
                break;
            default:
                paths = "['/dashboard/progress/leader-group-points','/dashboard/progress/leader-stream-points'," +
                        "'/dashboard/progress/leader-group','/dashboard/progress/leader-stream'," +
                        "'/dashboard/progress/academic-performance','/dashboard/progress/attendance-statistic'," +
                        "'/dashboard/chart/average-progress','/dashboard/chart/attendance'," +
                        "'/dashboard/info/future-exams','/dashboard/progress/activity?limit=8','/count/homework','/settings/user-info'," +
                        "'/feedback/students/evaluate-lesson-list']";
                break;
        }
        String script = "(function(){const date=offset=>{const d=new Date();d.setDate(d.getDate()+offset);" +
                "const p=n=>String(n).padStart(2,'0');return d.getFullYear()+'-'+p(d.getMonth()+1)+'-'+p(d.getDate());};" +
                "return window.__tjStartJob?window.__tjStartJob('" + id + "'," + paths + "):false;})()";
        webView.evaluateJavascript(script, ignored -> {
            if (validRequest(route, id, generation)) pollNativeRequest(route, id, generation, attempt, userInitiated,
                    android.os.SystemClock.elapsedRealtime() + 45000);
        });
        handler.postDelayed(() -> {
            if (validRequest(route, id, generation)) finishNativeRequest(route, id, generation, false, "Данные не пришли вовремя · Повторить");
        }, 47000);
    }

    private boolean validRequest(String route, int id, int generation) {
        return !isDestroyed() && webView != null && generation == sessionGeneration
                && Integer.valueOf(id).equals(nativeRequests.get(route));
    }

    private void pollNativeRequest(String route, int id, int generation, int attempt, boolean userInitiated, long deadline) {
        if (!validRequest(route, id, generation)) return;
        if (android.os.SystemClock.elapsedRealtime() >= deadline) {
            finishNativeRequest(route, id, generation, false, "Данные не пришли вовремя · Повторить");
            return;
        }
        webView.evaluateJavascript("window.__tjReadJob?window.__tjReadJob('" + id + "'):JSON.stringify({done:true,reason:'bridge_pending'})", raw -> {
            if (!validRequest(route, id, generation)) return;
            try {
                JSONObject job = new JSONObject(decodeJavascriptString(raw));
                if (job.optInt("loaded") > 0) syncSnapshot(route);
                if (!job.optBoolean("done")) {
                    handler.postDelayed(() -> pollNativeRequest(route, id, generation, attempt, userInitiated, deadline), 350);
                    return;
                }
                String reason = job.optString("reason");
                if (("authorization_pending".equals(reason) || "bridge_pending".equals(reason)) && attempt < 4) {
                    webView.evaluateJavascript("window.__tjDropJob&&window.__tjDropJob('" + id + "')", null);
                    // Retain the request lock while waiting, so repeated taps cannot multiply retries.
                    handler.postDelayed(() -> {
                        if (!validRequest(route, id, generation)) return;
                        nativeRequests.remove(route);
                        requestNativeData(route, userInitiated, attempt + 1);
                    }, 700L + attempt * 700L);
                    return;
                }
                JSONArray errors = job.optJSONArray("errors");
                boolean transientFailure = "network_or_cors".equals(reason) || "timeout".equals(reason)
                        || (errors != null && errors.toString().matches(".*5[0-9][0-9].*"));
                if (transientFailure && attempt == 0 && (errors == null || (!errors.toString().contains("401") && !errors.toString().contains("403")))) {
                    webView.evaluateJavascript("window.__tjDropJob&&window.__tjDropJob('" + id + "')", null);
                    handler.postDelayed(() -> {
                        if (!validRequest(route, id, generation)) return;
                        nativeRequests.remove(route);
                        requestNativeData(route, userInitiated, 1);
                    }, 1800);
                    return;
                }
                boolean complete = job.optInt("loaded") == job.optInt("total", -1) && (errors == null || errors.length() == 0);
                String message = "Не удалось обновить данные · Повторить";
                if ("authorization_pending".equals(reason)) message = "Сессия сайта ещё не готова · Повторить";
                else if ("bridge_pending".equals(reason)) message = "Модуль загрузки ещё не готов · Повторить";
                else if ("network_or_cors".equals(reason)) message = "Сетевой запрос заблокирован или соединение недоступно · Повторить";
                else if ("timeout".equals(reason)) message = "Сервер не ответил вовремя · Повторить";
                else if ("invalid_json".equals(reason) || "invalid_data".equals(reason)) message = "Сервер вернул неожиданный ответ · Повторить";
                else if ("http_error".equals(reason)) message = "Ошибка сервера " + errors + " · Повторить";
                if (errors != null && errors.toString().contains("401")) message = "Сессия истекла (401). Войдите в дневник";
                else if (errors != null && errors.toString().contains("403")) message = "Сервер запретил доступ (403) · Повторить";
                if ("session_unverified".equals(reason)) message = "Вход в дневник не подтверждён";
                finishNativeRequest(route, id, generation, complete, message);
            } catch (JSONException ignored) {
                finishNativeRequest(route, id, generation, false, "Подключение ещё не готово · Повторить");
            }
        });
    }

    private void finishNativeRequest(String route, int id, int generation, boolean complete, String failure) {
        if (!validRequest(route, id, generation)) return;
        nativeRequests.remove(route);
        String variant = requestVariants.remove(route);
        if (variant != null && !variant.equals(snapshotKey(route))) {
            webView.evaluateJavascript("window.__tjDropJob&&window.__tjDropJob('" + id + "')", null);
            syncSnapshot(route);
            requestNativeData(route, true);
            return;
        }
        webView.evaluateJavascript("window.__tjDropJob&&window.__tjDropJob('" + id + "')", null);
        syncSnapshot(route, () -> {
            if (generation != sessionGeneration) return;
            JSONObject data = nativeDataCache.get(route);
            boolean mapped = data != null;
            for (String key : "home".equals(route)
                    ? Arrays.asList("tasks", "score", "attendance", "group", "stream", "scoreSeries", "attendanceSeries", "exams", "leaders", "activity")
                    : Collections.singletonList(snapshotKey(route))) mapped &= data != null && data.has(key);
            String time = android.text.format.DateFormat.getTimeFormat(this).format(new java.util.Date());
            String message = complete && mapped ? "Обновлено в " + time + " · Обновить"
                    : complete ? "Часть данных имеет неизвестный формат · Повторить" : failure;
            refreshMessages.put(route, message);
            if (route.equals(requestedRoute)) showCachedNativeData(route);
            syncNativeHeader();
            finishHeaderLoading();
        });
        // Warm each unopened core screen after the preceding successful request.
        if (complete) for (String next : Arrays.asList("home", "schedule", "grades", "tasks")) {
            if (!refreshMessages.containsKey(next)) {
                handler.postDelayed(() -> { if (generation == sessionGeneration) requestNativeData(next, false); }, 500);
                break;
            }
        }
    }

    private void attemptInternalNavigation(String key, int generation) {
        if (generation != navigationGeneration || webView == null) return;
        navigationAttempt++;
        String[] terms = termsForKey(key);
        JSONArray jsonTerms = new JSONArray(Arrays.asList(terms));
        String script = "(function(){" +
                "const terms=" + jsonTerms + ";" +
                "const norm=s=>(s||'').toLowerCase().replace(/ё/g,'е').trim();" +
                "const key=" + JSONObject.quote(key) + ";const path=location.pathname.toLowerCase();" +
                "const routeParts={home:'/dashboard/',schedule:'/schedule/',grades:'/progress/',tasks:'/homework/'," +
                "materials:'/library/',announcements:'/news/',rewards:'/rewards/',reviews:'/feedback/',payment:'/payment/'," +
                "profile:'/settings/',faq:'/faq/',contacts:'/contacts/',tickets:'/signal/',complaints:'/complaints/',shop:'/market/'};" +
                "if(routeParts[key]&&path.includes(routeParts[key]))return 'already';" +
                "const classes={home:'main-item',schedule:'schedule-item',grades:'progress-item',tasks:'homeworks-item'," +
                "materials:'library-item',announcements:'news-item',rewards:'rewards-item',reviews:'feedback-item'," +
                "payment:'payment-item',profile:'profile-item',faq:'faq-item',contacts:'contacts-item'," +
                "tickets:'signal-item',complaints:'complaints',shop:'market-item'};" +
                "let exact=classes[key]?document.querySelector('.'+classes[key]):null;" +
                "if(exact){const target=exact.matches('a,button,[role=button],[role=menuitem],[routerlink]')?exact:" +
                "(exact.querySelector('a,button,[role=button],[role=menuitem],[routerlink]')||exact);" +
                "target.click();return 'clicked';}" +
                "const roots=[...document.querySelectorAll('nav,aside,[role=navigation],[class*=sidebar],[class*=side-menu],[class*=menu],mystat')];" +
                "const nodes=[...new Set(roots.flatMap(r=>[...r.querySelectorAll('a,button,[role=button],[role=menuitem],[onclick],[routerlink]')]))];" +
                "let best=null,bestScore=0,ties=0;" +
                "for(const el of nodes){" +
                "const txt=norm((el.innerText||'')+' '+(el.getAttribute('aria-label')||'')+' '+(el.title||''));" +
                "const href=norm(el.href||el.getAttribute('href')||'')+' '+norm(el.getAttribute('routerlink')||'');let score=0;" +
                "if(routeParts[key]&&href.includes(routeParts[key].replaceAll('/','')))score=120;" +
                "for(const raw of terms){const t=norm(raw);if(!t)continue;if(txt===t)score=Math.max(score,100);" +
                "else if(txt.includes(t))score=Math.max(score,55);if(href.includes(t))score=Math.max(score,45);}" +
                "if(score>bestScore){best=el;bestScore=score;ties=1;}else if(score===bestScore&&score>0){ties++;}}" +
                "if(best&&bestScore>=40&&(ties===1||bestScore>=100)){" +
                "best.click();return 'clicked';}" +
                "return 'missing';})()";
        webView.evaluateJavascript(script, value -> {
            if (generation != navigationGeneration) return;
            String result = decodeJavascriptString(value);
            if ("already".equals(result)) {
                completeInternalNavigation(key, generation);
                return;
            }
            handler.postDelayed(() -> confirmInternalNavigation(key, generation),
                    "clicked".equals(result) ? 260 : 420);
        });
    }

    private void confirmInternalNavigation(String key, int generation) {
        if (generation != navigationGeneration || webView == null) return;
        String expected = expectedPathForRoute(key);
        String script = "(function(){const p=location.pathname.toLowerCase();return " +
                JSONObject.quote(expected) + "===''||p.includes(" + JSONObject.quote(expected) + ");})()";
        webView.evaluateJavascript(script, value -> {
            if (generation != navigationGeneration) return;
            if ("true".equals(value)) {
                completeInternalNavigation(key, generation);
            } else if (navigationAttempt < 10) {
                navigationAttempt++;
                handler.postDelayed(() -> confirmInternalNavigation(key, generation), 320);
            } else {
                navigationInFlight = false;
                navigationRoute = "";
                requestedRoute = confirmedRoute;
                if (isNativeRoute(requestedRoute)) { hybridLoading = false; showCachedNativeData(requestedRoute); }
                if (isNativeRoute(confirmedRoute)) updateNavigation(navigationTab(confirmedRoute));
                else updateNavigation("more");
                Toast.makeText(this, "Не удалось открыть раздел. Повторите после завершения загрузки.", Toast.LENGTH_LONG).show();
                handler.postDelayed(this::syncNativeRoute, 150);
            }
        });
    }

    private void completeInternalNavigation(String key, int generation) {
        if (generation != navigationGeneration) return;
        confirmedRoute = key;
        requestedRoute = key;
        navigationInFlight = false;
        navigationRoute = "";
        handler.postDelayed(() -> {
            if (generation == navigationGeneration && signedIn && !connectionUnavailable) {
                hybridLoading = false;
                updateNativeHomeVisibility();
            }
        }, 350);
        if (isNativeRoute(key)) updateNavigation(navigationTab(key));
        else {
            updateNavigation("more");
            setPageTitleAnimated(labelForRoute(key));
        }
        handler.postDelayed(this::applyMobileStyle, 220);
        handler.postDelayed(this::syncNativeHeader, 360);
        if ("home".equals(key)) {
            handler.postDelayed(this::syncNativeHome, 420);
            handler.postDelayed(this::syncNativeHome, 1300);
        } else if (isNativeRoute(key)) {
            handler.postDelayed(this::syncNativeRoute, 420);
            handler.postDelayed(this::syncNativeRoute, 1300);
            handler.postDelayed(this::syncNativeRoute, 2800);
            handler.postDelayed(this::syncNativeRoute, 5200);
        }
    }

    private String expectedPathForRoute(String key) {
        switch (key) {
            case "home": return "/dashboard/";
            case "schedule": return "/schedule/";
            case "grades": return "/progress/";
            case "tasks": return "/homework/";
            case "materials": return "/library/";
            case "announcements": return "/news/";
            case "rewards": return "/rewards/";
            case "reviews": return "/feedback/";
            case "payment": return "/payment/";
            case "profile": return "/settings/";
            case "faq": return "/faq/";
            case "contacts": return "/contacts/";
            case "tickets": return "/signal/";
            case "complaints": return "/complaints/";
            case "shop": return "/market/";
            default: return "";
        }
    }

    private String[] termsForKey(String key) {
        switch (key) {
            case "schedule": return new String[]{"расписание", "schedule", "timetable", "calendar"};
            case "grades": return new String[]{"оценки", "grades", "marks", "rating", "progress"};
            case "tasks": return new String[]{"домашние задания", "homework", "assignments", "tasks"};
            case "materials": return new String[]{"учебные материалы", "materials", "library"};
            case "announcements": return new String[]{"объявления", "announcements", "notifications", "news"};
            case "rewards": return new String[]{"ваши награды", "награды", "rewards", "awards"};
            case "reviews": return new String[]{"отзывы о студенте", "отзывы", "reviews", "feedback"};
            case "payment": return new String[]{"информация для оплаты", "оплата", "payments", "payment"};
            case "profile": return new String[]{"личный кабинет", "профиль", "profile", "account"};
            case "faq": return new String[]{"часто задаваемые вопросы", "faq", "help"};
            case "contacts": return new String[]{"контакты", "contacts"};
            case "tickets": return new String[]{"обращения", "appeals", "tickets", "signals"};
            case "complaints": return new String[]{"жалобы", "complaints"};
            case "shop": return new String[]{"магазин", "shop", "store"};
            default: return new String[]{key};
        }
    }

    private void detectCurrentSection() {
        if (webView == null || !signedIn || "material_detail".equals(requestedRoute)
                || (shellVisible && isNativeRoute(requestedRoute) && !navigationInFlight)) return;
        String script = "(function(){const visible=e=>{const s=getComputedStyle(e);return s.display!=='none'&&s.visibility!=='hidden';};" +
                "const heads=[...document.querySelectorAll('h1,h2,h3')].filter(visible).slice(0,4).map(e=>e.innerText||'').join(' | ');" +
                "return location.pathname+' '+document.title+' '+heads;})()";
        webView.evaluateJavascript(script, value -> {
            if (!signedIn) return;
            if ("material_detail".equals(requestedRoute) || (shellVisible && isNativeRoute(requestedRoute) && !navigationInFlight)) return;
            String descriptor = decodeJavascriptString(value).toLowerCase(Locale.ROOT).replace('ё', 'е');
            String section = classifySection(descriptor);
            if (section == null) {
                if (navigationInFlight) return;
                requestedRoute = "more";
                updateNavigation("more");
                return;
            }
            if (navigationInFlight && !section.equals(navigationRoute)) return;
            confirmedRoute = section;
            requestedRoute = section;
            if (isNativeRoute(section)) {
                updateNavigation(navigationTab(section));
                setPageTitleAnimated(labelForRoute(section));
                showCachedNativeData(section);
                if ("home".equals(section)) handler.postDelayed(this::syncNativeHome, 180);
                else handler.postDelayed(this::syncNativeRoute, 240);
            } else {
                updateNavigation("more");
                setPageTitleAnimated(labelForRoute(section));
            }
        });
    }

    private String labelForRoute(String key) {
        switch (key) {
            case "home": return "Главная";
            case "schedule": return "Расписание";
            case "assistant": return "AI-аналитик";
            case "grades": return "Оценки";
            case "tasks": return "Задания";
            case "materials": return "Материалы";
            case "announcements": return "Объявления";
            case "rewards": return "Награды";
            case "reviews": return "Отзывы";
            case "payment": return "Оплата";
            case "profile": return "Профиль";
            case "faq": return "Помощь";
            case "contacts": return "Контакты";
            case "tickets": return "Обращения";
            case "complaints": return "Жалобы";
            case "shop": return "Магазин";
            default: return "Ещё";
        }
    }

    private String classifySection(String value) {
        Uri uri = Uri.parse(webView.getUrl() == null ? BASE_URL : webView.getUrl());
        String rawPath = uri.getPath();
        String path = rawPath == null ? "" : rawPath.toLowerCase(Locale.ROOT);
        if (path.contains("/dashboard/")) return "home";
        if (path.contains("/schedule/")) return "schedule";
        if (path.contains("/progress/")) return "grades";
        if (path.contains("/homework/")) return "tasks";
        if (path.contains("/library/")) return "materials";
        if (path.contains("/news/")) return "announcements";
        if (path.contains("/rewards/")) return "rewards";
        if (path.contains("/feedback/")) return "reviews";
        if (path.contains("/payment/")) return "payment";
        if (path.contains("/settings/")) return "profile";
        if (path.contains("/faq/")) return "faq";
        if (path.contains("/contacts/")) return "contacts";
        if (path.contains("/signal/")) return "tickets";
        if (path.contains("/complaints/")) return "complaints";
        if (path.contains("/market/") || path.contains("/shop/")) return "shop";
        if (matchesAny(value, "домашние задания", "homework", "assignment")) return "tasks";
        if (matchesAny(value, "расписание", "schedule", "timetable")) return "schedule";
        if (matchesAny(value, "оценки", "grades", "marks")) return "grades";
        if (matchesAny(value, "учебные материалы", "materials")) return "materials";
        if (matchesAny(value, "объявления", "announcements")) return "announcements";
        if (matchesAny(value, "награды", "rewards", "awards")) return "rewards";
        if (matchesAny(value, "отзывы", "reviews")) return "reviews";
        if (matchesAny(value, "оплата", "payment")) return "payment";
        if (matchesAny(value, "личный кабинет", "profile", "account")) return "profile";
        if (matchesAny(value, "часто задаваемые", "faq")) return "faq";
        if (matchesAny(value, "контакты", "contacts")) return "contacts";
        if (matchesAny(value, "обращения", "tickets")) return "tickets";
        if (matchesAny(value, "жалобы", "complaints")) return "complaints";
        if (matchesAny(value, "магазин", "shop", "store")) return "shop";
        if (path == null || path.equals("/") || path.isEmpty()) return "home";
        return null;
    }

    private boolean matchesAny(String value, String... terms) {
        for (String term : terms) {
            if (value.contains(term)) return true;
        }
        return false;
    }

    private String decodeJavascriptString(String value) {
        if (value == null || "null".equals(value)) return "";
        try {
            return new JSONArray("[" + value + "]").getString(0);
        } catch (JSONException error) {
            return value.replace("\"", "");
        }
    }

    private void copySanitizedPageMap() {
        if (webView == null) return;
        String script = "(function(){" +
                "if(location.protocol!=='https:'||location.hostname!=='journal.top-academy.ru')return '';" +
                "const known=['главная','расписание','оценки','домашние задания','учебные материалы','объявления','ваши награды'," +
                "'отзывы о студенте','информация для оплаты','личный кабинет','часто задаваемые вопросы','контакты','обращения','жалобы','магазин'];" +
                "const norm=s=>(s||'').replace(/\\s+/g,' ').trim().toLowerCase();" +
                "const safePath=value=>{try{return new URL(value,location.href).pathname.replace(/\\d+/g,'#');}catch(e){return '';}};" +
                "const headings=[...document.querySelectorAll('h1,h2,h3,[data-topjournal-page-heading]')].map(e=>norm(e.innerText))" +
                ".filter(t=>known.some(k=>t===k||t.includes(k))).slice(0,12);" +
                "const roots=[...document.querySelectorAll('nav,aside,[role=navigation],[class*=sidebar],[class*=menu]')];" +
                "const nav=[...new Set(roots.flatMap(r=>[...r.querySelectorAll('a,button,[role=menuitem]')]))].map(el=>{" +
                "const label=norm((el.getAttribute('aria-label')||'')+' '+(el.title||'')+' '+(el.innerText||''));" +
                "const matched=known.find(k=>label===k||label.includes(k))||'';return {tag:el.tagName.toLowerCase(),label:matched," +
                "path:safePath(el.href||el.getAttribute('href')||''),class:String(el.className||'').slice(0,120)};})" +
                ".filter(x=>x.label||x.path).slice(0,40);" +
                "const samples=[...document.querySelectorAll('[data-topjournal-content-root],[data-topjournal-card],[data-topjournal-toolbar]')]" +
                ".slice(0,60).map(el=>({tag:el.tagName.toLowerCase(),role:el.hasAttribute('data-topjournal-content-root')?'root':" +
                "el.hasAttribute('data-topjournal-toolbar')?'toolbar':'card',class:String(el.className||'').slice(0,140),children:el.children.length}));" +
                "return JSON.stringify({app:'TopJournal 0.2',path:location.pathname.replace(/\\d+/g,'#'),route:document.body?.dataset.tjRoute||'',headings,nav,samples});})()";
        webView.evaluateJavascript(script, value -> {
            String map = decodeJavascriptString(value);
            if (map.isEmpty()) {
                Toast.makeText(this, "Техническая карта доступна только на странице журнала", Toast.LENGTH_LONG).show();
                return;
            }
            ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            clipboard.setPrimaryClip(ClipData.newPlainText("TopJournal page map", map));
            Toast.makeText(this, "Обезличенная карта страницы скопирована", Toast.LENGTH_LONG).show();
        });
    }

    private static final String APP_CSS = """
            :root {
              --tj-bg: #fcfcfc;
              --tj-surface: #f7f7f7;
              --tj-surface-soft: #ffffff;
              --tj-accent: #f0f0f0;
              --tj-text: #000000;
              --tj-muted: #666666;
              --tj-border: #e5e5e5;
              --tj-border-soft: #eeeeee;
              --tj-danger: #e5484d;
              --tj-success: #16a34a;
              --tj-warning: #f5a623;
              --tj-radius: 8px;
            }
            html, body {
              background: var(--tj-bg) !important;
              color: var(--tj-text) !important;
              width: 100% !important;
              max-width: 100vw !important;
              min-width: 0 !important;
              overflow-x: hidden !important;
            }
            body.top-journal-app {
              margin: 0 !important;
              font-family: Geist, -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Arial, sans-serif !important;
              -webkit-font-smoothing: antialiased;
              background: var(--tj-bg) !important;
            }
            .top-journal-app *, .top-journal-app *::before, .top-journal-app *::after {
              box-sizing: border-box !important;
            }
            .top-journal-app img,
            .top-journal-app video,
            .top-journal-app canvas,
            .top-journal-app svg { max-width: 100% !important; }
            .top-journal-app [data-topjournal-hidden-sidebar],
            .top-journal-app [data-topjournal-hidden-promo],
            .top-journal-app [data-topjournal-hidden-header],
            .top-journal-app [data-topjournal-hidden-help],
            .top-journal-app [data-topjournal-hidden-spacer],
            .top-journal-app [data-topjournal-hidden-before-content],
            .top-journal-app [data-topjournal-hidden-original],
            .top-journal-app [data-topjournal-original-root] { display: none !important; }
            .top-journal-app[data-tj-signed="1"] top-pane,
            .top-journal-app[data-tj-signed="1"] app-story,
            .top-journal-app[data-tj-signed="1"] .top-panel {
              display: none !important;
              width: 0 !important;
              height: 0 !important;
              min-height: 0 !important;
              margin: 0 !important;
              padding: 0 !important;
              overflow: hidden !important;
            }

            .top-journal-app [data-topjournal-app-view] {
              display: block !important;
              width: 100% !important;
              max-width: 100% !important;
              min-height: calc(100vh - 96px) !important;
              margin: 0 !important;
              padding: 18px 16px 116px !important;
              color: var(--tj-text) !important;
              background: var(--tj-bg) !important;
              animation: tj-view-enter .28s cubic-bezier(.2,.8,.2,1) both !important;
            }
            .top-journal-app [data-topjournal-app-view] * {
              box-sizing: border-box !important;
            }
            .top-journal-app [data-topjournal-app-view] .tj-view-kicker {
              margin: 2px 2px 8px !important;
              color: var(--tj-muted) !important;
              font-size: 12px !important;
              font-weight: 750 !important;
              letter-spacing: .7px !important;
              text-transform: uppercase !important;
              font-family: "Courier New", monospace !important;
            }
            .top-journal-app [data-topjournal-app-view] .tj-view-title {
              margin: 0 2px 18px !important;
              color: var(--tj-text) !important;
              font-family: "Times New Roman", Georgia, serif !important;
              font-size: 31px !important;
              font-weight: 800 !important;
              letter-spacing: -.8px !important;
              line-height: 1.08 !important;
            }
            .top-journal-app [data-topjournal-app-view] .tj-hero {
              margin: 0 0 18px !important;
              padding: 22px !important;
              border: 1px solid var(--tj-border) !important;
              border-radius: 12px !important;
              color: var(--tj-text) !important;
              background: var(--tj-accent) !important;
              box-shadow: none !important;
            }
            .top-journal-app [data-topjournal-app-view] .tj-hero-head,
            .top-journal-app [data-topjournal-app-view] .tj-row {
              display: flex !important;
              align-items: center !important;
              justify-content: space-between !important;
              gap: 12px !important;
            }
            .top-journal-app [data-topjournal-app-view] .tj-hero h2 {
              margin: 0 !important;
              color: var(--tj-text) !important;
              font-family: "Times New Roman", Georgia, serif !important;
              font-size: 22px !important;
              font-weight: 780 !important;
            }
            .top-journal-app [data-topjournal-app-view] .tj-hero-total {
              margin: 26px 0 20px !important;
              color: var(--tj-text) !important;
              font-family: "Times New Roman", Georgia, serif !important;
              font-size: 48px !important;
              font-weight: 800 !important;
              line-height: 1 !important;
            }
            .top-journal-app [data-topjournal-app-view] .tj-hero-total span {
              margin-left: 8px !important;
              color: var(--tj-muted) !important;
              font-family: "Courier New", monospace !important;
              font-size: 14px !important;
              font-weight: 500 !important;
            }
            .top-journal-app [data-topjournal-app-view] .tj-action {
              min-height: 40px !important;
              padding: 9px 14px !important;
              border: 1px solid var(--tj-border) !important;
              border-radius: var(--tj-radius) !important;
              color: var(--tj-text) !important;
              background: var(--tj-bg) !important;
              font-size: 13px !important;
              font-weight: 750 !important;
            }
            .top-journal-app [data-topjournal-app-view] .tj-metrics {
              display: grid !important;
              grid-template-columns: 1fr 1fr !important;
              gap: 8px !important;
            }
            .top-journal-app [data-topjournal-app-view] .tj-metric {
              display: flex !important;
              align-items: center !important;
              justify-content: space-between !important;
              min-height: 48px !important;
              padding: 12px 13px !important;
              border: 1px solid var(--tj-border-soft) !important;
              border-radius: var(--tj-radius) !important;
              color: var(--tj-muted) !important;
              background: var(--tj-bg) !important;
              font-size: 13px !important;
            }
            .top-journal-app [data-topjournal-app-view] .tj-metric b {
              color: var(--tj-text) !important;
              font-size: 16px !important;
            }
            .top-journal-app [data-topjournal-app-view] .tj-section-title {
              margin: 26px 2px 13px !important;
              color: var(--tj-text) !important;
              font-family: "Times New Roman", Georgia, serif !important;
              font-size: 22px !important;
              font-weight: 780 !important;
            }
            .top-journal-app [data-topjournal-app-view] .tj-grid {
              display: grid !important;
              grid-template-columns: 1fr 1fr !important;
              gap: 10px !important;
            }
            .top-journal-app [data-topjournal-app-view] .tj-tile,
            .top-journal-app [data-topjournal-app-view] .tj-card {
              width: 100% !important;
              margin: 0 !important;
              padding: 17px !important;
              border: 1px solid var(--tj-border) !important;
              border-radius: 12px !important;
              color: var(--tj-text) !important;
              background: var(--tj-surface-soft) !important;
              box-shadow: none !important;
              text-align: left !important;
            }
            .top-journal-app [data-topjournal-app-view] .tj-tile {
              min-height: 116px !important;
            }
            .top-journal-app [data-topjournal-app-view] .tj-tile b,
            .top-journal-app [data-topjournal-app-view] .tj-card b {
              display: block !important;
              margin-bottom: 6px !important;
              color: var(--tj-text) !important;
              font-family: "Times New Roman", Georgia, serif !important;
              font-size: 16px !important;
            }
            .top-journal-app [data-topjournal-app-view] .tj-tile span,
            .top-journal-app [data-topjournal-app-view] .tj-card span {
              color: var(--tj-muted) !important;
              font-size: 13px !important;
              line-height: 1.35 !important;
            }
            .top-journal-app [data-topjournal-app-view] .tj-stack {
              display: grid !important;
              gap: 10px !important;
            }
            .top-journal-app [data-topjournal-app-view] .tj-bento {
              display: grid !important;
              grid-template-columns: 1fr 1fr !important;
              gap: 10px !important;
            }
            .top-journal-app [data-topjournal-app-view] .tj-stat-card {
              position: relative !important;
              min-height: 142px !important;
              padding: 17px !important;
              border: 1px solid var(--tj-border) !important;
              border-radius: 12px !important;
              color: var(--tj-text) !important;
              background: var(--tj-surface-soft) !important;
              overflow: hidden !important;
            }
            .top-journal-app [data-topjournal-app-view] .tj-stat-card.tj-dark {
              color: var(--tj-text) !important;
              border-color: var(--tj-border) !important;
              background: var(--tj-accent) !important;
            }
            .top-journal-app [data-topjournal-app-view] .tj-stat-card.tj-wide {
              grid-column: 1 / -1 !important;
              min-height: 112px !important;
            }
            .top-journal-app [data-topjournal-app-view] .tj-stat-label {
              color: var(--tj-muted) !important;
              font-size: 12px !important;
              font-weight: 650 !important;
              line-height: 1.25 !important;
            }
            .top-journal-app [data-topjournal-app-view] .tj-dark .tj-stat-label {
              color: #a7a7ad !important;
            }
            .top-journal-app [data-topjournal-app-view] .tj-stat-value {
              margin-top: 18px !important;
              color: var(--tj-text) !important;
              font-size: 34px !important;
              font-weight: 820 !important;
              letter-spacing: -1px !important;
              line-height: 1 !important;
            }
            .top-journal-app [data-topjournal-app-view] .tj-dark .tj-stat-value {
              color: var(--tj-text) !important;
            }
            .top-journal-app [data-topjournal-app-view] .tj-stat-note {
              margin-top: 7px !important;
              color: var(--tj-muted) !important;
              font-size: 12px !important;
            }
            .top-journal-app [data-topjournal-app-view] .tj-dark .tj-stat-note {
              color: #929298 !important;
            }
            .top-journal-app [data-topjournal-app-view] .tj-list-card {
              padding: 8px 16px !important;
              border: 1px solid var(--tj-border) !important;
              border-radius: 12px !important;
              background: var(--tj-surface-soft) !important;
            }
            .top-journal-app [data-topjournal-app-view] .tj-list-row {
              display: flex !important;
              align-items: center !important;
              gap: 12px !important;
              min-height: 64px !important;
              padding: 10px 0 !important;
              border-bottom: 1px solid var(--tj-border) !important;
            }
            .top-journal-app [data-topjournal-app-view] .tj-list-row:last-child {
              border-bottom: 0 !important;
            }
            .top-journal-app [data-topjournal-app-view] .tj-rank {
              display: grid !important;
              flex: 0 0 36px !important;
              width: 36px !important;
              height: 36px !important;
              place-items: center !important;
              border: 1px solid var(--tj-border) !important;
              border-radius: var(--tj-radius) !important;
              color: var(--tj-text) !important;
              background: var(--tj-accent) !important;
              font-size: 13px !important;
              font-weight: 800 !important;
            }
            .top-journal-app [data-topjournal-app-view] .tj-list-copy {
              min-width: 0 !important;
              flex: 1 !important;
            }
            .top-journal-app [data-topjournal-app-view] .tj-list-copy b {
              display: block !important;
              color: var(--tj-text) !important;
              font-size: 14px !important;
              line-height: 1.3 !important;
            }
            .top-journal-app [data-topjournal-app-view] .tj-list-copy span {
              display: block !important;
              margin-top: 3px !important;
              color: var(--tj-muted) !important;
              font-size: 12px !important;
              line-height: 1.3 !important;
            }
            .top-journal-app [data-topjournal-app-view] .tj-pill {
              flex: 0 0 auto !important;
              padding: 7px 10px !important;
              border-radius: 999px !important;
              color: var(--tj-text) !important;
              background: var(--tj-surface) !important;
              font-size: 11px !important;
              font-weight: 750 !important;
            }
            .top-journal-app [data-topjournal-app-view] .tj-empty {
              padding: 34px 22px !important;
              border: 1px solid var(--tj-border) !important;
              border-radius: 12px !important;
              color: var(--tj-muted) !important;
              background: var(--tj-surface-soft) !important;
              text-align: center !important;
              font-size: 14px !important;
              line-height: 1.45 !important;
            }
            .top-journal-app [data-topjournal-app-view] > * {
              animation: tj-card-enter .32s cubic-bezier(.2,.8,.2,1) both !important;
            }
            .top-journal-app [data-topjournal-app-view] > :nth-child(2) { animation-delay: .035s !important; }
            .top-journal-app [data-topjournal-app-view] > :nth-child(3) { animation-delay: .07s !important; }
            .top-journal-app [data-topjournal-app-view] > :nth-child(4) { animation-delay: .105s !important; }
            .top-journal-app [data-topjournal-app-view] > :nth-child(n+5) { animation-delay: .14s !important; }
            .top-journal-app [data-topjournal-app-view] button:active {
              transform: scale(.97) !important;
              transition: transform .09s ease !important;
            }
            @keyframes tj-view-enter {
              from { opacity: 0; transform: translateY(8px); }
              to { opacity: 1; transform: translateY(0); }
            }
            @keyframes tj-card-enter {
              from { opacity: 0; transform: translateY(12px); }
              to { opacity: 1; transform: translateY(0); }
            }
            @media (prefers-reduced-motion: reduce) {
              .top-journal-app [data-topjournal-app-view],
              .top-journal-app [data-topjournal-app-view] > * {
                animation: none !important;
                transition: none !important;
              }
            }

            .top-journal-app [data-topjournal-teal-surface] {
              background: transparent !important;
              background-color: transparent !important;
            }
            .top-journal-app[data-tj-signed="1"] mystat,
            .top-journal-app[data-tj-signed="1"] mystat > ng-component,
            .top-journal-app[data-tj-signed="1"] mystat > ng-component > ng-component {
              display: block !important;
              width: 100% !important;
              max-width: 100% !important;
              min-width: 0 !important;
              margin: 0 !important;
              padding: 0 !important;
              background: var(--tj-bg) !important;
            }
            .top-journal-app[data-tj-signed="1"] .homepage-wrapper {
              display: block !important;
              width: 100% !important;
              max-width: 100% !important;
              min-width: 0 !important;
              margin: 0 !important;
              padding: 14px 16px 112px !important;
              background: var(--tj-bg) !important;
            }
            .top-journal-app[data-tj-signed="1"] .homepage-wrapper > .row {
              display: block !important;
              width: 100% !important;
              margin: 0 !important;
            }
            .top-journal-app[data-tj-signed="1"] .homepage-wrapper .block-item,
            .top-journal-app[data-tj-signed="1"] .homepage-wrapper [class*="col-md-"] {
              display: block !important;
              width: 100% !important;
              max-width: 100% !important;
              min-width: 0 !important;
              margin: 0 0 14px !important;
              padding: 0 !important;
              flex: none !important;
            }
            .top-journal-app[data-tj-signed="1"] .homepage-wrapper .part-container {
              width: 100% !important;
              margin: 0 !important;
              padding: 18px !important;
              border: 1px solid var(--tj-border) !important;
              border-radius: 12px !important;
              background: var(--tj-surface-soft) !important;
              box-shadow: none !important;
            }

            .top-journal-app [data-topjournal-content-root] {
              display: block !important;
              width: 100% !important;
              max-width: 100% !important;
              min-width: 0 !important;
              min-height: calc(100vh - 64px) !important;
              margin: 0 !important;
              padding: 14px 16px 112px !important;
              background: var(--tj-bg) !important;
              color: var(--tj-text) !important;
              overflow: visible !important;
            }
            .top-journal-app [data-topjournal-page-heading] {
              display: none !important;
            }
            .top-journal-app [data-topjournal-native-summary] {
              display: block !important;
              width: 100% !important;
              margin: 0 0 16px !important;
              padding: 20px !important;
              border: 1px solid var(--tj-border) !important;
              border-radius: 12px !important;
              color: var(--tj-text) !important;
              background: var(--tj-accent) !important;
              box-shadow: none !important;
            }
            .top-journal-app [data-topjournal-native-summary] .tj-summary-head {
              display: flex !important;
              align-items: flex-start !important;
              justify-content: space-between !important;
              gap: 12px !important;
            }
            .top-journal-app [data-topjournal-native-summary] h2 {
              margin: 3px 0 0 !important;
              color: var(--tj-text) !important;
              font-family: "Times New Roman", Georgia, serif !important;
              font-size: 22px !important;
              font-weight: 750 !important;
            }
            .top-journal-app [data-topjournal-native-summary] .tj-eyebrow {
              color: var(--tj-muted) !important;
              font-family: "Courier New", monospace !important;
              font-size: 12px !important;
              font-weight: 700 !important;
              letter-spacing: .5px !important;
              text-transform: uppercase !important;
            }
            .top-journal-app [data-topjournal-native-summary] .tj-open {
              min-height: 40px !important;
              padding: 8px 13px !important;
              color: var(--tj-text) !important;
              border: 1px solid var(--tj-border) !important;
              border-radius: var(--tj-radius) !important;
              background: #fff !important;
              font-size: 13px !important;
              font-weight: 700 !important;
            }
            .top-journal-app [data-topjournal-native-summary] .tj-summary-total {
              display: flex !important;
              align-items: baseline !important;
              gap: 10px !important;
              margin: 22px 0 18px !important;
            }
            .top-journal-app [data-topjournal-native-summary] .tj-summary-total strong {
              color: var(--tj-text) !important;
              font-family: "Times New Roman", Georgia, serif !important;
              font-size: 46px !important;
              line-height: 1 !important;
            }
            .top-journal-app [data-topjournal-native-summary] .tj-summary-total span {
              color: var(--tj-muted) !important;
              font-size: 14px !important;
            }
            .top-journal-app [data-topjournal-native-summary] .tj-summary-grid {
              display: grid !important;
              grid-template-columns: 1fr 1fr !important;
              gap: 8px !important;
            }
            .top-journal-app [data-topjournal-native-summary] .tj-stat {
              display: flex !important;
              justify-content: space-between !important;
              gap: 8px !important;
              padding: 11px 12px !important;
              border: 1px solid var(--tj-border-soft) !important;
              border-radius: var(--tj-radius) !important;
              color: var(--tj-muted) !important;
              background: #fff !important;
              font-size: 13px !important;
            }
            .top-journal-app [data-topjournal-native-summary] .tj-stat b { color: var(--tj-text) !important; }
            .top-journal-app [data-topjournal-card] {
              width: 100% !important;
              max-width: 100% !important;
              min-width: 0 !important;
              margin: 0 0 14px !important;
              padding: 18px !important;
              border: 1px solid var(--tj-border) !important;
              border-radius: 12px !important;
              background: var(--tj-surface-soft) !important;
              color: var(--tj-text) !important;
              box-shadow: none !important;
              overflow: hidden !important;
            }
            .top-journal-app [data-topjournal-card] [data-topjournal-card] {
              padding: 0 !important;
              border: 0 !important;
              background: transparent !important;
            }
            .top-journal-app [data-topjournal-toolbar] {
              display: flex !important;
              flex-wrap: wrap !important;
              align-items: center !important;
              gap: 10px !important;
              width: 100% !important;
              margin: 0 0 18px !important;
              padding: 10px !important;
              border: 1px solid var(--tj-border) !important;
              border-radius: 12px !important;
              background: var(--tj-surface-soft) !important;
            }
            .top-journal-app [data-topjournal-content-root] input,
            .top-journal-app [data-topjournal-content-root] select,
            .top-journal-app [data-topjournal-content-root] textarea,
            .top-journal-app [data-topjournal-content-root] button {
              min-height: 48px !important;
              max-width: 100% !important;
              font: inherit !important;
              font-size: 16px !important;
              color: var(--tj-text) !important;
              border: 1px solid var(--tj-border) !important;
              border-radius: var(--tj-radius) !important;
              background: #fff !important;
              box-shadow: none !important;
            }
            .top-journal-app [data-topjournal-content-root] input,
            .top-journal-app [data-topjournal-content-root] select,
            .top-journal-app [data-topjournal-content-root] textarea {
              width: 100% !important;
              padding: 12px 14px !important;
            }
            .top-journal-app [data-topjournal-content-root] textarea {
              min-height: 120px !important;
              resize: vertical !important;
            }
            .top-journal-app [data-topjournal-content-root] button,
            .top-journal-app [data-topjournal-content-root] input[type="submit"],
            .top-journal-app [data-topjournal-content-root] [class*="btn-primary" i] {
              width: auto !important;
              padding: 11px 20px !important;
              color: #fff !important;
              border-color: var(--tj-text) !important;
              background: var(--tj-text) !important;
              font-weight: 700 !important;
            }
            .top-journal-app [data-topjournal-content-root] a {
              color: var(--tj-text) !important;
              text-decoration-thickness: 1px !important;
              text-underline-offset: 4px !important;
            }
            .top-journal-app [data-topjournal-content-root] h2,
            .top-journal-app [data-topjournal-content-root] h3,
            .top-journal-app [data-topjournal-content-root] h4 {
              color: var(--tj-text) !important;
              line-height: 1.2 !important;
              letter-spacing: -.25px !important;
            }
            .top-journal-app [data-topjournal-content-root] p,
            .top-journal-app [data-topjournal-content-root] label,
            .top-journal-app [data-topjournal-content-root] small {
              line-height: 1.45 !important;
            }
            .top-journal-app [data-topjournal-empty] {
              display: flex !important;
              align-items: center !important;
              justify-content: center !important;
              min-height: 136px !important;
              padding: 28px 18px !important;
              color: var(--tj-muted) !important;
              font-size: 16px !important;
              text-align: center !important;
            }

            .top-journal-app [data-topjournal-content-root] table {
              display: table !important;
              width: 100% !important;
              min-width: 560px !important;
              border: 0 !important;
              border-collapse: separate !important;
              border-spacing: 0 !important;
              border-radius: var(--tj-radius) !important;
              background: #fff !important;
              overflow: hidden !important;
            }
            .top-journal-app [data-topjournal-table-wrap] {
              display: block !important;
              width: 100% !important;
              max-width: 100% !important;
              overflow-x: auto !important;
              border: 1px solid var(--tj-border) !important;
              border-radius: var(--tj-radius) !important;
              -webkit-overflow-scrolling: touch;
            }
            .top-journal-app [data-topjournal-content-root] th {
              padding: 13px !important;
              color: var(--tj-text) !important;
              background: var(--tj-accent) !important;
              border-color: var(--tj-border) !important;
              font-weight: 700 !important;
            }
            .top-journal-app [data-topjournal-content-root] td {
              padding: 13px !important;
              color: var(--tj-text) !important;
              background: #fff !important;
              border-color: var(--tj-border) !important;
            }

            .top-journal-app[data-tj-route="schedule"] [data-topjournal-day] {
              display: flex !important;
              align-items: center !important;
              justify-content: center !important;
              min-width: 42px !important;
              min-height: 48px !important;
              border: 1px solid var(--tj-border) !important;
              border-radius: 999px !important;
              color: var(--tj-text) !important;
              background: var(--tj-surface) !important;
              box-shadow: none !important;
            }
            .top-journal-app[data-tj-route="schedule"] [data-topjournal-day][data-topjournal-selected] {
              color: var(--tj-text) !important;
              border-color: var(--tj-text) !important;
              background: var(--tj-accent) !important;
            }
            .top-journal-app[data-tj-route="announcements"] [data-topjournal-card],
            .top-journal-app[data-tj-route="faq"] [data-topjournal-card],
            .top-journal-app[data-tj-route="contacts"] [data-topjournal-card] {
              padding: 20px !important;
              background: var(--tj-bg) !important;
            }
            .top-journal-app[data-tj-route="rewards"] [data-topjournal-card],
            .top-journal-app[data-tj-route="profile"] [data-topjournal-card] {
              text-align: center !important;
            }
            .top-journal-app[data-tj-route="shop"] [data-topjournal-card] img {
              width: 100% !important;
              max-height: 260px !important;
              object-fit: cover !important;
              border-radius: var(--tj-radius) !important;
              filter: saturate(.7) contrast(1.03) !important;
            }
            .top-journal-app[data-tj-route="login"] [data-topjournal-content-root] {
              min-height: 100vh !important;
              padding: 38px 24px !important;
              background: var(--tj-bg) !important;
            }
            .top-journal-app[data-tj-route="login"] #cover,
            .top-journal-app[data-tj-route="login"] #cover-caption {
              width: 100% !important;
              min-height: 100vh !important;
              margin: 0 !important;
              padding: 0 !important;
              background: var(--tj-bg) !important;
            }
            .top-journal-app[data-tj-route="login"] #cover-caption {
              display: flex !important;
              flex-direction: column !important;
              align-items: center !important;
              justify-content: center !important;
              padding: 40px 22px !important;
            }
            .top-journal-app[data-tj-route="login"] #cover-caption > h1 {
              margin: 0 0 36px !important;
              padding: 0 !important;
              color: var(--tj-text) !important;
              font-family: "Times New Roman", Georgia, serif !important;
              font-size: 40px !important;
              font-weight: 800 !important;
              letter-spacing: -1px !important;
            }
            .top-journal-app[data-tj-route="login"] #cover-caption .container,
            .top-journal-app[data-tj-route="login"] #cover-caption .row,
            .top-journal-app[data-tj-route="login"] #cover-caption .form {
              width: 100% !important;
              max-width: 420px !important;
              margin: 0 !important;
              padding: 0 !important;
            }
            .top-journal-app[data-tj-route="login"] .auth-decoration {
              width: 100% !important;
              margin: 0 !important;
              padding: 22px !important;
              border: 1px solid var(--tj-border) !important;
              border-radius: 12px !important;
              background: var(--tj-surface-soft) !important;
              box-shadow: none !important;
            }
            .top-journal-app[data-tj-route="login"] .nav-tabs { display: none !important; }
            .top-journal-app[data-tj-route="login"] .form-group {
              width: 100% !important;
              margin: 0 0 12px !important;
            }
            .top-journal-app[data-tj-route="login"] .btn-group {
              display: flex !important;
              justify-content: center !important;
              margin: 24px 0 0 !important;
              padding: 0 !important;
            }
            .top-journal-app[data-tj-route="login"] input {
              border: 1px solid var(--tj-border) !important;
              border-radius: var(--tj-radius) !important;
              background: var(--tj-surface-soft) !important;
            }
            .top-journal-app[data-tj-route="login"] button,
            .top-journal-app[data-tj-route="login"] input[type="submit"] {
              color: #fff !important;
              border-color: var(--tj-text) !important;
              background: var(--tj-text) !important;
            }
            .top-journal-app [class*="danger" i],
            .top-journal-app [class*="overdue" i] { color: var(--tj-danger) !important; }
            .top-journal-app [class*="success" i],
            .top-journal-app [class*="checked" i] { color: var(--tj-success) !important; }

            @media (max-width: 760px) {
              .top-journal-app aside,
              .top-journal-app [class*="sidebar" i],
              .top-journal-app [class*="left-menu" i],
              .top-journal-app [class*="side-menu" i],
              .top-journal-app [class*="navigation-left" i] { display: none !important; }
              .top-journal-app main,
              .top-journal-app [role="main"],
              .top-journal-app [class*="main-content" i],
              .top-journal-app [class*="page-content" i],
              .top-journal-app [class*="content-wrapper" i],
              .top-journal-app [class*="container" i] {
                width: 100% !important;
                max-width: 100% !important;
                min-width: 0 !important;
                margin-left: 0 !important;
                margin-right: 0 !important;
              }
              .top-journal-app [class*="banner" i],
              .top-journal-app [class*="promo" i],
              .top-journal-app [class*="advert" i] { display: none !important; }
              .top-journal-app .row,
              .top-journal-app [class*="row" i] { max-width: 100% !important; }
            }

            /* Vercel system — final authority over legacy route-specific styling. */
            :root {
              --tj-bg: #fcfcfc;
              --tj-surface: #f7f7f7;
              --tj-surface-soft: #ffffff;
              --tj-accent: #f0f0f0;
              --tj-text: #000000;
              --tj-muted: #666666;
              --tj-border: #e5e5e5;
              --tj-border-soft: #eeeeee;
              --tj-danger: #e5484d;
              --tj-success: #16a34a;
              --tj-warning: #f5a623;
              --tj-radius: 8px;
            }
            body.top-journal-app,
            .top-journal-app [data-topjournal-app-view],
            .top-journal-app [data-topjournal-content-root],
            .top-journal-app[data-tj-signed="1"] .homepage-wrapper {
              font-family: Geist, "Segoe UI", Roboto, Arial, sans-serif !important;
              background: var(--tj-bg) !important;
              background-image: none !important;
            }
            .top-journal-app [data-topjournal-app-view] { min-height: calc(100vh - 64px) !important; }
            .top-journal-app [data-topjournal-app-view] .tj-view-kicker,
            .top-journal-app [data-topjournal-native-summary] .tj-eyebrow {
              font-family: "Geist Mono", "Roboto Mono", monospace !important;
              letter-spacing: 0 !important;
            }
            .top-journal-app [data-topjournal-app-view] .tj-view-title,
            .top-journal-app [data-topjournal-app-view] .tj-section-title,
            .top-journal-app [data-topjournal-app-view] .tj-card b,
            .top-journal-app [data-topjournal-app-view] .tj-tile b,
            .top-journal-app [data-topjournal-native-summary] h2,
            .top-journal-app [data-topjournal-native-summary] .tj-summary-total strong,
            .top-journal-app[data-tj-route="login"] #cover-caption > h1 {
              font-family: Geist, "Segoe UI", Roboto, Arial, sans-serif !important;
              letter-spacing: -.03em !important;
            }
            .top-journal-app [data-topjournal-app-view] .tj-hero,
            .top-journal-app [data-topjournal-native-summary] {
              color: #fff !important;
              background: #000 !important;
              border-color: #000 !important;
              border-radius: 10px !important;
            }
            .top-journal-app [data-topjournal-app-view] .tj-hero h2,
            .top-journal-app [data-topjournal-app-view] .tj-hero-total,
            .top-journal-app [data-topjournal-native-summary] h2,
            .top-journal-app [data-topjournal-native-summary] .tj-summary-total strong {
              color: #fff !important;
            }
            .top-journal-app [data-topjournal-app-view] .tj-hero-total span,
            .top-journal-app [data-topjournal-native-summary] .tj-summary-total span,
            .top-journal-app [data-topjournal-native-summary] .tj-eyebrow { color: #a1a1a1 !important; }
            .top-journal-app [data-topjournal-app-view] .tj-metric,
            .top-journal-app [data-topjournal-native-summary] .tj-stat {
              color: #b5b5b5 !important;
              background: #1c1c1c !important;
              border-color: #2b2b2b !important;
            }
            .top-journal-app [data-topjournal-app-view] .tj-metric b,
            .top-journal-app [data-topjournal-native-summary] .tj-stat b { color: #fff !important; }
            .top-journal-app [data-topjournal-app-view] .tj-card,
            .top-journal-app [data-topjournal-app-view] .tj-tile,
            .top-journal-app [data-topjournal-app-view] .tj-stat-card,
            .top-journal-app [data-topjournal-app-view] .tj-list-card,
            .top-journal-app [data-topjournal-card],
            .top-journal-app[data-tj-signed="1"] .homepage-wrapper .part-container,
            .top-journal-app [data-topjournal-toolbar],
            .top-journal-app [data-topjournal-table-wrap] {
              border: 1px solid var(--tj-border) !important;
              border-radius: var(--tj-radius) !important;
              background: #fff !important;
              box-shadow: 0 1px 2px rgba(0,0,0,.035) !important;
            }
            .top-journal-app [data-topjournal-app-view] .tj-stat-card.tj-dark {
              color: #fff !important;
              background: #000 !important;
              border-color: #000 !important;
            }
            .top-journal-app [data-topjournal-app-view] .tj-rank {
              color: #fff !important;
              background: #000 !important;
              border-color: #000 !important;
              border-radius: 6px !important;
            }
            .top-journal-app [data-topjournal-app-view] .tj-pill { background: var(--tj-surface) !important; }
            .top-journal-app [data-topjournal-content-root] button,
            .top-journal-app [data-topjournal-content-root] input,
            .top-journal-app [data-topjournal-content-root] select,
            .top-journal-app [data-topjournal-content-root] textarea,
            .top-journal-app[data-tj-route="login"] input { border-radius: var(--tj-radius) !important; }
            .top-journal-app[data-tj-route="schedule"] [data-topjournal-day] {
              color: var(--tj-muted) !important;
              background: #fff !important;
              border-color: var(--tj-border) !important;
              border-radius: 7px !important;
            }
            .top-journal-app[data-tj-route="schedule"] [data-topjournal-day][data-topjournal-selected] {
              color: #fff !important;
              background: #000 !important;
              border-color: #000 !important;
            }
            """;

    private static final String APP_JS = """
            (function(){
              if (location.protocol !== 'https:' || location.hostname !== 'journal.top-academy.ru') return false;
              const styleId = 'top-journal-app-style';
              const css = __CSS__;
              const preferredRoute = __ROUTE__;
              const labels = {
                home: 'Главная', schedule: 'Расписание', grades: 'Оценки', tasks: 'Домашние задания',
                materials: 'Учебные материалы', announcements: 'Объявления', rewards: 'Ваши награды',
                reviews: 'Отзывы о студенте', payment: 'Информация для оплаты', profile: 'Личный кабинет',
                faq: 'Часто задаваемые вопросы', contacts: 'Контакты', tickets: 'Обращения',
                complaints: 'Жалобы', shop: 'Магазин'
              };
              const norm = value => (value || '').replace(/\\s+/g, ' ').trim().toLowerCase().replace(/ё/g, 'е');
              const visible = el => {
                if (!el) return false;
                const style = getComputedStyle(el);
                const rect = el.getBoundingClientRect();
                return style.display !== 'none' && style.visibility !== 'hidden' && rect.width > 1 && rect.height > 1;
              };
              const nearWhite = color => /rgba?\\((24[4-9]|25[0-5]),\\s*(24[4-9]|25[0-5]),\\s*(24[4-9]|25[0-5])/.test(color || '');
              const colored = color => color && color !== 'transparent' && color !== 'rgba(0, 0, 0, 0)' && !nearWhite(color);

              let meta = document.querySelector('meta[name=viewport]');
              if (!meta) {
                meta = document.createElement('meta');
                meta.name = 'viewport';
                document.head.appendChild(meta);
              }
              meta.content = 'width=device-width,initial-scale=1,maximum-scale=1';
              let style = document.getElementById(styleId);
              if (!style) {
                style = document.createElement('style');
                style.id = styleId;
                document.head.appendChild(style);
              }
              style.textContent = css;

              const detectRoute = headings => {
                const joined = headings.map(el => norm(el.innerText)).join(' | ');
                const path = norm(location.pathname);
                const routePaths = [
                  ['home','/dashboard/'], ['schedule','/schedule/'], ['grades','/progress/'],
                  ['tasks','/homework/'], ['materials','/library/'], ['announcements','/news/'],
                  ['rewards','/rewards/'], ['reviews','/feedback/'], ['payment','/payment/'],
                  ['profile','/settings/'], ['faq','/faq/'], ['contacts','/contacts/'],
                  ['tickets','/signal/'], ['complaints','/complaints/'], ['shop','/market/']
                ];
                for (const [key, part] of routePaths) if (path.includes(part)) return key;
                const tests = [
                  ['schedule', ['расписание','schedule','timetable']],
                  ['grades', ['оценки','grades','marks']],
                  ['tasks', ['домашние задания','homework','assignments']],
                  ['materials', ['учебные материалы','materials']],
                  ['announcements', ['объявления','announcements']],
                  ['rewards', ['ваши награды','rewards','awards']],
                  ['reviews', ['отзывы о студенте','reviews']],
                  ['payment', ['информация для оплаты','payment']],
                  ['profile', ['личный кабинет','profile','account']],
                  ['faq', ['часто задаваемые вопросы','faq']],
                  ['contacts', ['контакты','contacts']],
                  ['tickets', ['обращения','tickets']],
                  ['complaints', ['жалобы','complaints']],
                  ['shop', ['магазин','shop','store']]
                ];
                for (const [key, terms] of tests) {
                  if (terms.some(term => path.includes(norm(term)))) return key;
                }
                for (const [key, terms] of tests) {
                  if (terms.some(term => joined.split(' | ').some(title => title === norm(term)))) return key;
                }
                if (preferredRoute && preferredRoute !== 'more' && labels[preferredRoute]) return preferredRoute;
                return 'home';
              };

              const node = (tag, className, text) => {
                const element = document.createElement(tag);
                if (className) element.className = className;
                if (text !== undefined) element.textContent = text;
                return element;
              };
              const numberFrom = (scope, selector) => {
                const element = scope && scope.querySelector(selector);
                const match = norm(element && element.innerText).match(/\\d+/);
                return match ? Number(match[0]) : 0;
              };
              const findOriginalAction = terms => {
                const lowered = terms.map(norm);
                const candidates = [...document.querySelectorAll('a,button,[role=button],[role=menuitem],[routerlink]')];
                return candidates.find(element => {
                  if (element.closest('[data-topjournal-app-view]')) return false;
                  const value = norm((element.innerText || '') + ' ' + (element.getAttribute('href') || '')
                      + ' ' + (element.getAttribute('routerlink') || '') + ' ' + (element.title || ''));
                  return lowered.some(term => value.includes(term));
                });
              };
              const bindOriginalAction = (button, terms) => {
                const original = findOriginalAction(terms);
                button.addEventListener('click', () => { if (original) original.click(); });
              };
              const addSectionTitle = (view, title) => view.appendChild(node('div', 'tj-section-title', title));
              const addEmpty = (view, text) => view.appendChild(node('div', 'tj-empty', text));
              const addTextCards = (view, root, selectors, emptyText) => {
                const unique = new Set();
                const items = [];
                for (const element of root.querySelectorAll(selectors)) {
                  const text = (element.innerText || '').replace(/\\s+/g, ' ').trim();
                  const key = norm(text);
                  if (text.length < 6 || text.length > 360 || unique.has(key)
                      || key.includes('нет данных для отображения')) continue;
                  if (element.querySelectorAll(selectors).length > 0) continue;
                  unique.add(key);
                  items.push(text);
                  if (items.length >= 12) break;
                }
                if (!items.length) {
                  addEmpty(view, emptyText);
                  return;
                }
                const stack = node('div', 'tj-stack');
                for (const text of items) {
                  const card = node('div', 'tj-card');
                  const split = text.search(/[.!?]| {2,}/);
                  const title = split > 5 ? text.slice(0, split + 1) : text.slice(0, 72);
                  card.appendChild(node('b', '', title));
                  if (text.length > title.length) card.appendChild(node('span', '', text.slice(title.length).trim()));
                  stack.appendChild(card);
                }
                view.appendChild(stack);
              };
              const prepareAppShell = root => {
                for (const chrome of document.querySelectorAll('top-pane,app-story,.top-panel')) {
                  chrome.dataset.topjournalHiddenHeader = '1';
                  chrome.style.setProperty('display', 'none', 'important');
                }
                let branch = root;
                while (branch && branch.parentElement && branch.parentElement !== document.body) {
                  const parent = branch.parentElement;
                  parent.dataset.topjournalAdjustedParent = '1';
                  parent.style.setProperty('width', '100%', 'important');
                  parent.style.setProperty('max-width', '100%', 'important');
                  parent.style.setProperty('min-width', '0', 'important');
                  parent.style.setProperty('margin', '0', 'important');
                  parent.style.setProperty('padding', '0', 'important');
                  parent.style.setProperty('background', '#fff', 'important');
                  branch = parent;
                  if (parent.tagName === 'MYSTAT') break;
                }
              };
              const renderAppView = (route, root) => {
                if (!['home','schedule','grades','tasks'].includes(route)) return false;
                prepareAppShell(root);
                const existing = document.querySelector('[data-topjournal-app-view]');
                if (existing) existing.remove();
                root.dataset.topjournalOriginalRoot = '1';
                root.style.setProperty('display', 'none', 'important');
                const view = node('main');
                view.dataset.topjournalAppView = route;
                view.dataset.topjournalSourceSignature = String((root.textContent || '').length)
                    + ':' + String(root.querySelectorAll('*').length);
                const kicker = route === 'home' ? 'Сегодня' : 'Учебный процесс';
                view.appendChild(node('div', 'tj-view-kicker', kicker));

                if (route === 'home') {
                  const source = root.querySelector('.part-homeworks') || root;
                  const hero = node('section', 'tj-hero');
                  const head = node('div', 'tj-hero-head');
                  head.appendChild(node('h2', '', 'Домашние задания'));
                  const open = node('button', 'tj-action', 'Открыть');
                  open.type = 'button';
                  bindOriginalAction(open, ['homework','домашние задания']);
                  head.appendChild(open);
                  hero.appendChild(head);
                  const total = numberFrom(source, '.all-count');
                  const totalRow = node('div', 'tj-hero-total');
                  totalRow.appendChild(document.createTextNode(String(total)));
                  totalRow.appendChild(node('span', '', 'всего заданий'));
                  hero.appendChild(totalRow);
                  const metrics = node('div', 'tj-metrics');
                  const values = [
                    ['Текущие', numberFrom(source, '.open')],
                    ['Проверено', numberFrom(source, '.done')],
                    ['На проверке', numberFrom(source, '.inspection')],
                    ['Просрочено', numberFrom(source, '.lose')]
                  ];
                  for (const [label, value] of values) {
                    const metric = node('div', 'tj-metric');
                    metric.appendChild(node('span', '', label));
                    metric.appendChild(node('b', '', String(value)));
                    metrics.appendChild(metric);
                  }
                  hero.appendChild(metrics);
                  view.appendChild(hero);

                  const cleanText = element => (element && element.innerText || '').replace(/\\s+/g, ' ').trim();
                  const statCard = (label, value, note, dark, wide) => {
                    const card = node('div', 'tj-stat-card' + (dark ? ' tj-dark' : '') + (wide ? ' tj-wide' : ''));
                    card.appendChild(node('div', 'tj-stat-label', label));
                    card.appendChild(node('div', 'tj-stat-value', value || '—'));
                    if (note) card.appendChild(node('div', 'tj-stat-note', note));
                    return card;
                  };
                  const listCard = entries => {
                    const card = node('div', 'tj-list-card');
                    entries.forEach((entry, index) => {
                      const row = node('div', 'tj-list-row');
                      row.appendChild(node('div', 'tj-rank', entry.rank || String(index + 1)));
                      const copy = node('div', 'tj-list-copy');
                      copy.appendChild(node('b', '', entry.title));
                      if (entry.subtitle) copy.appendChild(node('span', '', entry.subtitle));
                      row.appendChild(copy);
                      if (entry.pill) row.appendChild(node('div', 'tj-pill', entry.pill));
                      card.appendChild(row);
                    });
                    return card;
                  };

                  addSectionTitle(view, 'Учебный прогресс');
                  const progressBento = node('div', 'tj-bento');
                  const progressValues = [...root.querySelectorAll('.part-progress .middle-count')]
                      .map(cleanText).filter(Boolean);
                  const attendanceValues = [...root.querySelectorAll('.attendance-content .middle-count,.attendance-content [class*="count"]')]
                      .map(cleanText).filter(Boolean);
                  const testSource = root.querySelector('.part-testing');
                  const testsTotal = numberFrom(testSource, '.all-count');
                  const testsDone = numberFrom(testSource, '.done');
                  progressBento.appendChild(statCard('Средний результат', progressValues[0] || '—', 'По текущему периоду', true, false));
                  progressBento.appendChild(statCard('Посещаемость', attendanceValues[0] || progressValues[1] || '—', 'С учётом последних занятий', false, false));
                  progressBento.appendChild(statCard('Тесты', String(testsDone) + ' / ' + String(testsTotal),
                      'Проверено и выполнено', false, true));
                  view.appendChild(progressBento);

                  addSectionTitle(view, 'Назначенные экзамены');
                  const exams = [...root.querySelectorAll('.exams-content li')].map(item => ({
                    title: cleanText(item.querySelector('.exam-spec')) || cleanText(item),
                    subtitle: 'Предстоящий экзамен',
                    pill: cleanText(item.querySelector('.exam-date')) || '',
                    rank: 'E'
                  })).filter(item => item.title).slice(0, 6);
                  if (exams.length) view.appendChild(listCard(exams));
                  else addEmpty(view, 'Назначенных экзаменов пока нет. Когда появится дата, она будет показана здесь.');

                  addSectionTitle(view, 'Рейтинг');
                  const groupPosition = cleanText(root.querySelector('.rating-group .rating-position'));
                  const streamPosition = cleanText(root.querySelector('.rating-stream .rating-position'));
                  const ratingBento = node('div', 'tj-bento');
                  ratingBento.appendChild(statCard('Место в группе', groupPosition || '—', 'Текущая позиция', false, false));
                  ratingBento.appendChild(statCard('Место в потоке', streamPosition || '—', 'Среди всех групп', true, false));
                  view.appendChild(ratingBento);
                  const leaders = [...root.querySelectorAll('.history-item.leader > li')].map((item, index) => {
                    const parts = [...item.querySelectorAll('.full-name-block > span')]
                        .map(cleanText).filter(Boolean);
                    return {
                      rank: parts[0] || String(index + 1),
                      title: parts[1] || '',
                      subtitle: 'Топ студентов',
                      pill: cleanText(item.querySelector('.point span'))
                    };
                  }).filter(item => item.title).slice(0, 5);
                  if (leaders.length) {
                    addSectionTitle(view, 'Топ студентов');
                    view.appendChild(listCard(leaders));
                  }

                  const activity = [...root.querySelectorAll('.part-history .history-item > div')].map(wrapper => {
                    const item = wrapper.querySelector('li') || wrapper;
                    return {
                      title: cleanText(item.querySelector('.name-achive > span:first-child'))
                          || cleanText(item.querySelector('.name-achive')),
                      date: cleanText(item.querySelector('.history-date')),
                      amount: cleanText(item.querySelector('.point span'))
                    };
                  }).filter(item => item.title).slice(0, 5);
                  if (activity.length) {
                    addSectionTitle(view, 'Последняя активность');
                    view.appendChild(listCard(activity.map((item, index) => ({
                      rank: String(index + 1), title: item.title,
                      subtitle: item.date || 'Недавнее событие', pill: item.amount
                    }))));
                  }

                  addSectionTitle(view, 'Быстрый доступ');
                  const grid = node('div', 'tj-grid');
                  const shortcuts = [
                    ['Материалы','Уроки, тесты и библиотека',['library','учебные материалы']],
                    ['Объявления','Новости учебного центра',['news','объявления']],
                    ['Награды','Достижения и бонусы',['rewards','награды']],
                    ['Оплата','История и реквизиты',['payment','оплата']]
                  ];
                  for (const [title, subtitle, terms] of shortcuts) {
                    const tile = node('button', 'tj-tile');
                    tile.type = 'button';
                    tile.appendChild(node('b', '', title));
                    tile.appendChild(node('span', '', subtitle));
                    bindOriginalAction(tile, terms);
                    grid.appendChild(tile);
                  }
                  view.appendChild(grid);
                } else if (route === 'schedule') {
                  view.appendChild(node('div', 'tj-view-title', 'Ближайшие занятия'));
                  addTextCards(view, root,
                      '[class*="lesson"],[class*="schedule"][class*="item"],article,tbody tr',
                      'На выбранную дату занятий нет. Расписание появится здесь после публикации.');
                } else if (route === 'grades') {
                  view.appendChild(node('div', 'tj-view-title', 'Успеваемость'));
                  addTextCards(view, root,
                      '[class*="mark"],[class*="grade"],[class*="progress"][class*="item"],tbody tr,article',
                      'Оценок пока нет. Новые результаты появятся после проверки преподавателем.');
                } else {
                  view.appendChild(node('div', 'tj-view-title', 'На сегодня'));
                  addTextCards(view, root,
                      '[class*="homework"][class*="item"],[class*="task"][class*="item"],article,tbody tr',
                      'Активных домашних заданий нет. Можно спокойно готовиться к следующим занятиям.');
                }
                root.parentNode.insertBefore(view, root);
                return true;
              };

              const tune = () => {
                if (!document.body) return;
                document.body.classList.add('top-journal-app');
                document.body.dataset.tjEnhanced = '4';
                const passwordField = document.querySelector('input[type=password]');
                const authPath = norm(location.pathname).includes('/auth/');
                const loginPage = !!passwordField || authPath;
                const signed = !loginPage && !!document.querySelector('mystat');
                if (loginPage) {
                  document.body.dataset.tjRoute = 'login';
                  delete document.body.dataset.tjSigned;
                  let loginRoot = document.querySelector('#cover') || passwordField.closest('form') || passwordField.parentElement;
                  let node = loginRoot;
                  while (node && node.parentElement && node.parentElement !== document.body) {
                    const parentRect = node.parentElement.getBoundingClientRect();
                    if (parentRect.width < innerWidth * .48 || parentRect.height > innerHeight * .92) break;
                    node = node.parentElement;
                    loginRoot = node;
                  }
                  if (loginRoot) {
                    loginRoot.dataset.topjournalContentRoot = '1';
                    loginRoot.style.setProperty('background', '#ffffff', 'important');
                    loginRoot.style.setProperty('border-color', '#0a0a0a', 'important');
                  }
                  return;
                }
                if (!signed) return;
                document.body.dataset.tjSigned = '1';

                for (const el of document.querySelectorAll('body *')) {
                  if (!visible(el) || el.closest('dialog')) continue;
                  const rect = el.getBoundingClientRect();
                  if (rect.left <= 3 && rect.width >= 48 && rect.width <= 150 && rect.height > innerHeight * .72
                      && el.tagName !== 'BODY' && el.tagName !== 'HTML') {
                    el.dataset.topjournalHiddenSidebar = '1';
                    el.style.setProperty('display', 'none', 'important');
                  }
                }

                const promoCandidates = [...document.querySelectorAll('body *')].filter(el => {
                  if (!visible(el)) return false;
                  const rect = el.getBoundingClientRect();
                  return rect.top >= 0 && rect.top < 340 && rect.height > 65 && rect.height < 280
                      && rect.width > innerWidth * .62 && el.querySelectorAll('img').length >= 4;
                }).sort((a,b) => {
                  const ar = a.getBoundingClientRect(), br = b.getBoundingClientRect();
                  return ar.width * ar.height - br.width * br.height;
                });
                if (promoCandidates[0]) {
                  promoCandidates[0].dataset.topjournalHiddenPromo = '1';
                  promoCandidates[0].style.setProperty('display', 'none', 'important');
                }

                const headings = [...document.querySelectorAll('h1,h2,h3,h4')].filter(visible);
                const route = detectRoute(headings);
                document.body.dataset.tjRoute = route;
                const currentAppView = document.querySelector('[data-topjournal-app-view]');
                const hiddenOriginal = document.querySelector('[data-topjournal-original-root]');
                const hiddenSignature = hiddenOriginal
                    ? String((hiddenOriginal.textContent || '').length) + ':' + String(hiddenOriginal.querySelectorAll('*').length)
                    : '';
                if (currentAppView && currentAppView.dataset.topjournalAppView === route
                    && currentAppView.dataset.topjournalSourceSignature === hiddenSignature) return;
                if (currentAppView) {
                  currentAppView.remove();
                  for (const original of document.querySelectorAll('[data-topjournal-original-root]')) {
                    original.style.removeProperty('display');
                    delete original.dataset.topjournalOriginalRoot;
                  }
                }
                const wanted = norm(labels[route]);
                let pageHeading = headings.find(el => {
                  const title = norm(el.innerText);
                  return title === wanted || title.includes(wanted);
                });

                const knownSelectors = {
                  home: '.homepage-wrapper',
                  schedule: 'schedule-component,.schedule-component,[class*="schedule-wrapper"]',
                  grades: 'progress-component,.progress-component,[class*="progress-wrapper"]',
                  tasks: 'homework-component,.homework-component,[class*="homework-wrapper"]',
                  materials: 'library-component,.library-component,[class*="library-wrapper"]',
                  announcements: 'news-component,.news-component,[class*="news-wrapper"]'
                };
                let root = null;
                if (knownSelectors[route]) {
                  root = [...document.querySelectorAll(knownSelectors[route])].find(el => {
                    if (!visible(el)) return false;
                    const rect = el.getBoundingClientRect();
                    return rect.width > innerWidth * .55 && rect.height > 160;
                  }) || null;
                }
                if (!root && pageHeading) {
                  let node = pageHeading.parentElement;
                  let best = node;
                  while (node && node !== document.body) {
                    const rect = node.getBoundingClientRect();
                    if (rect.width > innerWidth * .58 && rect.height > 170) best = node;
                    if (rect.width > innerWidth * .58 && rect.height > 230
                        && colored(getComputedStyle(node).backgroundColor)) {
                      best = node;
                      break;
                    }
                    node = node.parentElement;
                  }
                  root = best;
                }
                if (!root) {
                  const coloredRoots = [...document.querySelectorAll('main,section,div')].filter(el => {
                    if (!visible(el)) return false;
                    const rect = el.getBoundingClientRect();
                    return rect.width > innerWidth * .62 && rect.height > 260
                        && colored(getComputedStyle(el).backgroundColor);
                  }).sort((a,b) => {
                    const ar = a.getBoundingClientRect(), br = b.getBoundingClientRect();
                    return br.width * br.height - ar.width * ar.height;
                  });
                  root = coloredRoots[0] || document.querySelector('main,[role=main],[class*=main-content],[class*=page-content]');
                }
                if (!root || root === document.body || root === document.documentElement) return;
                if (renderAppView(route, root)) return;
                root.dataset.topjournalContentRoot = '1';
                root.style.setProperty('background', '#ffffff', 'important');
                root.style.setProperty('background-color', '#ffffff', 'important');
                root.style.setProperty('width', '100%', 'important');
                root.style.setProperty('max-width', '100%', 'important');
                root.style.setProperty('min-width', '0', 'important');
                root.style.setProperty('margin-left', '0', 'important');
                root.style.setProperty('margin-right', '0', 'important');

                for (const heading of headings) {
                  const title = norm(heading.innerText);
                  if (title === wanted || title === wanted.toUpperCase()) {
                    heading.dataset.topjournalPageHeading = '1';
                  }
                }

                const rootRect = root.getBoundingClientRect();
                const headerCandidates = [...document.querySelectorAll('header,section,div')].filter(el => {
                  if (!visible(el) || el === root || el.contains(root) || root.contains(el)) return false;
                  const text = norm(el.innerText);
                  const rect = el.getBoundingClientRect();
                  return text.includes('группа:') && rect.width > innerWidth * .55 && rect.height > 80 && rect.height < 760
                      && rect.bottom <= rootRect.top + 50;
                }).sort((a,b) => {
                  const ar = a.getBoundingClientRect(), br = b.getBoundingClientRect();
                  return br.width * br.height - ar.width * ar.height;
                });
                if (headerCandidates[0]) {
                  headerCandidates[0].dataset.topjournalHiddenHeader = '1';
                  headerCandidates[0].style.setProperty('display', 'none', 'important');
                }

                if (root.parentElement && root.parentElement !== document.body) {
                  root.parentElement.dataset.topjournalAdjustedParent = '1';
                  root.parentElement.style.setProperty('padding-top', '0', 'important');
                  root.parentElement.style.setProperty('margin-top', '0', 'important');
                  root.parentElement.style.setProperty('min-height', '0', 'important');
                }
                let contentBranch = root;
                while (contentBranch.parentElement && contentBranch.parentElement !== document.body
                    && contentBranch.parentElement.tagName !== 'MYSTAT') {
                  const parent = contentBranch.parentElement;
                  parent.dataset.topjournalAdjustedParent = '1';
                  parent.style.setProperty('padding-top', '0', 'important');
                  parent.style.setProperty('margin-top', '0', 'important');
                  parent.style.setProperty('min-height', '0', 'important');
                  for (let sibling = contentBranch.previousElementSibling; sibling; sibling = sibling.previousElementSibling) {
                    if (!visible(sibling)) continue;
                    const rect = sibling.getBoundingClientRect();
                    const text = norm(sibling.innerText);
                    if (rect.height > 48 && rect.width > innerWidth * .55
                        && (text.length < 80 || text.includes('группа:'))) {
                      sibling.dataset.topjournalHiddenBeforeContent = '1';
                      sibling.style.setProperty('display', 'none', 'important');
                    }
                  }
                  contentBranch = parent;
                }
                const shiftedRootRect = root.getBoundingClientRect();
                const spacerCandidates = [...document.querySelectorAll('header,section,div')].filter(el => {
                  if (!visible(el) || el === root || el.contains(root) || root.contains(el)) return false;
                  const rect = el.getBoundingClientRect();
                  const text = norm(el.innerText);
                  return rect.width > innerWidth * .72 && rect.height > 55 && rect.height < 440
                      && rect.bottom <= shiftedRootRect.top + 35 && text.length < 100
                      && !el.querySelector('input,select,textarea');
                }).sort((a,b) => {
                  const ar = a.getBoundingClientRect(), br = b.getBoundingClientRect();
                  return br.width * br.height - ar.width * ar.height;
                });
                for (const spacer of spacerCandidates.slice(0, 2)) {
                  spacer.dataset.topjournalHiddenSpacer = '1';
                  spacer.style.setProperty('display', 'none', 'important');
                }

                for (const el of root.querySelectorAll('section,div,main')) {
                  if (!visible(el) || el.hasAttribute('data-topjournal-native-summary')) continue;
                  const channels = (getComputedStyle(el).backgroundColor.match(/\\d+/g) || []).map(Number);
                  if (channels.length >= 3 && channels[0] < 205 && channels[1] > 145 && channels[2] > 130
                      && channels[1] > channels[0] + 10) {
                    el.dataset.topjournalTealSurface = '1';
                  }
                }

                for (const el of [...document.querySelectorAll('div,button,a')]) {
                  const text = norm(el.innerText);
                  if ((text === 'нужна помощь с домашним заданием?' || text.includes('нужна помощь с домашним заданием?'))
                      && visible(el) && el.getBoundingClientRect().height < 180) {
                    const target = el.closest('div') || el;
                    target.dataset.topjournalHiddenHelp = '1';
                    target.style.setProperty('display', 'none', 'important');
                    break;
                  }
                }

                for (const old of root.querySelectorAll('[data-topjournal-card],[data-topjournal-toolbar],[data-topjournal-day],'
                    + '[data-topjournal-empty],[data-topjournal-table-wrap]')) {
                  old.removeAttribute('data-topjournal-card');
                  old.removeAttribute('data-topjournal-toolbar');
                  old.removeAttribute('data-topjournal-day');
                  old.removeAttribute('data-topjournal-selected');
                  old.removeAttribute('data-topjournal-empty');
                  old.removeAttribute('data-topjournal-table-wrap');
                }

                for (const control of root.querySelectorAll('select,input[type=search],input[type=date]')) {
                  let toolbar = control.parentElement;
                  while (toolbar && toolbar !== root) {
                    const rect = toolbar.getBoundingClientRect();
                    if (rect.width > rootRect.width * .55 && rect.height > 44 && rect.height < 210) break;
                    toolbar = toolbar.parentElement;
                  }
                  if (toolbar && toolbar !== root) toolbar.dataset.topjournalToolbar = '1';
                }

                const candidates = [...root.querySelectorAll('article,form,section,div,li')];
                for (const el of candidates) {
                  if (!visible(el) || el === root || el.hasAttribute('data-topjournal-toolbar')
                      || el.closest('[data-topjournal-toolbar]') || el.querySelector('[data-topjournal-page-heading]')) continue;
                  const rect = el.getBoundingClientRect();
                  if (rect.width < rootRect.width * .56 || rect.height < 64 || rect.height > 920) continue;
                  const className = String(el.className || '').toLowerCase();
                  const background = getComputedStyle(el).backgroundColor;
                  const cardLike = nearWhite(background) || /card|panel|widget|item|box/.test(className);
                  if (!cardLike || el.closest('[data-topjournal-card]')) continue;
                  if (!norm(el.innerText) && !el.querySelector('img,table,input,select,textarea')) continue;
                  el.dataset.topjournalCard = '1';
                }

                for (const empty of root.querySelectorAll('div,p,span')) {
                  const text = norm(empty.innerText);
                  if (text === 'нет данных для отображения' || text.startsWith('у вас пока нет ')) {
                    empty.dataset.topjournalEmpty = '1';
                  }
                }

                for (const table of root.querySelectorAll('table')) {
                  const parent = table.parentElement;
                  if (parent && parent !== root) parent.dataset.topjournalTableWrap = '1';
                }

                if (route === 'home' && !root.querySelector('[data-topjournal-native-summary]')) {
                  const summaryCandidates = [...root.querySelectorAll('section,article,div')].filter(el => {
                    if (!visible(el) || el === root) return false;
                    const text = norm(el.innerText);
                    const rect = el.getBoundingClientRect();
                    return text.includes('все задания') && text.includes('текущие') && text.includes('просрочено')
                        && rect.width > rootRect.width * .55 && rect.height > 110 && rect.height < 950;
                  }).sort((a,b) => {
                    const ar = a.getBoundingClientRect(), br = b.getBoundingClientRect();
                    return ar.width * ar.height - br.width * br.height;
                  });
                  const originalSummary = root.querySelector('.part-homeworks') || summaryCandidates[0];
                  if (originalSummary) {
                    const summaryText = norm(originalSummary.innerText);
                    const count = label => {
                      const after = summaryText.match(new RegExp('(\\d+)\\s*' + label));
                      if (after) return Number(after[1]);
                      const before = summaryText.match(new RegExp(label + '\\s*(\\d+)'));
                      return before ? Number(before[1]) : 0;
                    };
                    const directNumber = selector => {
                      const node = originalSummary.querySelector(selector);
                      const match = norm(node && node.innerText).match(/\\d+/);
                      return match ? Number(match[0]) : null;
                    };
                    const total = directNumber('.all-count') ?? count('все задания');
                    const current = directNumber('.open') ?? count('текущие');
                    const checked = directNumber('.done') ?? count('проверено');
                    const review = directNumber('.inspection') ?? count('на проверке');
                    const overdue = directNumber('.lose') ?? count('просрочено');
                    const details = [...originalSummary.querySelectorAll('a,button')].find(el =>
                        norm(el.innerText).includes('подробнее'));
                    const summary = document.createElement('section');
                    summary.dataset.topjournalNativeSummary = '1';
                    summary.innerHTML = '<div class="tj-summary-head"><div><div class="tj-eyebrow">На сегодня</div>'
                        + '<h2>Домашние задания</h2></div><button class="tj-open" type="button">Открыть</button></div>'
                        + '<div class="tj-summary-total"><strong>' + total + '</strong><span>всего заданий</span></div>'
                        + '<div class="tj-summary-grid"><div class="tj-stat"><span>Текущие</span><b>' + current + '</b></div>'
                        + '<div class="tj-stat"><span>Проверено</span><b>' + checked + '</b></div>'
                        + '<div class="tj-stat"><span>На проверке</span><b>' + review + '</b></div>'
                        + '<div class="tj-stat"><span>Просрочено</span><b>' + overdue + '</b></div></div>';
                    summary.querySelector('.tj-open').addEventListener('click', () => {
                      if (details) details.click();
                    });
                    originalSummary.dataset.topjournalHiddenOriginal = '1';
                    originalSummary.style.setProperty('display', 'none', 'important');
                    root.insertBefore(summary, root.firstChild);
                  }
                }

                if (route === 'schedule') {
                  for (const day of root.querySelectorAll('div,td,button,span')) {
                    const value = norm(day.innerText);
                    if (!/^(?:[1-9]|[12]\\d|3[01])$/.test(value) || !visible(day)) continue;
                    const rect = day.getBoundingClientRect();
                    if (rect.width < 30 || rect.width > 155 || rect.height < 30 || rect.height > 155) continue;
                    const background = getComputedStyle(day).backgroundColor;
                    day.dataset.topjournalDay = '1';
                    if (colored(background)) day.dataset.topjournalSelected = '1';
                  }
                }
              };

              tune();
              if (window.__topJournalObserver) window.__topJournalObserver.disconnect();
              let timer;
              window.__topJournalObserver = new MutationObserver(() => {
                clearTimeout(timer);
                timer = setTimeout(tune, 160);
              });
              window.__topJournalObserver.observe(document.documentElement, {childList:true, subtree:true});
              return true;
            })()
            """;

    private void applyMobileStyle() {
        if (webView == null || !signedIn || preferences.getBoolean("original_mode", false)) return;
        String script = APP_JS
                .replace("__CSS__", JSONObject.quote(APP_CSS))
                .replace("__ROUTE__", JSONObject.quote(requestedRoute));
        webView.evaluateJavascript(script, null);
    }

    private void removeMobileStyle() {
        if (webView == null) return;
        String script = "(function(){document.getElementById('top-journal-app-style')?.remove();" +
                "if(window.__topJournalObserver)window.__topJournalObserver.disconnect();" +
                "for(const el of document.querySelectorAll('[data-topjournal-hidden-sidebar],[data-topjournal-hidden-promo]," +
                "[data-topjournal-hidden-header],[data-topjournal-hidden-help],[data-topjournal-hidden-spacer]," +
                "[data-topjournal-hidden-before-content]," +
                "[data-topjournal-hidden-original]')){el.style.removeProperty('display');}" +
                "for(const el of document.querySelectorAll('[data-topjournal-app-view]'))el.remove();" +
                "for(const el of document.querySelectorAll('[data-topjournal-original-root]'))el.style.removeProperty('display');" +
                "for(const el of document.querySelectorAll('[data-topjournal-native-summary]'))el.remove();" +
                "for(const el of document.querySelectorAll('[data-topjournal-content-root]')){" +
                "for(const prop of ['background','background-color','border-color','width','max-width','min-width','margin-left','margin-right'])" +
                "el.style.removeProperty(prop);}" +
                "for(const el of document.querySelectorAll('[data-topjournal-adjusted-parent]')){" +
                "for(const prop of ['padding-top','margin-top','min-height'])el.style.removeProperty(prop);}" +
                "for(const el of document.querySelectorAll('[data-topjournal-original-text]')){" +
                "el.textContent=el.dataset.topjournalOriginalText;delete el.dataset.topjournalOriginalText;}" +
                "for(const el of document.querySelectorAll('[data-topjournal-synthetic-heading]'))el.remove();" +
                "for(const el of document.querySelectorAll('[data-topjournal-content-root],[data-topjournal-card],[data-topjournal-toolbar]," +
                "[data-topjournal-page-heading],[data-topjournal-day],[data-topjournal-empty],[data-topjournal-table-wrap]," +
                "[data-topjournal-teal-surface],[data-topjournal-adjusted-parent],[data-topjournal-hidden-sidebar]," +
                "[data-topjournal-hidden-promo],[data-topjournal-hidden-header],[data-topjournal-hidden-help]," +
                "[data-topjournal-hidden-spacer],[data-topjournal-hidden-before-content],[data-topjournal-hidden-original]," +
                "[data-topjournal-original-root]')){" +
                "for(const name of [...el.getAttributeNames()])if(name.startsWith('data-topjournal-'))el.removeAttribute(name);}" +
                "document.body?.classList.remove('top-journal-app');if(document.body){delete document.body.dataset.tjRoute;" +
                "delete document.body.dataset.tjEnhanced;delete document.body.dataset.tjSigned;}return true;})()";
        webView.evaluateJavascript(script, null);
    }

    private void scanVisibleCounters() {
        if (webView == null || !preferences.getBoolean("legacy_counter_notifications", false)) return;
        String script = "(function(){let total=0;const seen=new Set();" +
                "for(const el of document.querySelectorAll('[class*=badge],[class*=counter],[class*=count]')){" +
                "const raw=(el.innerText||'').trim();if(!/^\\d{1,3}$/.test(raw))continue;" +
                "const r=el.getBoundingClientRect();const key=(el.className||'')+'|'+raw+'|'+Math.round(r.top);if(seen.has(key))continue;seen.add(key);" +
                "total+=parseInt(raw,10);}return Math.min(total,9999);})()";
        webView.evaluateJavascript(script, value -> {
            try {
                int current = Integer.parseInt(value == null ? "0" : value);
                if (current <= 0) return;
                int previous = preferences.getInt("last_counter_total", -1);
                preferences.edit().putInt("last_counter_total", current).apply();
                if (previous >= 0 && current > previous) postEventNotification(current - previous);
            } catch (NumberFormatException ignored) {
                // Dynamic pages may return no numeric value while switching routes.
            }
        });
    }

    private void createNotificationChannels() {
        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        NotificationChannel reminders = new NotificationChannel(
                CHANNEL_REMINDERS, "Напоминания", NotificationManager.IMPORTANCE_DEFAULT);
        reminders.setDescription("Напоминания проверить задания и расписание");
        manager.createNotificationChannel(reminders);

        NotificationChannel events = new NotificationChannel(
                CHANNEL_EVENTS, "Изменения в журнале", NotificationManager.IMPORTANCE_DEFAULT);
        events.setDescription("Изменения счётчиков при обновлении журнала");
        manager.createNotificationChannel(events);

        NotificationChannel grades = new NotificationChannel(
                JournalNotifications.CHANNEL_GRADES, "Новые оценки", NotificationManager.IMPORTANCE_DEFAULT);
        grades.setDescription("Уведомления о новых оценках в дневнике");
        manager.createNotificationChannel(grades);
    }

    private void requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, NOTIFICATION_PERMISSION_REQUEST);
        }
    }

    private void postEventNotification(int difference) {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) return;
        Intent intent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 11, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        android.app.Notification notification = new android.app.Notification.Builder(this, CHANNEL_EVENTS)
                .setSmallIcon(R.drawable.ic_bell)
                .setContentTitle("В журнале есть изменения")
                .setContentText("Новых событий по счётчикам: " + difference)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .build();
        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        manager.notify(41, notification);
    }

    private final class SkeletonView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Matrix shaderMatrix = new Matrix();
        private final float radius;
        private final long startDelay;
        private LinearGradient gradient;
        private ValueAnimator animator;
        private float phase;

        SkeletonView(float radiusDp, long startDelayMs) {
            super(MainActivity.this);
            radius = dp(radiusDp);
            startDelay = startDelayMs;
            setContentDescription("Загрузка данных");
            setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        }

        @Override
        protected void onMeasure(int widthSpec, int heightSpec) {
            setMeasuredDimension(resolveSize(getSuggestedMinimumWidth(), widthSpec),
                    resolveSize(Math.max(dp(20), getSuggestedMinimumHeight()), heightSpec));
        }

        @Override
        protected void onWindowVisibilityChanged(int visibility) {
            super.onWindowVisibilityChanged(visibility);
            if (animator != null) { if (visibility == VISIBLE) animator.resume(); else animator.pause(); }
        }

        @Override
        protected void onVisibilityChanged(View changedView, int visibility) {
            super.onVisibilityChanged(changedView, visibility);
            if (animator != null) { if (isShown()) animator.resume(); else animator.pause(); }
        }

        @Override
        protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
            super.onSizeChanged(width, height, oldWidth, oldHeight);
            if (width <= 0) return;
            gradient = new LinearGradient(
                    -width, 0, width, 0,
                    new int[]{Color.rgb(222, 222, 226), Color.rgb(248, 248, 250), Color.rgb(222, 222, 226)},
                    new float[]{0f, .5f, 1f}, Shader.TileMode.CLAMP
            );
            paint.setShader(gradient);
        }

        @Override
        protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            if (animator != null) return;
            animator = ValueAnimator.ofFloat(0f, 1f);
            animator.setDuration(1150);
            animator.setStartDelay(startDelay);
            animator.setRepeatCount(ValueAnimator.INFINITE);
            animator.addUpdateListener(value -> {
                phase = (float) value.getAnimatedValue();
                invalidate();
            });
            animator.start();
            if (!isShown()) animator.pause();
        }

        @Override
        protected void onDetachedFromWindow() {
            if (animator != null) {
                animator.cancel();
                animator = null;
            }
            super.onDetachedFromWindow();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            if (gradient == null) return;
            shaderMatrix.reset();
            shaderMatrix.setTranslate(getWidth() * 2f * phase, 0);
            gradient.setLocalMatrix(shaderMatrix);
            canvas.drawRoundRect(0, 0, getWidth(), getHeight(), radius, radius, paint);
        }
    }
}
