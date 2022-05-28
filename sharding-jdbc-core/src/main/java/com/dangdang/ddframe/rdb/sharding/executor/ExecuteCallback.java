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

/**
 * Statement execute callback interface.
 * 
 * @param <T> class type of return value
 * 
 * @author gaohongtao
 * @author zhangliang
 */
public interface ExecuteCallback<T> {
    
    /**
     * Execute task. 执行任务
     * 
     * @param baseStatementUnit statement execute unit 语句对象执行单元
     * @return execute result 处理结果
     * @throws Exception execute exception 执行期异常
     */
    T execute(BaseStatementUnit baseStatementUnit) throws Exception;
}
