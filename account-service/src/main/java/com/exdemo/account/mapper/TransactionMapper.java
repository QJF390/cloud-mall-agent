package com.exdemo.account.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.exdemo.account.entity.Transaction;
import org.apache.ibatis.annotations.Mapper;

/**
 * 资金流水 Mapper
 * 表：t_transaction
 */
@Mapper
public interface TransactionMapper extends BaseMapper<Transaction> {
}
