/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.shardingsphere.underlying.rewrite.engine;

import org.apache.shardingsphere.underlying.rewrite.context.SQLRewriteContext;
import org.apache.shardingsphere.underlying.rewrite.sql.impl.DefaultSQLBuilder;

/**
 * SQL rewrite engine.
 */
public final class SQLRewriteEngine {
    
    /**
     * Rewrite SQL and parameters. 通过 DefaultSQLBuilder 类对象的 toSQL() 生成了改写后 SQL，然后创建了 SQLRewriteResult 实例
     *
     * @param sqlRewriteContext SQL rewrite context
     * @return SQL rewrite result
     */
    public SQLRewriteResult rewrite(final SQLRewriteContext sqlRewriteContext) { // 将 SQL 改写上下文中 Token 转化成 SQL， 获取改写后参数，然后构造成 SQL 改写结果返回
        return new SQLRewriteResult(new DefaultSQLBuilder(sqlRewriteContext).toSQL(), sqlRewriteContext.getParameterBuilder().getParameters());
    }
}
