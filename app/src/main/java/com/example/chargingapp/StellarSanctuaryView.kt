package com.example.chargingapp
import android.content.*
import android.graphics.*
import android.os.BatteryManager
import android.view.View
import kotlin.math.*

class StellarSanctuaryView(c:Context):View(c){
 private val G=Color.rgb(246,226,177);private val GB=Color.rgb(255,239,188);private val B=Color.rgb(160,225,255);private val BB=Color.rgb(70,195,255);private val W=Color.WHITE
 private val s=Paint(3).apply{style=Paint.Style.STROKE;strokeCap=Paint.Cap.ROUND;strokeJoin=Paint.Join.ROUND};private val f=Paint(3);private val t=Paint(3).apply{textAlign=Paint.Align.CENTER}
 private var pc=69;private var tp=32.5f;private var hl="양호";private var pl="연결되지 않음"
 init{setLayerType(LAYER_TYPE_SOFTWARE,null);setBackgroundColor(Color.rgb(2,4,8))}
 fun updateBattery(i:Intent){val l=i.getIntExtra(BatteryManager.EXTRA_LEVEL,-1);val z=i.getIntExtra(BatteryManager.EXTRA_SCALE,-1);if(l>=0&&z>0)pc=(l*100f/z).toInt();tp=i.getIntExtra(BatteryManager.EXTRA_TEMPERATURE,325)/10f;hl=if(i.getIntExtra(BatteryManager.EXTRA_HEALTH,0)==BatteryManager.BATTERY_HEALTH_GOOD)"양호" else "확인 중";pl=when(i.getIntExtra(BatteryManager.EXTRA_PLUGGED,0)){BatteryManager.BATTERY_PLUGGED_AC->"유선 충전";BatteryManager.BATTERY_PLUGGED_USB->"USB 충전";BatteryManager.BATTERY_PLUGGED_WIRELESS->"무선 충전";else->"연결되지 않음"};invalidate()}
 override fun onDraw(c:Canvas){val w=width.toFloat();val h=height.toFloat();val x=w/2;val y=h*.47f;val r=min(w*.455f,h*.285f)
  f.shader=RadialGradient(x,y,r*1.5f,intArrayOf(Color.rgb(18,55,78),Color.rgb(4,12,20),Color.rgb(2,4,8)),null,Shader.TileMode.CLAMP);c.drawRect(0f,0f,w,h,f);f.shader=null
  for(i in 0..110){val xx=(abs(sin(i*12.9898)*43758.5)%1).toFloat()*w;val yy=(abs(sin(i*78.233)*19341.1)%1).toFloat()*h;node(c,xx,yy,if(i%11==0)2f else .8f,if(i%5==0)G else B)}
  t.color=GB;t.textSize=w*.03f;t.setShadowLayer(15f,0f,0f,G);c.drawText("✦",x,h*.07f,t);t.clearShadowLayer();t.textSize=w*.055f;c.drawText("별을 읽는 성역",x,h*.11f,t);t.color=G;t.textSize=w*.03f;c.drawText("지혜는 내일을 비춘다.",x,h*.16f,t)
  val rs=floatArrayOf(1f,.965f,.925f,.875f,.825f,.765f,.70f,.64f);for(i in rs.indices)glow(c,x,y,r*rs[i],if(i%2==0)G else B,if(i<2)1.5f else .8f)
  ticks(c,x,y,r*.96f,r*.03f,192,16);ticks(c,x,y,r*.85f,r*.022f,144,12);ticks(c,x,y,r*.73f,r*.018f,120,10)
  for(i in 0 until 64){val a=i*360f/64;val q=p(x,y,r*.91f,a);rune(c,q.first,q.second,r*.018f,a,i%4,if(i%4==0)GB else G)}
  for(i in 0 until 56){val a=i*360f/56;val q=p(x,y,r*.79f,a);rune(c,q.first,q.second,r*.016f,a,(i+1)%4,if(i%3==0)W else B)}
  s.color=G;s.strokeWidth=1.1f;c.drawLine(x,y-r*1.04f,x,y+r*1.04f,s);c.drawLine(x-r*1.04f,y,x+r*1.04f,y,s)
  poly(c,x,y,r*.68f,12,0f,B,1f);star(c,x,y,r*.65f,r*.43f,12,0f,G,1.2f);poly(c,x,y,r*.54f,8,22.5f,BB,1.1f);star(c,x,y,r*.50f,r*.29f,8,22.5f,W,1f);poly(c,x,y,r*.40f,6,30f,GB,1f)
  for(i in 0 until 16){val a=i*22.5f;val q=p(x,y,r*.70f,a);s.color=if(i%2==0)G else B;s.alpha=65;c.drawLine(x,y,q.first,q.second,s)};s.alpha=255
  for(i in 0 until 8){val a=i*45f;val q=p(x,y,r*.53f,a);glow(c,q.first,q.second,r*.07f,if(i%2==0)BB else GB,1f);poly(c,q.first,q.second,r*.035f,if(i%2==0)4 else 6,a,if(i%2==0)G else B,.9f);node(c,q.first,q.second,r*.008f,W)}
  for(i in 0..4){c.save();c.rotate(i*36f,x,y);s.color=if(i%2==0)B else G;s.alpha=170;s.strokeWidth=if(i<2)1.6f else 1f;val rx=r*(.62f-i*.012f);val ry=r*(.215f+(i%2)*.025f);c.drawOval(RectF(x-rx,y-ry,x+rx,y+ry),s);c.restore()};s.alpha=255
  for(a in listOf(0f,45f,90f,135f,180f,225f,270f,315f)){val q=p(x,y,r*(if(a%90f==0f).62f else .56f),a);node(c,q.first,q.second,r*.018f,if(a%90f==0f)BB else GB)}
  f.shader=RadialGradient(x,y,r*.42f,intArrayOf(Color.argb(100,255,255,255),Color.argb(55,255,230,170),Color.argb(25,80,190,255),Color.TRANSPARENT),null,Shader.TileMode.CLAMP);c.drawCircle(x,y,r*.42f,f);f.shader=null;f.color=Color.rgb(3,9,15);c.drawCircle(x,y,r*.29f,f);glow(c,x,y,r*.30f,BB,1.8f);glow(c,x,y,r*.255f,GB,.9f);poly(c,x,y,r*.225f,6,30f,GB,1.1f);poly(c,x,y,r*.185f,6,0f,W,1f)
  f.color=Color.argb(225,4,10,16);c.drawCircle(x,y,r*.205f,f);t.color=GB;t.textSize=r*.12f;t.setShadowLayer(r*.025f,0f,0f,G);c.drawText("$pc%",x,y,t);t.clearShadowLayer();t.textSize=r*.035f;c.drawText(pl,x,y+r*.10f,t)
  val fy=y+r*1.14f;t.color=G;t.textSize=w*.03f;c.drawText("지혜는 더 밝은 내일을 비춘다.",x,fy+h*.035f,t);info(c,w*.2f,fy+h*.095f,"%.1f°C".format(tp),"배터리 온도",w);info(c,w*.5f,fy+h*.095f,hl,"배터리 상태",w);info(c,w*.8f,fy+h*.095f,pl,"연결 방식",w)
 }
 private fun info(c:Canvas,x:Float,y:Float,a:String,b:String,w:Float){t.color=W;t.textSize=w*.035f;c.drawText(a,x,y,t);t.color=G;t.textSize=w*.022f;c.drawText(b,x,y+height*.028f,t)}
 private fun ticks(c:Canvas,x:Float,y:Float,r:Float,l:Float,n:Int,m:Int){for(i in 0 until n){val a=i*360f/n;val q=p(x,y,r-if(i%m==0)l else l*.35f,a);val e=p(x,y,r,a);s.color=if(i%m==0)GB else B;s.alpha=if(i%m==0)230 else 80;s.strokeWidth=if(i%m==0)1.5f else .65f;c.drawLine(q.first,q.second,e.first,e.second,s)};s.alpha=255}
 private fun rune(c:Canvas,x:Float,y:Float,z:Float,a:Float,k:Int,col:Int){c.save();c.rotate(a,x,y);s.color=col;s.strokeWidth=1f;if(k%2==0){c.drawLine(x-z,y,x+z,y,s);c.drawLine(x,y-z,x,y+z,s)}else{val q=Path();q.moveTo(x-z,y+z);q.lineTo(x,y-z);q.lineTo(x+z,y+z);q.close();c.drawPath(q,s)};c.restore()}
 private fun glow(c:Canvas,x:Float,y:Float,r:Float,col:Int,w:Float){s.color=col;for(v in listOf(22 to w*10,55 to w*4,220 to w)){s.alpha=v.first;s.strokeWidth=v.second;c.drawCircle(x,y,r,s)};s.alpha=255}
 private fun node(c:Canvas,x:Float,y:Float,r:Float,col:Int){f.color=col;f.alpha=30;c.drawCircle(x,y,r*3f,f);f.alpha=255;c.drawCircle(x,y,r,f)}
 private fun poly(c:Canvas,x:Float,y:Float,r:Float,n:Int,rot:Float,col:Int,w:Float){val q=Path();for(i in 0 until n){val v=p(x,y,r,rot+i*360f/n);if(i==0)q.moveTo(v.first,v.second)else q.lineTo(v.first,v.second)};q.close();s.color=col;s.strokeWidth=w;c.drawPath(q,s)}
 private fun star(c:Canvas,x:Float,y:Float,ro:Float,ri:Float,n:Int,rot:Float,col:Int,w:Float){val q=Path();for(i in 0 until n*2){val v=p(x,y,if(i%2==0)ro else ri,rot+i*180f/n);if(i==0)q.moveTo(v.first,v.second)else q.lineTo(v.first,v.second)};q.close();s.color=col;s.strokeWidth=w;c.drawPath(q,s)}
 private fun p(x:Float,y:Float,r:Float,a:Float):Pair<Float,Float>{val z=Math.toRadians((a-90).toDouble());return Pair(x+(cos(z)*r).toFloat(),y+(sin(z)*r).toFloat())}
}