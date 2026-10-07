package dev.abros.rivet.core.pack;

import dev.abros.rivet.core.*;
import java.io.*;
import java.nio.file.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/** Public provider download, isolated from resumable server transfers. */
final class PackDownloads {
 @FunctionalInterface interface Source {void download(String url,Path target,long size,AtomicBoolean cancel)throws IOException,InterruptedException;}
 static boolean source(PackManifest.Entry file,Path object,AtomicBoolean cancel,Consumer<String> progress,Source source)throws IOException{
  cancelled(cancel);if(file.url().isEmpty())return false;
  Path part=object.resolveSibling(object.getFileName()+".source.part");PackPublisher.safe(part);
  Path etag=part.resolveSibling(part.getFileName()+".etag");PackPublisher.safe(etag);
  try{
   progress.accept("Загрузка из источника: "+file.path());
   source.download(PackSources.publicDownload(file.url()),part,file.size(),cancel);cancelled(cancel);
   if(Files.size(part)!=file.size()||!Hashes.sha256(part).equals(file.hash()))throw new IOException("Источник передал другой файл");
   Json.move(part,object);return true;
  }catch(InterruptedException e){Thread.currentThread().interrupt();throw new InterruptedIOException("Загрузка отменена");}
  catch(IOException e){cancelled(cancel);progress.accept("Источник недоступен. Загрузка с сервера: "+file.path());return false;}
  finally{Files.deleteIfExists(part);Files.deleteIfExists(etag);}
 }
 private static void cancelled(AtomicBoolean cancel)throws InterruptedIOException{if(cancel.get()||Thread.currentThread().isInterrupted())throw new InterruptedIOException("Загрузка отменена");}
 private PackDownloads(){}
}
