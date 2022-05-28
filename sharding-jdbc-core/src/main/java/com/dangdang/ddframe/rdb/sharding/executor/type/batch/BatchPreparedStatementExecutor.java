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

package com.dangdang.ddframe.rdb.sharding.executor.type.batch;

import com.dangdang.ddframe.rdb.sharding.constant.DatabaseType;
import com.dangdang.ddframe.rdb.sharding.constant.SQLType;
import com.dangdang.ddframe.rdb.sharding.executor.BaseStatementUnit;
import com.dangdang.ddframe.rdb.sharding.executor.ExecuteCallback;
import com.dangdang.ddframe.rdb.sharding.executor.ExecutorEngine;
import lombok.RequiredArgsConstructor;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * PreparedStatement Executor for  multiple threads to process add batch. 批量预编译语句对象请求的执行器
 * 
 * @author zhangliang
 */
@RequiredArgsConstructor
public final class BatchPreparedStatementExecutor {
    
    private final ExecutorEngine executorEngine;
    
    private final DatabaseType dbType;
    
    private final SQLType sqlType;
    
    private final Collection<BatchPreparedStatementUnit> batchPreparedStatementUnits;
    
    private final List<List<Object>> parameterSets;
    
    /**
     * Execute batch. 执行批量SQL
     * 
     * @return execute results 执行结果
     */
    public int[] executeBatch() {
        return accumulate(executorEngine.executeBatch(sqlType, batchPreparedStatementUnits, parameterSets, new ExecuteCallback<int[]>() {
            
            @Override
            public int[] execute(final BaseStatementUnit baseStatementUnit) throws Exception {
                return baseStatementUnit.getStatement().executeBatch();
            }
        }));
    }
    // 计算每个语句的更新数量
    private int[] accumulate(final List<int[]> results) {
        int[] result = new int[parameterSets.size()];
        int count = 0;
        for (BatchPreparedStatementUnit each : batchPreparedStatementUnits) {  // 每个语句按照顺序，读取到其对应的每个分片SQL影响的行数进行累加
            for (Map.Entry<Integer, Integer> entry : each.getJdbcAndActualAddBatchCallTimesMap().entrySet()) {
                int value = null == results.get(count) ? 0 : results.get(count)[entry.getValue()];
                if (DatabaseType.Oracle == dbType) {
                    result[entry.getKey()] = value;
                } else {
                    result[entry.getKey()] += value;
                }
            }
            count++;
        }
        return result;
    }
}
