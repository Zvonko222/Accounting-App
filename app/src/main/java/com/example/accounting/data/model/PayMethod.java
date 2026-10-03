package com.example.accounting.data.model;

/**
 * 收款方式常量。数据库存 int，界面显示走 displayName()。
 * 不用魔法数字：if (sale.payMethod == PayMethod.WECHAT) 一眼读懂。
 */
public final class PayMethod {

    public static final int OTHER = 0;
    public static final int CASH = 1;
    public static final int WECHAT = 2;
    public static final int ALIPAY = 3;
    public static final int CARD = 4;

    private PayMethod() {
    }

    public static String displayName(int payMethod) {
        switch (payMethod) {
            case CASH:   return "现金";
            case WECHAT: return "微信";
            case ALIPAY: return "支付宝";
            case CARD:   return "刷卡";
            default:     return "其他";
        }
    }

    /** 开单界面的收款方式选项，顺序即显示顺序 */
    public static int[] options() {
        return new int[]{CASH, WECHAT, ALIPAY, CARD, OTHER};
    }
}
