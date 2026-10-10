package dev.abros.rivet.client;
import com.google.gson.JsonObject;
import dev.abros.rivet.core.*;
import net.minecraft.network.chat.*;
/** Display-only metadata: never used as command arguments or identity. */
final class PlayerText {
 private PlayerText(){}
 static MutableComponent text(String text){var result=Component.empty();for(var span:LegacyText.parse(text)){var style=Style.EMPTY.withBold(span.bold()).withItalic(span.italic()).withUnderlined(span.underlined()).withStrikethrough(span.strike()).withObfuscated(span.obfuscated());if(span.color()!=null)style=style.withColor(span.color());result.append(Component.literal(span.text()).withStyle(style));}return result;}
 static MutableComponent name(JsonObject player){
  String prefix=Json.opt(player,"prefix","").strip(),suffix=Json.opt(player,"suffix","").strip();
  var result=Component.empty();
  if(!prefix.isEmpty())result.append(text(prefix)).append(" ");
  result.append(Component.literal(Json.opt(player,"name",Client.text("map.tool.player"))));
  if(!suffix.isEmpty())result.append(" ").append(text(suffix));
  return result;
 }
}
