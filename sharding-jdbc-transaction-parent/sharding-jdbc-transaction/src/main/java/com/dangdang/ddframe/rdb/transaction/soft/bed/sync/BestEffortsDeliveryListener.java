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

package com.dangdang.ddframe.rdb.transaction.soft.bed.sync;

import com.dangdang.ddframe.rdb.sharding.constant.SQLType;
import com.dangdang.ddframe.rdb.sharding.executor.event.DMLExecutionEvent;
import com.dangdang.ddframe.rdb.transaction.soft.api.SoftTransactionManager;
import com.dangdang.ddframe.rdb.transaction.soft.api.config.SoftTransactionConfiguration;
import com.dangdang.ddframe.rdb.transaction.soft.bed.BEDSoftTransaction;
import com.dangdang.ddframe.rdb.transaction.soft.storage.TransactionLog;
import com.dangdang.ddframe.rdb.transaction.soft.storage.TransactionLogStorage;
import com.dangdang.ddframe.rdb.transaction.soft.storage.TransactionLogStorageFactory;
import com.google.common.eventbus.AllowConcurrentEvents;
import com.google.common.eventbus.Subscribe;
import lombok.extern.slf4j.Slf4j;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import static com.dangdang.ddframe.rdb.transaction.soft.constants.SoftTransactionType.BestEffortsDelivery;

/**
 * Best efforts delivery B.A.S.E transaction listener. 最大努力送达型事务监听器，负责记录事务日志、同步重试执行失败 SQL
 * 
 * @author zhangliang
 */
@Slf4j
public final class BestEffortsDeliveryListener {
    
    @Subscribe
    @AllowConcurrentEvents  // 是否允许并发执行，即线程安全
    public void listen(final DMLExecutionEvent event) {
        if (!isProcessContinuously()) {
            return;
        }
        SoftTransactionConfiguration transactionConfig = SoftTransactionManager.getCurrentTransactionConfiguration().get();
        TransactionLogStorage transactionLogStorage = TransactionLogStorageFactory.createTransactionLogStorage(transactionConfig.buildTransactionLogDataSource());
        BEDSoftTransaction bedSoftTransaction = (BEDSoftTransaction) SoftTransactionManager.getCurrentTransaction().get();
        switch (event.getEventExecutionType()) {
            case BEFORE_EXECUTE:  // 执行前，插入事务日志
                //TODO for batch SQL need split to 2-level records 对于批量执行的SQL需要解析成两层列表
                transactionLogStorage.add(new TransactionLog(event.getId(), bedSoftTransaction.getTransactionId(), bedSoftTransaction.getTransactionType(), 
                        event.getDataSource(), event.getSql(), event.getParameters(), System.currentTimeMillis(), 0));
                return;
            case EXECUTE_SUCCESS:  // 执行成功，移除事务日志
                transactionLogStorage.remove(event.getId());
                return;
            case EXECUTE_FAILURE:  // 执行失败，同步重试
                boolean deliverySuccess = false;
                for (int i = 0; i < transactionConfig.getSyncMaxDeliveryTryTimes(); i++) { // 同步【多次】重试
                    if (deliverySuccess) {
                        return;
                    }
                    boolean isNewConnection = false;
                    Connection conn = null;
                    PreparedStatement preparedStatement = null;
                    try {
                        conn = bedSoftTransaction.getConnection().getConnection(event.getDataSource(), SQLType.DML); // 获得数据库连接
                        if (!isValidConnection(conn)) { // 因为可能执行失败是数据库连接异常，所以判断一次，如果无效，重新获取数据库连接
                            bedSoftTransaction.getConnection().release(conn);
                            conn = bedSoftTransaction.getConnection().getConnection(event.getDataSource(), SQLType.DML);
                            isNewConnection = true;
                        }
                        preparedStatement = conn.prepareStatement(event.getSql());
                        //TODO for batch event need split to 2-level records // 同步重试
                        for (int parameterIndex = 0; parameterIndex < event.getParameters().size(); parameterIndex++) {
                            preparedStatement.setObject(parameterIndex + 1, event.getParameters().get(parameterIndex));
                        }
                        preparedStatement.executeUpdate();
                        deliverySuccess = true;
                        transactionLogStorage.remove(event.getId()); // 同步重试成功，移除事务日志
                    } catch (final SQLException ex) {
                        log.error(String.format("Delivery times %s error, max try times is %s", i + 1, transactionConfig.getSyncMaxDeliveryTryTimes()), ex);
                    } finally {
                        close(isNewConnection, conn, preparedStatement);
                    }
                }
                return;
            default: 
                throw new UnsupportedOperationException(event.getEventExecutionType().toString());
        }
    }
    
    private boolean isProcessContinuously() {
        return SoftTransactionManager.getCurrentTransaction().isPresent()
                && BestEffortsDelivery == SoftTransactionManager.getCurrentTransaction().get().getTransactionType();
    }
    // 通过 SELECT 1 校验数据库连接是否有效
    private boolean isValidConnection(final Connection conn) {
        try (PreparedStatement preparedStatement = conn.prepareStatement("SELECT 1")) {
            try (ResultSet rs = preparedStatement.executeQuery()) {
                return rs.next() && 1 == rs.getInt("1");
            }
        } catch (final SQLException ex) {
            return false;
        }
    }
    // 关闭释放预编译SQL对象和数据库连接
    private void close(final boolean isNewConnection, final Connection conn, final PreparedStatement preparedStatement) {
        if (null != preparedStatement) {
            try {
                preparedStatement.close();
            } catch (final SQLException ex) {
                log.error("PreparedStatement closed error:", ex);
            }
        }
        if (isNewConnection && null != conn) {
            try {
                conn.close();
            } catch (final SQLException ex) {
                log.error("Connection closed error:", ex);
            }
        }
    }
}
