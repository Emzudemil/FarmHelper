package com.jelly.farmhelper.macros;

import com.jelly.farmhelper.FarmHelper;
import com.jelly.farmhelper.config.enums.CropEnum;
import com.jelly.farmhelper.config.interfaces.FailsafeConfig;
import com.jelly.farmhelper.config.interfaces.FarmConfig;
import com.jelly.farmhelper.config.interfaces.MiscConfig;
import com.jelly.farmhelper.features.Failsafe;
import com.jelly.farmhelper.player.Rotation;
import com.jelly.farmhelper.utils.*;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.init.Blocks;

import java.util.Arrays;

import static com.jelly.farmhelper.utils.BlockUtils.*;
import static com.jelly.farmhelper.utils.KeyBindUtils.*;

public class VerticalCropMacro extends Macro{
    private static final Minecraft mc = Minecraft.getMinecraft();
    enum direction {
        RIGHT,
        LEFT,
        NONE
    }

    final float RANDOM_CONST = 1 / 10.0f;
    direction dir;

    float pitch;
    float yaw;

    int flyToggleTimer = 0;

    Rotation rotation = new Rotation();

    private final Clock lastTp = new Clock();

    private final Block[] wall = new Block[]{Blocks.cobblestone, Blocks.stone_slab};

    @Override
    public void onEnable() {
        lastTp.reset();
        yaw = AngleUtils.getClosest();
        switch(FarmConfig.cropType){
            case SUGARCANE:
                pitch = (float) (Math.random() * 2); // 0 - 2
                break;
            case POTATO: case CARROT: case WHEAT:
                pitch = 2.8f + (float) (Math.random() * 0.6); // 2.8-3.4
                break;
            case NETHERWART:
                pitch = (float) (Math.random() * 2 - 1); // -1 - 1
                break;
            case MELONS:
                pitch = 28 + (float) (Math.random() * 2); //28-30
                break;
            case COCOA_BEANS:
                pitch = -90;
        }
        dir = direction.NONE;
        flyToggleTimer = 0;
        rotation.easeTo(yaw, pitch, 500);
        if (FarmConfig.cropType != CropEnum.MELONS)
            mc.thePlayer.inventory.currentItem = PlayerUtils.getHoeSlot();
        else
            mc.thePlayer.inventory.currentItem = PlayerUtils.getAxeSlot();

    }

    @Override
    public void onDisable() {
        KeyBindUtils.stopMovement();
    }

    @Override
    public void onTick() {

        if(mc.thePlayer == null || mc.theWorld == null)
            return;

        if(rotation.rotating) {
            KeyBindUtils.stopMovement();
            return;
        }

        if (lastTp.isScheduled() && !lastTp.passed()) {
            KeyBindUtils.stopMovement();
            return;
        }

        if (MiscConfig.rotateAfterTP && lastTp.isScheduled() && lastTp.getRemainingTime() < 700 && rotation.completed) {
            yaw = AngleUtils.getClosest((yaw + 180 + 360) % 360);
            rotation.easeTo(yaw, pitch, 750);
            lastTp.reset();
            dir = direction.NONE;
            return;
        } else if (lastTp.isScheduled() && lastTp.getRemainingTime() < 700 && rotation.completed) {
            yaw = AngleUtils.getClosest();
            rotation.easeTo(yaw, pitch, 500);
            lastTp.reset();
            dir = direction.NONE;
            return;
        }

        if (lastTp.passed()) {
            lastTp.reset();
        }

        if (!lastTp.isScheduled() && (AngleUtils.smallestAngleDifference(AngleUtils.get360RotationYaw(), yaw) > FailsafeConfig.rotationSens || Math.abs(mc.thePlayer.rotationPitch - pitch) > FailsafeConfig.rotationSens)) {
            rotation.reset();
            Failsafe.emergencyFailsafe(Failsafe.FailsafeType.ROTATION);
            return;
        }

        if (BlockUtils.getRelativeBlock(0, -1, 0).equals(Blocks.end_portal_frame)
                || BlockUtils.getRelativeBlock(0, 0, 0).equals(Blocks.end_portal_frame) ||
                BlockUtils.getRelativeBlock(0, -2, 0).equals(Blocks.end_portal_frame)) {//standing on tp pad
            if(flyToggleTimer > 0)
                flyToggleTimer --;
            if (!lastTp.isScheduled())
                lastTp.schedule(1500);
            LogUtils.debugLog("Scheduled tp");
            dir = direction.NONE;

            if(mc.thePlayer.capabilities.isFlying || (!getRelativeBlock(0, 0, 0).equals(Blocks.end_portal_frame) && !mc.thePlayer.onGround)) {
                KeyBindUtils.updateKeys(false, false, false, false, false, true, false);
//            } else if (mc.thePlayer.posY % 1 > 0 && mc.thePlayer.posY % 1 < 0.8125f) {
//                KeyBindUtils.updateKeys(false, false, false, false, false, false, true);
            } else if (isWalkable(getRelativeBlock(1, 1, 0))) {
                updateKeys(false, false, true, false, false);
            } else if (isWalkable(getRelativeBlock(-1, 1, 0))) {
                updateKeys(false, false, false, true, false);
                return;
            }
        }

        if (isWalkable(getRightBlock()) && isWalkable(getLeftBlock())) {
            if(mc.thePlayer.lastTickPosY - mc.thePlayer.posY != 0)
                return;

            if (dir == direction.NONE) {
                dir = calculateDirection();
            }
            if (dir == direction.RIGHT) {
                updateKeys(FarmConfig.cropType != CropEnum.MELONS && shouldWalkForwards(), false, true, false, true);
            } else if (dir == direction.LEFT) {
                updateKeys(FarmConfig.cropType != CropEnum.MELONS && shouldWalkForwards(), false, false, true, true);
            } else {
                stopMovement();
            }
        } else if ((!isWalkable(getLeftBlock()) && (Arrays.asList(wall).contains(getLeftBlock()) || Arrays.asList(wall).contains(getLeftTopBlock()))) &&
                (isWalkable(getRightBlock()) && !Arrays.asList(wall).contains(getRightBlock()) && !Arrays.asList(wall).contains(getRightTopBlock()))) {
            if (FarmHelper.gameState.dx < 0.01d && FarmHelper.gameState.dz < 0.01d && Math.random() < RANDOM_CONST) {
                dir = direction.RIGHT;
                updateKeys(false, false, true, false, true);

                if(Math.random() < 0.5d)
                    PlayerUtils.attemptSetSpawn();
            }
        } else if (isWalkable(getLeftBlock()) && !Arrays.asList(wall).contains(getLeftBlock()) && !Arrays.asList(wall).contains(getLeftTopBlock()) &&
                (!isWalkable(getRightBlock()) && (Arrays.asList(wall).contains(getRightBlock()) || Arrays.asList(wall).contains(getRightTopBlock())))) {
            if (FarmHelper.gameState.dx < 0.01d && FarmHelper.gameState.dz < 0.01d && Math.random() < RANDOM_CONST) {
                dir = direction.LEFT;
                updateKeys(false, false, false, true, true);

                if(Math.random() < 0.5d)
                    PlayerUtils.attemptSetSpawn();
            }
        }
    }

    private static boolean shouldWalkForwards() {
        float angle = AngleUtils.getClosest();
        double x = mc.thePlayer.posX % 1;
        double z = mc.thePlayer.posZ % 1;
        System.out.println(angle);
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

    direction calculateDirection() {

        boolean f1 = true, f2 = true;

        if (!leftCropIsReady()) {
            return direction.RIGHT;
        } else if (!rightCropIsReady()) {
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
