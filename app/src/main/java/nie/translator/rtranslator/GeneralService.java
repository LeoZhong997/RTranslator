/*
 * Copyright 2016 Luca Martino.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copyFile of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package nie.translator.rtranslator;

import android.app.Service;
import android.content.Intent;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Messenger;
import android.os.RemoteException;
import androidx.annotation.Nullable;


public abstract class GeneralService extends Service {
    /**
     * GeneralService 的抽象类，是 Android 中 Service 类的子类
     * 为其他服务提供通用的功能和通信机制，特别是通过 Messenger 实现与客户端（如 Activity 或 Fragment）之间的跨进程通信（IPC）。
     * 功能：
     *  （1）处理命令：接收并执行来自客户端的命令。
     *  （2）通知客户端：通过 Messenger 将结果或错误信息发送回客户端。
     *  （3）统一错误处理：提供通用的错误通知机制。
     */
    // 提供统一的命令和回调标识符，便于客户端和服务之间进行通信。
    //commands
    public static final int INITIALIZE_COMMUNICATION = 50;  // 初始化客户端与服务之间的通信
    //callbacks
    public static final int ON_ERROR = 50;  // 标识错误回调
    //objects
    private Messenger clientMessenger;      // 存储客户端的 Messenger 对象，用于向客户端发送消息


    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        /**
         * 重写了 Service 的 onBind 方法，但返回 null
         * 当客户端尝试绑定到服务时调用
         * 表示该服务不支持绑定模式（bindService），仅支持启动模式（startService）。
         */
        return null;
    }

    protected boolean executeCommand(int command, Bundle data) {
        /**
         * 根据传入的命令执行相应的操作。
         * 当前仅支持 INITIALIZE_COMMUNICATION 命令
         */
        switch (command) {
            case INITIALIZE_COMMUNICATION: {
                // 从 Bundle 中提取客户端的 Messenger 对象，并存储到 clientMessenger 中
                // 初始化客户端与服务之间的通信通道。
                clientMessenger = data.getParcelable("messenger");
                return true;
            }
        }
        return false;
    }

    /*@Override
    public boolean onUnbind(Intent intent) {
        //si cancella l' handler e di conseguenza si interrompe l'invio al fragment
        clientMessenger = null;
        return super.onUnbind(intent);
    }*/

    protected void notifyToClient(Bundle bundle) {
        /**
         * 向客户端发送消息。
         * 实现服务向客户端的通知机制。
         */
        if (clientMessenger != null) {
            // 如果 clientMessenger 不为空，则创建一个 Message 对象，将数据打包到 Bundle 中，并通过 clientMessenger 发送给客户端。
            android.os.Message message = android.os.Message.obtain();
            message.setData(bundle);
            try {
                clientMessenger.send(message);
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }
    }

    public void notifyError(int[] reasons, long value) {
        /**
         * 构造一个包含错误信息的 Bundle，并通过 notifyToClient 方法发送给客户端。
         * 提供统一的错误通知机制，便于客户端处理错误。
         */
        Bundle bundle = new Bundle();
        // 错误信息包括：回调标识符（ON_ERROR）。回调标识符（ON_ERROR）。错误值（value）。
        bundle.putInt("callback", ON_ERROR);
        bundle.putIntArray("reasons",reasons);
        bundle.putLong("value",value);
        notifyToClient(bundle);
    }
}
