package dev.abros.rivet.core;
import java.util.function.*;
import java.util.concurrent.atomic.AtomicLong;
import com.google.gson.JsonObject;
/** Cached API contract checks and independent, fail-closed optional capabilities. */
public final class OptionalIntegration {
 @FunctionalInterface public interface Operation<T>{T run()throws Exception;}
 /** A supported API whose runtime is still starting; does not consume the failure budget. */
 public static final class NotReadyException extends Exception {public NotReadyException(String message){super(message);}}
 private static final AtomicLong CHANGES=new AtomicLong();
 private final String id;private final BooleanSupplier installed,enabled;private final BiConsumer<String,Throwable> logger;private final Supplier<String> version;private final Operation<String> probe;
 private int failures;private String error="",api="",waiting="";private Boolean supported;private long revision;
 public OptionalIntegration(String id,BooleanSupplier installed,BooleanSupplier enabled,BiConsumer<String,Throwable> logger){this(id,installed,enabled,()->"неизвестно",()->"",logger);}
 public OptionalIntegration(String id,BooleanSupplier installed,BooleanSupplier enabled,Supplier<String> version,Operation<String> probe,BiConsumer<String,Throwable> logger){this.id=id;this.installed=installed;this.enabled=enabled;this.version=version;this.probe=probe;this.logger=logger;}
 public synchronized boolean available(){if(!enabled.getAsBoolean()||!installed.getAsBoolean()||failures>=3)return false;if(supported==null){try{api=probe.run();supported=true;}catch(Exception|LinkageError failure){supported=false;error=reason(failure);logger.accept(id,failure);}changed();}return supported;}
 public synchronized String status(){return !enabled.getAsBoolean()?"Выключен в настройках":!installed.getAsBoolean()?"Не установлен":!available()?(failures>=3?"Отключён после ошибок":"Несовместимый API"):!waiting.isEmpty()?"Загружается":error.isEmpty()?"Доступен":"Ошибка API ("+failures+" / 3)";}
 public synchronized JsonObject diagnostics(){var row=new JsonObject();row.addProperty("id",id);row.addProperty("version",installed.getAsBoolean()?version.get():"");row.addProperty("status",status());row.addProperty("available",available());row.addProperty("api",api);row.addProperty("reason",waiting.isEmpty()?error:waiting);row.addProperty("failures",failures);row.addProperty("revision",revision);return row;}
 public synchronized long revision(){return revision;}
 public static long changes(){return CHANGES.get();}
 private void changed(){revision=CHANGES.incrementAndGet();}
 private static String reason(Throwable failure){while(failure.getCause()!=null&&failure.getCause()!=failure)failure=failure.getCause();String message=failure.getClass().getSimpleName()+": "+String.valueOf(failure.getMessage());return message.substring(0,Math.min(240,message.length()));}
 private void waiting(NotReadyException failure){String next=failure.getMessage();if(!waiting.equals(next)){waiting=next;changed();}}
 private void recovered(){if(!error.isEmpty()||!waiting.isEmpty()){error="";waiting="";changed();}}
 private void failed(Throwable failure){waiting="";failures++;error=reason(failure);changed();logger.accept(id,failure);}
 public synchronized <T>T call(Operation<T> action,Supplier<T> fallback){if(!available())return fallback.get();try{T value=action.run();recovered();return value;}catch(NotReadyException pending){waiting(pending);return fallback.get();}catch(IllegalArgumentException expected){throw expected;}catch(Exception|LinkageError failure){failed(failure);return fallback.get();}}
 public synchronized <T>T required(Operation<T> action)throws Exception{if(!available())throw new IllegalArgumentException(id+": "+status());try{T value=action.run();recovered();return value;}catch(NotReadyException pending){waiting(pending);throw new IllegalArgumentException(pending.getMessage(),pending);}catch(IllegalArgumentException expected){throw expected;}catch(Exception|LinkageError failure){failed(failure);throw new IllegalStateException("Ошибка адаптера "+id,failure);}}
 public synchronized void reset(){failures=0;error="";api="";waiting="";supported=null;changed();}
}
