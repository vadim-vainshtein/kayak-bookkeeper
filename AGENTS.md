# Kayak Bookkeeper: локальная сборка и запуск

Эти инструкции относятся к `F:\dev\kayak-bookkeeper`. Соблюдайте глобальные
инструкции пользователя; сохраняйте чужие незакоммиченные изменения. Для изменений
кода используйте `developer-review-pipeline`: `task_developer`, затем независимый
`task_reviewer`, исправления и повторное ревью при необходимости.

Запрос пользователя запустить или перезапустить **локальное** приложение разрешает
необходимый запуск его PostgreSQL, резервное копирование, обычные Flyway-миграции
и перезапуск принадлежащего этому проекту процесса. Не запрашивайте повторное
разрешение на эти шаги. Push, merge, deploy, внешние записи и другие действия за
пределами локального запроса остаются под глобальными ограничениями.

## Проверенный инструментарий

- PowerShell; рабочий каталог `F:\dev\kayak-bookkeeper`.
- Java 21: `C:\Users\dima\.jdks\corretto-21.0.4`.
- Maven: `C:\apache-maven-3.9.16\bin\mvn.cmd`.
- Установленный проектом Node: `target\node\node.exe`.
- Spring Boot / Maven backend и React / Vite frontend в `frontend`.
- Для этого проекта сейчас **нет** зарегистрированного профиля `project-tests`.
  Используйте приведённый ниже Maven-процесс; при появлении профиля применяйте
  глобальное правило `project-tests`.

Сначала проверьте `git status --short --branch`, наличие инструментов и процессов.
Не меняйте глобальные настройки Maven/JDK или пользовательские профили тестов.

## Сборка и тесты

Обычная полная сборка — `mvn verify`: Maven устанавливает зафиксированный Node,
выполняет `npm ci`, frontend build/test и Java-тесты. Для уже установленного Node
и `frontend/node_modules` ниже приведён проверенный вариант без повторных
скачиваний. Не используйте Maven-флаги пропуска frontend, пока отдельно не прошли
**обе** проверки frontend: тесты и production build.

Пример из корня проекта; все пути и переменные относятся только к текущему shell:

```powershell
$env:JAVA_HOME = 'C:\Users\dima\.jdks\corretto-21.0.4'
$maven = 'C:\apache-maven-3.9.16\bin\mvn.cmd'
$node = Join-Path (Get-Location) 'target\node\node.exe'
$mockitoAgent = 'C:\Users\dima\.m2\repository\org\mockito\mockito-core\5.17.0\mockito-core-5.17.0.jar'

Push-Location frontend
try {
    & $node --import ./test/register.js --test test/*.test.js
    if ($LASTEXITCODE -ne 0) { throw 'Frontend tests failed' }
    & $node node_modules\vite\bin\vite.js build
    if ($LASTEXITCODE -ne 0) { throw 'Frontend build failed' }
} finally { Pop-Location }

# Только локальный settings-файл без глобальных корпоративных mirror-настроек.
# Он нужен, когда Maven ошибочно ищет уже закешированные Central-зависимости
# через недоступный корпоративный mirror. Не копируйте credentials из settings.
New-Item -ItemType Directory -Path target -Force | Out-Null
$localSettings = Join-Path (Get-Location) 'target\local-maven-settings.xml'
Set-Content -LiteralPath $localSettings -Encoding utf8 -Value '<settings xmlns="http://maven.apache.org/SETTINGS/1.0.0" />'

& $maven -s $localSettings -o -B -ntp `
    '-Dskip.installnodenpm' '-Dskip.npm' `
    "-DargLine=-javaagent:$mockitoAgent" verify
if ($LASTEXITCODE -ne 0) { throw 'Maven verify failed' }
```

Используйте существующий Maven cache `C:\Users\dima\.m2\repository`: вложенный
`.m2\repository` проекта оказался неполным. Не задавайте его как
`maven.repo.local`. Ключ `-o` подходит только при наличии всех зависимостей в
cache. Если их нет, используйте обычную сборку с доступными разрешёнными
репозиториями; не устанавливайте инструменты и не меняйте permissions автоматически.
Если Node или `node_modules` отсутствуют, обычный Maven `verify` должен выполнить
frontend-этапы: уберите `-Dskip.installnodenpm` и `-Dskip.npm`.

