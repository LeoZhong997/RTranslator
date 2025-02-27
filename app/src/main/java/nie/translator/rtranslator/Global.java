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

import android.app.ActivityManager;
import android.app.Application;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.DefaultLifecycleObserver;
import androidx.lifecycle.LifecycleOwner;

import java.io.File;
import java.util.ArrayList;

import nie.translator.rtranslator.access.AccessActivity;
import nie.translator.rtranslator.tools.CustomLocale;
import nie.translator.rtranslator.tools.TTS;
import nie.translator.rtranslator.voice_translation._conversation_mode.communication.ConversationBluetoothCommunicator;
import nie.translator.rtranslator.bluetooth.BluetoothCommunicator;
import nie.translator.rtranslator.bluetooth.Peer;
import nie.translator.rtranslator.voice_translation._conversation_mode.communication.recent_peer.RecentPeersDataManager;
import nie.translator.rtranslator.voice_translation.neural_networks.NeuralNetworkApi;
import nie.translator.rtranslator.voice_translation.neural_networks.translation.Translator;
import nie.translator.rtranslator.voice_translation.neural_networks.voice.Recognizer;
import nie.translator.rtranslator.voice_translation.neural_networks.voice.Recorder;


public class Global extends Application implements DefaultLifecycleObserver {
    /**
     * Global 类，是 Android 应用中的一个全局应用类（继承自 Application）。
     * 为整个应用程序提供全局的状态管理、资源初始化和工具方法。
     * 功能：
     *  （1）全局状态管理：存储应用的全局变量（如语言设置、蓝牙通信器、翻译器等）。
     *  （2）资源初始化：在应用启动时初始化必要的组件（如通知通道、内存信息等）。
     *  （3）工具方法：提供一些通用的工具方法（如获取设备内存、检查网络连接等）。
     */
    // 定义多个成员变量，用于存储应用的全局状态和组件。
    // 包括语言设置、蓝牙通信器、翻译器、语音识别器、API 密钥文件名、麦克风灵敏度等。
    // 提供全局访问点，便于在应用的不同部分共享数据和组件。
    private ArrayList<CustomLocale> languages = new ArrayList<>();
    private ArrayList<CustomLocale> translatorLanguages = new ArrayList<>();
    private ArrayList<CustomLocale> ttsLanguages = new ArrayList<>();
    private CustomLocale language;
    private CustomLocale firstLanguage;
    private CustomLocale secondLanguage;
    private CustomLocale firstTextLanguage;
    private CustomLocale secondTextLanguage;
    private RecentPeersDataManager recentPeersDataManager;
    private ConversationBluetoothCommunicator bluetoothCommunicator;
    private Translator translator;
    private Recognizer speechRecognizer;
    private String name = "";
    private String apiKeyFileName = "";
    private int micSensitivity = -1;
    private int speechTimeout = -1;
    private int prevVoiceDuration = -1;
    private int amplitudeThreshold = Recorder.DEFAULT_AMPLITUDE_THRESHOLD;
    private boolean isForeground = false;
    @Nullable
    private AccessActivity accessActivity;
    private Handler mainHandler;
    private static Handler mHandler = new Handler();
    private final Object lock = new Object();

    @Override
    public void onCreate() {
        /**
         * 在应用启动时调用，用于初始化全局状态和组件。
         * 设置应用的初始状态，例如创建通知通道、加载语言列表等。
         */
        super.onCreate();
        mainHandler = new Handler(Looper.getMainLooper());
        recentPeersDataManager = new RecentPeersDataManager(this);
        //initializeBluetoothCommunicator();
        getMicSensitivity();
        createNotificationChannel();
    }

    public void initializeTranslator(NeuralNetworkApi.InitListener initListener){
        /**
         * 初始化翻译器 (Translator)。
         *  确保翻译器只被初始化一次，避免重复创建。
         *  提供回调机制，通知调用者翻译器的初始化状态。
         */
        if(translator == null) {
            // 如果翻译器尚未创建，则创建一个新的 Translator 实例
            translator = new Translator(this, Translator.NLLB_CACHE, initListener);
        }else{
            // 如果翻译器已经存在，则直接调用 initListener.onInitializationFinished() 表示初始化已完成。
            initListener.onInitializationFinished();
        }
    }

    public void initializeSpeechRecognizer(NeuralNetworkApi.InitListener initListener){
        /**
         * 初始化语音识别器 (Recognizer)。
         *  确保语音识别器只被初始化一次，避免重复创建。
         *  提供回调机制，通知调用者语音识别器的初始化状态。
         */
        if(speechRecognizer == null) {
            // 如果语音识别器尚未创建，则创建一个新的 Recognizer 实例
            speechRecognizer = new Recognizer(this, true, initListener);
        }else{
            // 如果语音识别器已经存在，则直接调用 initListener.onInitializationFinished() 表示初始化已完成。
            initListener.onInitializationFinished();
        }
    }

