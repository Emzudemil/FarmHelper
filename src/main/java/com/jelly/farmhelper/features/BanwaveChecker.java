package com.jelly.farmhelper.features;

import com.jelly.farmhelper.macros.MacroHandler;
import com.jelly.farmhelper.network.APIHelper;
import com.jelly.farmhelper.utils.Clock;
import com.jelly.farmhelper.utils.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import java.util.LinkedList;

public class BanwaveChecker {
    private static final Clock cooldown = new Clock();
    private static final LinkedList<Integer> staffBanLast15Mins = new LinkedList<>();
    @SubscribeEvent
    public final void tick(TickEvent event) {
        if (event.phase == TickEvent.Phase.END)
            return;
        if(!cooldown.isScheduled() || cooldown.passed()){
            new Thread(() -> {
                try {
                    String s = APIHelper.readJsonFromUrl("https://api.plancke.io/hypixel/v1/punishmentStats", "User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_11_5) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/50.0.2661.102 Safari/537.36")
                            .get("record").toString();
                    JSONParser parser = new JSONParser();
                    JSONObject record = (JSONObject) parser.parse(s);

                    staffBanLast15Mins.addLast((Integer.parseInt(record.get("staff_total").toString())));
                    if(staffBanLast15Mins.size() == 17) staffBanLast15Mins.removeFirst();

                } catch(Exception e){
                    LogUtils.scriptLog(e.getMessage());
                }
            }).start();
            cooldown.schedule(60000);
        }

    }
    public static int getBanTimeDiff(){
        return staffBanLast15Mins.size() > 1 ? staffBanLast15Mins.size() - 1 : 0;
    }
    public static int getBanDiff(){
        return staffBanLast15Mins.size() > 1 ? Math.abs(staffBanLast15Mins.getLast() - staffBanLast15Mins.getFirst()) : 0;
    }
}
