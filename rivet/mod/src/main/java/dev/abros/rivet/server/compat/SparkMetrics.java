package dev.abros.rivet.server.compat;

import com.google.gson.*;
import dev.abros.rivet.core.SparkTimeline;
import java.lang.management.ManagementFactory;

/** Only spark's public API is reflected; no compile/runtime dependency when spark is absent. */
final class SparkMetrics {
    private static Object call(Object object,String type,String method)throws ReflectiveOperationException {
        return Class.forName(type).getMethod(method).invoke(object);
    }
    @SuppressWarnings({"rawtypes","unchecked"})
    private static Object poll(Object statistic,String window,String value,String api)throws ReflectiveOperationException {
        Class type=Class.forName("me.lucko.spark.api.statistic.StatisticWindow$"+window);
        Object selected=Enum.valueOf(type,value);
        return Class.forName("me.lucko.spark.api.statistic.types."+api).getMethod("poll",Enum.class).invoke(statistic,selected);
    }
    static String probe()throws ReflectiveOperationException {
        Class.forName("me.lucko.spark.api.SparkProvider").getMethod("get");
        Class<?> api=Class.forName("me.lucko.spark.api.Spark");
        for(String name:new String[]{"tps","mspt","cpuProcess","cpuSystem"})api.getMethod(name);
        for(String type:new String[]{"DoubleStatistic","GenericStatistic"})Class.forName("me.lucko.spark.api.statistic.types."+type).getMethod("poll",Enum.class);
        for(String window:new String[]{"TicksPerSecond","MillisPerTick","CpuUsage"})Enum.valueOf((Class)Class.forName("me.lucko.spark.api.statistic.StatisticWindow$"+window),"SECONDS_10");
        Class<?> average=Class.forName("me.lucko.spark.api.statistic.misc.DoubleAverageInfo");average.getMethod("mean");average.getMethod("percentile95th");
        return "spark public metrics API";
    }
    static JsonObject read(long now)throws Exception {
        var row=new JsonObject();row.addProperty("at",now);
        Object api;try{api=Class.forName("me.lucko.spark.api.SparkProvider").getMethod("get").invoke(null);}catch(java.lang.reflect.InvocationTargetException failure){if(failure.getCause() instanceof IllegalStateException)throw new dev.abros.rivet.core.OptionalIntegration.NotReadyException("spark ещё не зарегистрировал API");throw failure;}
        String type="me.lucko.spark.api.Spark";
        Object tps=call(api,type,"tps");if(tps!=null)SparkTimeline.metric(row,"tps",((Number)poll(tps,"TicksPerSecond","SECONDS_10","DoubleStatistic")).doubleValue());
        Object mspt=call(api,type,"mspt");if(mspt!=null){Object average=poll(mspt,"MillisPerTick","SECONDS_10","GenericStatistic");if(average!=null){SparkTimeline.metric(row,"mspt",((Number)call(average,"me.lucko.spark.api.statistic.misc.DoubleAverageInfo","mean")).doubleValue());SparkTimeline.metric(row,"mspt95",((Number)call(average,"me.lucko.spark.api.statistic.misc.DoubleAverageInfo","percentile95th")).doubleValue());}}
        for(String method:new String[]{"cpuProcess","cpuSystem"}){Object cpu=call(api,type,method);if(cpu!=null)SparkTimeline.metric(row,method,((Number)poll(cpu,"CpuUsage","SECONDS_10","DoubleStatistic")).doubleValue()*100);}
        var memory=ManagementFactory.getMemoryMXBean().getHeapMemoryUsage();row.addProperty("heapUsed",memory.getUsed());row.addProperty("heapMax",memory.getMax()>0?memory.getMax():memory.getCommitted());
        var gc=new JsonArray();for(var bean:ManagementFactory.getGarbageCollectorMXBeans()){var item=new JsonObject();item.addProperty("name",bean.getName());item.addProperty("count",bean.getCollectionCount());item.addProperty("millis",bean.getCollectionTime());gc.add(item);}row.add("gc",gc);
        return row;
    }
}
