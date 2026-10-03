package com.example.accounting.data.repository;

/**
 * 写操作的回调：Repository 在后台线程完成写库后，把结果切回主线程通知界面。
 *
 * 记账软件的铁律：保存失败必须让用户看见（ARCHITECTURE.md 2.6），
 * 所以界面层一定实现 onError 弹提示，绝不静默吞掉。
 */
public interface SaveCallback {

    void onSuccess();

    void onError(String message);
}
