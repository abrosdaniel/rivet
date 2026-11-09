package dev.abros.rivet.core.skins;
public record SkinSettings(boolean enabled,int maxFileSizeMiB,int maxSkinsPerPlayer,String mojangFallback,boolean capes,int maxCapesPerPlayer,int maxCapeSizeKiB,int maxOutfitsPerPlayer,int maxResolution,int maxCapeResolution){
 public SkinSettings(boolean enabled,int maxFileSizeMiB,int maxSkinsPerPlayer,String mojangFallback){this(enabled,maxFileSizeMiB,maxSkinsPerPlayer,mojangFallback,true,8,256,8);}
 public SkinSettings(boolean enabled,int size,int skins,String fallback,boolean capes,int count,int capeSize){this(enabled,size,skins,fallback,capes,count,capeSize,8);}
 public SkinSettings(boolean enabled,int size,int skins,String fallback,boolean capes,int count,int capeSize,int outfits){this(enabled,size,skins,fallback,capes,count,capeSize,outfits,256,256);}
 public static boolean resolution(int value){return value==64||value==128||value==256||value==512||value==1024;}
 public int maxCapeBytes(){return maxCapeSizeKiB*1024;}
 public SkinSettings{if(!resolution(maxResolution)||!resolution(maxCapeResolution)||maxOutfitsPerPlayer<1||maxOutfitsPerPlayer>8||maxCapesPerPlayer<1||maxCapesPerPlayer>8||maxCapeSizeKiB<16||maxCapeSizeKiB>1024||maxFileSizeMiB<1||maxFileSizeMiB>16||maxSkinsPerPlayer<1||maxSkinsPerPlayer>8||!java.util.Set.of("false","UUID","nickname").contains(mojangFallback))throw new IllegalArgumentException("Invalid skins settings");}
 public int maxBytes(){return maxFileSizeMiB*1024*1024;}
}
