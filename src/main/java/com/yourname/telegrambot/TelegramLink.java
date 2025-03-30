package com.yourname.telegrambot;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.Statistic;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.server.ServerCommandEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.List;
import java.util.ArrayList;

public class TelegramLink extends JavaPlugin implements Listener {

    private boolean botEnabled;
    private String botToken;
    private String chatId;
    private String messageFormat;
    private String commandFormat;
    private String deniedCommandFormat;
    private String playerInfoFormat;
    private long lastUpdateId = 0;

    @Override
    public void onEnable() {
        // Сохраняем дефолтный конфиг если его нет
        saveDefaultConfig();
        loadConfig();

        // Регистрируем ивенты
        Bukkit.getPluginManager().registerEvents(this, this);

        // Регистрируем команду
        this.getCommand("tgbot").setExecutor(this);

        // Запускаем проверку сообщений из Telegram, если бот включен
        if (botEnabled) {
            startTelegramUpdatesChecker();
            getLogger().info("Бот Telegram активен. Ожидание команд...");
        }

        getLogger().info("Плагин включен. Статус бота: " + (botEnabled ? "ВКЛ" : "ВЫКЛ"));
    }

    private void loadConfig() {
        FileConfiguration config = getConfig();
        botToken = config.getString("telegram.bot-token");
        chatId = config.getString("telegram.chat-id");
        messageFormat = config.getString("telegram.message-format", "[Minecraft] {player}: {message}");
        commandFormat = config.getString("telegram.command-format", "[Minecraft] Игрок {player} использовал команду: {command}");
        deniedCommandFormat = config.getString("telegram.denied-command-format", "[Minecraft] Игрок {player} попытался использовать команду без прав: {command}");
        playerInfoFormat = config.getString("telegram.player-info-format",
                "📊 Информация об игроке {player}:\n" +
                        "🕒 Дата регистрации: {first-played}\n" +
                        "⏱ Общее время игры: {play-time}\n" +
                        "📍 Последний вход: {last-played}\n" +
                        "🏠 Мир: {world}");
        botEnabled = config.getBoolean("telegram.enabled", true);
    }

    private void startTelegramUpdatesChecker() {
        Bukkit.getScheduler().runTaskTimerAsynchronously(this, () -> {
            try {
                checkTelegramUpdates();
            } catch (Exception e) {
                getLogger().warning("Ошибка при проверке обновлений Telegram: " + e.getMessage());
            }
        }, 0L, 20L * 5); // Проверяем каждые 5 секунд
    }

