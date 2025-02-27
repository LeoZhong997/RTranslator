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

package nie.translator.rtranslator;     // 包声明：nie.translator.rtranslator 表示该文件属于 rtranslator 模块

import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import androidx.appcompat.app.AlertDialog;
import java.util.ArrayList;
import nie.translator.rtranslator.access.AccessActivity;
import nie.translator.rtranslator.tools.CustomLocale;
import nie.translator.rtranslator.tools.ErrorCodes;
import nie.translator.rtranslator.tools.ImageActivity;
import nie.translator.rtranslator.voice_translation.VoiceTranslationActivity;
import nie.translator.rtranslator.voice_translation.neural_networks.NeuralNetworkApi;
import nie.translator.rtranslator.voice_translation.neural_networks.translation.Translator;

import androidx.core.splashscreen.SplashScreen;


public class LoadingActivity extends GeneralActivity {
    /**
     * LoadingActivity 继承自 GeneralActivity，用于在应用启动时进行初始化操作。
     * 功能：
     *  （1）显示启动画面，提升用户体验
     *  （2）检查和加载应用所需的核心组件（语言列表、翻译器、语音识别器等）
     *  （3）处理初始化过程中的各种错误，并向用户提供反馈
     *  （4）根据应用状态和用户操作，跳转到不同的界面（如语音翻译界面、下载界面等）
     *  （5）管理 Activity 的生命周期，确保资源的正确使用
     */
    private final boolean START_IMAGE = false;  // 决定是否启动图片界面
    private Handler mainHandler;                // 用于在主线程中执行任务
    private boolean isVisible = false;          // 表示当前界面是否可见
    private Global global;                      // 全局配置对象，用于存储应用的全局状态
    private boolean startingActivity = false;   // 是否正在启动新界面
    private boolean showingError = false;       // 是否显示错误

    public LoadingActivity() {
        // Required empty public constructor
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        /**
         * 初始化启动画面（SplashScreen）
         * 设置界面主题和布局（R.layout.activity_loading）
         * 创建主线程的 Handler 对象（mainHandler）
         * 控制启动画面的显示条件（例如，如果显示错误信息时，则隐藏启动画面）
         */
        String previousActivity = getIntent().getStringExtra("activity");   // 获取跳转到当前 Activity 的来源
        SplashScreen splashScreen = null;
        // if this activity is called by the DownloadFragment we don't use the splash screen
        if(previousActivity == null || !previousActivity.equals("download")) {  
            // Handle the splash screen transition (it must remain before the super.onCreate() call).
            // 确保只有在非下载界面跳转时才显示启动画面，避免从下载界面跳转时重复显示
            splashScreen = SplashScreen.installSplashScreen(this);  // 初始化启动画面，一个短暂的过渡界面，通常用于提升用户体验。
        }
        super.onCreate(savedInstanceState);     // 调用父类（GeneralActivity 或 AppCompatActivity）的 onCreate 方法，完成 Activity 的基本初始化。
        if(splashScreen == null){
            // 如果没有启动画面（即从下载界面跳转），则设置界面主题为 R.style.Theme_Speech，确保界面在不同跳转来源下具有一致的外观。
            setTheme(R.style.Theme_Speech); 
        }
        setContentView(R.layout.activity_loading);  // 加载 activity_loading 布局文件，作为当前 Activity 的界面。
        // 创建一个 Handler 对象，绑定到主线程的消息队列（Looper.getMainLooper()）。用于在主线程中执行任务，例如更新 UI 或延迟操作。
        mainHandler = new Handler(Looper.getMainLooper());  

        // 控制启动画面的显示条件
        // Keep the splash screen visible for this Activity.
        if(splashScreen != null) {
            splashScreen.setKeepOnScreenCondition(new SplashScreen.KeepOnScreenCondition() {
                @Override
                public boolean shouldKeepOnScreen() {
                    // 启动画面会在 showingError 为 false 时保持可见。
                    // 确保启动画面在出现错误时不会继续显示，从而避免用户困惑。
                    return !showingError;
                }
            });
        }
    }

