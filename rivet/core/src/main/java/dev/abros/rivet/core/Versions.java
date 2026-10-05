package dev.abros.rivet.core;
public final class Versions {
    private Versions(){}
    private static final String NUMBER="(?:0|[1-9][0-9]*)";
    private static final String RELEASE=NUMBER+"\\."+NUMBER+"\\."+NUMBER;
    private static final java.util.regex.Pattern RANGE=java.util.regex.Pattern.compile(">=("+RELEASE+") <("+RELEASE+")");
    public static boolean isRelease(String version){return version!=null&&version.matches(RELEASE);}
    /** Release families are distinct; wire protocols and negotiated features decide connection support. */
    public static boolean sameMajor(String installed,String required){
        return isRelease(installed)&&isRelease(required)&&installed.split("\\.")[0].equals(required.split("\\.")[0]);
    }
    public static boolean validRequirement(String requirement){
        if(requirement==null)return false;
        if(requirement.matches("="+RELEASE)||requirement.matches(NUMBER+"\\.x(\\.x)?")||requirement.matches(NUMBER+"\\."+NUMBER+"\\.(x|"+NUMBER+")"))return true;
        var range=RANGE.matcher(requirement);
        return range.matches()&&compare(range.group(1),range.group(2))<0;
    }
    /** Explicit bounds: lower inclusive, upper exclusive. Bare releases retain their legacy minimum meaning. */
    public static boolean supportsRequirement(String installed,String requirement){
        if(!isRelease(installed)||!validRequirement(requirement))return false;
        if(requirement.startsWith("="))return installed.equals(requirement.substring(1));
        var range=RANGE.matcher(requirement);
        if(range.matches())return compare(installed,range.group(1))>=0&&compare(installed,range.group(2))<0;
        if(requirement.matches(NUMBER+"\\.x(\\.x)?"))return sameMajor(installed,requirement.split("\\.")[0]+".0.0");
        String minimum=requirement.endsWith(".x")?requirement.substring(0,requirement.length()-1)+"0":requirement;
        return sameMajor(installed,minimum)&&compare(installed,minimum)>=0;
    }
    /** Retained for callers compiled against the earlier API. */
    public static boolean supportsBranch(String installed,String requirement){return supportsRequirement(installed,requirement);}
    public static int compare(String a,String b){
        String[] aa=a.split("\\+",2)[0].split("-",2),bb=b.split("\\+",2)[0].split("-",2);
        String[] av=aa[0].split("\\."),bv=bb[0].split("\\.");if(av.length!=3||bv.length!=3)throw new IllegalArgumentException("SemVer required");
        for(int i=0;i<3;i++){int c=new java.math.BigInteger(av[i]).compareTo(new java.math.BigInteger(bv[i]));if(c!=0)return c;}
        if(aa.length!=bb.length)return aa.length==1?1:-1;if(aa.length==1)return 0;
        String[] ap=aa[1].split("\\."),bp=bb[1].split("\\.");for(int i=0;i<Math.min(ap.length,bp.length);i++){boolean an=ap[i].matches("[0-9]+"),bn=bp[i].matches("[0-9]+");int c=an&&bn?new java.math.BigInteger(ap[i]).compareTo(new java.math.BigInteger(bp[i])):an!=bn?(an?-1:1):ap[i].compareTo(bp[i]);if(c!=0)return c;}return Integer.compare(ap.length,bp.length);
    }
}
