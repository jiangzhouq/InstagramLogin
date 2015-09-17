package com.qjizho.inspmarker.service;

import android.app.Service;
import android.content.Intent;
import android.database.Cursor;
import android.os.Binder;
import android.os.IBinder;
import android.support.annotation.Nullable;
import android.util.Log;

import com.loopj.android.http.AsyncHttpClient;
import com.loopj.android.http.AsyncHttpResponseHandler;
import com.loopj.android.http.RequestParams;
import com.qjizho.inspmarker.db.Account;
import com.qjizho.inspmarker.helper.InsImage;
import com.qjizho.inspmarker.helper.UserProfileHeaderInfo;
import com.qjizho.inspmarker.helper.Utils;

import org.apache.http.Header;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;

import in.srain.cube.views.list.ListPageInfo;

/**
 * Created by qjizho on 15-9-11.
 */
public class InsHttpRequestService extends Service {
    //Users
    //Get basic information about a user. To get information about the owner of the access token, you can use self instead of the user-id.
    public static final String GET_USERS_USERID = "https://api.instagram.com/v1/users/%s";
    //See the authenticated user's feed.
    public static final String GET_USERS_SELF_FEED ="https://api.instagram.com/v1/users/self/feed";
    //Get the most recent media published by a user. To get the most recent media published by the owner of the access token, you can use self instead of the user-id.
    public static final String GET_USERS_USERID_MEDIA_RECENT ="https://api.instagram.com/v1/users/%s/media/recent";
    //See the list of media liked by the authenticated user. Private media is returned as long as the authenticated user has permission to view that media.
    // Liked media lists are only available for the currently authenticated user.
    public static final String GET_USERS_SELF_MEDIA_LIKED = "https://api.instagram.com/v1/users/self/media/liked";
    //Search for a user by name.
    public static final String GET_USERS_SEARCH = "https://api.instagram.com/v1/users/search?q=%s";
    /*Relationship*/
    //Get the list of users this user follows.
    public static final String GET_USERS_USERID_FOLLOWS = "https://api.instagram.com/v1/users/%s/follows";
    //Get the list of users this user is followed by.
    public static final String GET_USERS_USERID_FOLLOWEDBY = "https://api.instagram.com/v1/users/%s/followed-by";
    //List the users who have requested this user's permission to follow.
    public static final String GET_USERS_USERID_REQUESTBY = "https://api.instagram.com/v1/users/self/requested-by";
    //Get information about a relationship to another user.
    public static final String GET_USERS_USERID_RELATIONSHIP = "https://api.instagram.com/v1/users/%s/relationship";
    //Modify the relationship between the current user and the target user.
    public static final String POST_USERS_USERID_RELATIONSHIP = "https://api.instagram.com/v1/users/%s/relationship";
    //Media
    //Get information about a media object. The returned type key will allow you to differentiate between image and video media.
    //Note: if you authenticate with an OAuth Token, you will receive the user_has_liked key which quickly tells you whether the current user has liked this media item.
    public static final String GET_MEDIA_MEDIAID = "https://api.instagram.com/v1/media/%s";
    //Search for media in a given area. The default time span is set to 5 days. The time span must not exceed 7 days. Defaults time stamps cover the last 5 days. Can return mix of image and video types.
    public static final String GET_MEDIA_SEARCH = "https://api.instagram.com/v1/media/search?lat=%s&lng=%s";
    //Get a list of what media is most popular at the moment. Can return mix of image and video types.
    public static final String GET_MEDIA_POPULAR = "https://api.instagram.com/v1/media/popular";
    //Comment
    //Get a list of recent comments on a media object.
    public static final String GET_MEDIA_COMMENTS = "https://api.instagram.com/v1/media/%s/comments";
    //Create a comment on a media object with the following rules:
    public static final String POST_MEDIA_COMMENTS = "";
    //Remove a comment either on the authenticated user's media object or authored by the authenticated user.
    public static final String DEL_MEDIA_COMMENTS_COMMENTID = "";
    //Likes
    //Get a list of users who have liked this media.
    public static final String GET_MEDIA_MEDIAID_LIKES = "https://api.instagram.com/v1/media/%s/likes";
    //Set a like on this media by the currently authenticated user.
    public static final String POST_MEDIA_MEDIAID_LIKES = "";
    //Remove a like on this media by the currently authenticated user.
    public static final String DEL_MEDIA_MEDIAID_LIKES = "";
    //Tag
    //Get information about a tag object.
    public static final String GET_TAGS_TAGNAME = "https://api.instagram.com/v1/tags/%s";
    //Get a list of recently tagged media. Use the max_tag_id and min_tag_id parameters in the pagination response to paginate through these objects.
    public static final String GET_TAGS_TAGNAME_MEDIA_RECENT = "https://api.instagram.com/v1/tags/%s/media/recent";
    //Search for tags by name.
    public static final String GET_TAGS_SEARCH = "https://api.instagram.com/v1/tags/search?q=%s";
    //Location
    //Get information about a location.
    public static final String GET_LOCATIONS_LOCATIONID = "https://api.instagram.com/v1/locations/%s";
    //Get a list of recent media objects from a given location.
    public static final String GET_LOCATIONS_LOCATIONID_MEDIA_RECENT = "https://api.instagram.com/v1/locations/%s/media/recent";
    //Search for a location by geographic coordinate.
    public static final String GET_LOCATIONS_SEARCH = "https://api.instagram.com/v1/locations/search?lat=%s&lng=%s";
    //Geography
    //Get recent media from a geography subscription that you created.
    public final String GET_GEOGRAPHY_GEOID_MEDIA_RECENT = "https://api.instagram.com/v1/geographies/%S/media/recent?client_id=%S";