    public void initializeBluetoothCommunicator(){
        /**
         * 初始化蓝牙通信器 (ConversationBluetoothCommunicator)。
         *  确保蓝牙通信器只被初始化一次，避免重复创建。
         *  配置蓝牙通信器的初始参数，例如设备名称和通信策略。
         */
        if(bluetoothCommunicator == null){
            // 如果蓝牙通信器尚未创建，则创建一个新的 ConversationBluetoothCommunicator 实例
            bluetoothCommunicator = new ConversationBluetoothCommunicator(this, getName(), BluetoothCommunicator.STRATEGY_P2P_WITH_RECONNECTION);
        }
    }

    @Nullable
    public ConversationBluetoothCommunicator getBluetoothCommunicator() {
        /**
         * 返回蓝牙通信器的实例。
         */
        return bluetoothCommunicator;
    }

    public void resetBluetoothCommunicator() {
        /**
         * 重置蓝牙通信器。
         *  用于处理蓝牙通信器的状态异常或连接问题。
         *  确保蓝牙通信器始终处于可用状态。
         */
        // 调用 destroy 方法销毁当前的蓝牙通信器，并在销毁完成后重新创建一个新的实例。
        bluetoothCommunicator.destroy(new BluetoothCommunicator.DestroyCallback() {
            @Override
            public void onDestroyed() {
                bluetoothCommunicator = new ConversationBluetoothCommunicator(Global.this, getName(), BluetoothCommunicator.STRATEGY_P2P_WITH_RECONNECTION);
            }
        });
    }

    public void getLanguages(final boolean recycleResult, boolean ignoreTTSError, final GetLocalesListListener responseListener) {
        /**
         * 获取应用支持的语言列表，并确保这些语言与语音识别器（Speech Recognizer）、翻译器（Translator）以及文本转语音（TTS）系统兼容。
         * 功能：
         *  （1）语言获取：从多个组件（如 TTS、翻译器、语音识别器）中获取支持的语言列表。
         *  （2）兼容性检查：筛选出同时兼容语音识别器和翻译器的语言。
         *  （3）错误处理：处理可能的错误（如 TTS 错误），并根据配置决定是否忽略这些错误。
         *  （4）回调机制：通过回调接口 GetLocalesListListener 向调用者返回成功或失败的结果。
         * 参数：
         *  recycleResult：如果为 true，且已有语言列表（languages 不为空），则直接返回缓存的语言列表，避免重复加载。如果为 false，则强制重新加载语言列表。
         *  ignoreTTSError：如果为 true，即使 TTS 加载失败，也会继续尝试加载翻译器和语音识别器的语言。如果为 false，TTS 加载失败时会直接返回错误。
         *  responseListener：回调接口，用于向调用者返回成功或失败的结果。

         */
        if (recycleResult && !languages.isEmpty()) {
            responseListener.onSuccess(languages);  // 缓存结果复用
        } else {
            // 获取 TTS 支持的语言列表
            TTS.getSupportedLanguages(this, new TTS.SupportedLanguagesListener() {    //we load TTS languages to catch eventual TTS errors
                @Override
                // 处理 TTS 语言加载成功的情况
                public void onLanguagesListAvailable(ArrayList<CustomLocale> ttsLanguages) {
                    // 获取翻译器支持的语言列表
                    getTranslatorLanguages(recycleResult, new GetLocalesListListener() {
                        @Override
                        // 处理翻译器语言加载成功的情况
                        public void onSuccess(ArrayList<CustomLocale> translatorLanguages) {
                            // 获取语音识别器支持的语言列表
                            ArrayList<CustomLocale> speechRecognizerLanguages = Recognizer.getSupportedLanguages(Global.this);
                            //we return only the languages compatible with the speech recognizer and the translator
                            // 遍历翻译器支持的语言列表，筛选出同时兼容语音识别器的语言。确保最终的语言列表既支持翻译，也支持语音识别。
                            final ArrayList<CustomLocale> compatibleLanguages = new ArrayList<>();
                            for (CustomLocale translatorLanguage : translatorLanguages) {
                                if (CustomLocale.containsLanguage(speechRecognizerLanguages, translatorLanguage)) {
                                    compatibleLanguages.add(translatorLanguage);
                                }
                            }
                            languages = compatibleLanguages;
                            responseListener.onSuccess(compatibleLanguages);
                        }

                        @Override
                        // 处理翻译器语言加载失败的情况
                        public void onFailure(int[] reasons, long value) {
                            responseListener.onFailure(reasons, 0);
                        }
                    });
                }

                @Override
                // 处理 TTS 语言加载失败的情况
                public void onError(int reason) {
                    if(ignoreTTSError) {
                        // 忽略 TTS 错误，继续加载翻译器和语音识别器的语言
                        getTranslatorLanguages(recycleResult, new GetLocalesListListener() {
                            @Override
                            public void onSuccess(ArrayList<CustomLocale> translatorLanguages) {
                                ArrayList<CustomLocale> speechRecognizerLanguages = Recognizer.getSupportedLanguages(Global.this);
                                //we return only the languages compatible with the speech recognizer and the translator (without loading TTS languages)
                                final ArrayList<CustomLocale> compatibleLanguages = new ArrayList<>();
                                for (CustomLocale translatorLanguage : translatorLanguages) {
                                    if (CustomLocale.containsLanguage(speechRecognizerLanguages, translatorLanguage)) {
                                        compatibleLanguages.add(translatorLanguage);
                                    }
                                }
                                languages = compatibleLanguages;
                                responseListener.onSuccess(compatibleLanguages);
                            }

                            @Override
                            public void onFailure(int[] reasons, long value) {
                                responseListener.onFailure(reasons, 0);
                            }
                        });
                    }else{
                        responseListener.onFailure(new int[]{reason}, 0);
                    }
                }
            });
        }
    }