    public void onResume() {
        /**
         * 恢复界面时调用
         * 检查是否是首次启动（global.isFirstStart()），如果是，则跳转到权限请求界面（AccessActivity）
         * 如果翻译器和语音识别器已初始化，则跳转到语音翻译界面。
         * 否则，调用 initializeApp 方法初始化应用。
         */
        super.onResume();   // 调用父类（GeneralActivity 或 AppCompatActivity）的 onResume 方法，完成 Activity 的基本恢复操作。
        isVisible = true;   // 设置界面可见状态。用于控制某些逻辑（如启动画面的显示条件）
        global = (Global) getApplication();     // 获取全局应用对象 global，global 是一个自定义的 Application 子类，通常用于存储全局状态和配置。
        if (global.isFirstStart()) {
            // 如果是首次启动，则跳转到权限请求界面（AccessActivity）。
            Intent intent = new Intent(this, AccessActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
            finish();
        } else if (global.getTranslator() != null && global.getSpeechRecognizer() != null) {
            // 如果两者都已初始化，则调用 startVoiceTranslationActivity() 方法，跳转到语音翻译界面。
            startVoiceTranslationActivity();
        } else {
            // 如果既不是首次启动，也没有完成初始化（比如模型文件损坏或丢失），则调用 initializeApp 方法重新加载。
            initializeApp(false);
            //onFailure(new int[]{ErrorCodes.GOOGLE_TTS_ERROR}, 0);     // 在初始化失败时可以触发错误处理逻辑。
        }
    }

    @Override
    protected void onPause() {
        /**
         * 当界面暂停时，将 isVisible 标志设置为 false，表示当前界面不再可见。
         * 使用场景：（1）用户切换到其他界面，（2）应用进入后台，（3）界面被覆盖，如对话框或另一个 Activity
         */
        super.onPause();
        isVisible = false;  // 在 onCreate 方法中，启动画面的显示条件依赖于 isVisible 和 showingError 的值
    }

    private void initializeApp(boolean ignoreTTSError) {
        /**
         * 获取语言列表：加载支持的语言列表。
         * 初始化翻译器：加载翻译模型。
         * 初始化语音识别器：加载语音识别模型。
         * 跳转逻辑：如果初始化成功，则跳转到语音翻译界面；否则，处理错误。
         */
        global.getLanguages(false, ignoreTTSError, new Global.GetLocalesListListener() {
            @Override
            public void onSuccess(ArrayList<CustomLocale> result) {
                // 成功获取语言列表后，继续初始化翻译器
                global.initializeTranslator(new Translator.InitListener() {
                    @Override
                    public void onInitializationFinished() {
                        // 翻译器初始化成功后，继续初始化语音识别器
                        global.initializeSpeechRecognizer(new NeuralNetworkApi.InitListener() {
                            @Override
                            public void onInitializationFinished() {
                                // 跳转到语音翻译界面
                                if (isVisible) {
                                    startVoiceTranslationActivity();
                                }
                            }

                            @Override
                            public void onError(int[] reasons, long value) {
                                // // 删除语音识别器以确保下次启动时重新加载
                                global.deleteSpeechRecognizer();  //we do this to ensure the restart of the loading of models when the app is restarted
                                LoadingActivity.this.onFailure(reasons, value);
                            }
                        });
                    }

                    @Override
                    public void onError(int[] reasons, long value) {
                        // 删除翻译器以确保下次启动时重新加载
                        global.deleteTranslator();   //we do this to ensure the restart of the loading of models when the app is restarted
                        LoadingActivity.this.onFailure(reasons, value);
                    }
                });
            }

            @Override
            public void onFailure(int[] reasons, long value) {
                LoadingActivity.this.onFailure(reasons, value);
            }
        });
    }

    private void startVoiceTranslationActivity() {
        /**
         * 跳转到语音翻译界面
         */
        if(!START_IMAGE) {
            startingActivity = true;
            Intent intent = new Intent(LoadingActivity.this, VoiceTranslationActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
            finish();
        }else{
            startImageActivity();
        }
    }

    private void startImageActivity() {
        /**
         * 跳转到图片界面
         */
        startingActivity = true;
        Intent intent = new Intent(LoadingActivity.this, ImageActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
        finish();

    }

    private void notifyGoogleTTSErrorDialog() {
        /**
         * 显示 Google TTS（Text-to-Speech）相关的错误提示对话框。
         * 提示用户 Google TTS 初始化失败，并提供重试的机会。
         */
        // 使用 mainHandler.post 确保对话框的创建和显示在主线程中执行。
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                showGoogleTTSErrorDialog(new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        // 用户点击对话框按钮后，调用 initializeApp(true) 方法重新初始化应用，并忽略 TTS 错误。
                        initializeApp(true);
                    }
                });
            }
        });
    }

    public void notifyInternetLack() {
        /**
         * 显示网络连接不足的错误提示对话框。
         * 提示用户网络连接问题，并允许用户选择退出或重试。
         */
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                // 只有在界面可见时才显示对话框，避免在后台显示无效的 UI。
                if (isVisible) {
                    // creation of the dialog.
                    // 使用 AlertDialog.Builder 创建对话框，并设置消息和按钮。
                    AlertDialog.Builder builder = new AlertDialog.Builder(LoadingActivity.this);
                    //builder.setCancelable(true);
                    builder.setMessage(R.string.error_internet_lack_loading);
                    builder.setNegativeButton(R.string.exit, new DialogInterface.OnClickListener() {
                        @Override
                        // 退出：用户点击后调用 finish() 结束当前 Activity。
                        public void onClick(DialogInterface dialog, int which) {
                            finish();
                        }
                    });
                    builder.setPositiveButton(R.string.retry, new DialogInterface.OnClickListener() {
                        @Override
                        // 重试：用户点击后调用 initializeApp(false) 方法重新初始化应用。
                        public void onClick(DialogInterface dialogInterface, int i) {
                            initializeApp(false);
                        }
                    });
                    AlertDialog dialog = builder.create();
                    dialog.setCanceledOnTouchOutside(false);
                    dialog.show();
                }
            }
        });
    }

    public void notifyModelsLoadingError() {
        /**
         * 显示模型加载失败的错误提示对话框。
         * 提示用户模型文件加载失败，并允许用户选择修复或退出。
         */
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                if (isVisible) {
                    // creation of the dialog.
                    AlertDialog.Builder builder = new AlertDialog.Builder(LoadingActivity.this);
                    //builder.setCancelable(true);
                    builder.setMessage(R.string.error_models_loading);
                    builder.setPositiveButton(R.string.fix, new DialogInterface.OnClickListener() {
                        @Override
                        // 修复：用户点击后调用 restartDownload() 方法重新下载模型文件。
                        public void onClick(DialogInterface dialog, int which) {
                            if(global != null){
                                restartDownload();
                            }
                        }
                    });
                    builder.setNegativeButton(R.string.exit, new DialogInterface.OnClickListener() {
                        @Override
                        // 退出：用户点击后调用 finish() 结束当前 Activity。
                        public void onClick(DialogInterface dialog, int which) {
                            finish();
                        }
                    });
                    AlertDialog dialog = builder.create();
                    dialog.setCanceledOnTouchOutside(false);
                    dialog.show();
                }
            }
        });
    }

    private void notifyMissingGoogleTTSDialog() {
        /**
         * 显示缺少 Google TTS 的错误提示对话框。
         * 提示用户 Google TTS 不可用，并提供重试的机会。
         */
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                if (isVisible) {
                    showMissingGoogleTTSDialog(new DialogInterface.OnClickListener() {
                        @Override
                        // 点击对话框按钮后，调用 initializeApp(true) 方法重新初始化应用，并忽略 TTS 错误。
                        public void onClick(DialogInterface dialog, int which) {
                            initializeApp(true);
                        }
                    });
                }
            }
        });
    }


    private void restartDownload(){
        /**
         * 重置下载相关的配置并重新启动下载流程
         */
        // 重置下载相关的共享偏好设置（SharedPreferences）：清除之前的下载状态，确保重新开始下载。
        // 共享偏好设置（SharedPreferences）是 Android 中用于存储轻量级数据的机制，通常用来保存应用的状态或配置信息。
        //we reset all the download shared preferences
        SharedPreferences sharedPreferences = getSharedPreferences("default", Context.MODE_PRIVATE);
        SharedPreferences.Editor editor;
        editor = sharedPreferences.edit();
        editor.putLong("currentDownloadId", -1);    // 将 currentDownloadId 的值设置为 -1，表示当前没有正在进行的下载任务
        editor.apply();
        editor = sharedPreferences.edit();
        editor.putString("lastDownloadSuccess", "");    // 将 lastDownloadSuccess 的值设置为空字符串，表示之前没有成功的下载记录。
        editor.apply();
        editor = sharedPreferences.edit();
        editor.putString("lastTransferSuccess", "");    // 将 lastTransferSuccess 的值设置为空字符串，表示之前没有成功的文件转移记录。
        editor.apply();
        editor = sharedPreferences.edit();
        editor.putString("lastTransferFailure", "");    // 将 lastTransferFailure 的值设置为空字符串，表示之前没有失败的文件转移记录。
        editor.apply();
        // 标记应用为首次启动：通过调用 global.setFirstStart(true)，通知应用需要重新初始化模型下载流程。
        //we restart the download (only the corrupted files will be re-downloaded)
        global.setFirstStart(true);
        // 跳转到下载界面：启动 AccessActivity，引导用户重新下载模型文件
        Intent intent = new Intent(LoadingActivity.this, AccessActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);     // 设置 FLAG_ACTIVITY_NEW_TASK 标志，确保新 Activity 在独立的任务栈中启动。
        startActivity(intent);
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);     // 添加淡入淡出的动画效果
        finish();
    }

    private void onFailure(int[] reasons, long value) {
        for (int aReason : reasons) {
            switch (aReason) {
                case ErrorCodes.ERROR_LOADING_MODEL:
                    showingError = true;
                    notifyModelsLoadingError();
                    break;
                case ErrorCodes.SAFETY_NET_EXCEPTION:
                case ErrorCodes.MISSED_CONNECTION:
                    showingError = true;
                    notifyInternetLack();
                    break;
                case ErrorCodes.MISSING_GOOGLE_TTS:
                    showingError = true;
                    notifyMissingGoogleTTSDialog();
                    break;
                case ErrorCodes.GOOGLE_TTS_ERROR:
                    showingError = true;
                    notifyGoogleTTSErrorDialog();
                    break;
                default:
                    onError(aReason, value);
                    break;
            }
        }
    }
}
