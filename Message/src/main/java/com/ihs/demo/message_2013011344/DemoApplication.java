package com.ihs.demo.message_2013011344;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Configuration;
import android.media.MediaPlayer;
import android.os.Handler;
import android.text.TextUtils;
import android.widget.Toast;

import com.baidu.location.BDLocation;
import com.baidu.location.BDLocationListener;
import com.baidu.location.LocationClient;
import com.baidu.location.LocationClientOption;
import com.baidu.mapapi.SDKInitializer;
import com.ihs.account.api.account.HSAccountManager;
import com.ihs.account.api.account.HSAccountManager.HSAccountSessionState;
import com.ihs.app.framework.HSApplication;
import com.ihs.app.framework.HSSessionMgr;
import com.ihs.commons.keepcenter.HSKeepCenter;
import com.ihs.commons.notificationcenter.HSGlobalNotificationCenter;
import com.ihs.commons.notificationcenter.INotificationObserver;
import com.ihs.commons.utils.HSBundle;
import com.ihs.commons.utils.HSLog;
import com.ihs.contacts.api.HSPhoneContactMgr;
import com.ihs.message_2013011344.R;
import com.ihs.message_2013011344.managers.HSMessageChangeListener;
import com.ihs.message_2013011344.managers.HSMessageManager;
import com.ihs.message_2013011344.types.HSBaseMessage;
import com.ihs.message_2013011344.types.HSOnlineMessage;
import com.ihs.message_2013011344.utils.Utils;
import com.nostra13.universalimageloader.cache.disc.naming.Md5FileNameGenerator;
import com.nostra13.universalimageloader.core.ImageLoader;
import com.nostra13.universalimageloader.core.ImageLoaderConfiguration;
import com.nostra13.universalimageloader.core.assist.QueueProcessingType;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.List;

import test.contacts.demo.friends.api.HSContactFriendsMgr;

/**
 * 用作中央控制器，各种消息(Notification)大多从这里发出
 * 仅有这里对MessageManager做了监听
 */
public class DemoApplication extends HSApplication implements HSMessageChangeListener, INotificationObserver {

    /*
     * 同步好友列表的服务器 URL
     */
    public static final String URL_SYNC = "http://54.223.212.19:8024/template/contacts/friends/get";
    public static final String URL_ACK = "http://54.223.212.19:8024/template/contacts/friends/get";
    public static final String APPLICATION_NOTIFICATION_MESSAGE_CHANGE = "APPLICATION_NOTIFICATION_MESSAGE_CHANGE";
    public static final String APPLICATION_NOTIFICATION_UNREAD_CHANGE = "APPLICATION_NOTIFICATION_UNREAD_CHANGE";
    MediaPlayer receivePlayer;
    MediaPlayer sendPlayer;
    private static final String TAG = DemoApplication.class.getName(); // 用于打印 log

    private NotificationManager notificationManager;

    LocationClient mLocationClient = null;
    static public BDLocation mLocation = new BDLocation();
    
    @Override
    public void onCreate() {
        super.onCreate();

        HSAccountManager.getInstance();

        doInit();

        initImageLoader(this);

        // 初始化百度地图 SDK
//        SDKInitializer.initialize(getApplicationContext());

        // 初始化通讯录管理类，同步通讯录，用于生成好友列表
        HSPhoneContactMgr.init();
        HSPhoneContactMgr.enableAutoUpload(true);
        HSPhoneContactMgr.startSync();

        // 初始化好友列表管理类，同步好友列表
        HSContactFriendsMgr.init(this, null, URL_SYNC, URL_ACK);
        HSContactFriendsMgr.startSync(true);

        // 将本类添加为 HSMessageManager 的监听者，监听各类消息变化事件
        // 参见 HSMessageManager 类与 HSMessageChangeListener 接口
        HSMessageManager.getInstance().addListener(this, new Handler());

        // 为 HSGlobalNotificationCenter 功能设定监听接口
        INotificationObserver observer = this;
        HSGlobalNotificationCenter.addObserver(SampleFragment.SAMPLE_NOTIFICATION_NAME, observer);// 演示HSGlobalNotificationCenter功能：增加名为 SAMPLE_NOTIFICATION_NAME 的观察者

        notificationManager = (NotificationManager)getSystemService(NOTIFICATION_SERVICE);

        receivePlayer = MediaPlayer.create(this, R.raw.message_ringtone_received);
        sendPlayer = MediaPlayer.create(this, R.raw.message_ringtone_sent);
        
        LocationClientOption opt = new LocationClientOption();
        opt.setLocationMode(LocationClientOption.LocationMode.Battery_Saving);
        mLocationClient = new LocationClient(this);
        mLocationClient.setLocOption(opt);

        mLocationClient.registerLocationListener(new BDLocationListener() {
            @Override
            public void onReceiveLocation(BDLocation bdLocation) {
                mLocation = bdLocation;
            }
        });
        mLocationClient.start();
        SDKInitializer.initialize(this);
        IntentFilter iFilter = new IntentFilter();
        iFilter.addAction(SDKInitializer.SDK_BROADTCAST_ACTION_STRING_PERMISSION_CHECK_ERROR);
        iFilter.addAction(SDKInitializer.SDK_BROADCAST_ACTION_STRING_NETWORK_ERROR);
        SDKReceiver mReceiver = new SDKReceiver();
        registerReceiver(mReceiver, iFilter);

    }

