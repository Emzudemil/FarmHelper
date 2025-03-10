package com.jelly.farmhelper.macros;

import com.jelly.farmhelper.FarmHelper;
import com.jelly.farmhelper.config.enums.CropEnum;
import com.jelly.farmhelper.config.enums.MacroEnum;
import com.jelly.farmhelper.config.interfaces.FailsafeConfig;
import com.jelly.farmhelper.config.interfaces.FarmConfig;
import com.jelly.farmhelper.features.Failsafe;
import com.jelly.farmhelper.player.Rotation;
import com.jelly.farmhelper.utils.*;
import net.minecraft.client.Minecraft;
import net.minecraft.init.Blocks;

import static com.jelly.farmhelper.utils.BlockUtils.*;
import static com.jelly.farmhelper.utils.KeyBindUtils.stopMovement;
import static com.jelly.farmhelper.utils.KeyBindUtils.updateKeys;

public class VerticalCropMacro extends Macro{
    private static final Minecraft mc = Minecraft.getMinecraft();
    enum direction {
        RIGHT,
        LEFT,
        FORWARD,
        NONE
    }

    direction dir;
    direction prevDir;

    float pitch;
    float yaw;

    Rotation rotation = new Rotation();

    private final Clock lastTp = new Clock();
    private boolean isTping = false;
    private final Clock waitForChangeDirection = new Clock();
    private final Clock waitBetweenTp = new Clock();

    private CropEnum crop;


    @Override
    public void onEnable() {
        lastTp.reset();
        waitForChangeDirection.reset();
        waitBetweenTp.reset();
        yaw = AngleUtils.getClosest();
        crop = MacroHandler.getFarmingCrop();
        LogUtils.debugLog("Crop: " + crop);
        MacroHandler.crop = crop;

        switch(crop){
            case SUGARCANE:
                pitch = (float) (Math.random() * 2); // 0 - 2
                break;
            case POTATO: case CARROT: case WHEAT:
                pitch = 2.8f + (float) (Math.random() * 0.6); // 2.8-3.4
                break;
            case NETHERWART:
                pitch = (float) (Math.random() * 2 - 1); // -1 - 1
                break;
            case MELON: case PUMPKIN:
                pitch = 28 + (float) (Math.random() * 2); //28-30
                break;
            case COCOA_BEANS:
                pitch = -90;
                break;
        }
        prevDir = null;
        dir = direction.NONE;
        rotation.easeTo(yaw, pitch, 500);
        if (FarmConfig.cropType != MacroEnum.PUMPKIN_MELON)
            mc.thePlayer.inventory.currentItem = PlayerUtils.getHoeSlot(crop);
        else
            mc.thePlayer.inventory.currentItem = PlayerUtils.getAxeSlot();
        isTping = false;
        if (getRelativeBlock(0, 0, 0).equals(Blocks.end_portal_frame) || getRelativeBlock(0, -1, 0).equals(Blocks.end_portal_frame)) {
            lastTp.schedule(1000);
            LogUtils.debugLog("Started on tp pad");
            waitBetweenTp.schedule(10000);
        }
    }

    @Override
    public void onDisable() {
        KeyBindUtils.stopMovement();
    }

    @Override
    public void onChatMessageReceived(String msg) {
        super.onChatMessageReceived(msg);
        if (msg.contains("Warped from the ") && msg.contains(" to the ")) {
            lastTp.schedule(1000);
            isTping = false;
            LogUtils.debugLog("Tped");
            waitBetweenTp.schedule(10000);
        }
    }

