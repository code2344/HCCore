package com.hackclub.hccore.fabric;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

public final class HCCoreConfig {
  private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

  public SlackLink slackLink = new SlackLink();

  public static HCCoreConfig load(Path path) throws IOException {
    if (Files.notExists(path)) {
      Files.createDirectories(path.getParent());
      HCCoreConfig config = new HCCoreConfig();
      config.save(path);
      return config;
    }

    try (Reader reader = Files.newBufferedReader(path)) {
      HCCoreConfig config = GSON.fromJson(reader, HCCoreConfig.class);
      return config == null ? new HCCoreConfig() : config;
    }
  }

  public void save(Path path) throws IOException {
    Files.createDirectories(path.getParent());
    try (Writer writer = Files.newBufferedWriter(path)) {
      GSON.toJson(this, writer);
    }
  }

  public static final class SlackLink {
    public boolean enabled = false;
    public boolean required = false;
    public int linkCodeExpirationSeconds = 600;
    public String botToken = "";
    public String signingSecret = "";
    public String channelId = "";
    public String baseCommand = "minecraft";
    public String slackJoinUrl = "https://slack.hackclub.com";
  }
}