    private void checkTelegramUpdates() {
        try {
            URL url = new URL("https://api.telegram.org/bot" + botToken + "/getUpdates?offset=" + (lastUpdateId + 1));
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");

            try (BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()))) {
                String inputLine;
                StringBuilder content = new StringBuilder();
                while ((inputLine = in.readLine()) != null) {
                    content.append(inputLine);
                }

                // Парсим JSON ответ (упрощенно)
                String response = content.toString();
                if (response.contains("\"ok\":true") && response.contains("\"result\":")) {
                    processTelegramUpdates(response);
                }
            }
        } catch (IOException e) {
            getLogger().warning("Ошибка при получении обновлений Telegram: " + e.getMessage());
        }
    }

    private void processTelegramUpdates(String json) {
        // Упрощенный парсинг JSON (в реальном проекте лучше использовать библиотеку)
        String[] updates = json.split("\"update_id\":");
        for (int i = 1; i < updates.length; i++) {
            try {
                String update = updates[i];
                long updateId = Long.parseLong(update.split(",")[0].trim());
                lastUpdateId = Math.max(lastUpdateId, updateId);

                if (update.contains("\"text\":") && update.contains("\"chat\":{\"id\":" + chatId)) {
                    String text = update.split("\"text\":\"")[1].split("\"")[0];
                    processTelegramCommand(text);
                }
            } catch (Exception e) {
                getLogger().warning("Ошибка обработки обновления Telegram: " + e.getMessage());
            }
        }
    }

    private void processTelegramCommand(String text) {
        getLogger().info("[Telegram] Получена команда: " + text);

        try {
            String command = text.trim();

            if (command.equalsIgnoreCase("/online") || command.equalsIgnoreCase("/online@YourBotName")) {
                handleOnlineCommand();
            }
            else if (command.toLowerCase().startsWith("/player ") || command.toLowerCase().startsWith("/player@yourbotname ")) {
                String playerName = command.substring(command.indexOf(" ") + 1).trim();
                handlePlayerCommand(playerName);
            }
            else if (command.equalsIgnoreCase("/help") || command.equalsIgnoreCase("/help@YourBotName")) {
                sendHelpMessage();
            }
            else if (command.toLowerCase().startsWith("/"))  {
                getLogger().warning("[Telegram] Неизвестная команда: " + command);
                sendTelegramMessage("❌ Неизвестная команда. Доступные команды:\n" +
                        "/online - список игроков онлайн\n" +
                        "/player <ник> - информация об игроке\n" +
                        "/help - справка по командам");
            }
        } catch (Exception e) {
            getLogger().severe("[Telegram] Ошибка обработки команды: " + e.getMessage());
            sendTelegramMessage("⚠ Произошла ошибка при обработке команды. Попробуйте позже.");
        }
    }

    private void handleOnlineCommand() {
        try {
            StringBuilder onlinePlayers = new StringBuilder();
            int count = 0;

            for (Player player : Bukkit.getOnlinePlayers()) {
                if (count > 0) onlinePlayers.append("\n");
                onlinePlayers.append(player.getName());
                count++;
            }

            String message;
            if (count == 0) {
                message = "🕸 На сервере сейчас нет игроков";
            } else {
                message = "🟢 Онлайн игроки (" + count + "):\n" + onlinePlayers.toString();
            }

            sendTelegramMessage(message);
            getLogger().info("[Telegram] Отправлен ответ на /online");
        } catch (Exception e) {
            getLogger().severe("[Telegram] Ошибка обработки /online: " + e.getMessage());
            sendTelegramMessage("⚠ Не удалось получить список игроков");
        }
    }


    private void handlePlayerCommand(String playerName) {
        if (playerName.isEmpty()) {
            sendTelegramMessage("ℹ Использование: /player <ник>");
            return;
        }

        // Проверяем онлайн-игроков сначала
        Player onlinePlayer = Bukkit.getPlayerExact(playerName);
        if (onlinePlayer != null) {
            sendPlayerInfo(onlinePlayer);
            return;
        }

        // Для оффлайн-игроков выполняем в асинхронном режиме
        Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
            try {
                OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(playerName);

                if (!offlinePlayer.hasPlayedBefore()) {
                    sendTelegramMessage("❌ Игрок '" + playerName + "' не найден на сервере");
                    return;
                }

                sendOfflinePlayerInfo(offlinePlayer);
            } catch (Exception e) {
                getLogger().warning("[Telegram] Ошибка обработки /player " + playerName + ": " + e.getMessage());
                sendTelegramMessage("⚠ Ошибка при получении информации об игроке " + playerName);
            }
        });
    }

    private void sendHelpMessage() {
        String helpText = "📚 Доступные команды:\n\n" +
                "/online - Показать онлайн игроков\n" +
                "/player <ник> - Информация об игроке\n" +
                "/help - Эта справка\n\n" +
                "⚙ Бот работает с Minecraft сервером " + Bukkit.getServer().getName();

        sendTelegramMessage(helpText);
    }

    private void sendPlayerInfo(Player player) {
        SimpleDateFormat dateFormat = new SimpleDateFormat("dd.MM.yyyy HH:mm");
        long playTimeTicks = player.getStatistic(Statistic.PLAY_ONE_MINUTE);

        String info = getConfig().getString("telegram.player-info-format")
                .replace("{player}", player.getName())
                .replace("{status}", "🟢 Онлайн")
                .replace("{uuid}", player.getUniqueId().toString())
                .replace("{first-played}", dateFormat.format(new Date(player.getFirstPlayed())))
                .replace("{play-time}", formatPlayTime(playTimeTicks))
                .replace("{last-played}", dateFormat.format(new Date(player.getLastPlayed())))
                .replace("{world}", player.getWorld().getName())
                .replace("{time}", getCurrentTime());

        sendTelegramMessage(info);
    }

    private void sendOfflinePlayerInfo(OfflinePlayer player) {
        SimpleDateFormat dateFormat = new SimpleDateFormat("dd.MM.yyyy HH:mm");
        long playTimeTicks = player.getStatistic(Statistic.PLAY_ONE_MINUTE);

        String info = getConfig().getString("telegram.player-info-format")
                .replace("{player}", player.getName() != null ? player.getName() : "Unknown")
                .replace("{status}", "🔴 Оффлайн")
                .replace("{uuid}", player.getUniqueId().toString())
                .replace("{first-played}", dateFormat.format(new Date(player.getFirstPlayed())))
                .replace("{play-time}", formatPlayTime(playTimeTicks))
                .replace("{last-played}", dateFormat.format(new Date(player.getLastPlayed())))
                .replace("{world}", "Не в игре")
                .replace("{time}", getCurrentTime());

        sendTelegramMessage(info);
    }

    private String formatPlayTime(long ticks) {
        long seconds = ticks / 20;
        long hours = TimeUnit.HOURS.convert(seconds, TimeUnit.SECONDS);
        long minutes = TimeUnit.MINUTES.convert(seconds, TimeUnit.SECONDS) - hours * 60;

        return String.format("%d ч. %d мин.", hours, minutes);
    }

    private void saveConfigState() {
        FileConfiguration config = getConfig();
        config.set("telegram.enabled", botEnabled);
        saveConfig();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (cmd.getName().equalsIgnoreCase("tgbot")) {
            // Логируем выполнение команды
            String senderName = sender.getName();
            String command = "/tgbot " + String.join(" ", args);
            getLogger().log(Level.INFO, "Игрок {0} выполнил команду: {1}", new Object[]{senderName, command});

            if (!sender.hasPermission("telegramlink.toggle")) {
                sender.sendMessage("§cУ вас нет прав на использование этой команды!");
                sendTelegramMessage(deniedCommandFormat
                        .replace("{player}", senderName)
                        .replace("{command}", command));
                getLogger().warning("Игрок " + senderName + " попытался выполнить команду без прав: " + command);
                return true;
            }

            if (args.length == 0) {
                sender.sendMessage("§6Использование: /tgbot <on|off|status|reload>");
                return true;
            }

            switch (args[0].toLowerCase()) {
                case "on":
                    botEnabled = true;
                    saveConfigState();
                    sender.sendMessage("§aБот Telegram включен");
                    getLogger().info("Бот Telegram был включен игроком " + senderName);
                    break;
                case "off":
                    botEnabled = false;
                    saveConfigState();
                    sender.sendMessage("§cБот Telegram выключен");
                    getLogger().info("Бот Telegram был выключен игроком " + senderName);
                    break;
                case "status":
                    sender.sendMessage("§6Статус бота: " + (botEnabled ? "§aВКЛЮЧЕН" : "§cВЫКЛЮЧЕН"));
                    getLogger().info("Игрок " + senderName + " запросил статус бота: " + (botEnabled ? "ВКЛ" : "ВЫКЛ"));
                    break;
                case "reload":
                    reloadConfig();
                    loadConfig();
                    sender.sendMessage("§aКонфигурация перезагружена");
                    getLogger().info("Конфигурация перезагружена игроком " + senderName);
                    break;
                default:
                    sender.sendMessage("§6Использование: /tgbot <on|off|status|reload>");
                    getLogger().info("Игрок " + senderName + " ввел неверную команду: " + command);
                    break;
            }
            return true;
        }
        return false;
    }

    private String getCurrentTime() {
        return new SimpleDateFormat("dd.MM.yyyy HH:mm:ss").format(new Date());
    }

    @EventHandler
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        if (!botEnabled) return;

        String formatted = getConfig().getString("telegram.message-format")
                .replace("{player}", event.getPlayer().getName())
                .replace("{message}", event.getMessage())
                .replace("{time}", getCurrentTime());

        sendTelegramMessage(formatted);
    }

    @EventHandler
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        if (!botEnabled) return;

        String formatted = getConfig().getString("telegram.command-format")
                .replace("{player}", event.getPlayer().getName())
                .replace("{command}", event.getMessage())
                .replace("{time}", getCurrentTime());

        sendTelegramMessage(formatted);
    }
    @EventHandler
    public void onConsoleCommand(ServerCommandEvent event) {
        if (!botEnabled) return;
        if (botToken == null || botToken.isEmpty() || chatId == null || chatId.isEmpty()) return;

        String sender = event.getSender().getName();
        String command = event.getCommand();

        String formatted = commandFormat
                .replace("{player}", sender)
                .replace("{command}", "/" + command)
                .replace("{time}", getCurrentTime());  // Добавляем время

        getLogger().info("Консоль использовала команду: " + command);
        sendTelegramMessage(formatted);
    }

    private void sendTelegramMessage(String text) {
        Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
            try {
                URL url = new URL("https://api.telegram.org/bot" + botToken + "/sendMessage");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
                conn.setDoOutput(true);

                // Формируем JSON вручную (лучше использовать библиотеку)
                String json = String.format("{\"chat_id\":\"%s\",\"text\":\"%s\"}",
                        chatId,
                        text.replace("\\", "\\\\")
                                .replace("\"", "\\\"")
                                .replace("\n", "\\n")
                                .replace("\r", "\\r")
                                .replace("\t", "\\t"));

                try (OutputStream os = conn.getOutputStream()) {
                    byte[] input = json.getBytes(StandardCharsets.UTF_8);
                    os.write(input, 0, input.length);
                }

                int responseCode = conn.getResponseCode();
                if (responseCode != 200) {
                    // Читаем тело ошибки для диагностики
                    try (BufferedReader br = new BufferedReader(
                            new InputStreamReader(conn.getErrorStream(), StandardCharsets.UTF_8))) {
                        StringBuilder response = new StringBuilder();
                        String responseLine;
                        while ((responseLine = br.readLine()) != null) {
                            response.append(responseLine.trim());
                        }
                        getLogger().warning("Ошибка Telegram API: " + responseCode + " - " +
                                conn.getResponseMessage() + " - " + response.toString());
                    }
                } else {
                    getLogger().info("Сообщение успешно отправлено в Telegram");
                }
            } catch (IOException e) {
                getLogger().warning("Ошибка отправки в Telegram: " + e.getMessage());
            }
        });
    }

    private String escapeJson(String input) {
        return input.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    @Override
    public void onDisable() {
        saveConfigState();
        getLogger().info("Плагин выключен");
    }
}