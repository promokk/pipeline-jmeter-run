# Pipeline-jmeter-run
Jenkins pipeline для автоматизированного запуска нагрузочного теста (Jmeter).  
Выберите сценарий из репозитория git, укажите параметры запуска, выберите сервера (генераторы нагрузки). 
Остальное за вас сделает pipeline.

---
# Оглавление
* [Начало работы](#getStart)
* [Описание pipeline](#pipelineDescription)
  * [Этапы сборки](#assemblySteps)
  * [Обязательные параметры запуска](#requiredStartupParameters)
  * [Архитектура репозитория Git](#gitRepositoryArchitecture)
  * [Используемые плагины Jenkins](#pluginsUseJenkins)
* [Параметры запуска](#launchOptions)
  * [NAMEJMX](#NAMEJMX)
  * [SERVER](#SERVER)
  * [MASTERSERVER](#MASTERSERVER)
  * [CHECKSERVER](#CHECKSERVER)
  * [MASTERRUN](#MASTERRUN)
  * [DURATION](#DURATION)
  * [TYPETEST](#TYPETEST)
  * [STAND](#STAND)
  * [URLRUN](#URLRUN)
  * [CUSTOMURL](#CUSTOMURL)
  * [CERTIFICATE](#CERTIFICATE)
  * [CERTIFICATEPASS](#CERTIFICATEPASS)
  * [ALIASNAME](#ALIASNAME)
  * [CERTIFICATECACHED](#CERTIFICATECACHED)
  * [CUSTOMPARAMRUN](#CUSTOMPARAMRUN)
  * [JMXEXPORTERRUN](#JMXEXPORTERRUN)
  * [BRANCH](#BRANCH)

---
## Начало работы <a id="getStart"></a>
1. **Environment в pipeline.**  
   Обязательные к редактированию: userSSH, credentialSSH, gitRepository, gitCredentialsId, gitPathScript, 
   gitPathCertificate, jmeterDir, jmxJmeterPort, jmxJmeterServerPort.
~~~
// environment
// Пользователь для подключения по ssh
def userSSH = 'user'
// Сredential для подключения по ssh
def credentialSSH = 'credential-ssh'
// Ссылка для подключения к репозиторию
def gitRepository = 'git@github.com:username/jmeter-script.git'
// Сredential для подключения к репозиторию
def gitCredentialsId = 'credential-git'
// Путь в репозитории до директории с архивами jmx-скриптов
def gitPathScript = 'jmeter-script'
// Путь в репозитории до директории с сертификатами
def gitPathCertificate = 'certificate'
// Корневая директория jmeter
def jmeterDir = '/opt/jmeter/bin'
// Директория в которой будет создаваться временная директория для запуска теста (workDir)
def homeDir = '/dir'
// Временная директория из которой будет запускаться тест
def workDir = 'jmeterRun'
// Порты jmx-exporter для запуска процесса jmeter
def jmxJmeterPort = [port01, port02, port03]
// Порты jmx-exporter для запуска процесса jmeter-server
def jmxJmeterServerPort = [port04, port05, port06]
// Наименование файла для запуска jmeter (название по умолчанию)
def jmeter = 'jmeter'
// Наименование файла для запуска jmeter-server (название по умолчанию)
def jmeterServer = 'jmeter-server'
~~~

2. **Обязательные properties, которые нужно отредактировать под ваш проект:**
* SERVER - указать свои сервера (генераторы нагрузки)
* STAND - указать наименование проектов / стендов
* URLRUN - указать url для каждого проекта / стенда

3. **Настройка jmx-exporter:**
* Переменная jmeter и jmeterServer - для запуска Jmeter + jmx-exporter используются другие файлы.  
  Если параметр запуска JMXEXPORTERRUN = true, то для запуска jmeter и jmeter-server будут использованы другие файлы.
* Переменная jmxJmeterPort и jmxJmeterServerPort - необходимо указать порты, которые на серверах выделены под jmx-exporter.  
  При запуске распределенного теста на master-сервере запускается два процесса Jmeter (jmeter и jmeter-server).
  Поэтому для каждого сервера нужно указать минимум по два порта для jmx-exporter.  
  Одна группа портов будет для процессов jmeter - jmxJmeterPort, другая для процессов jmeter-server - jmxJmeterServerPort.  
  Например если на одном сервере будет запускаться одновременно 3 jmx-файла (скрипта Jmeter),
  то на сервере под jmx-exporter должно быть выделено 6 портов.

[Дашборд Grafana и настройка Jmeter + jmx-exporter](https://github.com/promokk/jmeter-jmx-dashboard).  
Файлы доступны в репозитории --> [jmeter-file/](https://github.com/promokk/jmeter-jmx-dashboard/tree/main/jmeter-file).  
Файлы необходимо переименовать:
* jmeter -> jmeter-jmx
* jmeter-server -> jmeter-server-jmx

После этого файлы нужно переместить в корневую директорию Jmeter - {JMETER_HOME}/bin/

4. **Настройка скриптов Jmeter**  
Job запускает jmx-файлы с параметрами, передавая их через командную строку. Наименование параметров в скрипте должно
быть таким же, как при запуске.  
Пример скрипта Jmeter доступен в репозитории --> [jmeter-script/](https://github.com/promokk/pipeline-jmeter-run/tree/main/jmeter-script).

---
## Описание pipeline <a id="pipelineDescription"></a>

---
### Этапы сборки <a id="assemblySteps"></a>
Pipeline состоит из этапов (stage):
* Info About Test  
  Выводит базовую информацию о сборке: выбранные параметры, задействованные порты jmx-exporter.
* Check Server Is Busy  
  Содержит несколько шагов:
  * DELETING WORKSPACE  
    Рекурсивное удаление текущего каталога и его содержимого.
  * CHECK SSH-KEYGEN  
    Если во время сборки возникает ошибка "host key verification failed", то данный шаг решит эту проблему.
  * CHECK SERVER IS BUSY  
    Проверка, что выбранные сервера свободны для запуска теста.
* Checkout  
  Выполнение git checkout.
* Copying Files  
  Копирование и редактирование файлов на выбранные сервера. Этап выполняется параллельно.
* Start Jmeter Server  
  Запуск jmeter-server на выбранных серверах
* Start Test  
  Запуск jmeter на выбранных серверах. Также на этом этапе происходит проверка, что на master-сервере процесс jmeter
  не упал с ошибкой.
* finally  
  Содержит несколько шагов:
  * STOP TEST  
    Завершение теста
  * KILL JMETER  
    Остановка процессов связанных с запуском теста

![assemblySteps - картинка](https://raw.githubusercontent.com/promokk/pipeline-jmeter-run/main/data/assemblySteps.png)

---
### Обязательные параметры запуска <a id="requiredStartupParameters"></a>
Список обязательных параметров, которые необходимо указать для запуска успешной сборки:
* NAMEJMX
* SERVER
* MASTERSERVER
* MASTERRUN
* DURATION
* STAND
* URLRUN / CUSTOMURL
* BRANCH

---
### Архитектура репозитория Git <a id="gitRepositoryArchitecture"></a>
Для работы Job в репозитории понадобятся две директории (названия могут быть любыми):
* jmeter-script - директория с jmx-файлами (скрипты Jmeter)
* certificate - директория с сертификатами

---
### Используемые плагины Jenkins <a id="pluginsUseJenkins"></a>
* Active Choices
* Git Parameter Plug-In
* Mask Passwords Plugin
* Pipeline: Stage View Plugin
* Rebuilder
* SSH Agent Plugin
* SSH Build Agents

---
## Параметры запуска <a id="launchOptions"></a>

---
### NAMEJMX <a id="NAMEJMX"></a>
Название jmx-файла и параметры запуска теста:
* throughput - пропускная способность (rps)
* threads - количество потоков
* rampUp - время выхода потоков (ms)

Пример: script01:{throughput}:{threads}:{rampUp},script02:{throughput}:{threads}:{rampUp}

Параметры запуска теста указываются для каждого сервера. То есть сценарий будет запущен на
каждом сервера с указанными параметрами. Можно указать любое кол-во скриптов.   
Например: если нужно запустить тест с throughput = 100 rps с двух серверов, то в параметрах запуска нужно
указать throughput = 50 rps.

![NAMEJMX - картинка](https://raw.githubusercontent.com/promokk/pipeline-jmeter-run/main/data/NAMEJMX.png)

---
### SERVER <a id="SERVER"></a>
Выбор серверов из списка для запуска теста.  
Минимальное кол-во серверов для запуска - 1, максимальное - ∞.

![SERVER - картинка](https://raw.githubusercontent.com/promokk/pipeline-jmeter-run/main/data/SERVER.png)

---
### MASTERSERVER <a id="MASTERSERVER"></a>
Выбор master-сервера.  
Вариативность зависит от указанных серверов в параметре SERVER.

![MASTERSERVER - картинка](https://raw.githubusercontent.com/promokk/pipeline-jmeter-run/main/data/MASTERSERVER.png)

---
### CHECKSERVER <a id="CHECKSERVER"></a>
Проверка доступности выбранных серверов.  
Для работы параметра на серверах должен быть установлен [process-exporter](https://github.com/ncabatoff/process-exporter).   
Скрипт groovy используемый в параметре должен получить одобрение от администратора Jenkins (так как используется openConnection).  
Динамическая проверка на наличие активных процессов Jmeter на выбранных серверах происходит после любых изменений
параметра SERVER.  
Проверка осуществляется запросом к process-exporter, /metrics.  
Имеет три статуса:
* All Free - все выбранные сервера свободны
* Busy: {servers} - список занятых серверов
* No servers selected! - не выбраны сервера в параметре SERVER

![CHECKSERVER - гифка](https://raw.githubusercontent.com/promokk/pipeline-jmeter-run/main/data/CHECKSERVER.gif)

---
### MASTERRUN <a id="MASTERRUN"></a>
Boolen значение, которое опредяет должен ли master-сервер генерировать нагрузку.
* True - на master-сервере запускается выбранный сценарий. То есть запуск будет осуществлен со всех выбранных серверов.
* False - на master-сервере не запускается выбранный сценарий. Это позволяет снизить нагрузку на master-сервер.

**Примечание:** если при запуске указать один сервер и в параметре MASTERRUN передать значение False, то при запуске  
Jmeter будет ориентироваться на параметр [remote_hosts](https://jmeter.apache.org/usermanual/remote-test.html) в 
файле {JMETER_HOME}/bin/jmeter.properties.

![MASTERRUN - картинка](https://raw.githubusercontent.com/promokk/pipeline-jmeter-run/main/data/MASTERRUN.png)

---
### DURATION <a id="DURATION"></a>
Продолжительность теста.  
Продолжительность работы выбранного сценария. Указывается в секундах.

![DURATION - картинка](https://raw.githubusercontent.com/promokk/pipeline-jmeter-run/main/data/DURATION.png)

---
### TYPETEST <a id="TYPETEST"></a>
Выбор типа теста.  
Используется для параметра testTitle.  
Опциональный параметр. При запуске jmeter передается значение в параметр testTitle. Данный параметр используется в
Backend Listener. Это позволяет на дашборде Grafana выводить значение этого параметра.  
[Дашборд Grafana для Jmeter](https://github.com/promokk/jmeter-dashboard-influxdb), 
параметр используется в таблице Run Test.

![TYPETEST - картинка](https://raw.githubusercontent.com/promokk/pipeline-jmeter-run/main/data/TYPETEST.png)

---
### STAND <a id="STAND"></a>
Выбор стенда.  
Влияет на вариативность значений в параметре URLRUN. Используется в названии jmx-файла.  
Если тестирование проектов происходит на разных тестовых стендах, то этот параметр позволит указать для каждого стенда 
свой набор url.  
Для избежания конфликтов в отображение метрик при запуска нескольких одинаковых jmx-файлов на разных стендах, предусмотрено
изменение названия jmx-файла перед запуском. Название меняется в зависимости от стенда.  
Если при выборе нет нужного стенда, следует выбрать значение "other" и указать url в параметре CUSTOMURL.  
Пример названия jmx-файла после изменения: scriptExample-{STAND}.jmx.

![STAND - картинка](https://raw.githubusercontent.com/promokk/pipeline-jmeter-run/main/data/STAND.png)

---
### URLRUN <a id="URLRUN"></a>
Выбор URL из списка.  
Вариативность зависит от выбранного стенда в параметре STAND.  
Если значение параметра STAND = other, то в значение URLRUN выводится сообщение "other - use custom URL".  
В таких случаях для передачи параметра url следует использовать параметр CUSTOMURL.

![URLRUN - картинка](https://raw.githubusercontent.com/promokk/pipeline-jmeter-run/main/data/URLRUN.png)

---
### CUSTOMURL <a id="CUSTOMURL"></a>
Свой вариант URL для запуска jmx-файла.  
Параметр используется когда нужно запустить тест с url, который не предусмотрена в pipeline.

![CUSTOMURL - картинка](https://raw.githubusercontent.com/promokk/pipeline-jmeter-run/main/data/CUSTOMURL.png)

---
### CERTIFICATE <a id="CERTIFICATE"></a>
Выбор сертификата (p12 / keystore) для запуска jmx-файла.  
Необходимо указать полное название с расширением.  
Если для проведения тестирования должен использоваться сертификат, то следует указать его в этом параметре.  
Сертификат предварительно должен лежать в репозитории GIT.

![CERTIFICATE - картинка](https://raw.githubusercontent.com/promokk/pipeline-jmeter-run/main/data/CERTIFICATE.png)

---
### CERTIFICATEPASS <a id="CERTIFICATEPASS"></a>
Пароль от сертификата.  
**Примечание:** если используется сертификат без пароля, то нужно учесть несколько пунктов.
* Отредактировать значение параметра (нажать "Change Password") и удалить все скрытые символы 
(Jenkins подставляет что-то свое).
* Так как значение параметра будет пустым, то при запуске Jmeter этот параметр не будет учитываться. На выбранных серверах
должен быть указан пустой пароль в файле {JMETER_HOME}/bin/system.properties, параметр javax.net.ssl.keyStorePassword.
~~~
# system.properties
javax.net.ssl.keyStorePassword=
~~~

![CERTIFICATEPASS - картинка](https://raw.githubusercontent.com/promokk/pipeline-jmeter-run/main/data/CERTIFICATEPASS.png)

---
### ALIASNAME <a id="ALIASNAME"></a>
Значение параметра aliasName в Keystore Configuration.  
Если при использование [Keystore Configuration](https://jmeter.apache.org/usermanual/component_reference.html#Keystore_Configuration) 
необходимо выбрать один сертификат из файла keystore, то небоходимо передать название сертификата через данный параметр.

![ALIASNAME - картинка](https://raw.githubusercontent.com/promokk/pipeline-jmeter-run/main/data/ALIASNAME.png)

---
### CERTIFICATECACHED <a id="CERTIFICATECACHED"></a>
Boolen значение, которое позволяет вкл / выкл повторное использование кэшированного контекста SSL между итерациями.  
Установите значение False для сброса контекста SSL на каждой итерации.  
Документация Jmeter - [SSL configuration](https://jmeter.apache.org/usermanual/properties_reference.html#ssl_config).

![CERTIFICATECACHED - картинка](https://raw.githubusercontent.com/promokk/pipeline-jmeter-run/main/data/CERTIFICATECACHED.png)

---
### CUSTOMPARAMRUN <a id="CUSTOMPARAMRUN"></a>
Дополнительные параметры запуска Jmeter.  
Позволяет указать параметры запуска, которые не предусмотрена в pipeline.  
Пример: -Gparam01=hello -Gparam02=12345

![CUSTOMPARAMRUN - картинка](https://raw.githubusercontent.com/promokk/pipeline-jmeter-run/main/data/CUSTOMPARAMRUN.png)

---
### JMXEXPORTERRUN <a id="JMXEXPORTERRUN"></a>
Вкл / Выкл мониторинг JMX-метрик.  
Если включить данный параметр, то при запуске jmeter будет запущен jmx-exporter. Это позволит просматривать JMX-метрики
процессов jmeter.  
По [ссылке](https://github.com/promokk/jmeter-jmx-dashboard) можно ознакомиться с дашбордом Grafana для просмотра 
jmx-метрик и с настройкой связки jmeter + jmx-exporter.

![JMXEXPORTERRUN - картинка](https://raw.githubusercontent.com/promokk/pipeline-jmeter-run/main/data/JMXEXPORTERRUN.png)

---
### BRANCH <a id="BRANCH"></a>
Выбор ветки в репозитории GIT для запуска теста.

![BRANCH - картинка](https://raw.githubusercontent.com/promokk/pipeline-jmeter-run/main/data/BRANCH.png)
