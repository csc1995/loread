package me.wizos.loread.activity;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.v4.util.ArrayMap;
import android.support.v4.view.ViewPager;
import android.support.v7.widget.Toolbar;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.webkit.JavascriptInterface;
import android.widget.TextView;

import com.afollestad.materialdialogs.MaterialDialog;
import com.socks.library.KLog;

import java.io.File;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

import me.wizos.loread.App;
import me.wizos.loread.R;
import me.wizos.loread.bean.Article;
import me.wizos.loread.bean.Img;
import me.wizos.loread.bean.Tag;
import me.wizos.loread.data.WithDB;
import me.wizos.loread.data.WithSet;
import me.wizos.loread.net.API;
import me.wizos.loread.net.Neter;
import me.wizos.loread.net.Parser;
import me.wizos.loread.presenter.adapter.MaterialSimpleListAdapter;
import me.wizos.loread.presenter.adapter.MaterialSimpleListItem;
import me.wizos.loread.presenter.adapter.ViewPagerAdapter;
import me.wizos.loread.utils.FileUtil;
import me.wizos.loread.utils.HttpUtil;
import me.wizos.loread.utils.StringUtil;
import me.wizos.loread.utils.ToastUtil;
import me.wizos.loread.utils.Tool;
import me.wizos.loread.view.IconFontView;
import me.wizos.loread.view.X5WebView;
import me.wizos.loread.view.colorful.Colorful;

@SuppressLint("SetJavaScriptEnabled")
public class ArticleActivity extends BaseActivity implements View.OnClickListener {
    protected static final String TAG = "ArticleActivity";
    private X5WebView webView;
    private IconFontView vStar, vRead, vSave;
    //    private NestedScrollView vScrolllayout ; // 这个是为上级顶部，页面滑动至最顶层而做的。目前由于 webview 是动态添加，所以无法用到
    private TextView vArticleNum;

    private Article article;
    private static String articleID = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_article);
        App.artHandler = artHandler;
        mNeter = new Neter(artHandler);
//        mParser = new Parser();
        initView(); // 初始化界面上的 View，将变量映射到布局上。
        KLog.d("开始初始化数据");
        if (savedInstanceState != null) {
            articleNo = savedInstanceState.getInt("articleNo"); // setSelection 没有滚动效果，直接跳到指定位置。smoothScrollToPosition 有滚动效果的
            articleCount = savedInstanceState.getInt("articleCount");
        } else {
            articleNo = getIntent().getExtras().getInt("articleNo"); // 文章在列表中的位置编号，下标从 0 开始
            articleCount = getIntent().getExtras().getInt("articleCount"); // 列表中所有的文章数目
        }
        initViewPager();

