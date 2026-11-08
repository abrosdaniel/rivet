package dev.abros.rivet.client;

import dev.abros.rivet.core.auth.Totp;
import net.minecraft.client.Minecraft;

/** Real Auth TLS requests through the factor modal; fixture secrets never leave memory. */
final class AuthFactorNetworkFlow {
    private static int phase;
    private static long next;
    private static AuthFactorScreen screen;
    private static String consumed="", replacement="";
    static boolean tick() throws Exception {
        if(phase>=6)return true;
        if(next==0){next=System.currentTimeMillis()+1400;return false;}
        if(System.currentTimeMillis()<next)return false;
        if (phase==0) {
            screen=new AuthFactorScreen(null,false);
            Minecraft.getInstance().setScreen(screen);
            send("factorBegin","");phase=1;return false;
        }
        if ((boolean)get("busy")) return false;
        switch(phase) {
            case 1 -> {
                String secret=(String)get("secret");
                require(!secret.isEmpty(),"TOTP setup failed");
                var method=Totp.class.getDeclaredMethod("code",String.class,long.class,int.class);
                method.setAccessible(true);
                send("factorConfirm",(String)method.invoke(null,secret,System.currentTimeMillis()/30000,6));
                phase=2;
            }
            case 2 -> {
                require((boolean)get("enabled"),"TOTP confirmation failed");
                consumed=firstCode();reopen();send("factorCodes",consumed);phase=3;
            }
            case 3 -> {
                replacement=firstCode();require(!replacement.equals(consumed),"Backup codes were not renewed");
                reopen();send("factorRemove",consumed);phase=4;
            }
            case 4 -> {
                require((boolean)get("enabled")&&!((String)get("status")).isBlank(),"Consumed backup code was accepted");
                send("factorRemove",replacement);phase=5;
            }
            case 5 -> {
                require(!(boolean)get("enabled"),"Fresh backup code could not remove optional factor");
                screen.onClose();screen=null;consumed="";replacement="";phase=6;
                System.out.println("RIVET_AUTH_FACTOR_NETWORK_OK setup=true totp=true backupRotation=true replayDenied=true remove=true");
                return true;
            }
            default -> {return true;}
        }
        return false;
    }
    private static String firstCode()throws Exception {
        String codes=(String)get("codes");require(!codes.isBlank(),"Backup codes missing");return codes.split(" ")[0];
    }
    private static void reopen(){screen.onClose();screen=new AuthFactorScreen(null,true);Minecraft.getInstance().setScreen(screen);}
    private static Object get(String name)throws Exception {var f=AuthFactorScreen.class.getDeclaredField(name);f.setAccessible(true);return f.get(screen);}
    private static void send(String action,String code)throws Exception {
        for(var entry:java.util.Map.of("passwordValue","role network test password","codeValue",code).entrySet()) {
            var f=AuthFactorScreen.class.getDeclaredField(entry.getKey());f.setAccessible(true);f.set(screen,entry.getValue());
        }
        var m=AuthFactorScreen.class.getDeclaredMethod("send",String.class);m.setAccessible(true);m.invoke(screen,action);next=System.currentTimeMillis()+1400;
    }
    private static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
    private AuthFactorNetworkFlow(){}
}
