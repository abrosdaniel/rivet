package dev.abros.rivet.server;

import io.netty.channel.*;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.timeout.ReadTimeoutException;
import javax.net.ssl.SSLException;
import org.junit.jupiter.api.Test;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class AuthTransportFailureHandlerTest {
 @Test void timeoutClosesBelowBrokenTlsWithoutDisconnectWriteOrRecursiveClose(){
  var tlsCloses=new AtomicInteger();var reported=new AtomicInteger();var minecraft=new AtomicInteger();
  var ch=new EmbeddedChannel();
  ch.pipeline().addLast("rivet_auth_tls",new ChannelOutboundHandlerAdapter(){@Override public void close(ChannelHandlerContext ctx,ChannelPromise promise){tlsCloses.incrementAndGet();ctx.fireExceptionCaught(new SSLException("closed already"));promise.setFailure(new SSLException("closed already"));}});
  ch.pipeline().addLast("guard",new AuthTransportFailureHandler(e->reported.incrementAndGet()));
  ch.pipeline().addLast("packet_handler",new ChannelInboundHandlerAdapter(){@Override public void exceptionCaught(ChannelHandlerContext ctx,Throwable cause){minecraft.incrementAndGet();ctx.close();}});
  ch.pipeline().fireExceptionCaught(ReadTimeoutException.INSTANCE);ch.runPendingTasks();
  assertFalse(ch.isActive());assertEquals(0,tlsCloses.get());assertEquals(0,minecraft.get());assertEquals(1,reported.get());ch.finishAndReleaseAll();
 }
 @Test void packetBugsAreNotSwallowed(){
  var forwarded=new AtomicInteger();var ch=new EmbeddedChannel(new AuthTransportFailureHandler(e->fail("not TLS")),new ChannelInboundHandlerAdapter(){@Override public void exceptionCaught(ChannelHandlerContext ctx,Throwable cause){forwarded.incrementAndGet();}});
  ch.pipeline().fireExceptionCaught(new IllegalArgumentException("bad packet"));assertEquals(1,forwarded.get());assertTrue(ch.isActive());ch.finishAndReleaseAll();
 }
}