//        // 配合X5内核使用。避免网页中的视频，上屏幕的时候，可能出现闪烁的情况
//        getWindow().setFormat(PixelFormat.TRANSLUCENT);
//        // 配合X5内核使用。避免输入法界面弹出后遮挡输入光标的问题
//        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE | WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN);
//        KLog.d("【op2】适配器总数：" + mViewPager.getAdapter().getCount() + "，View 总数：" +  views.size()  + "，当前项："  + mViewPager.getCurrentItem() );
    }

    @Override
    protected Colorful.Builder buildColorful(Colorful.Builder mColorfulBuilder) {
        mColorfulBuilder
                .backgroundColor(R.id.art_coordinator, R.attr.root_view_bg)
                // 设置 toolbar
                .backgroundColor(R.id.art_toolbar, R.attr.topbar_bg)
                .textColor(R.id.art_toolbar_num, R.attr.topbar_fg)

                // 设置 bottombar
                .backgroundColor(R.id.art_bottombar, R.attr.bottombar_bg)
                .textColor(R.id.art_bottombar_read, R.attr.bottombar_fg)
                .textColor(R.id.art_bottombar_star, R.attr.bottombar_fg)
                .textColor(R.id.art_bottombar_tag, R.attr.bottombar_fg)
                .textColor(R.id.art_bottombar_save, R.attr.bottombar_fg);
//        KLog.e("loread","这里是Article窗口");
        return mColorfulBuilder;
    }

    private void initView() {
        initToolbar();
        vStar = (IconFontView) findViewById(R.id.art_bottombar_star);
        vRead = (IconFontView) findViewById(R.id.art_bottombar_read);
        vSave = (IconFontView) findViewById(R.id.art_bottombar_save);
        vArticleNum =  (TextView)findViewById(R.id.art_toolbar_num);
//        vScrolllayout = (NestedScrollView) findViewById(R.id.art_scroll);
        vSave.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onSaveClick(v);
            }
        });
    }
    private void initToolbar() {
        Toolbar toolbar = (Toolbar) findViewById(R.id.art_toolbar);
        setSupportActionBar(toolbar); // ActionBar
        getSupportActionBar().setHomeButtonEnabled(true);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setDisplayShowTitleEnabled(false);
        toolbar.setOnClickListener(this);
//        白色箭头
//        Drawable upArrow = getResources().getDrawable(R.drawable.mz_ic_sb_back);
//        upArrow.setColorFilter(Color.WHITE, PorterDuff.Mode.SRC_ATOP);
//        getSupportActionBar().setHomeAsUpIndicator(upArrow); // 替换返回箭头
    }

    @Override
    protected void onDestroy() {
        // 如果参数为null的话，会将所有的Callbacks和Messages全部清除掉。
        // 这样做的好处是在 Acticity 退出的时候，可以避免内存泄露。因为 handler 内可能引用 Activity ，导致 Activity 退出后，内存泄漏
        KLog.e("loread", "onDestroy" + webView);
        artHandler.removeCallbacksAndMessages(null);
        for (int i = 0; i < viewPager.getChildCount(); i++) {
            // 使用自己包装的 webview
            X5WebView webView = (X5WebView) viewPager.getChildAt(i);
            webView.destroy();
        }
        viewPager.removeAllViews();
        super.onDestroy();
    }

    private static String fileTitle = "";


    @JavascriptInterface
    public void onDocumentReady() {
        KLog.d("loread", "文档已加载");
    }

    @JavascriptInterface
    public void log(String paramString) {
        KLog.d("loread", "【JSLog】" + paramString);
    }

    @JavascriptInterface
    public void loadImage(int imgNo, String url, String localUrl) {
        KLog.e("loread", "====================loadImage", WithDB.i().getImg(articleID, url).getDownState() + "【下载图片】" + imgNo);
        // 这么检测是有问题的，有些在数据库中已经被标记为已下，但是实际没有下的（标记为已读后），相关的图片那下载记录没有删
        if (WithDB.i().getImg(articleID, url).getDownState() != API.ImgMeta_Downover) {
            restartDownloadImg(imgNo, url);
        } else {
            imgLoadSuccess(url, localUrl);
        }
    }

    @JavascriptInterface
    public boolean readCachedImage(String src) {
        KLog.e("loread", "Loread", "读取缓存图片");
        Img imgMeta = WithDB.i().getImg(src);
        if (imgMeta != null) {
            if (imgMeta.getDownState() == API.ImgMeta_Downover) {
                return true;
            }
        }
        return false;
    }

    @JavascriptInterface
    public boolean canLoadImage() {
        if (WithSet.i().isDownImgWifi() && !HttpUtil.isWiFiActive()) {
            KLog.e("loread", "Loread", "能否加载图片false1");
            return false;
        } else if (!WithSet.i().isDownImgWifi() && !HttpUtil.isNetworkAvailable()) {
            KLog.e("loread", "Loread", "能否加载图片false2");
            return false;
        }
        KLog.e("loread", "Loread", "能否加载图片true");
        return true;
    }

    // 在JS中调用该方法
    @JavascriptInterface
    public void onImgClicked(final int imgNo, String src) {
        KLog.e(imgNo + " = " + src);

        // 下载图片
        // 打开大图
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                final MaterialSimpleListAdapter adapter = new MaterialSimpleListAdapter( ArticleActivity.this);
                adapter.add(new MaterialSimpleListItem.Builder(ArticleActivity.this)
                        .content("重新下载")
                        .icon(R.drawable.ic_vector_mark_after)
                        .backgroundColor(Color.WHITE)
                        .build());
                adapter.add(new MaterialSimpleListItem.Builder(ArticleActivity.this)
                        .content("打开大图")
                        .icon(R.drawable.ic_vector_mark_before)
                        .backgroundColor(Color.WHITE)
                        .build());
                new MaterialDialog.Builder(ArticleActivity.this)
                        .adapter(adapter, new MaterialDialog.ListCallback() {
                            @Override
                            public void onSelection(MaterialDialog dialog, View itemView, int which, CharSequence text) {
                                switch (which) {
                                    case 0:
                                        KLog.e( "重新下载" + imgNo );
                                        // 此时还要判断他的储存位置 是 box 还是 cache
                                        restartDownloadImg(imgNo, "");
                                        break;
                                    case 1:
                                        KLog.e( "打开大图" );
                                        break;
                                }

                                dialog.dismiss();
                            }
                        })
                        .show();

            }
        });
    }


    private void restartDownloadImg(int imgNo, String url) {
        imgNo = imgNo + 1 ;
        KLog.e("loread", "restartDownloadImg" + imgNo);
        Img imgMeta;
        imgMeta = WithDB.i().getImg(articleID, imgNo);
        if (imgMeta == null) {
            ToastUtil.showShort("没有找到图片");
            return;
        }

        String savePath = FileUtil.getRelativeDir(article.getSaveDir()) + fileTitle + "_files" + File.separator + imgMeta.getName();
        KLog.i("图片的保存目录为：" + savePath + "  下载地址为：" + imgMeta.getSrc());
        App.mNeter.loadImg(articleID, imgNo, imgMeta.getSrc(), savePath);
    }


    private void replaceSrc(final int imgNo, final String localSrc, Message msg) {
        final int no = imgNo-1; // 因为图片的标号是从 1 开始，而 DOM 中，要从 0 开始。
        KLog.e("loread", "替换src" + no + article.getSaveDir() + article.getTitle() + "：" + localSrc + "=" + webView);
        if (null == webView) {
            Message message = Message.obtain();
            message.what = API.S_BITMAP;
            message.setData(msg.getData());
            artHandler.sendMessageDelayed(message, 1000);
        } else {
            webView.post(new Runnable() {
                @Override
                public void run() {
                    webView.loadUrl("javascript:(function(){" +
                            "imgList = document.getElementsByTagName(\"img\");" +
                            "imgList[" + no + "].src = \"" + localSrc + "\"" +
                            "})()");
                }
            });
        }
    }

    private void replaceImgSrc(final int imgNo, final String localSrc, String imgSrc, Message msg) {
        final int no = imgNo - 1; // 因为图片的标号是从 1 开始，而 DOM 中，要从 0 开始。
        KLog.e("loread", "替换src" + no + article.getSaveDir() + article.getTitle() + "：" + localSrc + "=" + webView);
        if (null == webView) {
            Message message = Message.obtain();
            message.what = API.S_BITMAP;
            message.setData(msg.getData());
            artHandler.sendMessageDelayed(message, 1000);
        } else {
            webView.loadUrl("javascript:onImageLoadSuccess('" + imgSrc + "','" + localSrc + "');"); // "+ imgNo+ "," + localSrc +"
        }
    }

    private void imgLoadSuccess(final String imgSrc, final String localSrc) {
        KLog.e("loread", "替换imgLoadSuccess" + article.getSaveDir() + article.getTitle() + "：" + localSrc + "=" + webView);
        if (null == webView) {
//            Message message = Message.obtain();
//            message.what = API.S_BITMAP;
//            message.setData(msg.getData());
//            artHandler.sendMessageDelayed(message, 1000);
            ToastUtil.showShort("imgLoadSuccess此时webView尽然为空？");
        } else {
            webView.postDelayed(new Runnable() {
                @Override
                public void run() {
                    webView.loadUrl("javascript:onImageLoadSuccess('" + imgSrc + "','" + localSrc + "');");
                }
            }, webView.getDelayTime());
//            webView.loadUrl("javascript:onImageLoadSuccess('" + imgSrc+ "','" + localSrc +"');"); // "+ imgNo+ "," + localSrc +"
        }
    }

    private void imgLoadFail(String imgSrc) {
        if (null == webView) {
            ToastUtil.showShort("imgLoadFail此时webView尽然为空？");
        } else {
            webView.loadUrl("javascript:onImageLoadFailed('" + imgSrc + "');");
        }
    }


