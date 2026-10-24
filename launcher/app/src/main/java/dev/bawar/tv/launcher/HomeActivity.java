package dev.bawar.tv.launcher;

import android.animation.ValueAnimator;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.media.tv.TvContract;
import android.media.tv.TvInputInfo;
import android.media.tv.TvInputManager;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.provider.Settings;
import android.text.format.DateFormat;
import android.util.LruCache;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.OvershootInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import java.io.InputStream;
import java.text.Collator;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Local-only TV home. No accessibility service, network polling, or boot receiver. */
public class HomeActivity extends Activity {
    private final Handler handler = new Handler();
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final List<App> dock = new ArrayList<>(), apps = new ArrayList<>();
    private final TextView[] pills = new TextView[3];
    private final LruCache<String, Bitmap> banners = new LruCache<String, Bitmap>(16 * 1024) {
        @Override protected int sizeOf(String key, Bitmap bitmap) { return bitmap.getAllocationByteCount() / 1024; }
    };
    private FrameLayout root;
    private Backdrop backdrop;
    private FocusGlass glass;
    private RecyclerView grid;
    private GridLayoutManager layout;
    private Adapter adapter;
    private TextView clock, date, hero;
    private SharedPreferences prefs;
    private Typeface font;
    private int columns, selected = 0, topIndex = 0;
    private boolean top = false, active = false;
    private int heroHeight;
    private ValueAnimator focusAnimation;
    private final RectF focusRect = new RectF();
    private Uri wallpaper;
    private final Runnable ticker = new Runnable() {
        @Override public void run() {
            clock.setText(DateFormat.getTimeFormat(HomeActivity.this).format(Calendar.getInstance().getTime()));
            date.setText(DateFormat.format("EEEE, d MMMM", Calendar.getInstance()));
            if (active) handler.postDelayed(this, 30000);
        }
    };

