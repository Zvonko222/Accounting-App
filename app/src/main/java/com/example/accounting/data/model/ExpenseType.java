package com.example.accounting.data.model;

/**
 * 支出类型常量。数据库存 int，界面显示走 displayName()。
 */
public final class ExpenseType {

    public static final int OTHER = 0;
    public static final int RENT = 1;
    public static final int UTILITY = 2;
    public static final int TRANSPORT = 3;
    public static final int SALARY = 4;
    public static final int STOCK_LOSS = 5;

    private ExpenseType() {
    }

    public static String displayName(int expenseType) {
        switch (expenseType) {
            case RENT:       return "房租";
            case UTILITY:    return "水电";
            case TRANSPORT:  return "运费";
            case SALARY:     return "工资";
            case STOCK_LOSS: return "损耗";
            default:         return "其他";
        }
    }

    /** 记支出界面的类型选项，顺序即显示顺序 */
    public static int[] options() {
        return new int[]{UTILITY, TRANSPORT, SALARY, RENT, STOCK_LOSS, OTHER};
    }
}
