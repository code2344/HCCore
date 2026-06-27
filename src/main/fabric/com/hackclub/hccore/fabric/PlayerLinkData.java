package com.hackclub.hccore.fabric;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

public final class PlayerLinkData {
  private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

  private final Path dataDirectory;

  public PlayerLinkData(Path dataDirectory) throws IOException {
    this.dataDirectory = dataDirectory;
    Files.createDirectories(dataDirectory);
  }

  public Entry load(UUID uuid) {
    Path path = pathFor(uuid);
    if (Files.notExists(path)) {
      return new Entry(uuid);
    }

    try (Reader reader = Files.newBufferedReader(path)) {
      Entry entry = GSON.fromJson(reader, Entry.class);
      return entry == null ? new Entry(uuid) : entry.withUuid(uuid);
    } catch (IOException e) {
      HCCoreMod.LOGGER.error("Could not load player link data for {}", uuid, e);
      return new Entry(uuid);
    }
  }

  public void save(Entry entry) throws IOException {
    Files.createDirectories(dataDirectory);
    try (Writer writer = Files.newBufferedWriter(pathFor(entry.uuid))) {
      GSON.toJson(entry, writer);
    }
  }

  public Optional<Entry> findBySlackId(String slackId) {
    if (slackId == null || slackId.isBlank() || Files.notExists(dataDirectory)) {
      return Optional.empty();
    }

    try (Stream<Path> files = Files.list(dataDirectory)) {
      return files
          .filter(path -> path.getFileName().toString().endsWith(".json"))
          .map(this::readEntry)
          .filter(Objects::nonNull)
          .filter(entry -> slackId.equals(entry.slackId))
          .findFirst();
    } catch (IOException e) {
      HCCoreMod.LOGGER.error("Could not scan player link data", e);
      return Optional.empty();
    }
  }

  public Optional<Entry> findByLastKnownName(String name) {
    if (name == null || name.isBlank() || Files.notExists(dataDirectory)) {
      return Optional.empty();
    }

    try (Stream<Path> files = Files.list(dataDirectory)) {
      return files
          .filter(path -> path.getFileName().toString().endsWith(".json"))
          .map(this::readEntry)
          .filter(Objects::nonNull)
          .filter(entry -> entry.lastKnownName != null)
          .filter(entry -> entry.lastKnownName.equalsIgnoreCase(name))
          .findFirst();
    } catch (IOException e) {
      HCCoreMod.LOGGER.error("Could not scan player link data", e);
      return Optional.empty();
    }
  }

  private Entry readEntry(Path path) {
    try (Reader reader = Files.newBufferedReader(path)) {
      Entry entry = GSON.fromJson(reader, Entry.class);
      if (entry == null || entry.uuid == null) {
        return null;
      }
      return entry;
    } catch (IOException e) {
      HCCoreMod.LOGGER.warn("Could not read player link data file {}", path, e);
      return null;
    }
  }

  private Path pathFor(UUID uuid) {
    return dataDirectory.resolve(uuid + ".json");
  }

  public static final class Entry {
    public UUID uuid;
    public String lastKnownName;
    public String slackId;

    public Entry(UUID uuid) {
      this.uuid = uuid;
    }

    public Entry withUuid(UUID uuid) {
      this.uuid = uuid;
      return this;
    }
  }
}