    private static class App {
        final String id, name;
        final Intent intent;
        final ResolveInfo info;
        int accent = 0xff53769b;
        App(String id, String name, Intent intent, ResolveInfo info) {
            this.id = id; this.name = name; this.intent = intent; this.info = info;
        }
    }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().getDecorView().setSystemUiVisibility(5894);
        prefs = getSharedPreferences("quiet-tv", MODE_PRIVATE);
        columns = Math.max(4, Math.min(7, getResources().getInteger(R.integer.columns)));
        selected = prefs.getInt("selection", 0);
        topIndex = prefs.getInt("topIndex", 0);
        try { font = Typeface.createFromAsset(getAssets(), "Outfit.ttf"); }
        catch (RuntimeException ignored) { font = Typeface.create("sans-serif-light", Typeface.NORMAL); }
        createUi();
        String uri = prefs.getString("wallpaper", null);
        if (uri != null) loadWallpaper(Uri.parse(uri));
    }

    private int dp(float n) { return Math.round(n * getResources().getDisplayMetrics().density); }
    private TextView text(String value, int size, int color) {
        TextView t = new TextView(this); t.setText(value); t.setTextSize(size); t.setTextColor(color);
        t.setTypeface(font); t.setFontFeatureSettings("tnum"); t.setIncludeFontPadding(false);
        return t;
    }
    private GradientDrawable rounded(int color, int radius) {
        GradientDrawable bg = new GradientDrawable(); bg.setColor(color); bg.setCornerRadius(dp(radius)); return bg;
    }
    private void createUi() {
        root = new FrameLayout(this);
        root.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        backdrop = new Backdrop(); root.addView(backdrop, new FrameLayout.LayoutParams(-1, -1));
        LinearLayout header = new LinearLayout(this); header.setOrientation(LinearLayout.VERTICAL);
        header.addView(text(getString(R.string.greeting), 14, 0xffa9adb5));
        clock = text("", 37, Color.WHITE); header.addView(clock);
        date = text("", 13, 0xffa9adb5); header.addView(date);
        FrameLayout.LayoutParams hp = new FrameLayout.LayoutParams(dp(330), dp(100));
        hp.leftMargin = dp(42); hp.topMargin = dp(25); root.addView(header, hp);

        LinearLayout bar = new LinearLayout(this); bar.setGravity(Gravity.CENTER_VERTICAL);
        String[] names = { "Inputs", "Wi-Fi", "Settings" };
        for (int i = 0; i < 3; i++) {
            final int index = i;
            pills[i] = text(names[i], 14, Color.WHITE); pills[i].setGravity(Gravity.CENTER);
            pills[i].setBackground(rounded(0x9925282d, 24));
            LinearLayout.LayoutParams pp = new LinearLayout.LayoutParams(dp(i == 1 ? 118 : 90), dp(42));
            pp.leftMargin = dp(10); bar.addView(pills[i], pp);
            pills[i].setOnClickListener(v -> { top = true; topIndex = index; moveFocus(); activate(); });
        }
        FrameLayout.LayoutParams bp = new FrameLayout.LayoutParams(-2, dp(48), Gravity.RIGHT | Gravity.TOP);
        bp.rightMargin = dp(42); bp.topMargin = dp(31); root.addView(bar, bp);

        grid = new RecyclerView(this);
        grid.setClipToPadding(false); grid.setPadding(dp(36), dp(6), dp(36), dp(25));
        grid.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
        grid.setItemAnimator(null);
        layout = new GridLayoutManager(this, columns);
        layout.setSpanSizeLookup(new GridLayoutManager.SpanSizeLookup() {
            @Override public int getSpanSize(int p) { return p == 0 || p == columns + 1 ? columns : 1; }
        });
        grid.setLayoutManager(layout); adapter = new Adapter(); grid.setAdapter(adapter);
        FrameLayout.LayoutParams gp = new FrameLayout.LayoutParams(-1, -1); gp.topMargin = dp(128);
        root.addView(grid, gp);
        grid.addItemDecoration(new RecyclerView.ItemDecoration() {
            private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
            @Override public void onDraw(Canvas canvas, RecyclerView parent, RecyclerView.State state) {
                View first = layout.findViewByPosition(1);
                if (first != null) {
                    paint.setColor(0xc018191c);
                    canvas.drawRoundRect(dp(35), first.getTop() - dp(6), parent.getWidth() - dp(35),
                        first.getBottom() + dp(5), dp(22), dp(22), paint);
                }
            }
        });
        grid.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override public void onScrolled(RecyclerView r, int x, int y) { positionGlass(false); }
        });
        glass = new FocusGlass(); glass.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        root.addView(glass, new FrameLayout.LayoutParams(-1, -1));
        setContentView(root);
        root.post(() -> {
            heroHeight = Math.max(dp(65), grid.getHeight() - dp(205));
            adapter.notifyDataSetChanged(); moveFocus();
        });
    }

    @Override protected void onResume() {
        super.onResume(); active = true; handler.removeCallbacks(ticker); ticker.run(); refreshNetwork(); reloadApps();
    }
    @Override protected void onPause() {
        super.onPause(); active = false; handler.removeCallbacks(ticker);
        prefs.edit().putInt("selection", selected).putInt("topIndex", topIndex).apply();
    }
    @Override protected void onDestroy() {
        super.onDestroy(); handler.removeCallbacksAndMessages(null); worker.shutdownNow();
        if (focusAnimation != null) focusAnimation.cancel();
    }
    private void refreshNetwork() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
        NetworkCapabilities caps = cm.getNetworkCapabilities(cm.getActiveNetwork());
        String label = caps == null ? "Offline" : caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ? "Wi-Fi" : "Network";
        // SSID access needs location on Android 11; avoid requesting it merely for this label.
        pills[1].setText(label);
    }

    private void reloadApps() {
        worker.execute(() -> {
            PackageManager pm = getPackageManager();
            Map<String, App> found = new HashMap<>();
            Intent query = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER);
            for (ResolveInfo ri : pm.queryIntentActivities(query, 0)) {
                String pkg = ri.activityInfo.packageName;
                if (pkg.equals(getPackageName()) || pkg.equals("dev.bawar.tv.installcheck")) continue;
                Intent launch = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)
                    .setComponent(new ComponentName(pkg, ri.activityInfo.name)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                found.put(pkg, new App(pkg, ri.loadLabel(pm).toString(), launch, ri));
            }
            App settings = new App("@settings", "Settings", new Intent(Settings.ACTION_SETTINGS), null);
            List<App> newDock = new ArrayList<>(); Set<String> used = new HashSet<>();
            for (String pkg : getResources().getStringArray(R.array.dock_apps)) {
                App app = pkg.equals("@settings") ? settings : found.get(pkg);
                if (app != null && used.add(app.id) && newDock.size() < columns) newDock.add(app);
            }
            List<App> remaining = new ArrayList<>();
            for (App app : found.values()) if (!used.contains(app.id)) remaining.add(app);
            Collator collator = Collator.getInstance();
            Collections.sort(remaining, (a, b) -> collator.compare(a.name, b.name));
            while (newDock.size() < columns && !remaining.isEmpty()) newDock.add(remaining.remove(0));
            while (newDock.size() < columns) newDock.add(settings);
            if (!used.contains(settings.id) && !newDock.contains(settings)) remaining.add(settings);
            handler.post(() -> {
                if (isDestroyed()) return;
                dock.clear(); dock.addAll(newDock); apps.clear(); apps.addAll(remaining);
                selected = Math.max(0, Math.min(selected, dock.size() + apps.size() - 1));
                adapter.notifyDataSetChanged(); moveFocus();
            });
        });
    }
    private App selectedApp() {
        if (selected < dock.size()) return dock.get(selected);
        int p = selected - columns; return p >= 0 && p < apps.size() ? apps.get(p) : null;
    }
    private int adapterPosition() { return selected < columns ? selected + 1 : selected + 2; }

    @Override public boolean dispatchKeyEvent(KeyEvent e) {
        int key = e.getKeyCode();
        boolean nav = key == KeyEvent.KEYCODE_DPAD_UP || key == KeyEvent.KEYCODE_DPAD_DOWN ||
            key == KeyEvent.KEYCODE_DPAD_LEFT || key == KeyEvent.KEYCODE_DPAD_RIGHT ||
            key == KeyEvent.KEYCODE_DPAD_CENTER || key == KeyEvent.KEYCODE_ENTER ||
            key == KeyEvent.KEYCODE_BACK || key == KeyEvent.KEYCODE_MENU;
        if (!nav) return super.dispatchKeyEvent(e);
        if (e.getAction() != KeyEvent.ACTION_DOWN) return true;
        if (dock.isEmpty()) return true;
        if (key == KeyEvent.KEYCODE_MENU) { if (e.getRepeatCount() == 0) options(); return true; }
        if (key == KeyEvent.KEYCODE_BACK) { top = false; selected = Math.min(selected, columns - 1); moveFocus(); return true; }
        if (key == KeyEvent.KEYCODE_DPAD_CENTER || key == KeyEvent.KEYCODE_ENTER) {
            if (e.getRepeatCount() == 0) activate(); return true;
        }
        if (top) {
            if (key == KeyEvent.KEYCODE_DPAD_LEFT) topIndex = Math.max(0, topIndex - 1);
            if (key == KeyEvent.KEYCODE_DPAD_RIGHT) topIndex = Math.min(2, topIndex + 1);
            if (key == KeyEvent.KEYCODE_DPAD_DOWN) top = false;
        } else {
            GridNavigation.Direction direction = key == KeyEvent.KEYCODE_DPAD_LEFT ? GridNavigation.Direction.LEFT :
                key == KeyEvent.KEYCODE_DPAD_RIGHT ? GridNavigation.Direction.RIGHT :
                key == KeyEvent.KEYCODE_DPAD_UP ? GridNavigation.Direction.UP : GridNavigation.Direction.DOWN;
            int next = GridNavigation.move(selected, columns + apps.size(), columns, direction);
            if (next < 0) top = true; else selected = next;
        }
        moveFocus(); return true;
    }
    private void moveFocus() {
        if (dock.isEmpty()) return;
        if (top || selected < columns) layout.scrollToPositionWithOffset(0, 0);
        else grid.smoothScrollToPosition(adapterPosition());
        App app = selectedApp();
        if (hero != null && app != null) hero.setText(app.name);
        if (app != null) backdrop.setAccent(app.accent);
        for (int i = 0; i < grid.getChildCount(); i++) {
            View child = grid.getChildAt(i);
            if (child instanceof Tile) ((Tile) child).selected(!top && grid.getChildAdapterPosition(child) == adapterPosition());
        }
        grid.post(() -> positionGlass(true));
    }
    private void positionGlass(boolean animate) {
        View view = top ? pills[topIndex] : layout.findViewByPosition(adapterPosition());
        if (view == null) { glass.setAlpha(0); return; }
        glass.setAlpha(1);
        Rect rect = new Rect(); view.getDrawingRect(rect); root.offsetDescendantRectToMyCoords(view, rect);
        if (!top) { rect.top += dp(5); rect.bottom -= dp(31); rect.left += dp(7); rect.right -= dp(7); }
        RectF target = new RectF(rect); target.inset(-dp(3), -dp(3));
        if (focusAnimation != null) focusAnimation.cancel();
        if (!animate || focusRect.isEmpty()) { focusRect.set(target); glass.invalidate(); return; }
        final RectF start = new RectF(focusRect);
        focusAnimation = ValueAnimator.ofFloat(0, 1); focusAnimation.setDuration(240);
        focusAnimation.setInterpolator(new OvershootInterpolator(0.65f));
        focusAnimation.addUpdateListener(a -> {
            float t = (float) a.getAnimatedValue();
            focusRect.set(start.left + (target.left - start.left) * t, start.top + (target.top - start.top) * t,
                start.right + (target.right - start.right) * t, start.bottom + (target.bottom - start.bottom) * t);
            glass.invalidate();
        }); focusAnimation.start();
    }
    private void activate() {
        if (top) {
            if (topIndex == 0) showInputs();
            else safeStart(new Intent(topIndex == 1 ? Settings.ACTION_WIFI_SETTINGS : Settings.ACTION_SETTINGS));
        } else { App app = selectedApp(); if (app != null) safeStart(app.intent); }
    }
    private void safeStart(Intent intent) {
        try { startActivity(intent); }
        catch (ActivityNotFoundException | SecurityException error) {
            Toast.makeText(this, "This shortcut is unavailable on this TV. Open TV Settings instead.", Toast.LENGTH_LONG).show();
        }
    }
    private void showInputs() {
        List<TvInputInfo> inputs = new ArrayList<>(); List<String> labels = new ArrayList<>();
        try {
            TvInputManager manager = (TvInputManager) getSystemService(TV_INPUT_SERVICE);
            for (TvInputInfo input : manager.getTvInputList()) {
                if (input.isHidden(this)) continue;
                inputs.add(input); CharSequence custom = input.loadCustomLabel(this);
                labels.add((custom != null ? custom : input.loadLabel(this)).toString());
            }
        } catch (SecurityException ignored) { }
        if (inputs.isEmpty()) { safeStart(new Intent(Intent.ACTION_VIEW, TvContract.Channels.CONTENT_URI)); return; }
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle("Inputs")
            .setItems(labels.toArray(new String[0]), (d, which) -> {
                TvInputInfo input = inputs.get(which);
                safeStart(new Intent(Intent.ACTION_VIEW, input.isPassthroughInput()
                    ? TvContract.buildChannelUriForPassthroughInput(input.getId()) : TvContract.Channels.CONTENT_URI));
            }).create();
        dialog.show();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(rounded(0xf022252c, 20));
            dialog.getWindow().setGravity(Gravity.TOP | Gravity.RIGHT);
            android.view.WindowManager.LayoutParams p = dialog.getWindow().getAttributes();
            p.width = dp(340); p.height = -2; p.x = dp(40); p.y = dp(90); dialog.getWindow().setAttributes(p);
        }
    }
    private void options() {
        new AlertDialog.Builder(this).setTitle("Quiet TV")
            .setItems(new String[] { "Choose wallpaper", "Reset wallpaper", "Selected app settings", "Open Google TV" }, (d, which) -> {
                if (which == 0) safeStart(new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("image/*").addCategory(Intent.CATEGORY_OPENABLE));
                if (which == 1) { wallpaper = null; backdrop.bitmap = null; prefs.edit().remove("wallpaper").apply(); backdrop.invalidate(); }
                if (which == 2) { App a = selectedApp(); if (a != null && !a.id.startsWith("@")) safeStart(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + a.id))); }
                if (which == 3) safeStart(new Intent().setComponent(new ComponentName("com.google.android.apps.tv.launcherx", "com.google.android.apps.tv.launcherx.home.HomeActivity")));
            }).show();
    }
    // External file-picker results are delivered here; no storage or location permissions are requested.
    @Override public void startActivity(Intent intent) {
        if (Intent.ACTION_OPEN_DOCUMENT.equals(intent.getAction())) super.startActivityForResult(intent, 40);
        else super.startActivity(intent);
    }
    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request == 40 && result == RESULT_OK && data != null && data.getData() != null) {
            Uri uri = data.getData();
            try { getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION); }
            catch (SecurityException ignored) { }
            prefs.edit().putString("wallpaper", uri.toString()).apply(); loadWallpaper(uri);
        }
    }
    private void loadWallpaper(Uri uri) {
        wallpaper = uri;
        worker.execute(() -> {
            try {
                BitmapFactory.Options o = new BitmapFactory.Options(); o.inJustDecodeBounds = true;
                try (InputStream in = getContentResolver().openInputStream(uri)) { BitmapFactory.decodeStream(in, null, o); }
                o.inSampleSize = 1; while (o.outWidth / o.inSampleSize > 1920 || o.outHeight / o.inSampleSize > 1080) o.inSampleSize *= 2;
                o.inJustDecodeBounds = false;
                final Bitmap bitmap;
                try (InputStream in = getContentResolver().openInputStream(uri)) { bitmap = BitmapFactory.decodeStream(in, null, o); }
                handler.post(() -> { if (uri.equals(wallpaper) && !isDestroyed()) { backdrop.bitmap = bitmap; backdrop.invalidate(); } });
            } catch (Exception ignored) { }
        });
    }

    private class Adapter extends RecyclerView.Adapter<Holder> {
        @Override public int getItemCount() { return dock.isEmpty() ? 0 : 2 + columns + apps.size(); }
        @Override public int getItemViewType(int position) { return position == 0 ? 0 : position == columns + 1 ? 1 : 2; }
        @Override public Holder onCreateViewHolder(ViewGroup parent, int type) {
            if (type == 0) {
                FrameLayout space = new FrameLayout(HomeActivity.this);
                hero = text("", 43, Color.WHITE);
                FrameLayout.LayoutParams hp = new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM);
                hp.leftMargin = dp(12); hp.bottomMargin = dp(28); space.addView(hero, hp); return new Holder(space);
            }
            if (type == 1) { TextView label = text("All apps", 16, 0xffb0b2b8); label.setPadding(dp(12), dp(27), 0, 0); return new Holder(label); }
            return new Holder(new Tile());
        }
        @Override public void onBindViewHolder(Holder holder, int p) {
            int type = getItemViewType(p);
            holder.itemView.setLayoutParams(new RecyclerView.LayoutParams(-1, type == 0 ? heroHeight : type == 1 ? dp(65) : dp(158)));
            if (type == 0) { App app = selectedApp(); if (hero != null && app != null) hero.setText(app.name); return; }
            if (type != 2) return;
            App app = p <= columns ? dock.get(p - 1) : apps.get(p - columns - 2);
            Tile tile = (Tile) holder.itemView; tile.bind(app);
            tile.selected(!top && p == adapterPosition());
            tile.setOnClickListener(v -> { selected = p <= columns ? p - 1 : p - 2; top = false; moveFocus(); activate(); });
        }
        @Override public void onViewAttachedToWindow(Holder holder) { grid.post(() -> positionGlass(false)); }
    }
    private static class Holder extends RecyclerView.ViewHolder { Holder(View view) { super(view); } }
    private class Tile extends LinearLayout {
        final ImageView image; final TextView name; String bound;
        Tile() {
            super(HomeActivity.this); setOrientation(VERTICAL); setGravity(Gravity.TOP | Gravity.CENTER_HORIZONTAL);
            setPadding(dp(9), dp(7), dp(9), 0);
            image = new ImageView(HomeActivity.this); image.setScaleType(ImageView.ScaleType.FIT_CENTER);
            image.setBackground(rounded(0xff25272c, 12)); image.setClipToOutline(true);
            addView(image, new LinearLayout.LayoutParams(-1, dp(119)));
            name = text("", 13, Color.WHITE); name.setGravity(Gravity.CENTER); name.setSingleLine(true);
            name.setEllipsize(android.text.TextUtils.TruncateAt.END);
            LinearLayout.LayoutParams np = new LinearLayout.LayoutParams(-1, dp(27)); np.topMargin = dp(4); addView(name, np);
        }
        void selected(boolean yes) {
            name.setAlpha(yes ? 1 : 0); image.animate().scaleX(yes ? 1.035f : 1).scaleY(yes ? 1.035f : 1).setDuration(170).start();
        }
        void bind(App app) {
            bound = app.id; name.setText(app.name); setContentDescription(app.name);
            Bitmap cached = banners.get(app.id); image.setImageBitmap(cached);
            if (cached != null) return;
            worker.execute(() -> {
                Bitmap bitmap = Bitmap.createBitmap(400, 225, Bitmap.Config.ARGB_8888);
                Canvas canvas = new Canvas(bitmap); canvas.drawColor(0xff25272c);
                Drawable drawable = null; boolean banner = false;
                if (app.info != null) {
                    try { drawable = app.info.activityInfo.loadBanner(getPackageManager()); banner = drawable != null; }
                    catch (RuntimeException ignored) { }
                    if (drawable == null) { try { drawable = app.info.loadIcon(getPackageManager()); } catch (RuntimeException ignored) { } }
                }
                if (drawable != null) {
                    int h = banner ? 225 : 140;
                    int iw = Math.max(1, drawable.getIntrinsicWidth()), ih = Math.max(1, drawable.getIntrinsicHeight());
                    int w = Math.min(400, Math.round((float) iw / ih * h));
                    drawable.setBounds((400 - w) / 2, (225 - h) / 2, (400 + w) / 2, (225 + h) / 2); drawable.draw(canvas);
                } else {
                    Paint p = new Paint(Paint.ANTI_ALIAS_FLAG); p.setColor(Color.WHITE); p.setTypeface(font); p.setTextSize(65);
                    p.setTextAlign(Paint.Align.CENTER); canvas.drawText(app.id.equals("@settings") ? "Settings" : app.name.substring(0, 1), 200, 136, p);
                }
                long red = 0, green = 0, blue = 0; int n = 0;
                for (int y = 20; y < 225; y += 20) for (int x = 20; x < 400; x += 20) {
                    int c = bitmap.getPixel(x, y); red += Color.red(c); green += Color.green(c); blue += Color.blue(c); n++;
                }
                app.accent = Color.rgb((int) (red / n), (int) (green / n), (int) (blue / n));
                banners.put(app.id, bitmap);
                handler.post(() -> {
                    if (isDestroyed()) return;
                    if (bound.equals(app.id)) image.setImageBitmap(bitmap);
                    if (selectedApp() == app) backdrop.setAccent(app.accent);
                });
            });
        }
    }
    private class FocusGlass extends View {
        final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        FocusGlass() { super(HomeActivity.this); setClickable(false); setFocusable(false); }
        @Override protected void onDraw(Canvas canvas) {
            paint.setStyle(Paint.Style.FILL); paint.setColor(0x16ffffff);
            canvas.drawRoundRect(focusRect, dp(top ? 26 : 15), dp(top ? 26 : 15), paint);
            paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(dp(1.5f)); paint.setColor(0xbbffffff);
            canvas.drawRoundRect(focusRect, dp(top ? 26 : 15), dp(top ? 26 : 15), paint);
        }
    }
    private class Backdrop extends View {
        final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        final RectF wallpaperRect = new RectF();
        Bitmap bitmap; int accent = 0xff456684; RadialGradient glow;
        Backdrop() { super(HomeActivity.this); }
        void setAccent(int color) { if (accent != color) { accent = color; updateGlow(); invalidate(); } }
        @Override protected void onSizeChanged(int w, int h, int oldW, int oldH) { updateGlow(); }
        private void updateGlow() {
            glow = new RadialGradient(getWidth() * .35f, getHeight() * .57f, Math.max(1, getWidth() * .55f),
                (accent & 0xffffff) | 0x66000000, Color.TRANSPARENT, Shader.TileMode.CLAMP);
        }
        @Override protected void onDraw(Canvas canvas) {
            int base = Color.BLACK; try { base = Color.parseColor(getString(R.string.wallpaper_color)); } catch (IllegalArgumentException ignored) { }
            canvas.drawColor(base);
            if (bitmap != null) {
                float scale = Math.max((float) getWidth() / bitmap.getWidth(), (float) getHeight() / bitmap.getHeight());
                float w = bitmap.getWidth() * scale, h = bitmap.getHeight() * scale;
                paint.setShader(null); paint.setAlpha(105);
                wallpaperRect.set((getWidth()-w)/2, (getHeight()-h)/2, (getWidth()+w)/2, (getHeight()+h)/2);
                canvas.drawBitmap(bitmap, null, wallpaperRect, paint);
                paint.setAlpha(255);
            }
            paint.setShader(glow);
            canvas.drawRect(0, 0, getWidth(), getHeight(), paint); paint.setShader(null);
        }
    }
}