    @Override
    public void onTick() {

        if (mc.thePlayer == null || mc.theWorld == null)
            return;

        if (rotation.rotating) {
            KeyBindUtils.stopMovement();
            return;
        }

        if (lastTp.isScheduled() && lastTp.getRemainingTime() < 500 && !rotation.rotating && mc.thePlayer.rotationPitch != pitch) {
            yaw = AngleUtils.getClosest();
            rotation.easeTo(yaw, pitch, 500);
            KeyBindUtils.stopMovement();
        }

        if (lastTp.isScheduled() && !lastTp.passed() && (FarmConfig.cropType != MacroEnum.PUMPKIN_MELON)) {
            updateKeys(true, false, false, false, false);
            dir = direction.NONE;
            return;
        }

        if (lastTp.passed()) {
            lastTp.reset();
        }

        if (waitBetweenTp.isScheduled() && waitBetweenTp.passed()) {
            waitBetweenTp.reset();
        }

        if (!rotation.rotating && !lastTp.isScheduled() && !isTping && (AngleUtils.smallestAngleDifference(AngleUtils.get360RotationYaw(), yaw) > FailsafeConfig.rotationSens || Math.abs(mc.thePlayer.rotationPitch - pitch) > FailsafeConfig.rotationSens)) {
            rotation.reset();
            Failsafe.emergencyFailsafe(Failsafe.FailsafeType.ROTATION);
            return;
        }

        if ((BlockUtils.getRelativeBlock(0, -1, 0).equals(Blocks.end_portal_frame)
                || BlockUtils.getRelativeBlock(0, 0, 0).equals(Blocks.end_portal_frame) ||
                BlockUtils.getRelativeBlock(0, -2, 0).equals(Blocks.end_portal_frame)) && !lastTp.isScheduled() && (!waitBetweenTp.isScheduled() || waitBetweenTp.passed()) && !isTping) {//standing on tp pad
            isTping = true;
            LogUtils.debugLog("Scheduled tp");

//            dir = direction.NONE;

            if(mc.thePlayer.capabilities.isFlying || (!getRelativeBlock(0, 0, 0).equals(Blocks.end_portal_frame) && !mc.thePlayer.onGround)) {
                KeyBindUtils.updateKeys(false, false, false, false, false, true, false);
            } else if (isWalkable(getRelativeBlock(1, 1, 0))) {
                updateKeys(false, false, true, false, false);
            } else if (isWalkable(getRelativeBlock(-1, 1, 0))) {
                updateKeys(false, false, false, true, false);
                return;
            }
        }

        if (stairsAtTheFront()) {
            dir = direction.FORWARD;
            updateKeys(true, false, false, false, false);
            return;
        }

        if (dir == direction.FORWARD) {
            updateKeys(true, false, false, false, false);
        }

        if (isWalkable(getRightBlock()) && isWalkable(getLeftBlock())) {
            if(mc.thePlayer.lastTickPosY - mc.thePlayer.posY != 0)
                return;

            PlayerUtils.attemptSetSpawn();

            if (dir == direction.NONE) {
                dir = calculateDirection();
            }
            if (dir == direction.RIGHT)
                updateKeys(((FarmConfig.cropType != MacroEnum.PUMPKIN_MELON) && shouldWalkForwards()), false, true, false, true);
            else if (dir == direction.LEFT) {
                updateKeys((FarmConfig.cropType != MacroEnum.PUMPKIN_MELON)  && shouldWalkForwards(), false, false, true, true);
            } else {
                stopMovement();
            }
        } else if (isWalkable(getRightBlock()) && isWalkable(getRightTopBlock()) &&
                (!isWalkable(getLeftBlock()) || !isWalkable(getLeftTopBlock()))) {
            if (FarmHelper.gameState.dx < 0.01d && FarmHelper.gameState.dz < 0.01d) {
                if (waitForChangeDirection.isScheduled() && waitForChangeDirection.passed()) {
                    dir = direction.RIGHT;
                    waitForChangeDirection.reset();
                    updateKeys(false, false, true, false, true);
                    return;
                }
                if (!waitForChangeDirection.isScheduled()) {
                    long waitTime = (long) (Math.random() * 750 + 500);
                    if ((BlockUtils.getRelativeBlock(0, -1, 0).equals(Blocks.end_portal_frame)
                            || BlockUtils.getRelativeBlock(0, 0, 0).equals(Blocks.end_portal_frame) ||
                            BlockUtils.getRelativeBlock(0, -2, 0).equals(Blocks.end_portal_frame)))
                        waitTime = 1;
                    System.out.println("Scheduling wait for change direction for " + waitTime + "ms");
                    waitForChangeDirection.schedule(waitTime);
                }
            }
        } else if (isWalkable(getLeftBlock()) && isWalkable(getLeftTopBlock()) &&
                (!isWalkable(getRightBlock()) || !isWalkable(getRightTopBlock()))) {
            if (FarmHelper.gameState.dx < 0.01d && FarmHelper.gameState.dz < 0.01d) {
                if (waitForChangeDirection.isScheduled() && waitForChangeDirection.passed()) {
                    dir = direction.LEFT;
                    waitForChangeDirection.reset();
                    updateKeys(false, false, false, true, true);
                    return;
                }
                if (!waitForChangeDirection.isScheduled()) {
                    long waitTime = (long) (Math.random() * 750 + 500);
                    if ((BlockUtils.getRelativeBlock(0, -1, 0).equals(Blocks.end_portal_frame)
                            || BlockUtils.getRelativeBlock(0, 0, 0).equals(Blocks.end_portal_frame) ||
                            BlockUtils.getRelativeBlock(0, -2, 0).equals(Blocks.end_portal_frame)))
                        waitTime = 1;
                    System.out.println("Scheduling wait for change direction for " + waitTime + "ms");
                    waitForChangeDirection.schedule(waitTime);
                }
            }
        }

        if (prevDir != dir) {
            System.out.println("Changed direction to " + dir + " from " + prevDir);
            prevDir = dir;
            waitForChangeDirection.reset();
        }
    }

