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

package com.dangdang.ddframe.rdb.sharding.executor.threadlocal;

import com.dangdang.ddframe.rdb.sharding.exception.ShardingJdbcException;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Executor runtime exception handler.
 * 
 * @author zhangliang
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
@Slf4j
public final class ExecutorExceptionHandler {
    
    private static final ThreadLocal<Boolean> IS_EXCEPTION_THROWN = new ThreadLocal<>();
    
    /**
     * Set throw exception if error occur or not. 设置执行 SQL 错误时，也不抛出异常
     *
     * @param isExceptionThrown throw exception if error occur or not
     */
    public static void setExceptionThrown(final boolean isExceptionThrown) {
        ExecutorExceptionHandler.IS_EXCEPTION_THROWN.set(isExceptionThrown);
    }
    
    /**
     * Get throw exception if error occur or not.
     * 
     * @return throw exception if error occur or not
     */
    public static boolean isExceptionThrown() {
        return null == IS_EXCEPTION_THROWN.get() ? true : IS_EXCEPTION_THROWN.get();
    }
    
    /**
     * Handle exception. 
     * 
     * @param exception to be handled exception
     */
    public static void handleException(final Exception exception) {
        if (isExceptionThrown()) {
            throw new ShardingJdbcException(exception);
        }
        log.error("exception occur: ", exception);
    }
}