В этой среде Java-тесты однажды зависли после Mockito self-attach. Явный
`-javaagent` выше позволил выполнить проверки. Версия `5.17.0` соответствует
проверенной зависимости проекта: при изменении зависимостей найдите фактически
используемый Mockito jar, не подставляйте случайную версию. Не меняйте `pom.xml`
ради локальной особенности запуска. Для выбранных Java-тестов можно вызвать
те же settings/agent и цели `resources:resources compiler:compile
resources:testResources compiler:testCompile surefire:test` с
`'-Dtest=ИмяТеста,ДругойТест'`. Это не заменяет frontend build/test или итоговую
полную проверку при изменении общего поведения.

Проверьте `BUILD SUCCESS`, сводки тестов, `git diff --check` и свежий
`target\kayak-bookkeeper-1.0.0.jar`. Не выдавайте старые отчёты из `target` за новую
проверку. Подробные команды выше проверены с Java 21; итоговый `verify` после
реализации посещений выполнил 92 Java-теста, отдельно frontend — 78 тестов.
Это историческая контрольная точка, а не требование к будущему числу тестов.

Не запускайте `clean` и не пересобирайте каталог, из которого работает приложение.
Запускайте приложение из отдельной неизменяемой копии jar, как описано ниже.
Рабочие `target\classes` и итоговый jar тогда можно обновлять следующей сборкой,
не изменяя файлы работающего экземпляра. Не перезаписывайте уже запущенную копию.

## Локальная БД и резервная копия

Используйте `docker-compose.yml` и профиль `local` из
`src\main\resources\application-local.yml`. Локальная БД — PostgreSQL 17,
`localhost:5450/kayak_bookkeeper`, пользователь `postgres`. Настройки подключения
берите из конфигурации; production-конфигурацию для локального запуска не меняйте.
Проверенный существующий контейнер назывался `kayak-bookkeeper-postgres-1`;
получайте актуальный ID через Compose, не полагайтесь на сохранённый PID/ID.

```powershell
docker compose start postgres
if ($LASTEXITCODE -ne 0) { throw 'Could not start existing PostgreSQL' }
$dbContainer = (docker compose ps -q postgres).Trim()
if (-not $dbContainer) { throw 'PostgreSQL container not found' }
docker inspect --format '{{.State.Status}} {{.State.Health.Status}}' $dbContainer
```

Дождитесь `running healthy` ограниченными проверками (например, каждые 2 секунды,
не более 60 секунд). При ошибке проверьте `docker compose logs postgres`.
Если контейнер ещё не создан, в рамках разрешённого первого локального запуска
используйте `docker compose up -d postgres` и затем ту же проверку health.
Никогда не сбрасывайте существующую БД и не удаляйте Docker volumes.

**До запуска jar с новыми миграциями на существующей БД** сохраните backup.
Не передавайте бинарный `pg_dump` через PowerShell text pipeline:

```powershell
$backupStamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$backupDirectory = Join-Path (Get-Location) 'backups'
New-Item -ItemType Directory -Path $backupDirectory -Force | Out-Null
$containerDump = "/tmp/kayak-bookkeeper-$backupStamp.dump"
$backupFile = Join-Path $backupDirectory "kayak-bookkeeper-$backupStamp.dump"
docker exec $dbContainer pg_dump -U postgres -d kayak_bookkeeper -Fc -f $containerDump
if ($LASTEXITCODE -ne 0) { throw 'Database backup failed' }
docker cp "${dbContainer}:$containerDump" $backupFile
if ($LASTEXITCODE -ne 0 -or (Get-Item -LiteralPath $backupFile).Length -eq 0) {
    throw 'Database backup was not copied successfully'
}
```

`backups/` исключён из Git. Не коммитьте backup и не восстанавливайте его поверх
рабочей БД для проверки. Flyway применяется штатно при запуске; сбой миграции —
причина остановиться и исследовать логи, а не удалять данные или менять историю.

## Владение процессом и перезапуск

Рабочий HTTP-порт — `8080`. Метаданные своего экземпляра:

- `target\local-app.pid` — PID запущенного Java-процесса;
- `target\restart-current-path.txt` — абсолютный каталог запущенной копии jar;
- `<каталог запуска>\app.log` и `app-error.log` — логи этого экземпляра.

