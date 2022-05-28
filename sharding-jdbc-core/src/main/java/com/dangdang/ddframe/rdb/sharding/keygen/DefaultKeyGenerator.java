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

package com.dangdang.ddframe.rdb.sharding.keygen;

import com.google.common.base.Preconditions;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;

/**
 * Default distributed primary key generator. 默认的主键生成器。
 * 
 * <p>
 * Use snowflake algorithm. Length is 64 bit.
 * </p>
 * 
 * <pre>
 * 1bit   sign bit.
 * 41bits timestamp offset from 2016.11.01(Sharding-JDBC distributed primary key published data) to now.
 * 10bits worker process id.
 * 12bits auto increment offset in one mills
 * </pre>
 * 
 * <p>
 * Call @{@code DefaultKeyGenerator.setWorkerId} to set.
 * </p>
 * 
 * @author gaohongtao
 */
@Slf4j
public final class DefaultKeyGenerator implements KeyGenerator {
    // 时间偏移量，从2016年11月1日零点开始
    public static final long EPOCH;
    // 自增量占用比特
    private static final long SEQUENCE_BITS = 12L;
    // 工作进程ID比特
    private static final long WORKER_ID_BITS = 10L;
    // 自增量掩码（最大值）
    private static final long SEQUENCE_MASK = (1 << SEQUENCE_BITS) - 1;
    // 工作进程ID左移比特数（位数）
    private static final long WORKER_ID_LEFT_SHIFT_BITS = SEQUENCE_BITS;
    // 时间戳左移比特数（位数）
    private static final long TIMESTAMP_LEFT_SHIFT_BITS = WORKER_ID_LEFT_SHIFT_BITS + WORKER_ID_BITS;
    // 工作进程ID最大值
    private static final long WORKER_ID_MAX_VALUE = 1L << WORKER_ID_BITS;
    
    @Setter
    private static TimeService timeService = new TimeService();
    // 工作进程ID
    private static long workerId;
    
    static {
        Calendar calendar = Calendar.getInstance();
        calendar.set(2016, Calendar.NOVEMBER, 1);
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        EPOCH = calendar.getTimeInMillis();
    }
    // 最后自增量
    private long sequence;
    // 最后生成编号时间戳，单位：毫秒
    private long lastTime;
    
    /**
     * Set work process id. 设置工作进程Id
     * 
     * @param workerId work process id 工作进程Id
     */
    public static void setWorkerId(final long workerId) {
        Preconditions.checkArgument(workerId >= 0L && workerId < WORKER_ID_MAX_VALUE);
        DefaultKeyGenerator.workerId = workerId;
    }
    
    /**
     * Generate key. 生成Id
     * 
     * @return key type is @{@link Long}.
     */
    @Override
    public synchronized Number generateKey() {
        long currentMillis = timeService.getCurrentMillis(); // 保证当前时间大于最后时间。时间回退会导致产生重复id
        Preconditions.checkState(lastTime <= currentMillis, "Clock is moving backwards, last time is %d milliseconds, current time is %d milliseconds", lastTime, currentMillis);
        if (lastTime == currentMillis) {
            if (0L == (sequence = ++sequence & SEQUENCE_MASK)) {  // 当获得序号超过最大值时，归0，并去获得新的时间
                currentMillis = waitUntilNextTime(currentMillis); // 去获得新的时间，直到大于当前时间
            }
        } else {
            sequence = 0;
        }
        lastTime = currentMillis; // 设置最后时间戳
        if (log.isDebugEnabled()) {
            log.debug("{}-{}-{}", new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS").format(new Date(lastTime)), workerId, sequence);
        }
        return ((currentMillis - EPOCH) << TIMESTAMP_LEFT_SHIFT_BITS) | (workerId << WORKER_ID_LEFT_SHIFT_BITS) | sequence; // 生成编号
    }
    // 不停获得时间，直到大于最后时间
    private long waitUntilNextTime(final long lastTime) {
        long time = timeService.getCurrentMillis();
        while (time <= lastTime) {
            time = timeService.getCurrentMillis();
        }
        return time;
    }
}
