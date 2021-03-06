/**
 *
 * Copyright 2016 Xiaofei
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package xiaofei.library.hermeseventbus;

import android.app.ActivityManager;
import android.content.Context;
import android.os.Process;
import android.util.Log;

import org.greenrobot.eventbus.EventBus;

import xiaofei.library.concurrentutils.ObjectCanary;
import xiaofei.library.concurrentutils.util.Action;
import xiaofei.library.concurrentutils.util.Function;
import xiaofei.library.hermes.Hermes;
import xiaofei.library.hermes.HermesListener;
import xiaofei.library.hermes.HermesService;

/**
 * Created by Xiaofei on 16/6/4.
 */
public class HermesEventBus {

    private static final String TAG = "HermesEventBus";

    private static final String HERMES_SERVICE_DISCONNECTED = "Hermes service disconnected!";

    private static volatile HermesEventBus sInstance = null;

    private final EventBus mEventBus;

    private volatile Context mContext;

    private volatile boolean mMainProcess;

    private volatile ObjectCanary<IMainService> mApis;

    private volatile MainService mMainApis;

    /**
     * TODO
     * 1. Change the name of mApis
     * 2. Create an integer field to indicate the state: NOT_CONNECTED, CONNECTED, DISCONNECTED
     *    to fix the problems in the GitHub issues.
     * 3. Consider more about the interleaving.
      */

    private HermesEventBus() {
        mEventBus = EventBus.getDefault();
        mApis = new ObjectCanary<IMainService>();
    }

    public static HermesEventBus getDefault() {
        if (sInstance == null) {
            synchronized (HermesEventBus.class) {
                if (sInstance == null) {
                    sInstance = new HermesEventBus();
                }
            }
        }
        return sInstance;
    }

    private static boolean isMainProcess(Context context) {
        return context.getPackageName().equals(getCurrentProcessName(context));
    }

    private static String getCurrentProcessName(Context context) {
        ActivityManager activityManager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        if (activityManager == null) {
            return null;
        }
        for (ActivityManager.RunningAppProcessInfo processInfo : activityManager.getRunningAppProcesses()) {
            if (processInfo.pid == Process.myPid()) {
                return processInfo.processName;
            }
        }
        return null;
    }

    public void init(Context context) {
        mContext = context;
        mMainProcess = isMainProcess(context.getApplicationContext());
        if (mMainProcess) {
            Hermes.init(context);
            Hermes.register(MainService.class);
            mMainApis = MainService.getInstance();
        } else {
            Hermes.register(SubService.class); // TODO is this necessary?
            Hermes.setHermesListener(new HermesListener());
            Hermes.connect(context, Service.class);
        }
    }

    public void connectApp(Context context, String packageName) {
        mContext = context;
        mMainProcess = false;
        Hermes.register(SubService.class); // TODO is this necessary?
        Hermes.setHermesListener(new HermesListener());
        Hermes.connectApp(context, packageName);
    }

    public void destroy() {
        if (!mMainProcess) {
            Hermes.disconnect(mContext);
        }
    }

    public void register(Object subscriber) {
        mEventBus.register(subscriber);
    }

    public boolean isRegistered(Object subscriber) {
        return mEventBus.isRegistered(subscriber);
    }

    public void unregister(Object subscriber) {
        mEventBus.unregister(subscriber);
    }

    public void post(final Object event) {
        if (mMainProcess) {
            mMainApis.post(event);
        } else {
            if (mApis == null) {
                Log.w(TAG, HERMES_SERVICE_DISCONNECTED);
            } else {
                mApis.actionNonNullNonBlocking(new Action<IMainService>() {
                    @Override
                    public void call(IMainService o) {
                        o.post(event);
                    }
                });
            }
        }
    }

    public void cancelEventDelivery(final Object event) {
        if (mMainProcess) {
            mMainApis.cancelEventDelivery(event);
        } else {
            if (mApis == null) {
                Log.w(TAG, HERMES_SERVICE_DISCONNECTED);
            } else {
                mApis.actionNonNullNonBlocking(new Action<IMainService>() {
                    @Override
                    public void call(IMainService o) {
                        o.cancelEventDelivery(event);
                    }
                });
            }
        }
    }

    public void postSticky(final Object event) {
        if (mMainProcess) {
            mMainApis.postSticky(event);
        } else {
            if (mApis == null) {
                Log.w(TAG, HERMES_SERVICE_DISCONNECTED);
            } else {
                mApis.actionNonNullNonBlocking(new Action<IMainService>() {
                    @Override
                    public void call(IMainService o) {
                        o.postSticky(event);
                    }
                });
            }
        }
    }

    public <T> T getStickyEvent(final Class<T> eventType) {
        if (mMainProcess) {
            return eventType.cast(mMainApis.getStickyEvent(eventType.getName()));
        } else {
            if (mApis == null) {
                Log.w(TAG, HERMES_SERVICE_DISCONNECTED);
                return null;
            } else {
                return mApis.calculateNonNull(new Function<IMainService, T>() {
                    @Override
                    public T call(IMainService o) {
                        return eventType.cast(o.getStickyEvent(eventType.getName()));
                    }
                });
            }
        }
    }

    public <T> T removeStickyEvent(final Class<T> eventType) {
        if (mMainProcess) {
            return eventType.cast(mMainApis.removeStickyEvent(eventType.getName()));
        } else {
            if (mApis == null) {
                Log.w(TAG, HERMES_SERVICE_DISCONNECTED);
                return null;
            } else {
                return mApis.calculateNonNull(new Function<IMainService, T>() {
                    @Override
                    public T call(IMainService o) {
                        return eventType.cast(o.removeStickyEvent(eventType.getName()));
                    }
                });
            }
        }
    }

    public boolean removeStickyEvent(final Object event) {
        if (mMainProcess) {
            return mMainApis.removeStickyEvent(event);
        } else {
            if (mApis == null) {
                Log.w(TAG, HERMES_SERVICE_DISCONNECTED);
                return false;
            } else {
                return mApis.calculateNonNull(new Function<IMainService, Boolean>() {
                    @Override
                    public Boolean call(IMainService o) {
                        return o.removeStickyEvent(event);
                    }
                });
            }
        }
    }

    public void removeAllStickyEvents() {
        if (mMainProcess) {
            mMainApis.removeAllStickyEvents();
        } else {
            if (mApis == null) {
                Log.w(TAG, HERMES_SERVICE_DISCONNECTED);
            } else {
                mApis.actionNonNullNonBlocking(new Action<IMainService>() {
                    @Override
                    public void call(IMainService o) {
                        o.removeAllStickyEvents();
                    }
                });
            }
        }
    }

    public boolean hasSubscriberForEvent(Class<?> eventClass) {
        return mEventBus.hasSubscriberForEvent(eventClass);
    }

    public class HermesListener extends xiaofei.library.hermes.HermesListener {

        @Override
        public void onHermesConnected(Class<? extends HermesService> service) {
            IMainService mainService = Hermes.getInstanceInService(service, IMainService.class);
            mainService.register(Process.myPid(), SubService.getInstance());
            if (mApis == null) {
                mApis = new ObjectCanary<IMainService>();
            }
            mApis.set(mainService);
        }

        @Override
        public void onHermesDisconnected(Class<? extends HermesService> service) {
            mApis.action(new Action<IMainService>() {
                @Override
                public void call(IMainService o) {
                    o.unregister(Process.myPid());
                }
            });
            mApis.set(null);
            mApis = null;
        }
    }

    public static class Service extends HermesService {

    }

}
