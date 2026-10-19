package br.com.shocktv;

import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Bounded plain-text SRT/WebVTT timeline; positive delay displays captions later. */
public final class SubtitleTimeline {
    public static final class Cue {
        public final long start,end;public final String text;
        Cue(long s,long e,String t){start=s;end=e;text=t;}
    }
    public final ArrayList<Cue> cues=new ArrayList<>();
    private static final Pattern TIME=Pattern.compile("(?:(\\d{1,3}):)?(\\d{2}):(\\d{2})[,.](\\d{3})");
    private static long time(String value){
        Matcher m=TIME.matcher(value.trim());if(!m.find())return -1;
        return ((m.group(1)==null?0:Long.parseLong(m.group(1)))*3600+
            Long.parseLong(m.group(2))*60+Long.parseLong(m.group(3)))*1000+Long.parseLong(m.group(4));
    }
    public static SubtitleTimeline parse(String value){
        SubtitleTimeline out=new SubtitleTimeline();
        for(String block:value.replace("\r","").split("\n\\s*\n")){
            String[] lines=block.split("\n");
            for(int i=0;i<lines.length;i++)if(lines[i].contains("-->")){
                String[] pair=lines[i].split("-->",2);long s=time(pair[0]),e=time(pair[1]);
                if(s<0||e<=s)break;
                StringBuilder text=new StringBuilder();
                for(int n=i+1;n<lines.length;n++){if(text.length()>0)text.append('\n');text.append(lines[n]);}
                out.cues.add(new Cue(s,e,text.toString().replaceAll("<[^>]+>","")));break;
            }
            if(out.cues.size()>=30000)break;
        }
        out.cues.sort((a,b)->Long.compare(a.start,b.start));return out;
    }
    public String at(long position,long delay){
        long t=position-delay;StringBuilder result=new StringBuilder();
        for(Cue c:cues){if(c.start>t)break;if(c.end>t){if(result.length()>0)result.append('\n');result.append(c.text);}}
        return result.toString();
    }
}
