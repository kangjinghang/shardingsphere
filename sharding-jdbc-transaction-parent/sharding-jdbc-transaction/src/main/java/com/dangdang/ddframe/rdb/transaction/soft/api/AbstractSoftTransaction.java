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

package com.dangdang.ddframe.rdb.transaction.soft.api;

import com.dangdang.ddframe.rdb.sharding.executor.threadlocal.ExecutorExceptionHandler;
import com.dangdang.ddframe.rdb.sharding.jdbc.core.connection.ShardingConnection;
import com.dangdang.ddframe.rdb.transaction.soft.constants.SoftTransactionType;
import com.google.common.base.Preconditions;
import lombok.Getter;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.UUID;

/**
 * B.A.S.E transaction abstract class.
 * 
 * @author zhangliang 
 */
public abstract class AbstractSoftTransaction {
    // 分片连接原自动提交状态
    private boolean previousAutoCommit;
    // 分片连接
    @Getter
    private ShardingConnection connection;
    // 事务类型
    @Getter
    private SoftTransactionType transactionType;
    // 事务编号
    @Getter
    private String transactionId;
    // 开启柔性事务方法，提供给子类调用
    protected final void beginInternal(final Connection conn, final SoftTransactionType type) throws SQLException {
        // TODO if in traditional transaction, then throw exception 判断如果在传统事务中，则抛异常
        Preconditions.checkArgument(conn instanceof ShardingConnection, "Only ShardingConnection can support eventual consistency transaction.");
        ExecutorExceptionHandler.setExceptionThrown(false); // 设置执行错误，不抛出异常
        connection = (ShardingConnection) conn;
        transactionType = type;
        previousAutoCommit = connection.getAutoCommit(); // 设置自动提交状态
        connection.setAutoCommit(true); // 设置执行自动提交，使用最大努力型事务时，上层业务执行 SQL 会马上提交，即使调用 Connection#rollback() 也是无法回滚的
        // TODO replace to snowflake 生成事务编号
        transactionId = UUID.randomUUID().toString();
    }
    
    /**
     * End transaction. 结束柔性事务方法，提供给子类调用
     * 
     * @throws SQLException SQL exception
     */
    public final void end() throws SQLException {
        if (connection != null) {
            ExecutorExceptionHandler.setExceptionThrown(true);
            connection.setAutoCommit(previousAutoCommit);
            SoftTransactionManager.closeCurrentTransactionManager();
        }
    }
}