    public void getTranslatorLanguages(final boolean recycleResult, final GetLocalesListListener responseListener) {
        /**
         * 获取翻译器支持的语言列表。
         * 如果启用了缓存复用（recycleResult），则直接返回已有的语言列表；否则重新加载翻译器支持的语言。
         */
        if (recycleResult && !translatorLanguages.isEmpty()) {
            responseListener.onSuccess(translatorLanguages);
        } else {
            ArrayList<CustomLocale> languages = Translator.getSupportedLanguages(Global.this, Translator.NLLB);
            translatorLanguages = languages;
            responseListener.onSuccess(languages);
        }
    }

    public void getTTSLanguages(final boolean recycleResult, final GetLocalesListListener responseListener){
        /**
         * 获取 TTS 支持的语言列表。
         * 如果启用了缓存复用（recycleResult），则直接返回已有的语言列表；否则通过异步方式加载 TTS 支持的语言。
         */
        if(recycleResult && !ttsLanguages.isEmpty()){
            responseListener.onSuccess(ttsLanguages);
        }else{
            TTS.getSupportedLanguages(this, new TTS.SupportedLanguagesListener() {    //we load TTS languages to catch eventual TTS errors
                @Override
                public void onLanguagesListAvailable(ArrayList<CustomLocale> ttsLanguages) {
                    Global.this.ttsLanguages = ttsLanguages;
                    responseListener.onSuccess(ttsLanguages);
                }

                @Override
                public void onError(int reason) {
                    responseListener.onSuccess(new ArrayList<>());
                }
            });
        }
    }

    public Translator getTranslator() {
        return translator;
    }

    public void deleteTranslator(){
        translator = null;
    }

    public Recognizer getSpeechRecognizer() {
        return speechRecognizer;
    }

    public void deleteSpeechRecognizer(){
        speechRecognizer = null;
    }

    public boolean isForeground() {
        /**
         * 表示应用当前是否处于前台。
         * 提供全局访问点，便于其他部分的代码检查应用的前台/后台状态。
         */
        return isForeground;
    }

    @Override
    public void onStop(@NonNull LifecycleOwner owner) {
        /**
         * 当应用进入后台时调用，将 isForeground 设置为 false。
         * 跟踪应用的生命周期，确保在应用进入后台时更新状态。
         */
        DefaultLifecycleObserver.super.onStop(owner);
        //App in background
        isForeground = false;
    }

    @Override
    public void onStart(@NonNull LifecycleOwner owner) {
        /**
         * 当应用进入前台时调用，将 isForeground 设置为 true。
         * 跟踪应用的生命周期，确保在应用进入前台时更新状态。
         */
        DefaultLifecycleObserver.super.onStart(owner);
        // App in foreground
        isForeground = true;
    }

    @Nullable
    public AccessActivity getRunningAccessActivity() {
        /**
         * 返回当前运行的 AccessActivity 实例。
         * 提供全局访问点，便于其他部分的代码获取当前的 AccessActivity。
         */
        return accessActivity;
    }

    public void setAccessActivity(@Nullable AccessActivity accessActivity) {
        /**
         * 设置当前运行的 AccessActivity 实例。
         * 更新全局变量 accessActivity，确保其始终指向当前的 AccessActivity。
         */
        this.accessActivity = accessActivity;
    }

    public interface GetLocalesListListener {
        /**
         * 定义了一个回调接口，用于在异步操作中返回成功或失败的结果。
         *  onSuccess：当操作成功时调用，返回支持的语言列表（ArrayList<CustomLocale>）。
         *  onFailure：当操作失败时调用，返回失败原因（int[] reasons）和附加值（long value）。
         * 提供统一的回调机制，便于调用者处理异步操作的结果。
         */
        void onSuccess(ArrayList<CustomLocale> result);

