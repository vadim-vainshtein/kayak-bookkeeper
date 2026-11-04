# Kayak Bookkeeper

Веб-приложение для учёта тренировок, абонементов, начислений и оплат каяк-клуба. Все суммы хранятся целыми тенге.

## Production-деплой с Caddy

Нужны Docker Engine с Compose plugin, публичный DNS-адрес сервера и открытые входящие порты **80** и **443**. Никакие порты приложения или PostgreSQL наружу не публикуются: HTTPS и сертификат обслуживает Caddy.

```bash
git clone https://github.com/<owner>/kayak-bookkeeper.git
cd kayak-bookkeeper
cp .env.example .env
chmod 600 .env
# Отредактируйте .env: укажите настоящий APP_DOMAIN, ACME_EMAIL и два случайных секрета.
docker compose --env-file .env -f compose.production.yml up -d --build
docker compose --env-file .env -f compose.production.yml ps
```

Для `DB_PASSWORD` и `APP_INITIAL_ADMIN_PASSWORD` используйте разные значения длиной не менее 12 символов; например, сгенерируйте их командой `openssl rand -base64 32`. В `.env.example` эти поля намеренно пустые: Compose откажется запускаться, пока оба секрета не заданы. Файл `.env` **не коммитится** и не должен попадать в резервные копии, тикеты или логи.

После статуса `healthy` откройте `https://<APP_DOMAIN>/`. Логин первого администратора — `admin`, а пароль — значение `APP_INITIAL_ADMIN_PASSWORD`. Сразу после первого входа смените пароль в разделе «Пароль» и храните первоначальный секрет как аварийный до подтверждения смены.

`APP_INITIAL_ADMIN_PASSWORD` нужен только когда в базе ещё нет `admin`; на последующих запусках существующая учётная запись не перезаписывается. При пустой базе без этого секрета приложение завершится с ошибкой, а не создаст известный пароль.

### Проверка после запуска

```bash
docker compose --env-file .env -f compose.production.yml ps
docker compose --env-file .env -f compose.production.yml logs --tail=100 app
curl -I https://<APP_DOMAIN>/
```

Проверьте вход с первоначальным паролем, создание тестового каякера и отсутствие внешнего доступа к 5432/8080. Swagger и OpenAPI доступны только аутентифицированному администратору: `/swagger-ui/index.html` и `/v3/api-docs`.

## Production-деплой за существующим Nginx

Этот вариант предназначен для **Nginx, запущенного непосредственно на хосте**. Не запускайте одновременно Caddy из `compose.production.yml` и существующий Nginx: оба процесса занимают порты 80 и 443. В этой схеме Nginx остаётся единственной публичной точкой входа и обслуживает TLS-сертификат, а приложение доступно только локально на `127.0.0.1:8081`.

Для первого запуска используйте отдельный поддомен, например `bookkeeper.example.com`. Это не затрагивает существующее приложение на основном домене. Установка в путь вида `example.com/bookkeeper/` требует дополнительной настройки base path у SPA и не рекомендуется для первого production-развёртывания.

### Предварительные условия

- Docker Engine и Compose plugin установлены на сервере.
- DNS-запись выбранного поддомена указывает на IP сервера.
- В Nginx уже подключён действующий TLS-сертификат для поддомена либо настроено его получение и обновление (например, Certbot).
- В firewall открыты только 80 и 443 для публичного доступа. Порт 8081, PostgreSQL и Docker socket не должны быть доступны извне.
- Приведённая ниже конфигурация предполагает, что Nginx работает на хосте, а не в Docker-контейнере.

### 1. Подготовить каталог и секреты

```bash
git clone https://github.com/vadim-vainshtein/kayak-bookkeeper.git
cd kayak-bookkeeper
cp .env.example .env
chmod 600 .env
```

В `.env` задайте фактические `APP_DOMAIN`, `ACME_EMAIL`, `DB_PASSWORD` и `APP_INITIAL_ADMIN_PASSWORD`. Хотя Caddy в этом варианте не запускается, `APP_DOMAIN` и `ACME_EMAIL` всё равно должны быть заполнены: Compose проверяет обязательные переменные при чтении базового файла. Для двух паролей используйте разные случайные значения длиной не менее 12 символов:

```bash
openssl rand -base64 32
```

### 2. Создать локальный Compose override

Создайте на сервере файл `compose.nginx.yml`. Его не нужно коммитить в репозиторий: это host-specific конфигурация.

```yaml
services:
  app:
    ports:
      - "127.0.0.1:8081:8080"

  # Переводит Caddy в неактивный профиль. Без --profile caddy он не запустится.
  caddy:
    profiles: ["caddy"]
```

Проверьте итоговую конфигурацию до запуска. В выводе у `app` должен быть только `127.0.0.1:8081:8080`; у `postgres` не должно быть секции `ports`.

