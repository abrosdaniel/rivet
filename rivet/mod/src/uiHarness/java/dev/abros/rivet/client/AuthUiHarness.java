package dev.abros.rivet.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;

/** Layout-only fixtures. No connection, credentials or authentication mutations. */
final class AuthUiHarness {
    static void verify(Minecraft mc,int width,int height)throws Exception {
        var stale=dev.abros.rivet.core.Json.parse("{\"entries\":[{\"id\":\"task\",\"section\":\"task\"},{\"id\":\"event\",\"section\":\"events\"},{\"id\":\"board\",\"section\":\"board\"},{\"id\":\"local:moderation-vote\",\"section\":\"notifications\"}]}").getAsJsonArray("entries");
        var current=HomeLayout.arrange(stale);if(current.size()!=1||!dev.abros.rivet.core.Json.str(current.get(0).getAsJsonObject(),"id").equals("local:moderation-vote"))throw new IllegalStateException("Home retained retired rows or lost moderation vote");
        var original=AuthClient.offer;
        try {
            AuthClient.offer=new com.google.gson.JsonObject();
            for(int state=0;state<5;state++) {
                AuthClient.offer.addProperty("factorRequired",state==4);
                var screen=new AuthFactorScreen(null,state==1||state==4);
                if(state==2)set(screen,"secret","GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ");
                if(state==3)set(screen,"codes","fixture-code-1 fixture-code-2");
                screen.init(mc,width,height);
                for(var child:screen.children())if(child instanceof EditBox field&&field.getMessage().getString().equals(Client.text("ui.current_server_password_91ac0570")))field.setValue("fixture-password");
                int rows=state==2?5:state==1?4:state==4?3:2;
                for(int row=0;row<rows;row++){screen.revealRow(row);screen.rebuildWidgets();geometry(screen);}
                screen.revealRow(0);screen.rebuildWidgets();
                if(state!=3&&screen.children().stream().noneMatch(c->c instanceof EditBox field&&field.getValue().equals("fixture-password")))throw new IllegalStateException("Factor form lost password when scrolling");
                screen.resize(mc,width,height);geometry(screen);
                // A late response belonging to a closed/replaced modal must not reveal its secret.
                set(screen,"requestId","current-request");var staleReply=new com.google.gson.JsonObject();staleReply.addProperty("kind","factorSetup");staleReply.addProperty("request","old-request");staleReply.addProperty("secret","STALE_SECRET");screen.receive(staleReply);
                if(get(screen,"secret").equals("STALE_SECRET"))throw new IllegalStateException("Factor form accepted stale response");
            }
        }finally{AuthClient.offer=original;}
    }
    private static void geometry(AuthFactorScreen screen){
        UiGeometryHarness.verify(screen);
        var widgets=screen.children().stream().filter(c->c instanceof AbstractWidget).map(c->(AbstractWidget)c).filter(w->w.visible).toList();
        for(var w:widgets)if(w.getX()<0||w.getY()<0||w.getWidth()<1||w.getY()+w.getHeight()>screen.height||w.getX()+w.getWidth()>screen.width)throw new IllegalStateException("Factor widget outside viewport");
        for(int i=0;i<widgets.size();i++)for(int j=i+1;j<widgets.size();j++){var a=widgets.get(i);var b=widgets.get(j);if(a.getX()<b.getX()+b.getWidth()&&b.getX()<a.getX()+a.getWidth()&&a.getY()<b.getY()+b.getHeight()&&b.getY()<a.getY()+a.getHeight())throw new IllegalStateException("Factor widgets overlap");}
    }
    private static void set(AuthFactorScreen screen,String name,String value)throws Exception{var field=AuthFactorScreen.class.getDeclaredField(name);field.setAccessible(true);field.set(screen,value);}
    private static String get(AuthFactorScreen screen,String name)throws Exception{var field=AuthFactorScreen.class.getDeclaredField(name);field.setAccessible(true);return (String)field.get(screen);}
    private AuthUiHarness(){}
}
