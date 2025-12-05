package com.douyin.huoshan.hook;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import com.google.gson.Gson;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import de.robv.android.xposed.XposedBridge;

/**
 * 数据提取器
 * 从抖音火山版的 databases、files、shared_prefs 目录提取登录数据
 */
public class DataExtractor {
    
    private static final String TARGET_PACKAGE = "com.ss.android.ugc.live";
    private static final Gson gson = new Gson();
    
    /**
     * 提取完整的应用数据
     */
    public static Map<String, Object> extractAllData(Context context) {
        Map<String, Object> allData = new HashMap<>();
        
        try {
            // 1. 提取 SharedPreferences
            Map<String, Object> spData = extractSharedPreferences(context);
            allData.put("shared_prefs", spData);
            
            // 2. 提取数据库数据
            Map<String, Object> dbData = extractDatabases(context);
            allData.put("databases", dbData);
            
            // 3. 提取文件数据
            Map<String, Object> filesData = extractFiles(context);
            allData.put("files", filesData);
            
            // 4. 保存到备份目录
            saveExtractedData(allData);
            
            XposedBridge.log("数据提取完成");
        } catch (Exception e) {
            XposedBridge.log("数据提取失败: " + e.getMessage());
            e.printStackTrace();
        }
        
        return allData;
    }
    
    /**
     * 提取 SharedPreferences 数据
     */
    private static Map<String, Object> extractSharedPreferences(Context context) {
        Map<String, Object> spData = new HashMap<>();
        
        try {
            // SharedPreferences 目录
            File spDir = new File("/data/data/" + TARGET_PACKAGE + "/shared_prefs");
            if (!spDir.exists() || !spDir.isDirectory()) {
                return spData;
            }
            
            // 遍历所有 SP 文件
            File[] spFiles = spDir.listFiles((dir, name) -> name.endsWith(".xml"));
            if (spFiles != null) {
                for (File spFile : spFiles) {
                    try {
                        String fileName = spFile.getName().replace(".xml", "");
                        Map<String, Object> fileData = parseSharedPrefsFile(spFile);
                        spData.put(fileName, fileData);
                        
                        XposedBridge.log("提取 SP 文件: " + fileName + ", 数据量: " + fileData.size());
                    } catch (Exception e) {
                        XposedBridge.log("解析 SP 文件失败: " + spFile.getName());
                    }
                }
            }
        } catch (Exception e) {
            XposedBridge.log("提取 SharedPreferences 失败: " + e.getMessage());
        }
        
        return spData;
    }
    
    /**
     * 解析 SharedPreferences XML 文件
     */
    private static Map<String, Object> parseSharedPrefsFile(File file) throws Exception {
        Map<String, Object> data = new HashMap<>();
        
        // 读取文件内容
        FileInputStream fis = new FileInputStream(file);
        byte[] buffer = new byte[(int) file.length()];
        fis.read(buffer);
        fis.close();
        
        String content = new String(buffer, StandardCharsets.UTF_8);
        
        // 简单的 XML 解析（提取 key-value）
        // 格式: <string name="key">value</string>
        String[] lines = content.split("\n");
        for (String line : lines) {
            if (line.contains("name=") && (line.contains("<string") || line.contains("<int") || line.contains("<long"))) {
                try {
                    String key = extractAttribute(line, "name");
                    String value = extractValue(line);
                    if (key != null && value != null) {
                        data.put(key, value);
                    }
                } catch (Exception e) {
                    // 忽略解析错误
                }
            }
        }
        
        return data;
    }
    
    /**
     * 提取数据库数据
     */
    private static Map<String, Object> extractDatabases(Context context) {
        Map<String, Object> dbData = new HashMap<>();
        
        try {
            // 数据库目录
            File dbDir = new File("/data/data/" + TARGET_PACKAGE + "/databases");
            if (!dbDir.exists() || !dbDir.isDirectory()) {
                return dbData;
            }
            
            // 遍历所有数据库文件
            File[] dbFiles = dbDir.listFiles((dir, name) -> name.endsWith(".db"));
            if (dbFiles != null) {
                for (File dbFile : dbFiles) {
                    try {
                        String dbName = dbFile.getName();
                        Map<String, Object> tables = extractDatabaseTables(dbFile);
                        dbData.put(dbName, tables);
                        
                        XposedBridge.log("提取数据库: " + dbName + ", 表数量: " + tables.size());
                    } catch (Exception e) {
                        XposedBridge.log("提取数据库失败: " + dbFile.getName());
                    }
                }
            }
        } catch (Exception e) {
            XposedBridge.log("提取数据库失败: " + e.getMessage());
        }
        
        return dbData;
    }
    
