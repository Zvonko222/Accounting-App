package com.example.accounting.ui.sales;

import android.app.Application;

import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.accounting.AccountingApp;
import com.example.accounting.data.db.entity.Product;
import com.example.accounting.data.model.SaleCartLine;
import com.example.accounting.data.repository.SaleRepository;
import com.example.accounting.data.repository.SaveCallback;
import com.example.accounting.util.SaleCalculator;

import java.util.ArrayList;
import java.util.List;

/**
 * 开单页的状态：商品网格数据 + 购物车。
 *
 * 购物车是普通内存对象（还没写库的草稿），只有点"完成销售"才会
 * 经 SaleRepository 的一个事务落库。所有改动后重算合计并通知 UI。
 */
public class SaleEditViewModel extends AndroidViewModel {

    /** 每次点击商品默认加 1（数量×1000），称重商品在购物车里改精确数量 */
    private static final long DEFAULT_STEP_MILLI = 1000;

    private final SaleRepository saleRepository;

    /** 商品网格 */
    private final LiveData<List<Product>> products;

    /** 购物车草稿。List 内容变化后必须重新 setValue 才会通知 UI */
    private final MutableLiveData<List<SaleCartLine>> cart = new MutableLiveData<>(new ArrayList<>());

    /** 合计（分）。购物车每次变化都重算 */
    private final MutableLiveData<Long> totalCents = new MutableLiveData<>(0L);

    /** 购物车里的商品种数（"本单共 N 种商品"） */
    private final MutableLiveData<Integer> cartLineCount = new MutableLiveData<>(0);

    /** 整单优惠（分）。0 = 无优惠；显示的合计已扣除优惠 */
    private final MutableLiveData<Long> discountCents = new MutableLiveData<>(0L);

    /** 记账时间（毫秒）。默认当下；补录昨天的单时可选过去的时间（不允许未来） */
    private final MutableLiveData<Long> recordTime =
            new MutableLiveData<>(System.currentTimeMillis());

    public SaleEditViewModel(Application app) {
        super(app);
        saleRepository = ((AccountingApp) app).getSaleRepository();
        products = ((AccountingApp) app).getProductRepository().observeAllProducts();
    }

    public LiveData<List<Product>> getProducts() {
        return products;
    }

    public LiveData<List<SaleCartLine>> getCart() {
        return cart;
    }

    public LiveData<Long> getTotalCents() {
        return totalCents;
    }

    public LiveData<Integer> getCartLineCountLive() {
        return cartLineCount;
    }

    public void setDiscountCents(long cents) {
        discountCents.setValue(Math.max(cents, 0));
        // 优惠变化会改变合计：用当前购物车重新发布一次
        publish(currentLines());
    }

    public LiveData<Long> getDiscountCents() {
        return discountCents;
    }

    public LiveData<Long> getRecordTime() {
        return recordTime;
    }

    /** 用户在日期/时间选择器里选定的记账时间 */
    public void setRecordTime(long timeMillis) {
        recordTime.setValue(timeMillis);
    }

    /** 修改模式：回填旧单的记账时间 */
    public void prefillRecordTime(long timeMillis) {
        recordTime.setValue(timeMillis);
    }

    public int getCartLineCount() {
        List<SaleCartLine> lines = cart.getValue();
        return lines == null ? 0 : lines.size();
    }

    // ---------------- 购物车操作 ----------------

    /** 点商品网格：已有该商品则加 1，没有则新增一行 */
    public void addProduct(Product product) {
        List<SaleCartLine> lines = currentLines();
        for (SaleCartLine line : lines) {
            if (line.productId.equals(product.id)) {
                line.quantityMilli += DEFAULT_STEP_MILLI;
                publish(lines);
                return;
            }
        }
        lines.add(new SaleCartLine(product.id, product.name,
                product.salePriceCents, DEFAULT_STEP_MILLI));
        publish(lines);
    }

    public void increaseQuantity(String productId) {
        List<SaleCartLine> lines = currentLines();
        for (SaleCartLine line : lines) {
            if (line.productId.equals(productId)) {
                line.quantityMilli += DEFAULT_STEP_MILLI;
                publish(lines);
                return;
            }
        }
    }

