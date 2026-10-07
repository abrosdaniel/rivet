package dev.abros.rivet.core.pack;

import com.electronwill.nightconfig.core.UnmodifiableConfig;
import com.google.gson.*;
import dev.abros.rivet.core.*;
import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.security.*;
import java.util.*;

/** Exact provider files, resolved only on explicit preparation. Credentials never enter a publication. */
public final class PackSources {
 interface Transport {
  JsonObject json(String url,String key)throws IOException;
  void download(String url,String key,Path target,long size)throws IOException;
 }
 record Resolved(String path,Path file,String component,String url){}
 private final Transport transport;
 private final java.util.function.Function<String,String> environment;
 public PackSources(){this(new Network(),System::getenv);}
 PackSources(Transport transport,java.util.function.Function<String,String> environment){this.transport=transport;this.environment=environment;}
 List<Resolved> resolve(List<UnmodifiableConfig> rows,String keyEnv,Path cache,String minecraft)throws IOException{
  if(rows.size()>512)throw new IOException("В сборке больше 512 ссылок на моды");
  PackPublisher.safe(cache);Files.createDirectories(cache);
  var out=new ArrayList<Resolved>();var seen=new HashSet<String>();
  for(var row:rows){
   if(Thread.currentThread().isInterrupted())throw new InterruptedIOException("Публикация отменена");
   for(var e:row.entrySet())if(!Set.of("url","component").contains(e.getKey()))throw new IOException("Неизвестный параметр мода: "+e.getKey());
   String url=string(row,"url",""),component=string(row,"component","");URI uri;
   try{uri=Remote.https(url);}catch(IllegalArgumentException e){throw new IOException("Укажите HTTPS-ссылку на конкретную версию Modrinth или файл CurseForge");}
   if(uri.getQuery()!=null||uri.getPort()!=-1)throw new IOException("Ссылка на мод не должна содержать порт или параметры");
   var parts=uri.getPath().split("/");Source source;
   if(uri.getHost().equals("modrinth.com")&&parts.length==5&&parts[1].equals("mod")&&parts[3].equals("version")){
    token(parts[2]);token(parts[4]);
    var version=transport.json("https://api.modrinth.com/v2/project/"+parts[2]+"/version/"+parts[4],"");
    if(!contains(version,"game_versions",minecraft)||!contains(version,"loaders","neoforge"))throw new IOException("Версия Modrinth не поддерживает Minecraft "+minecraft+" / NeoForge");
    var files=version.getAsJsonArray("files");var primary=new ArrayList<JsonObject>();for(var f:files)if(f.getAsJsonObject().get("primary").getAsBoolean())primary.add(f.getAsJsonObject());
    if(primary.size()!=1)throw new IOException("У версии Modrinth должен быть один основной файл");
    var file=primary.getFirst();source=new Source(Json.str(file,"filename"),Json.str(file,"url"),file.get("size").getAsLong(),"SHA-512",Json.str(file.getAsJsonObject("hashes"),"sha512"),"");
   }else if(Set.of("www.curseforge.com","curseforge.com").contains(uri.getHost())&&parts.length==6&&parts[1].equals("minecraft")&&parts[2].equals("mc-mods")&&parts[4].equals("files")){
    token(parts[3]);if(!parts[5].matches("[1-9][0-9]{0,9}"))throw new IOException("Укажите конкретный файл CurseForge");
    if(!keyEnv.matches("[A-Za-z_][A-Za-z0-9_]{0,127}"))throw new IOException("Укажите curseforgeKeyEnv в pack.toml");
    String key=environment.apply(keyEnv);if(key==null||key.isBlank())throw new IOException("Переменная "+keyEnv+" не содержит ключ API CurseForge");
    if(key.length()>4096||!key.chars().allMatch(ch->ch>=33&&ch<=126))throw new IOException("Некорректное значение ключа в переменной "+keyEnv);
    var search=transport.json("https://api.curseforge.com/v1/mods/search?gameId=432&classId=6&slug="+parts[3],key).getAsJsonArray("data");
    var matches=new ArrayList<JsonObject>();for(var item:search)if(Json.str(item.getAsJsonObject(),"slug").equals(parts[3]))matches.add(item.getAsJsonObject());
    if(matches.size()!=1)throw new IOException("Не удалось однозначно найти проект CurseForge");
    var mod=matches.getFirst();if(mod.has("allowModDistribution")&&!mod.get("allowModDistribution").isJsonNull()&&!mod.get("allowModDistribution").getAsBoolean())throw new IOException("Автор запретил распространение этого мода через сторонние сборки");
    long project=mod.get("id").getAsLong();var file=transport.json("https://api.curseforge.com/v1/mods/"+project+"/files/"+parts[5],key).getAsJsonObject("data");
    if(file.get("id").getAsLong()!=Long.parseLong(parts[5])||file.get("modId").getAsLong()!=project||!file.get("isAvailable").getAsBoolean()||!contains(file,"gameVersions",minecraft)||!contains(file,"gameVersions","NeoForge"))throw new IOException("Файл CurseForge недоступен или не поддерживает Minecraft "+minecraft+" / NeoForge");
    String hash="";for(var h:file.getAsJsonArray("hashes"))if(h.getAsJsonObject().get("algo").getAsInt()==1)hash=Json.str(h.getAsJsonObject(),"value");
    String download=file.has("downloadUrl")&&!file.get("downloadUrl").isJsonNull()?Json.str(file,"downloadUrl"):"";
    if(download.isEmpty())throw new IOException("CurseForge не разрешает загрузку этого файла через API");
    source=new Source(Json.str(file,"fileName"),download,file.get("fileLength").getAsLong(),"SHA-1",hash,key);
   }else throw new IOException("Поддерживаются ссылки modrinth.com/mod/…/version/… и curseforge.com/minecraft/mc-mods/…/files/…");
   String path="mods/"+source.name();SafePaths.validate(path);if(source.name().contains("/")||source.name().contains("\\")||!source.name().endsWith(".jar"))throw new IOException("Источник должен содержать JAR мода");
   if(!seen.add(SafePaths.key(path)))throw new IOException("Повторяющийся путь мода: "+path);
   if(source.size()<1||source.size()>512L*1024*1024)throw new IOException("Размер мода должен быть от 1 байта до 512 МиБ");
   int digits=source.algorithm().equals("SHA-512")?128:40;if(!source.hash().matches("[0-9a-fA-F]{"+digits+"}"))throw new IOException("Источник не предоставил контрольную сумму файла");
   Network.endpoint(source.url(),!source.key().isEmpty(),false);
   Path target=cache.resolve(source.hash().toLowerCase(Locale.ROOT));PackPublisher.safe(target);
   if(!Files.isRegularFile(target)||Files.size(target)!=source.size()||!digest(target,source.algorithm()).equalsIgnoreCase(source.hash())){
    Path tmp=Files.createTempFile(cache,"download-",".tmp");try{
     transport.download(source.url(),source.key(),tmp,source.size());
     if(Files.size(tmp)!=source.size()||!digest(tmp,source.algorithm()).equalsIgnoreCase(source.hash()))throw new IOException("Контрольная сумма загруженного мода не совпадает: "+source.name());
     Json.move(tmp,target);
    }finally{Files.deleteIfExists(tmp);}
   }
   out.add(new Resolved(path,target,component,source.url()));
  }
  return List.copyOf(out);
 }
 static String publicDownload(String url)throws IOException{
  URI uri;try{uri=Remote.https(url);}catch(IllegalArgumentException e){throw new IOException("Некорректный адрес источника",e);}
  if(url.length()>2048||uri.getQuery()!=null)throw new IOException("Некорректный адрес источника");
  Network.endpoint(url,!uri.getHost().equals("cdn.modrinth.com"),false);return url;
 }
 private record Source(String name,String url,long size,String algorithm,String hash,String key){}
 private static String string(UnmodifiableConfig row,String key,String fallback)throws IOException{Object v=row.get(key);if(v==null)return fallback;if(v instanceof String s)return s;throw new IOException("Ожидается строка: "+key);}
 private static void token(String s)throws IOException{if(!s.matches("[A-Za-z0-9._+\\-]{1,128}"))throw new IOException("Некорректный идентификатор проекта или версии");}
 private static boolean contains(JsonObject o,String key,String value){return o.getAsJsonArray(key).asList().stream().anyMatch(e->e.isJsonPrimitive()&&e.getAsString().equals(value));}
 static String digest(Path file,String algorithm)throws IOException{try{var d=MessageDigest.getInstance(algorithm);try(var in=Files.newInputStream(file)){byte[] b=new byte[65536];for(int n;(n=in.read(b))!=-1;)d.update(b,0,n);}return HexFormat.of().formatHex(d.digest());}catch(NoSuchAlgorithmException e){throw new AssertionError(e);}}
 private static final class Network implements Transport {
  static URI endpoint(String url,boolean curseforge,boolean metadata)throws IOException{
   URI u;try{u=Remote.https(url);}catch(IllegalArgumentException e){throw new IOException("Некорректный адрес источника");}
   Set<String> hosts=metadata?Set.of(curseforge?"api.curseforge.com":"api.modrinth.com"):curseforge?Set.of("edge.forgecdn.net","mediafilez.forgecdn.net"):Set.of("cdn.modrinth.com");
   if(u.getPort()!=-1||!hosts.contains(u.getHost())||(!metadata&&u.getQuery()!=null))throw new IOException("Источник вернул неподдерживаемый адрес загрузки");
   return u;
  }
  private static URLConnection open(String url,String key,boolean metadata)throws IOException{
   URI u=endpoint(url,!key.isEmpty(),metadata);for(var a:InetAddress.getAllByName(u.getHost()))if(a.isAnyLocalAddress()||a.isLoopbackAddress()||a.isSiteLocalAddress()||a.isLinkLocalAddress()||a.isMulticastAddress()||(a.getAddress().length==16&&(a.getAddress()[0]&0xfe)==0xfc))throw new IOException("Непубличный адрес источника");
   var c=(HttpURLConnection)u.toURL().openConnection();c.setInstanceFollowRedirects(false);c.setConnectTimeout(10000);c.setReadTimeout(30000);c.setRequestProperty("User-Agent","Rivet server pack (https://github.com/abrosdaniel/rivet)");if(!key.isEmpty())c.setRequestProperty("x-api-key",key);
   int status=c.getResponseCode();if(status!=200){c.disconnect();throw new IOException("Источник "+u.getHost()+" вернул HTTP "+status);}
   return c;
  }
  public JsonObject json(String url,String key)throws IOException{var c=(HttpURLConnection)open(url,key,true);try(var in=c.getInputStream()){byte[] b=in.readNBytes(2*1024*1024+1);if(b.length>2*1024*1024)throw new IOException("Ответ источника превышает 2 МиБ");return Json.parse(new String(b,java.nio.charset.StandardCharsets.UTF_8));}finally{c.disconnect();}}
  public void download(String url,String key,Path target,long size)throws IOException{var c=(HttpURLConnection)open(url,key,false);try(var in=c.getInputStream();var out=Files.newOutputStream(target)){byte[] b=new byte[65536];long total=0,deadline=System.nanoTime()+java.util.concurrent.TimeUnit.MINUTES.toNanos(5);for(int n;(n=in.read(b))!=-1;){if(Thread.currentThread().isInterrupted()||System.nanoTime()>deadline)throw new java.io.InterruptedIOException("Публикация отменена");total+=n;if(total>size)throw new IOException("Источник превысил размер файла");out.write(b,0,n);}if(total!=size)throw new IOException("Источник передал неполный файл");}finally{c.disconnect();}}
 }
}
