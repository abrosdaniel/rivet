package dev.abros.rivet.client;
import dev.abros.rivet.*;
import dev.abros.rivet.core.*;
import dev.abros.rivet.network.Protocol;
import net.minecraft.client.Minecraft;

import net.minecraft.client.gui.screens.*;
import net.minecraft.client.multiplayer.*;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.fml.ModList;
import java.util.concurrent.*;
public final class Client {
    public static final ExecutorService IO=Executors.newSingleThreadExecutor(r->{Thread t=new Thread(r,"Rivet IO");t.setDaemon(true);return t;});
    public static final ExecutorService NETWORK=new ThreadPoolExecutor(3,3,0,TimeUnit.SECONDS,new ArrayBlockingQueue<>(128),r->{var t=new Thread(r,"Rivet network");t.setDaemon(true);return t;},new ThreadPoolExecutor.AbortPolicy());
    public static final ExecutorService CONNECT=new ThreadPoolExecutor(1,1,0,TimeUnit.SECONDS,new ArrayBlockingQueue<>(8),r->{var t=new Thread(r,"Rivet connection");t.setDaemon(true);return t;},new ThreadPoolExecutor.AbortPolicy());
    public static volatile java.nio.file.Path loadedJar;public static volatile Hub hub;public static volatile String error="";public static volatile String pending="";
    private static boolean initialized;
    static CoreUpdater.Update offeredUpdate;
    static synchronized Hub ensureHub()throws java.io.IOException {if(hub==null)hub=new Hub(Minecraft.getInstance().gameDirectory.toPath(),Rivet.VERSION,ModList.get().getModContainerById("neoforge").orElseThrow().getModInfo().getVersion().toString());return hub;}
    public static Component tr(String key,Object...args){return Component.translatable("rivet."+key,args);}
    public static void install(net.neoforged.bus.api.IEventBus bus){ClientModules.install(bus);loadedJar=System.getProperty("rivet.bundlePath")==null?ModList.get().getModFileById("rivet").getFile().getFilePath():java.nio.file.Path.of(System.getProperty("rivet.bundlePath"));NeoForge.EVENT_BUS.addListener(Client::screen);NeoForge.EVENT_BUS.addListener(UiNavigation::opening);NeoForge.EVENT_BUS.addListener(UiKeyboard::initialized);NeoForge.EVENT_BUS.addListener(UiNavigation::key);Protocol.clientState=()->{
        var j=new com.google.gson.JsonObject();j.addProperty("coreVersion",Rivet.VERSION);j.addProperty("packHash",hub==null?"":hub.activeHash());return j;
    };}
    private static void screen(ScreenEvent.Init.Post e){
        if(!(e.getScreen() instanceof TitleScreen))return;
        if(!initialized){initialized=true;ClientDefaults.initialize();IO.submit(()->{try{ensureHub();NETWORK.submit(()->{try{var update=hub.checkCoreUpdate();update.ifPresent(value->Minecraft.getInstance().execute(()->{offeredUpdate=value;}));}catch(Exception ex){failure(ex);}});}catch(Exception ex){error=Errors.message(ex);}});}
        BundledServers.sync(Minecraft.getInstance());
        var screen=e.getScreen();
        for(var child:screen.children()){
            if(child instanceof net.minecraft.client.gui.components.Button button
                    && button.getMessage().getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents text
                    && text.getKey().equals("menu.multiplayer")){
                e.addListener(new CoreVersionButton(button.getX()+button.getWidth()+4,button.getY(),screen));
                break;
            }
        }

    }
    public static void failure(Exception e){error=Errors.message(e);}
}