    /**
     * 提取数据库中的表数据
     */
    private static Map<String, Object> extractDatabaseTables(File dbFile) {
        Map<String, Object> tables = new HashMap<>();
        
        try {
            SQLiteDatabase db = SQLiteDatabase.openDatabase(
                dbFile.getAbsolutePath(), 
                null, 
                SQLiteDatabase.OPEN_READONLY
            );
            
            // 获取所有表名
            Cursor cursor = db.rawQuery(
                "SELECT name FROM sqlite_master WHERE type='table'", 
                null
            );
            
            while (cursor.moveToNext()) {
                String tableName = cursor.getString(0);
                
                // 跳过系统表
                if (tableName.startsWith("sqlite_") || tableName.startsWith("android_")) {
                    continue;
                }
                
                try {
                    // 查询表数据（只取前100条）
                    Cursor tableCursor = db.rawQuery(
                        "SELECT * FROM " + tableName + " LIMIT 100", 
                        null
                    );
                    
                    java.util.List<Map<String, Object>> rows = new java.util.ArrayList<>();
                    while (tableCursor.moveToNext()) {
                        Map<String, Object> row = new HashMap<>();
                        for (int i = 0; i < tableCursor.getColumnCount(); i++) {
                            String columnName = tableCursor.getColumnName(i);
                            String value = tableCursor.getString(i);
                            row.put(columnName, value);
                        }
                        rows.add(row);
                    }
                    tableCursor.close();
                    
                    tables.put(tableName, rows);
                    XposedBridge.log("  表: " + tableName + ", 行数: " + rows.size());
                } catch (Exception e) {
                    XposedBridge.log("  提取表失败: " + tableName);
                }
            }
            
            cursor.close();
            db.close();
        } catch (Exception e) {
            XposedBridge.log("打开数据库失败: " + e.getMessage());
        }
        
        return tables;
    }
    
    /**
     * 提取文件数据
     */
    private static Map<String, Object> extractFiles(Context context) {
        Map<String, Object> filesData = new HashMap<>();
        
        try {
            // files 目录
            File filesDir = new File("/data/data/" + TARGET_PACKAGE + "/files");
            if (!filesDir.exists() || !filesDir.isDirectory()) {
                return filesData;
            }
            
            // 遍历文件
            scanDirectory(filesDir, filesData, 0);
            
        } catch (Exception e) {
            XposedBridge.log("提取文件失败: " + e.getMessage());
        }
        
        return filesData;
    }
    
    /**
     * 递归扫描目录
     */
    private static void scanDirectory(File dir, Map<String, Object> result, int depth) {
        if (depth > 3) return; // 限制深度
        
        File[] files = dir.listFiles();
        if (files == null) return;
        
        for (File file : files) {
            try {
                if (file.isDirectory()) {
                    Map<String, Object> subDir = new HashMap<>();
                    scanDirectory(file, subDir, depth + 1);
                    result.put(file.getName(), subDir);
                } else {
                    // 只读取小文件（< 1MB）
                    if (file.length() < 1024 * 1024) {
                        String content = readSmallFile(file);
                        result.put(file.getName(), content);
                        XposedBridge.log("  文件: " + file.getName() + ", 大小: " + file.length());
                    } else {
                        result.put(file.getName(), "[文件过大，已跳过]");
                    }
                }
            } catch (Exception e) {
                // 忽略单个文件错误
            }
        }
    }
    
    /**
     * 读取小文件
     */
    private static String readSmallFile(File file) {
        try {
            FileInputStream fis = new FileInputStream(file);
            byte[] buffer = new byte[(int) file.length()];
            fis.read(buffer);
            fis.close();
            
            // 尝试作为文本读取
            String content = new String(buffer, StandardCharsets.UTF_8);
            
            // 检查是否是有效的文本
            if (content.matches(".*[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F].*")) {
                return "[二进制文件]";
            }
            
            return content;
        } catch (Exception e) {
            return "[读取失败]";
        }
    }
    
    /**
     * 保存提取的数据
     */
    private static void saveExtractedData(Map<String, Object> data) {
        try {
            File backupDir = new File("/sdcard/DouyinHuoshanBackup");
            if (!backupDir.exists()) {
                backupDir.mkdirs();
            }
            
            String timestamp = new java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.getDefault())
                .format(new java.util.Date());
            File file = new File(backupDir, "full_data_" + timestamp + ".json");
            
            String json = gson.toJson(data);
            FileWriter writer = new FileWriter(file);
            writer.write(json);
            writer.close();
            
            XposedBridge.log("完整数据已保存: " + file.getAbsolutePath());
        } catch (Exception e) {
            XposedBridge.log("保存数据失败: " + e.getMessage());
        }
    }
    
    /**
     * 提取 XML 属性值
     */
    private static String extractAttribute(String line, String attrName) {
        try {
            int start = line.indexOf(attrName + "=\"");
            if (start == -1) return null;
            start += (attrName + "=\"").length();
            int end = line.indexOf("\"", start);
            if (end == -1) return null;
            return line.substring(start, end);
        } catch (Exception e) {
            return null;
        }
    }
    
    /**
     * 提取 XML 值
     */
    private static String extractValue(String line) {
        try {
            int start = line.indexOf(">");
            if (start == -1) return null;
            start++;
            int end = line.indexOf("<", start);
            if (end == -1) return null;
            return line.substring(start, end);
        } catch (Exception e) {
            return null;
        }
    }
}
