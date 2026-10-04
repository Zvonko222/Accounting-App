package com.example.accounting.data.db.entity;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/**
 * 销售单（主表）。明细在 {@link SaleItem}，两者在同一个事务里写入。
 */
@Entity(tableName = "sales",
        indices = {
                @Index("saleTime"),
                @Index("syncStatus")
        })
public class Sale extends SyncEntity {

    @PrimaryKey
    @NonNull
    public String id;

    /**
     * 业务发生时间。用户可以修改它（比如补录昨天的单），
     * 统计一律按这个时间算，与 createdAt（记录何时写入数据库）区分。
     */
    public long saleTime;

    /** 合计金额（分）= 各行小计之和 - 优惠。保存时一次算定，之后永不重算 */
    public long totalAmountCents;

    /** 整单优惠（分） */
    public long discountCents;

    /** 收款方式，见 {@link com.example.accounting.data.model.PayMethod} */
    public int payMethod;

    /** 可空：客户名。小商户记账不做客户档案 */
    public String customerName;

    public String note;

    /** 交付状态：0=无需交付（堂食/自提） 1=待交付（外卖/预订） 2=已交付 */
    public int deliveryStatus;

    /** 交付时间（毫秒），未交付为 null */
    public Long deliveredAt;

    /** 货物交付地址，支持换行或分隔符填写多个地址 */
    public String deliveryAddress;

    /** 联系电话，支持换行或分隔符填写多个号码 */
    public String deliveryPhone;
}