        void onFailure(int[] reasons, long value);
    }

    public void getLanguage(final boolean recycleResult, final GetLocaleListener responseListener) {
        /**
         * 获取应用当前使用的语言（CustomLocale 对象），并确保该语言在支持的语言列表中。
         * 如果当前语言不可用，则会回退到默认语言或预定义的备用语言（如英语）。
         */
        getLanguages(true, true, new GetLocalesListListener() {
            @Override
            public void onSuccess(ArrayList<CustomLocale> languages) {
                // 根据缓存、共享偏好设置或默认值，确定当前使用的语言。
                CustomLocale predefinedLanguage = CustomLocale.getDefault();
                CustomLocale language = null;
                if (recycleResult && Global.this.language != null) {
                    language = Global.this.language;
                } else {
                    SharedPreferences sharedPreferences = Global.this.getSharedPreferences("default", Context.MODE_PRIVATE);
                    String code = sharedPreferences.getString("language", predefinedLanguage.getCode());
                    if (code != null) {
                        language = CustomLocale.getInstance(code);
                    }
                }

                // 确保当前语言在支持的语言列表中；如果不在，则回退到默认语言或备用语言。
                int index = CustomLocale.search(languages, language);
                if (index != -1) {
                    language = languages.get(index);
                } else {
                    int index2 = CustomLocale.search(languages, predefinedLanguage);
                    if (index2 != -1) {
                        language = predefinedLanguage;
                    } else {
                        language = new CustomLocale("en");
                    }
                }

                Global.this.language = language;
                responseListener.onSuccess(language);
            }

            @Override
            public void onFailure(int[] reasons, long value) {
                responseListener.onFailure(reasons, value);
            }
        });
    }

    public void getFirstLanguage(final boolean recycleResult, final GetLocaleListener responseListener) {
        /**
         * 获取应用的首选语言（firstLanguage），并确保该语言在支持的语言列表中。
         * 如果当前首选语言不可用，则会回退到默认语言或预定义的备用语言（如英语）。
         */
        // 调用 getLanguages 方法获取应用支持的语言列表
        getLanguages(true, true, new GetLocalesListListener() {
            @Override
            public void onSuccess(final ArrayList<CustomLocale> languages) {
                // 调用 getLanguage 方法获取当前使用的语言（CustomLocale 对象）
                getLanguage(true, new GetLocaleListener() {
                    @Override
                    public void onSuccess(CustomLocale predefinedLanguage) {
                        // 根据缓存、共享偏好设置或默认值，确定首选语言。
                        CustomLocale language = null;
                        if (recycleResult && Global.this.firstLanguage != null) {
                            // 如果 recycleResult 为 true 且全局变量 Global.this.firstLanguage 不为空，则直接复用当前首选语言。
                            language = Global.this.firstLanguage;
                        } else {
                            // 如果未复用缓存首选语言，则从共享偏好设置中读取首选语言代码（键为 "firstLanguage"）
                            SharedPreferences sharedPreferences = Global.this.getSharedPreferences("default", Context.MODE_PRIVATE);
                            String code = sharedPreferences.getString("firstLanguage", predefinedLanguage.getCode());
                            if (code != null) {
                                // 如果语言代码存在，则通过 CustomLocale.getInstance 创建对应的 CustomLocale 对象。
                                language = CustomLocale.getInstance(code);
                            }
                        }
                        // 如果共享偏好中没有首选语言代码，则 language 保持为 null。

                        // 确保首选语言在支持的语言列表中；如果不在，则回退到默认语言或备用语言。
                        int index = CustomLocale.search(languages, language);
                        if (index != -1) {
                            language = languages.get(index);
                        } else {
                            int index2 = CustomLocale.search(languages, predefinedLanguage);
                            if (index2 != -1) {
                                language = predefinedLanguage;
                            } else {
                                language = new CustomLocale("en");
                            }
                        }

                        Global.this.firstLanguage = language;
                        responseListener.onSuccess(language);
                    }

                    @Override
                    public void onFailure(int[] reasons, long value) {
                        responseListener.onFailure(reasons, value);
                    }
                });
            }

            @Override
            public void onFailure(int[] reasons, long value) {
                responseListener.onFailure(reasons, value);
            }
        });
    }

