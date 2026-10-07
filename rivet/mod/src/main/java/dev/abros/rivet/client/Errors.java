package dev.abros.rivet.client;
import dev.abros.rivet.core.Remote;
final class Errors {
 static String message(Exception error){
  if(error.getCause() instanceof Exception cause && (error instanceof java.util.concurrent.ExecutionException || error instanceof java.util.concurrent.CompletionException))return message(cause);
  if(error.getMessage()!=null && error.getMessage().startsWith("All download sources failed: "))return Client.tr("error.sources",error.getMessage().substring("All download sources failed: ".length())).getString();
  if(error instanceof Remote.HttpFailure http){
   if(http.status==404)return Client.tr("error.notfound").getString();
   if(http.status==403||http.status==429)return Client.tr("error.ratelimit").getString();
  }
  if(error instanceof Remote.Unavailable||error instanceof java.net.UnknownHostException)return Client.tr("error.network").getString();
  if(error instanceof java.nio.file.NoSuchFileException)return Client.tr("error.cachemissing").getString();
  return error.getMessage()==null?error.getClass().getSimpleName():error.getMessage();
 }
}
