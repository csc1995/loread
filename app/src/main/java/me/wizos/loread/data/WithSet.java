package me.wizos.loread.data;

import android.app.Activity;
import android.content.SharedPreferences;

import me.wizos.loread.App;
import me.wizos.loread.net.API;

/**
 * 内部设置
 * Created by Wizos on 2016/4/30.
 */
public class WithSet {
    private static WithSet withSet;
    private static SharedPreferences mySharedPreferences;
    private static SharedPreferences.Editor editor;


    private WithSet() {
    }

    public static WithSet i() {
        if (withSet == null) { // 双重锁定，只有在 mySharedPreferences 还没被初始化的时候才会进入到下一行，然后加上同步锁
            synchronized (WithSet.class) {  // 同步锁，避免多线程时可能 new 出两个实例的情况
                if (withSet == null) {
                    withSet = new WithSet();
                    mySharedPreferences = App.i().getSharedPreferences("loread", Activity.MODE_PRIVATE);
                    editor = mySharedPreferences.edit();
                }
            }
        }
        return withSet;
    }


    private String readPref(String key, String defaultValue) {
        return mySharedPreferences.getString(key, defaultValue);//getString()第二个参数为缺省值，如果preference中不存在该key，将返回缺省值
    }

    private void savePref(String key, String value) {
//        SharedPreferences.Editor editor = mySharedPreferences.edit();//实例化SharedPreferences.Editor对象
        editor.putString(key, value); //用putString的方法保存数据
        editor.apply(); //提交当前数据
    }

    private boolean readPref(String key, boolean defaultValue) {
        return mySharedPreferences.getBoolean(key, defaultValue);
    }

    private void savePref(String key, boolean value) {
        editor.putBoolean(key, value); //用putString的方法保存数据
        editor.apply(); //提交当前数据
    }

    private int readPref(String key, int value) {
        return mySharedPreferences.getInt(key, value);
    }

    private void savePref(String key, int value) {
        editor.putInt(key, value);
        editor.apply();
    }

    private long readPref(String key, long value) {
        return mySharedPreferences.getLong(key, value);
    }

    private void savePref(String key, long value) {
        editor.putLong(key, value);
        editor.apply();
    }


    public String getAuth() {
        return readPref("Auth", "");
    }

    public void setAuth(String auth) {
        savePref("Auth", auth);
    }


    public String getAccountID() {
        return readPref("AccountID", "");
    }

    public void setAccountID(String accountID) {
        savePref("AccountID", accountID);
    }

    public String getAccountPD() {
        return readPref("AccountPD", "");
    }

    public void setAccountPD(String accountPD) {
        savePref("AccountPD", accountPD);
    }

    public long getUseId() {
        return readPref("UserID", 0L);
    }
    public void setUseId(long useId) {
        savePref("UserID", useId);
    }

    public String getUseName() {
        return readPref("UserName", "");
    }

    public void setUseName(String useName) {
        savePref("UserName", useName);
    }


    public boolean isSyncFirstOpen() {
        return readPref("SyncFirstOpen", false);
    }

    public void setSyncFirstOpen(boolean syncFirstOpen) {
        savePref("SyncFirstOpen", syncFirstOpen);
    }

    public boolean isSyncAllStarred() {
        return readPref("SyncAllStarred", false);
    }
    public void setSyncAllStarred(boolean syncAllStarred) {
        savePref("SyncAllStarred", syncAllStarred);
    }

    public boolean isHadSyncAllStarred() {
        return readPref("HadSyncAllStarred", false);
    }
    public void setHadSyncAllStarred(boolean had) {
        savePref("HadSyncAllStarred", had);
    }

    public String getSyncFrequency() {
        return readPref("SyncFrequency", "");
    }

    public void setSyncFrequency(String syncFrequency) {
        savePref("SyncFrequency", syncFrequency);
    }

    public int getClearBeforeDay() {
        return readPref("ClearBeforeDay", 7);
    }

    public void setClearBeforeDay(int clearBeforeDay) {
        savePref("ClearBeforeDay", clearBeforeDay);
    }


    public boolean isDownImgWifi() {
        return readPref("DownImgWifi", true);
    }
    public void setDownImgWifi(boolean downImgMode) {
        savePref("DownImgWifi", downImgMode);
    }

    public boolean isInoreaderProxy() {
        return readPref("InoreaderProxy", false);
    }
    public void setInoreaderProxy(boolean proxyMode) {
        savePref("InoreaderProxy", proxyMode);
        if( !isInoreaderProxy()){
            API.HOST = API.HOST_OFFICIAL;
        }else {
            API.HOST = API.HOST_PROXY;
        }
    }

    /**
     * 是否为滚动标记为已读
     */
    public boolean isScrollMark() {
        return readPref("ScrollMark", true);
    }

    /**
     * 设置滚动标记为已读
     */
    public void setScrollMark(boolean scrollMark) {
        savePref("ScrollMark", scrollMark);
    }


    public String getListTabState() {
        return readPref("ListState", "Unread");
    }

    public void setListTabState(String listState) {
        savePref("ListState", listState);
    }


    public String getListTagId() {
        return readPref("listTagId", null);
    }

    public void setListTagId(String listTagId) {
        savePref("listTagId", listTagId);
    }


    public boolean isOrderTagFeed() {
        return readPref("OrderTagFeed", true);
    }
    public void setOrderTagFeed(boolean is) {
        savePref("OrderTagFeed", is);
    }

    public boolean isSysBrowserOpenLink() {
        return readPref("SysBrowserOpenLink", false);
    }

    public void setSysBrowserOpenLink(boolean is) {
        savePref("SysBrowserOpenLink", is);
    }

    public int getThemeMode() {
        return readPref("ThemeMode", App.theme_Day);
    }
    public void setThemeMode(int themeMode) {
        savePref("ThemeMode", themeMode);
    }

}
