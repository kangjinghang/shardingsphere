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

package com.dangdang.ddframe.rdb.sharding.executor;

import com.dangdang.ddframe.rdb.sharding.constant.SQLType;
import com.dangdang.ddframe.rdb.sharding.exception.ShardingJdbcException;
import com.dangdang.ddframe.rdb.sharding.executor.event.AbstractExecutionEvent;
import com.dangdang.ddframe.rdb.sharding.executor.event.DMLExecutionEvent;
import com.dangdang.ddframe.rdb.sharding.executor.event.DQLExecutionEvent;
import com.dangdang.ddframe.rdb.sharding.executor.event.EventExecutionType;
import com.dangdang.ddframe.rdb.sharding.executor.threadlocal.ExecutorDataMap;
import com.dangdang.ddframe.rdb.sharding.executor.threadlocal.ExecutorExceptionHandler;
import com.dangdang.ddframe.rdb.sharding.executor.type.batch.BatchPreparedStatementUnit;
import com.dangdang.ddframe.rdb.sharding.executor.type.prepared.PreparedStatementUnit;
import com.dangdang.ddframe.rdb.sharding.executor.type.statement.StatementUnit;
import com.dangdang.ddframe.rdb.sharding.util.EventBusInstance;
import com.google.common.base.Optional;
import com.google.common.collect.Lists;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import lombok.extern.slf4j.Slf4j;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * SQL execute engine. SQL执行引擎
 * 
 * @author gaohongtao
 * @author zhangliang
 */
@Slf4j
public final class ExecutorEngine implements AutoCloseable {
    // Guava 提供的继承自 ExecutorService 的线程服务接口，提供创建 ListenableFuture 功能
    private final ListeningExecutorService executorService;
    
    public ExecutorEngine(final int executorSize) {
        executorService = MoreExecutors.listeningDecorator(new ThreadPoolExecutor(
                executorSize, executorSize, 0, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<Runnable>(), new ThreadFactoryBuilder().setDaemon(true).setNameFormat("ShardingJDBC-%d").build()));
        MoreExecutors.addDelayedShutdownHook(executorService, 60, TimeUnit.SECONDS); // 应用关闭时，等待所有任务全部完成再关闭。默认配置等待时间为 60 秒，建议将等待时间做成可配的
    }
    
    /**
     * Execute statement. 执行Statement
     *
     * @param sqlType SQL type SQL类型
     * @param statementUnits statement execute unit 语句对象执行单元集合
     * @param executeCallback statement execute callback 执行回调函数
     * @param <T> class type of return value 返回值类型
     * @return execute result 执行结果
     */
    public <T> List<T> executeStatement(final SQLType sqlType, final Collection<StatementUnit> statementUnits, final ExecuteCallback<T> executeCallback) {
        return execute(sqlType, statementUnits, Collections.<List<Object>>emptyList(), executeCallback);
    }
    
    /**
     * Execute prepared statement. 执行 PreparedStatement
     *
     * @param sqlType SQL type SQL类型
     * @param preparedStatementUnits prepared statement execute unit 语句对象执行单元集合
     * @param parameters parameters for SQL placeholder 参数列表
     * @param executeCallback prepared statement execute callback 执行回调函数
     * @param <T> class type of return value 返回值类型
     * @return execute result 执行结果
     */
    public <T> List<T> executePreparedStatement(
            final SQLType sqlType, final Collection<PreparedStatementUnit> preparedStatementUnits, final List<Object> parameters, final ExecuteCallback<T> executeCallback) {
        return execute(sqlType, preparedStatementUnits, Collections.singletonList(parameters), executeCallback);
    }
    
    /**
     * Execute add batch. 执行 Batch
     *
     * @param sqlType SQL type SQL类型
     * @param batchPreparedStatementUnits prepared statement execute unit for batch 语句对象执行单元集合
     * @param parameterSets parameters for SQL placeholder 参数列表集
     * @param executeCallback prepared statement execute callback 执行回调函数
     * @return execute result 执行结果
     */
    public List<int[]> executeBatch(
            final SQLType sqlType, final Collection<BatchPreparedStatementUnit> batchPreparedStatementUnits, final List<List<Object>> parameterSets, final ExecuteCallback<int[]> executeCallback) {
        return execute(sqlType, batchPreparedStatementUnits, parameterSets, executeCallback);
    }
    
