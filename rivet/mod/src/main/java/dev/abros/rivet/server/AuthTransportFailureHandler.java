package dev.abros.rivet.server;

import io.netty.channel.*;
import io.netty.handler.timeout.ReadTimeoutException;
import javax.net.ssl.SSLException;
import java.util.function.Consumer;

/** A broken TLS stream cannot carry Minecraft's disconnect packet. Close below TLS once. */
final class AuthTransportFailureHandler extends ChannelInboundHandlerAdapter {
 private boolean closing;
 private final Consumer<Throwable> report;
 AuthTransportFailureHandler(Consumer<Throwable> report){this.report=report;}
 @Override public void exceptionCaught(ChannelHandlerContext ctx,Throwable cause){
  if(closing)return;
  boolean transport=false;for(Throwable t=cause;t!=null;t=t.getCause())if(t instanceof SSLException||t instanceof ReadTimeoutException){transport=true;break;}
  if(!transport){ctx.fireExceptionCaught(cause);return;}
  closing=true;
  try{report.accept(cause);}finally{
   // Outbound traversal starts BEFORE this context; bypass SslHandler's failing close_notify.
   var tls=ctx.pipeline().context("rivet_auth_tls");if(tls!=null)tls.close();else ctx.close();
  }
 }
}
