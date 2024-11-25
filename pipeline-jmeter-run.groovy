node {
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
    // Массив занятых серверов (создается пустым)
    def serverBusyArr = []


    try {
        properties([
            buildDiscarder(
                logRotator(
                    artifactDaysToKeepStr: '',
                    artifactNumToKeepStr: '',
                    daysToKeepStr: '14',
                    numToKeepStr: '30')
            ),
            parameters([
                string(
                    name: 'NAMEJMX',
                    defaultValue: 'scriptExample-01:10:20:60,scriptExample-02:20:40:60',
                    description: 'Название jmx-файла без расширения + параметры запуска. Пример: script01:throughput:threads:rampUp,script02:throughput:threads:rampUp',
                    trim: true),
                activeChoice(
                        choiceType: 'PT_CHECKBOX',
                        description: 'Выбор сервера',
                        filterLength: 1,
                        filterable: true,
                        name: 'SERVER',
                        script: groovyScript(
                                fallbackScript: [
                                        classpath: [],
                                        oldScript: '',
                                        sandbox: true,
                                        script: 'return ["ERROR"]'
                                ],
                                script: [
                                        classpath: [],
                                        oldScript: '',
                                        sandbox: true,
                                        script: '''
return [
    '192.168.1.111',
    '192.168.1.112',
    '192.168.1.113',
    '192.168.1.114',
    '192.168.1.115',
    '192.168.1.116',
    '192.168.1.117',
    '192.168.1.118',
    '192.168.1.119',
    '192.168.1.120',
    '192.168.1.121',
    '192.168.1.122',
    '192.168.1.123',
    '192.168.1.124',
    '192.168.1.125'
]
'''
                                ])),
                reactiveChoice(
                        choiceType: 'PT_SINGLE_SELECT',
                        description: 'Выбор master-сервера',
                        filterLength: 1,
                        filterable: false,
                        name: 'MASTERSERVER',
                        referencedParameters: 'SERVER',
                        script: groovyScript(
                                fallbackScript: [
                                        classpath: [],
                                        oldScript: '',
                                        sandbox: true,
                                        script: 'return ["ERROR"]'
                                ],
                                script: [
                                        classpath: [],
                                        oldScript: '',
                                        sandbox: true,
                                        script: 'return SERVER.split(",").collect{ it.toString() }'
                                ])),
                activeChoiceHtml(
                        choiceType: 'ET_FORMATTED_HTML',
                        description: 'Проверка доступности выбранных серверов',
                        name: 'CHECKSERVER',
                        omitValueField: false,
                        referencedParameters: 'SERVER',
                        script: groovyScript(
                                fallbackScript: [
                                        classpath: [],
                                        oldScript: '',
                                        sandbox: true,
                                        script: 'return "ERROR"'
                                ],
                                script: [
                                        classpath: [],
                                        oldScript: '',
                                        sandbox: false,
                                        script: '''
try {
    def server = SERVER.split(",").collect{ it.toString() }
    def serverBusy = []
    if (server[0] == '') {
        return "No servers selected!"
    }
    for (host in server) {
        def connection = new URL("http://${host}:9256/metrics").openConnection()
        def text = connection.inputStream.text/metrics
        def pattern = ~/namedprocess_namegroup_num_procs\\{groupname=".*jmeter.*"} [1-9999]/
        def checkPattern = text =~ pattern
        if (checkPattern.size() > 0) {
            serverBusy += host
        }
    }
    if (serverBusy) {
        return "Busy: ${serverBusy.join(",")}"
    } else {
        return "All Free"
    }
}  catch (e) {
        return "ERROR --> {$e}"
    }
'''
                                ])),
                booleanParam(
                    name: 'MASTERRUN',
                    defaultValue: true,
                    description: 'Должен ли master-сервер генерировать нагрузку'),
                string(
                    name: 'DURATION',
                    defaultValue: '54000',
                    description: 'Продолжительность запуска, сек',
                    trim: true),
                choice(
                        name: 'TYPETEST',
                        choices: ['stability', 'maximum'],
                        description: 'Выбор типа теста. Используется для параметра testTitle'),
                choice(
                        name: 'STAND',
                        choices: ['lt01', 'lt02', 'other'],
                        description: 'Выбор стенда. Влияет на выбор URL. Используется в названии jmx-файла'),
                reactiveChoice(
                        choiceType: 'PT_SINGLE_SELECT',
                        description: 'Выбор URL',
                        filterLength: 1,
                        filterable: false,
                        name: 'URLRUN',
                        referencedParameters: 'STAND',
                        script: groovyScript(
                                fallbackScript: [
                                        classpath: [],
                                        oldScript: '',
                                        sandbox: true,
                                        script: 'return ["ERROR"]'
                                ],
                                script: [
                                        classpath: [],
                                        oldScript: '',
                                        sandbox: true,
                                        script: '''
if (STAND == "lt01") {
return ["url-01", "url-02"]
} else if (STAND == "lt02") {
return ["url-03", "url-04"]
} else {
return ["other - use custom URL"]
}
'''
                                ])),
                string(
                    name: 'CUSTOMURL',
                    description: 'Свой вариант URL для запуска jmx-файла',
                    trim: true),
                string(
                        name: 'CERTIFICATE',
                        description: 'Выбор сертификата (p12 / keystore). Полное название с расширением',
                        trim: true),
                password(
                        name: 'CERTIFICATEPASS',
                        description: 'Пароль от сертификата'),
                string(
                        name: 'ALIASNAME',
                        description: 'Значение параметра aliasName в Keystore Configuration',
                        trim: true),
                booleanParam(
                        name: 'CERTIFICATECACHED',
                        defaultValue: true,
                        description: 'Повторное использование кэшированного контекста SSL между итерациями. Установите значение false для сброса контекста SSL на каждой итерации'),
                string(
                        name: 'CUSTOMPARAMRUN',
                        description: 'Доп. параметры запуска Jmeter (-G/-D/-J/-L и тд.). Пример: -Gparam01=hello -Gparam02=12345',
                        trim: true),
                booleanParam(
                        name: 'JMXEXPORTERRUN',
                        defaultValue: true,
                        description: 'Вкл / Выкл мониторинг JMX-метрик'),
                gitParameter(
                    name: 'BRANCH',
                    branch: '',
                    description: 'По умолчанию: master',
                    branchFilter: '.*',
                    defaultValue: 'master',
                    quickFilterEnabled: true,
                    selectedValue: 'NONE',
                    sortMode: 'NONE',
                    tagFilter: '*',
                    listSize: '10',
					useRepository: "${gitRepository}",
                    type: 'GitParameterDefinition')
                ])])

        // environment
        /*
        Используется если при выборе серверов используются не ip, а сокращенное название.
        Добавляет постфикс, получается полное название сервера.
        serverArr = params.SERVER.replace(',', '.suffix.ru,')  + '.suffix.ru'
        serverArr = serverArr.split(',').collect{ it.toString() }
         */
        // Массив серверов
        serverArr = params.SERVER.split(',').collect{ it.toString() }
        // Массив jmx-скриптов
        serviceJmxArr = params.NAMEJMX.split(',')
        // Количество jmx-скриптов
        countJmx = serviceJmxArr.size()

        // Для мониторинга jmx-метрик используются другой файл jmeter и jmeter-server
        if (params.JMXEXPORTERRUN) {
            jmeter = 'jmeter-jmx'
            jmeterServer = 'jmeter-server-jmx'
        }

        stage('Info About Test') {
            // Вывод информации о запуске
            echo '-----------------INFORMATION ABOUT TEST-----------------'
            def messageInfo = "Server:"
            def serviceInfo = ""
            def serviceInfoExporter = ""
            def countJmxPort = 0
            for (host in serverArr) {
                if (host == params.MASTERSERVER) {
                    messageInfo += "\n\t${host} - master"
                } else {
                    messageInfo += "\n\t${host}"
                }
            }
            messageInfo += "\nService:"
            for (service in serviceJmxArr) { 
                service = service.split(':')[0]
                messageInfo += "\n\t${service}"
                serviceInfo += "${service}--"
                if (params.JMXEXPORTERRUN) {
                    serviceInfoExporter += "\n\t${service}:\n\t\tjPort - ${jmxJmeterPort[countJmxPort]}\n\t\tjServerPort - ${jmxJmeterServerPort[countJmxPort]}"
                    countJmxPort++
                } else {
                    serviceInfoExporter = "\n\tnot used"
                }
            }
            messageInfo = """
                |${messageInfo}
                |Parameters:
                |\tstand: ${STAND}
                |\turl: ${URLRUN}
                |\tcustomUrl: ${CUSTOMURL}
                |\tduration: ${DURATION}
                |\ttypeTest: ${TYPETEST}
                |\tmasterRun: ${MASTERRUN}
                |\tcertificate: ${CERTIFICATE}
                |\taliasName: ${ALIASNAME}
                |\tcertificateCached: ${CERTIFICATECACHED}
                |\tcustomParamRun: ${CUSTOMPARAMRUN}
                |\tgitBranch: ${BRANCH}
                |JxmExporterInfo: ${serviceInfoExporter}
            """.stripMargin()
            
            echo "${messageInfo}"
            
            currentBuild.displayName = "${env.BUILD_ID}--${serviceInfo}"
            currentBuild.description = "${messageInfo}"
        }
        stage('Check Server Is Busy') {
            // Очистка WORKSPACE
            echo '-----------------DELETING WORKSPACE-----------------'
            deleteDir()

            // Удаление и добавление ssh-ключа
			echo '-----------------CHECK SSH-KEYGEN-----------------'
			for (host in serverArr) {
                sh "ssh-keygen -R ${host}"
                sh "ssh-keyscan ${host} >> ~/.ssh/known_hosts"
            }

            // Проверка что сервер свободен для запуска теста
			echo '-----------------CHECK SERVER IS BUSY-----------------'
			sshagent(["${credentialSSH}"]) {
				for (host in serverArr) {
					def checkServer = sh(returnStatus: true, script: "ssh ${userSSH}@${host} 'ps -ef | grep ApacheJMeter.jar | grep -v grep > /dev/null'") == 0
					if (checkServer) {
						serverBusyArr += host
					}
				}
				if (serverBusyArr) {
					def serverFree = serverArr - serverBusyArr
					serverFree = serverFree.join('\n\t')
					serverBusyArr = serverBusyArr.join('\n\t')
					echo """
						|Busy server: \n\t${serverBusyArr}
						|Free server: \n\t${serverFree}
					""".stripMargin()
					
					currentBuild.result = 'ABORTED'
					error("Stopping early. The server on which Jmeter is already running is selected.")
				}
			}
        }
        stage('Checkout') {
            echo '-----------------GIT CHECKOUT-----------------'
            checkout changelog: false, poll: false, scm: scmGit(
                branches: [[name: "${BRANCH}"]],
                    extensions: [cloneOption(depth: 1, noTags: true, reference: '', shallow: true)],
                    userRemoteConfigs: [[
                    credentialsId: "${gitCredentialsId}",
                        url: "${gitRepository}"
                        ]])
        }
        stage('Copying Files') {
            sshagent(["${credentialSSH}"]) {
                // Копирование и редактирование файлов на выбранные сервера
                echo '-----------------COPYING FILES-----------------'
                def hostMap = [:]
                for (h in serverArr) {
                    def host = h
                    hostMap[host] = {
                        sh "ssh ${userSSH}@${host} 'cd ${homeDir}; rm -r ${workDir}; mkdir ${workDir}'"
                        for (jmxArr in serviceJmxArr) { 
                            def jmxName = jmxArr.split(':')[0]
                            sh "ssh ${userSSH}@${host} 'mkdir ${homeDir}/${workDir}/${jmxName}'"
                            sh "scp ./${gitPathScript}/${jmxName}.zip ${userSSH}@${host}:${homeDir}/${workDir}/${jmxName}"
                            sh "ssh ${userSSH}@${host} 'cd ${homeDir}/${workDir}/${jmxName}; unzip ${jmxName}.zip; mv ${jmxName}.jmx ${jmxName}-${STAND}.jmx'"
                            if (params.CERTIFICATE) {
                                sh "scp ./${gitPathCertificate}/${CERTIFICATE} ${userSSH}@${host}:${homeDir}/${workDir}/${jmxName}"
                            }
                        }
                    }
                }
                parallel hostMap
            }
        }
        stage('Start Jmeter Server') {
            sshagent(["${credentialSSH}"]) {
                // Запуск jmeter-server на выбранных серверах
                echo '-----------------START JMETER-SERVER-----------------'
                def portJmeter = 1099
                def paramSSL = ''
                def portJmxExporter = ''
                def countJmxPort = 0
                if (params.CERTIFICATE) {
                    paramSSL = "-Djavax.net.ssl.keyStore=${CERTIFICATE} -Djavax.net.ssl.keyStorePassword=${CERTIFICATEPASS} -Dhttps.use.cached.ssl.context=${CERTIFICATECACHED}"
                }
                for (jmxArr in serviceJmxArr) {
                    if (params.JMXEXPORTERRUN) {
                        portJmxExporter = jmxJmeterServerPort[countJmxPort]
                    }
                    def jmxName = jmxArr.split(":")[0]
                    for (host in serverArr) {
                        maskPasswords(varPasswordPairs: [[password: '$CERTIFICATEPASS', var: 'CERTIFICATEPASS']]) {
                            sh "ssh ${userSSH}@${host} 'cd ${homeDir}/${workDir}/${jmxName}; SERVER_PORT=${portJmeter} nohup ${jmeterDir}/${jmeterServer} ${portJmxExporter} ${paramSSL} > /dev/null 2>&1&'"
                        }
                    }
                    portJmeter++
                    countJmxPort++
                }
                sleep 10
            }
        }
        stage('Start Test') {
            sshagent(["${credentialSSH}"]) {
                // Запуск jmeter на выбранных серверах
                echo '-----------------START TEST-----------------'
                def portJmeter = 1099
                def serverStr = ''
                def portJmxExporter = ''
                def countJmxPort = 0
                def urlRun = params.URLRUN
                def serverArrStartTest = serverArr
                if (!params.MASTERRUN) {
                    serverArrStartTest -= params.MASTERSERVER
                }
                if (params.CUSTOMURL) {
                    urlRun = params.CUSTOMURL
                }
                serverStr = serverArrStartTest.join(',')
                for (jmxArr in serviceJmxArr) {
                    if (params.JMXEXPORTERRUN) {
                        portJmxExporter = jmxJmeterPort[countJmxPort]
                    }
                    def serverStart = serverStr.replace(',', ":${portJmeter},") + ":${portJmeter}"
                    def jmxParam = jmxArr.split(':')
                    def jmxName = jmxParam[0]
                    def throughput = jmxParam[1]
                    def threads = jmxParam[2]
                    def rampUp = jmxParam[3]
                    sh "ssh ${userSSH}@${MASTERSERVER} 'cd ${homeDir}/${workDir}/${jmxName}; nohup ${jmeterDir}/${jmeter} ${portJmxExporter} -n -t ${jmxName}-${STAND}.jmx -R ${serverStart} -Dmode=StrippedAsynch -DtestTitle=${TYPETEST} -Gthroughput=${throughput} -Gthreads=${threads} -GrampUp=${rampUp} -Gduration=${DURATION} -Gurl=${urlRun} -GaliasName=${ALIASNAME} ${CUSTOMPARAMRUN} > /dev/null 2>&1&'"
                    portJmeter++
                    countJmxPort++
                }
                sleep 60
                // Проверка что на master-сервере процесс не упал с ошибкой
                def countProcessJmeter = sh(returnStdout: true, script: "ssh ${userSSH}@${MASTERSERVER} 'ps -ef | grep ApacheJMeter.jar | grep -v grep | wc -l'").trim()
                if (countProcessJmeter.toInteger() != countJmx * 2) {
                    currentBuild.result = 'ABORTED'
                    error("Stopping early. One or more jmeter processes failed with an error. Number of jmeter processes - ${countProcessJmeter} != ${countJmx * 2}.")
                }
                sleep (params.DURATION.toInteger() - 60)
            }
        }
    }  catch (e) {
        echo "Failed because of {$e}"
    } finally {
        sshagent(["${credentialSSH}"]) {
            if (!serverBusyArr) {
                // Завершение теста
                echo '-----------------STOP TEST-----------------'
                def portJmeter = 4445
                for (i=0; i < countJmx; i++) {
                        sh "ssh ${userSSH}@${MASTERSERVER} '${jmeterDir}/stoptest.sh ${portJmeter}'"
                        portJmeter += 1
                }
                def tryCount = 0
                while (tryCount < 10) {
                    sleep 10
                    def countProcessJmeter = sh(returnStdout: true, script: "ssh ${userSSH}@${MASTERSERVER} 'ps -ef | grep ApacheJMeter.jar | grep -v grep | wc -l'").trim()
                    if (countProcessJmeter.toInteger() <= countJmx) {
                        echo "Jmeter has successfully completed its work. Number of jmeter processes - ${countProcessJmeter} <= ${countJmx}."
                        break
                    }
                    tryCount++
                }
                // Остановка процессов связанных с запуском теста
                echo '-----------------KILL JMETER-----------------'
                for (host in serverArr) {
                    sh returnStatus: true, script: "ssh ${userSSH}@${host} 'pkill -f ApacheJMeter.jar'"
                }
            }
        }
    }
}
