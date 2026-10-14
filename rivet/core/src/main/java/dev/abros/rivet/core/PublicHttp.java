package dev.abros.rivet.core;

import java.io.*;
import java.net.*;
import java.net.http.*;
import java.util.*;
import javax.net.ssl.SSLSession;
import org.apache.hc.client5.http.DnsResolver;
import org.apache.hc.client5.http.config.*;
import org.apache.hc.client5.http.impl.classic.*;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.core5.util.Timeout;

/** The resolver used by the actual socket connection rejects non-public addresses. */
public final class PublicHttp {
 private PublicHttp(){}
 static boolean publicAddress(InetAddress address){
  byte[] b=address.getAddress();if(address.isAnyLocalAddress()||address.isLoopbackAddress()||address.isLinkLocalAddress()||address.isSiteLocalAddress()||address.isMulticastAddress())return false;
  if(b.length==4){int a=b[0]&255,c=b[1]&255;return a!=0&&a<224&&!(a==100&&c>=64&&c<=127)&&!(a==192&&c==0)&&!(a==198&&(c==18||c==19));}
  // Global unicast only: exclude ULA, link local, translation and transition ranges.
  return (b[0]&0xe0)==0x20&&!((b[0]&255)==0x20&&(b[1]&255)==2)&&!((b[0]&255)==0x20&&(b[1]&255)==1&&b[2]==0&&b[3]==0);
 }
 static InetAddress[] checked(InetAddress[] addresses)throws UnknownHostException{if(addresses.length==0)throw new UnknownHostException("Empty DNS response");for(var address:addresses)if(!publicAddress(address))throw new UnknownHostException("Non-public download endpoint rejected");return addresses;}
 @FunctionalInterface interface Lookup {InetAddress[] resolve(String host)throws UnknownHostException;}
 static DnsResolver resolver(Lookup lookup){return new DnsResolver(){
  public InetAddress[] resolve(String host)throws UnknownHostException{return checked(lookup.resolve(host));}
  public String resolveCanonicalHostname(String host)throws UnknownHostException{return host;}
 };}
 private static final DnsResolver DNS=resolver(InetAddress::getAllByName);
 private static final java.util.concurrent.ScheduledThreadPoolExecutor CANCELLATION=new java.util.concurrent.ScheduledThreadPoolExecutor(1,r->{var thread=new Thread(r,"Rivet HTTPS cancellation");thread.setDaemon(true);return thread;});
 static{CANCELLATION.setRemoveOnCancelPolicy(true);}
 private static final CloseableHttpClient CLIENT=HttpClients.custom()
  .setConnectionManager(PoolingHttpClientConnectionManagerBuilder.create().setDnsResolver(DNS).setMaxConnTotal(32).setMaxConnPerRoute(8)
   .setDefaultConnectionConfig(ConnectionConfig.custom().setConnectTimeout(Timeout.ofSeconds(10)).setSocketTimeout(Timeout.ofSeconds(30)).build()).build())
  .setDefaultRequestConfig(RequestConfig.custom().setConnectionRequestTimeout(Timeout.ofSeconds(10)).setResponseTimeout(Timeout.ofSeconds(30)).build())
  .disableRedirectHandling().disableAutomaticRetries().disableCookieManagement().disableContentCompression().build();
 public static HttpResponse<InputStream> send(HttpRequest request)throws IOException{
  URI uri=Remote.https(request.uri().toString());String host=uri.getHost();
  // Numeric addresses can bypass an HTTP client's DNS resolver.
  if(host.indexOf(':')>=0||host.matches("[0-9.]+"))checked(InetAddress.getAllByName(host));
  var get=new HttpGet(uri);request.headers().map().forEach((name,values)->values.forEach(value->get.addHeader(name,value)));
  if(Thread.currentThread().isInterrupted())throw new InterruptedIOException("Download interrupted");
  Thread owner=Thread.currentThread();var watch=CANCELLATION.scheduleAtFixedRate(()->{if(owner.isInterrupted())get.cancel();},100,100,java.util.concurrent.TimeUnit.MILLISECONDS);
  org.apache.hc.client5.http.impl.classic.CloseableHttpResponse response;
  try{response=CLIENT.execute(get);}finally{watch.cancel(false);}boolean owned=false;
  try{
   var headers=new HashMap<String,List<String>>();for(var header:response.getHeaders())headers.computeIfAbsent(header.getName().toLowerCase(Locale.ROOT),k->new ArrayList<>()).add(header.getValue());
   InputStream raw=response.getEntity()==null?InputStream.nullInputStream():response.getEntity().getContent();
   InputStream body=new FilterInputStream(raw){public void close()throws IOException{try{response.close(org.apache.hc.core5.io.CloseMode.IMMEDIATE);}finally{get.cancel();}}};
   var result=new Reply(request,response.getCode(),HttpHeaders.of(headers,(a,b)->true),body);owned=true;return result;
  }finally{if(!owned)response.close(org.apache.hc.core5.io.CloseMode.IMMEDIATE);}
 }
 private record Reply(HttpRequest request,int statusCode,HttpHeaders headers,InputStream body) implements HttpResponse<InputStream>{
  public Optional<HttpResponse<InputStream>> previousResponse(){return Optional.empty();}public Optional<SSLSession> sslSession(){return Optional.empty();}public URI uri(){return request.uri();}public HttpClient.Version version(){return HttpClient.Version.HTTP_1_1;}
 }
}
