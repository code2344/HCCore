package com.hackclub.hccore.fabric;

import static com.slack.api.model.block.Blocks.actions;
import static com.slack.api.model.block.Blocks.asBlocks;
import static com.slack.api.model.block.Blocks.section;
import static com.slack.api.model.block.composition.BlockCompositions.markdownText;
import static com.slack.api.model.block.element.BlockElements.asElements;
import static com.slack.api.model.block.element.BlockElements.button;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.slack.api.bolt.App;
import com.slack.api.bolt.AppConfig;
import com.slack.api.bolt.jetty.SlackAppServer;
import com.slack.api.bolt.request.builtin.SlashCommandRequest;
import com.slack.api.methods.MethodsClient;
import com.slack.api.methods.SlackApiException;
import com.slack.api.model.User;
import com.slack.api.model.event.MessageBotEvent;
import com.slack.api.model.event.MessageDeletedEvent;
import java.io.IOException;
import java.security.SecureRandom;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

public final class SlackBot {
  private static final String CODE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

  private final HCCoreMod mod;
  private final HCCoreConfig.SlackLink config;
  private final SecureRandom random = new SecureRandom();
  private final Map<UUID, LinkCode> linkCodes = new HashMap<>();
  private final App app;
  private final SlackAppServer server;

  public SlackBot(HCCoreMod mod, HCCoreConfig.SlackLink config) throws Exception {
    this.mod = mod;
    this.config = config;
    this.app = new App(AppConfig.builder()
        .singleTeamBotToken(required(config.botToken, "Slack bot token"))
        .signingSecret(required(config.signingSecret, "Slack signing secret"))
        .build());

    registerSlackEvents();

    this.server = new SlackAppServer(app);
    CompletableFuture.runAsync(() -> {
      try {
        this.server.start();
      } catch (Exception e) {
        HCCoreMod.LOGGER.error("Could not start Slack app server", e);
      }
    });
  }

  public void stop() throws Exception {
    server.stop();
  }

  public User getUserInfo(String slackId) throws IOException {
    try {
      var response = client().usersInfo(request -> request.token(config.botToken).user(slackId));
      if (!response.isOk()) {
        return null;
      }
      return response.getUser();
    } catch (SlackApiException e) {
      return null;
    }
  }

  public boolean isJoinAllowed(PlayerLinkData.Entry data) {
    if (!config.enabled || !config.required) {
      return true;
    }
    if (data.slackId == null || data.slackId.isBlank()) {
      return false;
    }

    try {
      User user = getUserInfo(data.slackId);
      return user != null && !user.isDeleted();
    } catch (IOException e) {
      HCCoreMod.LOGGER.warn("Could not check Slack status for {}", data.slackId, e);
      return false;
    }
  }

  public boolean sendVerificationMessage(String slackId, String mcName, UUID mcUuid) throws IOException {
    try {
      var response = client().chatPostMessage(request -> request
          .token(config.botToken)
          .channel(slackId)
          .text("A player on the Hack Club Minecraft server with username " + mcName
              + " (UUID " + mcUuid + ") is trying to link to your Slack account")
          .blocks(asBlocks(section(section -> section.text(markdownText(
                  "A player on the Hack Club Minecraft server with username *" + mcName
                      + "* (UUID `" + mcUuid + "`) is trying to link to your Slack account. "
                      + "If you are this player, click the \"Verify\" button. If you are not this player, click the \"Deny\" button."))),
              actions(actions -> actions.elements(asElements(
                  button(button -> button.actionId("verify-link")
                      .text(com.slack.api.model.block.composition.BlockCompositions.plainText("Verify"))
                      .value(mcUuid.toString())
                      .style("primary")),
                  button(button -> button.actionId("deny-link")
                      .text(com.slack.api.model.block.composition.BlockCompositions.plainText("Deny"))
                      .value(mcUuid.toString())
                      .style("danger"))))))));

      return response.isOk();
    } catch (SlackApiException e) {
      HCCoreMod.LOGGER.warn("Could not send Slack verification message", e);
      return false;
    }
  }

  public String generateVerificationCode(UUID mcUuid) {
    long now = System.currentTimeMillis();
    LinkCode existing = linkCodes.get(mcUuid);
    if (existing != null && existing.expiresAtMillis > now) {
      return existing.code;
    }

    StringBuilder code = new StringBuilder(6);
    for (int i = 0; i < 6; i++) {
      code.append(CODE_CHARS.charAt(random.nextInt(CODE_CHARS.length())));
    }
    linkCodes.put(mcUuid, new LinkCode(code.toString(),
        now + config.linkCodeExpirationSeconds * 1000L));
    return code.toString();
  }

