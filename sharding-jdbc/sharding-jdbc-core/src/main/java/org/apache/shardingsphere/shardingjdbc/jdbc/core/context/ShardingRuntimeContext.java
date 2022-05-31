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

package org.apache.shardingsphere.shardingjdbc.jdbc.core.context;

import lombok.Getter;
import org.apache.shardingsphere.core.metadata.ShardingMetaDataLoader;
import org.apache.shardingsphere.core.metadata.ShardingTableMetaDataDecorator;
import org.apache.shardingsphere.core.rule.ShardingRule;
import org.apache.shardingsphere.encrypt.metadata.EncryptTableMetaDataDecorator;
import org.apache.shardingsphere.shardingjdbc.jdbc.core.datasource.metadata.CachedDatabaseMetaData;
import org.apache.shardingsphere.spi.database.type.DatabaseType;
import org.apache.shardingsphere.sql.parser.binder.metadata.schema.SchemaMetaData;
import org.apache.shardingsphere.transaction.ShardingTransactionManagerEngine;
import org.apache.shardingsphere.underlying.common.config.properties.ConfigurationPropertyKey;
import org.apache.shardingsphere.underlying.common.metadata.decorator.SchemaMetaDataDecorator;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Map;
import java.util.Properties;

/**
 * Runtime context for sharding.
 */
@Getter
public final class ShardingRuntimeContext extends MultipleDataSourcesRuntimeContext<ShardingRule> {
    
    private final CachedDatabaseMetaData cachedDatabaseMetaData;
    
    private final ShardingTransactionManagerEngine shardingTransactionManagerEngine;
    // 根据应用传入的 DataSource 的 map，然后生成 DataSourceMetas 与 SchemaMetaData 对象从而构建 ShardingSphereMetaData 实例，其中后者是需要真正的功能 RuntimeContext 类这种实现
    public ShardingRuntimeContext(final Map<String, DataSource> dataSourceMap, final ShardingRule shardingRule, final Properties props, final DatabaseType databaseType) throws SQLException {
        super(dataSourceMap, shardingRule, props, databaseType);
        cachedDatabaseMetaData = createCachedDatabaseMetaData(dataSourceMap); // 创建缓存元数据
        shardingTransactionManagerEngine = new ShardingTransactionManagerEngine(); // 创建事务管理器
        shardingTransactionManagerEngine.init(databaseType, dataSourceMap);
    }
    // 创建缓存元数据
    private CachedDatabaseMetaData createCachedDatabaseMetaData(final Map<String, DataSource> dataSourceMap) throws SQLException {
        try (Connection connection = dataSourceMap.values().iterator().next().getConnection()) { // 通过 Connection 缓存元数据
            return new CachedDatabaseMetaData(connection.getMetaData());
        }
    }
    
    @Override
    protected SchemaMetaData loadSchemaMetaData(final Map<String, DataSource> dataSourceMap) throws SQLException {
        int maxConnectionsSizePerQuery = getProperties().<Integer>getValue(ConfigurationPropertyKey.MAX_CONNECTIONS_SIZE_PER_QUERY);
        boolean isCheckingMetaData = getProperties().<Boolean>getValue(ConfigurationPropertyKey.CHECK_TABLE_METADATA_ENABLED);
        SchemaMetaData result = new ShardingMetaDataLoader(dataSourceMap, getRule(), maxConnectionsSizePerQuery, isCheckingMetaData).load(getDatabaseType());
        result = SchemaMetaDataDecorator.decorate(result, getRule(), new ShardingTableMetaDataDecorator());
        if (!getRule().getEncryptRule().getEncryptTableNames().isEmpty()) {
            result = SchemaMetaDataDecorator.decorate(result, getRule().getEncryptRule(), new EncryptTableMetaDataDecorator());
        }
        return result;
    }
    
    @Override
    public void close() throws Exception {
        shardingTransactionManagerEngine.close();
        super.close();
    }
}
