package dev.abros.rivet.compat;

import dev.abros.rivet.core.ChatEffectEnvelope;
import dev.abros.rivet.core.OptionalIntegration;
import net.minecraft.network.chat.Component;
import java.lang.reflect.*;

/** Calls B&C's body transformer without consuming its pending signed-chat state. */
public final class BrewinChannelEffects {
    private static Method modify,getConfig,scramble;
    private static Field level,config;
    private static final OptionalIntegration adapter=new OptionalIntegration("brewinandchewin.chat",
        ()->net.neoforged.fml.ModList.get().isLoaded("brewinandchewin"),()->true,
        ()->net.neoforged.fml.ModList.get().getModContainerById("brewinandchewin").orElseThrow().getModInfo().getVersion().toString(),
        ()->{
            var utility=Class.forName("umpaz.brewinandchewin.client.utility.BnCClientTextUtils");
            modify=utility.getMethod("modifyComponents",Component.class,long.class);
            if(!Modifier.isStatic(modify.getModifiers())||!Component.class.isAssignableFrom(modify.getReturnType()))throw new NoSuchMethodException("Body transformer result");
            level=utility.getField("tipsyMessageLevel");if(!Modifier.isStatic(level.getModifiers())||level.getType()!=int.class)throw new NoSuchFieldException("Effect level");
            config=Class.forName("umpaz.brewinandchewin.common.BnCConfiguration").getField("CLIENT_CONFIG");
            if(!Modifier.isStatic(config.getModifiers()))throw new NoSuchFieldException("Client configuration");
            getConfig=Class.forName("house.greenhouse.greenhouseconfig.api.GreenhouseConfigHolder").getMethod("get");
            scramble=Class.forName("umpaz.brewinandchewin.common.BnCConfiguration$Client").getMethod("scrambleChat");
            return "B&C component transformer";
        },(id,error)->com.mojang.logging.LogUtils.getLogger().warn("Rivet chat adapter unavailable: {}",id,error));
    public static Component apply(Component message) {
        var metadata=ChatEffectEnvelope.read(message.getStyle().getInsertion());
        if(metadata.isPresent()) {
            var body=message.copy().withStyle(style->style.withInsertion(null));
            return adapter.call(()->{
                var player=net.minecraft.client.Minecraft.getInstance().player;
                if(player==null)return body;
                var tipsy=net.minecraft.core.registries.BuiltInRegistries.MOB_EFFECT.getHolder(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("brewinandchewin","tipsy"));
                if(metadata.get().level()==0&&!tipsy.map(player::hasEffect).orElse(false))return body;
                if(!Boolean.TRUE.equals(scramble.invoke(getConfig.invoke(config.get(null)))))return body;
                int previous=level.getInt(null);
                try{level.setInt(null,metadata.get().level());return transform(body,metadata.get().seed());}
                finally{level.setInt(null,previous);}
            },()->body);
        }
        var result=message.plainCopy().withStyle(message.getStyle());
        for(var child:message.getSiblings())result.append(apply(child));
        return result;
    }
    private static Component transform(Component body,long seed)throws ReflectiveOperationException {
        // B&C drops styles on empty container components. Keep inherited actions/colours
        // while using its transformer for each text node exactly once.
        var result=((Component)modify.invoke(null,body.plainCopy().withStyle(body.getStyle()),seed))
            .copy().withStyle(body.getStyle());
        for(var child:body.getSiblings())result.append(transform(child,seed));
        return result;
    }
    private BrewinChannelEffects(){}
}