  private void registerSlackEvents() {
    app.blockAction("verify-link", (request, context) -> {
      UUID mcUuid = UUID.fromString(request.getPayload().getActions().get(0).getValue());
      String slackId = request.getPayload().getUser().getId();
      PlayerLinkData.Entry data = mod.playerData().load(mcUuid);
      data.slackId = slackId;
      mod.playerData().save(data);

      context.respond("Your accounts have been linked!");
      MinecraftServer minecraftServer = mod.server();
      if (minecraftServer != null) {
        ServerPlayer player = minecraftServer.getPlayerList().getPlayer(mcUuid);
        if (player != null) {
          player.sendSystemMessage(HCMessages.success("Your accounts have been linked!"));
        }
      }
      return context.ack();
    });

    app.blockAction("deny-link", (request, context) -> {
      UUID mcUuid = UUID.fromString(request.getPayload().getActions().get(0).getValue());
      context.respond("Denied link request");
      MinecraftServer minecraftServer = mod.server();
      if (minecraftServer != null) {
        ServerPlayer player = minecraftServer.getPlayerList().getPlayer(mcUuid);
        if (player != null) {
          player.sendSystemMessage(HCMessages.error("The request to link the account was denied."));
        }
      }
      return context.ack();
    });

    app.event(MessageBotEvent.class, (payload, context) -> context.ack());
    app.event(MessageDeletedEvent.class, (payload, context) -> context.ack());

    CommandDispatcher<SlashCommandRequest> dispatcher = new CommandDispatcher<>();
    dispatcher.register(com.mojang.brigadier.builder.LiteralArgumentBuilder
        .<SlashCommandRequest>literal("/" + config.baseCommand)
        .then(com.mojang.brigadier.builder.LiteralArgumentBuilder.<SlashCommandRequest>literal("link")
            .then(com.mojang.brigadier.builder.RequiredArgumentBuilder
                .<SlashCommandRequest, String>argument("code", StringArgumentType.greedyString())
                .executes(context -> {
                  try {
                    return linkFromSlackCode(context.getSource(),
                        StringArgumentType.getString(context, "code"));
                  } catch (IOException e) {
                    try {
                      context.getSource().getContext().respond("Could not link your account.");
                    } catch (IOException responseError) {
                      HCCoreMod.LOGGER.warn("Could not send Slack link error response", responseError);
                    }
                    return 1;
                  }
                })))
        .then(com.mojang.brigadier.builder.LiteralArgumentBuilder.<SlashCommandRequest>literal("lookup")
            .then(com.mojang.brigadier.builder.RequiredArgumentBuilder
                .<SlashCommandRequest, String>argument("mention", StringArgumentType.greedyString())
                .executes(context -> {
                  try {
                    return lookupMinecraftAccount(context.getSource(),
                        StringArgumentType.getString(context, "mention"));
                  } catch (IOException e) {
                    try {
                      context.getSource().getContext().respond("Could not look up that account.");
                    } catch (IOException responseError) {
                      HCCoreMod.LOGGER.warn("Could not send Slack lookup error response", responseError);
                    }
                    return 1;
                  }
                }))));

    app.command("/" + config.baseCommand, (request, context) -> {
      String text = request.getPayload().getText();
      String command = request.getPayload().getCommand() + (text == null || text.isBlank() ? "" : " " + text);
      try {
        dispatcher.execute(command, request);
      } catch (Exception e) {
        context.respond("Parsing error: " + e.getMessage());
      }
      return context.ack();
    });
  }

  private int linkFromSlackCode(SlashCommandRequest request, String code) throws IOException {
    UUID mcUuid = consumeCode(code.trim());
    if (mcUuid == null) {
      request.getContext().respond("Invalid or expired code");
      return 1;
    }

    PlayerLinkData.Entry data = mod.playerData().load(mcUuid);
    if (data.slackId != null) {
      request.getContext().respond("This Minecraft account is already linked.");
      return 1;
    }

    data.slackId = request.getContext().getRequestUserId();
    mod.playerData().save(data);
    request.getContext().respond(
        "Successfully linked your Minecraft account to your Slack account! You may now join the server.");
    return 1;
  }

  private int lookupMinecraftAccount(SlashCommandRequest request, String mention) throws IOException {
    String slackId = mention.trim();
    if (slackId.startsWith("<@") && slackId.endsWith(">")) {
      int pipe = slackId.indexOf('|');
      slackId = pipe == -1 ? slackId.substring(2, slackId.length() - 1) : slackId.substring(2, pipe);
    }

    Optional<PlayerLinkData.Entry> data = mod.playerData().findBySlackId(slackId);
    request.getContext().respond(data
        .map(entry -> "The linked user is " + (entry.lastKnownName == null ? entry.uuid : entry.lastKnownName))
        .orElse("No linked user was found"));
    return 1;
  }

  private UUID consumeCode(String code) {
    long now = System.currentTimeMillis();
    UUID matchedUuid = null;
    for (Map.Entry<UUID, LinkCode> entry : linkCodes.entrySet()) {
      if (entry.getValue().expiresAtMillis > now && entry.getValue().code.equalsIgnoreCase(code)) {
        matchedUuid = entry.getKey();
        break;
      }
    }
    if (matchedUuid != null) {
      linkCodes.remove(matchedUuid);
    }
    return matchedUuid;
  }

  private MethodsClient client() {
    return app.getClient();
  }

  private static String required(String value, String label) {
    if (value == null || value.isBlank()) {
      throw new IllegalStateException(label + " is not set");
    }
    return value;
  }

  private record LinkCode(String code, long expiresAtMillis) {
  }
}
