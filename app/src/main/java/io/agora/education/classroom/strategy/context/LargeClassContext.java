package io.agora.education.classroom.strategy.context;

import android.annotation.SuppressLint;
import android.content.Context;

import androidx.annotation.NonNull;

import java.util.Collections;
import java.util.List;

import io.agora.base.Callback;
import io.agora.base.ToastManager;
import io.agora.education.R;
import io.agora.education.classroom.bean.channel.User;
import io.agora.education.classroom.bean.msg.ChannelMsg;
import io.agora.education.classroom.bean.msg.PeerMsg;
import io.agora.education.classroom.strategy.ChannelStrategy;
import io.agora.rtc.Constants;
import io.agora.sdk.manager.RtcManager;

import static io.agora.education.classroom.bean.msg.ChannelMsg.UpdateMsg.Cmd.ACCEPT_CO_VIDEO;
import static io.agora.education.classroom.bean.msg.ChannelMsg.UpdateMsg.Cmd.CANCEL_CO_VIDEO;
import static io.agora.education.classroom.bean.msg.PeerMsg.CoVideoMsg.Cmd.APPLY_CO_VIDEO;
import static io.agora.education.classroom.bean.msg.PeerMsg.CoVideoMsg.Cmd.REJECT_CO_VIDEO;

public class LargeClassContext extends ClassContext {

    private boolean applying;

    LargeClassContext(Context context, ChannelStrategy strategy) {
        super(context, strategy);
    }

    @Override
    public void checkChannelEnterable(@NonNull Callback<Boolean> callback) {
        callback.onSuccess(true);
    }

    @Override
    void preConfig() {
        RtcManager.instance().setChannelProfile(Constants.CHANNEL_PROFILE_LIVE_BROADCASTING);
        RtcManager.instance().setClientRole(Constants.CLIENT_ROLE_AUDIENCE);
        RtcManager.instance().enableDualStreamMode(false);
    }

    @Override
    public void onTeacherChanged(User teacher) {
        super.onTeacherChanged(teacher);
        if (classEventListener instanceof LargeClassEventListener) {
            runListener(() -> ((LargeClassEventListener) classEventListener).onTeacherMediaChanged(teacher));
        }
    }

    @Override
    public void onLocalChanged(User local) {
        super.onLocalChanged(local);
        if (local.isGenerate) return;
        if (applying) {
            applying = false;
            // send apply order to the teacher when local attributes updated
            apply(false);
        }
        onLinkMediaChanged(Collections.singletonList(local));
    }

    @Override
    public void onStudentsChanged(List<User> students) {
        super.onStudentsChanged(students);
        onLinkMediaChanged(students);
    }

    private void onLinkMediaChanged(List<User> users) {
        for (User user : users) {
            if (user.isCoVideoEnable()) {
                if (classEventListener instanceof LargeClassEventListener) {
                    runListener(() -> ((LargeClassEventListener) classEventListener).onLinkMediaChanged(user));
                }
                break;
            }
        }
    }

    @Override
    @SuppressLint("SwitchIntDef")
    public void onChannelMsgReceived(ChannelMsg msg) {
        super.onChannelMsgReceived(msg);
        if (msg.type == ChannelMsg.Type.UPDATE) {
            ChannelMsg.UpdateMsg updateMsg = msg.getMsg();
            switch (updateMsg.cmd) {
                case ACCEPT_CO_VIDEO:
                    accept();
                    break;
                case CANCEL_CO_VIDEO:
                    cancel(true);
                    break;
            }
        }
    }

    @Override
    public void onPeerMsgReceived(PeerMsg msg) {
        super.onPeerMsgReceived(msg);
        if (msg.type == PeerMsg.Type.CO_VIDEO) {
            PeerMsg.CoVideoMsg coVideoMsg = msg.getMsg();
            if (coVideoMsg.cmd == REJECT_CO_VIDEO) {
                reject();
            }
        }
    }

    public void apply(boolean isPrepare) {
        User local = channelStrategy.getLocal();
        if (isPrepare) {
            channelStrategy.clearLocalAttribute(new Callback<Void>() {
                @Override
                public void onSuccess(Void aVoid) {
                    channelStrategy.updateLocalAttribute(local, new Callback<Void>() {
                        @Override
                        public void onSuccess(Void aVoid) {
                            applying = true;
                        }

                        @Override
                        public void onFailure(Throwable throwable) {
                            applying = false;
                        }
                    });
                }

                @Override
                public void onFailure(Throwable throwable) {

                }
            });
        } else {
            local.sendCoVideoMsg(APPLY_CO_VIDEO, channelStrategy.getTeacher());
        }
    }

    @Override
    public void onMemberCountUpdated(int count) {
        super.onMemberCountUpdated(count);
        if (classEventListener instanceof LargeClassEventListener) {
            runListener(() -> ((LargeClassEventListener) classEventListener).onUserCountChanged(count));
        }
    }

    public void cancel(boolean isRemote) {
        channelStrategy.clearLocalAttribute(new Callback<Void>() {
            @Override
            public void onSuccess(Void aVoid) {
                RtcManager.instance().setClientRole(Constants.CLIENT_ROLE_AUDIENCE);
                if (isRemote) {
                    if (classEventListener instanceof LargeClassEventListener) {
                        runListener(() -> ((LargeClassEventListener) classEventListener).onHandUpCanceled());
                    }
                } else {
                    channelStrategy.getLocal().sendUpdateMsg(CANCEL_CO_VIDEO);
                }
            }

            @Override
            public void onFailure(Throwable throwable) {

            }
        });
    }

    private void accept() {
        User local = channelStrategy.getLocal();
        local.disableAudio(false);
        local.disableVideo(false);
        channelStrategy.updateLocalAttribute(local, new Callback<Void>() {
            @Override
            public void onSuccess(Void aVoid) {
                RtcManager.instance().setClientRole(Constants.CLIENT_ROLE_BROADCASTER);
            }

            @Override
            public void onFailure(Throwable throwable) {

            }
        });
        ToastManager.showShort(R.string.accept_interactive);
    }

    private void reject() {
        channelStrategy.clearLocalAttribute(new Callback<Void>() {
            @Override
            public void onSuccess(Void aVoid) {
                applying = false;
            }

            @Override
            public void onFailure(Throwable throwable) {

            }
        });
        ToastManager.showShort(R.string.reject_interactive);
    }

    public interface LargeClassEventListener extends ClassEventListener {
        void onUserCountChanged(int count);

        void onTeacherMediaChanged(User user);

        void onLinkMediaChanged(User user);

        void onHandUpCanceled();
    }

}
