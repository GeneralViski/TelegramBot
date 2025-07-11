# TelegramLink Minecraft Plugin

## Описание

TelegramLink — это плагин для Minecraft (Bukkit/Spigot/Paper), который позволяет интегрировать ваш сервер Minecraft с Telegram-каналом или чатом. Плагин отправляет сообщения о событиях сервера в Telegram и позволяет выполнять серверные команды через Telegram (только для разрешённых пользователей).

## Возможности

- Отправка сообщений о чате игроков и командах в Telegram.
- Получение информации об игроках и списке онлайн через Telegram.
- Выполнение серверных команд Minecraft через Telegram (команда `/cmd`, только для разрешённых Telegram ID).
- Управление ботом через команду `/tgbot` на сервере.

## Команды Telegram

- `/online` — Показать онлайн игроков на сервере.
- `/player <ник>` — Получить информацию об игроке (онлайн/оффлайн).
- `/help` — Справка по всем доступным командам.
- `/cmd <команда>` — Выполнить серверную команду Minecraft (только для разрешённых пользователей, команды выполняются от имени консоли сервера).
- `/tgbot <on|off|status|reload>` — Управление ботом (только для админов, используется в Minecraft-чате).

## Установка

1. Скачайте jar-файл плагина и поместите его в папку `plugins` вашего сервера Minecraft.
2. Перезапустите сервер.
3. Откройте файл `config.yml` в папке плагина и укажите:
    - `telegram.bot-token` — токен вашего Telegram-бота
    - `telegram.chat-id` — ID чата или канала
    - `telegram.allowed-ids` — список Telegram ID, которым разрешено выполнять команды через `/cmd`
4. Перезапустите сервер или используйте `/tgbot reload` для применения настроек.

## Пример config.yml

```yaml
telegram:
  enabled: true
  bot-token: "ВАШ_ТОКЕН_БОТА"
  chat-id: "ВАШ_CHAT_ID"
  allowed-ids:
    - 123456789
    - 987654321
  message-format: "[Minecraft] {player}: {message}"
  command-format: "[Minecraft] Игрок {player} использовал команду: {command}"
  denied-command-format: "[Minecraft] Игрок {player} попытался использовать команду без прав: {command}"
  player-info-format: |
    📊 Информация об игроке {player}:
    🕒 Дата регистрации: {first-played}
    ⏱ Общее время игры: {play-time}
    📍 Последний вход: {last-played}
    🏠 Мир: {world}
```

## Требования

- Minecraft сервер на базе Bukkit/Spigot/Paper
- Java 8+
- Telegram-бот (создайте через @BotFather)

## Безопасность

- Только Telegram ID, указанные в `allowed-ids`, могут выполнять команды через `/cmd`.
- Все команды выполняются от имени консоли сервера Minecraft.

## Лицензия

MIT License

---

**Внимание:** Будьте осторожны с выдачей доступа к команде `/cmd`, так как она даёт полный контроль над сервером через Telegram!

