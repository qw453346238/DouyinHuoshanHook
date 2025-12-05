package com.douyin.huoshan.hook;

import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.io.File;
import java.io.FileWriter;
import java.lang.reflect.Method;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * 登录 Hook 核心类
 * 负责拦截登录响应并提取关键数据
 */
public class LoginHook {
    
    private static Context appContext;
    private static final Gson gson = new Gson();
    
    public static void init(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            // Hook 多个可能的登录相关类
            hookLoginResponse(lpparam);
            hookTokenStorage(lpparam);
            hookUserInfo(lpparam);
            hookApplicationContext(lpparam);
        } catch (Throwable t) {
            XposedBridge.log("LoginHook 初始化失败: " + t.getMessage());
        }
    }
    
    /**
     * Hook Application Context 获取
     */
    private static void hookApplicationContext(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            // Hook Application 的 onCreate
            XposedHelpers.findAndHookMethod(
                "android.app.Application",
                lpparam.classLoader,
                "onCreate",
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        appContext = (Context) param.thisObject;
                        XposedBridge.log("获取到 Application Context");
                        
                        // 延迟5秒后提取完整数据（等待应用完全启动）
                        new Handler(Looper.getMainLooper()).postDelayed(() -> {
                            try {
                                Map<String, Object> allData = DataExtractor.extractAllData(appContext);
                                showDataExtractedDialog(allData);
                            } catch (Exception e) {
                                XposedBridge.log("提取完整数据失败: " + e.getMessage());
                            }
                        }, 5000);
                    }
                }
            );
        } catch (Throwable t) {
            XposedBridge.log("Hook Application Context 失败: " + t.getMessage());
        }
    }
    
    /**
     * 显示数据提取完成对话框
     */
    private static void showDataExtractedDialog(Map<String, Object> data) {
        if (appContext == null) return;
        
        new Handler(Looper.getMainLooper()).post(() -> {
            try {
                int spCount = data.containsKey("shared_prefs") ? 
                    ((Map) data.get("shared_prefs")).size() : 0;
                int dbCount = data.containsKey("databases") ? 
                    ((Map) data.get("databases")).size() : 0;
                int filesCount = data.containsKey("files") ? 
                    ((Map) data.get("files")).size() : 0;
                
                String message = "数据提取完成！\n\n" +
                               "SharedPreferences: " + spCount + " 个文件\n" +
                               "数据库: " + dbCount + " 个\n" +
                               "文件: " + filesCount + " 个\n\n" +
                               "数据已保存到:\n/sdcard/DouyinHuoshanBackup/";
                
                AlertDialog.Builder builder = new AlertDialog.Builder(appContext);
                builder.setTitle("数据提取成功");
                builder.setMessage(message);
                builder.setPositiveButton("复制路径", (dialog, which) -> {
                    copyToClipboard("/sdcard/DouyinHuoshanBackup/");
                    Toast.makeText(appContext, "路径已复制", Toast.LENGTH_SHORT).show();
                });
                builder.setNegativeButton("关闭", null);
                builder.show();
            } catch (Throwable t) {
                XposedBridge.log("显示对话框失败: " + t.getMessage());
            }
        });
    }
    
    /**
     * Hook 登录响应处理
     */
    private static void hookLoginResponse(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            // 通用的网络响应处理类（需要根据实际反编译结果调整）
            // 这里提供多个常见的类名尝试
            String[] possibleClasses = {
                "com.ss.android.ugc.aweme.account.login.model.LoginResponse",
                "com.ss.android.ugc.aweme.account.b",
                "com.bytedance.ies.ugc.aweme.network.RetrofitService",
                "com.ss.android.ugc.aweme.net.model.Response"
            };
            
            for (String className : possibleClasses) {
                try {
                    Class<?> clazz = XposedHelpers.findClass(className, lpparam.classLoader);
                    
                    // Hook 所有方法，查找登录相关的
                    for (Method method : clazz.getDeclaredMethods()) {
                        XposedBridge.hookMethod(method, new XC_MethodHook() {
                            @Override
                            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                                try {
                                    Object result = param.getResult();
                                    if (result != null) {
                                        String resultStr = result.toString();
                                        
                                        // 检查是否包含登录相关的关键字
                                        if (resultStr.contains("session_key") || 
                                            resultStr.contains("access_token") ||
                                            resultStr.contains("uid") ||
                                            resultStr.contains("user_id")) {
                                            
                                            XposedBridge.log("捕获到登录响应: " + method.getName());
                                            extractAndShowLoginData(result, param);
                                        }
                                    }
                                } catch (Throwable t) {
                                    // 忽略单个方法的错误
                                }
                            }
                        });
                    }
                    
                    XposedBridge.log("成功 Hook 类: " + className);
                } catch (Throwable t) {
                    // 类不存在，尝试下一个
                }
            }
        } catch (Throwable t) {
            XposedBridge.log("Hook 登录响应失败: " + t.getMessage());
        }
    }
    
    /**
     * Hook Token 存储方法
     */
    private static void hookTokenStorage(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            // SharedPreferences 的 putString 方法
            XposedHelpers.findAndHookMethod(
                "android.content.SharedPreferences.Editor",
                lpparam.classLoader,
                "putString",
                String.class,
                String.class,
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        String key = (String) param.args[0];
                        String value = (String) param.args[1];
                        
                        // 检查是否是 token 相关的 key
                        if (key != null && value != null && 
                            (key.contains("token") || key.contains("session") || 
                             key.contains("uid") || key.contains("cookie"))) {
                            
                            XposedBridge.log("捕获到 Token 存储: " + key + " = " + value);
                            
                            Map<String, String> data = new HashMap<>();
                            data.put(key, value);
                            saveLoginData(data);
                        }
                    }
                }
            );
        } catch (Throwable t) {
            XposedBridge.log("Hook Token 存储失败: " + t.getMessage());
        }
    }
    
    /**
     * Hook 用户信息设置
     */
    private static void hookUserInfo(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            // 尝试 Hook 用户信息类
            String[] userInfoClasses = {
                "com.ss.android.ugc.aweme.profile.model.User",
                "com.ss.android.ugc.aweme.user.model.User"
            };
            
            for (String className : userInfoClasses) {
                try {
                    Class<?> userClass = XposedHelpers.findClass(className, lpparam.classLoader);
                    
                    // Hook setter 方法
                    for (Method method : userClass.getDeclaredMethods()) {
                        if (method.getName().startsWith("set")) {
                            XposedBridge.hookMethod(method, new XC_MethodHook() {
                                @Override
                                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                                    XposedBridge.log("用户信息设置: " + method.getName() + 
                                                   " = " + (param.args.length > 0 ? param.args[0] : ""));
                                }
                            });
                        }
                    }
                } catch (Throwable t) {
                    // 继续尝试下一个类
                }
            }
        } catch (Throwable t) {
            XposedBridge.log("Hook 用户信息失败: " + t.getMessage());
        }
    }
    
    /**
     * 提取并显示登录数据
     */
    private static void extractAndShowLoginData(Object loginResponse, XC_MethodHook.MethodHookParam param) {
        try {
            // 提取所有字段
            Map<String, Object> loginData = new HashMap<>();
            Class<?> clazz = loginResponse.getClass();
            
            // 反射获取所有字段
            java.lang.reflect.Field[] fields = clazz.getDeclaredFields();
            for (java.lang.reflect.Field field : fields) {
                try {
                    field.setAccessible(true);
                    Object value = field.get(loginResponse);
                    if (value != null) {
                        loginData.put(field.getName(), value);
                    }
                } catch (Throwable t) {
                    // 忽略单个字段错误
                }
            }
            
            // 保存数据
            saveLoginData(loginData);
            
            // 显示弹窗
            showLoginDataDialog(loginData);
            
        } catch (Throwable t) {
            XposedBridge.log("提取登录数据失败: " + t.getMessage());
        }
    }
    
    /**
     * 保存登录数据到文件
     */
    private static void saveLoginData(Map<String, ?> data) {
        try {
            // 保存到外部存储
            File dir = new File("/sdcard/DouyinHuoshanBackup");
            if (!dir.exists()) {
                dir.mkdirs();
            }
            
            // 生成文件名（带时间戳）
            String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
                .format(new Date());
            File file = new File(dir, "login_data_" + timestamp + ".json");
            
            // 写入 JSON
            String json = gson.toJson(data);
            FileWriter writer = new FileWriter(file);
            writer.write(json);
            writer.close();
            
            XposedBridge.log("登录数据已保存: " + file.getAbsolutePath());
        } catch (Throwable t) {
            XposedBridge.log("保存登录数据失败: " + t.getMessage());
        }
    }
    
    /**
     * 显示登录数据弹窗
     */
    private static void showLoginDataDialog(Map<String, Object> data) {
        try {
            // 获取应用 Context
            if (appContext == null) {
                return;
            }
            
            // 在主线程显示弹窗
            new Handler(Looper.getMainLooper()).post(() -> {
                try {
                    String jsonData = gson.toJson(data);
                    
                    AlertDialog.Builder builder = new AlertDialog.Builder(appContext);
                    builder.setTitle("登录数据提取成功");
                    builder.setMessage("数据已保存到:\n/sdcard/DouyinHuoshanBackup/\n\n" +
                                     "点击复制按钮可复制完整数据");
                    builder.setPositiveButton("复制", (dialog, which) -> {
                        copyToClipboard(jsonData);
                        Toast.makeText(appContext, "已复制到剪贴板", Toast.LENGTH_SHORT).show();
                    });
                    builder.setNegativeButton("关闭", null);
                    builder.show();
                } catch (Throwable t) {
                    XposedBridge.log("显示弹窗失败: " + t.getMessage());
                }
            });
        } catch (Throwable t) {
            XposedBridge.log("显示登录数据弹窗失败: " + t.getMessage());
        }
    }
    
    /**
     * 复制到剪贴板
     */
    private static void copyToClipboard(String text) {
        try {
            if (appContext != null) {
                ClipboardManager clipboard = (ClipboardManager) 
                    appContext.getSystemService(Context.CLIPBOARD_SERVICE);
                ClipData clip = ClipData.newPlainText("登录数据", text);
                clipboard.setPrimaryClip(clip);
            }
        } catch (Throwable t) {
            XposedBridge.log("复制到剪贴板失败: " + t.getMessage());
        }
    }
    
    /**
     * 设置应用 Context
     */
    public static void setAppContext(Context context) {
        appContext = context;
    }
}
