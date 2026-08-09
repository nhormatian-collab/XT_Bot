package com.xtsignalbot;

import android.app.*;
import android.os.*;
import android.graphics.*;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.net.*;
import java.util.*;
import org.json.*;

public class MainActivity extends Activity {
    SignalView chart; Spinner tf; EditText symbol; TextView status,signal,price,details;
    final Handler h=new Handler(Looper.getMainLooper()); boolean running=false;
    final String[] intervals={"1m","5m","15m","30m","1h","4h","1d"};

    @Override public void onCreate(Bundle b){super.onCreate(b); build();}
    TextView tv(String s,int sp){TextView t=new TextView(this);t.setText(s);t.setTextColor(Color.WHITE);t.setTextSize(sp);t.setPadding(12,8,12,8);return t;}
    Button btn(String s){Button b=new Button(this);b.setText(s);return b;}

    void build(){
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(12,10,12,10);root.setBackgroundColor(Color.rgb(15,23,42));
        TextView title=tv("XT Signal Bot",25);title.setGravity(Gravity.CENTER);root.addView(title,new LinearLayout.LayoutParams(-1,58));
        TextView sub=tv("EMA 5/10/20/100/200  •  MACD  •  RSI  •  Volume  •  ADX",13);sub.setGravity(Gravity.CENTER);root.addView(sub,new LinearLayout.LayoutParams(-1,42));

        LinearLayout row=new LinearLayout(this);row.setOrientation(LinearLayout.HORIZONTAL);
        symbol=new EditText(this);symbol.setText("btc_usdt");symbol.setTextColor(Color.WHITE);symbol.setHintTextColor(Color.GRAY);symbol.setSingleLine();symbol.setHint("Symbol");
        tf=new Spinner(this);ArrayAdapter<String>a=new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,intervals);tf.setAdapter(a);tf.setSelection(2);
        row.addView(symbol,new LinearLayout.LayoutParams(0,56,1));row.addView(tf,new LinearLayout.LayoutParams(0,56,1));root.addView(row);

