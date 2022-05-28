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

package com.dangdang.ddframe.rdb.sharding.api.rule;

/**
 * Dynamic Sharding data unit node. 动态表的分库分表数据单元。逻辑表和真实表不一定需要在配置规则中静态配置。
 * 比如按照日期分片的场景，真实表的名称随着时间的推移会产生变化。
 * <p>Dynamic data node do not need configure actual tables, it will get actual tables depended on sharding algorithm.</p>
 * 
 * @author zhangliang
 */
public final class DynamicDataNode extends DataNode {
    
    private static final String DYNAMIC_TABLE_PLACEHOLDER = "SHARDING_JDBC DYNAMIC_TABLE_PLACEHOLDER";
    
    /**
     * Constructs a data node with data source name, actual table is unnecessary.
     * 
     * @param dataSourceName data source name
     */
    public DynamicDataNode(final String dataSourceName) {
        super(dataSourceName, DYNAMIC_TABLE_PLACEHOLDER);
    }
}
