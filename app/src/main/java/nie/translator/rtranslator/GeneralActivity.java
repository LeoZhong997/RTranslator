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

import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.FragmentActivity;

import javax.annotation.Nullable;


public abstract class GeneralActivity extends FragmentActivity {
    /**
     * GeneralActivity 抽象类，是 FragmentActivity 的子类
     * 为其他 Activity 提供通用的对话框和错误处理方法，减少重复代码，并提高代码的可维护性。
     * 功能：
     *  （1）显示通用对话框：提供多种类型的对话框（如错误提示、确认对话框等），用于与用户交互。
     *  （2）处理常见错误：提供统一的错误处理逻辑，例如缺少 Google TTS、网络连接不足或 API 密钥文件错误。
     *  （3）支持扩展：作为抽象类，允许子类继承并根据需要扩展功能。
     */

    public void showMissingGoogleTTSDialog(@Nullable DialogInterface.OnClickListener continueListener) {
        /**
         * 显示缺少 Google TTS 的错误提示对话框。
         * 提示用户安装 Google TTS 应用，或者选择继续运行应用。
         */
        final AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setMessage(R.string.error_missing_tts);
        builder.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
            @Override
            // 确定：点击后跳转到 Google Play 商店下载 Google TTS 应用。
            public void onClick(DialogInterface dialog, int which) {
                Intent intent = new Intent(Intent.ACTION_VIEW);
                intent.setData(Uri.parse("https://play.google.com/store/apps/details?id=com.google.android.tts"));
                intent.setPackage("com.android.vending");
                try {
                    startActivity(intent);
                }catch (Exception e){
                    e.printStackTrace();
                    finish();
                }
            }
        });
        if(continueListener != null){
            // 继续（可选）：如果提供了 continueListener，则显示“继续”按钮，允许用户忽略错误。
            builder.setNegativeButton(R.string.continue_without_tts, continueListener);
        }
        builder.create().show();
    }

    public void showGoogleTTSErrorDialog() {
        /**
         * 显示 Google TTS 初始化失败的错误提示对话框。
         * 提示用户 Google TTS 初始化失败。
         */
        final AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setMessage(R.string.error_tts);
        // 只有一个“确定”按钮，点击后关闭对话框。
        builder.setPositiveButton(android.R.string.ok, null);
        builder.create().show();
    }

    public void showGoogleTTSErrorDialog(DialogInterface.OnClickListener continueListener) {
        /**
         * showGoogleTTSErrorDialog 的重载版本，显示 Google TTS 初始化失败的错误提示对话框。
         * 提示用户 Google TTS 初始化失败，并提供继续或退出的选择。
         */
        final AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setMessage(R.string.error_tts);
        // 继续：点击后调用 continueListener，允许用户忽略错误。
        builder.setPositiveButton(R.string.continue_without_tts, continueListener);
        // 退出：点击后调用 finish() 结束当前 Activity。
        builder.setNegativeButton(R.string.exit, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                finish();
            }
        });
        builder.create().show();
    }

    public void showInternetLackDialog(int message, DialogInterface.OnClickListener listener) {
        /**
         * 显示网络连接不足的错误提示对话框。
         * 提示用户网络连接问题，并允许开发者自定义后续操作。
         */
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setCancelable(true);
        builder.setMessage(message);
        // 提供一个“确定”按钮，点击后调用 listener。
        builder.setPositiveButton(android.R.string.ok, listener);
        AlertDialog dialog = builder.create();
        dialog.show();
    }

    public void showApiKeyFileErrorDialog(int message, DialogInterface.OnClickListener confirmListener, DialogInterface.OnClickListener cancelListener) {
        /**
         * 显示 API 密钥文件错误的提示对话框。
         * 提示用户 API 密钥文件存在问题，并允许用户选择如何处理。
         */
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setCancelable(true);
        builder.setMessage(message);
        // 确定：点击后调用 confirmListener。
        builder.setPositiveButton(android.R.string.ok, confirmListener);
        // 退出：点击后调用 cancelListener。
        builder.setNegativeButton(R.string.exit, cancelListener);
        AlertDialog dialog = builder.create();
        dialog.show();
    }

    public void showConfirmDeleteDialog(DialogInterface.OnClickListener confirmListener) {
        /**
         * 显示确认删除的对话框。
         */
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setCancelable(true);
        builder.setMessage(R.string.dialog_confirm_delete);
        // 确定：点击后调用 confirmListener。
        builder.setPositiveButton(android.R.string.ok, confirmListener);
        // 取消：点击后关闭对话框。
        builder.setNegativeButton(android.R.string.cancel, null);
        builder.create().show();
    }

    protected void showConfirmExitDialog(DialogInterface.OnClickListener confirmListener) {
        /**
         * 显示确认退出的对话框。
         * 提示用户确认是否退出应用。
         */
        //creazione del dialog.
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setCancelable(true);
        builder.setMessage(R.string.dialog_confirm_exit);
        // 确定：点击后调用 confirmListener。
        builder.setPositiveButton(android.R.string.ok, confirmListener);
        // 取消：点击后关闭对话框。
        builder.setNegativeButton(android.R.string.cancel, null);

        AlertDialog dialog = builder.create();
        dialog.show();
    }

    public void onError(int reason, long value) {
        /**
         * 提供统一的错误处理入口，便于扩展。
         */
        /*switch (reason) {
            // 处理缺少 Google Play 服务的错误
            case ErrorCodes.MISSING_PLAY_SERVICES: {
                GoogleApiAvailability.getInstance().getErrorDialog(this, (int) value, 34).show();
                break;
            }
        }*/
    }
}
