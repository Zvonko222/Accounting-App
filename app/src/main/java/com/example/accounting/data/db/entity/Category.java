package com.example.accounting.data.db.entity;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/**
 * 商品分类。表很小，首次建库时预置常用分类（见 AppDatabase）。
 */
@Entity(tableName = "categories",
        indices = {
                @Index(value = "name", unique = true),
                @Index("syncStatus")
        })
public class Category extends SyncEntity {

    @PrimaryKey
    @NonNull
    public String id;

    /** 分类名，唯一约束防止出现"饮料/饮品"两个分类导致统计失真 */
    public String name;

    /** 界面显示顺序，小的在前 */
    public int sortOrder;
}
