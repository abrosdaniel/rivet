package dev.abros.rivet;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.ModContainer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.api.distmarker.Dist;
import dev.abros.rivet.server.ServerIntegration;
import dev.abros.rivet.network.Protocol;
@Mod("rivet")
public final class Rivet {
    public static String VERSION="unknown";
    public Rivet(IEventBus bus,ModContainer container){
        VERSION=container.getModInfo().getVersion().toString();
        bus.addListener(Protocol::register);
        bus.addListener(dev.abros.rivet.network.SkinWire::register);
        bus.addListener(dev.abros.rivet.server.AuthProtocol::register);
        if(FMLEnvironment.dist==Dist.CLIENT)dev.abros.rivet.client.Client.install(bus);
        else ServerIntegration.install(bus,container);
    }
}
