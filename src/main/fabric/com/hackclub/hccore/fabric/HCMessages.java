package com.hackclub.hccore.fabric;

import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

public final class HCMessages {
  private HCMessages() {
  }

  public static Component mustLink(String baseCommand, String code, int seconds, String slackJoinUrl) {
    return Component.empty()
        .append(Component.literal("You must link your Slack account to join the server!")
            .withStyle(ChatFormatting.RED, ChatFormatting.BOLD))
        .append("\n")
        .append(Component.literal("Please run ").withStyle(ChatFormatting.WHITE))
        .append(Component.literal("/" + baseCommand + " link " + code).withStyle(ChatFormatting.GOLD))
        .append(Component.literal(" in the #minecraft channel in the Slack (" + slackJoinUrl
            + ") to link your account.").withStyle(ChatFormatting.WHITE))
        .append("\n\n")
        .append(Component.literal("This code expires in " + seconds + " seconds.")
            .withStyle(ChatFormatting.ITALIC, ChatFormatting.GRAY));
  }

  public static Component slackDisabled() {
    return Component.literal("Slack integration is not enabled.").withStyle(ChatFormatting.RED);
  }

  public static Component unlinkedInfo(String baseCommand) {
    return Component.empty()
        .append(Component.literal("Link your MC account with Hack Club Slack")
            .withStyle(ChatFormatting.RED, ChatFormatting.BOLD))
        .append("\n")
        .append(Component.literal("1. ").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD))
        .append(Component.literal("Join at https://hackclub.com/slack"))
        .append("\n")
        .append(Component.literal("2. ").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD))
        .append(Component.literal("In Slack, run /" + baseCommand + " link with the code shown if you are blocked from joining."))
        .append("\n")
        .append(Component.literal("3. ").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD))
        .append(Component.literal("Or run /slack link <your slack id> in Minecraft."));
  }

  public static Component linkedInfo(String playerName, UUID uuid, String slackName, String slackId) {
    return Component.empty()
        .append(Component.literal("Your MC account is linked to Slack!").withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD))
        .append("\n")
        .append(Component.literal("MC Name: " + playerName + "\nMC UUID: " + uuid + "\n"))
        .append(Component.literal("Slack Name: " + slackName + "\nSlack ID: " + slackId));
  }

  public static MutableComponent success(String message) {
    return Component.literal(message).withStyle(ChatFormatting.GREEN);
  }

  public static MutableComponent error(String message) {
    return Component.literal(message).withStyle(ChatFormatting.RED);
  }
}