До остановки прочитайте эти файлы и проверьте **всё**:

1. Каталог после разрешения абсолютного пути находится внутри
   `F:\dev\kayak-bookkeeper\target\run-*`; jar существует в этом каталоге.
2. `Get-CimInstance Win32_Process -Filter "ProcessId = $ownedAppPid"` показывает
   Java с ожидаемым `ExecutablePath`, а `CommandLine` содержит `-jar`, **тот же
   абсолютный путь jar** и `--spring.profiles.active=local`.
3. `Get-NetTCPConnection -LocalPort 8080 -State Listen` показывает этот же
   `OwningProcess`. При отсутствии listener сначала выясните состояние процесса
   по логам; не считайте другой процесс своим по одному имени `java`.

Используйте переменную `$ownedAppPid`, не `$PID` (это PID PowerShell). Останавливать
можно только подтверждённый процесс этого приложения, например
`Stop-Process -Id $ownedAppPid`, затем дождаться его завершения и освобождения
порта. Не завершайте все процессы Java, launcher, IDE и чужого владельца порта.
При несовпадении метаданных сначала исследуйте процесс; не обходите проверку
широкими командами остановки. Если PID уже не существует, это устаревшая запись,
а не разрешение остановить новый процесс с тем же номером.

Если нужная версия уже работает и пользователь попросил только запуск,
проверьте готовность и используйте её. Не создавайте второй экземпляр. Для
перезапуска сначала успешно соберите новую версию, сохраните backup при новых
миграциях, затем остановите только проверенный старый экземпляр.

## Запуск проверенного jar

Запускайте из корня репозитория после успешной сборки, проверки БД/backup и
освобождения порта. Создайте **новый** каталог; при совпадении имени выберите
другое, не перезаписывайте существующий запуск.

```powershell
$repository = (Get-Location).Path
$runDirectory = Join-Path $repository ('target\run-' + (Get-Date -Format 'yyyyMMdd-HHmmss'))
New-Item -ItemType Directory -Path $runDirectory -ErrorAction Stop | Out-Null
$runJar = Join-Path $runDirectory 'kayak-bookkeeper-1.0.0.jar'
Copy-Item -LiteralPath (Join-Path $repository 'target\kayak-bookkeeper-1.0.0.jar') -Destination $runJar
$application = Start-Process `
    -FilePath 'C:\Users\dima\.jdks\corretto-21.0.4\bin\java.exe' `
    -ArgumentList @('-jar', $runJar, '--spring.profiles.active=local') `
    -WorkingDirectory $repository -WindowStyle Hidden -PassThru `
    -RedirectStandardOutput (Join-Path $runDirectory 'app.log') `
    -RedirectStandardError (Join-Path $runDirectory 'app-error.log')
Set-Content -LiteralPath (Join-Path $repository 'target\local-app.pid') -Value $application.Id
Set-Content -LiteralPath (Join-Path $repository 'target\restart-current-path.txt') -Value $runDirectory
```

Не ограничивайтесь фактом появления PID. В течение ограниченного времени
проверьте процесс, владельца порта и фактические HTTP-ответы:

- `http://localhost:8080/` возвращает **200** и HTML интерфейса;
- анонимный `GET http://localhost:8080/api/subscriptions` возвращает **401**;
- свежий `app.log` содержит успешную проверку/применение Flyway и сообщение
  `Started BookkeeperApplication`, без ошибки запуска; изучите также `app-error.log`.

Этот процесс проверен 7 ноября 2026 года на существующей локальной PostgreSQL
17.11: backup сохранён до запуска, Flyway успешно применил V9, UI вернул 200,
анонимный API — 401. Это историческое подтверждение процесса; состояние текущего
экземпляра всегда проверяйте заново по PID, порту, HTTP и свежим логам.

Ожидаемый 401 при проверке API — признак работающей защиты. Для проверки данных
используйте только известные разрешённые credentials; не угадывайте пароль.
`INITIAL_ADMIN_PASSWORD` создаёт первоначальную учётную запись и не сбрасывает
пароль уже существующего администратора. Не меняйте пользователей ради smoke-test.
При неготовности сообщите точную причину из новых логов; не запускайте параллельно
ещё одну копию и не скрывайте проблему повторным сбросом БД.
