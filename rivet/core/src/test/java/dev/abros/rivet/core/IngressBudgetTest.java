package dev.abros.rivet.core;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class IngressBudgetTest {
 @Test void queuedPacketsAreBoundedAndReleased(){var b=new IngressBudget();for(int i=0;i<64;i++)assertTrue(b.acquire(8192));assertFalse(b.acquire(1));b.release();assertTrue(b.acquire(1));}
 @Test void rapidMalformedPacketsStillConsumeBudget(){var b=new IngressBudget();long now=System.nanoTime();for(int i=0;i<256;i++){assertTrue(b.acquire(1,now));b.release();}assertFalse(b.acquire(1,now));assertTrue(b.acquire(1,now+1_000_000_000L));}
 @Test void normalSkinUploadAndMenuTrafficFit(){var b=new IngressBudget();long now=System.nanoTime();for(int tick=0;tick<200;tick++)for(int n=0;n<5;n++){assertTrue(b.acquire(6000,now+tick*50_000_000L));b.release();}}
}
