package dev.abros.rivet.core.api;

import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RestHttpServerTest {
    static int port()throws Exception{try(var socket=new java.net.ServerSocket(0,1,InetAddress.getLoopbackAddress())){return socket.getLocalPort();}}
    static RestSettings settings(int port,int timeout,int rpm){return new RestSettings(true,"127.0.0.1",port,8,8,2,4,timeout,rpm,rpm,Set.of(),Set.of("server.status"));}
    static String raw(int port,String request)throws Exception{
        try(var socket=new Socket("127.0.0.1",port)){socket.setSoTimeout(5000);socket.getOutputStream().write(request.getBytes(StandardCharsets.US_ASCII));return new String(socket.getInputStream().readAllBytes(),StandardCharsets.UTF_8);}
    }
    @Test void realHttpConditionalGetAndCloseReleasePort()throws Exception{
        int port=port();var cfg=settings(port,3,100);
        try(var feed=RestApiTest.feed();var server=new RestHttpServer(cfg,new RestApi(feed,new RestApiTest.Auth(),cfg));var client=HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).connectTimeout(Duration.ofSeconds(2)).build()){
            server.start();var uri=URI.create("http://127.0.0.1:"+port+"/api/v1/server/status");
            var response=client.send(HttpRequest.newBuilder(uri).GET().build(),HttpResponse.BodyHandlers.ofString());assertEquals(200,response.statusCode());assertTrue(response.body().contains("onlinePlayers"));assertEquals("close",response.headers().firstValue("connection").orElseThrow());
            var cached=client.send(HttpRequest.newBuilder(uri).header("If-None-Match",response.headers().firstValue("etag").orElseThrow()).GET().build(),HttpResponse.BodyHandlers.ofString());assertEquals(304,cached.statusCode());assertEquals("",cached.body());
        }
        try(var rebound=new java.net.ServerSocket()){rebound.setReuseAddress(true);rebound.bind(new InetSocketAddress("127.0.0.1",port));}
    }
    @Test void maliciousFramingAndBodiesNeverReachAuthorization()throws Exception{
        int port=port();var cfg=settings(port,2,100);var auth=new RestApiTest.Auth();
        try(var feed=RestApiTest.feed();var server=new RestHttpServer(cfg,new RestApi(feed,auth,cfg))){server.start();
            assertTrue(raw(port,"GET /api/v1/server/status HTTP/1.1\r\nHost: localhost\r\nAuthorization: Bearer secret\r\nAuthorization: Bearer secret\r\n\r\n").contains("400"));
            assertTrue(raw(port,"GET /api/v1/server/status HTTP/1.1\r\nHost: localhost\r\nContent-Length: 999999999\r\n\r\n").contains("413"));
            assertTrue(raw(port,"GET /api/v1/server/status HTTP/1.1\r\nHost: localhost\r\nTransfer-Encoding: chunked\r\n\r\n").contains("413"));
            assertTrue(raw(port,"GET /api/v1/server/status HTTP/1.1\r\nHost: localhost\r\nX-Large: "+"x".repeat(9000)+"\r\n\r\n").contains("400"));
            assertEquals(0,auth.calls.get());
        }
    }
    @Test void incompleteHeadersHaveAbsoluteDeadlineAndDoNotHoldPortAfterStop()throws Exception{
        int port=port();var cfg=settings(port,1,100);
        try(var feed=RestApiTest.feed();var server=new RestHttpServer(cfg,new RestApi(feed,new RestApiTest.Auth(),cfg))){server.start();
            try(var socket=new Socket("127.0.0.1",port)){socket.setSoTimeout(3500);socket.getOutputStream().write("GET /api/v1/server/status HTTP/1.1\r\nHost:".getBytes(StandardCharsets.US_ASCII));assertEquals(-1,socket.getInputStream().read());}
            assertTrue(raw(port,"GET /api/v1/server/status HTTP/1.1\r\nHost: localhost\r\n\r\n").contains("200"));
        }
    }
    @Test void socketRateBudgetIgnoresForwardedAddresses()throws Exception{
        int port=port();var cfg=settings(port,2,1);
        try(var feed=RestApiTest.feed();var server=new RestHttpServer(cfg,new RestApi(feed,new RestApiTest.Auth(),cfg))){server.start();
            assertTrue(raw(port,"GET /api/v1/server/status HTTP/1.1\r\nHost: localhost\r\nX-Forwarded-For: 192.0.2.1\r\n\r\n").contains("200"));
            assertTrue(raw(port,"GET /api/v1/server/status HTTP/1.1\r\nHost: localhost\r\nX-Forwarded-For: 192.0.2.2\r\n\r\n").contains("429"));
        }
    }
}