//    class getImages extends AsyncTask<String, int[], List<InoFeedArticle>>{
//    }


    @Override
    public void notifyDataChanged() {
    }
    // 非静态匿名内部类的实例，所以它持有外部类Activity的引用
    // 所以此处的 handler 会持有外部类 Activity 的引用，消息队列是在一个Looper线程中不断轮询处理消息。
    // 那么当这个Activity退出时消息队列中还有未处理的消息或者正在处理消息，而消息队列中的Message持有mHandler实例的引用，mHandler又持有Activity的引用，所以导致该Activity的内存资源无法及时回收，引发内存泄漏

    @Override
    public void onClick(View v) {
        KLog.e("loread", "【 toolbar 是否双击 】 vScrolllayout" + v.getId() + v.getTag());
        KLog.e("loread", "【 toolbar 是否双击 】 vScrolllayout" + R.id.art_bottombar_save);
        switch (v.getId()) {
            case R.id.art_toolbar_num:
            case R.id.art_toolbar:
                if (artHandler.hasMessages(API.MSG_DOUBLE_TAP)) {
                    artHandler.removeMessages(API.MSG_DOUBLE_TAP);
//                    vScrolllayout.smoothScrollTo(0, 0);
                } else {
                    artHandler.sendEmptyMessageDelayed(API.MSG_DOUBLE_TAP, ViewConfiguration.getDoubleTapTimeout());
                }
                break;
        }
    }

    private static Neter mNeter;
    public final ArtHandler artHandler = new ArtHandler(this);
    // 静态类不持有外部类的对象，所以你的Activity可以随意被回收，不容易造成内存泄漏
    public static class ArtHandler extends Handler {
        private final WeakReference<ArticleActivity> mActivity;

        ArtHandler(ArticleActivity activity) {
            mActivity = new WeakReference<>(activity);
        }

        @Override
        public void handleMessage(final Message msg) {
            KLog.d(msg);
            if (mActivity.get() == null) { // 返回引用对象的引用
                return;
            }
            String info = msg.getData().getString("res");
            String url = msg.getData().getString("url");
//            String filePath ="";
//            String articleID;
//            int imgNo;
            if ( info == null ){
                info = "";
            }
            KLog.d("【handler】" + msg.what + url);
            switch (msg.what) {
                case API.S_EDIT_TAG:
                    long logTime = msg.getData().getLong("logTime");
                    if(!info.equals("OK")){
                        mNeter.forData(url,API.request,logTime);
                        KLog.d("【返回的不是 ok");
                    }
                    break;
                case API.S_ARTICLE_CONTENTS:
                    Parser.instance().parseArticleContents(info);
//                    mActivity.get().initData(); // 内容重载
                    break;
                case API.S_BITMAP:
//                    imgNo = msg.getData().getInt("imgNo");
                    String imgName = msg.getData().getString("imgName");
                    String imgSrc = msg.getData().getString("imgSrc");
                    KLog.e("loread", "替换图片" + mActivity.get() + "=" + imgName + "=");
//                    mActivity.get().replaceImgSrc(imgNo, "./" + fileTitle + "_files" + File.separator + imgName,imgSrc, msg);
//                    mActivity.get().replaceSrc(imgNo, "./" + fileTitle + "_files" + File.separator + imgName, msg);
                    mActivity.get().imgLoadSuccess(imgSrc, "./" + fileTitle + "_files" + File.separator + imgName);

                    break;
                case API.F_BITMAP:
//                    mActivity.get().lmgLoadFail( msg.getData().getString("imgSrc") );
                    if (null == mActivity.get().webView) {
                        ToastUtil.showShort("此时webView尽然为空？");
                    } else {
                        mActivity.get().webView.postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                mActivity.get().webView.loadUrl("javascript:onImageLoadFailed('" + msg.getData().getString("imgSrc") + "');");
                            }
                        }, mActivity.get().webView.getDelayTime());
                    }
                    break;
                case API.F_Request:
                case API.F_Response:
                    if( info.equals("Authorization Required")){
                        ToastUtil.showShort("没有Authorization，请重新登录");
                        App.finishActivity(mActivity.get());
                        mActivity.get().goTo(LoginActivity.TAG, "Login For Authorization");
                        break;
                    }
                    ToastUtil.showShort("网络不好，中断");
                    break;
                case API.F_NoMsg:
                    break;
                case API.H_WEB:
                    break;
            }
        }
    }


    public void onTagClick(View view) {
        final List<Tag> tagsList = WithDB.i().getTags();
        ArrayList<String> tags = new ArrayList<>(tagsList.size()) ;
        for( Tag tag: tagsList ) {
            tags.add(tag.getTitle());
        }
        new MaterialDialog.Builder(this)
                .title(R.string.article_choose_tag_dialog_title)
                .items( tags )
                .itemsCallbackSingleChoice( -1, new MaterialDialog.ListCallbackSingleChoice() {
                    @Override
                    public boolean onSelection(MaterialDialog dialog, View view, int which, CharSequence text) {
                        String tagId = tagsList.get(which).getId();
                        StringBuilder newCategories = new StringBuilder(  article.getCategories().length()  );
                        String[] cateArray = article.getCategories().replace("]","").replace("[","").split(", ");
                        for (String cate:cateArray){
                            if (cate.contains("user/" + App.mUserID + "/label/")) {
                                mNeter.removeArticleTags(articleID, cate);
                                KLog.d("【-】" + cate );
                            }else {
                                newCategories.append(cate);
                                newCategories.append(", ");
                            }
                        }
                        newCategories.append(tagId);
                        newCategories.append("]");
                        KLog.d("【==】" + newCategories + articleID);
                        article.setCategories( newCategories.toString() );
                        mNeter.addArticleTags(articleID, tagId);
                        mNeter.markArticleStared(articleID);
                        dialog.dismiss();
                        return true; // allow selection
                    }
                })
                .show();
    }


    public void onStarClick(View view) {
        if (article.getStarState().equals(API.ART_UNSTAR)) {
            changeStarState(API.ART_STARED);
//            ToastUtil.showShort("已收藏");
            if (article.getSaveDir().equals(API.SAVE_DIR_BOX) || article.getSaveDir().equals(API.SAVE_DIR_BOXREAD)) {
                moveArticleDir(API.SAVE_DIR_STORE);
            }
        } else {
            changeStarState(API.ART_UNSTAR);
//            ToastUtil.showShort("取消收藏");
            if (article.getSaveDir().equals(API.SAVE_DIR_STORE) || article.getSaveDir().equals(API.SAVE_DIR_STOREREAD)) {
                moveArticleDir(API.SAVE_DIR_BOX);
            }
        }
        WithDB.i().saveArticle(article);
    }

    private void changeStarState(String iconState) {
        article.setStarState(iconState);
        if (iconState.equals(API.ART_STARED)) {
            vStar.setText(getString(R.string.font_stared));
            mNeter.markArticleStared(articleID);
        } else {
            vStar.setText(getString(R.string.font_unstar));
            mNeter.markArticleUnstar(articleID);
        }
        WithDB.i().saveArticle(article);
    }

    public void onSaveClick(View view){
        KLog.e("loread", "保存文件被点击");
        if (article.getSaveDir().equals(API.SAVE_DIR_CACHE)) {
            if (article.getStarState().equals(API.ART_STARED)) {
                moveArticleDir(API.SAVE_DIR_STORE);
            } else {
                moveArticleDir(API.SAVE_DIR_BOX);
            }
        } else {
            ToastUtil.showShort("文件已保存");
//            moveArticleDir( article.getSaveDir(), API.SAVE_DIR_CACHE ); // 暂时不能支持从 box/store 撤销保存回 cache，因为文章的正文是被加了修饰的，为 epub 文件做准备的
        }
        if (article.getSaveDir().equals(API.SAVE_DIR_CACHE)) {
            vSave.setText(getString(R.string.font_unsave));
        } else {
            vSave.setText(getString(R.string.font_saved));
        }
    }


    private void moveArticleDir(String targetDir) {
        String sourceTitle = fileTitle;
        KLog.d(fileTitle);
        String sourceDirPath = FileUtil.getRelativeDir(article.getSaveDir());
        String targetDirPath = FileUtil.getRelativeDir(targetDir);

        if (article.getSaveDir().equals(API.SAVE_DIR_CACHE)) {
            String articleHtml;
            articleHtml = FileUtil.readHtml(sourceDirPath + fileTitle + ".html");
            articleHtml = StringUtil.reviseHtmlForBox(article.getTitle(), articleHtml);
            FileUtil.saveHtml(sourceDirPath + fileTitle + ".html", articleHtml);
            fileTitle = article.getTitle();
//        KLog.d(articleHtml);
        }

        FileUtil.moveFile(sourceDirPath + sourceTitle + ".html", targetDirPath + fileTitle + ".html");
        FileUtil.moveDir(sourceDirPath + sourceTitle + "_files", targetDirPath + fileTitle + "_files");

//        KLog.d("原来文件夹" + sourceDirPath + sourceTitle + "_files");
//        KLog.d("目标文件夹" + targetDirPath + article.getTitle() + "_files");
        if (article.getImgState() != null && !article.getImgState().equals("")) {  // (lossSrcList != null && lossSrcList.size() != 0) || ( obtainSrcList!= null && obtainSrcList.size() != 0)
            article.setCoverSrc(targetDirPath + article.getTitle() + "_files" + File.separator + StringUtil.getFileNameExtByUrl(article.getCoverSrc()));
//            KLog.d("封面" + targetDirPath + article.getTitle() + "_files" + File.separator + StringUtil.getFileNameExtByUrl(article.getCoverSrc()));
        }
        article.setSaveDir(targetDir);
        WithDB.i().saveArticle(article);
    }


    public void onReadClick(View view) {
        KLog.e("loread", "被点击的是：" + article.getTitle());
        if (article.getReadState().equals(API.ART_READED)) {
            vRead.setText(getString(R.string.font_unread));
//            ToastUtil.showShort("未读");
            mNeter.markArticleUnread(articleID);
            article.setReadState(API.ART_UNREADING);
        }else {
            vRead.setText(getString(R.string.font_readed));
//            ToastUtil.showShort("已读");
            mNeter.markArticleReaded(articleID);
            article.setReadState(API.ART_READED);
        }
        WithDB.i().saveArticle(article);

    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK && event.getRepeatCount() == 0) { // 后者为短期内按下的次数
//            App.finishActivity(this);
            back();
            return true;//返回真表示返回键被屏蔽掉
        }
        return super.onKeyDown(keyCode, event);
    }

    private void back() {
        Intent data = new Intent();
        data.putExtra("articleNo", viewPager.getCurrentItem());
        ArticleActivity.this.setResult(3, data);//注意下面的RESULT_OK常量要与回传接收的Activity中onActivityResult（）方法一致
        App.finishActivity(this);//关闭当前activity
    }

    private int articleNo, articleCount;


    private void setIconState(Article article, int position) {
        if (article.getReadState().equals(API.ART_UNREAD)) {
            vRead.setText(getString(R.string.font_readed));
            article.setReadState(API.ART_READED);
            WithDB.i().saveArticle(article);
            mNeter.markArticleReaded(articleID);
            KLog.d("【 ReadState 】" + WithDB.i().getArticle(article.getId()).getReadState());
        } else if (article.getReadState().equals(API.ART_READED)) {
            vRead.setText(getString(R.string.font_readed));
        } else if (article.getReadState().equals(API.ART_UNREADING)) {
            vRead.setText(getString(R.string.font_unread));
        }

        if (article.getStarState().equals(API.ART_UNSTAR)) {
            vStar.setText(getString(R.string.font_unstar));
        } else {
            vStar.setText(getString(R.string.font_stared));
        }
        if (article.getSaveDir().equals(API.SAVE_DIR_CACHE)) {
            vSave.setText(getString(R.string.font_unsave));
        } else {
            vSave.setText(getString(R.string.font_saved));
        }
//        String numStr = String.valueOf(position + 1) + " / " + String.valueOf(articleCount);
        vArticleNum.setText((position + 1) + " / " + articleCount);
        KLog.d("=====position" + position);
    }


    public ViewPager viewPager;

    public void initViewPager() {
        viewPager = (ViewPager) findViewById(R.id.art_viewpager);
        viewPager.clearOnPageChangeListeners();
        Tool.setBackgroundColor(viewPager);
        ViewPagerAdapter viewPagerAdapter = new ViewPagerAdapter(this, viewPager, App.articleList, artHandler);
        viewPager.setAdapter(viewPagerAdapter);
        viewPager.addOnPageChangeListener(new PageChangeListener());
        viewPager.setCurrentItem(articleNo, false); // 本句放到 ViewPagerAdapter 的构造器中是无效的。
        if (articleNo == 0) {
            KLog.e("loread", "当articleNo为0时，试一次");
            ArticleActivity.this.initSelectedPage(0);
        }
//        KLog.e("loread","初始化ViewPager  "  + ( System.currentTimeMillis() - App.time ) );
    }


    // SimpleOnPageChangeListener 是 ViewPager 内部，用空方法实现 OnPageChangeListener 接口的一个类。
    // 主要是为了便于使用者，不用去实现 OnPageChangeListener 的每一个方法（没必要，好几个用不到），只需要直接继承 SimpleOnPageChangeListener 实现需要的方法即可。
    private class PageChangeListener extends ViewPager.SimpleOnPageChangeListener {
        /**
         * 参数position，代表哪个页面被选中。
         * 当用手指滑动翻页的时候，如果翻动成功了（滑动的距离够长），手指抬起来就会立即执行这个方法，position就是当前滑动到的页面。
         * 如果直接setCurrentItem翻页，那position就和setCurrentItem的参数一致，这种情况在onPageScrolled执行方法前就会立即执行。
         * 泪奔，当 setCurrentItem (0) 的时候，不会调用该函数
         * <p>
         * 点击进入 viewpager 时，该函数比 InstantiateItem 函数先执行。（之后滑动时，InstantiateItem 已经创建好了 view）
         * 所以，为了能在运行完 InstantiateItem ，有了 webView 之后再去执行 showingPageData，initSelectedPage 在首次进入时都不执行，放到 InstantiateItem 中。
         */
        @Override
        public void onPageSelected(final int pagePosition) {
            // 当知道页面被选中后：1.检查webView是否已经生成好了，若已生成则初始化所有数据（含页面的icon）；2.检查webView是否加载完毕，完毕则初始化懒加载
            KLog.e("loread", "【initSelectedPage】 " + "当前position=" + pagePosition + "  之前position=" + "  " + viewPager.findViewById(pagePosition)); //+ this.map.size()
            ArticleActivity.this.initSelectedPage(pagePosition);
        }
    }

    public void initSelectedPage(int position) {
        initSelectedArticle(position);
        initSelectedWebView(position);
    }

    public void initSelectedArticle(int position) {
        this.article = App.articleList.get(position);
        App.time = System.currentTimeMillis();
        KLog.e("loread", "initSelectedArticle" + article.getTitle() + "====" + webView);
        articleID = article.getId();
        App.currentArticleID = articleID;
        setIconState(article, position);

        if (article.getSaveDir().equals(API.SAVE_DIR_CACHE)) {
            fileTitle = StringUtil.stringToMD5(article.getId());
        } else {
            fileTitle = article.getTitle();
        }
    }

    public void initSelectedWebView(final int position) {
        if (viewPager.findViewById(position) != null) { // && dataList.get(pagePosition).getImgState()==null
            this.webView = (X5WebView) viewPager.findViewById(position);
            if (viewPager.getCurrentItem() == webView.getId()) {
                initWebViewLazyLoad();
            } else {
                KLog.e("loread", "----------选择的不是当前的webView");
            }
        } else {
            artHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    initSelectedWebView(position);
                }
            }, 100);
        }
    }

    private void initWebViewLazyLoad() {
        if (webView.getProgress() == 100) {
//            webView.loadUrl("javascript:docReady()");
            initImgDown();
        } else {
            artHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    initWebViewLazyLoad();
                }
            }, 100);
        }
    }

    private void initImgDown() {
        if (article.getImgState() != null && !article.getImgState().equals(API.ImgState_Downing)) {
            return;
        }
        final ArrayMap<Integer, Img> lossImgMap = WithDB.i().getLossImgs(articleID);
        KLog.e("loread", "initImgDown");
        artHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                App.mNeter.downImgs(articleID, lossImgMap, FileUtil.getRelativeDir(article.getSaveDir()) + fileTitle + "_files" + File.separator);
            }
        }, 1000);
    }


    @Override
    protected void onSaveInstanceState(Bundle outState) {
        outState.putInt("articleNo", viewPager.getCurrentItem());
        outState.putInt("articleCount", articleCount);
        super.onSaveInstanceState(outState);
    }
}