    public void getSecondLanguage(final boolean recycleResult, final GetLocaleListener responseListener) {
        /**
         * 获取应用的第二语言（secondLanguage），并确保该语言在支持的语言列表中。
         * 如果当前第二语言不可用，则会回退到默认语言或预定义的备用语言（如英语）。
         */
        // 调用 getLanguages 方法获取应用支持的语言列表。
        getLanguages(true, true, new GetLocalesListListener() {
            @Override
            public void onSuccess(ArrayList<CustomLocale> languages) {
                // 根据缓存、共享偏好设置或默认值，确定第二语言。
                CustomLocale predefinedLanguage = CustomLocale.getDefault();
                CustomLocale language = null;
                if (recycleResult && Global.this.secondLanguage != null) {
                    language = Global.this.secondLanguage;
                } else {
                    SharedPreferences sharedPreferences = Global.this.getSharedPreferences("default", Context.MODE_PRIVATE);
                    String code = sharedPreferences.getString("secondLanguage", null);
                    if (code != null) {
                        language = CustomLocale.getInstance(code);
                    }
                }

                // 确保第二语言在支持的语言列表中；如果不在，则回退到默认语言或备用语言。
                int index = CustomLocale.search(languages, language);
                if (index != -1) {
                    language = languages.get(index);
                } else {
                    language = new CustomLocale("en");
                }

                Global.this.secondLanguage = language;
                responseListener.onSuccess(language);
            }

            @Override
            public void onFailure(int[] reasons, long value) {
                responseListener.onFailure(reasons, value);
            }
        });
    }

    public void getFirstAndSecondLanguages(final boolean recycleResult, final GetTwoLocaleListener responseListener){
        /**
         * 同时获取应用的首选语言（firstLanguage）和第二语言（secondLanguage），并确保它们都在支持的语言列表中。
         * 如果获取成功，则通过回调接口返回这两个语言对象；如果失败，则返回失败原因。
         * 提供统一的接口，无需分别调用多个方法。
         */
        getFirstLanguage(recycleResult, new GetLocaleListener() {
            @Override
            public void onSuccess(CustomLocale result1) {
                getSecondLanguage(recycleResult, new GetLocaleListener() {
                    @Override
                    public void onSuccess(CustomLocale result2) {
                        responseListener.onSuccess(result1, result2);
                    }

                    @Override
                    public void onFailure(int[] reasons, long value) {
                        responseListener.onFailure(reasons, value);
                    }
                });
            }

            @Override
            public void onFailure(int[] reasons, long value) {
                responseListener.onFailure(reasons, value);
            }
        });
    }

    public void getFirstTextLanguage(final boolean recycleResult, final GetLocaleListener responseListener) {
        /**
         * 获取应用的第一文本语言，并确保它们在翻译器支持的语言列表中。
         * 如果当前语言不可用，则会回退到默认语言或预定义的备用语言（如英语）。
         */
        // 获取翻译器支持的语言列表
        getTranslatorLanguages(true, new GetLocalesListListener() {
            @Override
            public void onSuccess(final ArrayList<CustomLocale> languages) {
                 // 获取当前语言作为参考
                getLanguage(true, new GetLocaleListener() {
                    @Override
                    public void onSuccess(CustomLocale predefinedLanguage) {
                        CustomLocale language = null;
                        // 确定第一文本语言
                        if (recycleResult && Global.this.firstTextLanguage != null) {
                            language = Global.this.firstTextLanguage;
                        } else {
                            SharedPreferences sharedPreferences = Global.this.getSharedPreferences("default", Context.MODE_PRIVATE);
                            String code = sharedPreferences.getString("firstTextLanguage", predefinedLanguage.getCode());
                            if (code != null) {
                                language = CustomLocale.getInstance(code);
                            }
                        }

                        // 检查语言是否在支持的语言列表中
                        int index = CustomLocale.search(languages, language);
                        if (index != -1) {
                            language = languages.get(index);
                        } else {
                            int index2 = CustomLocale.search(languages, predefinedLanguage);
                            if (index2 != -1) {
                                language = predefinedLanguage;
                            } else {
                                language = new CustomLocale("en");
                            }
                        }

                        // 更新全局变量并返回结果
                        Global.this.firstTextLanguage = language;
                        responseListener.onSuccess(language);
                    }

                    @Override
                    public void onFailure(int[] reasons, long value) {
                        responseListener.onFailure(reasons, value);
                    }
                });
            }

            @Override
            public void onFailure(int[] reasons, long value) {
                responseListener.onFailure(reasons, value);
            }
        });
    }

    public void getSecondTextLanguage(final boolean recycleResult, final GetLocaleListener responseListener) {
        /**
         * 获取应用的第二文本语言，并确保它们在翻译器支持的语言列表中。
         * 如果当前语言不可用，则会回退到默认语言或预定义的备用语言（如英语）。
         */
        getTranslatorLanguages(true, new GetLocalesListListener() {
            @Override
            public void onSuccess(ArrayList<CustomLocale> languages) {
                CustomLocale predefinedLanguage = CustomLocale.getDefault();
                CustomLocale language = null;
                if (recycleResult && Global.this.secondTextLanguage != null) {
                    language = Global.this.secondTextLanguage;
                } else {
                    SharedPreferences sharedPreferences = Global.this.getSharedPreferences("default", Context.MODE_PRIVATE);
                    String code = sharedPreferences.getString("secondTextLanguage", null);
                    if (code != null) {
                        language = CustomLocale.getInstance(code);
                    }
                }

                int index = CustomLocale.search(languages, language);
                if (index != -1) {
                    language = languages.get(index);
                } else {
                    language = new CustomLocale("en");
                }

                Global.this.secondTextLanguage = language;
                responseListener.onSuccess(language);
            }

            @Override
            public void onFailure(int[] reasons, long value) {
                responseListener.onFailure(reasons, value);
            }
        });
    }

