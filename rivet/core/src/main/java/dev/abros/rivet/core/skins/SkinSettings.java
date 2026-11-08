package dev.abros.rivet.core.skins;
public record SkinSettings(boolean enabled,int maxFileSizeMiB,int maxSkinsPerPlayer,String mojangFallback,boolean capes,int maxCapesPerPlayer,int maxCapeSizeKiB,int maxOutfitsPerPlayer){
 public SkinSettings(boolean enabled,int maxFileSizeMiB,int maxSkinsPerPlayer,String mojangFallback){this(enabled,maxFileSizeMiB,maxSkinsPerPlayer,mojangFallback,true,8,256,8);}
 public SkinSettings(boolean enabled,int size,int skins,String fallback,boolean capes,int count,int capeSize){this(enabled,size,skins,fallback,capes,count,capeSize,8);}
 public int maxCapeBytes(){return maxCapeSizeKiB*1024;}
 public SkinSettings{if(maxOutfitsPerPlayer<1||maxOutfitsPerPlayer>8||maxCapesPerPlayer<1||maxCapesPerPlayer>8||maxCapeSizeKiB<16||maxCapeSizeKiB>1024||maxFileSizeMiB<1||maxFileSizeMiB>16||maxSkinsPerPlayer<1||maxSkinsPerPlayer>8||!java.util.Set.of("false","UUID","nickname").contains(mojangFallback))throw new IllegalArgumentException("Invalid skins settings");}
 public int maxBytes(){return maxFileSizeMiB*1024*1024;}
}