    private  <T> List<T> execute(
            final SQLType sqlType, final Collection<? extends BaseStatementUnit> baseStatementUnits, final List<List<Object>> parameterSets, final ExecuteCallback<T> executeCallback) {
        if (baseStatementUnits.isEmpty()) {
            return Collections.emptyList();
        }
        Iterator<? extends BaseStatementUnit> iterator = baseStatementUnits.iterator();
        BaseStatementUnit firstInput = iterator.next();
        ListenableFuture<List<T>> restFutures = asyncExecute(sqlType, Lists.newArrayList(iterator), parameterSets, executeCallback);  // 第二个任务开始所有 SQL任务 提交线程池【异步】执行任务
        T firstOutput;
        List<T> restOutputs;
        try {
            firstOutput = syncExecute(sqlType, firstInput, parameterSets, executeCallback);  // 第一个任务【同步】执行任务
            restOutputs = restFutures.get(); // 等待第二个任务开始所有 SQL 任务完成
            //CHECKSTYLE:OFF
        } catch (final Exception ex) {
            //CHECKSTYLE:ON
            ExecutorExceptionHandler.handleException(ex);
            return null;
        }
        List<T> result = Lists.newLinkedList(restOutputs); // 返回结果
        result.add(0, firstOutput);
        return result;
    }
    // 第二个开始的任务提交线程池异步调用 #executeInternal() 执行任务
    private <T> ListenableFuture<List<T>> asyncExecute(
            final SQLType sqlType, final Collection<BaseStatementUnit> baseStatementUnits, final List<List<Object>> parameterSets, final ExecuteCallback<T> executeCallback) {
        List<ListenableFuture<T>> result = new ArrayList<>(baseStatementUnits.size());
        final boolean isExceptionThrown = ExecutorExceptionHandler.isExceptionThrown();
        final Map<String, Object> dataMap = ExecutorDataMap.getDataMap();
        for (final BaseStatementUnit each : baseStatementUnits) {
            result.add(executorService.submit(new Callable<T>() { // 提交线程池【异步】执行任务
                
                @Override
                public T call() throws Exception {
                    return executeInternal(sqlType, each, parameterSets, executeCallback, isExceptionThrown, dataMap);
                }
            }));
        }
        return Futures.allAsList(result); // 返回 ListenableFuture
    }
    // 【同步】执行任务
    private <T> T syncExecute(final SQLType sqlType, final BaseStatementUnit baseStatementUnit, final List<List<Object>> parameterSets, final ExecuteCallback<T> executeCallback) throws Exception {
        return executeInternal(sqlType, baseStatementUnit, parameterSets, executeCallback, ExecutorExceptionHandler.isExceptionThrown(), ExecutorDataMap.getDataMap());
    }
    
    private <T> T executeInternal(final SQLType sqlType, final BaseStatementUnit baseStatementUnit, final List<List<Object>> parameterSets, final ExecuteCallback<T> executeCallback, 
                          final boolean isExceptionThrown, final Map<String, Object> dataMap) throws Exception {
        synchronized (baseStatementUnit.getStatement().getConnection()) {
            T result;
            ExecutorExceptionHandler.setExceptionThrown(isExceptionThrown);
            ExecutorDataMap.setDataMap(dataMap);
            List<AbstractExecutionEvent> events = new LinkedList<>();
            if (parameterSets.isEmpty()) {  // 生成 Event
                events.add(getExecutionEvent(sqlType, baseStatementUnit, Collections.emptyList()));
            }
            for (List<Object> each : parameterSets) {
                events.add(getExecutionEvent(sqlType, baseStatementUnit, each));
            }
            for (AbstractExecutionEvent event : events) {  // EventBus 发布 EventExecutionType.BEFORE_EXECUTE
                EventBusInstance.getInstance().post(event);
            }
            try {
                result = executeCallback.execute(baseStatementUnit); // 执行回调函数
            } catch (final SQLException ex) {
                for (AbstractExecutionEvent each : events) { // EventBus 发布 EventExecutionType.EXECUTE_FAILURE
                    each.setEventExecutionType(EventExecutionType.EXECUTE_FAILURE);
                    each.setException(Optional.of(ex));
                    EventBusInstance.getInstance().post(each);
                    ExecutorExceptionHandler.handleException(ex);
                }
                return null;
            }
            for (AbstractExecutionEvent each : events) { // EventBus 发布 EventExecutionType.EXECUTE_SUCCESS
                each.setEventExecutionType(EventExecutionType.EXECUTE_SUCCESS);
                EventBusInstance.getInstance().post(each);
            }
            return result;
        }
    }
    
    private AbstractExecutionEvent getExecutionEvent(final SQLType sqlType, final BaseStatementUnit baseStatementUnit, final List<Object> parameters) {
        AbstractExecutionEvent result;
        if (SQLType.DQL == sqlType) {
            result = new DQLExecutionEvent(baseStatementUnit.getSqlExecutionUnit().getDataSource(), baseStatementUnit.getSqlExecutionUnit().getSql(), parameters);
        } else {
            result = new DMLExecutionEvent(baseStatementUnit.getSqlExecutionUnit().getDataSource(), baseStatementUnit.getSqlExecutionUnit().getSql(), parameters);
        }
        return result;
    }
    
    @Override
    public void close() {
        executorService.shutdownNow(); //  尝试使用 Thread.interrupt() 打断正在执行中的任务，未执行的任务不再执行。建议打印下哪些任务未执行，因为 SQL 未执行，可能数据未能持久化。
        try {
            executorService.awaitTermination(5, TimeUnit.SECONDS); //  因为 #shutdownNow() 打断不是立即结束，需要一个过程，因此这里等待了 5 秒。
        } catch (final InterruptedException ignored) {
        }
        if (!executorService.isTerminated()) { // 等待 5 秒后，线程池不一定已经关闭，此时抛出异常给上层。建议打印下日志，记录出现这个情况。
            throw new ShardingJdbcException("ExecutorEngine can not been terminated");
        }
    }
}
