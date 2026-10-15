package dev.abros.rivet.core;

/** Immutable worker-to-UI snapshot. Unknown totals never become invented percentages. */
public record InstallProgress(String phase,String file,long bytes,long total,int files,int count) {
 public InstallProgress { bytes=Math.max(0,bytes);if(total>=0)bytes=Math.min(bytes,total);files=Math.max(0,files);count=Math.max(files,count); }
 public static InstallProgress phase(String phase){return new InstallProgress(phase,"",0,-1,0,0);}
 public double fraction(){return total>0?(double)bytes/total:total==0?1:-1;}
}
