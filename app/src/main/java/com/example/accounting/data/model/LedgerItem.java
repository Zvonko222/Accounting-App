package com.example.accounting.data.model;

/**
 * 流水页合并列表的一行：销售/进货/支出统一成一条时间线。
 * 对不熟手机的用户来说，"一个列表按时间排"比"三个 Tab 来回切"直观得多。
 */
public class LedgerItem {

    public static final int TYPE_SALE = 0;
    public static final int TYPE_PURCHASE = 1;
    public static final int TYPE_EXPENSE = 2;

    public final int type;

    /** 业务发生时间（排序依据） */
    public final long time;

    /** 标题：收款方式/供应商/支出类型 */
    public final String title;

    /** 摘要：商品摘要（"cola×2、薯片×1"）或备注 */
    public final String subtitle;

    /** 金额（分）。销售为正、进货/支出为负（显示层加符号） */
    public final long amountCents;

    /** 是否已作废/已删除 */
    public final boolean deleted;

    /** 单据 id（点开详情用） */
    public final String id;

    public LedgerItem(int type, long time, String title, String subtitle,
                      long amountCents, boolean deleted, String id) {
        this.type = type;
        this.time = time;
        this.title = title;
        this.subtitle = subtitle;
        this.amountCents = amountCents;
        this.deleted = deleted;
        this.id = id;
    }
}
