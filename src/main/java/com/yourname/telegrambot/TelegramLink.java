package com.yourname.telegrambot;

import org.bukkit.Bukkit;
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
        if (text.startsWith("/online")) {
            int onlineCount = Bukkit.getOnlinePlayers().size();
            String message = "🟢 На сервере сейчас " + onlineCount + " игрок(ов):\n";

            List<String> playerNames = new ArrayList<>();
            for (Player player : Bukkit.getOnlinePlayers()) {
                playerNames.add(player.getName());
            }

            if (!playerNames.isEmpty()) {
                message += String.join(", ", playerNames);
            } else {
                message += "Никого нет онлайн";
            }

            sendTelegramMessage(message);
        }
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

    @EventHandler
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        if (!botEnabled) return;
        if (botToken == null || botToken.isEmpty() || chatId == null || chatId.isEmpty()) return;

        Player player = event.getPlayer();
        String message = event.getMessage();

        String formatted = messageFormat
                .replace("{player}", player.getName())
                .replace("{message}", message);

        // Логируем отправку сообщения в Telegram
        getLogger().info("Отправка сообщения в Telegram от " + player.getName() + ": " + message);

        sendTelegramMessage(formatted);
    }

    @EventHandler
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        if (!botEnabled) return;
        if (botToken == null || botToken.isEmpty() || chatId == null || chatId.isEmpty()) return;

        Player player = event.getPlayer();
        String command = event.getMessage();

        String formatted = commandFormat
                .replace("{player}", player.getName())
                .replace("{command}", command);

        getLogger().info("Игрок " + player.getName() + " использовал команду: " + command);
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
                .replace("{command}", "/" + command);

        getLogger().info("Консоль использовала команду: " + command);
        sendTelegramMessage(formatted);
    }

    private void sendTelegramMessage(String text) {
        Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
            try {
                URL url = new URL("https://api.telegram.org/bot" + botToken + "/sendMessage");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setDoOutput(true);

                String json = String.format("{\"chat_id\":\"%s\",\"text\":\"%s\"}", chatId, escapeJson(text));

                try (OutputStream os = conn.getOutputStream()) {
                    byte[] input = json.getBytes(StandardCharsets.UTF_8);
                    os.write(input, 0, input.length);
                }

                int responseCode = conn.getResponseCode();
                if (responseCode != 200) {
                    getLogger().warning("Ошибка отправки в Telegram: " + responseCode + " - " + conn.getResponseMessage());
                } else {
                    getLogger().info("Сообщение успешно отправлено в Telegram");
                }

                conn.disconnect();
            } catch (IOException e) {
                getLogger().warning("Ошибка при отправке в Telegram: " + e.getMessage());
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