package io.agora.education.classroom.strategy;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import io.agora.base.Callback;
import io.agora.base.network.RetrofitManager;
import io.agora.education.BuildConfig;
import io.agora.education.classroom.bean.channel.ChannelInfo;
import io.agora.education.classroom.bean.channel.Room;
import io.agora.education.classroom.bean.channel.User;
import io.agora.education.classroom.bean.msg.ChannelMsg;
import io.agora.education.classroom.bean.msg.PeerMsg;
import io.agora.education.service.RoomService;
import io.agora.education.service.bean.ResponseBody;
import io.agora.education.service.bean.request.UserReq;
import io.agora.education.service.bean.response.RoomRes;
import io.agora.rtm.RtmChannelMember;
import io.agora.rtm.RtmMessage;
import io.agora.sdk.listener.RtmEventListener;
import io.agora.sdk.manager.RtcManager;
import io.agora.sdk.manager.RtmManager;
import io.agora.sdk.manager.SdkManager;

public class HttpChannelStrategy extends ChannelStrategy<Room> {

    private RoomService roomService;

    public HttpChannelStrategy(String channelId, User local) {
        super(channelId, local);
        roomService = RetrofitManager.instance().getService(BuildConfig.API_BASE_URL, RoomService.class);
        RtmManager.instance().registerListener(rtmEventListener);
    }

    @Override
    public void release() {
        super.release();
        RtmManager.instance().unregisterListener(rtmEventListener);
    }

    @Override
    public void joinChannel() {
        roomService.room(ChannelInfo.CONFIG.appId, getChannelId()).enqueue(new RetrofitManager.Callback<>(0, new Callback<ResponseBody<RoomRes>>() {
            @Override
            public void onSuccess(ResponseBody<RoomRes> res) {
                Room room = res.data.room;
                User user = res.data.user;
                RtmManager.instance().joinChannel(new HashMap<String, String>() {{
                    put(SdkManager.CHANNEL_ID, room.channelName);
                }});
                RtcManager.instance().joinChannel(new HashMap<String, String>() {{
                    put(SdkManager.TOKEN, user.rtcToken);
                    put(SdkManager.CHANNEL_ID, room.channelName);
                    put(SdkManager.USER_ID, user.getUid());
                }});
            }

            @Override
            public void onFailure(Throwable throwable) {
            }
        }));
    }

    @Override
    public void leaveChannel() {
        RtmManager.instance().leaveChannel();
        RtcManager.instance().leaveChannel();
    }

    @Override
    public void queryOnlineStudentNum(@NonNull Callback<Integer> callback) {
        callback.onSuccess(0);
    }

    @Override
    public void queryChannelInfo(@Nullable Callback<Void> callback) {
        roomService.room(ChannelInfo.CONFIG.appId, getChannelId()).enqueue(new RetrofitManager.Callback<>(0, new Callback<ResponseBody<RoomRes>>() {
            @Override
            public void onSuccess(ResponseBody<RoomRes> res) {
                parseChannelInfo(res.data.room);
                if (callback != null)
                    callback.onSuccess(null);
            }

            @Override
            public void onFailure(Throwable throwable) {
                if (callback != null)
                    callback.onFailure(throwable);
            }
        }));
    }

    @Override
    public void parseChannelInfo(Room data) {
        if (data.coVideoUsers != null) {
            List<User> students = new ArrayList<>();
            for (User user : data.coVideoUsers) {
                if (user.isTeacher()) {
                    setTeacher(user);
                } else if (user.uid == getLocal().uid) {
                    setLocal(user);
                } else {
                    students.add(user);
                }
            }
            setStudents(students);
        }
        data.coVideoUsers = null;
        setRoom(data);
    }

    @Override
    public void updateLocalAttribute(User local, @Nullable Callback<Void> callback) {
        roomService.user(ChannelInfo.CONFIG.appId, getChannelId(), local.userId, new UserReq(local)).enqueue(new RetrofitManager.Callback<>(0, new Callback<ResponseBody<Boolean>>() {
            @Override
            public void onSuccess(ResponseBody<Boolean> res) {
                if (callback != null) {
                    callback.onSuccess(null);
                }
                queryChannelInfo(null);
            }

            @Override
            public void onFailure(Throwable throwable) {
                if (callback != null) {
                    callback.onFailure(throwable);
                }
            }
        }));
    }

    @Override
    public void clearLocalAttribute(@Nullable Callback<Void> callback) {
        if (callback != null) {
            callback.onSuccess(null);
        }
    }

    private RtmEventListener rtmEventListener = new RtmEventListener() {
        @Override
        public void onJoinChannelSuccess(String channel) {
            queryChannelInfo(new Callback<Void>() {
                @Override
                public void onSuccess(Void aVoid) {
                    if (channelEventListener != null) {
                        channelEventListener.onChannelInfoInit();
                    }
                }

                @Override
                public void onFailure(Throwable throwable) {
                }
            });
        }

        @Override
        public void onMessageReceived(RtmMessage rtmMessage, RtmChannelMember rtmChannelMember) {
            if (channelEventListener != null) {
                ChannelMsg msg = ChannelMsg.fromJson(rtmMessage.getText(), ChannelMsg.class);
                channelEventListener.onChannelMsgReceived(msg);
            }
        }

        @Override
        public void onMessageReceived(RtmMessage rtmMessage, String s) {
            if (channelEventListener != null) {
                PeerMsg msg = PeerMsg.fromJson(rtmMessage.getText(), PeerMsg.class);
                channelEventListener.onPeerMsgReceived(msg);
            }
        }

        @Override
        public void onMemberCountUpdated(int i) {
            if (channelEventListener != null) {
                channelEventListener.onMemberCountUpdated(i);
            }
        }
    };

}