    public void getFirstAndSecondTextLanguages(final boolean recycleResult, final GetTwoLocaleListener responseListener){
        /**
         * 同时获取第一文本语言和第二文本语言，并通过回调接口返回两个语言对象。
         * 如果任何一步失败，则返回失败原因。
         */
        // 获取第一文本语言
        getFirstTextLanguage(recycleResult, new GetLocaleListener() {
            @Override
            public void onSuccess(CustomLocale result1) {
                // 获取第二文本语言
                getSecondTextLanguage(recycleResult, new GetLocaleListener() {
                    @Override
                    public void onSuccess(CustomLocale result2) {
                        // 返回两个语言对象
                        responseListener.onSuccess(result1, result2);
                    }

                    @Override
                    public void onFailure(int[] reasons, long value) {
                        responseListener.onFailure(reasons, value);
                    }
                });
            }

            @Override
            public void onFailure(int[] reasons, long value) {
                responseListener.onFailure(reasons, value);
            }
        });
    }

    public interface GetLocaleListener {
        /**
         * 回调接口，用于处理单个语言获取的结果
         */
        // 当语言成功获取时调用，返回一个 CustomLocale 对象，表示获取到的语言。
        void onSuccess(CustomLocale result);

        // 当语言获取失败时调用，返回失败的原因（reasons）和附加值（value）。
        void onFailure(int[] reasons, long value);
    }

    public interface GetTwoLocaleListener {
        /**
         * 回调接口，用于处理两个语言获取的结果。
         */
        // 当两个语言都成功获取时调用，返回两个 CustomLocale 对象，分别表示第一语言和第二语言。
        void onSuccess(CustomLocale language1, CustomLocale language2);

        // 当语言获取失败时调用，返回失败的原因（reasons）和附加值（value）。
        void onFailure(int[] reasons, long value);
    }

