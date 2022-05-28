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

package com.dangdang.ddframe.rdb.sharding.executor.event;

import com.google.common.base.Optional;
import lombok.Getter;
import lombok.Setter;

import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

/**
 * SQL execution event. SQL 执行事件抽象接口
 *
 * @author zhangliang
 */
@Getter
@Setter
public abstract class AbstractExecutionEvent {
    // 事件编号
    private final String id;
    // 数据源
    private final String dataSource;
    // SQL
    private final String sql;
    // 参数
    private final List<Object> parameters;
    // 事件类型
    private EventExecutionType eventExecutionType;
    // 异常
    private Optional<SQLException> exception;
    
    public AbstractExecutionEvent(final String dataSource, final String sql, final List<Object> parameters) {
        id = UUID.randomUUID().toString();
        this.dataSource = dataSource;
        this.sql = sql;
        this.parameters = parameters;
        eventExecutionType = EventExecutionType.BEFORE_EXECUTE;
    }
}