```bash
docker compose --env-file .env -f compose.production.yml -f compose.nginx.yml config
```

### 3. Настроить Nginx

Создайте virtual host, подставив домен и пути к уже выпущенному сертификату. Для Certbot типичные пути — `/etc/letsencrypt/live/<домен>/fullchain.pem` и `privkey.pem`.

```nginx
server {
    listen 80;
    listen [::]:80;
    server_name bookkeeper.example.com;
    return 301 https://$host$request_uri;
}

server {
    listen 443 ssl http2;
    listen [::]:443 ssl http2;
    server_name bookkeeper.example.com;

    ssl_certificate     /etc/letsencrypt/live/bookkeeper.example.com/fullchain.pem;
    ssl_certificate_key /etc/letsencrypt/live/bookkeeper.example.com/privkey.pem;
    ssl_protocols TLSv1.2 TLSv1.3;

    location / {
        proxy_pass http://127.0.0.1:8081;
        proxy_http_version 1.1;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto https;
    }
}
```

Например, сохраните файл как `/etc/nginx/sites-available/kayak-bookkeeper`, включите его по правилам вашей ОС и проверьте конфигурацию:

```bash
sudo nginx -t
sudo systemctl reload nginx
```

Не добавляйте Basic Auth в Nginx поверх приложения без отдельной необходимости: приложение уже использует собственную HTTP Basic-аутентификацию для API. Главное требование — Nginx должен принудительно перенаправлять HTTP на HTTPS и передавать приведённые proxy-заголовки.

### 4. Запустить и проверить приложение

```bash
docker compose --env-file .env -f compose.production.yml -f compose.nginx.yml up -d --build
docker compose --env-file .env -f compose.production.yml -f compose.nginx.yml ps
docker compose --env-file .env -f compose.production.yml -f compose.nginx.yml logs --tail=100 app

# С самого сервера: контейнер слушает только loopback.
curl -I http://127.0.0.1:8081/

# С внешней машины: проверяет DNS, TLS и Nginx.
curl -I https://bookkeeper.example.com/
```

После статуса `healthy` откройте `https://bookkeeper.example.com/`, войдите как `admin` с `APP_INITIAL_ADMIN_PASSWORD` и сразу смените первоначальный пароль. Проверьте, что `curl http://<публичный-IP>:8081/` и подключения к 5432 извне недоступны.

### Обновление и откат за Nginx

Перед обновлением создайте и проверьте резервную копию. Во всех командах используйте оба Compose-файла, иначе Caddy снова будет включён.

```bash
git fetch --tags origin
git checkout <проверенный-тег-или-коммит>
docker compose --env-file .env -f compose.production.yml -f compose.nginx.yml up -d --build
docker compose --env-file .env -f compose.production.yml -f compose.nginx.yml ps
docker compose --env-file .env -f compose.production.yml -f compose.nginx.yml logs --tail=100 app
```

Для отката переключитесь на предыдущий проверенный тег и повторите `up -d --build`. Не откатывайте Flyway-миграции вручную: если схема или данные несовместимы, остановите приложение, восстановите проверенную копию базы в отдельную БД и действуйте по процедуре восстановления ниже.

## Обновление и откат

Перед каждым обновлением сначала сделайте и проверьте резервную копию, затем сохраните используемый SHA образа/коммита.

```bash
# Обновление
git fetch --tags origin
git checkout v1.0.0                 # замените на проверенный тег
docker compose --env-file .env -f compose.production.yml up -d --build

# Проверка статуса и журналов
docker compose --env-file .env -f compose.production.yml ps
docker compose --env-file .env -f compose.production.yml logs --tail=100 app
```

Откат приложения допустим только после проверки совместимости схемы с предыдущей версией. Flyway-миграции являются прямыми: не откатывайте их вручную на рабочей базе. Если релиз повредил данные, остановите сервис, восстановите проверенный backup в чистую базу и разверните предыдущий проверенный тег. Сначала отрепетируйте этот сценарий на копии базы.

## Резервное копирование и восстановление

Создавайте резервную копию PostgreSQL в custom-format archive с хоста, на котором доступен Docker:

```bash
mkdir -p /secure/backups
docker compose --env-file .env -f compose.production.yml exec -T postgres \
  pg_dump -U kayak -d kayak_bookkeeper --format=custom \
  > /secure/backups/kayak_bookkeeper-$(date +%F-%H%M%S).dump
```

Храните backup вне репозитория, с ограниченным доступом и шифрованием на уровне хранилища. Регулярно проверяйте восстановление на отдельной БД:

```bash
docker compose --env-file .env -f compose.production.yml exec -T postgres \
  createdb -U kayak kayak_bookkeeper_restore_check
cat /secure/backups/<backup>.dump | docker compose --env-file .env -f compose.production.yml exec -T postgres \
  pg_restore -U kayak -d kayak_bookkeeper_restore_check --clean --if-exists
```

Перед релизом с Flyway-миграциями восстановите production-копию и прогоните новую версию именно на PostgreSQL. Сверьте число людей, абонементов, начислений, платежей и финансовые итоги до и после миграции.

## Локальная разработка

Локальная конфигурация намеренно отделена от production и использует профиль `local`:

```powershell
docker compose up -d
mvn spring-boot:run "-Dspring-boot.run.profiles=local"

cd frontend
npm ci
npm run dev
```

Локальный UI Vite доступен на `http://localhost:5173`; API — на `http://localhost:8080`. Первый локальный пароль по умолчанию — `local-development-password`; для иной локальной учётной записи задайте `INITIAL_ADMIN_PASSWORD` до первого запуска. Эти параметры предназначены только для локальной разработки.

## Сборка и проверки

```bash
mvn -B -ntp verify
cd frontend && npm ci && npm test && npm run build && npm audit --omit=dev --audit-level=high
```

`mvn verify` устанавливает зафиксированные frontend-зависимости, собирает UI, запускает Java- и frontend-тесты и создаёт исполняемый `target/kayak-bookkeeper-1.0.0.jar`. Frontend-тесты также можно запускать отдельно командой `npm test` из каталога `frontend`. Не запускайте `clean` или `package` в каталоге работающего приложения: собирайте релиз в CI либо в отдельной копии рабочего дерева. GitHub Actions выполняет ту же проверку для pull request, `master` и тегов `v*`.

## Структура приложения

### Пользователи и роли

Администратор создаёт учётные записи в разделе **«Пользователи»**: указывает уникальный
логин без пробелов и двоеточий, пароль (от 8 символов, не более 72 байт UTF-8) и роль.
По умолчанию выбрана роль **«Член клуба · только чтение»** (`READ_ONLY`). Можно также
создать другого администратора (`ADMIN`). Пароли хранятся только в виде хешей и не
отображаются в списке пользователей.

Член клуба может просматривать данные клуба, включая финансовые отчёты и их CSV-экспорт,
посещаемость, начисления и оплаты. Контакты и личные заметки каякеров доступны только
администратору: для `READ_ONLY` API возвращает `null` в полях `phone` и `note`,
а интерфейс скрывает эти данные и поиск по телефону. Любые изменения запрещены на уровне
API; формы редактирования и управление пользователями ему недоступны. В соответствии
с правилом «никаких записей» самостоятельная смена пароля для `READ_ONLY` тоже запрещена.
Миграция сохраняет роль `ADMIN` у всех существующих учётных записей.

API: `GET /api/account` возвращает текущую учётную запись и её роль;
`GET /api/users` и `POST /api/users` доступны только администратору. Запрос создания:
`{"login":"club-member","password":"<пароль>","role":"READ_ONLY"}`.
В ответах возвращаются только `id`, `login`, `role`; повторный логин даёт HTTP 409,
некорректные данные — HTTP 400, запрещённая операция — HTTP 403.

Backend разделён по операциям: контроллеры в `api` принимают HTTP-запросы,
а сервисы в `service` отвечают за каякеров, тарифы, абонементы, посещаемость,
начисления, оплаты, балансы и финансовые отчёты. Расчёт стоимости абонемента,
согласование начислений при редактировании и преобразование сущностей в ответы
выделены отдельно. Транзакции находятся на сервисных операциях; репозитории
сохраняют блокировки, необходимые при распределении платежей.

В `frontend/src/features` каждый раздел содержит свои экраны, формы и действия
API. Общие компоненты находятся в `components`, загрузка данных и координация
запросов — в `hooks`, подписи и форматирование — в `shared`. `App.jsx` связывает
разделы и навигацию. Тесты проверяют контракты API, бизнес-правила и поведение
извлечённых frontend-модулей. План рефакторинга: [REFACTORING.md](REFACTORING.md).

## Операционные ограничения

- Caddy получает и обновляет TLS-сертификаты автоматически. DNS должен указывать на сервер до первого запуска.
- Не публикуйте 8080, 5432 и Docker socket; разрешите в firewall только 80/443 и администрирование по защищённому каналу.
- PostgreSQL-том и Caddy-сертификаты находятся в Docker volumes. Не удаляйте volumes командой `docker compose down -v` без подтверждённой резервной копии.
- Production-конфигурация не имеет запасных логинов, паролей или адреса БД. Ошибка отсутствующего секрета должна останавливать запуск.
- Внешние ошибки не возвращают внутренние сообщения. Операционные причины ищите в ограниченных логах контейнера.
