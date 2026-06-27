package com.hackclub.hccore.fabric;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.slack.api.model.User;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;
import java.util.UUID;
import net.fabricmc.api.DedicatedServerModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class HCCoreMod implements DedicatedServerModInitializer {
  public static final String MOD_ID = "hccore";
  public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

  private HCCoreConfig config;
  private PlayerLinkData playerData;
  private SlackBot slackBot;
  private MinecraftServer server;

  @Override
  public void onInitializeServer() {
    try {
      Path configDir = FabricLoader.getInstance().getConfigDir().resolve(MOD_ID);
      this.config = HCCoreConfig.load(configDir.resolve("config.json"));
      this.playerData = new PlayerLinkData(configDir.resolve("players"));
      if (config.slackLink.enabled) {
        this.slackBot = new SlackBot(this, config.slackLink);
      }
    } catch (Exception e) {
      LOGGER.error("Could not initialize HCCore", e);
      this.config = new HCCoreConfig();
    }

    ServerLifecycleEvents.SERVER_STARTED.register(startedServer -> this.server = startedServer);
    ServerLifecycleEvents.SERVER_STOPPING.register(stoppingServer -> {
      if (slackBot != null) {
        try {
          slackBot.stop();
        } catch (Exception e) {
          LOGGER.warn("Could not stop Slack bot", e);
        }
      }
    });

    CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
        dispatcher.register(literal("slack")
            .executes(context -> slackInfo(context.getSource()))
            .then(literal("info").executes(context -> slackInfo(context.getSource())))
            .then(literal("link")
                .then(argument("slack_id", StringArgumentType.word())
                    .executes(context -> slackLink(context.getSource(),
                        StringArgumentType.getString(context, "slack_id")))))
            .then(literal("lookup")
                .then(argument("player", StringArgumentType.word())
                    .executes(context -> slackLookup(context.getSource(),
                        StringArgumentType.getString(context, "player")))))));

    ServerPlayConnectionEvents.JOIN.register((handler, sender, joinedServer) -> {
      ServerPlayer player = handler.player;
      PlayerLinkData.Entry data = playerData.load(player.getUUID());
      data.lastKnownName = player.getGameProfile().name();

      try {
        playerData.save(data);
      } catch (IOException e) {
        LOGGER.warn("Could not save player link data for {}", player.getUUID(), e);
      }

      if (slackBot != null && !slackBot.isJoinAllowed(data)) {
        String code = slackBot.generateVerificationCode(player.getUUID());
        Component message = HCMessages.mustLink(config.slackLink.baseCommand, code,
            config.slackLink.linkCodeExpirationSeconds, config.slackLink.slackJoinUrl);
        LOGGER.info("Preventing {}'s join because they are not linked", player.getGameProfile().name());
        player.connection.disconnect(message);
      }
    });
  }

  public PlayerLinkData playerData() {
    return playerData;
  }

  public MinecraftServer server() {
    return server;
  }

  private int slackInfo(CommandSourceStack source) {
    if (slackBot == null) {
      source.sendFailure(HCMessages.slackDisabled());
      return 1;
    }
    ServerPlayer player;
    try {
      player = source.getPlayerOrException();
    } catch (Exception e) {
      source.sendFailure(Component.literal("Only players can use this command.").withStyle(ChatFormatting.RED));
      return 1;
    }

    PlayerLinkData.Entry data = playerData.load(player.getUUID());
    if (data.slackId == null || data.slackId.isBlank()) {
      source.sendSuccess(() -> HCMessages.unlinkedInfo(config.slackLink.baseCommand), false);
      return 1;
    }

    try {
      User slackUser = slackBot.getUserInfo(data.slackId);
      String slackName = slackUser == null ? "Unlinked" : displayName(slackUser);
      String slackId = slackUser == null ? data.slackId : slackUser.getId();
      source.sendSuccess(() -> HCMessages.linkedInfo(player.getGameProfile().name(),
          player.getUUID(), slackName, slackId), false);
    } catch (IOException e) {
      source.sendFailure(HCMessages.error("Could not look up your Slack account."));
    }
    return 1;
  }

  private int slackLink(CommandSourceStack source, String slackId) {
    if (slackBot == null) {
      source.sendFailure(HCMessages.slackDisabled());
      return 1;
    }
    ServerPlayer player;
    try {
      player = source.getPlayerOrException();
    } catch (Exception e) {
      source.sendFailure(Component.literal("Only players can use this command.").withStyle(ChatFormatting.RED));
      return 1;
    }

    try {
      if (slackBot.getUserInfo(slackId) == null) {
        source.sendFailure(HCMessages.error("That Slack ID is invalid!"));
        return 1;
      }

      boolean sent = slackBot.sendVerificationMessage(slackId, player.getGameProfile().name(),
          player.getUUID());
      if (sent) {
        source.sendSuccess(() -> HCMessages.success("A verification message has been sent to your Slack account."), false);
      } else {
        source.sendFailure(HCMessages.error("Could not send the verification message."));
      }
    } catch (IOException e) {
      source.sendFailure(HCMessages.error("An error occurred while linking your account."));
    }
    return 1;
  }

  private int slackLookup(CommandSourceStack source, String playerName) {
    if (slackBot == null) {
      source.sendFailure(HCMessages.slackDisabled());
      return 1;
    }

    Optional<PlayerLinkData.Entry> data = playerData.findByLastKnownName(playerName);
    if (data.isEmpty() || data.get().slackId == null || data.get().slackId.isBlank()) {
      source.sendFailure(HCMessages.error("That player has not linked their Slack account."));
      return 1;
    }

    try {
      User slackUser = slackBot.getUserInfo(data.get().slackId);
      if (slackUser == null) {
        source.sendFailure(HCMessages.error("That player has not linked their Slack account."));
        return 1;
      }
      source.sendSuccess(() -> HCMessages.success(
          "Slack username for " + playerName + ": " + displayName(slackUser)), false);
    } catch (IOException e) {
      source.sendFailure(HCMessages.error("Could not look up that Slack account."));
    }
    return 1;
  }

  private static String displayName(User slackUser) {
    if (slackUser.getProfile() != null && slackUser.getProfile().getDisplayName() != null
        && !slackUser.getProfile().getDisplayName().isBlank()) {
      return slackUser.getProfile().getDisplayName();
    }
    if (slackUser.getRealName() != null && !slackUser.getRealName().isBlank()) {
      return slackUser.getRealName();
    }
    return slackUser.getName();
  }
}
