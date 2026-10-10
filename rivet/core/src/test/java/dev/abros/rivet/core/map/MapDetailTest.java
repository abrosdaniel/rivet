package dev.abros.rivet.core.map;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MapDetailTest {
    @Test void selectsDetailWithoutLosingFullResolution(){
        assertEquals(0,MapDetail.level(3));assertEquals(0,MapDetail.level(.75));
        assertEquals(1,MapDetail.level(.5));assertEquals(2,MapDetail.level(.25));assertEquals(4,MapDetail.level(.01));
        assertThrows(IllegalArgumentException.class,()->MapDetail.level(Double.NaN));
    }
    @Test void retainsBlocksThatStillCoverPhysicalPixels(){
        assertEquals(0,MapDetail.level(.5,2));assertEquals(0,MapDetail.level(.25,4));
        assertEquals(0,MapDetail.level(.25,3));assertEquals(1,MapDetail.level(.25,2));
        assertEquals(2,MapDetail.level(.25,1));assertEquals(0,MapDetail.level(1.87,3));
        assertThrows(IllegalArgumentException.class,()->MapDetail.level(1,0));
    }
    @Test void averagesColoursInsteadOfSkippingBlocks(){
        int[] pixels=new int[256];pixels[0]=0xffff0000;pixels[1]=0xff00ff00;pixels[16]=0xff0000ff;pixels[17]=0xffffffff;
        var levels=MapDetail.pyramid(pixels);assertEquals(0xff7f7f7f,levels[1][0]);
        assertEquals(0xff7f7f7f,levels[4][0]);assertEquals(0,levels[1][1]);
        pixels[0]=0;assertEquals(0xffff0000,levels[0][0]);
    }
    @Test void unexploredAreasStayUnknownAtEveryLevel(){
        for(var level:MapDetail.pyramid(new int[256]))for(int color:level)assertEquals(0,color);
        assertThrows(IllegalArgumentException.class,()->MapDetail.pyramid(new int[16]));
    }
}
