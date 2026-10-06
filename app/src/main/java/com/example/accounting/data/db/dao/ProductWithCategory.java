package com.example.accounting.data.db.dao;

import androidx.room.Embedded;

import com.example.accounting.data.db.entity.Product;

/** 商品 + 分类名（库存表导出用，LEFT JOIN 商品可能无分类） */
public class ProductWithCategory {

    @Embedded
    public Product product;

    /** 分类名；未设分类为空串 */
    public String categoryName;
}
