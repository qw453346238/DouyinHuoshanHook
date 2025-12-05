package com.douyin.huoshan.hook;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * Xposed Hook 入口类
 * 用于 Hook 抖音火山版应用
 */
public class HookEntry implements IXposedHookLoadPackage {
    
    // 抖音火山版包名
    private static final String TARGET_PACKAGE = "com.ss.android.ugc.live";
    
    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        // 只 Hook 抖音火山版
        if (!lpparam.packageName.equals(TARGET_PACKAGE)) {
            return;
        }
        
        XposedBridge.log("抖音火山Hook: 开始加载 Hook 模块");
        
        try {
            // Hook Application.onCreate 实现自动恢复
            XposedHelpers.findAndHookMethod(
                "android.app.Application",
                lpparam.classLoader,
                "onCreate",
                new de.robv.android.xposed.XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        android.content.Context context = (android.content.Context) param.thisObject;
                        XposedBridge.log("抖音火山Hook: 获取到 Application Context");
                        
                        // 1. 检查并自动恢复备份（免 Root）
                        AutoRestorer.checkAndRestore(context);
                        
                        // 2. 延迟5秒后提取数据（等待应用完全启动）
                        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                            try {
                                java.util.Map<String, Object> allData = DataExtractor.extractAllData(context);
                                XposedBridge.log("抖音火山Hook: 数据提取完成");
                            } catch (Exception e) {
                                XposedBridge.log("抖音火山Hook: 数据提取失败 - " + e.getMessage());
                            }
                        }, 5000);
                    }
                }
            );
            
            // 初始化登录 Hook
            LoginHook.init(lpparam);
            XposedBridge.log("抖音火山Hook: 登录 Hook 初始化成功");
        } catch (Throwable t) {
            XposedBridge.log("抖音火山Hook: 初始化失败 - " + t.getMessage());
            t.printStackTrace();
        }
    }
}
