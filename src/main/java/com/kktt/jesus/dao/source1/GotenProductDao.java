package com.kktt.jesus.dao.source1;

import com.kktt.jesus.dataobject.GotenProduct;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.springframework.stereotype.Repository;
import tk.mybatis.mapper.common.Mapper;

import java.math.BigDecimal;
import java.util.List;

@Repository
public interface GotenProductDao  extends Mapper<GotenProduct> {

    int insertIgnore(@Param("entity") GotenProduct record);

    @Update("update goten_product set price = #{price} where sku = #{sku}")
    void updatePrice(@Param("sku") Long sku,@Param("price") BigDecimal price);

    @Update("update goten_product set inventory = #{inventory} where sku = #{sku}")
    void updateQuantity(@Param("sku") Long sku,@Param("inventory") Integer inventory);

//    @Select("select * from goten_product")
    List<GotenProduct> selectAll();
}