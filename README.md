<div align="center">

<a href="https://git.io/typing-svg"><img src="https://readme-typing-svg.demolab.com?font=Manrope&weight=700&size=38&pause=1000&color=EB2544&background=FFFFFF00&center=true&vCenter=true&width=700&lines=React+RCP;Models+for+React+AntiCheat" alt="React RCP Typing SVG"/></a>

<p>Модели React и подключение сервера к сайту</p>

<p>
  <a href="https://github.com/react-minecraft/rcp/actions/workflows/build.yml">
    <img src="https://img.shields.io/github/actions/workflow/status/react-minecraft/rcp/build.yml?style=plastic&label=build&logo=github&logoColor=white" alt="Build">
  </a>
  <a href="https://github.com/react-minecraft/rcp/releases/tag/nightly">
    <img src="https://img.shields.io/badge/Nightly-EB2544?style=plastic&logo=github&logoColor=white" alt="Nightly">
  </a>
  <a href="https://github.com/react-minecraft/rcp/blob/main/LICENSE">
    <img src="https://img.shields.io/github/license/react-minecraft/rcp?style=plastic&logo=opensourceinitiative&logoColor=white" alt="License">
  </a>
</p>

<p>
  <a href="https://react-ac.space"><img src="https://img.shields.io/badge/Website-EB2544?style=plastic&logo=googlechrome&logoColor=white" alt="Website"/></a>
  <a href="https://t.me/react_ac"><img src="https://img.shields.io/badge/Telegram-26A5E4?style=plastic&logo=telegram&logoColor=white" alt="Telegram"/></a>
  <a href="https://t.me/react_ac_support"><img src="https://img.shields.io/badge/Support-555555?style=plastic&logo=telegram&logoColor=white" alt="Support"/></a>
</p>

</div>

---

## Что это?

- Тут живут модели React: **Flower Baseline**, **Flower Ultra** и **Vector Preview**, а ещё подключение сервера к
  [react-ac.space](https://www.react-ac.space).
- Плагин сам скачивает подписанный стабильный релиз RCP в `plugins/React/.rcp/providers/`, когда он доступен под API 2.
- Для проверки nightly возьмите `react-inference-rcp-*.jar` из [GitHub Nightly](https://github.com/react-minecraft/rcp/releases/tag/nightly), положите его в эту папку и выполните `/react reboot rcp`.

---

## Привязка сервера

| Команда             | Что делает                                                                |
|---------------------|---------------------------------------------------------------------------|
| `/react link`       | Печатает ссылку и код; открываете ссылку, входите и подтверждаете сервер  |
| `/react link <код>` | Привязывает по коду со страницы сервера на сайте (код начинается с `rx_`) |
| `/react link check` | Показывает, к какому серверу привязан ключ и какие модели включены        |

> [!IMPORTANT]
> При привязке сервер получает свой ключ ed25519, им подписывается каждый запрос к сайту. Ключ лежит в
> `plugins/React/.rcp/React/.link` в зашифрованном виде и привязан к этой установке (UUID основного мира, порт
> и machine-id). Скопировали `.link` на другую машину - он не откроется, просто привяжите сервер заново.

---

## Настройки

Всё лежит в `plugins/React/.rcp/React/` и перечитывается по `/react reload`.

| Файл                | Ключ               | По умолчанию | Описание                                        |
|---------------------|--------------------|--------------|-------------------------------------------------|
| `connection.yml`    | `timeouts.connect` | `10s`        | Время на установку соединения с сайтом          |
| `connection.yml`    | `timeouts.call`    | `10s`        | Время на ответ модели                           |
| `models/flower.yml` | `step`             | `10`         | Через сколько поворотов отправлять окно Flower  |
| `models/vector.yml` | `combat-ticks`     | `64`         | Сколько тиков после удара игрок считается в бою |
| `actions.yml`       | `link.*`           |              | Сообщения и звуки команды `/react link`         |

В `actions.yml` работают плейсхолдеры `{url}`, `{code}`, `{minutes}`, `{server}`, `{models}`, `{reason}`.

---

## Сборка исходников

Для сборки нужен JDK 21.

1. `git clone https://github.com/react-minecraft/rcp.git`
2. `cd rcp`
3. `./gradlew build`
4. Jar будет лежать в `build/libs/`

Чтобы проверить свою сборку на сервере, положите jar в `plugins/React/.rcp/providers/` и выключите
`enabled` в `plugins/React/update/rcp/React.yml`, чтобы плагин не подготовил обновление на замену вашей сборки.

---

## Свой RCP

Шаблон с пояснениями лежит в [example-rcp](https://github.com/react-minecraft/example-rcp).

---

## Лицензия

RCP распространяется под MIT — см. [LICENSE](LICENSE).