    public class SDKReceiver extends BroadcastReceiver {
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if(action.equals(SDKInitializer.SDK_BROADTCAST_ACTION_STRING_PERMISSION_CHECK_ERROR))
            {
                HSLog.e(TAG, "KEY ERROR!");
                SDKInitializer.initialize(getApplicationContext());
            }
        }
    }
    public static void doInit() {
        HSLog.d(TAG, "doInit invoked");

        // 验证登录状态
        if (HSAccountManager.getInstance().getSessionState() == HSAccountSessionState.VALID) {
            HSLog.d(TAG, "doInit during session is valid");
            HSMessageManager.getInstance();

            // 初始化长连接服务管理类 HSKeepCenter
            // 需传入标记应用的 App ID、标记帐户身份的 mid 和标记本次登录的 Session ID，三项信息均可从 HSAccountManager 获得
            HSKeepCenter.getInstance().set(HSAccountManager.getInstance().getAppID(), HSAccountManager.getInstance().getMainAccount().getMID(),
                    HSAccountManager.getInstance().getMainAccount().getSessionID());
            // 建立长连接
            HSKeepCenter.getInstance().connect();
        }
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
    }

    /**
     * 返回配置文件名
     */
    @Override
    protected String getConfigFileName() {
        return "config.ya";
    }

    public static void initImageLoader(Context context) {

        ImageLoaderConfiguration.Builder config = new ImageLoaderConfiguration.Builder(context);
        config.threadPriority(Thread.NORM_PRIORITY - 2);
        config.denyCacheImageMultipleSizesInMemory();
        config.diskCacheFileNameGenerator(new Md5FileNameGenerator());
        config.diskCacheSize(50 * 1024 * 1024); // 50 MiB
        config.tasksProcessingOrder(QueueProcessingType.LIFO);

        // Initialize ImageLoader with configuration.
        ImageLoader.getInstance().init(config.build());
    }

    /**
     * 返回多媒体消息的文件存储路径
     */
    void getMediaFilePath() {
        HSLog.d("getMediaFilePath: ", Utils.getMediaPath());
    }

    /**
     * 收到 “正在输入” 消息时被调用
     * 
     * @param fromMid “正在输入” 消息发送者的 mid
     */
    @Override
    public void onTypingMessageReceived(String fromMid) {

    }

    /**
     * 收到在线消息时被调用
     * 
     * @param message 收到的在线消息，其 content 值由用户定制，可实现自己的通讯协议和交互逻辑
     */
    @Override
    public void onOnlineMessageReceived(HSOnlineMessage message) {
        HSLog.d(TAG, "onOnlineMessageReceived");

        // 弹出 Toast 演示示例在线消息的 content 消息体内容
        HSBundle bundle = new HSBundle();
        bundle.putString(SampleFragment.SAMPLE_NOTIFICATION_BUNDLE_STRING, message.getContent().toString());
        HSGlobalNotificationCenter.sendNotificationOnMainThread(SampleFragment.SAMPLE_NOTIFICATION_NAME, bundle);
    }

    /**
     * 当来自某人的消息中，未读消息数量发生变化时被调用
     * 
     * @param mid 对应人的 mid
     * @param newCount 变化后的未读消息数量
     */
    @Override
    public void onUnreadMessageCountChanged(String mid, int newCount) {
        // 消息未读数量的变化大家可以在这里进行处理，比如修改每条会话的未读数量等。
    }

    /**
     * 当收到服务器通过长连接发送过来的推送通知时被调用，用途是进行新消息在通知窗口的通知，通知格式如下： alert 项为提示文字，fmid 代表是哪个 mid 发来的消息
     * {"act":"msg","aps":{"alert":"@: sent to a message_2013011344","sound":"push_audio_1.wav","badge":1},"fmid":"23"}
     * 
     * @param pushInfo 收到通知的信息
     */
    @Override
    public void onReceivingRemoteNotification(JSONObject userInfo) {
        HSLog.d(TAG, "receive remote notification: " + userInfo);
        HSLog.d(TAG, userInfo.toString());
        if (HSSessionMgr.getTopActivity() == null) {
            // 大家在这里做通知中心的通知即可
            String name;
            String mid;
            String message = "Send You A Message";
            String act;
            try {
                Contact contact = FriendManager.getInstance().getFriend(userInfo.getString("fmid"));
                mid = userInfo.getString("fmid");
                if (contact == null) {
                    name = "Stranger";
                } else {
                    name = contact.getName();
                }
                act = userInfo.getString("act");
                if (!act.equals("msg")) return;
            } catch (JSONException e) {
                return;
            }


            Intent intent = new Intent(this, ChatActivity.class);
            intent.putExtra("message_name", name);
            intent.putExtra("message_mid", mid);
            PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
            Notification notification;
            Notification.Builder builder = new Notification.Builder(this);
            builder.setWhen(System.currentTimeMillis());
            builder.setDefaults(Notification.DEFAULT_ALL);
            builder.setSmallIcon(R.drawable.ic_launcher);
            builder.setTicker("new message");
            builder.setContentTitle(name);
            builder.setContentText(message);
            builder.setContentIntent(pendingIntent);
            builder.setAutoCancel(true);
            notification = builder.getNotification();
            notificationManager.notify(Integer.valueOf(mid), notification);
        }
    }

   /**
     * 有消息发生变化时的回调方法
     * 
     * @param changeType 变化种类，消息增加 / 消息删除 / 消息状态变化
     * @param messages 变化涉及的消息对象
     */
    @Override
    public void onMessageChanged(HSMessageChangeType changeType, List<HSBaseMessage> messages) {
        // 同学们可以根据 changeType 的消息增加、删除、更新信息进行会话数据的构建

        if (!messages.isEmpty()) {
            HSBundle bundle = new HSBundle();
            bundle.putObject("changeType", changeType);
            bundle.putObject("messages", messages);
            HSGlobalNotificationCenter.sendNotificationOnMainThread(APPLICATION_NOTIFICATION_MESSAGE_CHANGE, bundle);
            HSGlobalNotificationCenter.sendNotificationOnMainThread(APPLICATION_NOTIFICATION_UNREAD_CHANGE, new HSBundle());
            if (HSSessionMgr.getTopActivity() != null && changeType == HSMessageChangeType.ADDED) {
                for (HSBaseMessage message : messages) {
                    if (message.getFrom().equals(HSAccountManager.getInstance().getMainAccount().getMID())) {
                        if (!sendPlayer.isPlaying()) {
                            sendPlayer.start();
                        }
                    }
                    if (message.getTo().equals(HSAccountManager.getInstance().getMainAccount().getMID())) {
                        if (!receivePlayer.isPlaying()) {
                            receivePlayer.start();
                        }
                    }
                }
            }
            if (changeType == HSMessageChangeType.ADDED) {
                ContactMsgManager.getInstance().insertMsgs(messages);
            }
        }
    }

    /**
     * 收到推送通知时的回调方法
     */
    @Override
    public void onReceive(String notificaitonName, HSBundle bundle) {
        // 供 HSGlobalNotificationCenter 功能参考，弹出 Toast 演示通知的效果
        String string = TextUtils.isEmpty(bundle.getString(SampleFragment.SAMPLE_NOTIFICATION_BUNDLE_STRING)) ? "消息为空" : bundle
                .getString(SampleFragment.SAMPLE_NOTIFICATION_BUNDLE_STRING); // 取得 bundle 中的信息
        Toast toast = Toast.makeText(getApplicationContext(), string, Toast.LENGTH_LONG);
        toast.show();
    }

}
