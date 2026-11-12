<div align="center">

<a href="https://git.io/typing-svg"><img src="https://readme-typing-svg.demolab.com?font=Manrope&weight=700&size=38&pause=1000&color=EB2544&background=FFFFFF00&center=true&vCenter=true&width=700&lines=React+RCP;Models+for+React+AntiCheat" alt="React RCP"/></a>

<p>Модели React и подключение сервера к сервису</p>

<p>
  <a href="https://react-ac.space"><img src="https://img.shields.io/badge/Website-EB2544?style=plastic&logo=googlechrome&logoColor=white" alt="Website"/></a>
  <a href="https://t.me/react_ac"><img src="https://img.shields.io/badge/Telegram-26A5E4?style=plastic&logo=telegram&logoColor=white" alt="Telegram"/></a>
  <a href="https://t.me/react_ac_support"><img src="https://img.shields.io/badge/Support-555555?style=plastic&logo=telegram&logoColor=white" alt="Support"/></a>
</p>

<p>
  <a href="https://github.com/react-minecraft/rcp/actions/workflows/build.yml"><img src="https://img.shields.io/github/actions/workflow/status/react-minecraft/rcp/build.yml?style=plastic&logo=github&logoColor=white&branch=main&label=build" alt="Build"/></a>
  <a href="https://github.com/react-minecraft/rcp/releases/tag/nightly"><img src="https://img.shields.io/badge/Nightly-EB2544?style=plastic&logo=github&logoColor=white" alt="Nightly"/></a>
  <a href="https://github.com/react-minecraft/rcp/blob/main/LICENSE"><img src="https://img.shields.io/github/license/react-minecraft/rcp?style=plastic&logo=opensourceinitiative&logoColor=white" alt="License"/></a>
</p>

</div>

## Что делает RCP

RCP получает от React данные о поворотах игроков в бою, отправляет запросы к моделям сервиса и возвращает результаты плагину. Сервис предоставляет **Flower Baseline**, **Flower Ultra** и **Vector Preview**. Доступные для сервера модели показывает `/react link check`.

## Установка

React устанавливает официальный RCP из подписанного стабильного релиза, когда он доступен для API 2. Для ручной установки возьмите `react-inference-rcp-*.jar` из [nightly](https://github.com/react-minecraft/rcp/releases/tag/nightly), положите в `plugins/React/.rcp/providers/` и выполните `/react reboot rcp` или перезапустите сервер. В папке должен остаться один JAR с именем RCP `React` в метаданных.

Канал и проверка обновлений настраиваются в `plugins/React/update/rcp/React.yml`. При проверке локальной сборки поставьте `enabled: false` в этом файле, чтобы React не подготовил обновление на её замену. За загрузку RCP отвечает отдельный ключ `settings.enabled` в `plugins/React/.rcp/react-rcp.yml`.

## Привязка сервера

Командам привязки нужно право `react.command.link`.

| Команда | Действие |
|---|---|
| `/react link` | Получить ссылку, войти на сайте и подтвердить привязку |
| `/react link <код>` | Привязать сервер по коду со страницы сервера |
| `/react link check` | Проверить привязку и доступные модели |

RCP хранит ключ привязки в `plugins/React/.rcp/React/.link` и подписывает им запросы. При переносе установки заново привяжите сервер, если сохранённый ключ больше не открывается.

## Настройки

Файлы лежат в `plugins/React/.rcp/React/` и перечитываются по `/react reload`.

| Файл | Ключ | По умолчанию |
|---|---|---|
| `connection.yml` | `timeouts.connect`, `timeouts.call` | `10s` для подключения и `10s` для одного запроса |
| `models/flower.yml` | `step` | `10` новых поворотов между запросами Flower |
| `models/vector.yml` | `combat-ticks` | `64` тика после удара |
| `actions.yml` | `link.*` | Сообщения, звуки и плейсхолдеры команд привязки |


## Сборка

Для сборки используйте JDK 21. Выполните `./gradlew build` или `.\gradlew.bat build` на Windows.

Готовый `react-inference-rcp-*.jar` лежит в `build/libs/`. Свой RCP можно начать с [example-rcp](https://github.com/react-minecraft/example-rcp).


## Лицензия

[MIT](LICENSE).
