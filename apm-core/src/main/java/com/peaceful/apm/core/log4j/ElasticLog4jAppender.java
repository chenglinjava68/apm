package com.peaceful.apm.core.log4j;

import com.google.common.base.Throwables;

import com.peaceful.apm.core.helper.Log;
import com.peaceful.apm.core.helper.NetHelper;
import com.peaceful.apm.core.store.MetricElasticStore;

import org.apache.log4j.AppenderSkeleton;
import org.apache.log4j.spi.LoggingEvent;
import org.perf4j.GroupedTimingStatistics;
import org.perf4j.TimingStatistics;

import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * 如果你使用log4j组件，通过该appender可以把数据写入到elasticsearch 必须作为 {@link org.perf4j.log4j.AsyncCoalescingStatisticsAppender}的子Appender
 *
 * @author WangJun
 * @version 1.0 16/6/25
 */
public class ElasticLog4jAppender extends AppenderSkeleton {

    private String host;
    private int port = 9300;
    private String clusterName = "elasticsearch";
    private String indexPrefix;
    private MetricElasticStore metricsFromElastic;

    public void setHost(String host) {
        this.host = host;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public void setIndexPrefix(String indexPrefix) {
        this.indexPrefix = indexPrefix;
    }

    public void setClusterName(String clusterName) {
        this.clusterName = clusterName;
    }

    @Override
    public void activateOptions() {
        if (!NetHelper.pingIpPort(host, port, 3)) {
            Log.warn("ElasticSearch connection fail, host:" + host + " port:" + port);
            this.closed = true;
            return;
        }
        metricsFromElastic = new MetricElasticStore(host, port, clusterName, indexPrefix);
    }

    public void save(Map<String, TimingStatistics> timingStatisticsSortedMap, long interval, TimeUnit timeUnit) {
        if (metricsFromElastic != null)
            metricsFromElastic.saveStatisticsData(timingStatisticsSortedMap, interval, timeUnit);
    }

    @Override
    protected void append(LoggingEvent loggingEvent) {
        if (this.closed) {
            return;
        }
        Object logMessage = loggingEvent.getMessage();
        if (logMessage instanceof GroupedTimingStatistics) {
            GroupedTimingStatistics statistics = (GroupedTimingStatistics) logMessage;

            try {
                SortedMap<String, TimingStatistics> timingStatisticsSortedMap = statistics.getStatisticsByTag();
                save(timingStatisticsSortedMap, statistics.getStopTime() - statistics.getStartTime(), TimeUnit.MILLISECONDS);
            } catch (Exception e) {
                getErrorHandler().error(Throwables.getStackTraceAsString(e));
            }
        }
    }

    @Override
    public void close() {
        this.closed = true;
    }

    @Override
    public boolean requiresLayout() {
        return false;
    }

}
