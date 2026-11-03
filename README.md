# Kayak Bookkeeper

Веб-приложение для учёта тренировок, абонементов, начислений и оплат каяк-клуба. Все суммы хранятся целыми тенге.

## Быстрый production-деплой

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
cd frontend && npm ci && npm run build && npm audit --omit=dev --audit-level=high
```

`mvn verify` устанавливает зафиксированные frontend-зависимости, собирает UI, запускает тесты и создаёт исполняемый `target/kayak-bookkeeper-1.0.0.jar`. Не запускайте `clean` или `package` в каталоге работающего приложения: собирайте релиз в CI либо в отдельной копии рабочего дерева. GitHub Actions выполняет ту же проверку для pull request, `master` и тегов `v*`.

## Операционные ограничения

- Caddy получает и обновляет TLS-сертификаты автоматически. DNS должен указывать на сервер до первого запуска.
- Не публикуйте 8080, 5432 и Docker socket; разрешите в firewall только 80/443 и администрирование по защищённому каналу.
- PostgreSQL-том и Caddy-сертификаты находятся в Docker volumes. Не удаляйте volumes командой `docker compose down -v` без подтверждённой резервной копии.
- Production-конфигурация не имеет запасных логинов, паролей или адреса БД. Ошибка отсутствующего секрета должна останавливать запуск.
- Внешние ошибки не возвращают внутренние сообщения. Операционные причины ищите в ограниченных логах контейнера.