    public static final int REQUEST_REFRESH = 0;
    public static final int REQUEST_HOLD = 1;
    public static final int REQUEST_LOADMORE = 2;

    private ArrayList<InsImage> mPicUrls = new ArrayList<InsImage>();
    public ListPageInfo<InsImage> mInfosSelfFeed = new ListPageInfo<InsImage>(36);
    public ListPageInfo<InsImage> mInfosRecentMedia = new ListPageInfo<InsImage>(36);
    public int mPosition = 0;
    public String mSelfFeedPagination = "";
    public String mRecentMediaPagination = "";
    public String mId;
    public String mToken;
    private InsHttpBinder mInsHttpBinder = new InsHttpBinder();
    private OnReturnListener mOnReturnListener;
    private String mCurUserId = "";
    private UserProfileHeaderInfo mUserProfileHeaderInfo;

    public interface OnReturnListener {
        void onReturnForSelfFeed (ListPageInfo listPageInfo);
        void onReturnForRecentMedia (ListPageInfo listPageInfo);
        void onReturnForUserInfo(UserProfileHeaderInfo userProfileHeaderInfo);
    }
    public class InsHttpBinder extends Binder{
        public void startHttpRequest(String url, int action, String x0, String x1){
            Log.d("qiqi", "Service received order . url: " + url + " action:" + action );
            if(url.equals(GET_USERS_SELF_FEED)){
                switch(action){
                    case REQUEST_HOLD:
                        mOnReturnListener.onReturnForSelfFeed(mInfosSelfFeed);
                        break;
                    case REQUEST_REFRESH:
                        mInfosSelfFeed = new ListPageInfo<InsImage>(36);
                        mSelfFeedPagination = "";
                    case REQUEST_LOADMORE:
                        requestForGetUsersSelfFeed(url);
                        break;
                }
//
            }else if(url.equals(GET_USERS_USERID_MEDIA_RECENT)){
                url = String.format(url, x0 == null ? mCurUserId : x0);
                switch(action){
                    case REQUEST_HOLD:
                        mOnReturnListener.onReturnForRecentMedia(mInfosRecentMedia);
                        break;
                    case REQUEST_REFRESH:
                        mInfosRecentMedia = new ListPageInfo<InsImage>(36);
                        mRecentMediaPagination = "";
                    case REQUEST_LOADMORE:
                        requestForGetUsersUseridMediaRecent(url);
                        break;
                }
//
            }else if (url.equals(GET_USERS_USERID)){
                url = String.format(url, x0 == null ? mCurUserId : x0);
                switch(action){
                    case REQUEST_REFRESH:
                        requestForGetUsersUserid(url);
                        break;
                }
            }

//            }
        }
        public InsHttpRequestService getService(){
            return InsHttpRequestService.this;
        }
    }
    @Override
    public void onCreate() {
        super.onCreate();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        Cursor activedCur = getContentResolver().query(Account.CONTENT_URI_ACCOUNTS,null,"actived=1",null,null);
        activedCur.moveToNext();
        mToken = activedCur.getString(Account.NUM_ACCESS_TOKEN);
        activedCur.close();
        return mInsHttpBinder;
    }
    public void setOnReturnListener (OnReturnListener onReturnListener){
        mOnReturnListener = onReturnListener;
    }
    private void requestForGetUsersUserid(String url){
        mUserProfileHeaderInfo = new UserProfileHeaderInfo();
        AsyncHttpClient client = new AsyncHttpClient();
        RequestParams params = new RequestParams();
        params.add("access_token", mToken);
        client.get(url, params, new AsyncHttpResponseHandler() {
                    @Override
                    public void onSuccess(int statusCode, Header[] headers, byte[] responseBody) {
                        try {
                            JSONObject obj = new JSONObject(new String(responseBody));
                            JSONObject dataObj = obj.getJSONObject("data");
                            mUserProfileHeaderInfo.mId = dataObj.getString("id");
                            mUserProfileHeaderInfo.mUserName = dataObj.getString("username");
                            mUserProfileHeaderInfo.mFullName = dataObj.getString("full_name");
                            mUserProfileHeaderInfo.mProfilePic = dataObj.getString("profile_picture");
                            mUserProfileHeaderInfo.mBio = dataObj.getString("bio");
                            mUserProfileHeaderInfo.mWebSite = dataObj.getString("website");
                            JSONObject countObj = dataObj.getJSONObject("counts");
                            mUserProfileHeaderInfo.mCountsMedia = countObj.getString("media");
                            mUserProfileHeaderInfo.mCountsFollows = countObj.getString("follows");
                            mUserProfileHeaderInfo.mCountsFollowing = countObj.getString("followed_by");
                            mOnReturnListener.onReturnForUserInfo(mUserProfileHeaderInfo);
                        } catch (Exception e) {
                            Log.d("qiqi", e.toString());
                        }
                    }
                    @Override
                    public void onFailure(int statusCode, Header[] headers, byte[] responseBody, Throwable error) {
                    }
                }

        );
    }
    private void requestForGetUsersUseridMediaRecent(String url){

        mPicUrls.clear();
        AsyncHttpClient client = new AsyncHttpClient();
        RequestParams params = new RequestParams();
        params.add("access_token", mToken);
        params.add("count", String.valueOf(Utils.mRefreshCount));
        url = mRecentMediaPagination.isEmpty() ? url : mRecentMediaPagination;
        Log.d("qiqi","http request:" + url + " " + mToken);
        client.get(url, params, new AsyncHttpResponseHandler() {
            @Override
            public void onSuccess(int statusCode, Header[] headers, byte[] responseBody) {
                try {
                    JSONObject obj = new JSONObject(new String(responseBody));
                    JSONObject pObj = obj.getJSONObject("pagination");
                    JSONArray dataArray = obj.getJSONArray("data");
                    InsImage insImage;
                    for (int i = 0; i < dataArray.length(); i++) {
                        insImage = new InsImage();
                        JSONObject dataObj = dataArray.getJSONObject(i);
                        JSONObject imageObj = dataObj.getJSONObject("images");
                        JSONObject lowPObj = imageObj.getJSONObject("low_resolution");
                        JSONObject thumbnailPObj = imageObj.getJSONObject("thumbnail");
                        JSONObject standardPObj = imageObj.getJSONObject("standard_resolution");
                        insImage.mLowResolution = lowPObj.getString("url");
                        insImage.mThumbnail = thumbnailPObj.getString("url");
                        insImage.mStandardResolution = standardPObj.getString("url");
                        JSONObject userObj = dataObj.getJSONObject("user");
                        insImage.mUserName = userObj.getString("username");
                        insImage.mUserFullName = userObj.getString("full_name");
                        insImage.mProfilePciture = userObj.getString("profile_picture");
                        insImage.mUserId = userObj.getString("id");
                        if (!dataObj.isNull("caption")) {
                            JSONObject captionObj = dataObj.getJSONObject("caption");
                            if (!captionObj.isNull("text")) {
                                insImage.mCaption = captionObj.getString("text");
                            } else {
                                insImage.mCaption = "";
                            }
                        }
                        mPicUrls.add(insImage);
                    }
                    mRecentMediaPagination = pObj.isNull("next_url") ? "" : pObj.getString("next_url");
                    mInfosRecentMedia.updateListInfo(mPicUrls, !mRecentMediaPagination.isEmpty());
                    mOnReturnListener.onReturnForRecentMedia(mInfosRecentMedia);
                } catch (Exception e) {
                    Log.d("qiqi", "error:" + e.toString());
                }
            }
            @Override
            public void onFailure(int statusCode, Header[] headers, byte[] responseBody, Throwable error) {
                Log.d("qiqi", "statusCode:" + statusCode);
            }
        });

    }
    private void requestForGetUsersSelfFeed(String url){

        mPicUrls.clear();
        AsyncHttpClient client = new AsyncHttpClient();
        RequestParams params = new RequestParams();
        params.add("access_token", mToken);
        params.add("count", String.valueOf(Utils.mRefreshCount));
        url = mSelfFeedPagination.isEmpty() ? url : mSelfFeedPagination;
        Log.d("qiqi","http request:" + url + " " + mToken);
        client.get(url, params, new AsyncHttpResponseHandler() {
            @Override
            public void onSuccess(int statusCode, Header[] headers, byte[] responseBody) {
                try {
                    JSONObject obj = new JSONObject(new String(responseBody));
                    JSONObject pObj = obj.getJSONObject("pagination");
                    JSONArray dataArray = obj.getJSONArray("data");
                    InsImage insImage;
                    for (int i = 0; i < dataArray.length(); i++) {
                        insImage = new InsImage();
                        JSONObject dataObj = dataArray.getJSONObject(i);
                        JSONObject imageObj = dataObj.getJSONObject("images");
                        JSONObject lowPObj = imageObj.getJSONObject("low_resolution");
                        JSONObject thumbnailPObj = imageObj.getJSONObject("thumbnail");
                        JSONObject standardPObj = imageObj.getJSONObject("standard_resolution");
                        insImage.mLowResolution = lowPObj.getString("url");
                        insImage.mThumbnail = thumbnailPObj.getString("url");
                        insImage.mStandardResolution = standardPObj.getString("url");
                        JSONObject userObj = dataObj.getJSONObject("user");
                        insImage.mUserName = userObj.getString("username");
                        insImage.mUserFullName = userObj.getString("full_name");
                        insImage.mProfilePciture = userObj.getString("profile_picture");
                        insImage.mUserId = userObj.getString("id");
                        if (!dataObj.isNull("caption")) {
                            JSONObject captionObj = dataObj.getJSONObject("caption");
                            if (!captionObj.isNull("text")) {
                                insImage.mCaption = captionObj.getString("text");
                            } else {
                                insImage.mCaption = "";
                            }
                        }
                        mPicUrls.add(insImage);
                    }
                    mSelfFeedPagination = pObj.isNull("next_url") ? "" : pObj.getString("next_url");
                    mInfosSelfFeed.updateListInfo(mPicUrls, !mSelfFeedPagination.isEmpty());
                    mOnReturnListener.onReturnForSelfFeed(mInfosSelfFeed);
                } catch (Exception e) {
                    Log.d("qiqi", "error:" + e.toString());
                }
            }
            @Override
            public void onFailure(int statusCode, Header[] headers, byte[] responseBody, Throwable error) {
                Log.d("qiqi", "statusCode:" + statusCode);
            }
        });

    }
    @Override
    public boolean onUnbind(Intent intent) {
        return super.onUnbind(intent);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
    }
}