    public void setLanguage(CustomLocale language) {
        /**
         * 将指定的语言对象保存到全局变量中，并通过 SharedPreferences 持久化存储语言代码。
         * 设置当前使用的语言（language），并将其代码保存到 SharedPreferences 中。
         */
        this.language = language;
        SharedPreferences sharedPreferences = this.getSharedPreferences("default", Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString("language", language.getCode());   // 将语言代码（language.getCode()）存储到键 "language" 中。
        editor.apply();     // 调用 apply() 方法异步保存数据。
    }

    public void setFirstLanguage(CustomLocale language) {
        /**
         * 将指定的语言对象保存到全局变量中，并通过 SharedPreferences 持久化存储语言代码。
         */
        this.firstLanguage = language;
        SharedPreferences sharedPreferences = this.getSharedPreferences("default", Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString("firstLanguage", language.getCode());
        editor.apply();
    }

    public void setSecondLanguage(CustomLocale language) {
        /**
         * 将指定的语言对象保存到全局变量中，并通过 SharedPreferences 持久化存储语言代码。
         */
        this.secondLanguage = language;
        SharedPreferences sharedPreferences = this.getSharedPreferences("default", Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString("secondLanguage", language.getCode());
        editor.apply();
    }

    public void setFirstTextLanguage(CustomLocale language) {
        /**
         * 将指定的语言对象保存到全局变量中，并通过 SharedPreferences 持久化存储语言代码。
         */
        this.firstTextLanguage = language;
        SharedPreferences sharedPreferences = this.getSharedPreferences("default", Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString("firstTextLanguage", language.getCode());
        editor.apply();
    }

    public void setSecondTextLanguage(CustomLocale language) {
        /**
         * 将指定的语言对象保存到全局变量中，并通过 SharedPreferences 持久化存储语言代码。
         */
        this.secondTextLanguage = language;
        SharedPreferences sharedPreferences = this.getSharedPreferences("default", Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString("secondTextLanguage", language.getCode());
        editor.apply();
    }


    public int getAmplitudeThreshold() {
        /**
         * 返回当前的振幅阈值（amplitudeThreshold）。
         */
        return amplitudeThreshold;
    }


    public int getMicSensitivity() {
        /**
         * 获取麦克风灵敏度（micSensitivity）。
         * 如果尚未初始化，则从 SharedPreferences 中读取默认值（默认为 50），并调用 setAmplitudeThreshold 更新振幅阈值。
         */
        if (micSensitivity == -1) {
            final SharedPreferences sharedPreferences = this.getSharedPreferences("default", Context.MODE_PRIVATE);
            micSensitivity = sharedPreferences.getInt("micSensibility", 50);
            setAmplitudeThreshold(micSensitivity);
        }
        return micSensitivity;
    }

    public void setMicSensitivity(int value) {
        /**
         * 设置麦克风灵敏度，并将其保存到 SharedPreferences 中，同时更新振幅阈值。
         */
        micSensitivity = value;
        setAmplitudeThreshold(micSensitivity);
        final SharedPreferences sharedPreferences = this.getSharedPreferences("default", Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putInt("micSensibility", value);
        editor.apply();
    }

    public int getSpeechTimeout() {
        /**
         * 获取语音超时时间（speechTimeout）。
         * 如果尚未初始化，则从 SharedPreferences 中读取默认值（默认为 Recorder.DEFAULT_SPEECH_TIMEOUT_MILLIS）。
         */
        if (speechTimeout == -1) {
            final SharedPreferences sharedPreferences = this.getSharedPreferences("default", Context.MODE_PRIVATE);
            speechTimeout = sharedPreferences.getInt("speechTimeout", Recorder.DEFAULT_SPEECH_TIMEOUT_MILLIS);
        }
        return speechTimeout;
    }

    public void setSpeechTimeout(int value) {
        /**
         * 设置语音超时时间，并将其保存到 SharedPreferences 中。
         */
        speechTimeout = value;
        final SharedPreferences sharedPreferences = this.getSharedPreferences("default", Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putInt("speechTimeout", value);
        editor.apply();
    }

    public int getPrevVoiceDuration() {
        /**
         * 获取前语音持续时间（prevVoiceDuration）。
         * 如果尚未初始化，则从 SharedPreferences 中读取默认值（默认为 Recorder.DEFAULT_PREV_VOICE_DURATION）。
         */
        if (prevVoiceDuration == -1) {
            final SharedPreferences sharedPreferences = this.getSharedPreferences("default", Context.MODE_PRIVATE);
            prevVoiceDuration = sharedPreferences.getInt("prevVoiceDuration", Recorder.DEFAULT_PREV_VOICE_DURATION);
        }
        return prevVoiceDuration;
    }

    public void setPrevVoiceDuration(int value) {
        /**
         * 设置前语音持续时间，并将其保存到 SharedPreferences 中。
         */
        prevVoiceDuration = value;
        final SharedPreferences sharedPreferences = this.getSharedPreferences("default", Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putInt("prevVoiceDuration", value);
        editor.apply();
    }

    private void setAmplitudeThreshold(int micSensitivity) {
        /**
         * 根据麦克风灵敏度（micSensitivity）计算并设置振幅阈值（amplitudeThreshold）。
         */
        // 将麦克风灵敏度转换为百分比形式（amplitudePercentage），范围为 [0, 1]。
        float amplitudePercentage = 1f - (micSensitivity / 100f);
        // 计算振幅阈值
        if (amplitudePercentage < 0.5f) {
            amplitudeThreshold = Math.round(Recorder.MIN_AMPLITUDE_THRESHOLD + ((Recorder.DEFAULT_AMPLITUDE_THRESHOLD - Recorder.MIN_AMPLITUDE_THRESHOLD) * (amplitudePercentage * 2)));
        } else {
            amplitudeThreshold = Math.round(Recorder.DEFAULT_AMPLITUDE_THRESHOLD + ((Recorder.MAX_AMPLITUDE_THRESHOLD - Recorder.DEFAULT_AMPLITUDE_THRESHOLD) * ((amplitudePercentage - 0.5F) * 2)));
        }
    }

    public String getName() {
        /**
         * 获取用户的名称（name）。如果尚未初始化，则从 SharedPreferences 中读取默认值（默认为 "user"）。
         */
        // 检查全局变量 name 是否为空字符串
        if (name.length() == 0) {
            // 从 SharedPreferences 中读取键 "name" 的值。
            final SharedPreferences sharedPreferences = this.getSharedPreferences("default", Context.MODE_PRIVATE);
            name = sharedPreferences.getString("name", "user");     // 如果没有找到该键，则使用默认值 "user"。
        }
        return name;
    }

    public void setName(String savedName) {
        /**
         * 设置用户的名称，并将其保存到 SharedPreferences 中。同时更新蓝牙通信器中的名称（如果存在）。
         */
        name = savedName;
        // 使用 SharedPreferences 存储新的名称
        final SharedPreferences sharedPreferences = this.getSharedPreferences("default", Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString("name", savedName);
        editor.apply();
        // 如果存在蓝牙通信器实例，则调用其 setName 方法同步更新名称
        if(getBluetoothCommunicator() != null) {
            getBluetoothCommunicator().setName(savedName);  //si aggiorna il nome anche per il comunicator
        }
    }

    public Peer getMyPeer() {
        /**
         * 返回当前用户的 Peer 对象，其中包含用户的名称（通过 getName 获取）和其他相关信息。
         */
        return new Peer(null, getName(), false);
    }

    public abstract static class MyPeerListener {
        public abstract void onSuccess(Peer myPeer);

        public void onFailure(int[] reasons, long value) {
        }
    }

    public void getMyID(final MyIDListener responseListener) {
        /**
         * 获取设备的唯一标识符（ANDROID_ID），并通过回调接口返回结果
         */
        responseListener.onSuccess(Settings.Secure.getString(this.getContentResolver(), Settings.Secure.ANDROID_ID));
    }

    public abstract static class MyIDListener {
        /**
         * 回调接口，用于处理设备 ID 获取的结果
         */
        public abstract void onSuccess(String id);

        public void onFailure(int[] reasons, long value) {
        }
    }

    public RecentPeersDataManager getRecentPeersDataManager() {
        /**
         * 返回最近联系人数据管理器（recentPeersDataManager）的实例。
         */
        return recentPeersDataManager;
    }

    public abstract static class ResponseListener {
        public void onSuccess() {

        }

        public void onFailure(int[] reasons, long value) {
        }
    }

    public boolean isFirstStart() {
        /**
         * 判断应用是否是第一次启动。如果是第一次启动，则返回 true；否则返回 false。
         */
        final SharedPreferences sharedPreferences = this.getSharedPreferences("default", Context.MODE_PRIVATE);
        return sharedPreferences.getBoolean("firstStart", true);
    }

    public void setFirstStart(boolean firstStart) {
        /**
         * 设置应用的首次启动状态，并将其保存到 SharedPreferences 中。
         */
        final SharedPreferences sharedPreferences = this.getSharedPreferences("default", Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putBoolean("firstStart", firstStart);
        editor.apply();
    }


    private void createNotificationChannel(){
        /**
         * 创建一个通知通道，用于在 Android 8.0 及以上版本中显示通知。
         * 确保应用的通知能够在现代 Android 版本中正常显示。
         */
        String channelID = "service_background_notification";
        String channelName = getResources().getString(R.string.notification_channel_name);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel notificationChannel = new NotificationChannel(channelID, channelName, NotificationManager.IMPORTANCE_LOW);
            NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            notificationManager.createNotificationChannel(notificationChannel);
        }
    }

    /**
     * Returns the total RAM size of the device in MB
     */
    public long getTotalRamSize(){
        /**
         * 获取设备的总 RAM 大小（以 MB 为单位）。
         */
        ActivityManager actManager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        ActivityManager.MemoryInfo memInfo = new ActivityManager.MemoryInfo();
        actManager.getMemoryInfo(memInfo);
        long totalMemory = memInfo.totalMem / 1000000L;
        android.util.Log.i("memory", "Total memory: " + totalMemory);
        return totalMemory;
    }

    /**
     * Returns the available RAM size of the device in MB
     */
    public long getAvailableRamSize(){
        /**
         * 获取设备的可用 RAM 大小（以 MB 为单位）。
         */
        ActivityManager actManager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        ActivityManager.MemoryInfo memInfo = new ActivityManager.MemoryInfo();
        actManager.getMemoryInfo(memInfo);
        long totalMemory = memInfo.availMem;
        android.util.Log.i("memory", "Total memory: " + totalMemory);
        return totalMemory / 1000000L;
    }

    /**
     * Returns the available internal memory space in MB
     */
    public long getAvailableInternalMemorySize() {
        /**
         * 获取设备内部存储的可用空间（以 MB 为单位）
         */
        File internalFilesDir = this.getFilesDir();
        if(internalFilesDir != null) {
            long freeMBInternal = new File(internalFilesDir.getAbsoluteFile().toString()).getFreeSpace() / 1000000L;
            return freeMBInternal;
        }
        return -1;
    }

    /**
     * Returns the available external memory space in MB
     */
    public long getAvailableExternalMemorySize() {
        /**
         * 获取设备外部存储的可用空间（以 MB 为单位）
         */
        File externalFilesDir = this.getExternalFilesDir(null);
        if(externalFilesDir != null) {
            long freeMBExternal = new File(externalFilesDir.getAbsoluteFile().toString()).getFreeSpace() / 1000000L;
            return freeMBExternal;
        }
        return -1;
    }



    public boolean isNetworkOnWifi() {
        /**
         * 检查设备是否已连接到 Wi-Fi 网络。
         */
        WifiManager wifi_m = (WifiManager) getSystemService(Context.WIFI_SERVICE);
        if (wifi_m.isWifiEnabled()) { 
            // if wifi is on
            WifiInfo wifi_i = wifi_m.getConnectionInfo();
            if (wifi_i.getNetworkId() == -1) {
                return false; // Not connected to any wifi device
            }
            return true; // Connected to some wifi device
        } else {
            return false; // user turned off wifi
        }
    }
}