    /** 减到 0 时整行移除 */
    public void decreaseQuantity(String productId) {
        List<SaleCartLine> lines = currentLines();
        for (int i = 0; i < lines.size(); i++) {
            SaleCartLine line = lines.get(i);
            if (line.productId.equals(productId)) {
                line.quantityMilli -= DEFAULT_STEP_MILLI;
                if (line.quantityMilli <= 0) {
                    lines.remove(i);
                }
                publish(lines);
                return;
            }
        }
    }

    /** 直接输入精确数量（称重商品用）。数量 <= 0 时移除该行 */
    public void setQuantity(String productId, long quantityMilli) {
        List<SaleCartLine> lines = currentLines();
        for (int i = 0; i < lines.size(); i++) {
            SaleCartLine line = lines.get(i);
            if (line.productId.equals(productId)) {
                if (quantityMilli <= 0) {
                    lines.remove(i);
                } else {
                    line.quantityMilli = quantityMilli;
                }
                publish(lines);
                return;
            }
        }
    }

    /**
     * 新增或编辑一行（行编辑弹窗的出口）：
     * 购物车里已有该商品 → 覆盖单价和数量；没有 → 新增一行。
     * 数量 <= 0 视为删除该行。单价与数量都以用户在弹窗里输入的为准。
     */
    public void upsertLine(Product product, long unitPriceCents, long quantityMilli) {
        List<SaleCartLine> lines = currentLines();
        for (int i = 0; i < lines.size(); i++) {
            SaleCartLine line = lines.get(i);
            if (line.productId.equals(product.id)) {
                if (quantityMilli <= 0) {
                    lines.remove(i);
                } else {
                    line.unitPriceCents = unitPriceCents;
                    line.quantityMilli = quantityMilli;
                }
                publish(lines);
                return;
            }
        }
        if (quantityMilli > 0) {
            lines.add(new SaleCartLine(product.id, product.name,
                    unitPriceCents, quantityMilli));
            publish(lines);
        }
    }

    /** 购物车里是否已有该商品（列表刷新时保留已加购的商品） */
    public boolean hasProduct(String productId) {
        List<SaleCartLine> lines = currentLines();
        for (SaleCartLine line : lines) {
            if (line.productId.equals(productId)) {
                return true;
            }
        }
        return false;
    }

    /** 完成销售：交给 Repository 的关键事务（含整单优惠与记账时间） */
    public void recordSale(long discountCents, int payMethod,
                           SaveCallback callback) {
        saleRepository.recordSale(currentLines(), discountCents, payMethod, null,
                currentRecordTime(), callback);
    }

    /**
     * 修改模式：加载已保存单据（Repository 回调在主线程）。
     * Activity 用它把旧单明细回填进购物车。
     */
    public void loadSaleDetail(String saleId, SaleRepository.DetailCallback callback) {
        saleRepository.loadSaleDetail(saleId, callback);
    }

    /** 修改模式：把旧单明细回填进购物车（单价/数量用快照值），然后像开新单一样编辑 */
    public void prefillCart(List<SaleCartLine> lines) {
        publish(new ArrayList<>(lines));
    }

    /**
     * 保存修改：Repository 在一个事务里"作废旧单 + 重开新单"。
     * 保存成功后购物车保持当前内容（Activity 会立即 finish）。
     */
    public void editSale(String saleId, long discountCents, int payMethod,
                         SaveCallback callback) {
        saleRepository.editSale(saleId, currentLines(), discountCents, payMethod,
                currentRecordTime(), callback);
    }

    /** 当前选定的记账时间（兜底当下） */
    private long currentRecordTime() {
        Long time = recordTime.getValue();
        return time == null ? System.currentTimeMillis() : time;
    }

    /** 修改模式：回填旧单的整单优惠 */
    public void prefillDiscount(long cents) {
        discountCents.setValue(Math.max(cents, 0));
    }

    // ---------------- 内部工具 ----------------

    private List<SaleCartLine> currentLines() {
        List<SaleCartLine> lines = cart.getValue();
        return lines == null ? new ArrayList<>() : new ArrayList<>(lines);
    }

    /** 购物车变化后统一出口：刷新 LiveData 并重算合计（已扣优惠，优惠不会把合计打成负数） */
    private void publish(List<SaleCartLine> lines) {
        cart.setValue(lines);
        cartLineCount.setValue(lines.size());
        long sum = 0;
        for (SaleCartLine line : lines) {
            sum += line.lineTotalCents();
        }
        Long discount = discountCents.getValue();
        totalCents.setValue(SaleCalculator.orderTotalCents(sum, discount == null ? 0 : discount));
    }
}
