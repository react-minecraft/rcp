# React RCP

Модели React (Flower Baseline, Flower Ultra, Vector) и подключение сервера к [react-ac.space](https://www.react-ac.space).
Плагин React скачивает RCP сам в `plugins/React/.rcp/providers/` и проверяет подпись `checksums.txt`.

## Привязка сервера

| Команда             | Что делает                                                                 |
|---------------------|----------------------------------------------------------------------------|
| `/react link`       | Печатает ссылку и код; откройте ссылку, войдите и подтвердите сервер       |
| `/react link <код>` | Привязывает по коду со страницы сервера на сайте (код начинается с `rx_`) |
| `/react link check` | Показывает, к какому серверу привязан ключ и какие модели включены         |

Ключ хранится в `plugins/React/.rcp/React/.link` в зашифрованном виде. Он привязан к этой установке
(UUID основного мира, порт и machine-id), поэтому скопированный на другую машину `.link` не откроется,
и сервер нужно привязать заново. Ключ из старого `plugins/React/inference.yml` переносится в `.link` при первом запуске.

## Настройки

Всё лежит в `plugins/React/.rcp/React/` и перечитывается по `/react reload`.

| Файл                | Ключ                                   | По умолчанию | Описание                                       |
|---------------------|----------------------------------------|--------------|------------------------------------------------|
| `connection.yml`    | `timeouts.connect`                     | `10s`        | Время на установку соединения с сайтом         |
| `connection.yml`    | `timeouts.call`                        | `10s`        | Время на ответ модели                          |
| `models/flower.yml` | `step`                                 | `10`         | Через сколько поворотов отправлять окно Flower |
| `models/vector.yml` | `combat-ticks`                         | `64`         | Сколько тиков после удара игрок считается в бою |
| `actions.yml`       | `link.*`                               |              | Сообщения и звуки команды `/react link`        |

В `actions.yml` работают плейсхолдеры `%url%`, `%code%`, `%minutes%`, `%server%`, `%models%`, `%reason%`.

## Сборка

```bash
./gradlew build
```

Jar появится в `build/libs/`. Для проверки на своём сервере положите его в `plugins/React/.rcp/providers/`
и выключите `settings.auto-update` в `plugins/React/.rcp/react-rcp.yml`.

## Свой RCP

Шаблон с пояснениями по API лежит в [example-rcp](https://github.com/react-minecraft/example-rcp).
