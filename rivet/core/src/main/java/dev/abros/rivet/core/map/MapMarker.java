package dev.abros.rivet.core.map;

import java.util.UUID;

/** Personal marker; not an authorization to reveal terrain or teleport. */
public record MapMarker(UUID id,String dimension,String name,int x,int y,int z,int color,String icon,long deathAt,String category,boolean mapVisible,boolean minimapVisible) {
    public MapMarker(UUID id,String dimension,String name,int x,int y,int z,int color,String icon,long deathAt){this(id,dimension,name,x,y,z,color,icon,deathAt,"",true,true);}
    public MapMarker(UUID id,String dimension,String name,int x,int y,int z,int color,String icon){this(id,dimension,name,x,y,z,color,icon,0);}
    public static String initial(String name){
        String text=name.strip();if(text.isEmpty())return "";
        var characters=java.text.BreakIterator.getCharacterInstance(java.util.Locale.ROOT);characters.setText(text);
        return text.substring(0,characters.next()).toUpperCase(java.util.Locale.ROOT);
    }
    public boolean death(){return deathAt>0;}
    public MapMarker {
        if(id==null||dimension==null||!dimension.matches("[a-z0-9_.-]+:[a-z0-9_./-]+"))throw new IllegalArgumentException("Invalid marker dimension");
        if(name==null||name.isBlank()||name.codePointCount(0,name.length())>80||name.codePoints().anyMatch(Character::isISOControl))throw new IllegalArgumentException("Invalid marker name");
        if(Math.abs((long)x)>30000000||Math.abs((long)z)>30000000||y < -2048||y>2048)throw new IllegalArgumentException("Invalid marker coordinates");
        if(icon==null||!icon.matches("[a-z0-9_]{1,40}"))throw new IllegalArgumentException("Invalid marker icon");
        if(category==null)throw new IllegalArgumentException("Invalid category");
        if(!category.isEmpty())UUID.fromString(category);
        if(deathAt>0){category="";mapVisible=true;minimapVisible=true;}
        if(deathAt<0)throw new IllegalArgumentException("Invalid death timestamp");
        if(deathAt>0)icon="skull";else if(icon.equals("skull"))icon="pin";
        color=deathAt>0?0xff606876:0xff000000|(color&0xffffff);
    }
}
