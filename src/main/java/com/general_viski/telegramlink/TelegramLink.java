package com.general_viski.telegramlink;

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


import org.bukkit.Server;
import org.bukkit.command.CommandSender;
import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionAttachment;
import org.bukkit.permissions.PermissionAttachmentInfo;
import org.bukkit.plugin.Plugin;
import java.util.UUID;
import java.util.HashSet;
import java.util.Set;

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

    private String lastUpdateJson; // Добавьте это поле в класс
    private boolean botEnabled;
    private String botToken;
    private String chatId;
    private String messageFormat;
    private String commandFormat;
    private String deniedCommandFormat;
    private String playerInfoFormat;
    private long lastUpdateId = 0;
    private List<Long> allowedTelegramIds; // Список разрешенных Telegram ID

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
        // Загружаем список разрешенных Telegram ID
        allowedTelegramIds = new ArrayList<>();
        for (String id : config.getStringList("telegram.allowed-ids")) {
            try {
                allowedTelegramIds.add(Long.parseLong(id));
            } catch (NumberFormatException e) {
                getLogger().warning("Некорректный Telegram ID в конфиге: " + id);
            }
        }
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
        this.lastUpdateJson = json; // Сохраняем сырой JSON для последующего использования

        try {
            // Упрощенный парсинг JSON
            String[] updates = json.split("\"update_id\":");
            for (int i = 1; i < updates.length; i++) {
                String update = updates[i];
                long updateId = Long.parseLong(update.split(",")[0].trim());
                lastUpdateId = Math.max(lastUpdateId, updateId);

                if (update.contains("\"text\":") && update.contains("\"chat\":{\"id\":" + chatId)) {
                    String text = update.split("\"text\":\"")[1].split("\"")[0];
                    processTelegramCommand(text);
                }
            }
        } catch (Exception e) {
            getLogger().warning("Ошибка обработки обновления Telegram: " + e.getMessage());
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
            } else if (command.toLowerCase().startsWith("/cmd ")) {
                // Выполнение консольной команды через Telegram
                long userId = getTelegramUserIdFromUpdate(); // Реализуйте этот метод для получения userId
                if (isAllowedTelegramUser(userId)) {
                    String cmd = command.substring(5).trim();
                    handleConsoleCommand(cmd);
                } else {
                    sendTelegramMessage("🔒 Доступ запрещен. Ваш ID: " + userId);
                    getLogger().warning("[Telegram] Попытка выполнения команды без прав: " + command);
                }
            }
            else if (command.toLowerCase().startsWith("/"))  {
                getLogger().warning("[Telegram] Неизвестная команда: " + command);
                sendTelegramMessage("❌ Неизвестная команда. Доступные команды:\n" +
                        "/online - список игроков онлайн\n" +
                        "/player <ник> - информация об игроке\n" +
                        "/help - справка по командам\n" +
                        "/cmd <команда> - выполнить консольную команду");
            }
        } catch (Exception e) {
            getLogger().severe("[Telegram] Ошибка обработки команды: " + e.getMessage());
            sendTelegramMessage("⚠ Произошла ошибка при обработке команды. Попробуйте позже.");
        }
    }

    // Выполнение консольной команды и отправка результата в Telegram
    private void handleConsoleCommand(String cmd) {
        Bukkit.getScheduler().runTask(this, () -> {
            try {
                // Выполнение команды строго от имени консоли сервера
                CustomCommandSender customSender = new CustomCommandSender();
                boolean success = Bukkit.dispatchCommand(new CommandWrapper(Bukkit.getConsoleSender(), customSender), cmd);
                String result = customSender.getOutput();
                if (!success) {
                    sendTelegramMessage("❌ Команда не поддерживается или синтаксис неверный. Попробуйте явно указать имя игрока или используйте команду, поддерживаемую консолью.\nПример: /cmd gamemode survival ИмяИгрока");
                } else if (result.isEmpty()) {
                    sendTelegramMessage("✅ Команда выполнена (нет вывода): " + cmd);
                } else {
                    sendTelegramMessage("Результат команды:\n" + result);
                }
            } catch (Exception e) {
                sendTelegramMessage("Ошибка при выполнении команды: " + e.getMessage() +
                        "\nВозможно, команда не поддерживается консолью или синтаксис неверный. Попробуйте явно указать имя игрока.");
            }
        });
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
                "/help - Эта справка\n" +
                "/cmd <команда> - Выполнить консольную команду на сервере (только для разрешённых пользователей)\n" +
                "/tgbot <on|off|status|reload> - Управление ботом (только на сервере)\n\n" +
                "⚙ Бот работает с Minecraft сервером " + Bukkit.getServer().getName();

        sendTelegramMessage(helpText);
    }

    // Класс-обертка для комбинирования двух CommandSender
    private static class CommandWrapper implements CommandSender {
        private final CommandSender primary;
        private final CommandSender secondary;

        public CommandWrapper(CommandSender primary, CommandSender secondary) {
            this.primary = primary;
            this.secondary = secondary;
        }

        @Override
        public void sendMessage(String message) {
            primary.sendMessage(message);
            secondary.sendMessage(message);
        }

        @Override
        public void sendMessage(String[] messages) {
            primary.sendMessage(messages);
            secondary.sendMessage(messages);
        }

        @Override
        public void sendMessage(java.util.UUID sender, String message) {
            primary.sendMessage(sender, message);
            secondary.sendMessage(sender, message);
        }

        @Override
        public void sendMessage(java.util.UUID sender, String[] messages) {
            primary.sendMessage(sender, messages);
            secondary.sendMessage(sender, messages);
        }

        // Реализация остальных методов CommandSender, делегирующая primary sender
        @Override
        public Server getServer() {
            return primary.getServer();
        }

        @Override
        public String getName() {
            return primary.getName();
        }

        @Override
        public boolean isOp() {
            return primary.isOp();
        }

        @Override
        public void setOp(boolean value) {
            primary.setOp(value);
        }

        @Override
        public Spigot spigot() {
            return primary.spigot();
        }

        @Override
        public boolean isPermissionSet(String name) {
            return primary.isPermissionSet(name);
        }

        @Override
        public boolean isPermissionSet(Permission perm) {
            return primary.isPermissionSet(perm);
        }

        @Override
        public boolean hasPermission(String name) {
            return primary.hasPermission(name);
        }

        @Override
        public boolean hasPermission(Permission perm) {
            return primary.hasPermission(perm);
        }

        @Override
        public PermissionAttachment addAttachment(Plugin plugin, String name, boolean value) {
            return primary.addAttachment(plugin, name, value);
        }

        @Override
        public PermissionAttachment addAttachment(Plugin plugin) {
            return primary.addAttachment(plugin);
        }

        @Override
        public PermissionAttachment addAttachment(Plugin plugin, String name, boolean value, int ticks) {
            return primary.addAttachment(plugin, name, value, ticks);
        }

        @Override
        public PermissionAttachment addAttachment(Plugin plugin, int ticks) {
            return primary.addAttachment(plugin, ticks);
        }

        @Override
        public void removeAttachment(PermissionAttachment attachment) {
            primary.removeAttachment(attachment);
        }

        @Override
        public void recalculatePermissions() {
            primary.recalculatePermissions();
        }

        @Override
        public Set<PermissionAttachmentInfo> getEffectivePermissions() {
            return primary.getEffectivePermissions();
        }
        // ... остальные методы CommandSender аналогично
    }

    // Внутренний класс для кастомного CommandSender
    private class CustomCommandSender implements CommandSender {
        private final StringBuilder output = new StringBuilder();

        @Override
        public void sendMessage(String message) {
            output.append(message).append("\n");
        }

        @Override
        public void sendMessage(String[] messages) {
            for (String msg : messages) {
                output.append(msg).append("\n");
            }
        }

        @Override
        public void sendMessage(UUID sender, String message) {
            output.append(message).append("\n");
        }

        @Override
        public void sendMessage(UUID sender, String[] messages) {
            for (String msg : messages) {
                output.append(msg).append("\n");
            }
        }

        @Override
        public Server getServer() {
            return Bukkit.getServer();
        }

        @Override
        public String getName() {
            return "TelegramBot";
        }

        @Override
        public boolean isOp() {
            return true;
        }

        @Override
        public void setOp(boolean value) {
            // Не требуется
        }

        @Override
        public boolean isPermissionSet(String name) {
            return true;
        }

        @Override
        public boolean isPermissionSet(Permission perm) {
            return true;
        }

        @Override
        public boolean hasPermission(String name) {
            return true;
        }

        @Override
        public boolean hasPermission(Permission perm) {
            return true;
        }

        @Override
        public PermissionAttachment addAttachment(Plugin plugin, String name, boolean value) {
            return null;
        }

        @Override
        public PermissionAttachment addAttachment(Plugin plugin) {
            return null;
        }

        @Override
        public PermissionAttachment addAttachment(Plugin plugin, String name, boolean value, int ticks) {
            return null;
        }

        @Override
        public PermissionAttachment addAttachment(Plugin plugin, int ticks) {
            return null;
        }

        @Override
        public void removeAttachment(PermissionAttachment attachment) {
        }

        @Override
        public void recalculatePermissions() {
        }

        @Override
        public Set<PermissionAttachmentInfo> getEffectivePermissions() {
            return new HashSet<>();
        }

        @Override
        public Spigot spigot() {
            return new Spigot();
        }

        public String getOutput() {
            return output.toString().trim();
        }
    }

    private boolean isAllowedTelegramUser(long userId) {
        return allowedTelegramIds.contains(userId);
    }

    private long getTelegramUserIdFromUpdate() {
        if (lastUpdateJson == null) {
            getLogger().warning("lastUpdateJson is null - невозможно получить ID пользователя");
            return -1;
        }

        try {
            // Ищем последнее сообщение в JSON
            int lastMessageIndex = lastUpdateJson.lastIndexOf("\"message\":");
            if (lastMessageIndex == -1) return -1;

            // Ищем блок "from" внутри последнего сообщения
            int fromIndex = lastUpdateJson.indexOf("\"from\":{", lastMessageIndex);
            if (fromIndex == -1) return -1;

            // Извлекаем ID пользователя
            int idStart = lastUpdateJson.indexOf("\"id\":", fromIndex) + 5;
            if (idStart < 5) return -1;

            int idEnd = lastUpdateJson.indexOf(",", idStart);
            if (idEnd == -1) idEnd = lastUpdateJson.indexOf("}", idStart);
            if (idEnd == -1) return -1;

            String idStr = lastUpdateJson.substring(idStart, idEnd).trim();
            return Long.parseLong(idStr);
        } catch (Exception e) {
            getLogger().warning("Ошибка получения Telegram ID: " + e.getMessage());
            return -1;
        }
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

    // Отправка сообщения в Telegram
    private void sendTelegramMessage(String message) {
        try {
            URL url = new URL("https://api.telegram.org/bot" + botToken + "/sendMessage");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            String data = "chat_id=" + chatId + "&text=" + java.net.URLEncoder.encode(message, "UTF-8");
            try (OutputStream os = conn.getOutputStream()) {
                os.write(data.getBytes(StandardCharsets.UTF_8));
            }
            conn.getInputStream().close();
        } catch (Exception e) {
            getLogger().warning("Ошибка отправки сообщения в Telegram: " + e.getMessage());
        }
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
