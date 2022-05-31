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

package org.apache.shardingsphere.sql.parser.binder.statement;

import org.apache.shardingsphere.sql.parser.binder.segment.table.TablesContext;
import org.apache.shardingsphere.sql.parser.sql.statement.SQLStatement;

/**
 * SQL statement context. 相当于 SQLStatement 的二次处理类，它也是后续路由、改写等环节间传递的上下文对象，每种 Context 往往对应一个 ContextEngine
 * 
 * @param <T> type of SQL statement
 */
public interface SQLStatementContext<T extends SQLStatement> {
    
    /**
     * Get SQL statement.
     * 
     * @return SQL statement
     */
    T getSqlStatement();
    
    /**
     * Get tables context.
     *
     * @return tables context
     */
    TablesContext getTablesContext();
}
