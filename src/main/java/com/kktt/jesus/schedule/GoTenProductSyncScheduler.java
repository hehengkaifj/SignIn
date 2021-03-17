package com.kktt.jesus.schedule;

import com.kktt.jesus.TaskConsumerComponent;
import com.kktt.jesus.api.ProductConverter;
import com.kktt.jesus.dao.source1.GotenProductDao;
import com.kktt.jesus.dataobject.GotenProduct;
import com.kktt.jesus.service.RedisQueueService;
import com.kktt.jesus.utils.CommonUtil;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import tk.mybatis.mapper.entity.Example;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class GoTenProductSyncScheduler {
    protected static final Logger logger = LoggerFactory.getLogger(GoTenProductSyncScheduler.class);

    public static final String GT_SYNC_PRODUCT_PRICE_QUEUE = "UPDATE_PRICE_QUEUE";
    public static final String GT_SYNC_PRODUCT_INVENTORY_QUEUE = "UPDATE_INVENTORY_QUEUE";

    @Resource
    private GotenProductDao gotenProductDao;
    @Resource
    private ProductConverter productConverter;
    @Resource
    private RedisQueueService redisQueueService;

//    @Scheduled(cron = "0 10 22 * * ?", zone = "GMT+8")
    @Scheduled(fixedDelay = 30000 * 1000, initialDelay = 10 * 1000)
    public void runSyncProduct() {
        Instant now = Instant.now();
        Instant startInstant = now.minus(0, ChronoUnit.DAYS);
        Instant endInstant = now.minus(12, ChronoUnit.DAYS);
        String startDate = startInstant.toString();
        String endDate = endInstant.toString();

        int index = 1;
        int max = productConverter.getProductSize(1,startDate,endDate);
        logger.info("同步商品开始 总页数：{}",max);
        List<GotenProduct> xx = productConverter.getProduct(index,startDate,endDate);
        while (index <= max){
            logger.info("同步商品数量：{},index:{}",xx.size(),index);
            saveProduct(xx);
            index ++;
            xx = productConverter.getProduct(index,startDate,endDate);
        }
        logger.info("同步当天商品完成 index:{}",index);
    }

    @Scheduled(fixedDelay = 30 * 1000, initialDelay = 30 * 1000)
    public void updatePrice() {
        List<GotenProduct> newProductList = gotenProductDao.queryByState(GotenProduct.STATE.NEW);
        if(CollectionUtils.isEmpty(newProductList)){
            return;
        }
        List<Long> skuList = newProductList.stream().map(GotenProduct::getSku).collect(Collectors.toList());
        List<List<Long>> skuGroup = CommonUtil.subCollection(skuList, 1);
        for (List<Long> group : skuGroup) {
            Map<Long, BigDecimal> priceMap = productConverter.getProductPrice(group);
            if(priceMap.isEmpty()){
                gotenProductDao.updateState(group.get(0)+"");
            }
            updateProductPrice(priceMap);
        }
    }


    @Scheduled(fixedDelay = 30 * 1000, initialDelay = 30 * 1000)
    public void updateInventory() {
        List<GotenProduct> taskList = gotenProductDao.queryByState(GotenProduct.STATE.UPDATING);
        if(CollectionUtils.isEmpty(taskList)){
            return;
        }
        List<Long> skuList = taskList.stream().map(GotenProduct::getSku).collect(Collectors.toList());
        List<List<Long>> skuGroup = CommonUtil.subCollection(skuList, 1);
        for (List<Long> group : skuGroup) {
            Map<Long, Integer> priceMap = productConverter.getProductInventory(group);
            updateProductInventory(priceMap);
        }
    }

    private void saveProduct(List<GotenProduct> xx ){
        for (GotenProduct product : xx) {
            gotenProductDao.insertIgnore(product);
        }

        List<String> skuList = xx.stream().map(GotenProduct::getSku).map(Object::toString).collect(Collectors.toList());
        if(CollectionUtils.isEmpty(skuList)){
            return;
        }
        redisQueueService.push(GT_SYNC_PRODUCT_PRICE_QUEUE,skuList);
    }

    private void updateProductPrice(Map<Long,BigDecimal> priceMap) {
        List<Map<String,Object>> datas = new ArrayList<>();
        priceMap.forEach((k,v)->{
            Map<String,Object> item = new HashMap<>();
            item.put("sku",k);
            item.put("price",v);
            datas.add(item);
        });
        if(CollectionUtils.isEmpty(datas)){
            return;
        }
        gotenProductDao.batchUpdatePrice(datas);
    }

    private void updateProductInventory(Map<Long,Integer> inventoryMap) {
        List<Map<String,Object>> datas = new ArrayList<>();
        inventoryMap.forEach((k,v)->{
            Map<String,Object> item = new HashMap<>();
            item.put("sku",k);
            item.put("inventory",v);
            datas.add(item);
        });
        if(CollectionUtils.isEmpty(datas)){
            return;
        }
        gotenProductDao.batchUpdateInventory(datas);
    }

}