    private static boolean shouldWalkForwards() {
        float angle = AngleUtils.getClosest();
        double x = mc.thePlayer.posX % 1;
        double z = mc.thePlayer.posZ % 1;
        if (angle == 0) {
            return (z > -0.9 && z < -0.35) || (z < 0.65 && z > 0.1);
        } else if (angle == 90) {
            return (x > -0.65 && x < -0.1) || (x < 0.9 && x > 0.35);
        } else if (angle == 180) {
            return (z > -0.65 && z < -0.1) || (z < 0.9 && z > 0.35);
        } else if (angle == 270) {
            return (x > -0.9 && x < -0.35) || (x < 0.65 && x > 0.1);
        }
        return false;
    }

    @Override
    public void onLastRender() {
        if(rotation.rotating)
            rotation.update();
    }

    private boolean stairsAtTheFront() {
        float angle = AngleUtils.getClosest();
        double x = Math.abs(mc.thePlayer.posX % 1);
        double z = Math.abs(mc.thePlayer.posZ % 1);
        if (((angle == 0 || angle == 180) && x > 0.35 && x < 0.65) || ((angle == 90 || angle == 270) && z > 0.35 && z < 0.65)) {
            return getRelativeBlock(0, 0, 1).equals(Blocks.stone_stairs) || getRelativeBlock(0, 0, 1).equals(Blocks.oak_stairs) || getRelativeBlock(0, 0, 1).equals(Blocks.birch_stairs) || getRelativeBlock(0, 0, 1).equals(Blocks.spruce_stairs) || getRelativeBlock(0, 0, 1).equals(Blocks.jungle_stairs) || getRelativeBlock(0, 0, 1).equals(Blocks.acacia_stairs) || getRelativeBlock(0, 0, 1).equals(Blocks.dark_oak_stairs) ||
                    getRelativeBlock(0, -1, 0).equals(Blocks.stone_stairs) || getRelativeBlock(0, -1, 0).equals(Blocks.oak_stairs) || getRelativeBlock(0, -1, 0).equals(Blocks.birch_stairs) || getRelativeBlock(0, -1, 0).equals(Blocks.spruce_stairs) || getRelativeBlock(0, -1, 0).equals(Blocks.jungle_stairs) || getRelativeBlock(0, -1, 0).equals(Blocks.acacia_stairs) || getRelativeBlock(0, -1, 0).equals(Blocks.dark_oak_stairs);
        }
        return false;
    }

    direction calculateDirection() {

        boolean f1 = true, f2 = true;

        if (rightCropIsReady()) {
            return direction.RIGHT;
        } else if (leftCropIsReady()) {
            return direction.LEFT;
        }

        for (int i = 0; i < 180; i++) {
            if (isWalkable(getRelativeBlock(i, -1, 0)) && f1) {
                return direction.RIGHT;
            }
            if(!isWalkable(getRelativeBlock(i, 0, 0)))
                f1 = false;
            if (isWalkable(getRelativeBlock(-i, -1, 0)) && f2) {
                return direction.LEFT;
            }
            if(!isWalkable(getRelativeBlock(-i, 0, 0)))
                f2 = false;
        }
        return direction.NONE;
    }
}
