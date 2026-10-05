package dev.abros.rivet.client;

import com.google.gson.*;
import dev.abros.rivet.core.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.util.*;

/** Compound diagnostic dialog: shared tabs, cards, scroll viewport and action footer. */
final class SparkDiagnosticsScreen extends ScrollScreen {
    private final Screen parent;
    private JsonObject data=new JsonObject();
    private UiDialog dialog;
    private boolean waiting;
    private long nextRefresh;
    private int tab;
    private String error="";
    SparkDiagnosticsScreen(Screen parent){super(Component.literal("Производительность · spark"));this.parent=parent;}
    private JsonObject object(String key){return data.has(key)&&data.get(key).isJsonObject()?data.getAsJsonObject(key):new JsonObject();}
    private JsonArray array(String key){return data.has(key)&&data.get(key).isJsonArray()?data.getAsJsonArray(key):new JsonArray();}
    private boolean flag(String key){return data.has(key)&&data.get(key).getAsBoolean();}
    private void request(String operation,int seconds,int threshold){
        if(waiting)return;waiting=true;nextRefresh=System.currentTimeMillis()+2000;
        var q=new JsonObject();q.addProperty("op",operation);q.addProperty("seconds",seconds);q.addProperty("threshold",threshold);if(operation.equals("alerts"))q.addProperty("enabled",!flag("alerts"));
        CompatibilityClient.request("spark",q).whenComplete((reply,failure)->{waiting=false;if(minecraft.screen!=this)return;error=failure==null?"":failure.getMessage();if(failure==null)data=reply;rebuildWidgets();});
    }
    @Override protected void init(){
        dialog=UiDialog.fit(width,height,400,280);var head=dialog.header();var body=dialog.body();
        UiTabs.build(this,font,new NativeLayout.Box(head.x(),head.bottom()+2,head.width(),20),List.of("Состояние","Профилирование","Отчёты"),tab,this::addRenderableWidget,n->{tab=n;resetScroll();rebuildWidgets();},true);
        int top=body.y()+38, bottom=dialog.footer().y()-20;
        var view=new NativeLayout.Box(body.x(),top,body.width(),Math.max(0,bottom-top));
        int count=tab==0?8:tab==1?5:Math.max(1,array("reports").size());int rowHeight=tab==0?44:tab==1?26:36;
        var layout=scrollArea(count,view,rowHeight);int x=layout.content().x(),w=layout.content().width();
        if(tab==0){String[][] cards={{"TPS","tps","MSPT","mspt"},{"CPU Minecraft","cpuProcess","CPU системы","cpuSystem"},{"Память","heap","MSPT · 95% тиков","mspt95"}};
            for(int row=firstRow;row<Math.min(count,firstRow+visibleRows);row++){int y=top+(row-firstRow)*rowHeight;
                if(row<3){var pair=cards[row];int cell=(w-6)/2;for(int n=0;n<2;n++){String key=pair[n*2+1];addRenderableWidget(UiMetricCard.compact(x+n*(cell+6),y,cell,pair[n*2],value(key)));}}
            }
        }else if(tab==1){
            String[] names={"Проверить состояние","Записать 60 секунд","Записать 120 секунд","Медленные тики · 50 мс","Медленные тики · 100 мс","Открыть текущий отчёт","Завершить запись","Отменить запись",flag("alerts")?"Предупреждения: включены":"Предупреждения: выключены"};
            String[] ops={"health","start","start","slow","slow","open","stop","cancel","alerts"};

            String[] hints={"Создаёт отчёт в просмотрщике spark","Чужая и фоновая запись не прерываются","Результат появится после завершения записи","Только тики дольше 50 мс","Только тики дольше 100 мс","Не останавливает текущую запись","Только ваша запись, запущенная из Rivet","Без загрузки результата","Предупреждения только для вас; пауза 5 минут"};
            for(int row=firstRow;row<Math.min(count,firstRow+visibleRows);row++)for(int col=0;col<2;col++){
                int index=row*2+col;if(index>=names.length)continue;int y=top+(row-firstRow)*rowHeight,cell=(w-6)/2;
                var button=addRenderableWidget(UiActions.button(Component.literal(names[index]),UiActions.Tone.NORMAL,"",b->request(ops[index],index==2?120:60,index==4?100:50)).bounds(x+col*(cell+6),y,index==8?w:cell,20).build());
                button.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.literal(hints[index])));
                button.active=!waiting&&(index==8||flag("commands")&&!flag("busy")&&(index==0?flag("canHealth"):flag("canProfile")))&&((index!=6&&index!=7)||flag("owned")&&Json.opt(data,"profiler","").equals("running"));
            }
        }else{var reports=array("reports");for(int row=firstRow;row<Math.min(count,firstRow+visibleRows);row++){if(row>=reports.size())continue;int y=top+(row-firstRow)*rowHeight;var report=reports.get(row).getAsJsonObject();String url=Json.opt(report,"url","");
            addRenderableWidget(UiActions.button(Component.literal(UiKit.fit(font,Json.opt(report,"label","Отчёт"),w-18)),UiActions.Tone.NORMAL,"",b->open(url)).bounds(x,y,w,20).build());}}
        var footer=UiPageFooter.fit(new NativeLayout.Box(dialog.footer().x(),dialog.footer().bottom()-20,dialog.footer().width(),20));
        footer.start(UiActions.Command.REFRESH,this::addRenderableWidget,()->request("snapshot",60,50)).active=!waiting;
        footer.end(UiActions.Command.CLOSE,this::addRenderableWidget,this::onClose);
    }
    private void open(String url){if(SparkTimeline.reportUrl(url).filter(url::equals).isEmpty())return;minecraft.setScreen(new UiConfirmDialog(yes->{minecraft.setScreen(this);if(yes)net.minecraft.Util.getPlatform().openUri(url);},Component.literal("Открыть отчёт spark?"),Component.literal(url)));}
    private String value(String key){var row=object("current");if(key.equals("heap")){if(!row.has("heapUsed"))return "—";return String.format(Locale.ROOT,"%.0f / %.0f МиБ",row.get("heapUsed").getAsDouble()/1048576,row.get("heapMax").getAsDouble()/1048576);}
        double value=SparkTimeline.number(row,key);return Double.isFinite(value)?String.format(Locale.ROOT,"%.1f%s",value,key.startsWith("cpu")?"%":key.startsWith("mspt")?" мс":""):"—";}
    @Override public void tick(){if(!ServerMenuClient.may("rivet.diagnostics")){onClose();return;}if(!waiting&&System.currentTimeMillis()>=nextRefresh)request("snapshot",60,50);}
    @Override public void renderBackground(GuiGraphics g,int mx,int my,float delta){if(dialog!=null)UiDialog.surface(g,dialog.frame().x(),dialog.frame().y(),dialog.frame().width(),dialog.frame().height());}
    @Override public void render(GuiGraphics g,int mx,int my,float delta){UiDialog.render(parent,this,g,delta,()->{
        super.render(g,mx,my,delta);if(dialog==null)return;UiHeading.dialog(g,font,title,dialog.header().x(),dialog.frame().y(),dialog.header().width());
        var body=dialog.body();int top=body.y()+38;var layout=scrollLayout();if(layout==null)return;int x=layout.content().x(),w=layout.content().width();
        String status=Json.opt(data,"status",waiting?"Загрузка…":"Нет данных")+(flag("installed")?" · spark "+Json.opt(data,"version",""):"");
        Ui.text(g,font,UiKit.fit(font,status,w),x,body.y()+22,UiKit.muted(),false);
        g.enableScissor(x,top,x+w,layout.viewport().bottom());
        for(int row=firstRow;row<Math.min(tab==0?8:tab==1?5:Math.max(1,array("reports").size()),firstRow+visibleRows);row++){int y=top+(row-firstRow)*(tab==0?44:tab==1?26:36);
            if(tab==0){if(row<3){/* Metrics use the shared compact card variant. */}
                else if(row==3)graph(g,x,y,w,"TPS · последние 15 минут","tps",20);
                else if(row==4)graph(g,x,y,w,"MSPT · последние 15 минут","mspt",50);
                else if(row==5)graph(g,x,y,w,"CPU Minecraft · последние 15 минут","cpuProcess",100);
                else if(row==6){Ui.text(g,font,"Сборка мусора · с запуска JVM",x,y+4,UiKit.text(),false);int line=0;var current=object("current");if(current.has("gc"))for(var e:current.getAsJsonArray("gc")){var gc=e.getAsJsonObject();Ui.text(g,font,UiKit.fit(font,Json.opt(gc,"name","")+": "+gc.get("count")+" очисток · "+gc.get("millis")+" мс",w),x,y+15+line++*10,UiKit.muted(),false);if(line>=3)break;}}
                else{var runtime=object("rivet");Ui.text(g,font,"Rivet · база и синхронизация",x,y+4,UiKit.text(),false);Ui.text(g,font,"Очереди: запись "+numberText(runtime,"writeQueue")+" · чтение "+numberText(runtime,"readQueue"),x,y+18,UiKit.muted(),false);Ui.text(g,font,UiKit.fit(font,"Последний цикл синхронизации: "+numberText(runtime,"syncMillis")+" мс · БД: "+numberText(runtime,"databaseMillis")+" мс",w),x,y+31,UiKit.muted(),false);}
            }else if(tab==1){/* Descriptions are shown as tooltips on the action buttons. */
            }else{var reports=array("reports");if(reports.isEmpty())Ui.wrap(g,font,Component.literal("Отчётов пока нет. Запустите диагностику во вкладке «Профилирование»."),x,y,w,UiKit.muted());else{var report=reports.get(row).getAsJsonObject();String date=java.time.Instant.ofEpochMilli(report.get("at").getAsLong()).atZone(java.time.ZoneId.systemDefault()).format(java.time.format.DateTimeFormatter.ofPattern("dd.MM HH:mm"));Ui.text(g,font,UiKit.fit(font,date+" · "+Json.opt(report,"actor","")+" · spark.lucko.me",w),x,y+23,UiKit.muted(),false);}}
        }g.disableScissor();
        String hint=error.isEmpty()?Json.opt(data,"notice",""):error;if(error.isEmpty()&&!Json.opt(data,"owner","").isBlank())hint="Запись: "+Json.opt(data,"owner","")+" · осталось "+Math.max(0,(data.get("until").getAsLong()-System.currentTimeMillis())/1000)+" с";
        Ui.text(g,font,UiKit.fit(font,hint,body.width()),body.x(),dialog.footer().y()-10,error.isEmpty()?UiKit.muted():UiPalette.color(0xFFEF7777),false);
    });}
    private static String numberText(JsonObject data,String key){double value=SparkTimeline.number(data,key);return Double.isFinite(value)?String.format(Locale.ROOT,"%.0f",value):"—";}
    private void graph(GuiGraphics g,int x,int y,int w,String title,String key,double baseline){
        UiKit.surface(g,x,y,w,40,UiKit.surface());Ui.text(g,font,UiKit.fit(font,title,w-16),x+8,y+6,UiKit.muted(),false);
        var samples=array("samples");double max=baseline;int valid=0;for(var e:samples){double n=SparkTimeline.number(e.getAsJsonObject(),key);if(Double.isFinite(n)){max=Math.max(max,n);valid++;}}
        if(valid<2){Ui.text(g,font,"Собираем измерения…",x+8,y+26,UiKit.muted(),false);return;}
        int pixels=Math.max(1,w-16);for(int px=0;px<pixels;px++){int a=px*samples.size()/pixels,b=Math.max(a+1,(px+1)*samples.size()/pixels);double n=Double.NaN;for(int i=a;i<Math.min(b,samples.size());i++){double v=SparkTimeline.number(samples.get(i).getAsJsonObject(),key);if(Double.isFinite(v))n=Double.isNaN(n)?v:Math.max(n,v);}if(Double.isFinite(n)){int h=(int)Math.min(16,n*16/Math.max(1,max));g.fill(x+8+px,y+36-h,x+9+px,y+36,UiKit.accent());}}
        if(key.equals("mspt")){int line=y+36-(int)(50*16/max);g.fill(x+8,line,x+w-8,line+1,UiPalette.color(0xFFEF7777));}
    }
    @Override public boolean isPauseScreen(){return false;}
    @Override public void onClose(){minecraft.setScreen(parent);}
}
