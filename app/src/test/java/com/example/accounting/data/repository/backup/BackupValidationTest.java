package com.example.accounting.data.repository.backup;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

/**
 * 恢复校验的单元测试：这是"恢复前先校验"安全网的最后一道闸，
 * 三种边界必须钉死——好文件放行、坏文件拒绝、新版本备份拒绝。
 */
public class BackupValidationTest {

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    /** 构造一个假的数据库文件：可选的 SQLite 魔数 + 指定 user_version */
    private File writeFakeDatabase(boolean validMagic, int userVersion) throws Exception {
        File file = tempFolder.newFile("test_" + System.nanoTime() + ".db");
        byte[] data = new byte[100];
        if (validMagic) {
            byte[] magic = "SQLite format 3\0".getBytes("US-ASCII");
            System.arraycopy(magic, 0, data, 0, magic.length);
        }
        // SQLite 文件头：偏移 60 起 4 字节大端序是 user_version
        data[60] = (byte) ((userVersion >> 24) & 0xFF);
        data[61] = (byte) ((userVersion >> 16) & 0xFF);
        data[62] = (byte) ((userVersion >> 8) & 0xFF);
        data[63] = (byte) (userVersion & 0xFF);
        try (FileOutputStream out = new FileOutputStream(file)) {
            out.write(data);
        }
        return file;
    }

    @Test
    public void 当前版本的合法备份放行() throws Exception {
        File file = writeFakeDatabase(true, 1);
        BackupManager.validateDatabaseFile(file); // 不抛异常即通过
    }

    @Test
    public void 空用户版本也放行() throws Exception {
        // user_version=0 的老库：合法，交给 Room 的 Migration 机制升级
        File file = writeFakeDatabase(true, 0);
        BackupManager.validateDatabaseFile(file);
    }

    @Test
    public void 非数据库文件拒绝() throws Exception {
        File file = writeFakeDatabase(false, 1);
        Exception e = assertThrows(IOException.class, () ->
                BackupManager.validateDatabaseFile(file));
        assertTrue(e.getMessage().contains("invalid_file"));
    }

    @Test
    public void 更新版本的备份拒绝() throws Exception {
        // 备份的 user_version 比当前 App 新，
        // 装回去 Room 会拒绝降级，必须在替换文件前拦下
        File file = writeFakeDatabase(true, 99);
        Exception e = assertThrows(IOException.class, () ->
                BackupManager.validateDatabaseFile(file));
        assertTrue(e.getMessage().contains("newer_version"));
    }

    @Test
    public void 文件不存在时拒绝() {
        Exception e = assertThrows(IOException.class, () ->
                BackupManager.validateDatabaseFile(
                        new File(tempFolder.getRoot(), "missing.db")));
        assertTrue(e.getMessage().contains("invalid_file"));
    }
}
