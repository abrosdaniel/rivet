package dev.abros.rivet.network;
import net.minecraft.network.chat.Component;
/** Standard names and optional mod language entries, with the exact ID as fallback. */
public final class DimensionLabels {
 public static Component name(String id){return switch(id){case "minecraft:overworld"->Component.literal("Мир");case "minecraft:the_nether"->Component.literal("Ад");case "minecraft:the_end"->Component.literal("Энд");default->Component.translatableWithFallback("dimension."+id.replace(':','.').replace('/','.'),id);};}
 private DimensionLabels(){}
}