        LinearLayout buttons=new LinearLayout(this);Button start=btn("شروع پایش ▶");Button stop=btn("توقف ■");buttons.addView(start,new LinearLayout.LayoutParams(0,55,1));buttons.addView(stop,new LinearLayout.LayoutParams(0,55,1));root.addView(buttons);
        status=tv("وضعیت: آماده",13);price=tv("قیمت: —",16);details=tv("RSI —   ADX —   Long —/8   Short —/8",12);root.addView(status);root.addView(price);root.addView(details);
        chart=new SignalView();root.addView(chart,new LinearLayout.LayoutParams(-1,0,1));
        signal=tv("WAIT",23);signal.setGravity(Gravity.CENTER);root.addView(signal,new LinearLayout.LayoutParams(-1,62));
        setContentView(root);
        start.setOnClickListener(v->{running=true;fetch();});stop.setOnClickListener(v->{running=false;status.setText("وضعیت: متوقف شد");});
    }

    void fetch(){
        if(!running)return;
        final String s=symbol.getText().toString().trim().toLowerCase(Locale.US);final String it=intervals[tf.getSelectedItemPosition()];
        status.setText("وضعیت: دریافت داده از XT…");
        new Thread(()->{
            try{
                String u="https://fapi.xt.com/future/market/v1/public/q/kline?symbol="+URLEncoder.encode(s,"UTF-8")+"&interval="+it+"&limit=300";
                HttpURLConnection c=(HttpURLConnection)new URL(u).openConnection();c.setConnectTimeout(12000);c.setReadTimeout(12000);c.setRequestMethod("GET");
                int code=c.getResponseCode();InputStream raw=(code>=200&&code<300)?c.getInputStream():c.getErrorStream();
                BufferedReader br=new BufferedReader(new InputStreamReader(raw));StringBuilder z=new StringBuilder();String x;while((x=br.readLine())!=null)z.append(x);
                if(code<200||code>=300)throw new Exception("HTTP "+code);
                ArrayList<Candle> cs=parse(z.toString());if(cs.size()<210)throw new Exception("داده کافی نیست: "+cs.size());
                Analysis a1=analyzeAll(cs);runOnUiThread(()->{
                    chart.set(cs,a1);price.setText("قیمت: "+fmt(cs.get(cs.size()-1).c));
                    signal.setText(a1.text);signal.setTextColor(a1.text.startsWith("BUY")?Color.rgb(74,222,128):a1.text.startsWith("SELL")?Color.rgb(248,113,113):a1.text.startsWith("EXIT")?Color.rgb(250,204,21):Color.WHITE);
                    details.setText(String.format(Locale.US,"RSI %.1f   ADX %.1f   Long %d/8   Short %d/8",a1.rsi,a1.adx,a1.buy,a1.sell));
                    status.setText("وضعیت: متصل و در حال پایش  •  "+it);
                });
            }catch(Exception e){runOnUiThread(()->status.setText("خطا: "+e.getMessage()));}
            if(running)h.postDelayed(this::fetch,15000);
        }).start();
    }

    ArrayList<Candle> parse(String j)throws Exception{
        JSONObject o=new JSONObject(j);if(o.optInt("returnCode",0)!=0)throw new Exception(o.optString("msgInfo","XT API error"));
        JSONArray a=o.getJSONArray("result");ArrayList<Candle> out=new ArrayList<>();
        for(int i=0;i<a.length();i++){JSONObject q=a.getJSONObject(i);Candle c=new Candle();c.t=q.getLong("t");c.o=q.getDouble("o");c.h=q.getDouble("h");c.l=q.getDouble("l");c.c=q.getDouble("c");c.v=q.getDouble("a");out.add(c);}Collections.sort(out,Comparator.comparingLong(v->v.t));return out;
    }
    static String fmt(double x){if(x>=1000)return String.format(Locale.US,"%.2f",x);if(x>=1)return String.format(Locale.US,"%.4f",x);return String.format(Locale.US,"%.8f",x);}
    static double[] ema(double[] x,int p){double[] e=new double[x.length];e[0]=x[0];double k=2.0/(p+1);for(int i=1;i<x.length;i++)e[i]=x[i]*k+e[i-1]*(1-k);return e;}
    static double[] rsi(double[] c,int p){double[] r=new double[c.length];double g=0,l=0;for(int i=1;i<c.length;i++){double d=c[i]-c[i-1],up=Math.max(d,0),dn=Math.max(-d,0);if(i<=p){g+=up;l+=dn;if(i==p){g/=p;l/=p;}}else{g=(g*(p-1)+up)/p;l=(l*(p-1)+dn)/p;}if(i>=p){double rs=l==0?100:g/l;r[i]=100-100/(1+rs);}}return r;}
    static double adxAt(double[] c,double[] h,double[] l,int end,int p){if(end<p+2)return 0;double atr=0,ap=0,am=0;for(int i=end-p+1;i<=end;i++){double tr=Math.max(h[i]-l[i],Math.max(Math.abs(h[i]-c[i-1]),Math.abs(l[i]-c[i-1])));double up=h[i]-h[i-1],dn=l[i-1]-l[i];atr+=tr;ap+=up>dn&&up>0?up:0;am+=dn>up&&dn>0?dn:0;}atr/=p;ap/=p;am/=p;double dp=100*ap/(atr+1e-9),dm=100*am/(atr+1e-9);return 100*Math.abs(dp-dm)/(dp+dm+1e-9);}

    static Analysis analyzeAll(ArrayList<Candle> cs){
        int n=cs.size();double[] c=new double[n],v=new double[n],hi=new double[n],lo=new double[n];for(int i=0;i<n;i++){Candle q=cs.get(i);c[i]=q.c;v[i]=q.v;hi[i]=q.h;lo[i]=q.l;}
        double[] e5=ema(c,5),e10=ema(c,10),e20=ema(c,20),e100=ema(c,100),e200=ema(c,200),m12=ema(c,12),m26=ema(c,26),macd=new double[n];for(int i=0;i<n;i++)macd[i]=m12[i]-m26[i];double[] ms=ema(macd,9),rs=rsi(c,14);
        int[] ls=new int[n],ss=new int[n];
        for(int i=1;i<n;i++){int L=0,S=0; if(e5[i]>e10[i])L++;else S++; if(e10[i]>e20[i])L++;else S++; if(e20[i]>e100[i])L++;else S++; if(e100[i]>e200[i])L++;else S++; if(macd[i]>ms[i])L++;else S++; if(rs[i]>50&&rs[i]<75)L++; if(rs[i]<50&&rs[i]>25)S++; double av=0;int a0=Math.max(0,i-20);for(int j=a0;j<i;j++)av+=v[j];av/=Math.max(1,i-a0);if(v[i]>av){if(c[i]>c[i-1])L++;else S++;}double ad=adxAt(c,hi,lo,i,14);if(ad>20){if(c[i]>e20[i])L++;else S++;}ls[i]=L;ss[i]=S;}
        Analysis a=new Analysis();a.e5=e5;a.e10=e10;a.e20=e20;a.e100=e100;a.e200=e200;a.rsiArr=rs;a.longScores=ls;a.shortScores=ss;a.buy=ls[n-1];a.sell=ss[n-1];a.rsi=rs[n-1];a.adx=adxAt(c,hi,lo,n-1,14);a.text="WAIT";
        if(n>2){boolean buy=ls[n-1]>=7&&ls[n-1]>ss[n-1]&&ls[n-2]<7;boolean sell=ss[n-1]>=7&&ss[n-1]>ls[n-1]&&ss[n-2]<7;boolean exitLong=ls[n-2]>=7&&(ls[n-1]<5||e5[n-1]<e10[n-1]&&e5[n-2]>=e10[n-2]);boolean exitShort=ss[n-2]>=7&&(ss[n-1]<5||e5[n-1]>e10[n-1]&&e5[n-2]<=e10[n-2]);if(buy)a.text="BUY ENTRY  •  "+ls[n-1]+"/8";else if(sell)a.text="SELL ENTRY  •  "+ss[n-1]+"/8";else if(exitLong)a.text="EXIT LONG";else if(exitShort)a.text="EXIT SHORT";}
        return a;
    }

    class SignalView extends View{
        Paint p=new Paint(1);ArrayList<Candle> c=new ArrayList<>();Analysis a;SignalView(){super(MainActivity.this);}
        void set(ArrayList<Candle>x,Analysis y){c=x;a=y;invalidate();}
        float py(double value,double min,double range,float top,float hh){return top+(float)((maxSafe(value,min,range))*hh);}
        double maxSafe(double value,double min,double range){return (min+range-value)/range;}
        protected void onDraw(Canvas cv){super.onDraw(cv);cv.drawColor(Color.rgb(15,23,42));if(c.size()<2||a==null)return;int N=Math.min(100,c.size()),st=c.size()-N;float w=getWidth(),hh=getHeight()-36;double max=-1e99,min=1e99;for(int i=st;i<c.size();i++){Candle z=c.get(i);max=Math.max(max,z.h);min=Math.min(min,z.l);max=Math.max(max,a.e200[i]);min=Math.min(min,a.e200[i]);}double range=Math.max(1e-12,max-min);float cw=w/N;
            drawLine(cv,a.e20,st,N,min,range,hh,Color.rgb(96,165,250),2);drawLine(cv,a.e100,st,N,min,range,hh,Color.rgb(250,204,21),2);drawLine(cv,a.e200,st,N,min,range,hh,Color.rgb(192,132,252),2);
            for(int i=st;i<c.size();i++){Candle z=c.get(i);float x=(i-st+.5f)*cw;float yo=py(z.o,min,range,0,hh),yc=py(z.c,min,range,0,hh),yh=py(z.h,min,range,0,hh),yl=py(z.l,min,range,0,hh);p.setColor(z.c>=z.o?Color.rgb(74,222,128):Color.rgb(248,113,113));p.setStrokeWidth(2);cv.drawLine(x,yh,x,yl,p);cv.drawRect(x-cw*.3f,Math.min(yo,yc),x+cw*.3f,Math.max(yo,yc)+1,p);
                if(a.longScores[i]>=7&&a.longScores[i]>a.shortScores[i]&&(i==0||a.longScores[i-1]<7)){p.setColor(Color.rgb(34,197,94));cv.drawCircle(x,Math.max(12,Math.min(hh-12,yl-10)),6,p);}
                if(a.shortScores[i]>=7&&a.shortScores[i]>a.longScores[i]&&(i==0||a.shortScores[i-1]<7)){p.setColor(Color.rgb(239,68,68));cv.drawCircle(x,Math.min(hh-12,Math.max(12,yh+10)),6,p);}
            }
            p.setColor(Color.WHITE);p.setTextSize(12);cv.drawText("EMA20",8,16,p);cv.drawText("EMA100",72,16,p);cv.drawText("EMA200",148,16,p);cv.drawText("● Entry",225,16,p);cv.drawText("RSI "+String.format(Locale.US,"%.1f",a.rsi)+"   ADX "+String.format(Locale.US,"%.1f",a.adx),8,getHeight()-10,p);
        }
        void drawLine(Canvas cv,double[] e,int st,int N,double min,double range,float hh,int color,float sw){p.setColor(color);p.setStrokeWidth(sw);p.setStyle(Paint.Style.STROKE);Path path=new Path();for(int i=st;i<c.size();i++){float x=(i-st+.5f)*getWidth()/N;float y=py(e[i],min,range,0,hh);if(i==st)path.moveTo(x,y);else path.lineTo(x,y);}cv.drawPath(path,p);p.setStyle(Paint.Style.FILL);}
    }
    static class Candle{long t;double o,h,l,c,v;}
    static class Analysis{String text;int buy,sell;double[] e5,e10,e20,e100,e200,rsiArr;int[] longScores,shortScores;double rsi,adx;}
}
