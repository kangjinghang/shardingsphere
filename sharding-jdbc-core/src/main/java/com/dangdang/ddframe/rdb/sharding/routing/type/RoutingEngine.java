package com.dangdang.ddframe.rdb.sharding.routing.type;

/**
 * Routing engine interface. 路由引擎接口，共有四种实现
 *
 * @author zhangliang
 */
public interface RoutingEngine {
    
    /**
     * Route.
     *
     * @return routing result
     */
    RoutingResult route();
}
