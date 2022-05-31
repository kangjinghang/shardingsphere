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

package org.apache.shardingsphere.masterslave.route.engine.impl;

import lombok.RequiredArgsConstructor;
import org.apache.shardingsphere.api.hint.HintManager;
import org.apache.shardingsphere.core.rule.MasterSlaveRule;
import org.apache.shardingsphere.sql.parser.sql.statement.SQLStatement;
import org.apache.shardingsphere.sql.parser.sql.statement.dml.SelectStatement;

import java.util.ArrayList;

/**
 * Data source router for master-slave. 计算路由到具体哪个数据库
 */
@RequiredArgsConstructor
public final class MasterSlaveDataSourceRouter {
    
    private final MasterSlaveRule masterSlaveRule;
    
    /**
     * Route. 计算路由到具体哪个数据库
     * 
     * @param sqlStatement SQL statement
     * @return data source name
     */
    public String route(final SQLStatement sqlStatement) {
        if (isMasterRoute(sqlStatement)) { // 需要路由到主库(SQL中包含锁例如select for update、非select、通过hint指定主库路由)
            MasterVisitedManager.setMasterVisited();
            return masterSlaveRule.getMasterDataSourceName(); // 真正的负责执行
        }
        return masterSlaveRule.getLoadBalanceAlgorithm().getDataSource( // 根据负载均衡算法计算要访问的数据源
                masterSlaveRule.getName(), masterSlaveRule.getMasterDataSourceName(), new ArrayList<>(masterSlaveRule.getSlaveDataSourceNames()));
    }
    // select for update || 非select || 通过hint指定主库路由
    private boolean isMasterRoute(final SQLStatement sqlStatement) {
        return containsLockSegment(sqlStatement) || !(sqlStatement instanceof SelectStatement) || MasterVisitedManager.isMasterVisited() || HintManager.isMasterRouteOnly();
    }
    // select for update
    private boolean containsLockSegment(final SQLStatement sqlStatement) {
        return sqlStatement instanceof SelectStatement && ((SelectStatement) sqlStatement).getLock().isPresent();
    }
}
