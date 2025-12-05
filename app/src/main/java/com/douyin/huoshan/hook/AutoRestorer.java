package com.douyin.huoshan.hook;

import android.content.Context;
import android.content.SharedPreferences;
import android.database.sqlite.SQLiteDatabase;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import com.google.gson.Gson;

import java.io.File;
import java.io.FileInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;

import de.robv.android.xposed.XposedBridge;

/**
 * 自动恢复器（免 Root）
 * 在应用启动时自动检测并恢复备份数据
 */
public class AutoRestorer {
    
    private static final String RESTORE_DIR = "/sdcard/DouyinLoginData";
    private static final String RESTORE_FILE = "restore.dybak";
    private static final String ENCRYPTION_KEY = "DouyinBackup2024";
    private static final Gson gson = new Gson();
    
    /**
     * 检查并自动恢复
     */
    public static void checkAndRestore(Context context) {
        try {
            File restoreFile = new File(RESTORE_DIR, RESTORE_FILE);
            
            if (!restoreFile.exists()) {
                XposedBridge.log("AutoRestorer: 没有待恢复的备份文件");
                return;
            }
            
            XposedBridge.log("AutoRestorer: 发现备份文件，开始恢复...");
            
            // 读取并解密备份
            String encrypted = readFile(restoreFile);
            String json = decrypt(encrypted);
            Map<String, Object> backupData = gson.fromJson(json, Map.class);
            
            // 恢复数据
            boolean success = restoreData(context, backupData);
            
            if (success) {
                XposedBridge.log("AutoRestorer: 恢复成功");
                
                // 删除备份文件（安全）
                restoreFile.delete();
                
                // 提示用户
                showToast(context, "账号恢复成功！");
                
                // 记录到计数器
                recordToCounter(context, backupData);
            } else {
                XposedBridge.log("AutoRestorer: 恢复失败");
                showToast(context, "账号恢复失败");
            }
            
        } catch (Exception e) {
            XposedBridge.log("AutoRestorer: 恢复异常 - " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * 恢复数据（免 Root）
     */
    private static boolean restoreData(Context context, Map<String, Object> backupData) {
        try {
            // 1. 恢复 SharedPreferences
            Map<String, Object> spData = (Map<String, Object>) backupData.get("shared_prefs");
            if (spData != null) {
                restoreSharedPreferences(context, spData);
            }
            
            // 2. 恢复数据库
            Map<String, Object> dbData = (Map<String, Object>) backupData.get("databases");
            if (dbData != null) {
                restoreDatabases(context, dbData);
            }
            
            // 3. 恢复文件
            Map<String, Object> filesData = (Map<String, Object>) backupData.get("files");
            if (filesData != null) {
                restoreFiles(context, filesData);
            }
            
            return true;
        } catch (Exception e) {
            XposedBridge.log("AutoRestorer: restoreData 失败 - " + e.getMessage());
            return false;
        }
    }
    
    /**
     * 恢复 SharedPreferences（免 Root）
     */
    private static void restoreSharedPreferences(Context context, Map<String, Object> spData) {
        try {
            for (Map.Entry<String, Object> entry : spData.entrySet()) {
                String spName = entry.getKey();
                Map<String, Object> values = (Map<String, Object>) entry.getValue();
                
                // 使用应用的 Context 写入（无需 Root）
                SharedPreferences sp = context.getSharedPreferences(spName, Context.MODE_PRIVATE);
                SharedPreferences.Editor editor = sp.edit();
                
                for (Map.Entry<String, Object> kv : values.entrySet()) {
                    String key = kv.getKey();
                    Object value = kv.getValue();
                    
                    if (value instanceof String) {
                        editor.putString(key, (String) value);
                    } else if (value instanceof Double) {
                        // JSON 数字默认是 Double
                        double d = (Double) value;
                        if (d == Math.floor(d)) {
                            // 整数
                            editor.putLong(key, (long) d);
                        } else {
                            editor.putFloat(key, (float) d);
                        }
                    } else if (value instanceof Boolean) {
                        editor.putBoolean(key, (Boolean) value);
                    }
                }
                
                editor.apply();
                XposedBridge.log("AutoRestorer: 恢复 SP - " + spName + ", 数据量: " + values.size());
            }
        } catch (Exception e) {
            XposedBridge.log("AutoRestorer: restoreSharedPreferences 失败 - " + e.getMessage());
        }
    }
    
    /**
     * 恢复数据库（免 Root）
     */
    private static void restoreDatabases(Context context, Map<String, Object> dbData) {
        try {
            for (Map.Entry<String, Object> entry : dbData.entrySet()) {
                String dbName = entry.getKey();
                Map<String, Object> tables = (Map<String, Object>) entry.getValue();
                
                // 打开或创建数据库（无需 Root）
                SQLiteDatabase db = context.openOrCreateDatabase(dbName, Context.MODE_PRIVATE, null);
                
                for (Map.Entry<String, Object> tableEntry : tables.entrySet()) {
                    String tableName = tableEntry.getKey();
                    java.util.List<Map<String, Object>> rows = 
                        (java.util.List<Map<String, Object>>) tableEntry.getValue();
                    
                    if (rows == null || rows.isEmpty()) {
                        continue;
                    }
                    
                    try {
                        // 创建表（如果不存在）
                        createTableFromData(db, tableName, rows.get(0));
                        
                        // 插入数据
                        for (Map<String, Object> row : rows) {
                            insertRow(db, tableName, row);
                        }
                        
                        XposedBridge.log("AutoRestorer: 恢复表 - " + tableName + ", 行数: " + rows.size());
                    } catch (Exception e) {
                        XposedBridge.log("AutoRestorer: 恢复表失败 - " + tableName);
                    }
                }
                
                db.close();
            }
        } catch (Exception e) {
            XposedBridge.log("AutoRestorer: restoreDatabases 失败 - " + e.getMessage());
        }
    }
    
    /**
     * 根据数据创建表
     */
    private static void createTableFromData(SQLiteDatabase db, String tableName, Map<String, Object> sampleRow) {
        try {
            StringBuilder sql = new StringBuilder("CREATE TABLE IF NOT EXISTS " + tableName + " (");
            
            int i = 0;
            for (String columnName : sampleRow.keySet()) {
                if (i > 0) sql.append(", ");
                sql.append(columnName).append(" TEXT");
                i++;
            }
            
            sql.append(")");
            db.execSQL(sql.toString());
        } catch (Exception e) {
            XposedBridge.log("AutoRestorer: createTable 失败 - " + e.getMessage());
        }
    }
    
    /**
     * 插入行数据
     */
    private static void insertRow(SQLiteDatabase db, String tableName, Map<String, Object> row) {
        try {
            StringBuilder columns = new StringBuilder();
            StringBuilder values = new StringBuilder();
            
            int i = 0;
            for (Map.Entry<String, Object> entry : row.entrySet()) {
                if (i > 0) {
                    columns.append(", ");
                    values.append(", ");
                }
                columns.append(entry.getKey());
                values.append("'").append(entry.getValue().toString().replace("'", "''")).append("'");
                i++;
            }
            
            String sql = "INSERT OR REPLACE INTO " + tableName + 
                        " (" + columns + ") VALUES (" + values + ")";
            db.execSQL(sql);
        } catch (Exception e) {
            // 忽略单行错误
        }
    }
    
    /**
     * 恢复文件（免 Root）
     */
    private static void restoreFiles(Context context, Map<String, Object> filesData) {
        try {
            File filesDir = context.getFilesDir();
            restoreFilesRecursive(filesDir, filesData);
        } catch (Exception e) {
            XposedBridge.log("AutoRestorer: restoreFiles 失败 - " + e.getMessage());
        }
    }
    
    /**
     * 递归恢复文件
     */
    private static void restoreFilesRecursive(File parentDir, Map<String, Object> filesData) {
        for (Map.Entry<String, Object> entry : filesData.entrySet()) {
            String name = entry.getKey();
            Object value = entry.getValue();
            
            File file = new File(parentDir, name);
            
            if (value instanceof Map) {
                // 目录
                file.mkdirs();
                restoreFilesRecursive(file, (Map<String, Object>) value);
            } else if (value instanceof String) {
                // 文件
                String content = (String) value;
                if (!content.equals("[文件过大，已跳过]") && !content.equals("[二进制文件]")) {
                    try {
                        java.io.FileWriter writer = new java.io.FileWriter(file);
                        writer.write(content);
                        writer.close();
                    } catch (Exception e) {
                        // 忽略单个文件错误
                    }
                }
            }
        }
    }
    
    /**
     * 记录到计数器
     */
    private static void recordToCounter(Context context, Map<String, Object> backupData) {
        try {
            // 从备份数据中提取 UID 和昵称
            Map<String, Object> spData = (Map<String, Object>) backupData.get("shared_prefs");
            if (spData == null) return;
            
            String uid = null;
            String nickname = null;
            
            // 尝试从各个 SP 文件中找到 UID 和昵称
            for (Map.Entry<String, Object> entry : spData.entrySet()) {
                Map<String, Object> values = (Map<String, Object>) entry.getValue();
                
                if (values.containsKey("uid")) {
                    uid = values.get("uid").toString();
                }
                if (values.containsKey("nickname")) {
                    nickname = values.get("nickname").toString();
                }
                if (values.containsKey("user_id")) {
                    uid = values.get("user_id").toString();
                }
                if (values.containsKey("user_name")) {
                    nickname = values.get("user_name").toString();
                }
            }
            
            if (uid != null) {
                // 保存到计数器文件
                File counterFile = new File("/sdcard/DouyinLoginData/counter.json");
                counterFile.getParentFile().mkdirs();
                
                java.util.List<Map<String, String>> accounts = new java.util.ArrayList<>();
                
                // 读取现有记录
                if (counterFile.exists()) {
                    String json = readFile(counterFile);
                    accounts = gson.fromJson(json, 
                        new com.google.gson.reflect.TypeToken<java.util.List<Map<String, String>>>(){}.getType());
                }
                
                // 检查是否已存在
                boolean exists = false;
                for (Map<String, String> account : accounts) {
                    if (account.get("uid").equals(uid)) {
                        // 更新时间
                        account.put("last_login_time", String.valueOf(System.currentTimeMillis()));
                        if (nickname != null) {
                            account.put("nickname", nickname);
                        }
                        exists = true;
                        break;
                    }
                }
                
                // 添加新账号
                if (!exists) {
                    Map<String, String> newAccount = new java.util.HashMap<>();
                    newAccount.put("uid", uid);
                    newAccount.put("nickname", nickname != null ? nickname : "未知");
                    newAccount.put("first_login_time", String.valueOf(System.currentTimeMillis()));
                    newAccount.put("last_login_time", String.valueOf(System.currentTimeMillis()));
                    accounts.add(0, newAccount);
                }
                
                // 保存
                String json = gson.toJson(accounts);
                java.io.FileWriter writer = new java.io.FileWriter(counterFile);
                writer.write(json);
                writer.close();
                
                XposedBridge.log("AutoRestorer: 已记录到计数器 - UID: " + uid);
            }
        } catch (Exception e) {
            XposedBridge.log("AutoRestorer: recordToCounter 失败 - " + e.getMessage());
        }
    }
    
    /**
     * 解密数据
     */
    private static String decrypt(String encryptedData) throws Exception {
        SecretKeySpec key = new SecretKeySpec(ENCRYPTION_KEY.getBytes(StandardCharsets.UTF_8), "AES");
        Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
        cipher.init(Cipher.DECRYPT_MODE, key);
        byte[] encrypted = android.util.Base64.decode(encryptedData, android.util.Base64.DEFAULT);
        byte[] decrypted = cipher.doFinal(encrypted);
        return new String(decrypted, StandardCharsets.UTF_8);
    }
    
    /**
     * 读取文件
     */
    private static String readFile(File file) throws Exception {
        FileInputStream fis = new FileInputStream(file);
        byte[] data = new byte[(int) file.length()];
        fis.read(data);
        fis.close();
        return new String(data, StandardCharsets.UTF_8);
    }
    
    /**
     * 显示 Toast
     */
    private static void showToast(Context context, String message) {
        new Handler(Looper.getMainLooper()).post(() -> {
            Toast.makeText(context, message, Toast.LENGTH_LONG).show();
        });
    }
}
