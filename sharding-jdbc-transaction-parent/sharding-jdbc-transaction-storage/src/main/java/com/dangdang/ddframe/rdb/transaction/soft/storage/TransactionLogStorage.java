/*
 * Copyright 1999-2015 dangdang.com.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * </p>
 */

package com.dangdang.ddframe.rdb.transaction.soft.storage;

import java.sql.Connection;
import java.util.List;

/**
 * Transaction log storage interface. 事务日志存储器接口
 * 
 * @author zhangliang
 */
public interface TransactionLogStorage {
    
    /**
     * Save transaction log. 存储事务日志
     * 
     * @param transactionLog transaction log 事务日志
     */
    void add(TransactionLog transactionLog);
    
    /**
     * Remove transaction log. 根据主键删除事务日志
     * 
     * @param id transaction log id 事务日志主键
     */
    void remove(String id);
    
    /**
     * Find eligible transaction logs. 读取需要处理的事务日志
     * 
     * <p>To be processed transaction logs: </p>
     * <p>1. retry times less than max retry times. 异步处理次数小于最大处理次数</p>
     * <p>2. transaction log last retry timestamp interval early than last retry timestamp. 异步处理的事务日志早于异步处理的间隔时间</p>
     * 
     * @param size size of fetch transaction log 获取日志的数量
     * @param maxDeliveryTryTimes max delivery try times 事务送达的最大尝试次数
     * @param maxDeliveryTryDelayMillis max delivery try delay millis 执行送达事务的延迟毫秒数
     * @return eligible transaction logs
     */
    List<TransactionLog> findEligibleTransactionLogs(int size, int maxDeliveryTryTimes, long maxDeliveryTryDelayMillis);
    
    /**
     * Increase asynchronized delivery try times. 增加事务日志异步重试次数
     * 
     * @param id transaction log id 事务主键
     */
    void increaseAsyncDeliveryTryTimes(String id);
    
    /**
     * Process transaction logs. 处理事务数据
     *
     * @param connection connection for business app 业务数据库连接
     * @param transactionLog transaction log 事务日志
     * @param maxDeliveryTryTimes max delivery try times 事务送达的最大尝试次数
     * @return process success or not
     */
    boolean processData(Connection connection, TransactionLog transactionLog, int maxDeliveryTryTimes);
}
